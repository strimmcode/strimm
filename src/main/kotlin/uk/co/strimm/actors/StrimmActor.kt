package uk.co.strimm.actors

import akka.actor.AbstractActor
import akka.actor.Props
import akka.actor.Terminated
import uk.co.strimm.Acknowledgement
//import uk.co.strimm.ActorConstants
import uk.co.strimm.actors.messages.*
import uk.co.strimm.actors.messages.create.*
import uk.co.strimm.gui.*
import java.util.*
import java.util.logging.Level

/**
 * Special, unique actor that manages (watches) all other actors created in the application
 */
class StrimmActor(actorMap : HashMap<Class<out Any>, Class<out ActorMessage>>) : AbstractActor(){
    private var actorMessageMap = HashMap<Class<out Any>, Class<out ActorMessage>>()

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
            .match<CreateCameraActor>(CreateCameraActor::class.java){
                message -> createCameraActor(message)
                sender().tell(Acknowledgement.INSTANCE, self())
            }
            .match<CreateTraceActor>(CreateTraceActor::class.java){
                message -> createTraceActor(message)
                sender().tell(Acknowledgement.INSTANCE, self())
            }
            .match<CreateHistogramActor>(CreateHistogramActor::class.java){
                    message -> createHistogramActor(message)
                sender().tell(Acknowledgement.INSTANCE, self())
            }
            .match<CreateSourceTestActor>(CreateSourceTestActor::class.java){
                message -> createSourceTestActor(message)
                sender().tell(Acknowledgement.INSTANCE, self())
            }
            .match<CreateFlowTestActor>(CreateFlowTestActor::class.java){
                message -> createFlowTestActor(message)
                sender().tell(Acknowledgement.INSTANCE, self())
            }
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
    private fun createCameraActor(create : CreateCameraActor){
        val plugin = create.content as CameraWindowPlugin
        val actorName = GUIMain.actorService.makeActorName(plugin.title)
        val cameraActor = GUIMain.actorService.createActor(CameraActor.props(plugin), actorName, CameraActor::class.java)
        GUIMain.loggerService.log(Level.INFO,"Created camera actor $actorName")
        cameraActor.tell(Message(plugin),self)
    }
    private fun createTraceActor(create : CreateTraceActor){
        val plugin = create.content as TraceWindowPlugin
        val actorName = GUIMain.actorService.makeActorName(plugin.title)
        val traceActor = GUIMain.actorService.createActor(TraceActor.props(plugin), actorName, TraceActor::class.java)
        GUIMain.loggerService.log(Level.INFO,"Created trace actor $actorName")
        traceActor.tell(Message(plugin),self)
    }
    private fun createHistogramActor(create : CreateHistogramActor){
        val plugin = create.content as HistogramWindowPlugin
        val actorName = GUIMain.actorService.makeActorName(plugin.title)
        val histogramActor = GUIMain.actorService.createActor(HistogramActor.props(plugin), actorName, HistogramActor::class.java)
        GUIMain.loggerService.log(Level.INFO,"Created histogram actor $actorName")
        histogramActor.tell(Message(plugin),self)
    }
    private fun createSourceTestActor(create : CreateSourceTestActor){
        val name = create.content as String
        val actorName = GUIMain.actorService.makeActorName(name)
        val sourceTestActor = GUIMain.actorService.createActor(SourceTestActor.props(), actorName, SourceTestActor::class.java)
        GUIMain.loggerService.log(Level.INFO,"Created source test actor $actorName")
        sourceTestActor.tell(Message(name),self)
    }
    private fun createFlowTestActor(create : CreateFlowTestActor){
        val name = create.content as String
        val actorName = GUIMain.actorService.makeActorName(name)
        val flowTestActor = GUIMain.actorService.createActor(FlowTestActor.props(), actorName, FlowTestActor::class.java)
        GUIMain.loggerService.log(Level.INFO,"Created flow test actor $actorName")
        flowTestActor.tell(Message(name),self)

    }

}