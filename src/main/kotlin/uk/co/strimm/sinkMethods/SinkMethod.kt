package uk.co.strimm.sinkMethods

import akka.actor.ActorRef
import uk.co.strimm.STRIMMBuffer
import uk.co.strimm.actors.messages.complete.CompleteStreaming
import uk.co.strimm.actors.messages.start.StartStreaming
import uk.co.strimm.experiment.Sink

interface SinkMethod {
    val properties : HashMap<String, String>
    fun init(sink : Sink)
    fun run(data : List<STRIMMBuffer>)
    fun useActor() : Boolean
    fun start() : StartStreaming
    fun complete() : CompleteStreaming
    fun fail(ex : Throwable)
    fun getActorRef() : ActorRef?
}