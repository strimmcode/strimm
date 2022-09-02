package uk.co.strimm.services

import akka.actor.*
import akka.pattern.AskableActorSelection
import akka.stream.ActorMaterializer
import akka.stream.ActorMaterializerSettings
import akka.util.Timeout
import net.imagej.ImageJService
import org.scijava.plugin.Plugin
import org.scijava.service.AbstractService
import org.scijava.service.Service
import scala.concurrent.Await
import uk.co.strimm.ActorConstants
import uk.co.strimm.actors.*
import uk.co.strimm.actors.messages.ActorMessage
import uk.co.strimm.actors.messages.Message
import uk.co.strimm.actors.messages.WatchThisActor
import uk.co.strimm.actors.messages.create.*
import uk.co.strimm.gui.CameraWindowPlugin
import uk.co.strimm.gui.GUIMain
import uk.co.strimm.gui.HistogramWindowPlugin
import uk.co.strimm.gui.TraceWindowPlugin
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.logging.Level

@Plugin(type = Service::class)
class ActorService : AbstractService(), ImageJService {
    //
    var mailboxConfig = com.typesafe.config.ConfigFactory.parseString("control-aware-dispatcher { mailbox-type = \"akka.dispatch.UnboundedControlAwareMailbox\" }")
    var actorSystem : ActorSystem? = ActorSystem.create("STRIMMAkkaSystem", mailboxConfig)
    var materializer = ActorMaterializer.create(ActorMaterializerSettings.create(actorSystem).withInputBuffer(1, 1), actorSystem)
    //
    val actorCreateMessages = hashMapOf<Class<out Any>,Class<out ActorMessage>>()
    var allActors = hashMapOf<ActorRef,Pair<String,Class<out Actor>>>()
    val cameraActorDisplays = hashMapOf<String, ActorRef>()//dataset name, actor ref
    //
    lateinit var mainActor : ActorRef
    lateinit var fileManagerActor : ActorRef
    //
    var deadActors = arrayListOf<ActorRef>()

    init {
        createActorMessageMap()
    }
    fun createActor(props : Props, name : String, actorClass : Class<out Actor>) : ActorRef {
        GUIMain.loggerService.log(Level.INFO,"Registering actor: $name")
        //Create every actor with a control aware dispatcher. This functions like default settings but will give
        //priority to any message that implements akka.dispatch.ControlMessage. This will mostly be used to send
        //high priority stop or kill messages
        val actorRef = actorSystem!!.actorOf(props.withDispatcher("control-aware-dispatcher"),name)

        if(name != ActorConstants.MAIN_ACTOR_NAME) {
            val msg = WatchThisActor(actorRef)
            mainActor.tell(msg, ActorRef.noSender())
        }

        allActors[actorRef] = Pair(name,actorClass)
        return actorRef
    }
    fun removeActor(actorToRemove : ActorRef){
        allActors.remove(actorToRemove)
    }
    fun getActorByName(actorName : String) : ActorRef?{
        var ref : ActorRef? = null
        for(actor in allActors){
            if(actor.value.first == actorName){
                ref = actor.key
                break
            }

            if(getActorPrettyName(actor.value.first) == actorName){
                ref = actor.key
                break
            }
        }
        return ref
    }
    fun <P : Actor?>getActorsOfType(actorClass : Class<P>) : ArrayList<ActorRef> {
        val actorsOfType = arrayListOf<ActorRef>()
        for(actor in allActors){
            if(actor.value.second == actorClass){
                actorsOfType.add(actor.key)
            }
        }
        return actorsOfType
    }
    //
    fun initStrimmAndFileManagerActors() {
        allActors = hashMapOf<ActorRef,Pair<String,Class<out Actor>>>()
        deadActors = arrayListOf<ActorRef>()
        val exServ = Executors.newSingleThreadExecutor()
        var callable = Callable { createActor(StrimmActor.props(actorCreateMessages), ActorConstants.MAIN_ACTOR_NAME, StrimmActor::class.java) }
        var future = exServ.submit(callable)
        mainActor = future.get()
        val fileManagerActorName = GUIMain.actorService.makeActorName("FileManagerActor")
        callable = Callable { createActor(FileManagerActor.props(), fileManagerActorName, FileManagerActor::class.java) }
        future = exServ.submit(callable)
        fileManagerActor = future.get()
        exServ.shutdown()
        fileManagerActor .tell(Message(4), mainActor)
    }
    fun createStrimmActorIfNotExists() : ActorRef {
        allActors = hashMapOf<ActorRef,Pair<String,Class<out Actor>>>()
        deadActors = arrayListOf<ActorRef>()
        mainActor = try {
            val selection = actorSystem!!.actorSelection("/user/StrimmActor")
            val timeOut = Timeout(200, TimeUnit.MILLISECONDS)
            val asker = AskableActorSelection(selection)
            val future = asker.ask(Identify(1),timeOut)
            val identity = Await.result(future, timeOut.duration()) as ActorIdentity
            val ref = identity.actorRef
            ref.get() //This line should cause an error if a StrimmActor doesn't exist
        }
        catch(ex : NoSuchElementException){
            GUIMain.loggerService.log(Level.INFO,"uk.co.strimm.Main STRIMM actor does not exist, creating...")
            createActor(StrimmActor.props(actorCreateMessages), ActorConstants.MAIN_ACTOR_NAME, StrimmActor::class.java)
        }


        synchronized(this){
            mainActor.tell(CreateFileManagerActor("FileManagerActor"), ActorRef.noSender())
        }

        return mainActor
    }
    private fun createActorMessageMap(){
        actorCreateMessages[CameraWindowPlugin::class.java] = CreateCameraActor::class.java
        actorCreateMessages[TraceWindowPlugin::class.java] = CreateTraceActor::class.java
        actorCreateMessages[HistogramWindowPlugin::class.java] = CreateHistogramActor::class.java
        actorCreateMessages[SourceTestActor::class.java] = CreateSourceTestActor::class.java
        actorCreateMessages[FlowTestActor::class.java] = CreateFlowTestActor::class.java
        actorCreateMessages[FileManagerActor::class.java] = CreateFileManagerActor::class.java
        actorCreateMessages[DatasetActor::class.java] = CreateDatasetActor::class.java
    }
    fun makeActorName(baseActorName : String) : String{
        val uniqueID = UUID.randomUUID().toString()
        val sanitizedName = GUIMain.utilsService.sanitiseNameForPlugin(baseActorName)
        return "$sanitizedName-ID-$uniqueID"
    }
    fun getActorPrettyName(fullActorName : String) : String{
        val split = fullActorName.split("-")
        return split.first()
    }


}