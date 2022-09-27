package uk.co.strimm.streams

import akka.NotUsed
import akka.actor.ActorRef
import akka.actor.PoisonPill
import akka.stream.ClosedShape
import akka.stream.Graph
import akka.stream.OverflowStrategy
import akka.stream.javadsl.*
import akka.stream.javadsl.Flow
import akka.stream.javadsl.Sink
import akka.stream.javadsl.Source
import javafx.scene.paint.Color
import net.imagej.overlay.EllipseOverlay
import net.imagej.overlay.Overlay
import org.scijava.util.ColorRGB
import uk.co.strimm.*
import uk.co.strimm.MicroManager.MMCameraDevice
import uk.co.strimm.actors.CameraDataStoreActor
import uk.co.strimm.actors.TraceDataStoreActor
import uk.co.strimm.actors.messages.complete.CompleteCameraDataStoring
import uk.co.strimm.actors.messages.complete.CompleteCameraStreaming
import uk.co.strimm.actors.messages.complete.CompleteStreamingTraceROI
import uk.co.strimm.actors.messages.complete.CompleteTraceDataStoring
import uk.co.strimm.actors.messages.fail.FailCameraDataStoring
import uk.co.strimm.actors.messages.fail.FailCameraStreaming
import uk.co.strimm.actors.messages.fail.FailStreamingTraceROI
import uk.co.strimm.actors.messages.fail.FailTraceDataStoring
import uk.co.strimm.actors.messages.start.*
import uk.co.strimm.actors.messages.stop.TerminateActor
import uk.co.strimm.actors.messages.tell.*
import uk.co.strimm.experiment.*
import uk.co.strimm.gui.CameraWindowPlugin
import uk.co.strimm.gui.GUIMain
import uk.co.strimm.gui.TraceWindowPlugin
import uk.co.strimm.plugins.DockableWindowPlugin
import uk.co.strimm.services.AcquisitionMethodService
import java.time.Duration
import java.util.*
import java.util.logging.Level
import kotlin.math.floor
import kotlin.math.round

class ExperimentStream(val expConfig: ExperimentConfiguration, loadCameraConfig : Boolean){
    var experimentImageSources = arrayListOf<ExperimentImageSource>()
    var experimentTraceSources = arrayListOf<ExperimentTraceSource>()
    var experimentImageImageFlows = arrayListOf<ExperimentImageImageFlow>()
    var experimentImageTraceFlows = arrayListOf<ExperimentImageTraceFlow>()
    var experimentTraceImageFlows = arrayListOf<ExperimentTraceImageFlow>()
    var experimentTraceTraceFlows = arrayListOf<ExperimentTraceTraceFlow>()
    var experimentImageSinks = arrayListOf<ExperimentImageSink>()
    var experimentTraceSinks = arrayListOf<ExperimentTraceSink>()
    var cameraActors = hashMapOf<ActorRef, String>()
    var traceActors = hashMapOf<ActorRef, String>() //ActorRef, DeviceName//TODO use a TraceDevice class when it exists
    val newTraceROIActors = hashMapOf<ActorRef, String>()
    var cameraDataStoreActors = hashMapOf<ActorRef, String>()
    var traceDataStoreActors = hashMapOf<ActorRef, String>()

    //
    var lastImageFlow = hashMapOf<String, STRIMMImage>() ///////////////////
    var numConnectionsSpecified = 0
    var numConnectionsMade = 0
    var experimentName = expConfig.experimentConfigurationName

    var cameraDevices = arrayListOf<CameraDeviceInfo>()

    var stream : RunnableGraph<NotUsed>? = null
    var durationMs = 0
    var numCameras = 0
    var bMultiCam = false
    var timeStart = 0.0
    var isRunning = false

    init {
        println("about to populateListOF CameraDevices")
        populateListOfCameraDevices(expConfig)
        println("finidshf populatecameradevices")
    }
//
//    fun loadMMCameras(loadCameraConfig : Boolean){
//        if(loadCameraConfig) {
//            //Load the device configuration first
//            GUIMain.mmService.loadConfigurationFile(expConfig.MMDeviceConfigFile)
//        }
//
//        //Make a note of the camera devices that will be used
//        populateListOfCameraDevices(expConfig)
//
//        for(cameraDevice in cameraDevices){
//            initialiseCamera(cameraDevice)
//        }
//    }

    private fun createStreamGraph(expConfig : ExperimentConfiguration) : Graph<ClosedShape, NotUsed> {
        println("ExperimentStream::createStreamGraph(expConfig : ExperimentConfiguration) ")
        try {
            return (GraphDSL.create () { builder ->
                println("****Building Graph*****")
//                configureTimer()
                durationMs = expConfig.experimentDurationMs
                //Build the graph objects from the experiment config
                populateLists(expConfig, builder)
                println("finished populatelists")

                //Find out what is going where
                experimentImageSources.forEach { x -> x.outs = getOutlets(x, expConfig) }
                experimentTraceSources.forEach { x -> x.outs = getOutlets(x, expConfig) }

                experimentImageImageFlows.forEach { x -> x.ins = getInlets(x, expConfig) }
                experimentImageImageFlows.forEach { x -> x.outs = getOutlets(x, expConfig) }

                experimentImageTraceFlows.forEach { x -> x.ins = getInlets(x, expConfig) }
                experimentImageTraceFlows.forEach { x -> x.outs = getOutlets(x, expConfig) }

                experimentTraceImageFlows.forEach { x -> x.ins = getInlets(x, expConfig) }
                experimentTraceImageFlows.forEach { x -> x.outs = getOutlets(x, expConfig) }

                experimentTraceTraceFlows.forEach { x -> x.ins = getInlets(x, expConfig) }
                experimentTraceTraceFlows.forEach { x -> x.outs = getOutlets(x, expConfig) }

                //Ins for sinks aren't currently used in the logic in buildGraph() (they're covered by the outs in flows)
                // but its still useful to have them
                experimentImageSinks.forEach { x -> x.ins = getInlets(x, expConfig) }
                experimentTraceSinks.forEach { x -> x.ins = getInlets(x, expConfig) }


                //Create broadcast objects if more than one outlet from node
                setBroadcastObjectForSources(builder)
                setBroadcastObjectForFlows(builder)
                setMergeObjectForSinks(builder)
                setMergeObjectForFlows(builder)


                //Remember, the graph has to be closed (everything connected) before runtime
                buildGraph(builder)

                //Print out some details to log
                logInfo()

                GUIMain.loggerService.log(Level.INFO, "Finished building graph")
                checkNumberOfConnections()

                println("***Finished Building Graph*****")
                //TODO should we also validate what connections have gone to where (in addition to checking the number)?
                ClosedShape.getInstance()
            })
        }
        catch(ex: Exception){
            throw ex
        }
    }

    fun configureTimer(){
//        GUIMain.timerService.CreateDummyTimer()
//        GUIMain.loggerService.log(Level.INFO, "Configuring timer")
//        val timer = GUIMain.timerService.createTimer(GUIMain.timerService.getAvailableTimers()!![0], expConfig.hardwareDevices.preinitProperties.deviceName)!!
//        val devProp = timer.timer.getProperty("Device Name")!! as StringTimerProperty
//        devProp.value = devProp.getAllowedValues()[0]
//
//        when (timer.timer.initialise()) {
//            TimerResult.Success, TimerResult.Warning -> {
//                for(traceSource in experimentTraceSources){
//                    if(traceSource.channelName != ""){
//                        val channel = GUIMain.experimentService.getChannelForSource(traceSource.channelName)
//                        when(channel!!.type){
//                            ExperimentConstants.ConfigurationProperties.ANALOGUE_IN_TYPE -> {
//                                val aiChannelDesc = AIChannelDesc(ChannelName(channel.channelName),channel.clockDiv,channel.voltageMax,channel.voltageMin)
//                                val ads = AnalogueDataStream(timer.timer, aiChannelDesc)
//                                GUIMain.timerService.analogueDataStreams.add(ads)
//                            }
//                            ExperimentConstants.ConfigurationProperties.DIGITAL_OUT_TYPE -> {
//                                timer.timer.addDigitalOutput(ChannelName(channel.channelName),channel.clockDiv,ByteArray(1000) {i -> (i % 100 == 0).toByte() })
//                            }
//                            ExperimentConstants.ConfigurationProperties.ANALOGUE_OUT_TYPE -> {
//                                //TODO hardcoded
//                                timer.timer.addAnalogueOutput(timer.timer.getAvailableChannels(ChannelType.AnalogueOut)[0].apply { println(name) },
//                                        1, DoubleArray(1000) { i -> 100*sin(i.toDouble()) })
//                            }
//                            ExperimentConstants.ConfigurationProperties.DIGITAL_IN_TYPE -> {
//                                //TODO
//                            }
//                        }
//                    }
//                }
//            }
//            TimerResult.Error -> { println(timer.timer.getLastError()) }
//        }
    }

    fun createStream(expConfig : ExperimentConfiguration) : RunnableGraph<NotUsed>?{
        println("ExperimentStream::createStream(expConfig : ExperimentConfiguration)")
        //this builds the stream graph - but it is still ultimately controlled by a main actor

        try{
            setNumberOfSpecifiedConnections(expConfig)
            val graph = createStreamGraph(expConfig)
            println("************created graph object - but this is not a runnable graph************")

            val streamGraph = RunnableGraph.fromGraph(graph)
            println("************created a stream graph - this graph is now runnable")

            stream = streamGraph
            return stream
        }
        catch(ex : Exception){
            GUIMain.loggerService.log(Level.SEVERE, "Error building stream graph. Error message: ${ex.message}")
            GUIMain.loggerService.log(Level.SEVERE, ex.stackTrace)
        }

        return null
    }

    /**
     * Tell all camera devices to start acquisition (either preview or live), then begin the stream that has been
     * created
     * @param liveAcquisition Flag if preview mode or live acquisition mode should be used
     */
    fun runStream(liveAcquisition : Boolean){
//        GUIMain.protocolService.WinInitSpeechEngine()
//        GUIMain.protocolService.WinSpeak("Run " + GUIMain.experimentService.expConfig.experimentConfigurationName)
//        if (GUIMain.experimentService.expConfig.experimentMode == "Preview"){
//            GUIMain.protocolService.WinSpeak("Preview mode.  Data will not be stored.")
//
//        }
//        else
//        {
//            GUIMain.protocolService.WinSpeak("Acquisition mode.  Data can be stored")
//        }
       // GUIMain.protocolService.WinShutdownSpeechEngine()
        isRunning = true
        if(GUIMain.experimentService.expConfig.experimentMode != "Preview")
        {
            println("Starting live acquisition")
            GUIMain.experimentService.expConfig.sourceConfig.sources.forEach { x -> x.camera?.StartAcquisition()}
            startStoringData()

            GUIMain.actorService.fileWriterActor.tell(TellCreateFile(), ActorRef.noSender())
            //tell fileWriter about the existence of each cameraDataStoreActor - it is assumed
            //each will produce 1 dataset
            cameraDataStoreActors.forEach { GUIMain.actorService.fileWriterActor.tell(TellNewCameraDataStore(), ActorRef.noSender()) }
            //tell the fileWriter about each traceDataStoreActor
            //it is possible that there are 1+ roi associated with each actor - where the data arrives
            //in a serial manner - so this needs to be changed
            //
            //find out how many datasets are in each traceDataStore
            traceDataStoreActors.forEach {
                //Tell the file writer actor about each trace data store actor, both trace and trace from ROI
                val isTraceFromROIActor = GUIMain.actorService.askTraceActorIfIsTraceROI(it.key)!!
                if (isTraceFromROIActor)
                    GUIMain.actorService.fileWriterActor.tell(TellNewTraceROIDataStore(), ActorRef.noSender())
                else {
                    GUIMain.actorService.fileWriterActor.tell(TellNewTraceDataStore(), ActorRef.noSender())
                }
            }
        }
        else{
            println("Starting preview acquisition")
            GUIMain.expStartStopButton.isEnabled = true
            GUIMain.experimentService.expConfig.sourceConfig.sources.forEach { x -> x.camera?.StartAcquisition()}
        }
/////////////////////////////////////////////////////////////////////
        ///this is when the stream starts
        println("*****************run stream *******************")

        if (bMultiCam) {
            println("startcameramap***")
            GUIMain.protocolService.StartCameraMap()
        } //turn on the MultiCam if used

        stream?.run(GUIMain.actorService.materializer)
        timeStart = GUIMain.protocolService.GetCurrentSystemTime()
        GUIMain.softwareTimerService.setFirstTimeMeasurement()
        GUIMain.acquisitionMethodService.bCamerasAcquire = true
    }

    /**
     * Log information about the number of connections specified and the number of connections actually made
     */
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

    /**
     * Call this method to get the number of connections that should be made for the stream graph. This is based on the
     * experiment config object. numConnectionsSpecified is then compared to numConnectionsMade to check the validity of
     * the created stream graph
     * @param expConfig The experiment configuration object created from the json specification
     */
    private fun setNumberOfSpecifiedConnections(expConfig: ExperimentConfiguration){
        numConnectionsSpecified += expConfig.flowConfig.flows.map { x -> x.inputNames.size }.sum()
        numConnectionsSpecified += expConfig.sinkConfig.sinks.map { x -> x.inputNames.size }.sum()
    }

    /**
     * Method to log a load of basic info about the experiment stream being created
     */
    private fun logInfo(){
        val sb = StringBuilder()
        sb.append("Experiment stream info:\n")
        sb.append("Number of image sources: ${experimentImageSources.size}\n")
        sb.append("Number of trace sources: ${experimentTraceSources.size}\n")
        sb.append("Number of image to image flows: ${experimentImageImageFlows.size}\n")
        sb.append("Number of image to trace flows ${experimentImageTraceFlows.size}\n")
        sb.append("Number of trace to image flows ${experimentTraceImageFlows.size}\n")
        sb.append("Number of trace to trace flows ${experimentTraceTraceFlows.size}\n")
        sb.append("Number of image sinks ${experimentImageSinks.size}\n")
        sb.append("Number of trace sinks: ${experimentTraceSinks.size}\n")

        sb.append("Image sources details:\n")
        experimentImageSources.forEach { x ->
            sb.append("Source name: ${x.imgSourceName}, ")
            sb.append("number of source outlets: ${x.outs.size}\n")
        }
        experimentTraceSources.forEach { x ->
            sb.append("Source name: ${x.traceSourceName}, ")
            sb.append("number of source outlets: ${x.outs.size}\n")
        }
        experimentImageImageFlows.forEach { x ->
            sb.append("Flow name: ${x.imgImgFlowName}, ")
            sb.append("number of flow inlets: ${x.ins.size}, ")
            sb.append("number of flow outlets: ${x.outs.size}, ")
            sb.append("number of fwdOps: ${x.fwdOps.size}\n")
        }
        experimentImageTraceFlows.forEach { x ->
            sb.append("Flow name: ${x.imgTraceFlowName}, ")
            sb.append("number of flow inlets: ${x.ins.size}, ")
            sb.append("number of flow outlets: ${x.outs.size}, ")
            sb.append("number of fwdOps: ${x.fwdOps.size}\n")
        }
        experimentTraceImageFlows.forEach { x ->
            sb.append("Flow name: ${x.traceImgFlowName}, ")
            sb.append("number of flow inlets: ${x.ins.size}, ")
            sb.append("number of flow outlets: ${x.outs.size}, ")
            sb.append("number of fwdOps: ${x.fwdOps.size}\n")
        }
        experimentTraceTraceFlows.forEach { x ->
            sb.append("Flow name: ${x.traceTraceFlowName}, ")
            sb.append("number of flow inlets: ${x.ins.size}, ")
            sb.append("number of flow outlets: ${x.outs.size}, ")
            sb.append("number of fwdOps: ${x.fwdOps.size}\n")
        }
        experimentImageSinks.forEach { x ->
            sb.append("Sink name: ${x.imageSinkName}, ")
            sb.append("number of sink inlets: ${x.ins.size}\n")
        }
        experimentTraceSinks.forEach { x ->
            sb.append("Sink name: ${x.traceSinkName}, ")
            sb.append("number of sink inlets: ${x.ins.size}\n")
        }

        GUIMain.loggerService.log(Level.INFO,sb.toString())
    }

    //region Graph building
    /**
     * build the graph based on sources, flows and sinks as per the Akka API specifications
     * @param builder The graph building object
     */
    private fun buildGraph(builder : GraphDSL.Builder<NotUsed>){
        buildSourceGraphParts(builder)
        buildFlowGraphParts(builder)
    }

    /**
     * This method calls all source graph building methods
     * @param builder The graph building object
     */
    private fun buildSourceGraphParts(builder : GraphDSL.Builder<NotUsed>){
        GUIMain.loggerService.log(Level.INFO, "Building source parts")
        buildImageSourceParts(builder)
        buildTraceSourceParts(builder)
    }

    /**
     * This method calls all flow graph building methods
     * @param builder The graph building object
     */
    private fun buildFlowGraphParts(builder : GraphDSL.Builder<NotUsed>){
        GUIMain.loggerService.log(Level.INFO, "Building flow parts")
        buildImageImageFlowParts(builder)
        buildImageTraceFlowParts(builder)
        buildTraceImageFlowParts(builder)
        buildTraceTraceFlowParts(builder)
    }

    /**
     * Specify the graph connections for image sources. Note that the partial connections (forward ops) are stored
     * with the outlet node (flow or sink) and not with the source
     * @param builder The graph building object
     */
    private fun buildImageSourceParts(builder : GraphDSL.Builder<NotUsed>){
        try {
            for (imageSource in experimentImageSources) {
                if (imageSource.bcastObject != null) {
                    for (outlet in imageSource.outs) { //Go through each outlet of the source
                        when (outlet) {
                            is ExperimentImageImageFlow -> { //Join to a flow (created a ForwardOps object)
                                if (outlet.mergeObject != null) {
                                    val mergeFwdOp = ExperimentImageFwdOp(imageSource.imgSourceName, outlet.imgImgFlowName)
                                    GUIMain.loggerService.log(Level.INFO, "Creating connection from ${imageSource.imgSourceName} (broadcast) viaFanIn ${outlet.imgImgFlowName} (merge)")
                                    mergeFwdOp.fwdOp = builder.from(imageSource.bcastObject).viaFanIn(outlet.mergeObject)
                                    outlet.mergeFwdOp = mergeFwdOp
                                    numConnectionsMade++
                                } else {
                                    val fwdOp = ExperimentImageFwdOp(imageSource.imgSourceName, outlet.imgImgFlowName)
                                    GUIMain.loggerService.log(Level.INFO, "Creating connection from ${imageSource.imgSourceName} (broadcast) via ${outlet.imgImgFlowName}")
                                    fwdOp.fwdOp = builder.from(imageSource.bcastObject).via(outlet.flow)
                                    outlet.fwdOps.add(fwdOp)
                                    numConnectionsMade++
                                }
                            }
                            is ExperimentImageTraceFlow -> {
                                if (outlet.mergeObject != null) {
                                    val mergeFwdOp = ExperimentImageFwdOp(imageSource.imgSourceName, outlet.imgTraceFlowName)
                                    GUIMain.loggerService.log(Level.INFO, "Creating connection from ${imageSource.imgSourceName} (broadcast) viaFanIn ${outlet.imgTraceFlowName} (merge)")
                                    mergeFwdOp.fwdOp = builder.from(imageSource.bcastObject).viaFanIn(outlet.mergeObject)
                                    outlet.mergeFwdOp = mergeFwdOp
                                    numConnectionsMade++
                                } else {
                                    val fwdOp = ExperimentTraceFwdOp(imageSource.imgSourceName, outlet.imgTraceFlowName)
                                    GUIMain.loggerService.log(Level.INFO, "Creating connection from ${imageSource.imgSourceName} (broadcast) via ${outlet.imgTraceFlowName}")
                                    fwdOp.fwdOp = builder.from(imageSource.bcastObject).via(outlet.flow)
                                    outlet.fwdOps.add(fwdOp)
                                    numConnectionsMade++
                                }
                            }
                            is ExperimentTraceImageFlow ->{
                                throw Exception("image source ${imageSource.imgSourceName} is connected to a trace-to-image flow. The flow input type must be the same as the source output type")
                            }
                            is ExperimentTraceTraceFlow ->{
                                throw Exception("image source ${imageSource.imgSourceName} is connected to a trace-to-trace flow. The flow input type must be the same as the source output type")
                            }
                            is ExperimentImageSink -> { //Join to a sink
                                builder.from(imageSource.bcastObject).to(outlet.sink)
                                if (outlet.mergeObject != null) {
                                    GUIMain.loggerService.log(Level.INFO, "Creating connection from ${imageSource.imgSourceName} (broadcast) toFanIn ${outlet.imageSinkName} (merge)")

                                    //Special case that only needs to be applied for image displays
                                    if(outlet.displayOrStore == ExperimentConstants.ConfigurationProperties.DISPLAY){
                                        val bufferFlow = builder.add(Flow.of(STRIMMImage::class.java)
                                               // .buffer(ExperimentConstants.ConfigurationProperties.IMAGE_BUFFER_SIZE, OverflowStrategy.dropTail())
                                                .async()
                                                .named("BufferFlow"))
                                        builder.from(imageSource.bcastObject).via(bufferFlow).toFanIn(outlet.mergeObject)
                                    }
                                    else{
                                        builder.from(imageSource.bcastObject).toFanIn(outlet.mergeObject)
                                    }

                                    numConnectionsMade++
                                } else {
                                    GUIMain.loggerService.log(Level.INFO, "Creating connection from ${imageSource.imgSourceName} (broadcast) to ${outlet.imageSinkName}")

                                    //Special case that only needs to be applied for image displays
                                    if(outlet.displayOrStore == ExperimentConstants.ConfigurationProperties.DISPLAY){
                                        val bufferFlow = builder.add(Flow.of(STRIMMImage::class.java)
                                                //.buffer(ExperimentConstants.ConfigurationProperties.IMAGE_BUFFER_SIZE, OverflowStrategy.dropTail())
                                                .async()
                                                .named("BufferFlow"))

                                        builder.from(imageSource.bcastObject).via(bufferFlow).to(outlet.sink)
                                    }
                                    else{
                                        builder.from(imageSource.bcastObject).to(outlet.sink)
                                    }

                                    numConnectionsMade++
                                }
                            }
                        }
                    }
                } else {
                    for (outlet in imageSource.outs) { //Go through each outlet of the source
                        when (outlet) {
                            is ExperimentImageImageFlow -> { //Join to a flow (created a ForwardOps object)
                                if(outlet.mergeObject != null){
                                    val fwdOp = ExperimentImageFwdOp(imageSource.imgSourceName, outlet.imgImgFlowName)
                                    GUIMain.loggerService.log(Level.INFO, "Creating connection from ${imageSource.imgSourceName} viaFanIn ${outlet.imgImgFlowName} (merge)")
                                    fwdOp.fwdOp = builder.from(imageSource.source).viaFanIn(outlet.mergeObject)
                                    outlet.mergeFwdOp = fwdOp
                                    numConnectionsMade++
                                }
                                else {
                                    val fwdOp = ExperimentImageFwdOp(imageSource.imgSourceName, outlet.imgImgFlowName)
                                    GUIMain.loggerService.log(Level.INFO, "Creating connection from ${imageSource.imgSourceName} via ${outlet.imgImgFlowName}")
                                    fwdOp.fwdOp = builder.from(imageSource.source).via(outlet.flow)
                                    outlet.fwdOps.add(fwdOp)
                                    numConnectionsMade++
                                }
                            }
                            is ExperimentImageTraceFlow -> {
                                if (outlet.mergeObject != null) {
                                    val mergeFwdOp = ExperimentImageFwdOp(imageSource.imgSourceName, outlet.imgTraceFlowName)
                                    GUIMain.loggerService.log(Level.INFO, "Creating connection from ${imageSource.imgSourceName} viaFanIn ${outlet.imgTraceFlowName} (merge)")
                                    mergeFwdOp.fwdOp = builder.from(imageSource.source).viaFanIn(outlet.mergeObject)
                                    outlet.mergeFwdOp = mergeFwdOp
                                    numConnectionsMade++
                                } else {
                                    val fwdOp = ExperimentTraceFwdOp(imageSource.imgSourceName, outlet.imgTraceFlowName)
                                    GUIMain.loggerService.log(Level.INFO, "Creating connection from ${imageSource.imgSourceName} via ${outlet.imgTraceFlowName}")
                                    fwdOp.fwdOp = builder.from(imageSource.source).via(outlet.flow)
                                    outlet.fwdOps.add(fwdOp)
                                    numConnectionsMade++
                                }
                            }
                            is ExperimentTraceImageFlow ->{
                                throw Exception("image source ${imageSource.imgSourceName} is connected to a trace-to-image flow. The flow input type must be the same as the source output type")
                            }
                            is ExperimentTraceTraceFlow ->{
                                throw Exception("image source ${imageSource.imgSourceName} is connected to a trace-to-trace flow. The flow input type must be the same as the source output type")
                            }
                            is ExperimentImageSink -> {
                                if (outlet.mergeObject != null) {
                                    GUIMain.loggerService.log(Level.INFO, "Creating connection from ${imageSource.imgSourceName} toFanIn ${outlet.imageSinkName} (merge)")

                                    //Special case that only needs to be applied for image displays
                                    if(outlet.displayOrStore == ExperimentConstants.ConfigurationProperties.DISPLAY){
                                        val bufferFlow = builder.add(Flow.of(STRIMMImage::class.java)
                                                //.buffer(ExperimentConstants.ConfigurationProperties.IMAGE_BUFFER_SIZE, OverflowStrategy.dropTail())
                                                .async()
                                                .named("BufferFlow"))
                                        builder.from(imageSource.source).via(bufferFlow).toFanIn(outlet.mergeObject)
                                    }
                                    else{
                                        builder.from(imageSource.source).toFanIn(outlet.mergeObject)
                                    }

                                    numConnectionsMade++
                                } else {
                                    GUIMain.loggerService.log(Level.INFO, "Creating connection from ${imageSource.imgSourceName} to ${outlet.imageSinkName}")

                                    //Special case that only needs to be applied for image displays
                                    if(outlet.displayOrStore == ExperimentConstants.ConfigurationProperties.DISPLAY){
                                        val bufferFlow = builder.add(Flow.of(STRIMMImage::class.java)
                                                //.buffer(ExperimentConstants.ConfigurationProperties.IMAGE_BUFFER_SIZE, OverflowStrategy.dropTail())
                                                .async()
                                                .named("BufferFlow"))
                                        builder.from(imageSource.source).via(bufferFlow).to(outlet.sink)
                                    }
                                    else{
                                        builder.from(imageSource.source).to(outlet.sink)
                                    }


                                    numConnectionsMade++
                                }
                            }
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

    /**
     * Specify the graph connections for trace sources. Note that the partial connections (forward ops) are stored
     * with the outlet node (flow or sink) and not with the source
     * @param builder The graph building object
     */
    private fun buildTraceSourceParts(builder: GraphDSL.Builder<NotUsed>){
        try {
            for (traceSource in experimentTraceSources) {
                if (traceSource.bcastObject != null) {
                    builder.from(traceSource.source).viaFanOut(traceSource.bcastObject)
                    for (outlet in traceSource.outs) {
                        when (outlet) {
                            is ExperimentTraceImageFlow -> {
                                if (outlet.mergeObject != null) {
                                    val mergeFwdOp = ExperimentTraceFwdOp(traceSource.traceSourceName, outlet.traceImgFlowName)
                                    GUIMain.loggerService.log(Level.INFO, "Creating connection from ${traceSource.traceSourceName} (broadcast) viaFanIn ${outlet.traceImgFlowName} (merge)")
                                    mergeFwdOp.fwdOp = builder.from(traceSource.bcastObject).viaFanIn(outlet.mergeObject)
                                    outlet.mergeFwdOp = mergeFwdOp
                                    numConnectionsMade++
                                } else {
                                    val mergeFwdOp = ExperimentImageFwdOp(traceSource.traceSourceName, outlet.traceImgFlowName)
                                    GUIMain.loggerService.log(Level.INFO, "Creating connection from ${traceSource.traceSourceName} (broadcast) via ${outlet.traceImgFlowName}")
                                    mergeFwdOp.fwdOp = builder.from(traceSource.bcastObject).via(outlet.flow)
                                    outlet.fwdOps.add(mergeFwdOp)
                                    numConnectionsMade++
                                }
                            }
                            is ExperimentTraceTraceFlow -> {
                                if (outlet.mergeObject != null) {
                                    val mergeFwdOp = ExperimentTraceFwdOp(traceSource.traceSourceName, outlet.traceTraceFlowName)
                                    GUIMain.loggerService.log(Level.INFO, "Creating connection from ${traceSource.traceSourceName} (broadcast) viaFanIn ${outlet.traceTraceFlowName} (merge)")
                                    mergeFwdOp.fwdOp = builder.from(traceSource.bcastObject).viaFanIn(outlet.mergeObject)
                                    outlet.mergeFwdOp = mergeFwdOp
                                    numConnectionsMade++
                                } else {
                                    val mergeFwdOp = ExperimentTraceFwdOp(traceSource.traceSourceName, outlet.traceTraceFlowName)
                                    GUIMain.loggerService.log(Level.INFO, "Creating connection from ${traceSource.traceSourceName} (broadcast) via ${outlet.traceTraceFlowName}")
                                    mergeFwdOp.fwdOp = builder.from(traceSource.bcastObject).via(outlet.flow)
                                    outlet.fwdOps.add(mergeFwdOp)
                                    numConnectionsMade++
                                }
                            }
                            is ExperimentImageTraceFlow ->{
                                throw Exception("trace source ${traceSource.traceSourceName} is connected to a image-to-trace flow. The flow input type must be the same as the source output type")
                            }
                            is ExperimentImageImageFlow ->{
                                throw Exception("trace source ${traceSource.traceSourceName} is connected to a image-to-image flow. The flow input type must be the same as the source output type")
                            }
                            is ExperimentTraceSink -> {
                                if(outlet.mergeObject != null){
                                    GUIMain.loggerService.log(Level.INFO, "Creating connection from ${traceSource.traceSourceName} (broadcast) toFanIn ${outlet.traceSinkName} (merge)")
                                    builder.from(traceSource.bcastObject).toFanIn(outlet.mergeObject)
                                    numConnectionsMade++
                                }
                                else{
                                    GUIMain.loggerService.log(Level.INFO, "Creating connection from ${traceSource.traceSourceName} (broadcast) to ${outlet.traceSinkName}")
                                    builder.from(traceSource.bcastObject).to(outlet.sink)
                                    numConnectionsMade++
                                }
                            }
                        }
                    }
                } else {
                    for (outlet in traceSource.outs) { //Go through each outlet of the source
                        when (outlet) {
                            is ExperimentTraceImageFlow -> {
                                if (outlet.mergeObject != null) {
                                    val mergeFwdOp = ExperimentTraceFwdOp(traceSource.traceSourceName, outlet.traceImgFlowName)
                                    GUIMain.loggerService.log(Level.INFO, "Creating connection from ${traceSource.traceSourceName} viaFanIn ${outlet.traceImgFlowName} (merge)")
                                    mergeFwdOp.fwdOp = builder.from(traceSource.source).viaFanIn(outlet.mergeObject)
                                    outlet.mergeFwdOp = mergeFwdOp
                                    numConnectionsMade++
                                } else {
                                    val mergeFwdOp = ExperimentImageFwdOp(traceSource.traceSourceName, outlet.traceImgFlowName)
                                    GUIMain.loggerService.log(Level.INFO, "Creating connection from ${traceSource.traceSourceName} via ${outlet.traceImgFlowName}")
                                    mergeFwdOp.fwdOp = builder.from(traceSource.source).via(outlet.flow)
                                    outlet.fwdOps.add(mergeFwdOp)
                                    numConnectionsMade++
                                }
                            }
                            is ExperimentTraceTraceFlow -> {
                                if (outlet.mergeObject != null) {
                                    val mergeFwdOp = ExperimentTraceFwdOp(traceSource.traceSourceName, outlet.traceTraceFlowName)
                                    GUIMain.loggerService.log(Level.INFO, "Creating connection from ${traceSource.traceSourceName} viaFanIn ${outlet.traceTraceFlowName} (merge)")
                                    mergeFwdOp.fwdOp = builder.from(traceSource.source).viaFanIn(outlet.mergeObject)
                                    outlet.mergeFwdOp = mergeFwdOp
                                    numConnectionsMade++
                                } else {
                                    val mergeFwdOp = ExperimentTraceFwdOp(traceSource.traceSourceName, outlet.traceTraceFlowName)
                                    GUIMain.loggerService.log(Level.INFO, "Creating connection from ${traceSource.traceSourceName} via ${outlet.traceTraceFlowName}")
                                    mergeFwdOp.fwdOp = builder.from(traceSource.source).via(outlet.flow)
                                    outlet.fwdOps.add(mergeFwdOp)
                                    numConnectionsMade++
                                }
                            }
                            is ExperimentImageTraceFlow ->{
                                throw Exception("trace source ${traceSource.traceSourceName} is connected to a image-to-trace flow. The flow input type must be the same as the source output type")
                            }
                            is ExperimentImageImageFlow ->{
                                throw Exception("trace source ${traceSource.traceSourceName} is connected to a image-to-image flow. The flow input type must be the same as the source output type")
                            }
                            is ExperimentTraceSink -> {
                                if(outlet.mergeObject != null){
                                    GUIMain.loggerService.log(Level.INFO, "Creating connection from ${traceSource.traceSourceName} toFanIn ${outlet.traceSinkName} (merge)")
                                    builder.from(traceSource.source).toFanIn(outlet.mergeObject)
                                    numConnectionsMade++
                                }
                                else{
                                    GUIMain.loggerService.log(Level.INFO, "Creating connection from ${traceSource.traceSourceName} to ${outlet.traceSinkName}")
                                    builder.from(traceSource.source).to(outlet.sink)
                                    numConnectionsMade++
                                }
                            }
                        }
                    }
                }
            }
        }
        catch(ex : Exception){
            GUIMain.loggerService.log(Level.SEVERE, "Error building connections for trace sources. Error message: ${ex.message}")
            GUIMain.loggerService.log(Level.SEVERE, ex.stackTrace)
        }
    }

    /**
     * Specify the graph connections for trace-to-image flows
     * @param builder The graph building object
     */
    private fun buildTraceImageFlowParts(builder: GraphDSL.Builder<NotUsed>){
        try {
            for (traceImgFlow in experimentTraceImageFlows) {
                if (traceImgFlow.bcastObject != null) {
                    for (outlet in traceImgFlow.outs) {
                        when (outlet) {
                            is ExperimentImageTraceFlow -> {
                                createBroadcastConnection(traceImgFlow, outlet, builder)
                            }
                            is ExperimentImageImageFlow -> {
                                createBroadcastConnection(traceImgFlow, outlet, builder)
                            }
                            is ExperimentTraceImageFlow ->{
                                throw Exception("trace-to-image flow ${traceImgFlow.traceImgFlowName} is connected to a trace-to-image flow. The second flow input type must be the same as the flow output type")
                            }
                            is ExperimentTraceTraceFlow ->{
                                throw Exception("trace-to-image flow ${traceImgFlow.traceImgFlowName} is connected to a trace-to-trace flow. The second flow input type must be the same as the source output type")
                            }
                            is ExperimentImageSink -> {
                                //Add a new fwd op for the connection from the flow to the sink
                                //However a connection from a flow to a sink is terminal so we won't actually create
                                //the fwdOp object. We only create an ExperimentFwdOp object to keep a record of the
                                //connection
                                val newFwdOp = ExperimentImageFwdOp(traceImgFlow.traceImgFlowName, outlet.imageSinkName)

                                if(outlet.mergeObject != null){
                                    GUIMain.loggerService.log(Level.INFO, "Creating connection from ${traceImgFlow.traceImgFlowName} (broadcast) toFanIn ${outlet.imageSinkName} (merge)")

                                    //Special case that only needs to be applied for image displays
                                    if(outlet.displayOrStore == ExperimentConstants.ConfigurationProperties.DISPLAY){
                                        val bufferFlow = builder.add(Flow.of(STRIMMImage::class.java)
                                                //.buffer(ExperimentConstants.ConfigurationProperties.IMAGE_BUFFER_SIZE, OverflowStrategy.dropTail())
                                                .async()
                                                .named("BufferFlow"))
                                        builder.from(traceImgFlow.bcastObject).via(bufferFlow).toFanIn(outlet.mergeObject)
                                    }
                                    else{
                                        builder.from(traceImgFlow.bcastObject).toFanIn(outlet.mergeObject)
                                    }

                                    numConnectionsMade++
                                }
                                else{
                                    GUIMain.loggerService.log(Level.INFO, "Creating connection from ${traceImgFlow.traceImgFlowName} (broadcast) to ${outlet.imageSinkName}")

                                    //Special case that only needs to be applied for image displays
                                    if(outlet.displayOrStore == ExperimentConstants.ConfigurationProperties.DISPLAY){
                                        val bufferFlow = builder.add(Flow.of(STRIMMImage::class.java)
                                                //.buffer(ExperimentConstants.ConfigurationProperties.IMAGE_BUFFER_SIZE, OverflowStrategy.dropTail())
                                                .async()
                                                .named("BufferFlow"))
                                        builder.from(traceImgFlow.bcastObject).via(bufferFlow).to(outlet.sink)
                                    }
                                    else{
                                        builder.from(traceImgFlow.bcastObject).to(outlet.sink)
                                    }

                                    builder.from(traceImgFlow.bcastObject).to(outlet.sink)
                                    numConnectionsMade++
                                }

                                traceImgFlow.fwdOps.add(newFwdOp)
                            }
                        }
                    }
                } else {
                    for (outlet in traceImgFlow.outs) {
                        when (outlet) {
                            is ExperimentImageTraceFlow -> {
                                createConnection(traceImgFlow, outlet, builder)
                            }
                            is ExperimentImageImageFlow -> {
                                createConnection(traceImgFlow, outlet, builder)
                            }
                            is ExperimentTraceImageFlow ->{
                                throw Exception("trace-to-image flow ${traceImgFlow.traceImgFlowName} is connected to a trace-to-image flow. The second flow input type must be the same as the flow output type")
                            }
                            is ExperimentTraceTraceFlow ->{
                                throw Exception("trace-to-image flow ${traceImgFlow.traceImgFlowName} is connected to a trace-to-trace flow. The second flow input type must be the same as the source output type")
                            }
                            is ExperimentImageSink -> {
                                //Add a new fwd op for the connection from the flow to the sink
                                //However a connection from a flow to a sink is terminal so we won't actually create
                                //the fwdOp object. We only create an ExperimentFwdOp object to keep a record of the
                                //connection
                                val newFwdOp = ExperimentImageFwdOp(traceImgFlow.traceImgFlowName, outlet.imageSinkName)

                                if(outlet.mergeObject != null){
                                    GUIMain.loggerService.log(Level.INFO, "Creating connection from ${traceImgFlow.traceImgFlowName} toFanIn ${outlet.imageSinkName} (merge)")
                                    builder.from(traceImgFlow.flow).toFanIn(outlet.mergeObject)
                                    numConnectionsMade++
                                }
                                else{
                                    GUIMain.loggerService.log(Level.INFO, "Creating connection from ${traceImgFlow.traceImgFlowName} to ${outlet.imageSinkName}")
                                    builder.from(traceImgFlow.flow).to(outlet.sink)
                                    numConnectionsMade++
                                }

                                traceImgFlow.fwdOps.add(newFwdOp)
                            }
                        }
                    }
                }
            }
        }
        catch(ex : Exception){
            GUIMain.loggerService.log(Level.SEVERE, "Error building connections for trace to image flows. Error message: ${ex.message}")
            GUIMain.loggerService.log(Level.SEVERE, ex.stackTrace)
        }
    }

    /**
     * Specify the graph connections for trace-to-trace flows
     * @param builder The graph building object
     */
    private fun buildTraceTraceFlowParts(builder: GraphDSL.Builder<NotUsed>){
        try {
            for (traceTraceFlow in experimentTraceTraceFlows) {
                if (traceTraceFlow.bcastObject != null) {
                    for (outlet in traceTraceFlow.outs) {
                        when (outlet) {
                            is ExperimentTraceImageFlow -> {
                                createBroadcastConnection(traceTraceFlow, outlet, builder)
                            }
                            is ExperimentTraceTraceFlow -> {
                                createBroadcastConnection(traceTraceFlow, outlet, builder)
                            }
                            is ExperimentImageImageFlow ->{
                                throw Exception("trace-to-trace flow ${traceTraceFlow.traceTraceFlowName} is connected to a image-to-image flow. The second flow input type must be the same as the flow output type")
                            }
                            is ExperimentImageTraceFlow ->{
                                throw Exception("trace-to-trace flow ${traceTraceFlow.traceTraceFlowName} is connected to a image-to-trace flow. The second flow input type must be the same as the source output type")
                            }
                            is ExperimentTraceSink -> {
                                //Add a new fwd op for the connection from the flow to the sink
                                //However a connection from a flow to a sink is terminal so we won't actually create
                                //the fwdOp object. We only create an ExperimentFwdOp object to keep a record of the
                                //connection
                                val newFwdOp = ExperimentTraceFwdOp(traceTraceFlow.traceTraceFlowName, outlet.traceSinkName)

                                if(outlet.mergeObject != null){
                                    GUIMain.loggerService.log(Level.INFO, "Creating connection from ${traceTraceFlow.traceTraceFlowName} (broadcast) toFanIn ${outlet.traceSinkName} (merge)")
                                    builder.from(traceTraceFlow.bcastObject).toFanIn(outlet.mergeObject)
                                    numConnectionsMade++
                                }
                                else{
                                    GUIMain.loggerService.log(Level.INFO, "Creating connection from ${traceTraceFlow.traceTraceFlowName} (broadcast) to ${outlet.traceSinkName}")
                                    builder.from(traceTraceFlow.bcastObject).to(outlet.sink)
                                    numConnectionsMade++
                                }

                                traceTraceFlow.fwdOps.add(newFwdOp)
                            }
                        }
                    }
                } else {
                    for (outlet in traceTraceFlow.outs) {
                        when (outlet) {
                            is ExperimentTraceImageFlow -> {
                                createConnection(traceTraceFlow, outlet, builder)
                            }
                            is ExperimentTraceTraceFlow -> {
                                createConnection(traceTraceFlow, outlet, builder)
                            }
                            is ExperimentImageImageFlow ->{
                                throw Exception("trace-to-trace flow ${traceTraceFlow.traceTraceFlowName} is connected to a image-to-image flow. The second flow input type must be the same as the flow output type")
                            }
                            is ExperimentImageTraceFlow ->{
                                throw Exception("trace-to-trace flow ${traceTraceFlow.traceTraceFlowName} is connected to a image-to-trace flow. The second flow input type must be the same as the source output type")
                            }
                            is ExperimentTraceSink -> {
                                //Add a new fwd op for the connection from the flow to the sink
                                //However a connection from a flow to a sink is terminal so we won't actually create
                                //the fwdOp object. We only create an ExperimentFwdOp object to keep a record of the
                                //connection
                                val newFwdOp = ExperimentTraceFwdOp(traceTraceFlow.traceTraceFlowName, outlet.traceSinkName)

                                if(outlet.mergeObject != null){
                                    GUIMain.loggerService.log(Level.INFO, "Creating connection from ${traceTraceFlow.traceTraceFlowName} toFanIn ${outlet.traceSinkName} (merge)")
                                    builder.from(traceTraceFlow.flow).toFanIn(outlet.mergeObject)
                                    numConnectionsMade++
                                }
                                else{
                                    GUIMain.loggerService.log(Level.INFO, "Creating connection from ${traceTraceFlow.traceTraceFlowName} to ${outlet.traceSinkName}")
                                    builder.from(traceTraceFlow.flow).to(outlet.sink)
                                    numConnectionsMade++
                                }

                                traceTraceFlow.fwdOps.add(newFwdOp)
                            }
                        }
                    }
                }
            }
        }
        catch(ex : Exception){
            GUIMain.loggerService.log(Level.SEVERE, "Error building connections for trace to trace flows. Error message: ${ex.message}")
            GUIMain.loggerService.log(Level.SEVERE, ex.stackTrace)
        }
    }

    /**
     * Specify the graph connections for image-to-trace flows
     * @param builder The graph building object
     */
    private fun buildImageTraceFlowParts(builder : GraphDSL.Builder<NotUsed>){
        try {
            for (imgTraceFlow in experimentImageTraceFlows) {
                if (imgTraceFlow.bcastObject != null) {
                    for (outlet in imgTraceFlow.outs) {
                        when (outlet) {
                            is ExperimentTraceImageFlow -> {
                                createBroadcastConnection(imgTraceFlow, outlet, builder)
                            }
                            is ExperimentTraceTraceFlow -> {
                                createBroadcastConnection(imgTraceFlow, outlet, builder)
                            }
                            is ExperimentImageImageFlow ->{
                                throw Exception("image-to-trace flow ${imgTraceFlow.imgTraceFlowName} is connected to a image-to-image flow. The second flow input type must be the same as the flow output type")
                            }
                            is ExperimentImageTraceFlow ->{
                                throw Exception("image-to-trace flow ${imgTraceFlow.imgTraceFlowName} is connected to a image-to-trace flow. The second flow input type must be the same as the source output type")
                            }
                            is ExperimentTraceSink -> {
                                //Add a new fwd op for the connection from the flow to the sink
                                //However a connection from a flow to a sink is terminal so we won't actually create
                                //the fwdOp object. We only create an ExperimentFwdOp object to keep a record of the
                                //connection
                                val newFwdOp = ExperimentTraceFwdOp(imgTraceFlow.imgTraceFlowName, outlet.traceSinkName)

                                if(outlet.mergeObject != null){
                                    GUIMain.loggerService.log(Level.INFO, "Creating connection from ${imgTraceFlow.imgTraceFlowName} (broadcast) toFanIn ${outlet.traceSinkName} (merge)")
                                    builder.from(imgTraceFlow.bcastObject).toFanIn(outlet.mergeObject)
                                    numConnectionsMade++
                                }
                                else{
                                    GUIMain.loggerService.log(Level.INFO, "Creating connection from ${imgTraceFlow.imgTraceFlowName} (broadcast) to ${outlet.traceSinkName}")
                                    builder.from(imgTraceFlow.bcastObject).to(outlet.sink)
                                    numConnectionsMade++
                                }

                                imgTraceFlow.fwdOps.add(newFwdOp)
                            }
                        }
                    }
                } else {
                    for (outlet in imgTraceFlow.outs) {
                        when (outlet) {
                            is ExperimentTraceImageFlow -> {
                                createConnection(imgTraceFlow, outlet, builder)
                            }
                            is ExperimentTraceTraceFlow -> {
                                createConnection(imgTraceFlow, outlet, builder)
                            }
                            is ExperimentImageImageFlow ->{
                                throw Exception("image-to-trace flow ${imgTraceFlow.imgTraceFlowName} is connected to a image-to-image flow. The second flow input type must be the same as the flow output type")
                            }
                            is ExperimentImageTraceFlow ->{
                                throw Exception("image-to-trace flow ${imgTraceFlow.imgTraceFlowName} is connected to a image-to-trace flow. The second flow input type must be the same as the source output type")
                            }
                            is ExperimentTraceSink -> {
                                //Add a new fwd op for the connection from the flow to the sink
                                //However a connection from a flow to a sink is terminal so we won't actually create
                                //the fwdOp object. We only create an ExperimentFwdOp object to keep a record of the
                                //connection
                                val newFwdOp = ExperimentTraceFwdOp(imgTraceFlow.imgTraceFlowName, outlet.traceSinkName)

                                if(outlet.mergeObject != null){
                                    GUIMain.loggerService.log(Level.INFO, "Creating connection from ${imgTraceFlow.imgTraceFlowName} toFanIn ${outlet.traceSinkName} (merge)")
                                    builder.from(imgTraceFlow.flow).toFanIn(outlet.mergeObject)
                                    numConnectionsMade++
                                }
                                else{
                                    GUIMain.loggerService.log(Level.INFO, "Creating connection from ${imgTraceFlow.imgTraceFlowName} to ${outlet.traceSinkName}")
                                    builder.from(imgTraceFlow.flow).to(outlet.sink)
                                    numConnectionsMade++
                                }

                                imgTraceFlow.fwdOps.add(newFwdOp)
                            }
                        }
                    }
                }
            }
        }
        catch(ex : Exception){
            GUIMain.loggerService.log(Level.SEVERE, "Error building connections for image to trace flows. Error message: ${ex.message}")
            GUIMain.loggerService.log(Level.SEVERE, ex.stackTrace)
        }
    }

    /**
     * Specify the graph connections for image-to-image flows
     * @param builder The graph building object
     */
    private fun buildImageImageFlowParts(builder : GraphDSL.Builder<NotUsed>){
        try {
            for (imgImgFlow in experimentImageImageFlows) {
                if (imgImgFlow.bcastObject != null) {
                    for (outlet in imgImgFlow.outs) {
                        when (outlet) {
                            is ExperimentImageImageFlow -> {
                                createBroadcastConnection(imgImgFlow, outlet, builder)
                            }
                            is ExperimentImageTraceFlow -> {
                                createBroadcastConnection(imgImgFlow, outlet, builder)
                            }
                            is ExperimentTraceImageFlow ->{
                                throw Exception("image-to-image flow ${imgImgFlow.imgImgFlowName} is connected to a trace-to-image flow. The second flow input type must be the same as the flow output type")
                            }
                            is ExperimentTraceTraceFlow ->{
                                throw Exception("image-to-image flow ${imgImgFlow.imgImgFlowName} is connected to a trace-to-image flow. The second flow input type must be the same as the source output type")
                            }
                            is ExperimentImageSink -> {
                                //Add a new fwd op for the connection from the flow to the sink
                                //However a connection from a flow to a sink is terminal so we won't actually create
                                //the fwdOp object. We only create an ExperimentFwdOp object to keep a record of the
                                //connection
                                val newFwdOp = ExperimentImageFwdOp(imgImgFlow.imgImgFlowName, outlet.imageSinkName)

                                if(outlet.mergeObject != null){
                                    GUIMain.loggerService.log(Level.INFO, "Creating connection from ${imgImgFlow.imgImgFlowName} (broadcast) toFanIn ${outlet.imageSinkName} (merge)")

                                    //Special case that only needs to be applied for image displays
                                    if(outlet.displayOrStore == ExperimentConstants.ConfigurationProperties.DISPLAY){
                                        val bufferFlow = builder.add(Flow.of(STRIMMImage::class.java)
                                                //.buffer(ExperimentConstants.ConfigurationProperties.IMAGE_BUFFER_SIZE, OverflowStrategy.dropTail())
                                                .async()
                                                .named("BufferFlow"))
                                        builder.from(imgImgFlow.bcastObject).via(bufferFlow).toFanIn(outlet.mergeObject)
                                    }
                                    else {
                                        builder.from(imgImgFlow.bcastObject).toFanIn(outlet.mergeObject)
                                    }
                                    numConnectionsMade++
                                }
                                else{
                                    GUIMain.loggerService.log(Level.INFO, "Creating connection from ${imgImgFlow.imgImgFlowName} (broadcast) to ${outlet.imageSinkName}")

                                    //Special case that only needs to be applied for image displays
                                    if(outlet.displayOrStore == ExperimentConstants.ConfigurationProperties.DISPLAY){
                                        val bufferFlow = builder.add(Flow.of(STRIMMImage::class.java)
                                                //.buffer(ExperimentConstants.ConfigurationProperties.IMAGE_BUFFER_SIZE, OverflowStrategy.dropTail())
                                                .async()
                                                .named("BufferFlow"))
                                        builder.from(imgImgFlow.bcastObject).via(bufferFlow).to(outlet.sink)
                                    }
                                    else{
                                        builder.from(imgImgFlow.bcastObject).to(outlet.sink)
                                    }

                                    numConnectionsMade++
                                }

                                imgImgFlow.fwdOps.add(newFwdOp)
                            }
                        }
                    }
                } else {
                    for (outlet in imgImgFlow.outs) {
                        when (outlet) {
                            is ExperimentImageImageFlow -> {
                                createConnection(imgImgFlow, outlet, builder)
                            }
                            is ExperimentImageTraceFlow -> {
                                createConnection(imgImgFlow, outlet, builder)
                            }
                            is ExperimentTraceImageFlow ->{
                                throw Exception("image-to-image flow ${imgImgFlow.imgImgFlowName} is connected to a trace-to-image flow. The second flow input type must be the same as the flow output type")
                            }
                            is ExperimentTraceTraceFlow ->{
                                throw Exception("image-to-image flow ${imgImgFlow.imgImgFlowName} is connected to a trace-to-image flow. The second flow input type must be the same as the source output type")
                            }
                            is ExperimentImageSink -> {
                                //Add a new fwd op for the connection from the flow to the sink
                                //However a connection from a flow to a sink is terminal so we won't actually create
                                //the fwdOp object. We only create an ExperimentFwdOp object to keep a record of the
                                //connection
                                val newFwdOp = ExperimentImageFwdOp(imgImgFlow.imgImgFlowName, outlet.imageSinkName)

                                if(outlet.mergeObject != null){
                                    GUIMain.loggerService.log(Level.INFO, "Creating connection from ${imgImgFlow.imgImgFlowName} toFanIn ${outlet.imageSinkName} (merge)")

                                    //Special case that only needs to be applied for image displays
                                    if(outlet.displayOrStore == ExperimentConstants.ConfigurationProperties.DISPLAY){
                                        val bufferFlow = builder.add(Flow.of(STRIMMImage::class.java)
                                                //.buffer(ExperimentConstants.ConfigurationProperties.IMAGE_BUFFER_SIZE, OverflowStrategy.dropTail())
                                                .async()
                                                .named("BufferFlow"))
                                        builder.from(imgImgFlow.flow).via(bufferFlow).toFanIn(outlet.mergeObject)
                                    }
                                    else{
                                        builder.from(imgImgFlow.flow).toFanIn(outlet.mergeObject)
                                    }

                                    numConnectionsMade++
                                }
                                else{
                                    GUIMain.loggerService.log(Level.INFO, "Creating connection from ${imgImgFlow.imgImgFlowName} to ${outlet.imageSinkName}")

                                    //Special case that only needs to be applied for image displays
                                    if(outlet.displayOrStore == ExperimentConstants.ConfigurationProperties.DISPLAY){
                                        val bufferFlow = builder.add(Flow.of(STRIMMImage::class.java)
                                                //.buffer(ExperimentConstants.ConfigurationProperties.IMAGE_BUFFER_SIZE, OverflowStrategy.dropTail())
                                                .async()
                                                .named("BufferFlow"))
                                        builder.from(imgImgFlow.flow).via(bufferFlow).to(outlet.sink)
                                    }
                                    else{
                                        builder.from(imgImgFlow.flow).to(outlet.sink)
                                    }


                                    numConnectionsMade++
                                }

                                imgImgFlow.fwdOps.add(newFwdOp)
                            }
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

    /**
     * Create a connection from an image-to-image flow to an image-to-image flow
     * @param imgImgFlow The originating flow
     * @param outlet The destination flow
     * @builder The graph building object
     */
    private fun createConnection(imgImgFlow : ExperimentImageImageFlow, outlet: ExperimentImageImageFlow, builder: GraphDSL.Builder<NotUsed>){
        val fwdOpsList = imgImgFlow.fwdOps.filter { x -> x.toNodeName == imgImgFlow.name }
        if(fwdOpsList.isNotEmpty()) {
            //This is when the current flow has at least one forward op specified going to the flow
            for (fwdOp in fwdOpsList) {
                if (outlet.mergeObject != null) {
                    GUIMain.loggerService.log(Level.INFO, "Creating connection from ${imgImgFlow.imgImgFlowName} viaFanIn ${outlet.imgImgFlowName} (merge)")
                    fwdOp.fwdOp?.viaFanIn(outlet.mergeObject)
                    numConnectionsMade++
                } else {
                    GUIMain.loggerService.log(Level.INFO, "Creating connection from ${imgImgFlow.imgImgFlowName} via ${outlet.imgImgFlowName}")
                    fwdOp.fwdOp?.via(outlet.flow)
                    numConnectionsMade++
                }
            }
        } else {
            //Create a new forward op if none exist between flow and outlet
            val newFwdOp = ExperimentImageFwdOp(imgImgFlow.imgImgFlowName, outlet.imgImgFlowName)

            if (outlet.mergeObject != null) {
                GUIMain.loggerService.log(Level.INFO, "Creating connection from ${imgImgFlow.imgImgFlowName} viaFanIn ${outlet.imgImgFlowName} (merge)")
                builder.from(imgImgFlow.flow).viaFanIn(outlet.mergeObject)
                numConnectionsMade++
            } else {
                GUIMain.loggerService.log(Level.INFO, "Creating connection from ${imgImgFlow.imgImgFlowName} via ${outlet.imgImgFlowName}")
                newFwdOp.fwdOp = builder.from(imgImgFlow.flow).via(outlet.flow)
                numConnectionsMade++
            }

            outlet.fwdOps.add(newFwdOp)
        }
    }

    /**
     * Create a connection from an image-to-image flow to an image-to-trace flow
     * @param imgImgFlow The originating flow
     * @param outlet The destination flow
     * @builder The graph building object
     */
    private fun createConnection(imgImgFlow : ExperimentImageImageFlow, outlet: ExperimentImageTraceFlow, builder: GraphDSL.Builder<NotUsed>){
        val fwdOpsList = imgImgFlow.fwdOps.filter { x -> x.toNodeName == imgImgFlow.name }
        if(fwdOpsList.isNotEmpty()) {
            for (fwdOp in fwdOpsList) {
                if (outlet.mergeObject != null) {
                    GUIMain.loggerService.log(Level.INFO, "Creating connection from ${imgImgFlow.imgImgFlowName} viaFanIn ${outlet.imgTraceFlowName} (merge)")
                    fwdOp.fwdOp?.viaFanIn(outlet.mergeObject)
                    numConnectionsMade++
                } else {
                    GUIMain.loggerService.log(Level.INFO, "Creating connection from ${imgImgFlow.imgImgFlowName} via ${outlet.imgTraceFlowName}")
                    fwdOp.fwdOp?.via(outlet.flow)
                    numConnectionsMade++
                }
            }
        } else {
            val newFwdOp = ExperimentTraceFwdOp(imgImgFlow.imgImgFlowName, outlet.imgTraceFlowName)

            if (outlet.mergeObject != null) {
                GUIMain.loggerService.log(Level.INFO, "Creating connection from ${imgImgFlow.imgImgFlowName} viaFanIn ${outlet.imgTraceFlowName} (merge)")
                builder.from(imgImgFlow.flow).viaFanIn(outlet.mergeObject)
                numConnectionsMade++
            } else {
                GUIMain.loggerService.log(Level.INFO, "Creating connection from ${imgImgFlow.imgImgFlowName} via ${outlet.imgTraceFlowName}")
                newFwdOp.fwdOp = builder.from(imgImgFlow.flow).via(outlet.flow)
                numConnectionsMade++
            }

            outlet.fwdOps.add(newFwdOp)
        }
    }

    /**
     * Create a connection from an trace-to-image flow to an image-to-image flow
     * @param traceImgFlow The originating flow
     * @param outlet The destination flow
     * @builder The graph building object
     */
    private fun createConnection(traceImgFlow : ExperimentTraceImageFlow, outlet: ExperimentImageImageFlow, builder: GraphDSL.Builder<NotUsed>){
        val fwdOpsList = traceImgFlow.fwdOps.filter { x -> x.toNodeName == traceImgFlow.name }
        if(fwdOpsList.isNotEmpty()) {
            for (fwdOp in fwdOpsList) {
                if (outlet.mergeObject != null) {
                    GUIMain.loggerService.log(Level.INFO, "Creating connection from ${traceImgFlow.traceImgFlowName} viaFanIn ${outlet.imgImgFlowName} (merge)")
                    fwdOp.fwdOp?.viaFanIn(outlet.mergeObject)
                    numConnectionsMade++
                } else {
                    GUIMain.loggerService.log(Level.INFO, "Creating connection from ${traceImgFlow.traceImgFlowName} via ${outlet.imgImgFlowName}")
                    fwdOp.fwdOp?.via(outlet.flow)
                    numConnectionsMade++
                }
            }
        } else {
            val newFwdOp = ExperimentImageFwdOp(traceImgFlow.traceImgFlowName, outlet.imgImgFlowName)

            if (outlet.mergeObject != null) {
                GUIMain.loggerService.log(Level.INFO, "Creating connection from ${traceImgFlow.traceImgFlowName} viaFanIn ${outlet.imgImgFlowName} (merge)")
                builder.from(traceImgFlow.flow).viaFanIn(outlet.mergeObject)
                numConnectionsMade++
            } else {
                GUIMain.loggerService.log(Level.INFO, "Creating connection from ${traceImgFlow.traceImgFlowName} via ${outlet.imgImgFlowName}")
                newFwdOp.fwdOp = builder.from(traceImgFlow.flow).via(outlet.flow)
                numConnectionsMade++
            }

            outlet.fwdOps.add(newFwdOp)
        }
    }

    /**
     * Create a connection from an trace-to-image flow to an image-to-trace flow
     * @param traceImgFlow The originating flow
     * @param outlet The destination flow
     * @builder The graph building object
     */
    private fun createConnection(traceImgFlow : ExperimentTraceImageFlow, outlet: ExperimentImageTraceFlow, builder: GraphDSL.Builder<NotUsed>){
        val fwdOpsList = traceImgFlow.fwdOps.filter { x -> x.toNodeName == traceImgFlow.name }
        if(fwdOpsList.isNotEmpty()) {
            for (fwdOp in fwdOpsList) {
                if (outlet.mergeObject != null) {
                    GUIMain.loggerService.log(Level.INFO, "Creating connection from ${traceImgFlow.traceImgFlowName} viaFanIn ${outlet.imgTraceFlowName}")
                    fwdOp.fwdOp?.viaFanIn(outlet.mergeObject)
                    numConnectionsMade++
                } else {
                    GUIMain.loggerService.log(Level.INFO, "Creating connection from ${traceImgFlow.traceImgFlowName} via ${outlet.imgTraceFlowName}")
                    fwdOp.fwdOp?.via(outlet.flow)
                    numConnectionsMade++
                }
            }
        } else {
            val newFwdOp = ExperimentTraceFwdOp(traceImgFlow.traceImgFlowName, outlet.imgTraceFlowName)

            if (outlet.mergeObject != null) {
                GUIMain.loggerService.log(Level.INFO, "Creating connection from ${traceImgFlow.traceImgFlowName} viaFanIn ${outlet.imgTraceFlowName} (merge)")
                builder.from(traceImgFlow.flow).viaFanIn(outlet.mergeObject)
                numConnectionsMade++
            } else {
                GUIMain.loggerService.log(Level.INFO, "Creating connection from ${traceImgFlow.traceImgFlowName} via ${outlet.imgTraceFlowName}")
                newFwdOp.fwdOp = builder.from(traceImgFlow.flow).via(outlet.flow)
                numConnectionsMade++
            }

            outlet.fwdOps.add(newFwdOp)
        }
    }

    /**
     * Create a connection from an image-to-trace flow to an trace-to-trace flow
     * @param imgTraceFlow The originating flow
     * @param outlet The destination flow
     * @builder The graph building object
     */
    private fun createConnection(imgTraceFlow : ExperimentImageTraceFlow, outlet: ExperimentTraceTraceFlow, builder: GraphDSL.Builder<NotUsed>){
        val fwdOpsList = imgTraceFlow.fwdOps.filter { x -> x.toNodeName == imgTraceFlow.name }
        if(fwdOpsList.isNotEmpty()) {
            for (fwdOp in fwdOpsList) {
                if (outlet.mergeObject != null) {
                    GUIMain.loggerService.log(Level.INFO, "Creating connection from ${imgTraceFlow.imgTraceFlowName} viaFanIn ${outlet.traceTraceFlowName} (merge)")
                    fwdOp.fwdOp?.viaFanIn(outlet.mergeObject)
                    numConnectionsMade++
                } else {
                    GUIMain.loggerService.log(Level.INFO, "Creating connection from ${imgTraceFlow.imgTraceFlowName} via ${outlet.traceTraceFlowName}")
                    fwdOp.fwdOp?.via(outlet.flow)
                    numConnectionsMade++
                }
            }
        } else {
            val newFwdOp = ExperimentTraceFwdOp(imgTraceFlow.imgTraceFlowName, outlet.traceTraceFlowName)

            if (outlet.mergeObject != null) {
                GUIMain.loggerService.log(Level.INFO, "Creating connection from ${imgTraceFlow.imgTraceFlowName} viaFanIn ${outlet.traceTraceFlowName} (merge)")
                builder.from(imgTraceFlow.flow).viaFanIn(outlet.mergeObject)
                numConnectionsMade++
            } else {
                GUIMain.loggerService.log(Level.INFO, "Creating connection from ${imgTraceFlow.imgTraceFlowName} via ${outlet.traceTraceFlowName}")
                newFwdOp.fwdOp = builder.from(imgTraceFlow.flow).via(outlet.flow)
                numConnectionsMade++
            }

            outlet.fwdOps.add(newFwdOp)
        }
    }

    /**
     * Create a connection from an image-to-trace flow to an trace-to-image flow
     * @param imgTraceFlow The originating flow
     * @param outlet The destination flow
     * @builder The graph building object
     */
    private fun createConnection(imgTraceFlow : ExperimentImageTraceFlow, outlet: ExperimentTraceImageFlow, builder: GraphDSL.Builder<NotUsed>){
        val fwdOpsList = imgTraceFlow.fwdOps.filter { x -> x.toNodeName == imgTraceFlow.name }
        if(fwdOpsList.isNotEmpty()) {
            for (fwdOp in fwdOpsList) {
                if (outlet.mergeObject != null) {
                    //did not save the fwd op
                    GUIMain.loggerService.log(Level.INFO, "Creating connection from ${imgTraceFlow.imgTraceFlowName} viaFanIn ${outlet.traceImgFlowName} (merge)")
                    fwdOp.fwdOp?.viaFanIn(outlet.mergeObject)
                    numConnectionsMade++
                } else {
                    GUIMain.loggerService.log(Level.INFO, "Creating connection from ${imgTraceFlow.imgTraceFlowName} via ${outlet.traceImgFlowName}")
                    //did not save the fwd op
                    fwdOp.fwdOp?.via(outlet.flow)
                    numConnectionsMade++
                }
            }
        } else {
            //make a new forward op
            val newFwdOp = ExperimentImageFwdOp(imgTraceFlow.imgTraceFlowName, outlet.traceImgFlowName)

            //if an outlet has a mergeObject then it already has a forward op, otherwise it does not and one needs to be made
            if (outlet.mergeObject != null) {
                //this outlet already has a merge object
                //if the outlet has a mergeobject then build a link from the flow to the outlet viaFanIn
                GUIMain.loggerService.log(Level.INFO, "Creating connection from ${imgTraceFlow.imgTraceFlowName} viaFanIn ${outlet.traceImgFlowName} (merge)")
                builder.from(imgTraceFlow.flow).viaFanIn(outlet.mergeObject)
                //why didnt we capture the forwards op here
                //because we already have the mergeFwdOp
                //this path will just add a blank forward op is this deliberate?
                numConnectionsMade++
            } else {
                GUIMain.loggerService.log(Level.INFO, "Creating connection from ${imgTraceFlow.imgTraceFlowName} via ${outlet.traceImgFlowName}")
                newFwdOp.fwdOp = builder.from(imgTraceFlow.flow).via(outlet.flow)
                //make a link from the flow to the outler and then capture the fwd op
                numConnectionsMade++
            }
//save the fwd op
            outlet.fwdOps.add(newFwdOp)
        }
    }

    /**
     * Create a connection from an trace-to-trace flow to an trace-to-trace flow
     * @param traceTraceFlow The originating flow
     * @param outlet The destination flow
     * @builder The graph building object
     */
    private fun createConnection(traceTraceFlow : ExperimentTraceTraceFlow, outlet: ExperimentTraceTraceFlow, builder: GraphDSL.Builder<NotUsed>){
        val fwdOpsList = traceTraceFlow.fwdOps.filter { x -> x.toNodeName == traceTraceFlow.name }
        if(fwdOpsList.isNotEmpty()) {
            for (fwdOp in fwdOpsList) {
                if (outlet.mergeObject != null) {
                    GUIMain.loggerService.log(Level.INFO, "Creating connection from ${traceTraceFlow.traceTraceFlowName} viaFanIn ${outlet.traceTraceFlowName} (merge)")
                    fwdOp.fwdOp?.viaFanIn(outlet.mergeObject)
                    numConnectionsMade++
                } else {
                    GUIMain.loggerService.log(Level.INFO, "Creating connection from ${traceTraceFlow.traceTraceFlowName} via ${outlet.traceTraceFlowName}")
                    fwdOp.fwdOp?.via(outlet.flow)
                    numConnectionsMade++
                }
            }
        } else {
            val newFwdOp = ExperimentTraceFwdOp(traceTraceFlow.traceTraceFlowName, outlet.traceTraceFlowName)

            if (outlet.mergeObject != null) {
                GUIMain.loggerService.log(Level.INFO, "Creating connection from ${traceTraceFlow.traceTraceFlowName} viaFanIn ${outlet.traceTraceFlowName} (merge)")
                builder.from(traceTraceFlow.flow).viaFanIn(outlet.mergeObject)
                numConnectionsMade++
            } else {
                GUIMain.loggerService.log(Level.INFO, "Creating connection from ${traceTraceFlow.traceTraceFlowName} via ${outlet.traceTraceFlowName}")
                newFwdOp.fwdOp = builder.from(traceTraceFlow.flow).via(outlet.flow)
                numConnectionsMade++
            }

            outlet.fwdOps.add(newFwdOp)
        }
    }

    /**
     * Create a connection from an trace-to-trace flow to an trace-to-image flow
     * @param traceTraceFlow The originating flow
     * @param outlet The destination flow
     * @builder The graph building object
     */
    private fun createConnection(traceTraceFlow : ExperimentTraceTraceFlow, outlet: ExperimentTraceImageFlow, builder: GraphDSL.Builder<NotUsed>){
        val fwdOpsList = traceTraceFlow.fwdOps.filter { x -> x.toNodeName == traceTraceFlow.name }
        if(fwdOpsList.isNotEmpty()) {
            for (fwdOp in fwdOpsList) {
                if (outlet.mergeObject != null) {
                    GUIMain.loggerService.log(Level.INFO, "Creating connection from ${traceTraceFlow.traceTraceFlowName} viaFanIn ${outlet.traceImgFlowName} (merge)")
                    fwdOp.fwdOp?.viaFanIn(outlet.mergeObject)
                    numConnectionsMade++
                } else {
                    GUIMain.loggerService.log(Level.INFO, "Creating connection from ${traceTraceFlow.traceTraceFlowName} via ${outlet.traceImgFlowName}")
                    fwdOp.fwdOp?.via(outlet.flow)
                    numConnectionsMade++
                }
            }
        } else {
            val newFwdOp = ExperimentImageFwdOp(traceTraceFlow.traceTraceFlowName, outlet.traceImgFlowName)

            if (outlet.mergeObject != null) {
                GUIMain.loggerService.log(Level.INFO, "Creating connection from ${traceTraceFlow.traceTraceFlowName} viaFanIn ${outlet.traceImgFlowName} (merge)")
                builder.from(traceTraceFlow.flow).viaFanIn(outlet.mergeObject)
                numConnectionsMade++
            } else {
                GUIMain.loggerService.log(Level.INFO, "Creating connection from ${traceTraceFlow.traceTraceFlowName} via ${outlet.traceImgFlowName}")
                newFwdOp.fwdOp = builder.from(traceTraceFlow.flow).via(outlet.flow)
                numConnectionsMade++
            }

            outlet.fwdOps.add(newFwdOp)
        }
    }

    /**
     * Create a broadcast connection from an image-to-image flow to an image-to-image flow
     * @param imgImgFlow The originating flow
     * @param outlet The destination flow
     * @builder The graph building object
     */
    private fun createBroadcastConnection(imgImgFlow : ExperimentImageImageFlow, outlet: ExperimentImageImageFlow, builder: GraphDSL.Builder<NotUsed>){
        if (outlet.mergeObject != null) {
            //using a bcastFwdOp
            //This condition represents a broadcast connection going to a merge connection
            GUIMain.loggerService.log(Level.INFO, "Creating connection from ${imgImgFlow.imgImgFlowName} (broadcast) viaFanIn ${outlet.imgImgFlowName} (merge)")
            imgImgFlow.bcastFwdOp?.fwdOp?.viaFanIn(outlet.mergeObject)
            numConnectionsMade++
        } else {
            //Ths condition represents a broadcast connection going to a normal connection
            GUIMain.loggerService.log(Level.INFO, "Creating connection from ${imgImgFlow.imgImgFlowName} (broadcast) via ${outlet.imgImgFlowName}")
            //why not using bcastFwdOp ?
            builder.from(imgImgFlow.bcastObject).via(outlet.flow)
            numConnectionsMade++
        }
    }

    /**
     * Create a broadcast connection from an image-to-image flow to an image-to-trace flow
     * @param imgImgFlow The originating flow
     * @param outlet The destination flow
     * @builder The graph building object
     */
    private fun createBroadcastConnection(imgImgFlow : ExperimentImageImageFlow, outlet: ExperimentImageTraceFlow, builder: GraphDSL.Builder<NotUsed>){
        if (outlet.mergeObject != null) {
            //This condition represents a broadcast connection going to a merge connection
            GUIMain.loggerService.log(Level.INFO, "Creating connection from ${imgImgFlow.imgImgFlowName} (broadcast) viaFanIn ${outlet.imgTraceFlowName} (merge)")
            imgImgFlow.bcastFwdOp?.fwdOp?.viaFanIn(outlet.mergeObject)
            numConnectionsMade++
        } else {
            //Ths condition represents a broadcast connection going to a normal connection
            GUIMain.loggerService.log(Level.INFO, "Creating connection from ${imgImgFlow.imgImgFlowName} (broadcast) via ${outlet.imgTraceFlowName}")
            builder.from(imgImgFlow.bcastObject).via(outlet.flow)
            numConnectionsMade++
        }
    }

    /**
     * Create a broadcast connection from an trace-to-image flow to an image-to-image flow
     * @param traceImgFlow The originating flow
     * @param outlet The destination flow
     * @builder The graph building object
     */
    private fun createBroadcastConnection(traceImgFlow : ExperimentTraceImageFlow, outlet: ExperimentImageImageFlow, builder: GraphDSL.Builder<NotUsed>){
        if (outlet.mergeObject != null) {
            //This condition represents a broadcast connection going to a merge connection
            GUIMain.loggerService.log(Level.INFO, "Creating connection from ${traceImgFlow.traceImgFlowName} (broadcast) viaFanIn ${outlet.imgImgFlowName} (merge)")
            traceImgFlow.bcastFwdOp?.fwdOp?.viaFanIn(outlet.mergeObject)
            numConnectionsMade++
        } else {
            //Ths condition represents a broadcast connection going to a normal connection
            GUIMain.loggerService.log(Level.INFO, "Creating connection from ${traceImgFlow.traceImgFlowName} (broadcast) viaFanOut ${outlet.imgImgFlowName}")
            builder.from(traceImgFlow.bcastObject).via(outlet.flow)
            numConnectionsMade++
        }
    }

    /**
     * Create a broadcast connection from an trace-to-image flow to an image-to-trace flow
     * @param traceImgFlow The originating flow
     * @param outlet The destination flow
     * @builder The graph building object
     */
    private fun createBroadcastConnection(traceImgFlow : ExperimentTraceImageFlow, outlet: ExperimentImageTraceFlow, builder: GraphDSL.Builder<NotUsed>){
        if (outlet.mergeObject != null) {
            //This condition represents a broadcast connection going to a merge connection
            GUIMain.loggerService.log(Level.INFO, "Creating connection from ${traceImgFlow.traceImgFlowName} (broadcast) viaFanIn ${outlet.imgTraceFlowName} (merge)")
            traceImgFlow.bcastFwdOp?.fwdOp?.viaFanIn(outlet.mergeObject)
            numConnectionsMade++
        } else {
            //Ths condition represents a broadcast connection going to a normal connection
            GUIMain.loggerService.log(Level.INFO, "Creating connection from ${traceImgFlow.traceImgFlowName} (broadcast) via ${outlet.imgTraceFlowName}")
            builder.from(traceImgFlow.bcastObject).via(outlet.flow)
            numConnectionsMade++
        }
    }

    /**
     * Create a broadcast connection from an image-to-trace flow to an trace-to-trace flow
     * @param imgTraceFlow The originating flow
     * @param outlet The destination flow
     * @builder The graph building object
     */
    private fun createBroadcastConnection(imgTraceFlow : ExperimentImageTraceFlow, outlet: ExperimentTraceTraceFlow, builder: GraphDSL.Builder<NotUsed>){
            if (outlet.mergeObject != null) {
                //This condition represents a broadcast connection going to a merge connection
                GUIMain.loggerService.log(Level.INFO, "Creating connection from ${imgTraceFlow.imgTraceFlowName} (broadcast) viaFanIn ${outlet.traceTraceFlowName} (merge)")
                imgTraceFlow.bcastFwdOp?.fwdOp?.viaFanIn(outlet.mergeObject)
                numConnectionsMade++
            } else {
                //Ths condition represents a broadcast connection going to a normal connection
                GUIMain.loggerService.log(Level.INFO, "Creating connection from ${imgTraceFlow.imgTraceFlowName} (broadcast) via ${outlet.traceTraceFlowName}")
                builder.from(imgTraceFlow.bcastObject).via(outlet.flow)
                numConnectionsMade++
            }
    }

    /**
     * Create a broadcast connection from an image-to-trace flow to an trace-to-image flow
     * @param imgTraceFlow The originating flow
     * @param outlet The destination flow
     * @builder The graph building object
     */
    private fun createBroadcastConnection(imgTraceFlow : ExperimentImageTraceFlow, outlet: ExperimentTraceImageFlow, builder: GraphDSL.Builder<NotUsed>){
        if (outlet.mergeObject != null) {
            //This condition represents a broadcast connection going to a merge connection
            GUIMain.loggerService.log(Level.INFO, "Creating connection from ${imgTraceFlow.imgTraceFlowName} (broadcast) viaFanIn ${outlet.traceImgFlowName} (merge)")
            imgTraceFlow.bcastFwdOp?.fwdOp?.viaFanIn(outlet.mergeObject)
            numConnectionsMade++
        } else {
            //Ths condition represents a broadcast connection going to a normal connection
            GUIMain.loggerService.log(Level.INFO, "Creating connection from ${imgTraceFlow.imgTraceFlowName} (broadcast) via ${outlet.traceImgFlowName}")
            builder.from(imgTraceFlow.bcastObject).via(outlet.flow)
            numConnectionsMade++
        }
    }

    /**
     * Create a broadcast connection from an trace-to-trace flow to an trace-to-trace flow
     * @param traceTraceFlow The originating flow
     * @param outlet The destination flow
     * @builder The graph building object
     */
    private fun createBroadcastConnection(traceTraceFlow : ExperimentTraceTraceFlow, outlet: ExperimentTraceTraceFlow, builder: GraphDSL.Builder<NotUsed>){
        if (outlet.mergeObject != null) {
            //This condition represents a broadcast connection going to a merge connection
            GUIMain.loggerService.log(Level.INFO, "Creating connection from ${traceTraceFlow.traceTraceFlowName} (broadcast) viaFanIn ${outlet.traceTraceFlowName} (merge)")
            traceTraceFlow.bcastFwdOp?.fwdOp?.viaFanIn(outlet.mergeObject)
            numConnectionsMade++
        } else {
            //Ths condition represents a broadcast connection going to a normal connection
            GUIMain.loggerService.log(Level.INFO, "Creating connection from ${traceTraceFlow.traceTraceFlowName} (broadcast) via ${outlet.traceTraceFlowName}")
            builder.from(traceTraceFlow.bcastObject).via(outlet.flow)
            numConnectionsMade++
        }
    }

    /**
     * Create a broadcast connection from an trace-to-trace flow to an trace-to-image flow
     * @param traceTraceFlow The originating flow
     * @param outlet The destination flow
     * @builder The graph building object
     */
    private fun createBroadcastConnection(traceTraceFlow : ExperimentTraceTraceFlow, outlet: ExperimentTraceImageFlow, builder: GraphDSL.Builder<NotUsed>){
        if (outlet.mergeObject != null) {
            //This condition represents a broadcast connection going to a merge connection
            GUIMain.loggerService.log(Level.INFO, "Creating connection from ${traceTraceFlow.traceTraceFlowName} (broadcast) viaFanIn ${outlet.traceImgFlowName} (merge)")
            traceTraceFlow.bcastFwdOp?.fwdOp?.viaFanIn(outlet.mergeObject)
            numConnectionsMade++
        } else {
            //Ths condition represents a broadcast connection going to a normal connection
            GUIMain.loggerService.log(Level.INFO, "Creating connection from ${traceTraceFlow.traceTraceFlowName} (broadcast) via ${outlet.traceImgFlowName}")
            builder.from(traceTraceFlow.bcastObject).via(outlet.flow)
            numConnectionsMade++
        }
    }

    /**
     * If a source has more than one outlet (connection going out from it) then it will need to broadcast. This method
     * will create the necessary broadcast object
     * @param builder The graph building object
     */
    private fun setBroadcastObjectForSources(builder : GraphDSL.Builder<NotUsed>){
        try {
            experimentImageSources.forEach { x ->
                if (x.outs.size > 1) {
                    if (x.outputType.toLowerCase() == ExperimentConstants.ConfigurationProperties.IMAGE_OUTPUT_TYPE.toLowerCase()) {
                        //Create a broadcast object. We also need to specify it in the builder here
                        x.bcastObject = builder.add(Broadcast.create<STRIMMImage>(x.outs.size))

                        //Create a forward op object but both the from and to names will be the same (the flow's) as its a broadcast fwd op
                        val bcastFwdOp = builder.from(x.source).viaFanOut(x.bcastObject)
                        val experimentFwdOp = ExperimentImageFwdOp(x.imgSourceName, x.imgSourceName)
                        experimentFwdOp.fwdOp = bcastFwdOp
                        x.bcastFwdOp = experimentFwdOp
                    }
                }
            }

            experimentTraceSources.forEach { x ->
                if (x.outs.size > 1) {
                    if (x.outputType.toLowerCase() == ExperimentConstants.ConfigurationProperties.TRACE_OUTPUT_TYPE.toLowerCase()) {
                        //Create a broadcast object. We also need to specify it in the builder here
                        x.bcastObject = builder.add(Broadcast.create<List<ArrayList<TraceData>>>(x.outs.size))

                        //Create a forward op object but both the from and to names will be the same (the flow's) as its a broadcast fwd op
                        val bcastFwdOp = builder.from(x.source).viaFanOut(x.bcastObject)
                        val experimentFwdOp = ExperimentTraceFwdOp(x.traceSourceName, x.traceSourceName)
                        experimentFwdOp.fwdOp = bcastFwdOp
                        x.bcastFwdOp = experimentFwdOp
                    }
                }
            }
        }
        catch(ex : Exception){
            GUIMain.loggerService.log(Level.SEVERE, "Error creating broadcast objects for sources. Error message: ${ex.message}")
            GUIMain.loggerService.log(Level.SEVERE, ex.stackTrace)
        }
    }

    /**
     * If a flow has more than one outlet (connection going out from it) then it will need to broadcast. This method
     * will create the necessary broadcast object
     * @param builder The graph building object
     */
    private fun setBroadcastObjectForFlows(builder : GraphDSL.Builder<NotUsed>){
        try {
            experimentImageImageFlows.forEach { x ->
                if (x.outs.size > 1) {
                    if (x.outputType.toLowerCase() == ExperimentConstants.ConfigurationProperties.IMAGE_OUTPUT_TYPE.toLowerCase()) {
                        //Create a broadcast object. We also need to specify it in the builder here
                        x.bcastObject = builder.add(Broadcast.create<STRIMMImage>(x.outs.size))

                        //Create a forward op object but both the from and to names will be the same (the flow's) as its a broadcast fwd op
                        val bcastFwdOp = builder.from(x.flow).viaFanOut(x.bcastObject)
                        val experimentFwdOp = ExperimentImageFwdOp(x.imgImgFlowName, x.imgImgFlowName)
                        experimentFwdOp.fwdOp = bcastFwdOp
                        x.bcastFwdOp = experimentFwdOp
                    }
                }
            }
            experimentImageTraceFlows.forEach { x ->
                if (x.outs.size > 1) {
                    if (x.outputType.toLowerCase() == ExperimentConstants.ConfigurationProperties.TRACE_OUTPUT_TYPE.toLowerCase()) {
                        //Create a broadcast object. We also need to specify it in the builder here
                        x.bcastObject = builder.add(Broadcast.create<List<ArrayList<TraceData>>>(x.outs.size))

                        //Create a forward op object but both the from and to names will be the same (the flow's) as its a broadcast fwd op
                        val bcastFwdOp = builder.from(x.flow).viaFanOut(x.bcastObject)
                        val experimentFwdOp = ExperimentTraceFwdOp(x.imgTraceFlowName, x.imgTraceFlowName)
                        experimentFwdOp.fwdOp = bcastFwdOp
                        x.bcastFwdOp = experimentFwdOp
                    }
                }
            }
            experimentTraceImageFlows.forEach { x ->
                if (x.outs.size > 1) {
                    if (x.outputType.toLowerCase() == ExperimentConstants.ConfigurationProperties.IMAGE_OUTPUT_TYPE.toLowerCase()) {
                        //Create a broadcast object. We also need to specify it in the builder here
                        x.bcastObject = builder.add(Broadcast.create<STRIMMImage>(x.outs.size))

                        //Create a forward op object but both the from and to names will be the same (the flow's) as its a broadcast fwd op
                        val bcastFwdOp = builder.from(x.flow).viaFanOut(x.bcastObject)
                        val experimentFwdOp = ExperimentImageFwdOp(x.traceImgFlowName, x.traceImgFlowName)
                        experimentFwdOp.fwdOp = bcastFwdOp
                        x.bcastFwdOp = experimentFwdOp
                    }
                }
            }
            experimentTraceTraceFlows.forEach { x ->
                if (x.outs.size > 1) {
                    if (x.outputType.toLowerCase() == ExperimentConstants.ConfigurationProperties.TRACE_OUTPUT_TYPE.toLowerCase()) {
                        //Create a broadcast object. We also need to specify it in the builder here
                        x.bcastObject = builder.add(Broadcast.create<List<ArrayList<TraceData>>>(x.outs.size))

                        //Create a forward op object but both the from and to names will be the same (the flow's) as its a broadcast fwd op
                        val bcastFwdOp = builder.from(x.flow).viaFanOut(x.bcastObject)
                        val experimentFwdOp = ExperimentTraceFwdOp(x.traceTraceFlowName, x.traceTraceFlowName)
                        experimentFwdOp.fwdOp = bcastFwdOp
                        x.bcastFwdOp = experimentFwdOp
                    }
                }
            }
        }
        catch (ex : Exception){
            GUIMain.loggerService.log(Level.SEVERE, "Error creating broadcast objects for flows. Error message: ${ex.message}")
            GUIMain.loggerService.log(Level.SEVERE, ex.stackTrace)
        }
    }

    /**
     * If a flow has more than one inlet (connection going into it) then it will need to merge. THis method will create
     * the necessary merge object
     * @param builder The graph building object
     */
    private fun setMergeObjectForSinks(builder : GraphDSL.Builder<NotUsed>){
        try {
            experimentImageSinks.forEach { x ->
                if(x.ins.size > 1){
                    for(inlet in x.ins) {
                        if (x.outputType.toLowerCase() == ExperimentConstants.ConfigurationProperties.IMAGE_INPUT_TYPE.toLowerCase() && x.mergeObject == null) {
                            when(inlet){
                                is ExperimentImageSource ->{
                                    x.mergeObject = builder.add(Merge.create<STRIMMImage>(x.ins.size))

                                    //IMPORTANT NOTE: like broadcast, any merge object needs to have its forward op (builder.from(mergeObject).to(something)
                                    //This won't be used later but must be done now
                                    builder.from(x.mergeObject).to(x.sink)

                                    val experimentFwdOp = ExperimentImageFwdOp(inlet.imgSourceName, x.imageSinkName)
                                    x.mergeFwdOp = experimentFwdOp
                                }
                                is ExperimentImageImageFlow -> {
                                    x.mergeObject = builder.add(Merge.create<STRIMMImage>(x.ins.size))

                                    //IMPORTANT NOTE: like broadcast, any merge object needs to have its forward op (builder.from(mergeObject).to(something)
                                    //This won't be used later but must be done now
                                    builder.from(x.mergeObject).to(x.sink)

                                    val experimentFwdOp = ExperimentImageFwdOp(inlet.imgImgFlowName, x.imageSinkName)
                                    x.mergeFwdOp = experimentFwdOp
                                }
                                is ExperimentTraceImageFlow -> {
                                    x.mergeObject = builder.add(Merge.create<STRIMMImage>(x.ins.size))

                                    //IMPORTANT NOTE: like broadcast, any merge object needs to have its forward op (builder.from(mergeObject).to(something)
                                    //This won't be used later but must be done now
                                    builder.from(x.mergeObject).to(x.sink)

                                    val experimentFwdOp = ExperimentImageFwdOp(inlet.traceImgFlowName, x.imageSinkName)
                                    x.mergeFwdOp = experimentFwdOp
                                }
                            }
                        }
                    }
                }
            }
            experimentTraceSinks.forEach { x ->
                if(x.ins.size > 1){
                    for(inlet in x.ins) {
                        if (x.outputType.toLowerCase() == ExperimentConstants.ConfigurationProperties.TRACE_INPUT_TYPE.toLowerCase() && x.mergeObject == null) {
                            when(inlet){
                                is ExperimentTraceSource ->{
                                    x.mergeObject = builder.add(Merge.create<List<ArrayList<TraceData>>>(x.ins.size))

                                    //IMPORTANT NOTE: like broadcast, any merge object needs to have its forward op (builder.from(mergeObject).to(something)
                                    //This won't be used later but must be done now
                                    builder.from(x.mergeObject).to(x.sink)

                                    val experimentFwdOp = ExperimentTraceFwdOp(inlet.traceSourceName, x.traceSinkName)
                                    x.mergeFwdOp = experimentFwdOp
                                }
                                is ExperimentImageTraceFlow -> {
                                    x.mergeObject = builder.add(Merge.create<List<ArrayList<TraceData>>>(x.ins.size))

                                    //IMPORTANT NOTE: like broadcast, any merge object needs to have its forward op (builder.from(mergeObject).to(something)
                                    //This won't be used later but must be done now
                                    builder.from(x.mergeObject).to(x.sink)

                                    val experimentFwdOp = ExperimentTraceFwdOp(inlet.imgTraceFlowName, x.traceSinkName)
                                    x.mergeFwdOp = experimentFwdOp
                                }
                                is ExperimentTraceTraceFlow -> {
                                    x.mergeObject = builder.add(Merge.create<List<ArrayList<TraceData>>>(x.ins.size))

                                    //IMPORTANT NOTE: like broadcast, any merge object needs to have its forward op (builder.from(mergeObject).to(something)
                                    //This won't be used later but must be done now
                                    builder.from(x.mergeObject).to(x.sink)

                                    val experimentFwdOp = ExperimentTraceFwdOp(inlet.traceTraceFlowName, x.traceSinkName)
                                    x.mergeFwdOp = experimentFwdOp
                                }
                            }
                        }
                    }
                }
            }
        }
        catch(ex : Exception){
            GUIMain.loggerService.log(Level.SEVERE, "Error creating merge objects for sinks. Error message: ${ex.message}")
            GUIMain.loggerService.log(Level.SEVERE, ex.stackTrace)
        }
    }

    /**
     * If a flow has more than one inlet (connection going into it) then it will need to merge. This method will create
     * the necessary merge object
     * @param builder The graph building object
     */
    private fun setMergeObjectForFlows(builder : GraphDSL.Builder<NotUsed>){
        try {
            experimentImageImageFlows.forEach { x ->
                if (x.ins.size > 1) {
                    if (x.inputType.toLowerCase() == ExperimentConstants.ConfigurationProperties.IMAGE_INPUT_TYPE.toLowerCase()) {
                        //Create a merge object. We also need to specify it in the builder here
                        x.mergeObject = builder.add(Merge.create<STRIMMImage>(x.ins.size))

                        //IMPORTANT NOTE: like broadcast, any merge object needs to have its forward op (builder.from(mergeObject).to(something)
                        //This won't be used later but must be done now
                        builder.from(x.mergeObject).via(x.flow)

                        val experimentFwdOp = ExperimentImageFwdOp(x.imgImgFlowName, x.imgImgFlowName)
                        x.mergeFwdOp = experimentFwdOp
                    }
                }
            }
            experimentImageTraceFlows.forEach { x ->
                if (x.ins.size > 1) {
                    if (x.inputType.toLowerCase() == ExperimentConstants.ConfigurationProperties.IMAGE_INPUT_TYPE.toLowerCase()) {
                        //Create a merge object. We also need to specify it in the builder here
                        x.mergeObject = builder.add(Merge.create<STRIMMImage>(x.ins.size))

                        //IMPORTANT NOTE: like broadcast, any merge object needs to have its forward op (builder.from(mergeObject).to(something)
                        //This won't be used later but must be done now
                        builder.from(x.mergeObject).via(x.flow)

                        val experimentFwdOp = ExperimentImageFwdOp(x.imgTraceFlowName, x.imgTraceFlowName)
                        x.mergeFwdOp = experimentFwdOp
                    }
                }
            }
            experimentTraceImageFlows.forEach { x ->
                if (x.ins.size > 1) {
                    if (x.inputType.toLowerCase() == ExperimentConstants.ConfigurationProperties.TRACE_INPUT_TYPE.toLowerCase()) {
                        //Create a merge object. We also need to specify it in the builder here
                        x.mergeObject = builder.add(Merge.create<List<ArrayList<TraceData>>>(x.ins.size))

                        //IMPORTANT NOTE: like broadcast, any merge object needs to have its forward op (builder.from(mergeObject).to(something)
                        //This won't be used later but must be done now
                        builder.from(x.mergeObject).via(x.flow)

                        val experimentFwdOp = ExperimentTraceFwdOp(x.traceImgFlowName, x.traceImgFlowName)
                        x.mergeFwdOp = experimentFwdOp
                    }
                }
            }
            experimentTraceTraceFlows.forEach { x ->
                if (x.ins.size > 1) {
                    if (x.inputType.toLowerCase() == ExperimentConstants.ConfigurationProperties.TRACE_INPUT_TYPE.toLowerCase()) {
                        //Create a merge object. We also need to specify it in the builder here
                        x.mergeObject = builder.add(Merge.create<List<ArrayList<TraceData>>>(x.ins.size))

                        //IMPORTANT NOTE: like broadcast, any merge object needs to have its forward op (builder.from(mergeObject).to(something)
                        //This won't be used later but must be done now
                        builder.from(x.mergeObject).via(x.flow)

                        val experimentFwdOp = ExperimentTraceFwdOp(x.traceTraceFlowName, x.traceTraceFlowName)
                        x.mergeFwdOp = experimentFwdOp
                    }
                }
            }
        }
        catch (ex : Exception){
            GUIMain.loggerService.log(Level.SEVERE, "Error creating broadcast objects for flows. Error message: ${ex.message}")
            GUIMain.loggerService.log(Level.SEVERE, ex.stackTrace)
        }
    }
    //endregion

    //region Wrapper objects for experiment config
    /**
     * Short method to call each of the populating methods
     * @param expConfig The experiment config from the JSON config file
     * @param builder The graph building object
     */
    private fun populateLists(expConfig : ExperimentConfiguration, builder : GraphDSL.Builder<NotUsed>){
        println("ExperimentStream::populateLists(expConfig : ExperimentConfiguration, builder : GraphDSL.Builder<NotUsed>)")
        GUIMain.loggerService.log(Level.INFO, "Populating sources, flows and sinks")

        println("about to populate sources")
        populateSources(expConfig, builder)

        println("finished populate sources")
        populateFlows(expConfig, builder)

        populateSinks(expConfig, builder)
    }


    /**
     * Go through the experiment config (from json file) and create the appropriate source objects that correspond to the
     * config. This uses custom wrapper classes for the akka objects. This also specifies types explicitly
     * @param expConfig The experiment config from the JSON config file
     * @param builder The graph building object
     */
    private fun populateSources(expConfig: ExperimentConfiguration, builder : GraphDSL.Builder<NotUsed>){
        try {
            for (source in expConfig.sourceConfig.sources) {
                if (source.outputType.toLowerCase() == ExperimentConstants.ConfigurationProperties.IMAGE_OUTPUT_TYPE.toLowerCase()) {
                    //camera feed source
                    val expSource = ExperimentImageSource(source.sourceName, source.deviceLabel)
                    //snapImageMethod.run() will take in image with source.sourceName
                    val snapImageMethod  = GUIMain.acquisitionMethodService.getAcquisitionMethod("ConfiguredCamera") as AcquisitionMethodService.ImageMethod
                    val isTimeLapse = source.isTimeLapse
                    val intervalMs = source.intervalMs
                    //the cameras loaded by AcquisitionMethodService when it runs its init() at the start of STRIMM are all
                    //default settings.  Configure the camera depending on the settings in the JSON
                   // source.camera!!.SetGlobalStart(source.isGlobalStart)
                    source.camera!!.SetExposureMs(source.exposureMs)
                    source.camera!!.SetIntervalMs(source.intervalMs)
                    source.camera!!.SetSnapped(source.isImageSnapped)
                    source.camera!!.SetKeyboardSnapEnabled(source.isKeyboardSnapEnabled)
                    source.camera!!.SetSnapVirtualCode(source.SnapVirtualCode)
                    source.camera!!.SetTriggered(source.isTriggered)
                    source.camera!!.SetFramesInCircularBuffer(source.framesInCircularBuffer)
                    source.camera!!.SetROI(source.x.toInt(), source.y.toInt(), source.w.toInt(), source.h.toInt())
                    source.camera!!.Reset()
                    //the isGreyScale setting is important for OpenCV
                    //at present STRIMM does not support RGB however does support float images
                    //hence it is converted to a float image (R+G+B)/3.0
                    //the MultiCamera sink can display RGB however, so if the intention is only to display outside of STRIMM
                    // then RGB can be used
                    source.camera!!.SetGreyScale(source.isGreyScale)
                    val akkaSource =
                        //timelapse controlled with PC time
                        if (isTimeLapse){
                            Source.tick(Duration.ZERO, Duration.ofMillis(intervalMs.toLong()), Unit)
                        }
                        //fps determined by the runMethod of the camera
                        else {
                            Source.repeat(1)
                        }
                            .map { snapImageMethod.runMethod(source, 0) }
                            .async() //ensures that akka-stream source gets its own thread so cameras can work in parallel
                            .named(source.sourceName) as Source<STRIMMImage, NotUsed>
                    numCameras++


                    val killSwitchSource = akkaSource.viaMat(GUIMain.actorService.sharedKillSwitch.flow(), Keep.right())
                    val sourceGraph = killSwitchSource.toMat(BroadcastHub.of(STRIMMImage::class.java), Keep.right())
                    val fromSource = sourceGraph.run(GUIMain.actorService.materializer).async()

                    expSource.roiSource = fromSource
                    expSource.source = builder.add(fromSource)
                    expSource.outputType = ExperimentConstants.ConfigurationProperties.IMAGE_OUTPUT_TYPE
                    expSource.exposureMs = source.exposureMs
                    expSource.intervalMs = source.intervalMs
                    GUIMain.experimentService.calculateNumberOfDataPointsFromInterval(source.deviceLabel, intervalMs)
                    experimentImageSources.add(expSource)
                }
                else if (source.outputType.toLowerCase() == ExperimentConstants.ConfigurationProperties.TRACE_OUTPUT_TYPE.toLowerCase()) {
                    val expSource = ExperimentTraceSource(source.sourceName, source.deviceLabel)
                    expSource.samplingFrequencyHz = source.samplingFrequencyHz
                    //the source has a sourceType which is used here to
                    //select the kind of source
                    //also the source can be further parameterised with param1, param2, ... , param4
                    if (source.sourceType == "NIDAQSource") {
                        GUIMain.protocolService.SetNIDAQUsed(true)
                        var numAOChannels = GUIMain.protocolService.GetNumChannels(0)
                        var numAIChannels = GUIMain.protocolService.GetNumChannels( 1)
                        var numDOChannels = GUIMain.protocolService.GetNumChannels( 2)
                        var numDIChannels = GUIMain.protocolService.GetNumChannels( 3)

                        val dummyAO = mutableListOf<Overlay>()
                        val dummyAI = mutableListOf<Overlay>()
                        val dummyDO = mutableListOf<Overlay>()
                        val dummyDI = mutableListOf<Overlay>()
                        for (f in 0 until numAOChannels) {
                            val dummyOverlay = EllipseOverlay()
                            val chan = GUIMain.protocolService.GetChannelFromIndex(0, f)
                            dummyOverlay.name =
                                expSource.traceSourceName + " " + "ao" + chan.toString() + " " + Random().nextInt(1000)
                            dummyAO.add(dummyOverlay)

                            val numPoints = GUIMain.protocolService.GetNumberOfDataPoints()
                            GUIMain.experimentService.deviceDatapointNumbers[dummyOverlay.name] = numPoints
                        }
                        for (f in 0 until numAIChannels) {
                            val dummyOverlay = EllipseOverlay()
                            val chan = GUIMain.protocolService.GetChannelFromIndex(1, f)
                            dummyOverlay.name =
                                expSource.traceSourceName + " " + "ai" + chan.toString() + " " + Random().nextInt(1000)
                            dummyAI.add(dummyOverlay)
                        }
                        for (f in 0 until numDOChannels) {
                            val dummyOverlay = EllipseOverlay()
                            val chan = GUIMain.protocolService.GetChannelFromIndex(2, f)
                            dummyOverlay.name =
                                expSource.traceSourceName + " " + "do" + chan.toString() + " " + Random().nextInt(1000)
                            dummyDO.add(dummyOverlay)
                        }
                        for (f in 0 until numDIChannels) {
                            val dummyOverlay = EllipseOverlay()
                            val chan = GUIMain.protocolService.GetChannelFromIndex(3, f)
                            dummyOverlay.name =
                                expSource.traceSourceName + " " + "di" + chan.toString() + " " + Random().nextInt(1000)
                            dummyDI.add(dummyOverlay)
                        }

                        val getTraceDataMethodNIDAQ =
                            GUIMain.acquisitionMethodService.getAcquisitionMethod("Trace Data Method NIDAQ")
                                    as AcquisitionMethodService.TraceMethod

                        if (GUIMain.experimentService.expConfig.NIDAQ.bRepeat){
                            val akkaSource = Source.repeat(1)
                                .map {
                                    listOf(
                                        getTraceDataMethodNIDAQ.runMethod(
                                            dummyAO,   //send the Overlays which identify each virtual channels via its name
                                            dummyAI,
                                            dummyDO,
                                            dummyDI,
                                            source
                                        )
                                    )
                                }
                                .async()
                                .named(source.sourceName)
                            expSource.source = builder.add(akkaSource)
                        }
                        else{
                            val akkaSource = Source.repeat(1)
                                .take(GUIMain.protocolService.GetNumberOfStages())
                                .map {
                                    listOf(
                                        getTraceDataMethodNIDAQ.runMethod(
                                            dummyAO,
                                            dummyAI,
                                            dummyDO,
                                            dummyDI,
                                            source
                                        )
                                    )
                                } //dummyOverlay can be an array
                                .async()
                                .named(source.sourceName)
                            expSource.source = builder.add(akkaSource)
                        }
                    }
                    else if (source.sourceType == "KeyboardABCSource"){
                        val dummyKeys = mutableListOf<Overlay>()
                        val keys = arrayListOf<Int>('A'.toInt(),'B'.toInt(),'C'.toInt())
                        for (f in 0 until 3) {
                            val dummyOverlay = EllipseOverlay()
                            dummyOverlay.name =
                                expSource.traceSourceName + " " + "keys" + keys[f].toString() + " " + Random().nextInt(1000)
                            dummyKeys.add(dummyOverlay)
                        }
                        val getTraceDataMethodKeyboard =
                            GUIMain.acquisitionMethodService.getAcquisitionMethod("Trace Data Method Keyboard")
                                    as AcquisitionMethodService.TraceMethod
                        val akkaSource = Source.repeat(1)
                            .map {
                                listOf(
                                    getTraceDataMethodKeyboard.runMethod(
                                        keys, dummyKeys
                                    )
                                )
                            }
                            .async()
                            .named(source.sourceName)
                        expSource.source = builder.add(akkaSource)

                    }
                    else if (source.sourceType == "KeyboardASource"){
                        val key = 'A'.toInt()
                        val pollPeriod = source.param1.toInt()
                        val dummyOverlay = EllipseOverlay()
                        //the overlay name is used as the ID for this series of data - when plotted lines will all have the save overlay.name
                        dummyOverlay.name =
                            expSource.traceSourceName + " " + "key" + key + " " + Random().nextInt(1000)

                        val getTraceDataMethodKeyboard =
                            GUIMain.acquisitionMethodService.getAcquisitionMethod("KeyboardA")
                                    as AcquisitionMethodService.TraceMethod
                        val akkaSource = Source.repeat(1)
                            .map {
                                listOf(
                                    getTraceDataMethodKeyboard.runMethod(
                                        key, dummyOverlay, pollPeriod
                                    )
                                )
                            } //dummyOverlay can be an array
                            .async()
                            .named(source.sourceName)
                        expSource.source = builder.add(akkaSource)

                    }
                    else if (source.sourceType == "RandomTraceSource"){
                        val dummyOverlay = EllipseOverlay() //this dummyOverlay will be used as the id for this trace
                        dummyOverlay.name = expSource.traceSourceName  + Random().nextInt(1000)

                        val getTraceDataRandomTraceSource =  //get a TraceMethod with a run function which will return the data
                            GUIMain.acquisitionMethodService.getAcquisitionMethod("RandomTraceSource")
                                    as AcquisitionMethodService.TraceMethod
                        //the akka source will pump out 1s each will trigger the getTraceDataMethodConstantTraceSource run() method
                        val akkaSource = Source.repeat(1)
                            .map {
                                listOf(
                                    getTraceDataRandomTraceSource.runMethod(
                                        dummyOverlay, source
                                    )
                                )
                            }
                            .async()  //async means that it has its own thread of execution
                            .named(source.sourceName) //named command bakes it all into a 'compund source'
                        expSource.source = builder.add(akkaSource)  //add to the graph

                    }
                    else if (source.sourceType == "ConstantTraceSource"){
                        val dummyOverlay = EllipseOverlay()
                        dummyOverlay.name = expSource.traceSourceName  + Random().nextInt(1000)
                        val getTraceDataMethodConstantTraceSource =
                            GUIMain.acquisitionMethodService.getAcquisitionMethod("ConstantTraceSource")
                                    as AcquisitionMethodService.TraceMethod
                        val akkaSource = Source.repeat(1)
                            .map {
                                listOf(
                                    getTraceDataMethodConstantTraceSource.runMethod(
                                        dummyOverlay, source
                                    )
                                )
                            }
                            .async()
                            .named(source.sourceName)
                        expSource.source = builder.add(akkaSource)

                    }
                    else if (source.sourceType == "ConstantVectorSource"){
                            var overlays = arrayListOf<Overlay>()
                            val dummyOverlay1 = EllipseOverlay() //id for the 1st component
                            dummyOverlay1.name = expSource.traceSourceName  + 0
                            val dummyOverlay2 = EllipseOverlay() //id for the 2md component
                            dummyOverlay2.name = expSource.traceSourceName  + 1
                            overlays.add(dummyOverlay1)
                            overlays.add(dummyOverlay2)
                            val getTraceDataMethodConstantVectorSource =
                                GUIMain.acquisitionMethodService.getAcquisitionMethod("ConstantVectorSource")
                                        as AcquisitionMethodService.TraceMethod
                        val akkaSource = Source.repeat(1)
                            .map {
                                listOf(
                                    getTraceDataMethodConstantVectorSource.runMethod(
                                        overlays, source
                                    )
                                )
                            }
                            .async()  //async means that it has its own thread of execution
                            .named(source.sourceName) //named command bakes it all into a 'compound source'
                        expSource.source = builder.add(akkaSource)  //add to the graph

                    }
                    else if (source.sourceType == "SineWaveSource"){
                        val dummyOverlay = EllipseOverlay()
                        dummyOverlay.name = expSource.traceSourceName  + Random().nextInt(1000)
                        val getTraceDataMethodSineWaveSource =
                            GUIMain.acquisitionMethodService.getAcquisitionMethod("SineWaveSource")
                                    as AcquisitionMethodService.TraceMethod
                        val akkaSource = Source.repeat(1)
                            .map {
                                listOf(
                                    getTraceDataMethodSineWaveSource.runMethod(
                                        dummyOverlay, source
                                    )
                                )
                            }
                            .async()
                            .named(source.sourceName)
                        expSource.source = builder.add(akkaSource)

                    }
                    else if (source.sourceType == "SquareWaveSource"){
                        val dummyOverlay = EllipseOverlay()
                        dummyOverlay.name = expSource.traceSourceName  + Random().nextInt(1000)
                        val getTraceDataMethodSquareWaveSource =
                            GUIMain.acquisitionMethodService.getAcquisitionMethod("SquareWaveSource")
                                    as AcquisitionMethodService.TraceMethod
                        val akkaSource = Source.repeat(1)
                            .map {
                                listOf(
                                    getTraceDataMethodSquareWaveSource.runMethod(
                                        dummyOverlay, source
                                    )
                                )
                            }
                            .async()
                            .named(source.sourceName)
                        expSource.source = builder.add(akkaSource)

                    }
                    expSource.outputType = ExperimentConstants.ConfigurationProperties.TRACE_OUTPUT_TYPE
                    experimentTraceSources.add(expSource)
                }
            }
        }
        catch(ex : Exception){
            GUIMain.loggerService.log(Level.SEVERE, "Error creating sources from json experiment configuration. Error message: ${ex.message}")
            GUIMain.loggerService.log(Level.SEVERE, ex.stackTrace)
        }
    }

    /*
    Flows are specialised by a flowType and also
    a flow function.  The flow function will take a List<List<TraceData>>
    process and return it

     */
    private fun Arduino_Digital_Output(data : List<List<TraceData>>) : List<List<TraceData>> {
    //just sends the incoming byte data to the Arduino
        var dat = data[0]
        var incomingData = dat[0].data.second.toInt()
    //println("*********** " + incomingData)
//        if (incomingData >= 0  && incomingData <= 200){
//            //pass it through the serial connector to the program running on the Arduino
//                try {
//                    GUIMain.protocolService.COMPort.outputStream.write(byteArrayOf(incomingData.toByte()))
//                } catch(ex : Exception){
//                    println("ARDUINO_FAIL")
//                    println(ex.message)
//                }
//        }
        return data
    }
    private fun Threshold_Flow_Controller(data : List<List<TraceData>>, threshold : Double, channel : Int, type : Int ): List<List<TraceData>> {
        var dat = data[0]
        var len = dat.size
        var incomingData = dat[0].data.second
        var outgoingData = 0
        if (type >= 0) {
            if (incomingData > threshold) {
                outgoingData = 100 + channel
            } else {
                outgoingData = channel
            }
        }
        else{
            if (incomingData < threshold) {
                outgoingData = 100 + channel
            } else {
                outgoingData = channel
            }
        }
        var overlay = dat[0].data.first as Overlay
        var dat1 = arrayListOf<TraceData>()
        dat1.add(TraceData(Pair(overlay, outgoingData.toDouble()), dat[0].timeAcquired))
        var dat3 = arrayListOf< ArrayList<TraceData>>()
        dat3.add(dat1)
        return dat3
    }
    private fun Threshhold_controller(data : List<List<TraceData>>, threshhold : Double ): List<List<TraceData>> {
    var dat = data[0]
    var len = dat.size
    var dat1 = arrayListOf<TraceData>()
    for (d in dat){

        var res = if(d.data.second.toDouble() > threshhold) 'A'.toDouble() else 0.0
        println(d.data.second.toString()+"  " +threshhold.toString() + " " + res.toString())
        var overlay = d.data.first as Overlay
        dat1.add(TraceData(Pair(overlay, res), d.timeAcquired))
    }
    var dat3 = arrayListOf< ArrayList<TraceData>>()
    dat3.add(dat1)
    return dat3
}
    private fun ArduinoDO3_controller(data : List<List<TraceData>> ): List<List<TraceData>> {
        var dat = data[0]
        var len = dat.size

        var dataToSend = if (dat[0].data.second > 1.0) 'A' else 'Z'

       // GUIMain.protocolService.COMPort.outputStream.write(byteArrayOf(dataToSend.toByte()))
//        val portToUse = SerialPort.getCommPort("COM4") //find in DeviceManager
//        portToUse.setComPortParameters(9600, 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY)
//        portToUse.setComPortTimeouts(SerialPort.TIMEOUT_NONBLOCKING, 0, 0)
//        if (!portToUse.isOpen) {
//           // println("Port is not open")
//            if (portToUse.openPort()) {
//               // println("Opened port")
//                 portToUse.outputStream.write(byteArrayOf(dataToSend.toByte()))
//            } else {
//                println("Failed to open port")
//            }
//        } else {
//           // println("Port is already open")
//        }
//

        return data
    }
    private fun ArduinoDO4_controller(data : List<List<TraceData>>): List<List<TraceData>> {
        var dat = data[0]
        var len = dat.size

        var dataToSend = if (dat[0].data.second > 1.0) 'B' else 'Z'

       // GUIMain.protocolService.COMPort.outputStream.write(byteArrayOf(dataToSend.toByte()))


//        val portToUse = SerialPort.getCommPort("COM4") //find in DeviceManager
//        portToUse.setComPortParameters(9600, 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY)
//        portToUse.setComPortTimeouts(SerialPort.TIMEOUT_NONBLOCKING, 0, 0)
//        if (!portToUse.isOpen) {
//           // println("Port is not open")
//            if (portToUse.openPort()) {
//               // println("Opened port")
//                 portToUse.outputStream.write(byteArrayOf(dataToSend.toByte()))
//            } else {
//                println("Failed to open port")
//            }
//        } else {
//           // println("Port is already open")
//        }
//

        return data
    }
    private fun SelectChannels(data : List<List<TraceData>> , channels : IntArray, stride : Int) : List<List<TraceData>> {
        var dat = data[0]
        var len = dat.size


        var dat1 = arrayListOf<TraceData>()
        for(i in 0 .. (len-1) step stride){
            for (f in 0 .. (channels.size-1)){
                dat1.add(dat[i + channels[f]])
            }
        }
        var dat3 = arrayListOf< ArrayList<TraceData>>()
        dat3.add(dat1)
        return dat3
    }
    var cnt_s = 0
    private fun NIDAQ_inject_AO(data : List<List<TraceData>> , channel : Int, pAODataInject : DoubleArray) : List<List<TraceData>> {

        for (f in 0 until pAODataInject.size) {
            val random = Random()
            val randomInt = random.nextInt(10)
            if (randomInt > 5) {
                pAODataInject[f] = 10.0
            } else {
                pAODataInject[f] = -10.0
            }
        }
        cnt_s++
        val random = Random()
        val randomInt = random.nextInt(10)
        if (cnt_s % 2 == 0) {
            GUIMain.protocolService.UpdateAOChannel(pAODataInject, channel)
            println("inject")
        }
        return data
    }
    private fun NIDAQ_inject_DO(data : List<List<TraceData>> , line : Int, pDODataInject : IntArray) : List<List<TraceData>> {
        for (f in 0 until pDODataInject.size) {
            val random = Random()
            val randomInt = random.nextInt(10)
            if (randomInt > 5) {
                pDODataInject[f] = 0
            } else {
                pDODataInject[f] = 0
            }
        }
        cnt_s++
        val random = Random()
        val randomInt = random.nextInt(10)
        if (cnt_s % 2 == 0) {
            GUIMain.protocolService.UpdateDOChannel(pDODataInject, line)
            println("inject")
        }

        return data
    }

    var testCounter = 0
    var testStore = hashMapOf<String, STRIMMImage>()
    private fun PairImageRatio(data : STRIMMImage) : STRIMMImage{
        if (testCounter == 0){
            testStore["result"] = data
            testStore["store1"] = data
        } else {
            if (testCounter % 2 == 0){

                var pix = data.pix as FloatArray
                var newPix = FloatArray(pix.size)
                var pixOld = testStore["store1"]!!.pix as FloatArray

                for (f in 0 until pix.size){
                    newPix[f] = (pix[f] / pixOld[f])
                }
                testStore["result"] = STRIMMImage(data.sourceCamera, newPix, data.timeAcquired,0, 0, 0)


            } else {

                testStore["store1"] = data
            }

        }
        testCounter++

        return testStore["result"] as STRIMMImage


    }
    private fun ConsecutiveImageSubtractor(data : STRIMMImage) : STRIMMImage{
        if (lastImageFlow.containsKey(data.sourceCamera)){
            println("here")
            var pix = data.pix as FloatArray
            var newPix = FloatArray(pix.size)
            var pixOld = (lastImageFlow[data.sourceCamera])!!.pix as FloatArray
            for (f in 0 until pix.size){
                newPix[f] = (pix[f] - pixOld[f])
            }
            lastImageFlow[data.sourceCamera] = data
            return STRIMMImage(data.sourceCamera, newPix, data.timeAcquired,0, 0, 0)
        }
        lastImageFlow[data.sourceCamera] = data
        return data
    }
    private fun GlobalSourceStartTriggerFlow(data : List<List<TraceData>>)  : List<List<TraceData>>{

        var dat = data[0]
        var incomingData = dat[0].data.second.toInt()
        if (incomingData > 50){ //hard coded
            //global start to all sources
            GUIMain.protocolService.bGlobalSourceStartTrigger = true

        }
        return data
    }
    private fun ThreshholdImage(data : STRIMMImage, threshhold : Double) : STRIMMImage{
        var pix = data.pix as FloatArray
        var newPix = FloatArray(pix.size)
        for (f in 0 until pix.size){
            newPix[f] = (if(pix[f] > 70.0) 1.0 else 0.0) as Float
        }
        return data
    }
    private fun FlowReduceSize(data : STRIMMImage) : STRIMMImage{
        var pix = ShortArray(200*200)

        for (i in 0..(pix.size - 1)){
            pix[i] = Random().nextInt((65000 + 1) ).toShort()

        }
        var image : STRIMMImage = STRIMMImage(data.sourceCamera, pix, data.timeAcquired, data.imageCount, 200, 200);
        return image
    }
    private fun PrintFlow(data : STRIMMImage) : STRIMMImage{
        //println("image going through flow")


        return  data
    }
    private fun ReduceFlow(data : STRIMMImage) : STRIMMImage{
        println("Reduce flow")
//        var newPix = FloatArray(100*100)
//        for (f in 0 until newPix.size){
//            newPix[f] = f.toFloat()
//        }
        var pix0 = data.pix as FloatArray
        println(data.w.toString() + " " + data.h)
        for (j in 0..100){
            for (i in 0..100){
                if (i%2==0 && j%2==0){
                    pix0[i + j*100]=1000.0f;
                }
                else{
                    pix0[i + j*100] = 0.0f;
                }
            }
        }


        return data

    }
    private fun BrightFlow(data : STRIMMImage) : STRIMMImage{
        println("here")
        var pix = data.pix as FloatArray
        var newPix = FloatArray(pix.size)
        for (f in 0 until pix.size){
            newPix[f] = pix[f]
        }
        for (f in 0 until pix.size/2){
            newPix[f] = pix[f]+100.0F
        }
        return STRIMMImage(data.sourceCamera, newPix, data.timeAcquired, data.imageCount, data.w, data.h)
    }

    //
    //
    var imageSelectorFlow_lastImage = hashMapOf<String, STRIMMImage>()
    private fun Image_Selector_Flow(data : STRIMMImage, fl : uk.co.strimm.experiment.Flow) : STRIMMImage{
        val a : Int = fl.param1.toInt()
        val d : Int = fl.param2.toInt()

        if ((data.imageCount - a) % d == 0){
            //println(fl.flowName +  " " + data.imageCount.toString() + "   " + a.toString() + "   " + d.toString())
            imageSelectorFlow_lastImage[fl.flowName] = data
        }
        return imageSelectorFlow_lastImage[fl.flowName] as STRIMMImage
    }
    private fun Image_Reducer_Flow(data : STRIMMImage, fl : uk.co.strimm.experiment.Flow) : STRIMMImage{
        var x = fl.param1.toInt()
        var y = fl.param2.toInt()
        var w = fl.param3.toInt()
        var h = fl.param4.toInt()
        var im_w = data.w
        var im_h = data.h
        var pix = data.pix as ShortArray
        var newPix = ShortArray(w * h)
//issues with 0-ref and 1-ref
        for (j in 0..(h-1)){
            for (i in 0..(w-1)){
                newPix[i + w*j] = pix[ (x-1 + i) + im_w * (y-1 + j)]
            }
        }
        return STRIMMImage(data.sourceCamera, newPix, data.timeAcquired, data.imageCount, w, h)
    }
    private fun Image_Dual_Reducer_Combine_Addition_Flow(data : STRIMMImage, fl : uk.co.strimm.experiment.Flow) : STRIMMImage{
        var x1 = fl.param1.toInt()
        var y1 = fl.param2.toInt()
        var x2 = fl.param3.toInt()
        var y2 = fl.param4.toInt()

        var w = fl.param5.toInt()
        var h = fl.param6.toInt()

        var im_w = data.w
        var im_h = data.h
        var pix = data.pix as ShortArray
        var newPix = ShortArray(w * h)
//issues with 0-ref and 1-ref
        for (j in 0..(h-1)){
            for (i in 0..(w-1)){
                newPix[i + w*j] = (pix[ (x1-1 + i) + im_w * (y1-1 + j)] +  pix[ (x2-1 + i) + im_w * (y2-1 + j)]).toShort()
            }
        }
        return STRIMMImage(data.sourceCamera, newPix, data.timeAcquired, data.imageCount, w, h)
    }
    private fun Image_Dual_Reducer_Combine_Ratio_Flow(data : STRIMMImage, fl : uk.co.strimm.experiment.Flow) : STRIMMImage{
        var x1 = fl.param1.toInt()
        var y1 = fl.param2.toInt()
        var x2 = fl.param3.toInt()
        var y2 = fl.param4.toInt()

        var w = fl.param5.toInt()
        var h = fl.param6.toInt()

        var im_w = data.w
        var im_h = data.h
        var pix = data.pix as ShortArray
        var newPix = ShortArray(w * h)
//issues with 0-ref and 1-ref
        for (j in 0..(h-1)){
            for (i in 0..(w-1)){
                newPix[i + w*j] = (pix[ (x1-1 + i) + im_w * (y1-1 + j)] /  pix[ (x2-1 + i) + im_w * (y2-1 + j)]).toShort()
            }
        }
        return STRIMMImage(data.sourceCamera, newPix, data.timeAcquired, data.imageCount, w, h)
    }


    private fun Image_Ratio_Flow_Float(data : STRIMMImage, fl : uk.co.strimm.experiment.Flow) : STRIMMImage{
        val scale = fl.param1.toDouble()
        val offset = fl.param2.toDouble()
        if ((data.imageCount - 1) % 2 ==0){ // odd
            imageSelectorFlow_lastImage[fl.flowName + "_odd"] = data
        }
        else { //even
            var pix_odd = imageSelectorFlow_lastImage[fl.flowName + "_odd"]!!.pix as FloatArray
            var pix_even = data.pix as FloatArray

            var pix2 = FloatArray(data.pix.size)
            for (f in 0 until pix2.size){
                var res = (pix_even[f].toDouble() / pix_odd[f].toDouble())*scale + offset
                if (res < 0) res = 0.0
                if (res > 0xFFFF.toDouble()) res = 0xFFFF.toDouble()
                pix2[f] = res.toFloat()
            }
            imageSelectorFlow_lastImage[fl.flowName] = STRIMMImage(data.sourceCamera, pix2, data.timeAcquired, data.imageCount, data.w, data.h)
        }
        return imageSelectorFlow_lastImage[fl.flowName] as STRIMMImage
    }
    private fun Image_Ratio_Flow_Short(data : STRIMMImage, fl : uk.co.strimm.experiment.Flow) : STRIMMImage{
        if (data.pix == null) return data
        val scale = fl.param1.toDouble()
        val offset = fl.param2.toDouble()
        if ((data.imageCount - 1) % 2 ==0){ // odd
            //store the odd result
            imageSelectorFlow_lastImage[fl.flowName + "_odd"] = data
        }
        else { //even
            if ((imageSelectorFlow_lastImage[fl.flowName + "_odd"] == null) || (imageSelectorFlow_lastImage[fl.flowName + "_odd"]?.pix == null)) {
                var pix = ShortArray((data.pix as ShortArray).size)
                for (f in 0 until pix.size){
                    pix[f] = 0
                }
                imageSelectorFlow_lastImage[fl.flowName + "_odd"] = STRIMMImage(data.sourceCamera, pix, data.timeAcquired, data.imageCount, data.w, data.h)
            }

            var pix_odd = imageSelectorFlow_lastImage[fl.flowName + "_odd"]?.pix as ShortArray
            var pix_even = data.pix as ShortArray
            //println( (pix_odd == null).toString() + "," + (pix_even == null).toString())
            var pix2 = ShortArray(data.pix.size)
            for (f in 0 until pix2.size){
                var res = (pix_even[f].toDouble() / pix_odd[f].toDouble())*scale + offset
////                if (res < 0) res = 0.0
////                if (res > 0xFFFF.toDouble()) res = 0xFFFF.toDouble()
                pix2[f] = res.toShort()
           }
            imageSelectorFlow_lastImage[fl.flowName] = STRIMMImage(data.sourceCamera, pix2, data.timeAcquired, data.imageCount, data.w, data.h)
        }
        return imageSelectorFlow_lastImage[fl.flowName] as STRIMMImage

    }

    private fun Image_Histogram_Flow_Short(data : STRIMMImage, fl : uk.co.strimm.experiment.Flow) : STRIMMImage{
        if (data.pix == null) return data
        val numBins = fl.param1.toInt()
        val xMin = fl.param2.toDouble()
        val xMax = fl.param3.toDouble()

        if ((data.imageCount - 1) % 2 ==0){ // odd
            //store the odd result
            imageSelectorFlow_lastImage[fl.flowName + "_odd"] = data
        }
        else { //even
            if ((imageSelectorFlow_lastImage[fl.flowName + "_odd"] == null) || (imageSelectorFlow_lastImage[fl.flowName + "_odd"]?.pix == null)) {
                var pix = ShortArray((data.pix as ShortArray).size)
                for (f in 0 until pix.size){
                    pix[f] = 0
                }
                imageSelectorFlow_lastImage[fl.flowName + "_odd"] = STRIMMImage(data.sourceCamera, pix, data.timeAcquired, data.imageCount, data.w, data.h)
            }

            var pix_odd = imageSelectorFlow_lastImage[fl.flowName + "_odd"]?.pix as ShortArray
            var pix_even = data.pix as ShortArray
            //println( (pix_odd == null).toString() + "," + (pix_even == null).toString())
//create and construct histogram
            //make arrayList<Double>
            var values = DoubleArray(numBins * 2)
            //add 2 * numBins slots to arrayList
            for (f in 0 .. (values.size - 1)){
                values[f] = 0.0
            }
            var binWidth = (xMax - xMin).toDouble()/numBins.toDouble()
            //for loop over images
            var image_odd_mean = 0.0;
            for (ff in 0..(pix_odd.size-1)) {
                // ignore is val < xMin or > xMax
                if (pix_odd[ff] < xMin || pix_odd[ff] > xMax){
                    continue
                }
                var curVal = (round((pix_odd[ff] - xMin)/binWidth)).toInt()
                if (curVal < numBins) values[curVal]++
                image_odd_mean = image_odd_mean + pix_odd[ff]
            }
            image_odd_mean = image_odd_mean / pix_odd.size

            var image_even_mean = 0.0
            for (ff in 0..(pix_even.size-1)) {
                // ignore is val < xMin or > xMax
                if (pix_even[ff] < xMin || pix_even[ff] > xMax){
                    continue
                }
                var curVal = (round((pix_even[ff] - xMin)/binWidth) + numBins).toInt()
                if (curVal < 2*numBins && curVal >= numBins) values[curVal]++

                image_even_mean = image_even_mean + pix_even[ff]
            }
            image_even_mean = image_even_mean / pix_even.size
            var szDetails : String = "Mean image1 :" + round(image_odd_mean).toString() + " , Mean image2 :" + round(image_even_mean).toString()

            var pix = ShortArray(1000*1000)
            for (f in 0..(pix.size-1)){
                pix[f] = 0
            }


            GUIMain.protocolService.GDI_Test_Write_Array(1000, 1000, pix, numBins,
                2, szDetails, xMin, xMax, values)

            imageSelectorFlow_lastImage[fl.flowName + "_hist"] = STRIMMImage(
                data.sourceCamera,
                pix,
                data.timeAcquired,
                data.imageCount,
                1000,
                1000
            )
        }
        return imageSelectorFlow_lastImage[fl.flowName + "_hist"] as STRIMMImage

    }


    private fun Histogram_Flow(data : STRIMMImage, min : Double, max : Double, numBins : Int) : STRIMMImage{
        if (data.pix == null){
            return STRIMMImage("", null, 0,0,0,0)
        }
        var pix0 = data.pix as ShortArray
        var frequencies = IntArray(numBins)
        var newPix = ShortArray(pix0.size)
        var binWidth = (max - min)/numBins
        var freqMax = 0


        for (f in 0 until pix0.size){
            var bin = (floor((pix0[f].toFloat() - min) / binWidth.toFloat())).toInt()
            if (pix0[f] > max) bin = numBins-1
            if (bin > numBins - 1) bin = numBins-1
            frequencies[bin]++
            if (frequencies[bin] > freqMax) freqMax = frequencies[bin]
            newPix[f] = 0
        }
        for(i in 0 until data.w){
            var bin = (floor(i.toFloat()  / data.w.toFloat() * (numBins-1).toFloat())).toInt()
            if (bin > numBins - 1 ) bin = numBins-1

            var y = (frequencies[bin].toFloat() / freqMax.toFloat() * (data.h -100).toFloat()).toInt()
            for (j in 0 until y)  newPix[i + (data.h - 100 - j)*data.w] = 5000
        }
//println("hist")
        return STRIMMImage(data.sourceCamera, newPix, data.timeAcquired, data.imageCount, data.w, data.h)


    }
    //

    private fun GDI_Flow_Test(data : List<List<TraceData>>) : STRIMMImage{

        //println("in GDI_Flow_Test")
        var pix = ShortArray(1000*1000)
        for (f in 0..(pix.size-1)){
            pix[f] = 0
        }

        var values = DoubleArray(20);
        for (f in 0..10){
            values[f] = 1.0
        }
        for (f in 10..19){
            values[f] = 2.0
        }

        GUIMain.protocolService.GDI_Test_Write_Array(1000, 1000, pix, 10,
            2, "Mean 604nm : 205,3, Mean 410nm : 120.9", 0.0, 10.0, values)

       // println(pix[0])


//println("hist")
        return STRIMMImage("GDI_image", pix, data[0][0].timeAcquired, 0, 1000, 1000)



    }



    private fun GDI_Indicator_Pair(data : List<STRIMMImage>) : STRIMMImage{
        if (data[0].pix == null || data[1].pix == null){
            return STRIMMImage("", null, 0,0,0,0)
        }

//        var px0 = data[0].pix as ShortArray //not a short array
//        var px1 = data[1].pix as ShortArray
//        var mean0 = (px0.sum().toDouble()/px0.size.toDouble()).toInt()
//        var mean1 = (px1.sum().toDouble()/px1.size.toDouble()).toInt()

        var nums = IntArray(2)
        nums[0] = 100;//mean0;
        nums[1] = 200; //mean1;

        GUIMain.protocolService.GDI_Write_Numbers_onto_Array(nums)


//println("hist")
        return data[0]



    }
    //private fun Histogram_Flow_Pair(data : List<STRIMMImage>, min : Double, max : Double, numBins : Int) : STRIMMImage{
    //        if (data[0].pix == null || data[1].pix == null){
    //            return STRIMMImage("", null, 0,0,0,0)
    //        }
    //        var pix0 = data[0].pix as ShortArray
    //        var frequencies = IntArray(numBins)
    //        var newPix = ShortArray(pix0.size)
    //        var binWidth = (max - min)/numBins
    //        var freqMax = 0
    //
    //
    //        for (f in 0 until pix0.size){
    //            var bin = (floor((pix0[f].toFloat() - min) / binWidth.toFloat())).toInt()
    //            if (pix0[f] > max) bin = numBins-1
    //            if (bin > numBins - 1) bin = numBins-1
    //            frequencies[bin]++
    //            if (frequencies[bin] > freqMax) freqMax = frequencies[bin]
    //            newPix[f] = 0
    //        }
    //        for(i in 0 until data[0].w){
    //            var bin = (floor(i.toFloat()  / data[0].w.toFloat() * (numBins-1).toFloat())).toInt()
    //            if (bin > numBins - 1 ) bin = numBins-1
    //
    //            var y = (frequencies[bin].toFloat() / freqMax.toFloat() * (data[0].h -1).toFloat()).toInt()
    //            for (j in 0 until y)  newPix[i + (data[0].h - 1 - j)*data[0].w] = 100
    //        }
    //
    //        var pix1 = data[1].pix as ShortArray
    //
    //        for (f in 0 until pix1.size){
    //            var bin = (floor((pix1[f].toFloat() - min) / binWidth.toFloat())).toInt()
    //            if (pix1[f] > max) bin = numBins-1
    //            if (bin > numBins - 1) bin = numBins-1
    //            frequencies[bin]++
    //            if (frequencies[bin] > freqMax) freqMax = frequencies[bin]
    //
    //        }
    //
    //        for(i in 0 until data[1].w){
    //            var bin = (floor(i.toFloat()  / data[0].w.toFloat() * (numBins-1).toFloat())).toInt()
    //            if (bin > numBins - 1 ) bin = numBins-1
    //
    //            var y = (frequencies[bin].toFloat() / freqMax.toFloat() * (data[1].h -1).toFloat()).toInt()
    //            for (j in 0 until y)  newPix[i + (data[1].h - 1 - j)*data[1].w] = (newPix[i + (data[1].h - 100 - j)*data[1].w] + 100).toShort()
    //        }
    ////println("hist")
    //        return STRIMMImage(data[0].sourceCamera, newPix, data[0].timeAcquired, data[0].imageCount, data[0].w, data[0].h)
    //
    //
    //
    //    }
    //
    //
    private fun BinaryOp_Flow(data : List<STRIMMImage>, offset : Float, scale : Float) : STRIMMImage{
        println("binary_op")
        if (data[0].pix == null || data[1].pix == null){
            return STRIMMImage("", null, 0,0,0,0)
        }
        var pix0 = data[0].pix as ShortArray
        var pix1 = data[1].pix as ShortArray
        //binary operation
        var pix2 = ShortArray(pix0.size)
        for (f in 0 until pix0.size){
            var res = (pix0[f].toFloat() / pix1[f].toFloat())*scale + offset
            if (res < 0) res = 0.0F
            if (res > 65000) res = 0xFFFF.toFloat()
            pix2[f] = res.toShort()
        }
        var ret = STRIMMImage(data[0].sourceCamera, pix2, data[0].timeAcquired, data[0].imageCount, data[0].w, data[0].h)
        return ret
    }
    private fun SaveImageFromFlow(data : STRIMMImage) : STRIMMImage {

        var pix = data.pix as ShortArray
        GUIMain.exportService.writeImageDataTmp(3200, 3200,pix);



        return data
    }
    private fun SlowSaveFromFlow(data : STRIMMImage, szCamera : String, w : Long, h : Long) : STRIMMImage {

        // var pix = data.pix as ShortArray
//        if (data.pix != null) {
//            GUIMain.exportService.writeImageData(w, h, data.pix, szCamera);
//        }



        return data
    }
    ////////////////////////////////////////
    var bFirst = true
    var overlay1 = ""
    var overlay2 = ""
    var curValue1 = 0.0
    var curValue2 = 0.0
    var overlayHash = hashMapOf<String, Overlay?>()
    var overlayFirst : String = ""
    var curValue = 0.0
    private fun Add(data : List<List<TraceData>>) : List<List<TraceData>> {
        var pix = data[0]
        var overlay = data[0][0].data.first
        var time = data[0][0].timeAcquired
        if (bFirst){
            overlayFirst = overlay.toString()
            bFirst = false
        }
        if (overlay.toString() == overlayFirst){
            curValue1 = data[0][0].data.second
        }
        else{
            curValue2 = data[0][0].data.second
            curValue = curValue1 + curValue2
        }


        var tr = TraceData( Pair(overlay, curValue), time)
        val li1 = arrayListOf<TraceData>()
        li1.add(tr)


        val traceDataList = arrayListOf<List<TraceData>>() //we have a list of TraceData because we could have lots of data series
        traceDataList.add(li1)

        traceDataList.flatten()
        return traceDataList as List<List<TraceData>>
    }
    private fun TestFn(data : List<List<TraceData>>) : List<List<TraceData>> {
        var val1  = data[0][0].data.second

        println(data.toString()); // + val2.toString())
        println("************");
        return data as List<List<TraceData>>
    }
    var countII : Int = 0
    private fun TraceToImage(data : List<List<TraceData>>) : STRIMMImage {
        var pix = ShortArray(200*200)

        for (i in 0..(pix.size - 1)){
            pix[i] = Random().nextInt((65000 + 1) ).toShort()

        }
        var image : STRIMMImage = STRIMMImage("none", pix, GUIMain.softwareTimerService.getTime(), countII, 200, 200);
        countII++
        return image


    }
    private fun Split(data : List<List<TraceData>>, flow : uk.co.strimm.experiment.Flow) : List<List<TraceData>> {
        var overlay = data[0][0].data.first
        println("values " + data[0][0].data.second + " " + data[0][1].data.second)
        println("overlays " + data[0][0].data.first.toString() + " " + data[0][1].data.first.toString())
        var time = data[0][0].timeAcquired
        if (flow.param1.toInt() == 0){
            curValue = data[0][0].data.second
            overlay = data[0][0].data.first
        }
        else {
            curValue = data[0][1].data.second
            overlay = data[0][1].data.first
        }


        var tr = TraceData( Pair(overlay, curValue), time)
        val li1 = arrayListOf<TraceData>()
        li1.add(tr)


        val traceDataList = arrayListOf<List<TraceData>>() //we have a list of TraceData because we could have lots of data series
        traceDataList.add(li1)

        return traceDataList as List<List<TraceData>>

    }
    /**
     * Go through the experiment config (from json file) and create the appropriate flow objects that correspond to the
     * config. This uses custom wrapper classes for the akka objects. This also specifies types explicitly
     * @param expConfig The experiment config from the JSON config file
     * @param builder The graph building object
     */
    private fun populateFlows(expConfig: ExperimentConfiguration, builder : GraphDSL.Builder<NotUsed>){
        try {
            for (flow in expConfig.flowConfig.flows) {
                if (flow.inputType.toLowerCase() == ExperimentConstants.ConfigurationProperties.IMAGE_INPUT_TYPE.toLowerCase() &&
                    flow.outputType.toLowerCase() == ExperimentConstants.ConfigurationProperties.IMAGE_OUTPUT_TYPE.toLowerCase())
                {//IMAGE TO IMAGE FLOW
                    val expFlow = ExperimentImageImageFlow(flow.flowName)
                    val threshhold = 1200.0
                    if (flow.flowType       == "ThreshholdImage"){
                        val akkaFlow = Flow.of(STRIMMImage::class.java)
                             .map { it ->
                                 ThreshholdImage(
                                     it,
                                     threshhold
                                 )
                             }
                           .async()
                            .named(flow.flowName)
                        expFlow.flow = builder.add(akkaFlow)
                        }
//                    else if (flow.flowType       == "createTest"){
//                        //create a flow of this type
//                        val cls = Class.forName(flow.flowType)
//                        val obj: uk.co.strimm.experiment.Flow = cls.newInstance()  as uk.co.strimm.experiment.Flow
//                        val akkaFlow = Flow.of(STRIMMImage::class.java) //will this work in clojure
//                            .map { it ->
//                                obj.process(it)
//                            }
//                            .async()
//                            .named(flow.flowName)
//                        expFlow.flow = builder.add(akkaFlow)
//                    }
                    else if (flow.flowType       == "FlowReduceSize"){
                        val akkaFlow = Flow.of(STRIMMImage::class.java)
                            .map { it ->
                                FlowReduceSize(
                                    it
                                )
                            }
                            .async()
                            .named(flow.flowName)
                        expFlow.flow = builder.add(akkaFlow)
                    }
                    else if (flow.flowType  == "ReduceFlow"){
                        val akkaFlow = Flow.of(STRIMMImage::class.java)
                            .map { it ->
                                ReduceFlow(
                                    it
                                )
                            }
                            .async()
                            .named(flow.flowName)
                        expFlow.flow = builder.add(akkaFlow)
                    }
                    else if (flow.flowType == "ImageSelectorFlow"){
                        imageSelectorFlow_lastImage[flow.flowName] = STRIMMImage("", null, 0, 0, 0, 0)
                        val akkaFlow = Flow.of(STRIMMImage::class.java)
                            .map { it ->
                                Image_Selector_Flow(
                                it,
                                flow
                                )

                            }
                            .async()
                            .named(flow.flowName)
                        expFlow.flow = builder.add(akkaFlow)
                    }
                    else if (flow.flowType == "ImageReducerFlow"){
                        val akkaFlow = Flow.of(STRIMMImage::class.java)
                            .map { it ->
                                Image_Reducer_Flow(
                                    it,
                                    flow
                                )

                            }
                            .async()
                            .named(flow.flowName)
                        expFlow.flow = builder.add(akkaFlow)
                    }
                    else if (flow.flowType == "ImageDualReducerCombineAdditionFlow"){
                        val akkaFlow = Flow.of(STRIMMImage::class.java)
                            .map { it ->
                                Image_Dual_Reducer_Combine_Addition_Flow(
                                    it,
                                    flow
                                )

                            }
                            .async()
                            .named(flow.flowName)
                        expFlow.flow = builder.add(akkaFlow)
                    }
                    else if (flow.flowType == "ImageDualReducerCombineRatioFlow"){
                        val akkaFlow = Flow.of(STRIMMImage::class.java)
                            .map { it ->
                                Image_Dual_Reducer_Combine_Ratio_Flow(
                                    it,
                                    flow
                                )

                            }
                            .async()
                            .named(flow.flowName)
                        expFlow.flow = builder.add(akkaFlow)
                    }
                    else if (flow.flowType == "BrightFlow"){
                        val akkaFlow = Flow.of(STRIMMImage::class.java)
                            .map { it ->

                                BrightFlow(
                                    it
                                )

                            }
                            .async()
                            .named(flow.flowName)
                        expFlow.flow = builder.add(akkaFlow)
                    }
//                    else if (flow.flowType == "HistogramFlowPair"){
//                        val akkaFlow = Flow.of(STRIMMImage::class.java)
//                            .grouped(2)
//                            .map { it ->
//
//                                Histogram_Flow_Pair(
//                                    it, 0.0, 15000.0, 50
//                                )
//
//                            }
//                            .async()
//                            .named(flow.flowName)
//                        expFlow.flow = builder.add(akkaFlow)
//                    }
                    else if (flow.flowType == "GDIIndicatorPair"){
                        val akkaFlow = Flow.of(STRIMMImage::class.java)
                            .grouped(2)
                            .map { it ->

                                GDI_Indicator_Pair(
                                    it
                                )

                            }
                            .async()
                            .named(flow.flowName)
                        expFlow.flow = builder.add(akkaFlow)
                    }

                    else if (flow.flowType == "HistogramFlow"){
                        val akkaFlow = Flow.of(STRIMMImage::class.java)
                            .map { it ->

                                Histogram_Flow(
                                    it, 0.0, 1000.0, 50
                                )

                            }
                            .async()
                            .named(flow.flowName)
                        expFlow.flow = builder.add(akkaFlow)
                    }
                    else if (flow.flowType == "Ratio_Flow_Float"){
                        imageSelectorFlow_lastImage[flow.flowName] = STRIMMImage("", null, 0, 0, 0, 0)
                        imageSelectorFlow_lastImage[flow.flowName + "_odd"] = STRIMMImage("", null, 0, 0, 0, 0)

                        val akkaFlow = Flow.of(STRIMMImage::class.java)
                            .map { it ->
                                    Image_Ratio_Flow_Float(it, flow)
                            }
                            .async()
                            .named(flow.flowName)
                        expFlow.flow = builder.add(akkaFlow)
                    }
                    else if (flow.flowType == "Ratio_Flow_Short"){
                        imageSelectorFlow_lastImage[flow.flowName] = STRIMMImage("", null, 0, 0, 0, 0)
                        imageSelectorFlow_lastImage[flow.flowName + "_odd"] = STRIMMImage("", null, 0, 0, 0, 0)

                        val akkaFlow = Flow.of(STRIMMImage::class.java)
                            .map { it ->
                                Image_Ratio_Flow_Short(it, flow)
                            }
                            .async()
                            .named(flow.flowName)
                        expFlow.flow = builder.add(akkaFlow)
                    }
                    else if (flow.flowType == "Histogram_Flow_Short"){
                        imageSelectorFlow_lastImage[flow.flowName] = STRIMMImage("", null, 0, 0, 0, 0)
                        imageSelectorFlow_lastImage[flow.flowName + "_odd"] = STRIMMImage("", null, 0, 0, 0, 0)
                        imageSelectorFlow_lastImage[flow.flowName + "_hist"] = STRIMMImage("", null, 0, 0, 0, 0)

                        val akkaFlow = Flow.of(STRIMMImage::class.java)
                            .map { it ->
                                Image_Histogram_Flow_Short(it, flow)
                            }
                            .async()
                            .named(flow.flowName)
                        expFlow.flow = builder.add(akkaFlow)
                    }
                    else if (flow.flowType  == "ConsecutiveImageRatio"){
                        val akkaFlow = Flow.of(STRIMMImage::class.java)
                            .map { it ->
                                PairImageRatio(
                                    it
                                )
                            }
                            .async()
                            .named(flow.flowName)
                        expFlow.flow = builder.add(akkaFlow)
                    }
                    else if (flow.flowType  == "DataTap"){
                        val akkaFlow = Flow.of(STRIMMImage::class.java)
                                .map { it ->
                                    PrintFlow(
                                            it
                                    )
                                }
                                .async()
                                .named(flow.flowName)
                        expFlow.flow = builder.add(akkaFlow)

                    }
                    else { // flow.flowType == "Identity"
                        val akkaFlow = Flow.of(STRIMMImage::class.java)
                         .buffer(ExperimentConstants.ConfigurationProperties.IMAGE_BUFFER_SIZE, OverflowStrategy.dropTail())
                            .async()
                            .named(flow.flowName)
                        expFlow.flow = builder.add(akkaFlow)
                    }
                    expFlow.inputType = flow.inputType
                    expFlow.outputType = flow.outputType
                    experimentImageImageFlows.add(expFlow)
                } else if (flow.inputType.toLowerCase() == ExperimentConstants.ConfigurationProperties.IMAGE_INPUT_TYPE.toLowerCase() &&
                    flow.outputType.toLowerCase() == ExperimentConstants.ConfigurationProperties.TRACE_OUTPUT_TYPE.toLowerCase()
                ) {
                    //IMAGE TO TRACE  ROI *************************************
                    val expFlow = ExperimentImageTraceFlow(flow.flowName)

                    //for this flow, which has a flowName which is a field for each element in rois (to we can sort all of the rois associated with
                    //a particular flow), for each roi construct an overlay - the name is automatically carried over from roi to overlay
                    //we should associate rois with a flow rather than the raw camera image, because it is really an Image->Trace[] function
                    //because we want to make an roi from a display
                    //
                    //could associate roi with camerawindow
                    //could find the input to the camera window and attach a mutli-roi flow which sends a list of rois
                    //
                    //each window display could have an list of ROIs maybe stored in a file location
                    //when the source is made the list is loaded or if the rois are moved the list is updated
                    //insering a camera_display would always involved inserting an roi_flow for that camera
                    //
                    // if list   ====display     ====name_roi_flow====display
                    //                                           ||
                    //                                            ====multi_roi_flow=trace_display
                    //
                    // saving a new json would involve writing a roi list for each display
                    // each json display could have an roi list - there would be no change to the json - the change would occur
                    // to the roi file
                    GUIMain.strimmUIService.traceFromROICounterPerDisplayROIFlow[flow.flowName] = 0

                    GUIMain.strimmUIService.traceColourByFlowAndOverlay[flow.flowName] = ArrayList<Pair<Overlay,Int>>()
                    //this flow will have the displaySink and allows all to be chosen from rois
                    //this could easily be a file
                    val roiForThisFlow = GUIMain.experimentService.expConfig.roiConfig.rois.filter { x -> x.flowName.toLowerCase() == flow.flowName.toLowerCase() }
                    //there might be a set of rois which are associated with STRIMMImage
                    for (roi in roiForThisFlow) {
                        val overlay = ROIManager.createOverlayFromROIObject(roi)
                        //find the associated sink for this flow
                        var displaySink  = GUIMain.experimentService.experimentStream.expConfig.sinkConfig.sinks.filter{snk-> snk.roiFlowName == flow.flowName}.first()


                        //Setting colours for ROIs
                        var colNum =(GUIMain.strimmUIService.traceFromROICounterPerDisplayROIFlow[flow.flowName]) as Int
                        if (colNum < GUIMain.roiColours.size) {
                            var col : Color = GUIMain.roiColours[colNum]
                            var ddd = GUIMain.strimmUIService.traceColourByFlowAndOverlay[flow.flowName] as ArrayList<Pair<Overlay, Int>>
                            ddd.add(Pair(overlay as Overlay, colNum))
                            //ddd?.add(Pair(overlay as Overlay?, ColorRGB(0,0,0)))
                            overlay!!.fillColor = ColorRGB((255.0 * col.red).toInt(), (255.0 * col.green).toInt(), (255.0 * col.blue).toInt())
                            overlay!!.lineColor = ColorRGB((255.0 * col.red).toInt(), (255.0 * col.green).toInt(), (255.0 * col.blue).toInt())
                        }
                        else {
                            overlay!!.fillColor = ColorRGB(100, 100, 100)
                            overlay!!.lineColor = ColorRGB(100, 100, 100)
                        }

                        if (overlay != null) {
                            var curVal : Int = GUIMain.strimmUIService.traceFromROICounterPerDisplayROIFlow[flow.flowName] as Int
                            GUIMain.actorService.routedRoiOverlays[overlay] = flow.flowName
                            GUIMain.strimmUIService.traceFromROICounter++
                            GUIMain.strimmUIService.traceFromROICounterPerDisplayROIFlow[flow.flowName] = curVal + 1
//                            GUIMain.strimmUIService.traceFromROICounter++
                        }
                    }

                    //could switch here based on flowName
                    val averageROIAcquisitionMethod =
                        GUIMain.acquisitionMethodService.getAcquisitionMethod(ExperimentConstants.Acquisition.AVERAGE_ROI_METHOD_NAME) as AcquisitionMethodService.TraceMethod
//this does not have to be made here it could be made when the display is made
                    //
                    //when display is made also makes this -roi_flow__display
                    //                                               |__trace_display
                    val akkaFlow = Flow.of(STRIMMImage::class.java)
                        .map { it -> averageROIAcquisitionMethod.runMethod(it, flow.flowName) }
                        .groupedWithin(
                            ExperimentConstants.ConfigurationProperties.TRACE_GROUPING_AMOUNT,
                            Duration.ofMillis(ExperimentConstants.ConfigurationProperties.TRACE_GROUPING_DURATION_MS)
                        )
                        .async()
                        .named(flow.flowName)

                    expFlow.inputType = flow.inputType
                    expFlow.outputType = flow.outputType
                    expFlow.flow = builder.add(akkaFlow)
                    experimentImageTraceFlows.add(expFlow)
                } else if (flow.inputType.toLowerCase() == ExperimentConstants.ConfigurationProperties.TRACE_INPUT_TYPE.toLowerCase() &&
                    flow.outputType.toLowerCase() == ExperimentConstants.ConfigurationProperties.IMAGE_OUTPUT_TYPE.toLowerCase()
                ) {
                    //TRACE INPUT AND IMAGE OUTPUT
                    val expFlow = ExperimentTraceImageFlow(flow.flowName)
                    val generateImageMethod =
                        GUIMain.acquisitionMethodService.getAcquisitionMethod(ExperimentConstants.Acquisition.GENERATE_IMAGE_METHOD_NAME) as AcquisitionMethodService.ImageMethod

                    val akkaFlow = Flow.of(List::class.java)
//                        .groupedWithin(
//                            ExperimentConstants.ConfigurationProperties.TRACE_GROUPING_AMOUNT,
//                            Duration.ofMillis(ExperimentConstants.ConfigurationProperties.TRACE_GROUPING_DURATION_MS)
//                        )
                        .map { it -> GDI_Flow_Test(it as List<List<TraceData>>) }
                        .async()
                        .named(flow.flowName) as Flow<List<ArrayList<TraceData>>, STRIMMImage, NotUsed> //The "groupedWithin" call turns this into a List of ArrayLists
                    expFlow.inputType = flow.inputType
                    expFlow.outputType = flow.outputType
                    expFlow.flow = builder.add(akkaFlow)
                    experimentTraceImageFlows.add(expFlow)
                } else if (flow.inputType.toLowerCase() == ExperimentConstants.ConfigurationProperties.TRACE_INPUT_TYPE.toLowerCase() &&
                    flow.outputType.toLowerCase() == ExperimentConstants.ConfigurationProperties.TRACE_OUTPUT_TYPE.toLowerCase()
                ) {
                    //TRACE INPUT AND TRACE OUTPUT
                    val expFlow = ExperimentTraceTraceFlow(flow.flowName)
                    if (flow.flowType       == "ChannelSelector") {
                        val numChannels =  GUIMain.protocolService.GetTotalNumberOfChannels()
                        var channel_ixs = GUIMain.protocolService.GetChannelList(flow.flowDetails)
                        val akkaFlow = Flow.of(List::class.java)
                            .map { it ->
                                SelectChannels(
                                    (it as List<List<TraceData>>),
                                    channel_ixs.toIntArray(),
                                    numChannels
                                )
                            }
                            .async()
                            .named(flow.flowName) as Flow<List<ArrayList<TraceData>>, List<ArrayList<TraceData>>, NotUsed> //The "groupedWithin" call turns this into a List of ArrayLists
                        expFlow.inputType = flow.inputType
                        expFlow.outputType = flow.outputType
                        expFlow.flow = builder.add(akkaFlow)

                    }
                    else if (flow.flowType  == "Arduino_Digital_Output"){
                        val akkaFlow = Flow.of(List::class.java)
                            .map { it ->
                                Arduino_Digital_Output(
                                    (it as List<List<TraceData>>)
                                )
                            }
                            .async()
                            .named(flow.flowName) as Flow<List<ArrayList<TraceData>>, List<ArrayList<TraceData>>, NotUsed> //The "groupedWithin" call turns this into a List of ArrayLists
                        expFlow.inputType = flow.inputType
                        expFlow.outputType = flow.outputType
                        expFlow.flow = builder.add(akkaFlow)
                    }
                    else if (flow.flowType  == "Threshold_Flow" ){
                        var threshold = flow.param1
                        var channel = flow.param2.toInt()
                        var type = flow.param3.toInt()
                        val akkaFlow = Flow.of(List::class.java)
                            .map { it ->
                                Threshold_Flow_Controller(
                                    (it as List<List<TraceData>>),
                                    threshold,
                                    channel,
                                    type
                                )
                            }
                            .async()
                            .named(flow.flowName) as Flow<List<ArrayList<TraceData>>, List<ArrayList<TraceData>>, NotUsed> //The "groupedWithin" call turns this into a List of ArrayLists
                        expFlow.inputType = flow.inputType
                        expFlow.outputType = flow.outputType
                        expFlow.flow = builder.add(akkaFlow)
                    }
                    else if (flow.flowType  == "GlobalSourceStartTriggerFlow"){
                        val akkaFlow = Flow.of(List::class.java)
                            .map { it ->
                                GlobalSourceStartTriggerFlow(
                                    (it as List<List<TraceData>>)
                                )
                            }
                            .async()
                            .named(flow.flowName) as Flow<List<ArrayList<TraceData>>, List<ArrayList<TraceData>>, NotUsed> //The "groupedWithin" call turns this into a List of ArrayLists
                        expFlow.inputType = flow.inputType
                        expFlow.outputType = flow.outputType
                        expFlow.flow = builder.add(akkaFlow)
                    }
                    else if (flow.flowType  == "ArduinoDO3" ){
                        val akkaFlow = Flow.of(List::class.java)
                            .map { it ->
                                ArduinoDO3_controller(
                                    (it as List<List<TraceData>>)
                                )
                            }
                            .async()
                            .named(flow.flowName) as Flow<List<ArrayList<TraceData>>, List<ArrayList<TraceData>>, NotUsed> //The "groupedWithin" call turns this into a List of ArrayLists
                        expFlow.inputType = flow.inputType
                        expFlow.outputType = flow.outputType
                        expFlow.flow = builder.add(akkaFlow)
                    }
                    else if (flow.flowType  == "ArduinoDO4" ){
                        val akkaFlow = Flow.of(List::class.java)
                            .map { it ->
                                ArduinoDO4_controller(
                                    (it as List<List<TraceData>>)
                                )
                            }
                            .async()
                            .named(flow.flowName) as Flow<List<ArrayList<TraceData>>, List<ArrayList<TraceData>>, NotUsed> //The "groupedWithin" call turns this into a List of ArrayLists
                        expFlow.inputType = flow.inputType
                        expFlow.outputType = flow.outputType
                        expFlow.flow = builder.add(akkaFlow)
                    }
                    else if (flow.flowType  == "TestFn" ){

                        val akkaFlow = Flow.of(List::class.java)
                            .map { it ->
                                TestFn(
                                    (it as List<List<TraceData>>)
                                )
                            }
                            .async()
                            .named(flow.flowName) as Flow<List<ArrayList<TraceData>>, List<ArrayList<TraceData>>, NotUsed> //The "groupedWithin" call turns this into a List of ArrayLists
                        expFlow.inputType = flow.inputType
                        expFlow.outputType = flow.outputType
                        expFlow.flow = builder.add(akkaFlow)
                    }
                    else if (flow.flowType  ==  "Add" ){
                        val akkaFlow = Flow.of(List::class.java)
                            .map { it ->
                                Add(
                                    (it as List<List<TraceData>>)
                                )
                            }
                            .async()
                            .named(flow.flowName) as Flow<List<ArrayList<TraceData>>, List<ArrayList<TraceData>>, NotUsed> //The "groupedWithin" call turns this into a List of ArrayLists
                        expFlow.inputType = flow.inputType
                        expFlow.outputType = flow.outputType
                        expFlow.flow = builder.add(akkaFlow)
                    }
                    else if (flow.flowType  == "Split" ){
                        val akkaFlow = Flow.of(List::class.java)
                            .map { it ->
                                Split(
                                    (it as List<List<TraceData>>),
                                    flow
                                )
                            }
                            .async()
                            .named(flow.flowName) as Flow<List<ArrayList<TraceData>>, List<ArrayList<TraceData>>, NotUsed> //The "groupedWithin" call turns this into a List of ArrayLists
                        expFlow.inputType = flow.inputType
                        expFlow.outputType = flow.outputType
                        expFlow.flow = builder.add(akkaFlow)
                    }
                    else if (flow.flowType  == "NIDAQ_inject_AO" ){

                        val numSamples = GUIMain.protocolService.GetNextNumSamples()
                        val pInjectAO = DoubleArray(numSamples)

                        val channel = 0
                        val akkaFlow = Flow.of(List::class.java)
                            .map { it ->
                                NIDAQ_inject_AO(
                                    (it as List<List<TraceData>>),
                                    channel,
                                    pInjectAO
                                )
                            }
                            .async()
                            .named(flow.flowName) as Flow<List<ArrayList<TraceData>>, List<ArrayList<TraceData>>, NotUsed> //The "groupedWithin" call turns this into a List of ArrayLists
                        expFlow.inputType = flow.inputType
                        expFlow.outputType = flow.outputType
                        expFlow.flow = builder.add(akkaFlow)
                    }
                    else if (flow.flowType  == "NIDAQ_inject_DO" ){

                        val numSamples = GUIMain.protocolService.GetNextNumSamples()
                        val pInjectDO = IntArray(numSamples)

                        val channel = 0
                        val akkaFlow = Flow.of(List::class.java)
                            .map { it ->
                                NIDAQ_inject_DO(
                                    (it as List<List<TraceData>>),
                                    channel,
                                    pInjectDO
                                )
                            }
                            .async()
                            .named(flow.flowName) as Flow<List<ArrayList<TraceData>>, List<ArrayList<TraceData>>, NotUsed> //The "groupedWithin" call turns this into a List of ArrayLists
                        expFlow.inputType = flow.inputType
                        expFlow.outputType = flow.outputType
                        expFlow.flow = builder.add(akkaFlow)
                    }
                    else {
                        //flowType = "Identity"
                        val akkaFlow = Flow.of(List::class.java)
                            .async()
                            .named(flow.flowName) as Flow<List<ArrayList<TraceData>>, List<ArrayList<TraceData>>, NotUsed> //The "groupedWithin" call turns this into a List of ArrayLists
                        expFlow.inputType = flow.inputType
                        expFlow.outputType = flow.outputType
                        expFlow.flow = builder.add(akkaFlow)
                    }

                    experimentTraceTraceFlows.add(expFlow)

                }
            }
        }
        catch(ex : Exception){
            GUIMain.loggerService.log(Level.SEVERE, "Error creating flows from json experiment configuration. Error message: ${ex.message}")
            GUIMain.loggerService.log(Level.SEVERE, ex.stackTrace)
        }
    }

    /**
     * Go through the experiment config (from json file) and create the appropriate sink objects that correspond to the
     * config. This uses custom wrapper classes for the akka objects. This also specifies types explicitly
     * @param expConfig The experiment config from the JSON config file
     * @param builder The graph building object
     */
    fun cameraFunction( cameraSz : String,  fps : Double, interval : Double,  w : Int, h :Int, pix : Any, bytesPerPixel : Int, timeAcquired : Double  ) : Int{
        if (GUIMain.protocolService.GetCameraMapStatus() > 0) {
            var src =GUIMain.experimentService.expConfig.sourceConfig.sources.filter{x->
                x.sourceName == cameraSz
            }.first()
            src.param1 = timeAcquired
            //throttle to previewInterval
            if (timeAcquired > src.timeLastCaptured + src.previewInterval) {
                src.timeLastCaptured = timeAcquired
                //Send images to cam if within the preview rate
                if (bytesPerPixel == 1) {
                    return GUIMain.protocolService.Add8BitImageDataCameraMap(
                        cameraSz,
                        0.0,
                        0.0,
                        w,
                        h,
                        pix as ByteArray,
                        false
                    )
                } else if (bytesPerPixel == 2) {
                    return GUIMain.protocolService.Add16BitImageDataCameraMap(
                        cameraSz,
                        0.0,
                        0.0,
                        w,
                        h,
                        pix as ShortArray,
                        false
                    )
                } else if (bytesPerPixel == 4) {

                    return GUIMain.protocolService.AddARGBBitImageDataCameraMap(
                        cameraSz,
                        0.0,
                        0.0,
                        w,
                        h,
                        pix as ByteArray,
                        false
                    )

                } else {
                    println("MULTI-CAM ERROR: cameraFunction :  not supported bytesPerPixel")
                    return 0
                }
            }
        }
        return -1
    }

    private fun populateSinks(expConfig: ExperimentConfiguration, builder : GraphDSL.Builder<NotUsed>){
        try {
            for (sink in expConfig.sinkConfig.sinks) {
                if (sink.outputType.toLowerCase() == ExperimentConstants.ConfigurationProperties.IMAGE_OUTPUT_TYPE.toLowerCase()) {
                    if (sink.displayOrStore.toLowerCase() == ExperimentConstants.ConfigurationProperties.DISPLAY.toLowerCase()) {
                        if (sink.sinkType == "Null"){
                            val expSink = ExperimentImageSink(sink.sinkName)
                            expSink.sink = builder.add(Sink.foreach(){x : STRIMMImage->
                                var meanFrameTime : Double = x.timeAcquired.toDouble()/x.imageCount.toDouble()
                                val rate = 1/meanFrameTime*1000.0
                                //println("image received " + x.imageCount + " " + System.nanoTime()/1000)
                                 //if (x.imageCount%2 == 0) cameraFunction(sink.primaryDevice, rate, meanFrameTime, x.w, x.h, (x.pix) as ShortArray)
                                 if (x.imageCount%100==0) {
                                     println("source " + sink.primaryDevice +  " intervalMs " + Math.round(meanFrameTime).toString() + "  " + "fps " + Math.round(rate).toString() )
                                 }
                            })
                            expSink.outputType = sink.outputType
                            expSink.displayOrStore = sink.displayOrStore
                            experimentImageSinks.add(expSink)
                        }
                        else if (sink.sinkType == "MultiCamera") {
                            val expSink = ExperimentImageSink(sink.sinkName)

                            //remove the docking window
//                            val plugin = createCameraPluginWithActor(sink.primaryDevice)


//                            plugin?.dock(GUIMain.strimmUIService.dockableControl, GUIMain.strimmUIService.strimmFrame)
//                            val cameraActor =
//                                cameraActors.filter { x -> x.value.label.toLowerCase() == sink.primaryDevice.toLowerCase() }.keys.first()



                            var sc =
                                expConfig.sourceConfig.sources.filter { x -> x.camera?.label == sink.primaryDevice }
                                    .first()
                            //cameraDevices, cameraSizeList use camera.label as the index
                            //sink has a primaryDevice which will be used to decide the size of the display etc
                            var bytesPerPixel: Int = sc.camera!!.core.bytesPerPixel.toInt()
                            val rois = expConfig.roiConfig.rois.filter{x -> sc.camera!!.label == x.cameraDeviceLabel}
                            val rois_x = rois.map{ it.x.toInt() }.toIntArray()
                            val rois_y = rois.map{ it.y.toInt() }.toIntArray()
                            val rois_w = rois.map{ it.w.toInt() }.toIntArray()
                            val rois_h = rois.map{ it.h.toInt() }.toIntArray()
                            println("registercameraforcameramap******")
                            GUIMain.protocolService.RegisterCameraForCameraMap(
                                sc.camera?.label as String,
                                sc.camera?.core?.imageWidth!!.toInt(),
                                sc.camera?.core?.imageHeight!!.toInt(),
                                bytesPerPixel,
                                1,
                                rois.size,
                                rois_x,
                                rois_y,
                                rois_w,
                                rois_h
                            )
                            //

                            bMultiCam = true

                            //just send each STRIMMImage off to cameraFunction
                            //will render the images with GDIPlus
                            //throttle the flow here?
                            expSink.sink = builder.add(Sink.foreach{x : STRIMMImage->
                                var meanFrameTime : Double = 0.0
                                val rate = 0.0
                                cameraFunction(sink.primaryDevice,
                                    rate,
                                    meanFrameTime,
                                    x.w,
                                    x.h,
                                    (x.pix) as Any,
                                    bytesPerPixel,
                                    x.timeAcquired.toDouble()
                                )

                                //if (x.imageCount%100==0) println("source " + sink.primaryDevice +  " intervalMs " + Math.round(meanFrameTime).toString() + "  " + "fps " + Math.round(rate).toString() + " image map status: " + if (GUIMain.protocolService.GetCameraMapStatus() > 0) "operating" else "not operating")
                            })

                            expSink.outputType = sink.outputType
                            expSink.displayOrStore = sink.displayOrStore
                            experimentImageSinks.add(expSink)
                        }
                        else {

                            //the problem was that the same container CameraDeviceInfo object was being
                            //repeatedly re-referenced.
                            val expSink = ExperimentImageSink(sink.sinkName)
                            //displayInfo contains information about how the surface is to be displayed along with the primaryDevice which
                            //might be used as a default
var displayInfo : DisplayInfo = DisplayInfo(sink.primaryDevice, sink.bitDepth.toLong(), sink.imageWidth.toLong(), sink.imageHeight.toLong(), sink.sinkName)
val plugin : CameraWindowPlugin = GUIMain.dockableWindowPluginService.createPlugin(CameraWindowPlugin::class.java, displayInfo, true, displayInfo.feedName)
plugin.cameraWindowController.roiSz = sink.roiFlowName

//assign lookup table
plugin.cameraWindowController.lutSz = sink.lut
val cameraActor = GUIMain.actorService.getActorByName(displayInfo.feedName)

if (cameraActor != null) {
    cameraActors[cameraActor] = displayInfo.feedName
    cameraActor.tell(TellDisplaySinkName(sink.sinkName), cameraActor)
    cameraActor.tell(TellDisplayAutoscale(sink.autoscale), cameraActor)



}
plugin?.dock(GUIMain.strimmUIService.dockableControl, GUIMain.strimmUIService.strimmFrame)
val akkaSink: Sink<STRIMMImage, NotUsed> = Sink.actorRefWithAck(
    cameraActor, StartStreamingCamera(),
    Acknowledgement.INSTANCE, CompleteCameraStreaming()
) { ex -> FailCameraStreaming(ex) }
expSink.sink = builder.add(akkaSink)
expSink.outputType = sink.outputType
expSink.displayOrStore = sink.displayOrStore
experimentImageSinks.add(expSink)


                            //val plugin = createCameraPluginWithActor(sink.primaryDevice, sink.sinkName)



                            //plugin?.dock(GUIMain.strimmUIService.dockableControl, GUIMain.strimmUIService.strimmFrame)
                           // val cameraActor =
                            //    cameraActors.filter { x -> x.value.toLowerCase() == sink.sinkName.toLowerCase() }.keys.first()
//                            val akkaSink: Sink<STRIMMImage, NotUsed> = Sink.actorRefWithAck(
//                                cameraActor, StartStreamingCamera(),
//                                Acknowledgement.INSTANCE, CompleteCameraStreaming()
//                            ) { ex -> FailCameraStreaming(ex) }
//                            expSink.sink = builder.add(akkaSink)
//                            expSink.outputType = sink.outputType
//                            expSink.displayOrStore = sink.displayOrStore
//                            experimentImageSinks.add(expSink)
                        }
                    } else if (sink.displayOrStore.toLowerCase() == ExperimentConstants.ConfigurationProperties.STORE.toLowerCase()) {
                        //Store the STRIMMImage
                        val expSink = ExperimentImageSink(sink.sinkName)
                        //Create a cameraDataStoreActor - which will collect together all of the STRIMMImages which are sent to it
                        //into a large array and then after a set number of acquisitions send them all to the FileWriterActor which
                        //will either write them as a series of tiff files or package them into an HDF5
                        //a side effect of this function is to fill the freshly created CameraDataStoreActor into the hash map
                        //cameraDataStoreActors which is HashMap<ActorRef, String>
                        val cameraDataStoreActor = createCameraDataStoreActor(sink.sinkName)
                        //Create an Akka Sink which send the List<List<TraceData>> to the cameraDataStore actor
                        //as it is received.  These are stored in an array and then when enough have been received
                        //then are sent to the FileWriterActor to be written as a HDF5 or tiff
                        val akkaSink: Sink<STRIMMImage, NotUsed> = Sink.actorRefWithAck(cameraDataStoreActor, StartCameraDataStoring(),
                                Acknowledgement.INSTANCE, CompleteCameraDataStoring()) { ex -> FailCameraDataStoring(ex) }
                        expSink.sink = builder.add(akkaSink)
                        expSink.outputType = sink.outputType
                        expSink.displayOrStore = sink.displayOrStore
                        experimentImageSinks.add(expSink)
                    }
                } else if (sink.outputType.toLowerCase() == ExperimentConstants.ConfigurationProperties.TRACE_OUTPUT_TYPE.toLowerCase()) {
                    if (sink.displayOrStore.toLowerCase() == ExperimentConstants.ConfigurationProperties.DISPLAY.toLowerCase()) {
                        if (sink.sinkType == "Null"){
                            val expSink = ExperimentTraceSink(sink.sinkName)
                            expSink.sink = builder.add(Sink.foreach{x : List<ArrayList<TraceData>>->
                                0.0
                          })

                            expSink.outputType = sink.outputType
                            expSink.displayOrStore = sink.displayOrStore
                            experimentTraceSinks.add(expSink)
                        }
                        else {
                            //Display a trace in a JavaFx plot
                            //
                            //previously we had the CameraActor and CameraWindow - this was changed so that
                            //the sink contained bitDepth and with and height. Previously it had been getting
                            //that from the source device samplingrate and numberofdatapoints from the source device
                            //
                            //in this case it is getting the
                            val expSink = ExperimentTraceSink(sink.sinkName)
                            GUIMain.loggerService.log(
                                Level.SEVERE,
                                "TERRY___: " + sink.sinkName
                            )
                            GUIMain.loggerService.log(
                                Level.SEVERE,
                                "TERRY :About to enter createTracePluginWithActor"
                            )
                            val pluginCreation = createTracePluginWithActor(sink.sinkName)
                            pluginCreation.second?.dock(
                                GUIMain.strimmUIService.dockableControl,
                                GUIMain.strimmUIService.strimmFrame
                            )
                            GUIMain.loggerService.log(
                                Level.SEVERE,
                                "TERRY :Finished CreateTracePluginWithActor"
                            )
                            val traceActor = pluginCreation.first

                            (pluginCreation.second as TraceWindowPlugin)!!.traceWindowController.sinkName = sink.sinkName
                            //tell traceActor the sink.sinkName
                            if (traceActor != null) {
                                traceActor.tell(TellDisplaySinkName(sink.sinkName), traceActor)
                            }


//                            if (experimentTraceSources.isNotEmpty()) {
//                                try {
//                                    //get the sampling rate and also the number of points
//                                    //
//                                    val traceSourceToUse =
//                                        experimentTraceSources.first { x -> x.deviceLabel == sink.primaryDevice }// && x.channelName == sink.primaryDeviceChannel}
//                                    //find the trace source mentioned via primaryDevice
//                                    //then tell the traceActor the samplingFrequency in Hz
//                                    traceActor?.tell(
//                                        TellDeviceSamplingRate(traceSourceToUse.samplingFrequencyHz),
//                                        ActorRef.noSender()
//                                    )
//                                    //also set the number of datapoints
//                                    traceActor?.tell(TellSetNumDataPoints(), ActorRef.noSender())
//                                } catch (ex: Exception) {
//                                    GUIMain.loggerService.log(
//                                        Level.SEVERE,
//                                        "Exception in creating analogue data stream for trace source. From trace sink ${expSink.traceSinkName}"
//                                    )
//                                    GUIMain.loggerService.log(Level.SEVERE, ex.stackTrace)
//                                }
//                            }
                            //seem to have done it again
                            if (experimentImageSources.isNotEmpty()) {
                                traceActor?.tell(TellDeviceSamplingRate(10.0), ActorRef.noSender())//TODO hardcoded
                                traceActor?.tell(TellSetNumDataPoints(), ActorRef.noSender())
                            }
                            //create the akka,
                            //this will receive tracedata list and will display it had hints previously about the datarate
                            val akkaSink: Sink<List<ArrayList<TraceData>>, NotUsed> = Sink.actorRefWithAck(
                                traceActor, StartStreamingTraceROI(),
                                Acknowledgement.INSTANCE, CompleteStreamingTraceROI()
                            ) { ex -> FailStreamingTraceROI(ex) }
                            expSink.sink = builder.add(akkaSink)
                            expSink.outputType = sink.outputType
                            expSink.displayOrStore = sink.displayOrStore
                            experimentTraceSinks.add(expSink)
                        }
                    } else if (sink.displayOrStore.toLowerCase() == ExperimentConstants.ConfigurationProperties.STORE.toLowerCase()) {
                        //Store the trace.
                        val expSink = ExperimentTraceSink(sink.sinkName)

                        //Create a TraceDataStoreActor which is returned and also stored in the HashMap
                        //traceDataStoreActors = HashMap<ActorRef, String>  where sink.primaryDevice is the String
                        //It is possible that a single trace sink be receiving several traces in series
                        //in particular from several ROIs from the same feed so it could receive packets of the form
                        // [ROI1, ROI2, ROI3]  for 3 ROIs in the same camera feed, and yet it would still be processed
                        // by the same TraceDataStoreActor
                        // In this was this TraceDataStoreActor would be responsible for 3 trace datasets.
                        val traceDataStoreActor = createTraceDataStoreActor(sink.sinkName)/////////PROBLEM
                        if(sink.isROI){
                            traceDataStoreActor.tell(TellIsTraceROIActor(), ActorRef.noSender())
                        }
                        traceDataStoreActor.tell(TellTraceSinkName(sink.sinkName), ActorRef.noSender())
                        //build an Akka sink which will send List<List<TraceData>> to the traceDataStoreActor to be further
                        //saved by the FileWriterActor into an HDF5 file or saved as csv.
                        val akkaSink: Sink<List<ArrayList<TraceData>>, NotUsed> = Sink.actorRefWithAck(traceDataStoreActor, StartTraceDataStoring(),
                                Acknowledgement.INSTANCE, CompleteTraceDataStoring()) { ex -> FailTraceDataStoring(ex) }
                        expSink.sink = builder.add(akkaSink)
                        expSink.outputType = sink.outputType
                        expSink.displayOrStore = sink.displayOrStore
                        experimentTraceSinks.add(expSink)
                    }
                }
            }
//            for (cam in cameraActors){
//                cam.key.tell(uk.co.strimm.actors.messages.ask.AskTW("After creating sinks" + cam.key.toString())  , ActorRef.noSender())
//            sleep(2000)
//            }

        }
        catch(ex : Exception){
            GUIMain.loggerService.log(Level.SEVERE, "Error creating sinks from json experiment configuration. Error message: ${ex.message}")
            GUIMain.loggerService.log(Level.SEVERE, ex.stackTrace)
        }
    }
    //endregion

    /**
     * Get any node that is going out of the given node
     * Note - A sink cannot have an outlet (connections cannot go out from a sink)
     * @param currentNode The experiment node that the outlets are going out of
     * @param expConfig The experiment configuration (from json)
     */
    private fun getOutlets(currentNode : ExperimentNode, expConfig: ExperimentConfiguration) : ArrayList<ExperimentNode>{
        val outlets = arrayListOf<ExperimentNode>()
        try {
            for (flow in expConfig.flowConfig.flows) {
                flow.inputNames.filter{x -> x == currentNode.name}
                    .forEach {
                        outlets.addAll(experimentImageSources.filter { x -> x.imgSourceName == flow.flowName })
                        outlets.addAll(experimentTraceSources.filter { x -> x.traceSourceName == flow.flowName })
                        outlets.addAll(experimentImageImageFlows.filter { x -> x.imgImgFlowName == flow.flowName })
                        outlets.addAll(experimentImageTraceFlows.filter { x -> x.imgTraceFlowName == flow.flowName })
                        outlets.addAll(experimentTraceImageFlows.filter { x -> x.traceImgFlowName == flow.flowName })
                        outlets.addAll(experimentTraceTraceFlows.filter { x -> x.traceTraceFlowName == flow.flowName })
                        outlets.addAll(experimentImageSinks.filter { x -> x.imageSinkName == flow.flowName })
                        outlets.addAll(experimentTraceSinks.filter { x -> x.traceSinkName == flow.flowName })
                    }
            }

            for (sink in expConfig.sinkConfig.sinks) {
                val sinksAttachedToNode = sink.inputNames.filter{x -> x == currentNode.name}
                sinksAttachedToNode.forEach{
                    outlets.addAll(experimentImageSources.filter { x -> x.imgSourceName == sink.sinkName })
                    outlets.addAll(experimentTraceSources.filter { x -> x.traceSourceName == sink.sinkName })
                    outlets.addAll(experimentImageImageFlows.filter { x -> x.imgImgFlowName == sink.sinkName })
                    outlets.addAll(experimentImageTraceFlows.filter { x -> x.imgTraceFlowName == sink.sinkName })
                    outlets.addAll(experimentTraceImageFlows.filter { x -> x.traceImgFlowName == sink.sinkName })
                    outlets.addAll(experimentTraceTraceFlows.filter { x -> x.traceTraceFlowName == sink.sinkName })
                    outlets.addAll(experimentImageSinks.filter { x -> x.imageSinkName == sink.sinkName })
                    outlets.addAll(experimentTraceSinks.filter { x -> x.traceSinkName == sink.sinkName })
                    }
            }
        }
        catch(ex : Exception){
            GUIMain.loggerService.log(Level.SEVERE, "Error populating outlets for graph nodes from json experiment configuration. Error message: ${ex.message}")
            GUIMain.loggerService.log(Level.SEVERE, ex.stackTrace)
        }

        return outlets
    }

    /**
     * Get any node that is going into the given node
     * Note - A source cannot have an inlet (connections cannot go into a source)
     * @param currentNode The experiment node that the inlets are going into
     * @param expConfig The experiment configuration (from json)
     */
    private fun getInlets(currentNode : ExperimentNode, expConfig: ExperimentConfiguration) : ArrayList<ExperimentNode>{
        val inlets = arrayListOf<ExperimentNode>()
        try {
            for (flow in expConfig.flowConfig.flows) {
                if (flow.flowName == currentNode.name) { //Find the flow relating to currentNode
                    //Find any source, flows or sinks that have an inputName corresponding this the current node's input
                    inlets.addAll(experimentImageSources.filter { x -> x.imgSourceName in flow.inputNames })
                    inlets.addAll(experimentTraceSources.filter { x -> x.traceSourceName in flow.inputNames })
                    inlets.addAll(experimentImageImageFlows.filter { x -> x.imgImgFlowName in flow.inputNames })
                    inlets.addAll(experimentImageTraceFlows.filter { x -> x.imgTraceFlowName in flow.inputNames })
                    inlets.addAll(experimentTraceImageFlows.filter { x -> x.traceImgFlowName in flow.inputNames })
                    inlets.addAll(experimentTraceTraceFlows.filter { x -> x.traceTraceFlowName in flow.inputNames })
                    inlets.addAll(experimentImageSinks.filter { x -> x.imageSinkName in flow.inputNames })
                    inlets.addAll(experimentTraceSinks.filter { x -> x.traceSinkName in flow.inputNames })
                }
            }

            for (sink in expConfig.sinkConfig.sinks) {
                if (sink.sinkName == currentNode.name) { //Find the flow relating to currentNode
                    //Find any source, flows or sinks that have an inputName corresponding this the current node's input
                    inlets.addAll(experimentImageSources.filter { x -> x.imgSourceName in sink.inputNames })
                    inlets.addAll(experimentTraceSources.filter { x -> x.traceSourceName in sink.inputNames })
                    inlets.addAll(experimentImageImageFlows.filter { x -> x.imgImgFlowName in sink.inputNames })
                    inlets.addAll(experimentImageTraceFlows.filter { x -> x.imgTraceFlowName in sink.inputNames })
                    inlets.addAll(experimentTraceImageFlows.filter { x -> x.traceImgFlowName in sink.inputNames })
                    inlets.addAll(experimentTraceTraceFlows.filter { x -> x.traceTraceFlowName in sink.inputNames })
                    inlets.addAll(experimentImageSinks.filter { x -> x.imageSinkName in sink.inputNames })
                    inlets.addAll(experimentTraceSinks.filter { x -> x.traceSinkName in sink.inputNames })
                }
            }
        }
        catch (ex : Exception){
            GUIMain.loggerService.log(Level.SEVERE, "Error populating inlets for graph nodes from json experiment configuration. Error message: ${ex.message}")
            GUIMain.loggerService.log(Level.SEVERE, ex.stackTrace)
        }

        return inlets
    }

    /**
     * From the experiment config, go through the loaded camera devices. If the config has a configuration matching
     * a loaded camera device, add it to a list of camera devices
     * @param expConfig The loaded experiment configuration
     */
    fun populateListOfCameraDevices(expConfig: ExperimentConfiguration){
        //println("ExperimentStream::populateListOfCameraDevices(expConfig: ExperimentConfiguration)")
        for(src in expConfig.sourceConfig.sources) {
            //println("source " + src.toString())
            if (src.camera != null) {
//                println("*****************")
//                println(src.sourceName);
//
//                println("src.camera is not null")
//                println(src.camera!!.name)
//                println(src.camera!!.library)
//                println(src.camera!!.label)
//                println(src.camera!!.core.toString())

                var dev = MMCameraDevice(src.camera) //this would set the label, libr, etc and the core
                dev.label = src.deviceLabel
                cameraDevices.add(CameraDeviceInfo(dev, false, src.exposureMs, src.intervalMs, "", label=src.deviceLabel))
                dev.isActive = true
                //JOptionPane.showMessageDialog(null, "pop " + src.sourceName);
                noteCameraSize(dev)
           }
        }
    }

//    /**
//     * Initialise the camera device with any relevant info e.g. exposure
//     * @param info The camera device and relevant info as a data class
//     */
//    fun initialiseCamera(info : CameraDeviceInfo){
//        info.device.exposure = info.exposureMillis
////        MMCoreService.core.setProperty("retigaCam","Mode","Color Test Pattern")
//
//        info.device.initialise()
//        info.device.isActive = true
//        noteCameraSize(info.device)
//        GUIMain.loggerService.log(Level.INFO, "Camera ${info.device.label} has been initialized")
//    }

    /**
     * When creating and using a camera device, make a note of its image size for future reference. It will also make
     * a note if a size different to full size has been specified
     * @param camera The camera device
     */
    fun noteCameraSize(camera : MMCameraDevice){
       // println("ExperimentStream::noteCameraSize(camera : MMCameraDevice)")
        val camSizes = Pair(camera.imageWidth, camera.imageHeight)
       // println(camera.imageWidth.toString() + " " + camera.imageHeight)
        GUIMain.strimmUIService.cameraSizeList[camera.label] = camSizes
        println(camera.label);

        val camSourceConfig = expConfig.sourceConfig.sources.first { x -> x.sourceName == camera.label }

       // println(camSourceConfig.toString());
        if(camSourceConfig.x < 0.0 || camSourceConfig.y < 0.0){
            GUIMain.loggerService.log(Level.WARNING, "x or y coordinate for camera device ${camera.label} is less than zero, using full size coordinates instead")
        }
        else if(camSourceConfig.w <= 0.0 || camSourceConfig.h <= 0.0){
            GUIMain.loggerService.log(Level.WARNING, "w or h coordinate for camera device ${camera.label} is less than or equal to zero, using full size coordinates instead")
        }
        else if((camSourceConfig.x >= 0 && camSourceConfig.y >= 0) && camSourceConfig.w > 0.0 && camSourceConfig.h > 0.0) {
            GUIMain.strimmUIService.cameraViewSizeList[camera.label] = ResizeValues(camSourceConfig.x.toLong(), camSourceConfig.y.toLong(), camSourceConfig.w.toLong(), camSourceConfig.h.toLong())
        }
    }

    /**
     * Tell all storage based actors to start acquiring data
     */
    fun startStoringData(){
        println("ExperimentStream::startStoringData()")

        cameraDataStoreActors.forEach { x -> x.key.tell(StartAcquiring(), ActorRef.noSender()) }
        traceDataStoreActors.forEach { x -> x.key.tell(StartTraceStore(), ActorRef.noSender()) }
    }

    /**
     * This method is called when stopping an experiment. It will send a TerminateActor() message to all store-based
     * actors
     */
    fun stopStoringData(){
//        GUIMain.protocolService.WinInitSpeechEngine()
//        GUIMain.protocolService.WinSpeak("Stop storing data")
//        GUIMain.protocolService.WinShutdownSpeechEngine()
        //
        GUIMain.acquisitionMethodService.bCamerasAcquire = false;
        println("ExperimentStream::stopStoringData()")
        GUIMain.loggerService.log(Level.INFO, "Stopping data store actors")

        cameraDataStoreActors.forEach { x ->
            GUIMain.actorService.removeActor(x.key)
            x.key.tell(PoisonPill.getInstance(), ActorRef.noSender())
        }
        traceDataStoreActors.forEach { x ->
            GUIMain.actorService.removeActor(x.key)
            x.key.tell(PoisonPill.getInstance(), ActorRef.noSender())
        }
    }

    /**
     * This method is called when stopping an experiment. It will send a TerminateActor() message to all display-based
     * actors
     */
    fun stopDisplayingData(){
//        GUIMain.protocolService.WinInitSpeechEngine()
//        GUIMain.protocolService.WinSpeak("Stop displaying data")
//        GUIMain.protocolService.WinShutdownSpeechEngine()



        println("ExperimentStream::stopDisplayingData()")
        GUIMain.acquisitionMethodService.bCamerasAcquire = false;
        GUIMain.loggerService.log(Level.INFO, "Stopping display actors")
        //TW hack
        GUIMain.protocolService.SetEndSources()



        //these cause the camera actor to kill itself
        //the camera actor is the place that the sink sends the
        //data to in a message
        //so the graph is still there
        //pumping away
        //but without a display.....
        cameraActors.forEach{ x ->
            x.key.tell(TerminateActor(), ActorRef.noSender())
            GUIMain.actorService.removeActor(x.key)
            //for now
//            x.value.stopLive()
//            x.value.isActive = false // is this necessary
        }

        traceActors.forEach{ x ->
            x.key.tell(TerminateActor(), ActorRef.noSender())
            GUIMain.actorService.removeActor(x.key)
        }

        //so now the camera should not be running
        //only it is
        //I ran a pointgrey then changed to a ximea and the record shows that
        //the pointgrey and the ximea were both requesting images
        //but the pointgrey should not exist now.....




    }

    /**
     * This method will create both a camera display dockable window plugin and an associated camera actor to go with it
     * @param cameraDeviceLabel The label of the camera whose data the plugin will be displaying
     * @return The newly created dockable window plugin
     */

    fun createCameraPluginWithActor(cameraDeviceLabel: String, pluginName : String) : DockableWindowPlugin?{
//where is the CameraWindow created

//retrieve the CameradeviceInfo for the camera device eg OpenCV in the Sink that is being created
        var cameraDeviceInfo = cameraDevices.filter{ x -> x.device.label == cameraDeviceLabel}.first()
        cameraDeviceInfo = CameraDeviceInfo(cameraDeviceInfo.device, false, cameraDeviceInfo.exposureMillis, cameraDeviceInfo.intervalMillis, pluginName, cameraDeviceLabel)

//use this cameraDeviceInfo to make a plugin - so this will make the plugin based on the OpenCV dimensions etc
        //this will create an object of type CameraWindowPlugin::class.java
        val plugin = GUIMain.dockableWindowPluginService.createPlugin(CameraWindowPlugin::class.java, cameraDeviceInfo, true, pluginName)
//make a cameraActor
        val newCameraActor = GUIMain.actorService.getActorByName(pluginName)  //does not create the actor
//add to the register of camera actors
        if(newCameraActor != null){
            cameraActors[newCameraActor] = pluginName
        }

        return plugin
    }

    /**
     * This method will create both a trace display dockable window plugin and an associated trace actor to go with it.
     * @param traceDeviceLabel The label of the trace device relating to this trace display
     * @return A pair, where the first value is the newly created trace actor, the second value is the newly created
     * dockable window plugin
     */
    fun createTracePluginWithActor(traceDeviceLabel : String) : Pair<ActorRef?, DockableWindowPlugin?> {
        //Make sure the actor name is truly unique
        val numStoreActorsForDevice = traceActors.count { x -> x.value == traceDeviceLabel }
        val traceActorName = if(numStoreActorsForDevice > 0) {
            "${traceDeviceLabel}TraceActor${numStoreActorsForDevice + 1}"
        }
        else{
            "${traceDeviceLabel}TraceActor1"
        }
        GUIMain.loggerService.log(
            Level.SEVERE,
            "TERRY :TraceActor name " + traceActorName
        )
        GUIMain.loggerService.log(
            Level.SEVERE,
            "TERRY :About to createPlugin  "
        )
        val plugin = GUIMain.dockableWindowPluginService.createPlugin(TraceWindowPlugin::class.java, "", true, traceActorName)
        GUIMain.loggerService.log(
            Level.SEVERE,
            "TERRY :TraceActor :Finished createPlugin "
        )
        if (plugin == null) {
            GUIMain.loggerService.log(
                Level.SEVERE,
                "TERRY :Plugin null"
            )
        }
        else {
            GUIMain.loggerService.log(
                Level.SEVERE,
                "TERRY :Plugin not null"
            )

        }
        GUIMain.loggerService.log(
            Level.SEVERE,
            "TERRY :Retrieve the actorRef from actorService given the traceActorName"
        )

        val newTraceActor = GUIMain.actorService.getActorByName(traceActorName)
        if(newTraceActor != null){
            GUIMain.loggerService.log(
                Level.SEVERE,
                "TERRY :Found the traceActor by its name and add to the traceActors hash"
            )
            traceActors[newTraceActor] = traceDeviceLabel
        }
        else {
            GUIMain.loggerService.log(
                Level.SEVERE,
                "TERRY :Could not find the traceActor by its name and add to the traceActors hash"
            )
        }

        return Pair(newTraceActor, plugin)
    }

    /**
     * Create a camera data store actor. There will be one camera data store actor per camera feed (providing the user
     * has chosen to store the camera feed's data)
     * @param cameraDeviceLabel The camera device's label
     * @return The newly created camera data store actor
     */
    fun createCameraDataStoreActor(cameraDataStoreActorName : String) : ActorRef {
        //Make sure the actor name is truly unique
        val uniqueActorName = GUIMain.actorService.makeActorName(cameraDataStoreActorName)

        val cameraDataStoreActor = GUIMain.actorService.createActor(CameraDataStoreActor.props(), uniqueActorName, CameraDataStoreActor::class.java)

        cameraDataStoreActors[cameraDataStoreActor] = cameraDataStoreActorName
        return cameraDataStoreActor
    }

    /**
     * Create a trace data store actor. There will be one trace data store actor per trace feed (providing the user
     * has chosen to store the trace feed's data)
     * @param traceDeviceLabel The trace device's label
     * @return The newly created trace data store actor
     */
    fun createTraceDataStoreActor(traceDeviceLabel : String) : ActorRef {
        //Make sure the actor name is truly unique
        val numStoreActorsForDevice = traceDataStoreActors.count { x -> x.value == traceDeviceLabel }
        val traceDataStoreActorName = if(numStoreActorsForDevice > 0) {
            "${traceDeviceLabel}TraceDataStoreActor${numStoreActorsForDevice + 1}"
        }
        else{
            "${traceDeviceLabel}TraceDataStoreActor1"
        }

        val uniqueActorName = GUIMain.actorService.makeActorName(traceDataStoreActorName)

        val traceDataStoreActor = GUIMain.actorService.createActor(TraceDataStoreActor.props(), uniqueActorName, TraceDataStoreActor::class.java)
        traceDataStoreActors[traceDataStoreActor] = traceDeviceLabel
        return traceDataStoreActor
    }
}