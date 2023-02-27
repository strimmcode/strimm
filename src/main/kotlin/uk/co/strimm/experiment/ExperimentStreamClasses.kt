package uk.co.strimm.experiment

import uk.co.strimm.STRIMMBuffer
import akka.stream.*

class ExperimentSource(var imgSourceName : String) : ExperimentNode(imgSourceName){
    var source : SourceShape<STRIMMBuffer>? = null
    var outs = listOf<ExperimentNode>()
    var bcastObject : UniformFanOutShape<STRIMMBuffer, STRIMMBuffer>? = null
    //todo do I need this
    var exposureMs = 0.0
    var intervalMs = 0.0
}
class ExperimentFlow(var imgFlowName: String) : ExperimentNode(imgFlowName){
    var flow : FlowShape<List<STRIMMBuffer>, STRIMMBuffer>? = null
    var ins = listOf<ExperimentNode>()
    var outs = listOf<ExperimentNode>()
    var bcastObject : UniformFanOutShape<STRIMMBuffer, STRIMMBuffer>? = null
    var mergeObject : UniformFanInShape<STRIMMBuffer,List<STRIMMBuffer>>? = null
}
class ExperimentSink(var imageSinkName : String) : ExperimentNode(imageSinkName){
    var sink : SinkShape<List<STRIMMBuffer>>? = null
    var ins = listOf<ExperimentNode>()
    var mergeObject : UniformFanInShape<STRIMMBuffer, List<STRIMMBuffer>>? = null
}


open class ExperimentNode(val name : String)