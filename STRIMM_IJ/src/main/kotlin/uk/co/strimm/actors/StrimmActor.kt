package uk.co.strimm.actors

import akka.actor.AbstractActor
import akka.actor.Props
import akka.actor.Terminated
import uk.co.strimm.ActorConstants
import uk.co.strimm.actors.messages.*
import uk.co.strimm.actors.messages.create.*
import uk.co.strimm.gui.CameraWindowPlugin
import uk.co.strimm.gui.GUIMain
import uk.co.strimm.gui.MetaDataWindowPlugin
import uk.co.strimm.gui.TraceWindowPlugin
import java.util.*
import java.util.logging.Level

/**
 * Special, unique actor that manages (watches) all other actors created in the application
 */
class StrimmActor(actorMap : HashMap<Class<out Any>, Class<out ActorMessage>>) : AbstractActor(){
    private var actorMessageMap = HashMap<Class<out Any>, Class<out ActorMessage>>()

    //TODO eventually implement a singleton pattern as there will only ever be one FileWriterActor
    var fileWriterActorCreated = false

    companion object {
        fun props(actorMap : HashMap<Class<out Any>, Class<out ActorMessage>>): Props {
            return Props.create(StrimmActor::class.java) { StrimmActor(actorMap) }
        }
    }

    init {
        actorMessageMap = actorMap
    }

    override fun createReceive(): Receive {
        return receiveBuilder()
            .match<Message>(Message::class.java) { message -> GUIMain.loggerService.log(Level.INFO,"Message sent to STRIMM Actor: ${message.content}") }
            .match<CreateTraceActor>(CreateTraceActor::class.java){ message -> createTraceActor(message)}
            .match<CreateCameraActor>(CreateCameraActor::class.java){ message -> createCameraActor(message)}
            .match<CreateMetaDataActor>(CreateMetaDataActor::class.java){ message -> createMetaDataActor(message)}
            .match<CreateFileWriterActor>(CreateFileWriterActor::class.java){ message -> createFileWriterActor()}
            .match<WatchThisActor>(WatchThisActor::class.java){
                context.watch(it.actorToWatch)
                GUIMain.loggerService.log(Level.INFO, "STRIMM actor now watching ${it.actorToWatch.path().name()}")
            }
            .match<Terminated>(Terminated::class.java){
                GUIMain.loggerService.log(Level.INFO, "Actor ${it.actor.path().name()} terminated")
                GUIMain.actorService.deadActors.add(it.actor)
            }
            .matchAny{ GUIMain.loggerService.log(Level.INFO,"STRIMM actor does not recognise message")}
            .build()
    }


    private fun createFileWriterActor(){
        if(!fileWriterActorCreated) {
            val actorName = GUIMain.actorService.makeActorName(ActorConstants.FILE_WRITE_ACTOR_NAME)
            GUIMain.actorService.createFWActor(FileWriterActor.props(), actorName, FileWriterActor::class.java)
            GUIMain.loggerService.log(Level.INFO, "Created FileWriter actor $actorName")
            fileWriterActorCreated = true
        }
    }

    private fun createTraceActor(createMessage : CreateTraceActor){
        val plugin = createMessage.content as TraceWindowPlugin
        val actorName = GUIMain.actorService.makeActorName(plugin.title)
        val traceActor = GUIMain.actorService.createActor(TraceActor.props(plugin), actorName, TraceActor::class.java)
        GUIMain.loggerService.log(Level.INFO,"Created trace actor $actorName")
        traceActor.tell(Message(plugin), self)
    }

    private fun createTraceDataStoreActor(createMessage: CreateTraceActor){
        //Test method here
    }

    private fun createCameraDataStoreActor(createMessage: CreateTraceActor){
        //Test method here
    }

    private fun createCameraActor(create : CreateCameraActor){
        val plugin = create.content as CameraWindowPlugin
        val actorName = GUIMain.actorService.makeActorName(plugin.title)
        val cameraActor = GUIMain.actorService.createActor(CameraActor.props(plugin), actorName, CameraActor::class.java)
        GUIMain.loggerService.log(Level.INFO,"Created camera actor $actorName")
        cameraActor.tell(Message(plugin),self)
    }

    private fun createMetaDataActor(create : CreateMetaDataActor){
        val plugin = create.content as MetaDataWindowPlugin
        val actorName = GUIMain.actorService.makeActorName(plugin.title)
        val metaDataActor = GUIMain.actorService.createActor(MetaDataActor.props(plugin), actorName, MetaDataActor::class.java)
        GUIMain.loggerService.log(Level.INFO,"Created metadata actor $actorName")
        metaDataActor.tell(Message(plugin),self)
    }
}