package uk.co.strimm.flowMethods

import akka.actor.ActorRef
import uk.co.strimm.STRIMMBuffer
import uk.co.strimm.STRIMMImage
import uk.co.strimm.experiment.Flow
import java.util.concurrent.CompletionStage

interface FlowMethod {
    val properties : HashMap<String, String>
    var actor : ActorRef?
    fun init(flow : Flow)
    fun run(image : List<STRIMMBuffer>) : STRIMMBuffer
}