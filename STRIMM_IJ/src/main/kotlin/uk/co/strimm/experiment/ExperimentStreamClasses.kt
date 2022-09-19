package uk.co.strimm.experiment

import akka.stream.javadsl.GraphDSL
import uk.co.strimm.TraceData
import uk.co.strimm.STRIMMImage
import akka.NotUsed
import akka.stream.*

//region Sources
/**
 * Class that represents an image source - a camera
 * @param imgSourceName name of the image source
 */
class ExperimentImageSource(var imgSourceName : String, var deviceLabel : String) : ExperimentSource(imgSourceName, deviceLabel){
    var source : SourceShape<STRIMMImage>? = null
    var roiSource : akka.stream.javadsl.Source<STRIMMImage, NotUsed>? = null
    var outs = listOf<ExperimentNode>()
    var outputType = ""
    var bcastObject : UniformFanOutShape<STRIMMImage, STRIMMImage>? = null
    var bcastFwdOp : ExperimentImageFwdOp? = null
    var fwdOps = arrayListOf<ExperimentImageFwdOp>()
    var exposureMs = 0.0
    var intervalMs = 0.0
}

/**
 * Class that represents a trace source - a piece of hardware e.g. an electrode, or a race from an ROI.
 * @param traceSourceName name of the trace source
 */
class ExperimentTraceSource(var traceSourceName : String, var deviceLabel : String) : ExperimentNode(traceSourceName){
    var source : SourceShape<List<ArrayList<TraceData>>>? = null
    var outs = listOf<ExperimentNode>()
    var bcastObject : UniformFanOutShape<List<ArrayList<TraceData>>, List<ArrayList<TraceData>>>? = null
    var outputType = ""
    var bcastFwdOp : ExperimentTraceFwdOp? = null
    var fwdOps = arrayListOf<ExperimentTraceFwdOp>()
    var samplingFrequencyHz = 0.0
    var blockSize = 0
    var channelName = ""
}
//endregion

//region Flows
/**
 * Class that represents a flow that receives images and sends out images
 * @param imgImgFlowName name of the image to image flow
 */
class ExperimentImageImageFlow(var imgImgFlowName: String) : ExperimentFlow(imgImgFlowName){
    var flow : FlowShape<STRIMMImage, STRIMMImage>? = null
    var ins = listOf<ExperimentNode>()
    var outs = listOf<ExperimentNode>()
    var inputType = ""
    var outputType = ""
    var bcastFwdOp : ExperimentImageFwdOp? = null
    var bcastObject : UniformFanOutShape<STRIMMImage, STRIMMImage>? = null
    var mergeFwdOp : ExperimentImageFwdOp? = null
    var mergeObject : UniformFanInShape<STRIMMImage, STRIMMImage>? = null
    var fwdOps = arrayListOf<ExperimentImageFwdOp>()
}

/**
 * Class that represents a flow that receives images and sends out traces (numbers)
 * @param imgTraceFlowName name of the image to trace flow
 */
class ExperimentImageTraceFlow(var imgTraceFlowName: String) : ExperimentFlow(imgTraceFlowName){
    var flow : FlowShape<STRIMMImage, List<ArrayList<TraceData>>>? = null
    var ins = listOf<ExperimentNode>()
    var outs = listOf<ExperimentNode>()
    var inputType = ""
    var outputType = ""
    var bcastFwdOp : ExperimentTraceFwdOp? = null
    var bcastObject : UniformFanOutShape<List<ArrayList<TraceData>>, List<ArrayList<TraceData>>>? = null
    var mergeFwdOp : ExperimentImageFwdOp? = null
    var mergeObject : UniformFanInShape<STRIMMImage, STRIMMImage>? = null
    var fwdOps = arrayListOf<ExperimentTraceFwdOp>()
}

/**
 * Class that represents a flow that receives traces and sends out images
 * @param traceImgFlowName name of the trace to image flow
 */
class ExperimentTraceImageFlow(var traceImgFlowName: String) : ExperimentFlow(traceImgFlowName){
    var flow : FlowShape<List<ArrayList<TraceData>>, STRIMMImage>? = null
    var ins = listOf<ExperimentNode>()
    var outs = listOf<ExperimentNode>()
    var inputType = ""
    var outputType = ""
    var bcastFwdOp : ExperimentImageFwdOp? = null
    var bcastObject : UniformFanOutShape<STRIMMImage, STRIMMImage>? = null
    var mergeFwdOp : ExperimentTraceFwdOp? = null
    var mergeObject : UniformFanInShape<List<ArrayList<TraceData>>, List<ArrayList<TraceData>>>? = null
    var fwdOps = arrayListOf<ExperimentImageFwdOp>()
}

/**
 * Class that represents a flow that receives traces and sends out traces
 * @param traceTraceFlowName name of the trace to trace flow
 */
class ExperimentTraceTraceFlow(var traceTraceFlowName: String) : ExperimentFlow(traceTraceFlowName){
    var flow: FlowShape<List<ArrayList<TraceData>>, List<ArrayList<TraceData>>>? = null
    var ins = listOf<ExperimentNode>()
    var outs = listOf<ExperimentNode>()
    var inputType = ""
    var outputType = ""
    var bcastFwdOp : ExperimentTraceFwdOp? = null
    var bcastObject : UniformFanOutShape<List<ArrayList<TraceData>>, List<ArrayList<TraceData>>>? = null
    var mergeFwdOp : ExperimentTraceFwdOp? = null
    var mergeObject : UniformFanInShape<List<ArrayList<TraceData>>, List<ArrayList<TraceData>>>? = null
    var fwdOps = arrayListOf<ExperimentTraceFwdOp>()
}
//endregion

//region Sinks
/**
 * Class that represents a trace sink - either a trace display or trace data store
 * @param traceSinkName name of the trace sink
 */
class ExperimentTraceSink(var traceSinkName : String) : ExperimentSink(traceSinkName){
    var sink : SinkShape<List<ArrayList<TraceData>>>? = null
    var ins = listOf<ExperimentNode>()
    var outputType = ""
    var primaryDevice = ""
    var primaryDeviceChannel = ""
    var displayOrStore = ""
    var mergeObject : UniformFanInShape<List<ArrayList<TraceData>>, List<ArrayList<TraceData>>>? = null
    var mergeFwdOp : ExperimentTraceFwdOp? = null
}

/**
 * Class that represents a image sink - either a camera display or camera data store
 * @param imageSinkName name of the image sink
 */
class ExperimentImageSink(var imageSinkName : String) : ExperimentSink(imageSinkName){
    var sink : SinkShape<STRIMMImage>? = null
    var ins = listOf<ExperimentNode>()
    var outputType = ""
//    var primaryDevice = ""
//    var primaryDeviceChannel = ""
    var displayOrStore = ""
    var mergeObject : UniformFanInShape<STRIMMImage, STRIMMImage>? = null
    var mergeFwdOp : ExperimentImageFwdOp? = null
}
//endregion

/**
 * Forward ops are partially complete graphs. For example, GraphBuilder.from(source).via(flow) is an example of a
 * partially complete graph and is called a forward op. Forward ops can be chained infinitely, only when they
 * are use with a .to() call do they become fully complete. This class represents a forward op starting from a trace
 * source or flow
 * @param fromNodeName The first node involved in the forward op
 * @param toNodeName The destination node involved in the forward of
 */
class ExperimentTraceFwdOp(var fromNodeName : String, var toNodeName : String){
    var fwdOp : GraphDSL.Builder<NotUsed>.ForwardOps<List<ArrayList<TraceData>>>? = null
}

/**
 * Forward ops are partially complete graphs. For example, GraphBuilder.from(source).via(flow) is an example of a
 * partially complete graph and is called a forward op. Forward ops can be chained infinitely, only when they
 * are use with a .to() call do they become fully complete. This class represents a forward op starting from an image
 * source or flow
 * @param fromNodeName The first node involved in the forward op
 * @param toNodeName The destination node involved in the forward of
 */
class ExperimentImageFwdOp(var fromNodeName : String, var toNodeName : String){
    var fwdOp : GraphDSL.Builder<NotUsed>.ForwardOps<STRIMMImage>? = null
}

/**
 * Class to represent any source
 * @param sourceName The source name
 */
open class ExperimentSource(sourceName : String, deviceLabel : String) : ExperimentNode(sourceName)

/**
 * Class to represent any flow
 * @param flowName The flow name
 */
open class ExperimentFlow(flowName : String) : ExperimentNode(flowName)
/**
 * Class to represent any sink
 * @param sinkName The sink name
 */
open class ExperimentSink(sinkName : String) : ExperimentNode(sinkName)

/**
 * Class to represent any node in the graph
 * @param name
 */
open class ExperimentNode(val name : String)