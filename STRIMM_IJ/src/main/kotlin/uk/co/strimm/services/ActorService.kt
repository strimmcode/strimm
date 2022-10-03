package uk.co.strimm.services

import akka.actor.*
import akka.pattern.AskableActorSelection
import akka.pattern.PatternsCS
import akka.util.Timeout
import net.imagej.ImageJService
import org.scijava.plugin.Plugin
import org.scijava.service.AbstractService
import org.scijava.service.Service
import scala.concurrent.Await
import uk.co.strimm.ActorConstants
import uk.co.strimm.actors.StrimmActor
import uk.co.strimm.actors.messages.*
import uk.co.strimm.actors.messages.create.*
import uk.co.strimm.gui.CameraWindowPlugin
import uk.co.strimm.gui.TraceWindowPlugin
import java.util.*
import java.util.concurrent.TimeUnit
import akka.stream.ActorMaterializer
import akka.stream.ActorMaterializerSettings
import akka.stream.KillSwitches
import net.imagej.display.ImageDisplay
import net.imagej.overlay.Overlay
import uk.co.strimm.actors.CameraActor
import uk.co.strimm.actors.FileWriterActor
import uk.co.strimm.actors.messages.ask.*
import uk.co.strimm.gui.GUIMain
import uk.co.strimm.gui.MetaDataWindowPlugin
import java.util.logging.Level

@Plugin(type = Service::class)
class ActorService : AbstractService(), ImageJService {
    //Create mailbox config so every actor that uses a control aware dispatcher will use this config
    val mailboxConfig = com.typesafe.config.ConfigFactory.parseString("control-aware-dispatcher { mailbox-type = \"akka.dispatch.UnboundedControlAwareMailbox\" }")
    var actorSystem = ActorSystem.create("STRIMMAkkaSystem", mailboxConfig)
    val materializer = ActorMaterializer.create(ActorMaterializerSettings.create(actorSystem).withInputBuffer(1, 1), actorSystem)
    val actorCreateMessages = hashMapOf<Class<out Any>, Class<out ActorMessage>>()
    val allActors = hashMapOf<ActorRef, Pair<String, Class<out Actor>>>()
    val cameraActorDisplays = hashMapOf<String, ActorRef>()//dataset name, actor ref

    //There needs to be one shared killswitch per graph instance
    var sharedKillSwitch = KillSwitches.shared("strimm-shared-killswitch")

    //TODO need to determine if these are equivalent or are both needed
    var routedRoiList = hashMapOf<Overlay, Pair<ActorRef, String>>()
    var routedRoiOverlays = hashMapOf<Overlay, String>()
    lateinit var mainActor: ActorRef
    lateinit var fileWriterActor: ActorRef
    var deadActors = arrayListOf<ActorRef>()

    init {
        createActorMessageMap()
    }

    fun createFWActor(props: Props, name: String, actorClass: Class<out Actor>) {
        GUIMain.loggerService.log(Level.INFO, "Creating File Writer actor...")
        fileWriterActor = createActor(props, name, actorClass)
        fileWriterActor.tell(Message(""), ActorRef.noSender())
    }

    fun createActor(props: Props, name: String, actorClass: Class<out Actor>): ActorRef {
        GUIMain.loggerService.log(Level.INFO, "Registering actor: $name")
        //Create every actor with a control aware dispatcher. This functions like default settings but will give
        //priority to any message that implements akka.dispatch.ControlMessage. This will mostly be used to send
        //high priority stop or kill messages
        val actorRef = actorSystem.actorOf(props.withDispatcher("control-aware-dispatcher"), name)

        if (name != ActorConstants.MAIN_ACTOR_NAME) {
            val msg = WatchThisActor(actorRef)
            mainActor.tell(msg, ActorRef.noSender())
        }

        allActors[actorRef] = Pair(name, actorClass)
        return actorRef
    }

    fun removeActor(actorToRemove: ActorRef) {
        allActors.remove(actorToRemove)
    }

    fun getActorByName(actorName: String): ActorRef? {
        var ref: ActorRef? = null
        for (actor in allActors) {
            if (actor.value.first == actorName) {
                ref = actor.key
                break
            }

            if (getActorPrettyName(actor.value.first) == actorName) {
                ref = actor.key
                break
            }
        }
        return ref
    }

    /**
     * This function will get all the actors of a specific type. It accepts any object that inherits from Actor
     * @param actorClass The class of the actor
     * @return An array list of all actors with the actorClass (as ActorRef objects)
     */
    fun <P : Actor?> getActorsOfType(actorClass: Class<P>): ArrayList<ActorRef> {
        val actorsOfType = arrayListOf<ActorRef>()
        for (actor in allActors) {
            if (actor.value.second == actorClass) {
                actorsOfType.add(actor.key)
            }
        }
        return actorsOfType
    }

    /**
     * When we start using actors, we want a main actor (like a lead actor). As the actor service may not have been
     * instantiated on the initialisation of the dockable windows service, we have to create this main actor the first
     * time a dockable window plugin is created. We only need create this main actor once
     * @return The main actor as an ActorRef object
     */
    fun createStrimmActorIfNotExists(): ActorRef {
        mainActor = try {
            val selection = actorSystem.actorSelection("/user/StrimmActor")
            val timeOut = Timeout(200, TimeUnit.MILLISECONDS)
            val asker = AskableActorSelection(selection)
            val future = asker.ask(Identify(1), timeOut)
            val identity = Await.result(future, timeOut.duration()) as ActorIdentity
            val ref = identity.actorRef
            ref.get() //This line should cause an error if a StrimmActor doesn't exist
        } catch (ex: NoSuchElementException) {
            GUIMain.loggerService.log(Level.INFO, "Main STRIMM actor does not exist, creating...")
            createActor(StrimmActor.props(actorCreateMessages), ActorConstants.MAIN_ACTOR_NAME, StrimmActor::class.java)
        }

        // Only one instance of FileWriterActor is required per STRIMM session, so this can be created here
        synchronized(this) {
            mainActor.tell(CreateFileWriterActor(""), ActorRef.noSender())
        }

        return mainActor
    }

    /**
     * For any dockable window plugin that has an associated actor, make a note of which create message to use. Note
     * that data store actors are not registered here as they do not have an associated dockable window plugin
     */
    private fun createActorMessageMap() {
        actorCreateMessages[TraceWindowPlugin::class.java] = CreateTraceActor::class.java
        actorCreateMessages[CameraWindowPlugin::class.java] = CreateCameraActor::class.java
        actorCreateMessages[MetaDataWindowPlugin::class.java] = CreateMetaDataActor::class.java
    }

    /**
     * Creates a unique actor name. Actor names will have a GUID attached because it is highly advisable NOT to reuse
     * actor paths (names), even after the actor has been terminated. For more info see the "reusing actor paths"
     * section here: https://doc.akka.io/docs/akka/snapshot/general/addressing.html#reusing-actor-paths
     * @param baseActorName The actor name without any GUID
     * @return The unique actor name
     */
    fun makeActorName(baseActorName: String): String {
        val uniqueID = UUID.randomUUID().toString()
        val sanitizedName = GUIMain.utilsService.sanitiseNameForPlugin(baseActorName)
        return "$sanitizedName-ID-$uniqueID"
    }

    /**
     * Based on the format of actor names (see makeActorName) the base "pretty" name will be at the beginning
     * @param fullActorName The full actor name including GUID
     * @return The base actor name
     */
    fun getActorPrettyName(fullActorName: String): String {
        val split = fullActorName.split("-")
        return split.first()
    }

    fun getPertainingDisplaySinkNameFromDockableTitle(activeDisplaySz: String): String? {
        val cameraActor = getPertainingCameraActorFromDockableTitle(activeDisplaySz)
        if (cameraActor != null) {
            //use cameraActor to get sink.name
            val future = PatternsCS.ask(cameraActor, AskDisplaySinkName(), 5000).toCompletableFuture()
            val sinkName: String = future.toCompletableFuture().get() as String
            return sinkName
        }
        return null
    }

    /**
     * This code will be run from a right click on a camera feed. Find the camera actor relating to this context
     * @param activeDisplay The ImageDisplay currently in focus
     * @return The reference to the pertaining camera actor
     */
    fun getPertainingCameraActorFromDisplay(activeDisplay: ImageDisplay): ActorRef? {
        val cameraActors = getActorsOfType(CameraActor::class.java)
        for (cameraActor in cameraActors) {
            //TODO determine optimal timeout
            val future = PatternsCS.ask(cameraActor, AskDisplayName(), 5000).toCompletableFuture()
            //TODO do we need a completed/isDone check?
            val cameraActorDisplayName = future.toCompletableFuture().get()
            if (cameraActorDisplayName == activeDisplay.name) {
                return cameraActor
            }
        }

        return null
    }

    fun getPertainingCameraActorFromDockableTitle(dockableTitle: String): ActorRef? {
        val cameraActors = getActorsOfType(CameraActor::class.java)
        for (cameraActor in cameraActors) {
            //TODO determine optimal timeout
            val future = PatternsCS.ask(cameraActor, AskDisplayName(), 5000).toCompletableFuture()
            //TODO do we need a completed/isDone check?
            val cameraActorDisplayName = future.toCompletableFuture().get() as String
            if (cameraActorDisplayName in dockableTitle) {
                return cameraActor
            }
        }

        return null
    }

    fun askFileWriterActorIfWriting(): Boolean? {
        val future = PatternsCS.ask(fileWriterActor, AskIsWriting(), 5000).toCompletableFuture()
        if (future.toCompletableFuture().get() is Boolean) {
            return future.toCompletableFuture().get() as Boolean
        }

        return null
    }

    fun askTraceActorIfIsTraceROI(traceActor: ActorRef): Boolean? {
        val future = PatternsCS.ask(traceActor, AskIsTraceROI(), 5000).toCompletableFuture()
        if (future.toCompletableFuture().get() is Boolean) {
            return future.toCompletableFuture().get() as Boolean
        }

        return null
    }

    /**
     * This method will get the camera device label that is pertaining to a camera actor
     * @param cameraActor The camera actor for a display
     * @return The camera device label
     */
    fun getPertainingCameraDeviceLabelForActor(cameraActor: ActorRef): String? {
        val future = PatternsCS.ask(cameraActor, AskCameraStreamSource(), 5000).toCompletableFuture()
        val deviceLabel = future.toCompletableFuture().get()
        return deviceLabel as? String
    }

    fun getPertainingDatasetForCameraDevice(cameraDeviceLabel: String): String? {
        val allCameraActors = allActors.filter { x -> x.value.second == CameraActor::class.java }
        for (actor in allCameraActors) {
            val askDeviceLabelFuture = PatternsCS.ask(actor.key, AskCameraDeviceLabel(), 5000).toCompletableFuture()
            val actorCameraDeviceLabel = askDeviceLabelFuture.toCompletableFuture().get()
            if (cameraDeviceLabel == actorCameraDeviceLabel) {
                val askDatasetNameFuture = PatternsCS.ask(actor.key, AskDatasetName(), 5000).toCompletableFuture()
                val datasetName = askDatasetNameFuture.toCompletableFuture().get()
                return datasetName as? String
            }
        }

        return null
    }
}