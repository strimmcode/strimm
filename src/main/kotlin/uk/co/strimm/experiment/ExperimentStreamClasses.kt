package uk.co.strimm.experiment

import uk.co.strimm.STRIMMBuffer
import akka.stream.*

class ExperimentSource(var imgSourceName : String) : ExperimentNode(imgSourceName){
    var source : SourceShape<List<STRIMMBuffer>>? = null
    var outs = listOf<ExperimentNode>()
    var bcastObject : UniformFanOutShape<List<STRIMMBuffer>, List<STRIMMBuffer>>? = null
    //TODO do we need these settings here?
    var exposureMs = 0.0
    var intervalMs = 0.0
}
class ExperimentFlow(var imgFlowName: String) : ExperimentNode(imgFlowName){
    var flow : FlowShape<List<STRIMMBuffer>, List<STRIMMBuffer>>? = null
    var ins = listOf<ExperimentNode>()
    var outs = listOf<ExperimentNode>()
    var bcastObject : UniformFanOutShape<List<STRIMMBuffer>, List<STRIMMBuffer>>? = null
    var mergeObject : UniformFanInShape<List<STRIMMBuffer>,List<STRIMMBuffer>>? = null
}
class ExperimentSink(var imageSinkName : String) : ExperimentNode(imageSinkName){
    var sink : SinkShape<List<STRIMMBuffer>>? = null
    var ins = listOf<ExperimentNode>()
    var mergeObject : UniformFanInShape<List<STRIMMBuffer>, List<STRIMMBuffer>>? = null
}

open class ExperimentNode(val name : String)