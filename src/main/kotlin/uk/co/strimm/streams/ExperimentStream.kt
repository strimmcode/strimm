package uk.co.strimm.streams

import akka.NotUsed
import akka.actor.ActorRef
import akka.stream.ClosedShape
import akka.stream.FlowShape
import akka.stream.Graph
import akka.stream.javadsl.*
import akka.stream.javadsl.Flow
import akka.stream.javadsl.Sink
import akka.stream.javadsl.Source
import net.imagej.display.ImageDisplay
import net.imagej.overlay.Overlay
import uk.co.strimm.Acknowledgement
import uk.co.strimm.STRIMMBuffer
import uk.co.strimm.STRIMMImage
import uk.co.strimm.STRIMMSignalBuffer
import uk.co.strimm.experiment.*
import uk.co.strimm.flowMethods.FlowMethod
import uk.co.strimm.gui.GUIMain
import uk.co.strimm.sinkMethods.SinkMethod
import uk.co.strimm.sourceMethods.SourceMethod
import java.net.URL
import java.time.Duration
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.function.Supplier
import java.util.logging.Level


class ExperimentStream(val expConfig: ExperimentConfiguration){
    //construction of the stream

    //var sourceList = arrayListOf<SourceMethod>()

    var experimentSources = arrayListOf<ExperimentSource>()
    var experimentFlows = arrayListOf<ExperimentFlow>()
    var experimentSinks = arrayListOf<ExperimentSink>()

    var sourceMethods = hashMapOf<String, SourceMethod>() ////////////
    var flowMethods = hashMapOf<String, FlowMethod>() //////////
    var sinkMethods = hashMapOf<String, SinkMethod>() ////////////

    var numConnectionsSpecified = 0
    var numConnectionsMade = 0
    var stream : RunnableGraph<NotUsed>? = null
    //actors associated with the experiment
    var cameraActors = hashMapOf<ActorRef, String>()
    var cameraDisplays = hashMapOf<String, ImageDisplay>()

    //keep an overlay record
    var overlayIndices = arrayListOf<Int>()
    //maintain an overlay list for each experiment to deal with the fact that the OverlayService is only
    //reset when IJ is reset.
    var overlayList = hashMapOf<ImageDisplay, List<Overlay>>()





    var cameraDataStoreActors = hashMapOf<ActorRef, String>()
    var traceDataStoreActors = hashMapOf<ActorRef, String>()


    val newTraceROIActors = hashMapOf<ActorRef, String>()//traces created at runtime rather than loaded with json

    var durationMs = 0
    var isRunning = false

    fun createStream(expConfig : ExperimentConfiguration) : RunnableGraph<NotUsed>?{

        try{
            setNumberOfSpecifiedConnections(expConfig)
            val graph = createStreamGraph(expConfig)
            val streamGraph = RunnableGraph.fromGraph(graph)
            stream = streamGraph
            return stream
        }
        catch(ex : Exception){
            GUIMain.loggerService.log(Level.SEVERE, "Error building stream graph. Error message: ${ex.message}")
            GUIMain.loggerService.log(Level.SEVERE, ex.stackTrace)
        }

        return null
    }
    private fun createStreamGraph(expConfig : ExperimentConfiguration) : Graph<ClosedShape, NotUsed> {
        try {
            return (GraphDSL.create () { builder ->
                populateLists(expConfig, builder)
                experimentSources.forEach { x -> x.outs = getOutlets(x, expConfig) }
                experimentFlows.forEach { x -> x.ins = getInlets(x, expConfig) }
                experimentFlows.forEach { x -> x.outs = getOutlets(x, expConfig) }
                experimentSinks.forEach { x -> x.ins = getInlets(x, expConfig) }
                setMergeObjectForSinks(builder)
                setMergeObjectForFlows(builder)
                setBroadcastObjectForSources(builder)
                setBroadcastObjectForFlows(builder)
                buildGraph(builder)
                logInfo()
                GUIMain.loggerService.log(Level.INFO, "Finished building graph")
                checkNumberOfConnections()
                ClosedShape.getInstance()
            })
        }
        catch(ex: Exception){
            throw ex
        }
    }

    fun runStream(){
            isRunning = true
            GUIMain.softwareTimerService.setFirstTimeMeasurement()
            sourceMethods.values.forEach{
                it.preStart()
            }
            flowMethods.values.forEach{
                it.preStart()
            }
            sinkMethods.values.forEach{
                it.preStart()
            }
            GUIMain.loggerService.log(Level.INFO, "Running stream")
            stream?.run(GUIMain.actorService.materializer)
    }

    private fun checkNumberOfConnections(){
        if(numConnectionsMade == numConnectionsSpecified){
            GUIMain.loggerService.log(Level.INFO, "Number of connections specified is equal to number of connections made (this is good)." +
                    " Number of specified connections is $numConnectionsSpecified, number of connections made is $numConnectionsMade")
        }
        else{
            GUIMain.loggerService.log(Level.SEVERE, "Number of connections specified not equal to number of connections made. The running of the stream graph will likely fail." +
                    " Number of specified connections is $numConnectionsSpecified, number of connections made is $numConnectionsMade")
        }
    }

    private fun setNumberOfSpecifiedConnections(expConfig: ExperimentConfiguration){
        numConnectionsSpecified += expConfig.flowConfig.flows.map { x -> x.inputNames.size }.sum()
        numConnectionsSpecified += expConfig.sinkConfig.sinks.map { x -> x.inputNames.size }.sum()
    }

    private fun logInfo(){
        val sb = StringBuilder()
        sb.append("Experiment stream info:\n")
        sb.append("Number of sources: ${experimentSources.size}\n")
        sb.append("Number of flows: ${experimentFlows.size}\n")
        sb.append("Number of sinks ${experimentSinks.size}\n")

        sb.append("Sources details:\n")
        experimentSources.forEach { x ->
            sb.append("Source name: ${x.imgSourceName}, ")
            sb.append("number of source outlets: ${x.outs.size}\n")
        }
        experimentFlows.forEach { x ->
            sb.append("Flow name: ${x.imgFlowName}, ")
            sb.append("number of flow inlets: ${x.ins.size}, ")
            sb.append("number of flow outlets: ${x.outs.size}, ")

        }
        experimentSinks.forEach { x ->
            sb.append("Sink name: ${x.imageSinkName}, ")
            sb.append("number of sink inlets: ${x.ins.size}\n")
        }


        GUIMain.loggerService.log(Level.INFO,sb.toString())
    }

    private fun buildGraph(builder : GraphDSL.Builder<NotUsed>){
        buildSourceGraphParts(builder)
        buildFlowGraphParts(builder)
    }

    private fun buildSourceGraphParts(builder : GraphDSL.Builder<NotUsed>){
        buildImageSourceParts(builder)
    }

    private fun buildFlowGraphParts(builder : GraphDSL.Builder<NotUsed>){
       buildImageImageFlowParts(builder)
    }

    private fun buildImageSourceParts(builder : GraphDSL.Builder<NotUsed>){
        try {
            for (imageSource in experimentSources) {
                for (outlet in imageSource.outs) {
                    when (outlet) {
                        is ExperimentFlow -> {
                            builder.from(imageSource.bcastObject).viaFanIn(outlet.mergeObject)
                            numConnectionsMade++
                        }
                        is ExperimentSink -> {
                            builder.from(imageSource.bcastObject).viaFanIn(outlet.mergeObject)
                            numConnectionsMade++
                        }
                    }
                }
            }
        }
        catch(ex : Exception){
            GUIMain.loggerService.log(Level.SEVERE, "Error building connections for image sources. Error message: ${ex.message}")
            GUIMain.loggerService.log(Level.SEVERE, ex.stackTrace)
        }
    }

    private fun buildImageImageFlowParts(builder : GraphDSL.Builder<NotUsed>){
        try {
            for (imgImgFlow in experimentFlows) {
                    for (outlet in imgImgFlow.outs) {
                        when (outlet) {
                            is ExperimentFlow -> {
                                builder.from(imgImgFlow.bcastObject).viaFanIn(outlet.mergeObject)
                                numConnectionsMade++
                            }
                            is ExperimentSink -> {
                                builder.from(imgImgFlow.bcastObject).toFanIn(outlet.mergeObject)
                                numConnectionsMade++
                            }
                        }
                    }
            }
        }
        catch(ex : Exception){
            GUIMain.loggerService.log(Level.SEVERE, "Error building connections for image to image flows. Error message: ${ex.message}")
            GUIMain.loggerService.log(Level.SEVERE, ex.stackTrace)
        }
    }

    private fun setBroadcastObjectForSources(builder : GraphDSL.Builder<NotUsed>){
        try {
            experimentSources.forEach { x ->
                    x.bcastObject = builder.add(Broadcast.create<STRIMMBuffer>(x.outs.size))
                    builder.from(x.source).viaFanOut(x.bcastObject)
            }
        }
        catch(ex : Exception){
            GUIMain.loggerService.log(Level.SEVERE, "Error creating broadcast objects for sources. Error message: ${ex.message}")
            GUIMain.loggerService.log(Level.SEVERE, ex.stackTrace)
        }
    }

    private fun setBroadcastObjectForFlows(builder : GraphDSL.Builder<NotUsed>){
        try {
            experimentFlows.forEach { x ->
                x.bcastObject = builder.add(Broadcast.create<STRIMMBuffer>(x.outs.size))
                builder.from(x.flow).viaFanOut(x.bcastObject)
            }
        }
        catch (ex : Exception){
            GUIMain.loggerService.log(Level.SEVERE, "Error creating broadcast objects for flows. Error message: ${ex.message}")
            GUIMain.loggerService.log(Level.SEVERE, ex.stackTrace)
        }
    }

    private fun setMergeObjectForSinks(builder : GraphDSL.Builder<NotUsed>){
        try {
            experimentSinks.forEach { x ->
                x.mergeObject = builder.add(MergeLatest.create<STRIMMBuffer>(x.ins.size))
                builder.from(x.mergeObject).to(x.sink)
            }
        }
        catch(ex : Exception){
            GUIMain.loggerService.log(Level.SEVERE, "Error creating merge objects for sinks. Error message: ${ex.message}")
            GUIMain.loggerService.log(Level.SEVERE, ex.stackTrace)
        }
    }

    private fun setMergeObjectForFlows(builder : GraphDSL.Builder<NotUsed>){
        try {
            experimentFlows.forEach { x ->
                        x.mergeObject = builder.add(MergeLatest.create<STRIMMBuffer>(x.ins.size))
                        builder.from(x.mergeObject).via(x.flow)
            }
        }
        catch (ex : Exception){
            GUIMain.loggerService.log(Level.SEVERE, "Error creating merge objects for flows. Error message: ${ex.message}")
            GUIMain.loggerService.log(Level.SEVERE, ex.stackTrace)
        }
    }

    private fun populateLists(expConfig : ExperimentConfiguration, builder : GraphDSL.Builder<NotUsed>){
        populateSources(expConfig, builder)
        populateFlows(expConfig, builder)
        populateSinks(expConfig, builder)
    }

    private fun populateSources(expConfig: ExperimentConfiguration, builder : GraphDSL.Builder<NotUsed>){
            try {

            for (source in expConfig.sourceConfig.sources) {
                    val expSource = ExperimentSource(source.sourceName)
                    val cl = Class.forName("uk.co.strimm.sourceMethods." + source.sourceType)
                    val sourceMethod = cl.newInstance() as SourceMethod
                    sourceMethod.init(source)
                    //sourceList.add(sourceMethod)
                    sourceMethods[source.sourceName] = sourceMethod

                    //sourceMethods[source.sourceName] = sourceMethod

                    val akkaSource =
                        if (source.isTimeLapse) {
                            if (source.async) {
                                //this option will take images at a rate determined by Tick
                                Source.tick(Duration.ZERO, Duration.ofMillis(source.intervalMs.toLong()), Unit)
                                    //.map { sourceMethod.run() }
                                    //.filter{ GUIMain.experimentService.experimentStream.isRunning == true}
                                    .mapAsync(
                                        1,
                                        akka.japi.function.Function { t: Unit -> CompletableFuture.supplyAsync(Supplier { sourceMethod.run() }) as CompletionStage<STRIMMBuffer> }
                                    )
//                                    .filter { it.status == 1 }
                                    .async() //ensures that akka-stream source gets its own thread so cameras can work in parallel
                                    .named(source.sourceName) as Source<STRIMMBuffer, NotUsed>
                            }
                            else{
                                //this option will take images at a rate determined by Tick
                                Source.tick(Duration.ZERO, Duration.ofMillis(source.intervalMs.toLong()), Unit)
                                    //.map { sourceMethod.run() }
                                    //.filter{ GUIMain.experimentService.experimentStream.isRunning == true}
                                    .mapAsync(1,
                                        akka.japi.function.Function { t: Unit -> CompletableFuture.supplyAsync(Supplier { sourceMethod.run() }) as CompletionStage<STRIMMBuffer> }
                                    )
//                                    .filter { it.status == 1 }
                                    .named(source.sourceName) as Source<STRIMMBuffer, NotUsed>
                            }
                        }
                        else {
                            //previously done with unfoldAsync
//                                Source.unfoldAsync(
//                                    startState,
//                                    { currentState ->
//                                       CompletableFuture.supplyAsync(Supplier {
//                                           Optional.of(akka.japi.Pair(currentState, sourceMethod.run() ))
//                                       })
//                                    })
                            if (source.async) {
                                Source.repeat(Unit)
                                    .filter { GUIMain.experimentService.experimentStream.isRunning == true }

                                    .mapAsync(
                                        1,
                                        akka.japi.function.Function { t: Unit -> CompletableFuture.supplyAsync(Supplier { sourceMethod.run() }) as CompletionStage<STRIMMBuffer> }
                                    )
//                                    .filter { it.status == 1 }
                                    .async()
                                    .named(source.sourceName) as Source<STRIMMBuffer, NotUsed>
                            }
                            else{
                                Source.repeat(Unit)
                                    .filter { GUIMain.experimentService.experimentStream.isRunning == true }
                                    .mapAsync(1,
                                        akka.japi.function.Function { t: Unit -> CompletableFuture.supplyAsync(Supplier { sourceMethod.run() }) as CompletionStage<STRIMMBuffer> }
                                    )
//                                    .filter { it.status == 1 }
                                    .named(source.sourceName) as Source<STRIMMBuffer, NotUsed>
                            }
                        }
                    expSource.source = builder.add(akkaSource)
                    experimentSources.add(expSource)
            }
        }
        catch(ex : Exception){
            GUIMain.loggerService.log(Level.SEVERE, "Error creating sources from json experiment configuration. Error message: ${ex.message}")
            GUIMain.loggerService.log(Level.SEVERE, ex.stackTrace)
        }
    }

    private fun populateFlows(expConfig: ExperimentConfiguration, builder : GraphDSL.Builder<NotUsed>){
        try {
            for (flow in expConfig.flowConfig.flows) {
                val expFlow = ExperimentFlow(flow.flowName)
                val cl = Class.forName("uk.co.strimm.flowMethods." + flow.flowType)
                val flowMethod = cl.newInstance() as FlowMethod
                flowMethod.init(flow)
                flowMethods[flow.flowName] = flowMethod

                if (flow.async) {
                    val akkaFlow = Flow.of(List::class.java)
                        .mapAsync(
                            1,
                            akka.japi.function.Function { t: List<*> ->
                                CompletableFuture.supplyAsync(Supplier {
                                    flowMethod.run(
                                        t as List<STRIMMBuffer>
                                    )
                                }) as CompletionStage<STRIMMBuffer>
                            }
                        )
//                        .filter { it.status == 1 }
                        .async()
                        .named(flow.flowName)
                    expFlow.flow = builder.add(akkaFlow) as FlowShape<List<STRIMMBuffer>, STRIMMBuffer>
                    experimentFlows.add(expFlow)
                }
                else{
                    val akkaFlow = Flow.of(List::class.java)
                        .mapAsync(
                            1,
                            akka.japi.function.Function { t: List<*> ->
                                CompletableFuture.supplyAsync(Supplier {
                                    flowMethod.run(
                                        t as List<STRIMMBuffer>
                                    )
                                }) as CompletionStage<STRIMMBuffer>
                            }
                        ).named(flow.flowName)
                    expFlow.flow = builder.add(akkaFlow) as FlowShape<List<STRIMMBuffer>, STRIMMBuffer>
                    experimentFlows.add(expFlow)
                }
            }
        }
        catch(ex : Exception){
            GUIMain.loggerService.log(Level.SEVERE, "Error creating flows from json experiment configuration. Error message: ${ex.message}")
            GUIMain.loggerService.log(Level.SEVERE, ex.stackTrace)
        }
    }

    private fun populateSinks(expConfig: ExperimentConfiguration, builder : GraphDSL.Builder<NotUsed>) {
        try {
            for (sink in expConfig.sinkConfig.sinks) {
                    val expSink = ExperimentSink(sink.sinkName)
                    val cl = Class.forName("uk.co.strimm.sinkMethods." + sink.sinkType)
                    val sinkMethod = cl.newInstance() as SinkMethod
                    sinkMethod.init(sink)
                    sinkMethods[sink.sinkName] = sinkMethod

                    if (sinkMethod.useActor()) {
                        GUIMain.loggerService.log(Level.INFO, "Creating sink actor to go with sink")
                        val akkaSink: Sink<List<STRIMMBuffer>, NotUsed> =
                            Sink.actorRefWithAck(
                                sinkMethod.getActorRef()!!, sinkMethod.start(),
                                Acknowledgement.INSTANCE, sinkMethod.complete()
                            ) { ex -> sinkMethod.fail(ex) }
                        expSink.sink = builder.add(akkaSink)
                        experimentSinks.add(expSink)
                    }
                    else{
                        expSink.sink = builder.add(Sink.foreach{x : List<STRIMMBuffer> ->
                            sinkMethod.run(x)
                        })
                        experimentSinks.add(expSink)
                    }
            }
        } catch (ex: Exception) {
            GUIMain.loggerService.log(Level.SEVERE, "Error in creating sink. Message: ${ex.message}")
            GUIMain.loggerService.log(Level.SEVERE, ex.stackTrace)
        }
    }

    private fun getOutlets(currentNode : ExperimentNode, expConfig: ExperimentConfiguration) : ArrayList<ExperimentNode>{
        val outlets = arrayListOf<ExperimentNode>()
        try {
            for (flow in expConfig.flowConfig.flows) {
                //is this node mentioned in the input names of any flow, that is it is connecting to another flow
                //if so the that node needs to be added to the outlet list
                flow.inputNames.filter{x -> x == currentNode.name}
                    .forEach {
                        //now retrieve nodes from the matched flow.flowName
                        outlets.addAll(experimentSources.filter { x -> x.imgSourceName == flow.flowName })
                        outlets.addAll(experimentFlows.filter { x -> x.imgFlowName == flow.flowName })
                        outlets.addAll(experimentSinks.filter { x -> x.imageSinkName == flow.flowName })
                    }
            }

            for (sink in expConfig.sinkConfig.sinks) {
                //is this node name in the input list of any sink, is it connected to a sink
                val sinksAttachedToNode = sink.inputNames.filter{x -> x == currentNode.name}
                sinksAttachedToNode.forEach{
                    outlets.addAll(experimentSources.filter { x -> x.imgSourceName == sink.sinkName })
                    outlets.addAll(experimentFlows.filter { x -> x.imgFlowName == sink.sinkName })
                    outlets.addAll(experimentSinks.filter { x -> x.imageSinkName == sink.sinkName })
                    }
            }
        }
        catch(ex : Exception){
            GUIMain.loggerService.log(Level.SEVERE, "Error populating outlets for graph nodes from json experiment configuration. Error message: ${ex.message}")
            GUIMain.loggerService.log(Level.SEVERE, ex.stackTrace)
        }

        return outlets
    }

    private fun getInlets(currentNode : ExperimentNode, expConfig: ExperimentConfiguration) : ArrayList<ExperimentNode>{
        val inlets = arrayListOf<ExperimentNode>()
        try {
            for (flow in expConfig.flowConfig.flows) {
                if (flow.flowName == currentNode.name) { //Find the flow relating to currentNode
                    //Find any source, flows or sinks that have an inputName corresponding this the current node's input
                    inlets.addAll(experimentSources.filter { x -> x.imgSourceName in flow.inputNames })
                    inlets.addAll(experimentFlows.filter { x -> x.imgFlowName in flow.inputNames })
                    inlets.addAll(experimentSinks.filter { x -> x.imageSinkName in flow.inputNames })
                }
            }

            for (sink in expConfig.sinkConfig.sinks) {
                if (sink.sinkName == currentNode.name) { //Find the flow relating to currentNode
                    //Find any source, flows or sinks that have an inputName corresponding this the current node's input
                    inlets.addAll(experimentSources.filter { x -> x.imgSourceName in sink.inputNames })
                    inlets.addAll(experimentFlows.filter { x -> x.imgFlowName in sink.inputNames })
                    inlets.addAll(experimentSinks.filter { x -> x.imageSinkName in sink.inputNames })
                }
            }
        }
        catch (ex : Exception){
            GUIMain.loggerService.log(Level.SEVERE, "Error populating inlets for graph nodes from json experiment configuration. Error message: ${ex.message}")
            GUIMain.loggerService.log(Level.SEVERE, ex.stackTrace)
        }

        return inlets
    }
}