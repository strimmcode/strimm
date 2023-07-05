package uk.co.strimm.flowMethods

import akka.actor.ActorRef
import uk.co.strimm.STRIMMBuffer

import uk.co.strimm.experiment.Flow
import java.util.concurrent.CompletionStage
//Interface for all Flows
//classes in jars will subclass this interface
interface FlowMethod {
    val properties : HashMap<String, String>
    var actor : ActorRef?
    fun init(flow : Flow)
    fun run(images : List<STRIMMBuffer>) : List<STRIMMBuffer>
    fun preStart()
    fun postStop()
}