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
import uk.co.strimm.ExperimentConstants.Acquisition.Companion.AVERAGE_ROI_METHOD_NAME
import uk.co.strimm.ExperimentConstants.Acquisition.Companion.A_KEYBOARD_METHOD_NAME
import uk.co.strimm.ExperimentConstants.Acquisition.Companion.CONFIGURED_CAMERA_METHOD_NAME
import uk.co.strimm.ExperimentConstants.Acquisition.Companion.CONSTANT_TRACE_SOURCE_METHOD_NAME
import uk.co.strimm.ExperimentConstants.Acquisition.Companion.CONSTANT_VECTOR_SOURCE_METHOD_NAME
import uk.co.strimm.ExperimentConstants.Acquisition.Companion.RANDOM_TRACE_SOURCE_METHOD_NAME
import uk.co.strimm.ExperimentConstants.Acquisition.Companion.SINE_WAVE_SOURCE_METHOD_NAME
import uk.co.strimm.ExperimentConstants.Acquisition.Companion.SQUARE_WAVE_SOURCE_METHOD_NAME
import uk.co.strimm.ExperimentConstants.Acquisition.Companion.TRACE_DATA_KEYBOARD_METHOD_NAME
import uk.co.strimm.ExperimentConstants.Acquisition.Companion.TRACE_DATA_NIDAQ_METHOD_NAME
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

//TODO this class is too big, will need to split into several classes long term
class ExperimentStream(val expConfig: ExperimentConfiguration, loadCameraConfig: Boolean) {
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

    var lastImageFlow = hashMapOf<String, STRIMMImage>()
    var numConnectionsSpecified = 0
    var numConnectionsMade = 0
    var experimentName = expConfig.experimentConfigurationName
    var cameraDevices = arrayListOf<CameraDeviceInfo>()

    var stream: RunnableGraph<NotUsed>? = null
    var durationMs = 0
    var numCameras = 0
    var bMultiCam = false
    var timeStart = 0.0
    var isRunning = false

    init {
        populateListOfCameraDevices(expConfig)
    }

    private fun createStreamGraph(expConfig: ExperimentConfiguration): Graph<ClosedShape, NotUsed> {
        try {
            return (GraphDSL.create { builder ->
                durationMs = expConfig.experimentDurationMs
                //Build the graph objects from the experiment config
                populateLists(expConfig, builder)

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

                //TODO should we also validate what connections have gone to where (in addition to checking the number)?
                ClosedShape.getInstance()
            })
        } catch (ex: Exception) {
            throw ex
        }
    }

    fun createStream(expConfig: ExperimentConfiguration): RunnableGraph<NotUsed>? {
        try {
            setNumberOfSpecifiedConnections(expConfig)
            val graph = createStreamGraph(expConfig)

            val streamGraph = RunnableGraph.fromGraph(graph)

            stream = streamGraph
            return stream
        } catch (ex: Exception) {
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
    fun runStream(liveAcquisition: Boolean) {
        isRunning = true
        if (GUIMain.experimentService.expConfig.experimentMode != "Preview") {
            GUIMain.experimentService.expConfig.sourceConfig.sources.forEach { x -> x.camera?.StartAcquisition() }
            startStoringData()

            GUIMain.actorService.fileWriterActor.tell(TellCreateFile(), ActorRef.noSender())

            //Tell fileWriter about the existence of each cameraDataStoreActor - it is assumed each will produce 1 dataset
            cameraDataStoreActors.forEach { GUIMain.actorService.fileWriterActor.tell(TellNewCameraDataStore(), ActorRef.noSender()) }

            /**
             * Tell the fileWriter about each traceDataStoreActor it is possible that there are 1+ roi associated with
             * each actor - where the data arrives in a serial manner.
             * Find out how many datasets are in each traceDataStore
             */
            traceDataStoreActors.forEach {
                //Tell the file writer actor about each trace data store actor, both trace and trace from ROI
                val isTraceFromROIActor = GUIMain.actorService.askTraceActorIfIsTraceROI(it.key)!!
                if (isTraceFromROIActor)
                    GUIMain.actorService.fileWriterActor.tell(TellNewTraceROIDataStore(), ActorRef.noSender())
                else {
                    GUIMain.actorService.fileWriterActor.tell(TellNewTraceDataStore(), ActorRef.noSender())
                }
            }
        } else {
            GUIMain.loggerService.log(Level.INFO, "Starting preview acquisition")
            GUIMain.expStartStopButton.isEnabled = true
            GUIMain.experimentService.expConfig.sourceConfig.sources.forEach { x -> x.camera?.StartAcquisition() }
        }

        if (bMultiCam) {
            GUIMain.protocolService.StartCameraMap()
        }

        stream?.run(GUIMain.actorService.materializer)
        timeStart = GUIMain.protocolService.GetCurrentSystemTime()
        GUIMain.softwareTimerService.setFirstTimeMeasurement()
        GUIMain.acquisitionMethodService.bCamerasAcquire = true
    }

    /**
     * Log information about the number of connections specified and the number of connections actually made
     */
    private fun checkNumberOfConnections() {
        if (numConnectionsMade == numConnectionsSpecified) {
            GUIMain.loggerService.log(Level.INFO, "Number of connections specified is equal to number of connections made (this is good)." +
                    " Number of specified connections is $numConnectionsSpecified, number of connections made is $numConnectionsMade")
        } else {
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
    private fun setNumberOfSpecifiedConnections(expConfig: ExperimentConfiguration) {
        numConnectionsSpecified += expConfig.flowConfig.flows.map { x -> x.inputNames.size }.sum()
        numConnectionsSpecified += expConfig.sinkConfig.sinks.map { x -> x.inputNames.size }.sum()
    }

    /**
     * Method to log a load of basic info about the experiment stream being created
     */
    private fun logInfo() {
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

        GUIMain.loggerService.log(Level.INFO, sb.toString())
    }

    //region Graph building
    /**
     * build the graph based on sources, flows and sinks as per the Akka API specifications
     * @param builder The graph building object
     */
    private fun buildGraph(builder: GraphDSL.Builder<NotUsed>) {
        buildSourceGraphParts(builder)
        buildFlowGraphParts(builder)
    }

    /**
     * This method calls all source graph building methods
     * @param builder The graph building object
     */
    private fun buildSourceGraphParts(builder: GraphDSL.Builder<NotUsed>) {
        GUIMain.loggerService.log(Level.INFO, "Building source parts")
        buildImageSourceParts(builder)
        buildTraceSourceParts(builder)
    }

    /**
     * This method calls all flow graph building methods
     * @param builder The graph building object
     */
    private fun buildFlowGraphParts(builder: GraphDSL.Builder<NotUsed>) {
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
    private fun buildImageSourceParts(builder: GraphDSL.Builder<NotUsed>) {
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
                            is ExperimentTraceImageFlow -> {
                                throw Exception("image source ${imageSource.imgSourceName} is connected to a trace-to-image flow. The flow input type must be the same as the source output type")
                            }
                            is ExperimentTraceTraceFlow -> {
                                throw Exception("image source ${imageSource.imgSourceName} is connected to a trace-to-trace flow. The flow input type must be the same as the source output type")
                            }
                            is ExperimentImageSink -> { //Join to a sink
                                builder.from(imageSource.bcastObject).to(outlet.sink)
                                if (outlet.mergeObject != null) {
                                    GUIMain.loggerService.log(Level.INFO, "Creating connection from ${imageSource.imgSourceName} (broadcast) toFanIn ${outlet.imageSinkName} (merge)")

                                    //Special case that only needs to be applied for image displays
                                    if (outlet.displayOrStore == ExperimentConstants.ConfigurationProperties.DISPLAY) {
                                        val bufferFlow = builder.add(Flow.of(STRIMMImage::class.java)
                                                //TODO this was added in previously to fix a bug when using continuous mode
                                                // .buffer(ExperimentConstants.ConfigurationProperties.IMAGE_BUFFER_SIZE, OverflowStrategy.dropTail())
                                                .async()
                                                .named("BufferFlow"))
                                        builder.from(imageSource.bcastObject).via(bufferFlow).toFanIn(outlet.mergeObject)
                                    } else {
                                        builder.from(imageSource.bcastObject).toFanIn(outlet.mergeObject)
                                    }

                                    numConnectionsMade++
                                } else {
                                    GUIMain.loggerService.log(Level.INFO, "Creating connection from ${imageSource.imgSourceName} (broadcast) to ${outlet.imageSinkName}")

                                    //Special case that only needs to be applied for image displays
                                    if (outlet.displayOrStore == ExperimentConstants.ConfigurationProperties.DISPLAY) {
                                        val bufferFlow = builder.add(Flow.of(STRIMMImage::class.java)
                                                //TODO this was added in previously to fix a bug when using continuous mode
                                                //.buffer(ExperimentConstants.ConfigurationProperties.IMAGE_BUFFER_SIZE, OverflowStrategy.dropTail())
                                                .async()
                                                .named("BufferFlow"))

                                        builder.from(imageSource.bcastObject).via(bufferFlow).to(outlet.sink)
                                    } else {
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
                                if (outlet.mergeObject != null) {
                                    val fwdOp = ExperimentImageFwdOp(imageSource.imgSourceName, outlet.imgImgFlowName)
                                    GUIMain.loggerService.log(Level.INFO, "Creating connection from ${imageSource.imgSourceName} viaFanIn ${outlet.imgImgFlowName} (merge)")
                                    fwdOp.fwdOp = builder.from(imageSource.source).viaFanIn(outlet.mergeObject)
                                    outlet.mergeFwdOp = fwdOp
                                    numConnectionsMade++
                                } else {
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
                            is ExperimentTraceImageFlow -> {
                                throw Exception("image source ${imageSource.imgSourceName} is connected to a trace-to-image flow. The flow input type must be the same as the source output type")
                            }
                            is ExperimentTraceTraceFlow -> {
                                throw Exception("image source ${imageSource.imgSourceName} is connected to a trace-to-trace flow. The flow input type must be the same as the source output type")
                            }
                            is ExperimentImageSink -> {
                                if (outlet.mergeObject != null) {
                                    GUIMain.loggerService.log(Level.INFO, "Creating connection from ${imageSource.imgSourceName} toFanIn ${outlet.imageSinkName} (merge)")

                                    //Special case that only needs to be applied for image displays
                                    if (outlet.displayOrStore == ExperimentConstants.ConfigurationProperties.DISPLAY) {
                                        val bufferFlow = builder.add(Flow.of(STRIMMImage::class.java)
                                                //TODO this was added in previously to fix a bug when using continuous mode
                                                //.buffer(ExperimentConstants.ConfigurationProperties.IMAGE_BUFFER_SIZE, OverflowStrategy.dropTail())
                                                .async()
                                                .named("BufferFlow"))
                                        builder.from(imageSource.source).via(bufferFlow).toFanIn(outlet.mergeObject)
                                    } else {
                                        builder.from(imageSource.source).toFanIn(outlet.mergeObject)
                                    }

                                    numConnectionsMade++
                                } else {
                                    GUIMain.loggerService.log(Level.INFO, "Creating connection from ${imageSource.imgSourceName} to ${outlet.imageSinkName}")

                                    //Special case that only needs to be applied for image displays
                                    if (outlet.displayOrStore == ExperimentConstants.ConfigurationProperties.DISPLAY) {
                                        val bufferFlow = builder.add(Flow.of(STRIMMImage::class.java)
                                                //TODO this was added in previously to fix a bug when using continuous mode
                                                //.buffer(ExperimentConstants.ConfigurationProperties.IMAGE_BUFFER_SIZE, OverflowStrategy.dropTail())
                                                .async()
                                                .named("BufferFlow"))
                                        builder.from(imageSource.source).via(bufferFlow).to(outlet.sink)
                                    } else {
                                        builder.from(imageSource.source).to(outlet.sink)
                                    }

                                    numConnectionsMade++
                                }
                            }
                        }
                    }
                }
            }
        } catch (ex: Exception) {
            GUIMain.loggerService.log(Level.SEVERE, "Error building connections for image sources. Error message: ${ex.message}")
            GUIMain.loggerService.log(Level.SEVERE, ex.stackTrace)
        }
    }

    /**
     * Specify the graph connections for trace sources. Note that the partial connections (forward ops) are stored
     * with the outlet node (flow or sink) and not with the source
     * @param builder The graph building object
     */
    private fun buildTraceSourceParts(builder: GraphDSL.Builder<NotUsed>) {
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
                            is ExperimentImageTraceFlow -> {
                                throw Exception("trace source ${traceSource.traceSourceName} is connected to a image-to-trace flow. The flow input type must be the same as the source output type")
                            }
                            is ExperimentImageImageFlow -> {
                                throw Exception("trace source ${traceSource.traceSourceName} is connected to a image-to-image flow. The flow input type must be the same as the source output type")
                            }
                            is ExperimentTraceSink -> {
                                if (outlet.mergeObject != null) {
                                    GUIMain.loggerService.log(Level.INFO, "Creating connection from ${traceSource.traceSourceName} (broadcast) toFanIn ${outlet.traceSinkName} (merge)")
                                    builder.from(traceSource.bcastObject).toFanIn(outlet.mergeObject)
                                    numConnectionsMade++
                                } else {
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
                            is ExperimentImageTraceFlow -> {
                                throw Exception("trace source ${traceSource.traceSourceName} is connected to a image-to-trace flow. The flow input type must be the same as the source output type")
                            }
                            is ExperimentImageImageFlow -> {
                                throw Exception("trace source ${traceSource.traceSourceName} is connected to a image-to-image flow. The flow input type must be the same as the source output type")
                            }
                            is ExperimentTraceSink -> {
                                if (outlet.mergeObject != null) {
                                    GUIMain.loggerService.log(Level.INFO, "Creating connection from ${traceSource.traceSourceName} toFanIn ${outlet.traceSinkName} (merge)")
                                    builder.from(traceSource.source).toFanIn(outlet.mergeObject)
                                    numConnectionsMade++
                                } else {
                                    GUIMain.loggerService.log(Level.INFO, "Creating connection from ${traceSource.traceSourceName} to ${outlet.traceSinkName}")
                                    builder.from(traceSource.source).to(outlet.sink)
                                    numConnectionsMade++
                                }
                            }
                        }
                    }
                }
            }
        } catch (ex: Exception) {
            GUIMain.loggerService.log(Level.SEVERE, "Error building connections for trace sources. Error message: ${ex.message}")
            GUIMain.loggerService.log(Level.SEVERE, ex.stackTrace)
        }
    }

    /**
     * Specify the graph connections for trace-to-image flows
     * @param builder The graph building object
     */
    private fun buildTraceImageFlowParts(builder: GraphDSL.Builder<NotUsed>) {
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
                            is ExperimentTraceImageFlow -> {
                                throw Exception("trace-to-image flow ${traceImgFlow.traceImgFlowName} is connected to a trace-to-image flow. The second flow input type must be the same as the flow output type")
                            }
                            is ExperimentTraceTraceFlow -> {
                                throw Exception("trace-to-image flow ${traceImgFlow.traceImgFlowName} is connected to a trace-to-trace flow. The second flow input type must be the same as the source output type")
                            }
                            is ExperimentImageSink -> {
                                //Add a new fwd op for the connection from the flow to the sink
                                //However a connection from a flow to a sink is terminal so we won't actually create
                                //the fwdOp object. We only create an ExperimentFwdOp object to keep a record of the
                                //connection
                                val newFwdOp = ExperimentImageFwdOp(traceImgFlow.traceImgFlowName, outlet.imageSinkName)

                                if (outlet.mergeObject != null) {
                                    GUIMain.loggerService.log(Level.INFO, "Creating connection from ${traceImgFlow.traceImgFlowName} (broadcast) toFanIn ${outlet.imageSinkName} (merge)")

                                    //Special case that only needs to be applied for image displays
                                    if (outlet.displayOrStore == ExperimentConstants.ConfigurationProperties.DISPLAY) {
                                        val bufferFlow = builder.add(Flow.of(STRIMMImage::class.java)
                                                //TODO this was added in previously to fix a bug when using continuous mode
                                                //.buffer(ExperimentConstants.ConfigurationProperties.IMAGE_BUFFER_SIZE, OverflowStrategy.dropTail())
                                                .async()
                                                .named("BufferFlow"))
                                        builder.from(traceImgFlow.bcastObject).via(bufferFlow).toFanIn(outlet.mergeObject)
                                    } else {
                                        builder.from(traceImgFlow.bcastObject).toFanIn(outlet.mergeObject)
                                    }

                                    numConnectionsMade++
                                } else {
                                    GUIMain.loggerService.log(Level.INFO, "Creating connection from ${traceImgFlow.traceImgFlowName} (broadcast) to ${outlet.imageSinkName}")

                                    //Special case that only needs to be applied for image displays
                                    if (outlet.displayOrStore == ExperimentConstants.ConfigurationProperties.DISPLAY) {
                                        val bufferFlow = builder.add(Flow.of(STRIMMImage::class.java)
                                                //TODO this was added in previously to fix a bug when using continuous mode
                                                //.buffer(ExperimentConstants.ConfigurationProperties.IMAGE_BUFFER_SIZE, OverflowStrategy.dropTail())
                                                .async()
                                                .named("BufferFlow"))
                                        builder.from(traceImgFlow.bcastObject).via(bufferFlow).to(outlet.sink)
                                    } else {
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
                            is ExperimentTraceImageFlow -> {
                                throw Exception("trace-to-image flow ${traceImgFlow.traceImgFlowName} is connected to a trace-to-image flow. The second flow input type must be the same as the flow output type")
                            }
                            is ExperimentTraceTraceFlow -> {
                                throw Exception("trace-to-image flow ${traceImgFlow.traceImgFlowName} is connected to a trace-to-trace flow. The second flow input type must be the same as the source output type")
                            }
                            is ExperimentImageSink -> {
                                //Add a new fwd op for the connection from the flow to the sink
                                //However a connection from a flow to a sink is terminal so we won't actually create
                                //the fwdOp object. We only create an ExperimentFwdOp object to keep a record of the
                                //connection
                                val newFwdOp = ExperimentImageFwdOp(traceImgFlow.traceImgFlowName, outlet.imageSinkName)

                                if (outlet.mergeObject != null) {
                                    GUIMain.loggerService.log(Level.INFO, "Creating connection from ${traceImgFlow.traceImgFlowName} toFanIn ${outlet.imageSinkName} (merge)")
                                    builder.from(traceImgFlow.flow).toFanIn(outlet.mergeObject)
                                    numConnectionsMade++
                                } else {
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
        } catch (ex: Exception) {
            GUIMain.loggerService.log(Level.SEVERE, "Error building connections for trace to image flows. Error message: ${ex.message}")
            GUIMain.loggerService.log(Level.SEVERE, ex.stackTrace)
        }
    }

    /**
     * Specify the graph connections for trace-to-trace flows
     * @param builder The graph building object
     */
    private fun buildTraceTraceFlowParts(builder: GraphDSL.Builder<NotUsed>) {
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
                            is ExperimentImageImageFlow -> {
                                throw Exception("trace-to-trace flow ${traceTraceFlow.traceTraceFlowName} is connected to a image-to-image flow. The second flow input type must be the same as the flow output type")
                            }
                            is ExperimentImageTraceFlow -> {
                                throw Exception("trace-to-trace flow ${traceTraceFlow.traceTraceFlowName} is connected to a image-to-trace flow. The second flow input type must be the same as the source output type")
                            }
                            is ExperimentTraceSink -> {
                                //Add a new fwd op for the connection from the flow to the sink
                                //However a connection from a flow to a sink is terminal so we won't actually create
                                //the fwdOp object. We only create an ExperimentFwdOp object to keep a record of the
                                //connection
                                val newFwdOp = ExperimentTraceFwdOp(traceTraceFlow.traceTraceFlowName, outlet.traceSinkName)

                                if (outlet.mergeObject != null) {
                                    GUIMain.loggerService.log(Level.INFO, "Creating connection from ${traceTraceFlow.traceTraceFlowName} (broadcast) toFanIn ${outlet.traceSinkName} (merge)")
                                    builder.from(traceTraceFlow.bcastObject).toFanIn(outlet.mergeObject)
                                    numConnectionsMade++
                                } else {
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
                            is ExperimentImageImageFlow -> {
                                throw Exception("trace-to-trace flow ${traceTraceFlow.traceTraceFlowName} is connected to a image-to-image flow. The second flow input type must be the same as the flow output type")
                            }
                            is ExperimentImageTraceFlow -> {
                                throw Exception("trace-to-trace flow ${traceTraceFlow.traceTraceFlowName} is connected to a image-to-trace flow. The second flow input type must be the same as the source output type")
                            }
                            is ExperimentTraceSink -> {
                                //Add a new fwd op for the connection from the flow to the sink
                                //However a connection from a flow to a sink is terminal so we won't actually create
                                //the fwdOp object. We only create an ExperimentFwdOp object to keep a record of the
                                //connection
                                val newFwdOp = ExperimentTraceFwdOp(traceTraceFlow.traceTraceFlowName, outlet.traceSinkName)

                                if (outlet.mergeObject != null) {
                                    GUIMain.loggerService.log(Level.INFO, "Creating connection from ${traceTraceFlow.traceTraceFlowName} toFanIn ${outlet.traceSinkName} (merge)")
                                    builder.from(traceTraceFlow.flow).toFanIn(outlet.mergeObject)
                                    numConnectionsMade++
                                } else {
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
        } catch (ex: Exception) {
            GUIMain.loggerService.log(Level.SEVERE, "Error building connections for trace to trace flows. Error message: ${ex.message}")
            GUIMain.loggerService.log(Level.SEVERE, ex.stackTrace)
        }
    }

    /**
     * Specify the graph connections for image-to-trace flows
     * @param builder The graph building object
     */
    private fun buildImageTraceFlowParts(builder: GraphDSL.Builder<NotUsed>) {
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
                            is ExperimentImageImageFlow -> {
                                throw Exception("image-to-trace flow ${imgTraceFlow.imgTraceFlowName} is connected to a image-to-image flow. The second flow input type must be the same as the flow output type")
                            }
                            is ExperimentImageTraceFlow -> {
                                throw Exception("image-to-trace flow ${imgTraceFlow.imgTraceFlowName} is connected to a image-to-trace flow. The second flow input type must be the same as the source output type")
                            }
                            is ExperimentTraceSink -> {
                                //Add a new fwd op for the connection from the flow to the sink
                                //However a connection from a flow to a sink is terminal so we won't actually create
                                //the fwdOp object. We only create an ExperimentFwdOp object to keep a record of the
                                //connection
                                val newFwdOp = ExperimentTraceFwdOp(imgTraceFlow.imgTraceFlowName, outlet.traceSinkName)

                                if (outlet.mergeObject != null) {
                                    GUIMain.loggerService.log(Level.INFO, "Creating connection from ${imgTraceFlow.imgTraceFlowName} (broadcast) toFanIn ${outlet.traceSinkName} (merge)")
                                    builder.from(imgTraceFlow.bcastObject).toFanIn(outlet.mergeObject)
                                    numConnectionsMade++
                                } else {
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
                            is ExperimentImageImageFlow -> {
                                throw Exception("image-to-trace flow ${imgTraceFlow.imgTraceFlowName} is connected to a image-to-image flow. The second flow input type must be the same as the flow output type")
                            }
                            is ExperimentImageTraceFlow -> {
                                throw Exception("image-to-trace flow ${imgTraceFlow.imgTraceFlowName} is connected to a image-to-trace flow. The second flow input type must be the same as the source output type")
                            }
                            is ExperimentTraceSink -> {
                                //Add a new fwd op for the connection from the flow to the sink
                                //However a connection from a flow to a sink is terminal so we won't actually create
                                //the fwdOp object. We only create an ExperimentFwdOp object to keep a record of the
                                //connection
                                val newFwdOp = ExperimentTraceFwdOp(imgTraceFlow.imgTraceFlowName, outlet.traceSinkName)

                                if (outlet.mergeObject != null) {
                                    GUIMain.loggerService.log(Level.INFO, "Creating connection from ${imgTraceFlow.imgTraceFlowName} toFanIn ${outlet.traceSinkName} (merge)")
                                    builder.from(imgTraceFlow.flow).toFanIn(outlet.mergeObject)
                                    numConnectionsMade++
                                } else {
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
        } catch (ex: Exception) {
            GUIMain.loggerService.log(Level.SEVERE, "Error building connections for image to trace flows. Error message: ${ex.message}")
            GUIMain.loggerService.log(Level.SEVERE, ex.stackTrace)
        }
    }

    /**
     * Specify the graph connections for image-to-image flows
     * @param builder The graph building object
     */
    private fun buildImageImageFlowParts(builder: GraphDSL.Builder<NotUsed>) {
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
                            is ExperimentTraceImageFlow -> {
                                throw Exception("image-to-image flow ${imgImgFlow.imgImgFlowName} is connected to a trace-to-image flow. The second flow input type must be the same as the flow output type")
                            }
                            is ExperimentTraceTraceFlow -> {
                                throw Exception("image-to-image flow ${imgImgFlow.imgImgFlowName} is connected to a trace-to-image flow. The second flow input type must be the same as the source output type")
                            }
                            is ExperimentImageSink -> {
                                //Add a new fwd op for the connection from the flow to the sink
                                //However a connection from a flow to a sink is terminal so we won't actually create
                                //the fwdOp object. We only create an ExperimentFwdOp object to keep a record of the
                                //connection
                                val newFwdOp = ExperimentImageFwdOp(imgImgFlow.imgImgFlowName, outlet.imageSinkName)

                                if (outlet.mergeObject != null) {
                                    GUIMain.loggerService.log(Level.INFO, "Creating connection from ${imgImgFlow.imgImgFlowName} (broadcast) toFanIn ${outlet.imageSinkName} (merge)")

                                    //Special case that only needs to be applied for image displays
                                    if (outlet.displayOrStore == ExperimentConstants.ConfigurationProperties.DISPLAY) {
                                        val bufferFlow = builder.add(Flow.of(STRIMMImage::class.java)
                                                //TODO this was added in previously to fix a bug when using continuous mode
                                                //.buffer(ExperimentConstants.ConfigurationProperties.IMAGE_BUFFER_SIZE, OverflowStrategy.dropTail())
                                                .async()
                                                .named("BufferFlow"))
                                        builder.from(imgImgFlow.bcastObject).via(bufferFlow).toFanIn(outlet.mergeObject)
                                    } else {
                                        builder.from(imgImgFlow.bcastObject).toFanIn(outlet.mergeObject)
                                    }
                                    numConnectionsMade++
                                } else {
                                    GUIMain.loggerService.log(Level.INFO, "Creating connection from ${imgImgFlow.imgImgFlowName} (broadcast) to ${outlet.imageSinkName}")

                                    //Special case that only needs to be applied for image displays
                                    if (outlet.displayOrStore == ExperimentConstants.ConfigurationProperties.DISPLAY) {
                                        val bufferFlow = builder.add(Flow.of(STRIMMImage::class.java)
                                                //TODO this was added in previously to fix a bug when using continuous mode
                                                //.buffer(ExperimentConstants.ConfigurationProperties.IMAGE_BUFFER_SIZE, OverflowStrategy.dropTail())
                                                .async()
                                                .named("BufferFlow"))
                                        builder.from(imgImgFlow.bcastObject).via(bufferFlow).to(outlet.sink)
                                    } else {
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
                            is ExperimentTraceImageFlow -> {
                                throw Exception("image-to-image flow ${imgImgFlow.imgImgFlowName} is connected to a trace-to-image flow. The second flow input type must be the same as the flow output type")
                            }
                            is ExperimentTraceTraceFlow -> {
                                throw Exception("image-to-image flow ${imgImgFlow.imgImgFlowName} is connected to a trace-to-image flow. The second flow input type must be the same as the source output type")
                            }
                            is ExperimentImageSink -> {
                                //Add a new fwd op for the connection from the flow to the sink
                                //However a connection from a flow to a sink is terminal so we won't actually create
                                //the fwdOp object. We only create an ExperimentFwdOp object to keep a record of the
                                //connection
                                val newFwdOp = ExperimentImageFwdOp(imgImgFlow.imgImgFlowName, outlet.imageSinkName)

                                if (outlet.mergeObject != null) {
                                    GUIMain.loggerService.log(Level.INFO, "Creating connection from ${imgImgFlow.imgImgFlowName} toFanIn ${outlet.imageSinkName} (merge)")

                                    //Special case that only needs to be applied for image displays
                                    if (outlet.displayOrStore == ExperimentConstants.ConfigurationProperties.DISPLAY) {
                                        val bufferFlow = builder.add(Flow.of(STRIMMImage::class.java)
                                                //TODO this was added in previously to fix a bug when using continuous mode
                                                //.buffer(ExperimentConstants.ConfigurationProperties.IMAGE_BUFFER_SIZE, OverflowStrategy.dropTail())
                                                .async()
                                                .named("BufferFlow"))
                                        builder.from(imgImgFlow.flow).via(bufferFlow).toFanIn(outlet.mergeObject)
                                    } else {
                                        builder.from(imgImgFlow.flow).toFanIn(outlet.mergeObject)
                                    }

                                    numConnectionsMade++
                                } else {
                                    GUIMain.loggerService.log(Level.INFO, "Creating connection from ${imgImgFlow.imgImgFlowName} to ${outlet.imageSinkName}")

                                    //Special case that only needs to be applied for image displays
                                    if (outlet.displayOrStore == ExperimentConstants.ConfigurationProperties.DISPLAY) {
                                        val bufferFlow = builder.add(Flow.of(STRIMMImage::class.java)
                                                //TODO this was added in previously to fix a bug when using continuous mode
                                                //.buffer(ExperimentConstants.ConfigurationProperties.IMAGE_BUFFER_SIZE, OverflowStrategy.dropTail())
                                                .async()
                                                .named("BufferFlow"))
                                        builder.from(imgImgFlow.flow).via(bufferFlow).to(outlet.sink)
                                    } else {
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
        } catch (ex: Exception) {
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
    private fun createConnection(imgImgFlow: ExperimentImageImageFlow, outlet: ExperimentImageImageFlow, builder: GraphDSL.Builder<NotUsed>) {
        val fwdOpsList = imgImgFlow.fwdOps.filter { x -> x.toNodeName == imgImgFlow.name }
        if (fwdOpsList.isNotEmpty()) {
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
    private fun createConnection(imgImgFlow: ExperimentImageImageFlow, outlet: ExperimentImageTraceFlow, builder: GraphDSL.Builder<NotUsed>) {
        val fwdOpsList = imgImgFlow.fwdOps.filter { x -> x.toNodeName == imgImgFlow.name }
        if (fwdOpsList.isNotEmpty()) {
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
    private fun createConnection(traceImgFlow: ExperimentTraceImageFlow, outlet: ExperimentImageImageFlow, builder: GraphDSL.Builder<NotUsed>) {
        val fwdOpsList = traceImgFlow.fwdOps.filter { x -> x.toNodeName == traceImgFlow.name }
        if (fwdOpsList.isNotEmpty()) {
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
    private fun createConnection(traceImgFlow: ExperimentTraceImageFlow, outlet: ExperimentImageTraceFlow, builder: GraphDSL.Builder<NotUsed>) {
        val fwdOpsList = traceImgFlow.fwdOps.filter { x -> x.toNodeName == traceImgFlow.name }
        if (fwdOpsList.isNotEmpty()) {
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
    private fun createConnection(imgTraceFlow: ExperimentImageTraceFlow, outlet: ExperimentTraceTraceFlow, builder: GraphDSL.Builder<NotUsed>) {
        val fwdOpsList = imgTraceFlow.fwdOps.filter { x -> x.toNodeName == imgTraceFlow.name }
        if (fwdOpsList.isNotEmpty()) {
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
    private fun createConnection(imgTraceFlow: ExperimentImageTraceFlow, outlet: ExperimentTraceImageFlow, builder: GraphDSL.Builder<NotUsed>) {
        val fwdOpsList = imgTraceFlow.fwdOps.filter { x -> x.toNodeName == imgTraceFlow.name }
        if (fwdOpsList.isNotEmpty()) {
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
                //This outlet already has a merge object
                //If the outlet has a merge object then build a link from the flow to the outlet viaFanIn
                GUIMain.loggerService.log(Level.INFO, "Creating connection from ${imgTraceFlow.imgTraceFlowName} viaFanIn ${outlet.traceImgFlowName} (merge)")
                builder.from(imgTraceFlow.flow).viaFanIn(outlet.mergeObject)

                numConnectionsMade++
            } else {
                GUIMain.loggerService.log(Level.INFO, "Creating connection from ${imgTraceFlow.imgTraceFlowName} via ${outlet.traceImgFlowName}")
                newFwdOp.fwdOp = builder.from(imgTraceFlow.flow).via(outlet.flow)
                //make a link from the flow to the outler and then capture the fwd op
                numConnectionsMade++
            }
            outlet.fwdOps.add(newFwdOp)
        }
    }

    /**
     * Create a connection from an trace-to-trace flow to an trace-to-trace flow
     * @param traceTraceFlow The originating flow
     * @param outlet The destination flow
     * @builder The graph building object
     */
    private fun createConnection(traceTraceFlow: ExperimentTraceTraceFlow, outlet: ExperimentTraceTraceFlow, builder: GraphDSL.Builder<NotUsed>) {
        val fwdOpsList = traceTraceFlow.fwdOps.filter { x -> x.toNodeName == traceTraceFlow.name }
        if (fwdOpsList.isNotEmpty()) {
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
    private fun createConnection(traceTraceFlow: ExperimentTraceTraceFlow, outlet: ExperimentTraceImageFlow, builder: GraphDSL.Builder<NotUsed>) {
        val fwdOpsList = traceTraceFlow.fwdOps.filter { x -> x.toNodeName == traceTraceFlow.name }
        if (fwdOpsList.isNotEmpty()) {
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
    private fun createBroadcastConnection(imgImgFlow: ExperimentImageImageFlow, outlet: ExperimentImageImageFlow, builder: GraphDSL.Builder<NotUsed>) {
        if (outlet.mergeObject != null) {
            //This condition represents a broadcast connection going to a merge connection
            GUIMain.loggerService.log(Level.INFO, "Creating connection from ${imgImgFlow.imgImgFlowName} (broadcast) viaFanIn ${outlet.imgImgFlowName} (merge)")
            imgImgFlow.bcastFwdOp?.fwdOp?.viaFanIn(outlet.mergeObject)
            numConnectionsMade++
        } else {
            //Ths condition represents a broadcast connection going to a normal connection
            GUIMain.loggerService.log(Level.INFO, "Creating connection from ${imgImgFlow.imgImgFlowName} (broadcast) via ${outlet.imgImgFlowName}")
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
    private fun createBroadcastConnection(imgImgFlow: ExperimentImageImageFlow, outlet: ExperimentImageTraceFlow, builder: GraphDSL.Builder<NotUsed>) {
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
    private fun createBroadcastConnection(traceImgFlow: ExperimentTraceImageFlow, outlet: ExperimentImageImageFlow, builder: GraphDSL.Builder<NotUsed>) {
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
    private fun createBroadcastConnection(traceImgFlow: ExperimentTraceImageFlow, outlet: ExperimentImageTraceFlow, builder: GraphDSL.Builder<NotUsed>) {
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
    private fun createBroadcastConnection(imgTraceFlow: ExperimentImageTraceFlow, outlet: ExperimentTraceTraceFlow, builder: GraphDSL.Builder<NotUsed>) {
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
    private fun createBroadcastConnection(imgTraceFlow: ExperimentImageTraceFlow, outlet: ExperimentTraceImageFlow, builder: GraphDSL.Builder<NotUsed>) {
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
    private fun createBroadcastConnection(traceTraceFlow: ExperimentTraceTraceFlow, outlet: ExperimentTraceTraceFlow, builder: GraphDSL.Builder<NotUsed>) {
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
    private fun createBroadcastConnection(traceTraceFlow: ExperimentTraceTraceFlow, outlet: ExperimentTraceImageFlow, builder: GraphDSL.Builder<NotUsed>) {
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
    private fun setBroadcastObjectForSources(builder: GraphDSL.Builder<NotUsed>) {
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
        } catch (ex: Exception) {
            GUIMain.loggerService.log(Level.SEVERE, "Error creating broadcast objects for sources. Error message: ${ex.message}")
            GUIMain.loggerService.log(Level.SEVERE, ex.stackTrace)
        }
    }

    /**
     * If a flow has more than one outlet (connection going out from it) then it will need to broadcast. This method
     * will create the necessary broadcast object
     * @param builder The graph building object
     */
    private fun setBroadcastObjectForFlows(builder: GraphDSL.Builder<NotUsed>) {
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
        } catch (ex: Exception) {
            GUIMain.loggerService.log(Level.SEVERE, "Error creating broadcast objects for flows. Error message: ${ex.message}")
            GUIMain.loggerService.log(Level.SEVERE, ex.stackTrace)
        }
    }

    /**
     * If a flow has more than one inlet (connection going into it) then it will need to merge. THis method will create
     * the necessary merge object
     * @param builder The graph building object
     */
    private fun setMergeObjectForSinks(builder: GraphDSL.Builder<NotUsed>) {
        try {
            experimentImageSinks.forEach { x ->
                if (x.ins.size > 1) {
                    for (inlet in x.ins) {
                        if (x.outputType.toLowerCase() == ExperimentConstants.ConfigurationProperties.IMAGE_INPUT_TYPE.toLowerCase() && x.mergeObject == null) {
                            when (inlet) {
                                is ExperimentImageSource -> {
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
                if (x.ins.size > 1) {
                    for (inlet in x.ins) {
                        if (x.outputType.toLowerCase() == ExperimentConstants.ConfigurationProperties.TRACE_INPUT_TYPE.toLowerCase() && x.mergeObject == null) {
                            when (inlet) {
                                is ExperimentTraceSource -> {
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
        } catch (ex: Exception) {
            GUIMain.loggerService.log(Level.SEVERE, "Error creating merge objects for sinks. Error message: ${ex.message}")
            GUIMain.loggerService.log(Level.SEVERE, ex.stackTrace)
        }
    }

    /**
     * If a flow has more than one inlet (connection going into it) then it will need to merge. This method will create
     * the necessary merge object
     * @param builder The graph building object
     */
    private fun setMergeObjectForFlows(builder: GraphDSL.Builder<NotUsed>) {
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
        } catch (ex: Exception) {
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
    private fun populateLists(expConfig: ExperimentConfiguration, builder: GraphDSL.Builder<NotUsed>) {
        GUIMain.loggerService.log(Level.INFO, "Populating sources, flows and sinks")

        populateSources(expConfig, builder)
        populateFlows(expConfig, builder)
        populateSinks(expConfig, builder)
    }


    /**
     * Go through the experiment config (from json file) and create the appropriate source objects that correspond to the
     * config. This uses custom wrapper classes for the akka objects. This also specifies types explicitly
     * @param expConfig The experiment config from the JSON config file
     * @param builder The graph building object
     */
    private fun populateSources(expConfig: ExperimentConfiguration, builder: GraphDSL.Builder<NotUsed>) {
        try {
            for (source in expConfig.sourceConfig.sources) {
                if (source.outputType.toLowerCase() == ExperimentConstants.ConfigurationProperties.IMAGE_OUTPUT_TYPE.toLowerCase()) {
                    val expSource = ExperimentImageSource(source.sourceName, source.deviceLabel)
                    val snapImageMethod = GUIMain.acquisitionMethodService.getAcquisitionMethod(CONFIGURED_CAMERA_METHOD_NAME) as AcquisitionMethodService.ImageMethod
                    val isTimeLapse = source.isTimeLapse
                    val intervalMs = source.intervalMs

                    /**
                     * The cameras loaded by AcquisitionMethodService when it runs its init() at the start of STRIMM
                     * are all default settings.  Configure the camera depending on the settings in the JSON
                     */
                    //TODO review if isGlobalStart is needed
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

                    /**
                     * The isGreyScale setting is important for OpenCV. At present STRIMM does not support RGB however
                     * does support float images hence it is converted to a float image (R+G+B)/3.0
                     * The MultiCamera sink can display RGB however, so if the intention is only to display outside of
                     * STRIMM then RGB can be used
                     */
                    source.camera!!.SetGreyScale(source.isGreyScale)

                    val akkaSource =
                            //Timelapse controlled with PC time
                            if (isTimeLapse) {
                                Source.tick(Duration.ZERO, Duration.ofMillis(intervalMs.toLong()), Unit)
                            }
                            else { //fps determined by the runMethod of the camera
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
                } else if (source.outputType.toLowerCase() == ExperimentConstants.ConfigurationProperties.TRACE_OUTPUT_TYPE.toLowerCase()) {
                    val expSource = ExperimentTraceSource(source.sourceName, source.deviceLabel)
                    expSource.samplingFrequencyHz = source.samplingFrequencyHz

                    //The source has a sourceType which is used here to
                    //Also the source can be further parameterised with param1, param2, ... , param4
                    if (source.sourceType == "NIDAQSource") {
                        GUIMain.protocolService.SetNIDAQUsed(true)
                        GUIMain.protocolService.isEpisodic = true
                        val numAOChannels = GUIMain.protocolService.GetNumChannels(0)
                        val numAIChannels = GUIMain.protocolService.GetNumChannels(1)
                        val numDOChannels = GUIMain.protocolService.GetNumChannels(2)
                        val numDIChannels = GUIMain.protocolService.GetNumChannels(3)

                        val dummyAO = mutableListOf<Overlay>()
                        val dummyAI = mutableListOf<Overlay>()
                        val dummyDO = mutableListOf<Overlay>()
                        val dummyDI = mutableListOf<Overlay>()
                        for (AOChannel in 0 until numAOChannels) {
                            val dummyOverlay = EllipseOverlay()
                            val chan = GUIMain.protocolService.GetChannelFromIndex(0, AOChannel)
                            dummyOverlay.name =
                                    expSource.traceSourceName + " " + "ao" + chan.toString() + " " + Random().nextInt(1000)
                            dummyAO.add(dummyOverlay)

                            val numPoints = GUIMain.protocolService.GetNumberOfDataPoints()
                            GUIMain.experimentService.deviceDatapointNumbers[dummyOverlay.name] = numPoints
                        }

                        for (AIChannel in 0 until numAIChannels) {
                            val dummyOverlay = EllipseOverlay()
                            val chan = GUIMain.protocolService.GetChannelFromIndex(1, AIChannel)
                            dummyOverlay.name =
                                    expSource.traceSourceName + " " + "ai" + chan.toString() + " " + Random().nextInt(1000)
                            dummyAI.add(dummyOverlay)

                            val numPoints = GUIMain.protocolService.GetNumberOfDataPoints()
                            GUIMain.experimentService.deviceDatapointNumbers[dummyOverlay.name] = numPoints
                        }

                        for (DOChannel in 0 until numDOChannels) {
                            val dummyOverlay = EllipseOverlay()
                            val chan = GUIMain.protocolService.GetChannelFromIndex(2, DOChannel)
                            dummyOverlay.name =
                                    expSource.traceSourceName + " " + "do" + chan.toString() + " " + Random().nextInt(1000)
                            dummyDO.add(dummyOverlay)

                            val numPoints = GUIMain.protocolService.GetNumberOfDataPoints()
                            GUIMain.experimentService.deviceDatapointNumbers[dummyOverlay.name] = numPoints
                        }

                        for (DIChannels in 0 until numDIChannels) {
                            val dummyOverlay = EllipseOverlay()
                            val chan = GUIMain.protocolService.GetChannelFromIndex(3, DIChannels)
                            dummyOverlay.name =
                                    expSource.traceSourceName + " " + "di" + chan.toString() + " " + Random().nextInt(1000)
                            dummyDI.add(dummyOverlay)

                            val numPoints = GUIMain.protocolService.GetNumberOfDataPoints()
                            GUIMain.experimentService.deviceDatapointNumbers[dummyOverlay.name] = numPoints
                        }

                        val getTraceDataMethodNIDAQ =
                                GUIMain.acquisitionMethodService.getAcquisitionMethod(TRACE_DATA_NIDAQ_METHOD_NAME)
                                        as AcquisitionMethodService.TraceMethod

                        if (GUIMain.experimentService.expConfig.NIDAQ.bRepeat) {
                            val akkaSource = Source.repeat(1)
                                    .map {
                                        listOf(getTraceDataMethodNIDAQ.runMethod(
                                                        dummyAO,   //send the Overlays which identify each virtual channels via its name
                                                        dummyAI,
                                                        dummyDO,
                                                        dummyDI,
                                                        source))
                                    }
                                    .async()
                                    .named(source.sourceName)
                            expSource.source = builder.add(akkaSource)
                        } else {
                            val akkaSource = Source.repeat(1)
                                    .take(GUIMain.protocolService.GetNumberOfStages())
                                    .map {
                                        listOf(getTraceDataMethodNIDAQ.runMethod(
                                                        dummyAO,
                                                        dummyAI,
                                                        dummyDO,
                                                        dummyDI,
                                                        source))
                                    } //dummyOverlay can be an array
                                    .async()
                                    .named(source.sourceName)
                            expSource.source = builder.add(akkaSource)
                        }
                    } else if (source.sourceType == "KeyboardABCSource") {
                        val dummyKeys = mutableListOf<Overlay>()
                        val keys = arrayListOf('A'.toInt(), 'B'.toInt(), 'C'.toInt())
                        for (f in 0 until 3) {
                            val dummyOverlay = EllipseOverlay()
                            dummyOverlay.name =
                                    expSource.traceSourceName + " " + "keys" + keys[f].toString() + " " + Random().nextInt(1000)
                            dummyKeys.add(dummyOverlay)
                        }

                        val getTraceDataMethodKeyboard =
                                GUIMain.acquisitionMethodService.getAcquisitionMethod(TRACE_DATA_KEYBOARD_METHOD_NAME)
                                        as AcquisitionMethodService.TraceMethod

                        val akkaSource = Source.repeat(1)
                                .map {
                                    listOf(getTraceDataMethodKeyboard.runMethod(keys, dummyKeys))
                                }
                                .async()
                                .named(source.sourceName)
                        expSource.source = builder.add(akkaSource)

                    } else if (source.sourceType == "KeyboardASource") {
                        val key = 'A'.toInt()
                        val pollPeriod = source.param1.toInt()
                        val dummyOverlay = EllipseOverlay()

                        //the overlay name is used as the ID for this series of data - when plotted lines will all have the save overlay.name
                        dummyOverlay.name =
                                expSource.traceSourceName + " " + "key" + key + " " + Random().nextInt(1000)

                        val getTraceDataMethodKeyboard =
                                GUIMain.acquisitionMethodService.getAcquisitionMethod(A_KEYBOARD_METHOD_NAME)
                                        as AcquisitionMethodService.TraceMethod
                        val akkaSource = Source.repeat(1)
                                .map {
                                    listOf(getTraceDataMethodKeyboard.runMethod(key, dummyOverlay, pollPeriod))
                                } //dummyOverlay can be an array
                                .async()
                                .named(source.sourceName)
                        expSource.source = builder.add(akkaSource)

                    } else if (source.sourceType == "RandomTraceSource") {
                        val dummyOverlay = EllipseOverlay() //this dummyOverlay will be used as the id for this trace
                        dummyOverlay.name = expSource.traceSourceName + Random().nextInt(1000)

                        val getTraceDataRandomTraceSource =  //get a TraceMethod with a run function which will return the data
                                GUIMain.acquisitionMethodService.getAcquisitionMethod(RANDOM_TRACE_SOURCE_METHOD_NAME)
                                        as AcquisitionMethodService.TraceMethod
                        //the akka source will pump out 1s each will trigger the getTraceDataMethodConstantTraceSource run() method
                        val akkaSource = Source.repeat(1)
                                .map {
                                    listOf(getTraceDataRandomTraceSource.runMethod(dummyOverlay, source))
                                }
                                .async()  //async means that it has its own thread of execution
                                .named(source.sourceName)
                        expSource.source = builder.add(akkaSource)

                    } else if (source.sourceType == "ConstantTraceSource") {
                        val dummyOverlay = EllipseOverlay()
                        dummyOverlay.name = expSource.traceSourceName + Random().nextInt(1000)
                        val getTraceDataMethodConstantTraceSource =
                                GUIMain.acquisitionMethodService.getAcquisitionMethod(CONSTANT_TRACE_SOURCE_METHOD_NAME)
                                        as AcquisitionMethodService.TraceMethod

                        val akkaSource = Source.repeat(1)
                                .map {
                                    listOf(getTraceDataMethodConstantTraceSource.runMethod(dummyOverlay, source))
                                }
                                .async()
                                .named(source.sourceName)
                        expSource.source = builder.add(akkaSource)
                    } else if (source.sourceType == "ConstantVectorSource") {
                        val overlays = arrayListOf<Overlay>()
                        val dummyOverlay1 = EllipseOverlay() //id for the 1st component
                        dummyOverlay1.name = expSource.traceSourceName + 0
                        val dummyOverlay2 = EllipseOverlay() //id for the 2nd component
                        dummyOverlay2.name = expSource.traceSourceName + 1
                        overlays.add(dummyOverlay1)
                        overlays.add(dummyOverlay2)

                        val getTraceDataMethodConstantVectorSource =
                                GUIMain.acquisitionMethodService.getAcquisitionMethod(CONSTANT_VECTOR_SOURCE_METHOD_NAME)
                                        as AcquisitionMethodService.TraceMethod

                        val akkaSource = Source.repeat(1)
                                .map {
                                    listOf(getTraceDataMethodConstantVectorSource.runMethod(overlays, source))
                                }
                                .async()  //async means that it has its own thread of execution
                                .named(source.sourceName)
                        expSource.source = builder.add(akkaSource)
                    } else if (source.sourceType == "SineWaveSource") {
                        val dummyOverlay = EllipseOverlay()
                        dummyOverlay.name = expSource.traceSourceName + Random().nextInt(1000)
                        val getTraceDataMethodSineWaveSource =
                                GUIMain.acquisitionMethodService.getAcquisitionMethod(SINE_WAVE_SOURCE_METHOD_NAME)
                                        as AcquisitionMethodService.TraceMethod

                        val akkaSource = Source.repeat(1)
                                .map {
                                    listOf(getTraceDataMethodSineWaveSource.runMethod(dummyOverlay, source))
                                }
                                .async()
                                .named(source.sourceName)
                        expSource.source = builder.add(akkaSource)

                    } else if (source.sourceType == "SquareWaveSource") {
                        val dummyOverlay = EllipseOverlay()
                        dummyOverlay.name = expSource.traceSourceName + Random().nextInt(1000)
                        val getTraceDataMethodSquareWaveSource =
                                GUIMain.acquisitionMethodService.getAcquisitionMethod(SQUARE_WAVE_SOURCE_METHOD_NAME)
                                        as AcquisitionMethodService.TraceMethod

                        val akkaSource = Source.repeat(1)
                                .map {
                                    listOf(getTraceDataMethodSquareWaveSource.runMethod(dummyOverlay, source))
                                }
                                .async()
                                .named(source.sourceName)
                        expSource.source = builder.add(akkaSource)
                    }
                    expSource.outputType = ExperimentConstants.ConfigurationProperties.TRACE_OUTPUT_TYPE
                    experimentTraceSources.add(expSource)
                }
            }
        } catch (ex: Exception) {
            GUIMain.loggerService.log(Level.SEVERE, "Error creating sources from json experiment configuration. Error message: ${ex.message}")
            GUIMain.loggerService.log(Level.SEVERE, ex.stackTrace)
        }
    }

    /**
     * Flows are specialised by a flowType and also a flow function.
     * The flow function will take a List<List<TraceData>> process and return it
     */
    private fun Arduino_Digital_Output(data: List<List<TraceData>>): List<List<TraceData>> {
        val dataList = data[0]
        val incomingData = dataList[0].data.second.toInt()
        val COMPort = GUIMain.protocolService.COMPort()

        //TODO hardcoded numbers for range
        if (incomingData in 0..200) {
            try {
                val dataToWrite = byteArrayOf(incomingData.toByte())
                if (dataToWrite.isNotEmpty()) {
                    if (COMPort != null) {
                        COMPort.outputStream.write(dataToWrite)
                    } else {
                        throw Exception("COM Port is null")
                    }
                }
            } catch (ex: Exception) {
                GUIMain.loggerService.log(Level.SEVERE, "Error in sending data to arduino over COM port. Message: ${ex.message}")
                GUIMain.loggerService.log(Level.SEVERE, ex.stackTrace)
            }
        }
        return data
    }

    private fun thresholdFlowController(data: List<List<TraceData>>, threshold: Double, channel: Int, type: Int): List<List<TraceData>> {
        val dataList = data[0]
        val incomingData = dataList[0].data.second
        val outgoingData: Int

        //TODO hardcoded values need descriptions and unhardcoding
        outgoingData = if (type >= 0) {
            if (incomingData > threshold) {
                100 + channel
            } else {
                channel
            }
        } else {
            if (incomingData < threshold) {
                100 + channel
            } else {
                channel
            }
        }
        val overlay = dataList[0].data.first as Overlay
        val traceDataArray = arrayListOf(TraceData(Pair(overlay, outgoingData.toDouble()), dataList[0].timeAcquired))
        val nestedArray = arrayListOf(traceDataArray)
        return nestedArray
    }

    //TODO this is not used, can it be removed?
    private fun Threshhold_controller(data: List<List<TraceData>>, threshhold: Double): List<List<TraceData>> {
        val dat = data[0]
        val dat1 = arrayListOf<TraceData>()
        for (d in dat) {
            val res = if (d.data.second > threshhold) 'A'.toDouble() else 0.0
            println(d.data.second.toString() + "  " + threshhold.toString() + " " + res.toString())
            val overlay = d.data.first as Overlay
            dat1.add(TraceData(Pair(overlay, res), d.timeAcquired))
        }
        val dat3 = arrayListOf<ArrayList<TraceData>>()
        dat3.add(dat1)
        return dat3
    }

    //TODO this is functionally unused, can it be removed?
    private fun arduinoDO3Controller(data: List<List<TraceData>>): List<List<TraceData>> {
        val dat = data[0]
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
        return data
    }

    //TODO this is functionally unused, can it be removed?
    private fun arduinoDO4Controller(data: List<List<TraceData>>): List<List<TraceData>> {
        val dat = data[0]
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
        return data
    }

    private fun SelectChannels(data: List<List<TraceData>>, channels: IntArray, stride: Int): List<List<TraceData>> {
        val dat = data[0]
        val len = dat.size
        val selected = arrayListOf<TraceData>()

        for (i in 0 until len step stride) {
            for (f in 0 until channels.size) {
                selected.add(dat[i + channels[f]])
            }
        }

        val nestedArray = arrayListOf(selected)
        return nestedArray
    }

    //TODO what is this variable used for
    private var countS = 0

    private fun NIDAQInjectAO(data: List<List<TraceData>>, channel: Int, pAODataInject: DoubleArray): List<List<TraceData>> {
        //TODO determine the purpose of this for loop
        for (f in 0 until pAODataInject.size) {
            val random = Random()
            val randomInt = random.nextInt(10)
            if (randomInt > 5) {
                pAODataInject[f] = 10.0
            } else {
                pAODataInject[f] = -10.0
            }
        }
        countS++

        if (countS % 2 == 0) {
            GUIMain.protocolService.UpdateAOChannel(pAODataInject, channel)
        }
        return data
    }

    private fun NIDAQInjectDO(data: List<List<TraceData>>, line: Int, pDODataInject: IntArray): List<List<TraceData>> {
        //TODO determine the purpose of this for loop
        for (f in 0 until pDODataInject.size) {
            val random = Random()
            val randomInt = random.nextInt(10)
            if (randomInt > 5) {
                pDODataInject[f] = 0
            } else {
                pDODataInject[f] = 0
            }
        }
        countS++

        if (countS % 2 == 0) {
            GUIMain.protocolService.UpdateDOChannel(pDODataInject, line)
        }
        return data
    }

    //TODO can these variables be moved into function "PairImageRatio"?
    var testCounter = 0
    var testStore = hashMapOf<String, STRIMMImage>()
    private fun pairImageRatio(data: STRIMMImage): STRIMMImage {
        if (testCounter == 0) {
            testStore["result"] = data
            testStore["store1"] = data
        } else {
            if (testCounter % 2 == 0) {
                val pix = data.pix as FloatArray
                val newPix = FloatArray(pix.size)
                val pixOld = testStore["store1"]!!.pix as FloatArray

                for (f in 0 until pix.size) {
                    newPix[f] = (pix[f] / pixOld[f])
                }
                testStore["result"] = STRIMMImage(data.sourceCamera, newPix, data.timeAcquired, 0, 0, 0)
            } else {
                testStore["store1"] = data
            }
        }
        testCounter++

        return testStore["result"] as STRIMMImage
    }

    //TODO this is not used, can it be removed?
    private fun ConsecutiveImageSubtractor(data: STRIMMImage): STRIMMImage {
        if (lastImageFlow.containsKey(data.sourceCamera)) {
            val pix = data.pix as FloatArray
            val newPix = FloatArray(pix.size)
            val pixOld = (lastImageFlow[data.sourceCamera])!!.pix as FloatArray
            for (f in 0 until pix.size) {
                newPix[f] = (pix[f] - pixOld[f])
            }
            lastImageFlow[data.sourceCamera] = data
            return STRIMMImage(data.sourceCamera, newPix, data.timeAcquired, 0, 0, 0)
        }
        lastImageFlow[data.sourceCamera] = data
        return data
    }

    private fun globalSourceStartTriggerFlow(data: List<List<TraceData>>): List<List<TraceData>> {
        val dat = data[0]
        val incomingData = dat[0].data.second.toInt()
        if (incomingData > 50) { //TODO hardcoded
            //global start to all sources
            GUIMain.protocolService.bGlobalSourceStartTrigger = true
        }
        return data
    }

    private fun threshholdImage(data: STRIMMImage, threshhold: Double): STRIMMImage {
        val pix = data.pix as FloatArray
        val newPix = FloatArray(pix.size)
        for (f in 0 until pix.size) {
            //TODO threshold (70.0) is hardcoded
            newPix[f] = (if (pix[f] > 70.0) 1.0 else 0.0).toFloat()
        }
        return data
    }

    //TODO what is this method doing?
    private fun flowReduceSize(data: STRIMMImage): STRIMMImage {
        val pix = ShortArray(200 * 200)
        for (i in 0 until pix.size) {
            pix[i] = Random().nextInt((65000 + 1)).toShort()

        }
        val image = STRIMMImage(data.sourceCamera, pix, data.timeAcquired, data.imageCount, 200, 200)
        return image
    }

    //TODO this method is functionally unused. Can it be removed?
    private fun printFlow(data: STRIMMImage): STRIMMImage {
        return data
    }

    private fun reduceFlow(data: STRIMMImage): STRIMMImage {
        val pix0 = data.pix as FloatArray
        //TODO lots of hardcoded values with no meaning
        for (j in 0..100) {
            for (i in 0..100) {
                if (i % 2 == 0 && j % 2 == 0) {
                    pix0[i + j * 100] = 1000.0f
                } else {
                    pix0[i + j * 100] = 0.0f
                }
            }
        }
        return data
    }

    //TODO what is the purpose of this flow? Appears to act as a hardcoded gain?
    private fun brightFlow(data: STRIMMImage): STRIMMImage {
        val pix = data.pix as FloatArray
        val newPix = FloatArray(pix.size)
        for (f in 0 until pix.size) {
            newPix[f] = pix[f]
        }
        for (f in 0 until pix.size / 2) {
            newPix[f] = pix[f] + 100.0F
        }
        return STRIMMImage(data.sourceCamera, newPix, data.timeAcquired, data.imageCount, data.w, data.h)
    }

    private var imageSelectorFlowLastImage = hashMapOf<String, STRIMMImage>()
    //TODO what is the purpose of this flow
    private fun imageSelectorFlow(data: STRIMMImage, fl: uk.co.strimm.experiment.Flow): STRIMMImage {
        val a: Int = fl.param1.toInt()
        val d: Int = fl.param2.toInt()

        if ((data.imageCount - a) % d == 0) {
            imageSelectorFlowLastImage[fl.flowName] = data
        }
        return imageSelectorFlowLastImage[fl.flowName] as STRIMMImage
    }


    private fun imageReducerFlow(data: STRIMMImage, fl: uk.co.strimm.experiment.Flow): STRIMMImage {
        val x = fl.param1.toInt()
        val y = fl.param2.toInt()
        val w = fl.param3.toInt()
        val h = fl.param4.toInt()
        val im_w = data.w
        var im_h = data.h
        val pix = data.pix as ShortArray
        val newPix = ShortArray(w * h)
        //issues with 0-ref and 1-ref
        for (j in 0 until h) {
            for (i in 0 until w) {
                newPix[i + w * j] = pix[(x - 1 + i) + im_w * (y - 1 + j)]
            }
        }
        return STRIMMImage(data.sourceCamera, newPix, data.timeAcquired, data.imageCount, w, h)
    }

    private fun imageDualReducerCombineAdditionFlow(data: STRIMMImage, fl: uk.co.strimm.experiment.Flow): STRIMMImage {
        val x1 = fl.param1.toInt()
        val y1 = fl.param2.toInt()
        val x2 = fl.param3.toInt()
        val y2 = fl.param4.toInt()
        val w = fl.param5.toInt()
        val h = fl.param6.toInt()

        val im_w = data.w
        var im_h = data.h
        val pix = data.pix as ShortArray
        val newPix = ShortArray(w * h)
        //issues with 0-ref and 1-ref
        for (j in 0 until h) {
            for (i in 0 until w) {
                newPix[i + w * j] = (pix[(x1 - 1 + i) + im_w * (y1 - 1 + j)] + pix[(x2 - 1 + i) + im_w * (y2 - 1 + j)]).toShort()
            }
        }
        return STRIMMImage(data.sourceCamera, newPix, data.timeAcquired, data.imageCount, w, h)
    }

    private fun imageDualReducerCombineRatioFlow(data: STRIMMImage, fl: uk.co.strimm.experiment.Flow): STRIMMImage {
        val x1 = fl.param1.toInt()
        val y1 = fl.param2.toInt()
        val x2 = fl.param3.toInt()
        val y2 = fl.param4.toInt()
        val w = fl.param5.toInt()
        val h = fl.param6.toInt()

        val im_w = data.w
        val im_h = data.h
        val pix = data.pix as ShortArray
        val newPix = ShortArray(w * h)
        //issues with 0-ref and 1-ref
        for (j in 0 until h) {
            for (i in 0 until w) {
                newPix[i + w * j] = (pix[(x1 - 1 + i) + im_w * (y1 - 1 + j)] / pix[(x2 - 1 + i) + im_w * (y2 - 1 + j)]).toShort()
            }
        }
        return STRIMMImage(data.sourceCamera, newPix, data.timeAcquired, data.imageCount, w, h)
    }

    private fun imageRatioFlowFloat(data: STRIMMImage, fl: uk.co.strimm.experiment.Flow): STRIMMImage {
        val scale = fl.param1
        val offset = fl.param2
        if ((data.imageCount - 1) % 2 == 0) { // odd
            imageSelectorFlowLastImage[fl.flowName + "_odd"] = data
        } else { //even
            val pixOdd = imageSelectorFlowLastImage[fl.flowName + "_odd"]!!.pix as FloatArray
            val pixEven = data.pix as FloatArray

            val newPix = FloatArray(data.pix.size)
            for (pixel in 0 until newPix.size) {
                var result = (pixEven[pixel].toDouble() / pixOdd[pixel].toDouble()) * scale + offset
                if (result < 0) {
                    result = 0.0
                }

                if (result > 0xFFFF.toDouble()) {
                    result = 0xFFFF.toDouble()
                }

                newPix[pixel] = result.toFloat()
            }
            imageSelectorFlowLastImage[fl.flowName] = STRIMMImage(data.sourceCamera, newPix, data.timeAcquired, data.imageCount, data.w, data.h)
        }
        return imageSelectorFlowLastImage[fl.flowName] as STRIMMImage
    }

    private fun imageRatioFlowShort(data: STRIMMImage, fl: uk.co.strimm.experiment.Flow): STRIMMImage {
        if (data.pix == null) {
            return data
        }

        val scale = fl.param1
        val offset = fl.param2
        if ((data.imageCount - 1) % 2 == 0) { // odd
            //store the odd result
            imageSelectorFlowLastImage[fl.flowName + "_odd"] = data
        } else { //even
            if ((imageSelectorFlowLastImage[fl.flowName + "_odd"] == null) || (imageSelectorFlowLastImage[fl.flowName + "_odd"]?.pix == null)) {
                val pix = ShortArray((data.pix as ShortArray).size)
                for (pixel in 0 until pix.size) {
                    //TODO this seems like an inefficient way to create a ShortArray of zeros
                    pix[pixel] = 0
                }
                imageSelectorFlowLastImage[fl.flowName + "_odd"] = STRIMMImage(data.sourceCamera, pix, data.timeAcquired, data.imageCount, data.w, data.h)
            }

            val pixOdd = imageSelectorFlowLastImage[fl.flowName + "_odd"]?.pix as ShortArray
            val pixEven = data.pix as ShortArray
            val newPix = ShortArray(data.pix.size)
            for (f in 0 until newPix.size) {
                val res = (pixEven[f].toDouble() / pixOdd[f].toDouble()) * scale + offset
                newPix[f] = res.toShort()
            }
            imageSelectorFlowLastImage[fl.flowName] = STRIMMImage(data.sourceCamera, newPix, data.timeAcquired, data.imageCount, data.w, data.h)
        }
        return imageSelectorFlowLastImage[fl.flowName] as STRIMMImage
    }

    private fun imageHistogramFlowShort(data: STRIMMImage, fl: uk.co.strimm.experiment.Flow): STRIMMImage {
        if (data.pix == null) {
            return data
        }
        val numBins = fl.param1.toInt()
        val xMin = fl.param2
        val xMax = fl.param3

        if ((data.imageCount - 1) % 2 == 0) { // odd
            //store the odd result
            imageSelectorFlowLastImage[fl.flowName + "_odd"] = data
        } else { //even
            if ((imageSelectorFlowLastImage[fl.flowName + "_odd"] == null) || (imageSelectorFlowLastImage[fl.flowName + "_odd"]?.pix == null)) {
                val pix = ShortArray((data.pix as ShortArray).size)
                for (pixel in 0 until pix.size) {
                    //TODO this seems like an inefficient way to create a ShortArray of zeros
                    pix[pixel] = 0
                }
                imageSelectorFlowLastImage[fl.flowName + "_odd"] = STRIMMImage(data.sourceCamera, pix, data.timeAcquired, data.imageCount, data.w, data.h)
            }

            val pixOdd = imageSelectorFlowLastImage[fl.flowName + "_odd"]?.pix as ShortArray
            val pixEven = data.pix as ShortArray

            //Create and construct histogram
            val values = DoubleArray(numBins * 2)
            //add 2 * numBins slots to arrayList
            for (value in 0 until values.size) {
                values[value] = 0.0
            }

            val binWidth = (xMax - xMin) / numBins.toDouble()

            //Loop over images
            var imageOddMean = 0.0
            for (pixel in 0 until pixOdd.size) {
                // ignore is val < xMin or > xMax
                if (pixOdd[pixel] < xMin || pixOdd[pixel] > xMax) {
                    continue
                }
                val curVal = (round((pixOdd[pixel] - xMin) / binWidth)).toInt()
                if (curVal < numBins) values[curVal]++
                imageOddMean += pixOdd[pixel]
            }
            imageOddMean /= pixOdd.size

            var imageEvenMean = 0.0
            for (pixel in 0 until pixEven.size) {
                // ignore is val < xMin or > xMax
                if (pixEven[pixel] < xMin || pixEven[pixel] > xMax) {
                    continue
                }
                val curVal = (round((pixEven[pixel] - xMin) / binWidth) + numBins).toInt()
                if (curVal < 2 * numBins && curVal >= numBins) values[curVal]++

                imageEvenMean += pixEven[pixel]
            }
            imageEvenMean /= pixEven.size

            val details: String = "Mean image1 :" + round(imageOddMean).toString() + " , Mean image2 :" + round(imageEvenMean).toString()

            //TODO hardcoded
            val pix = ShortArray(1000 * 1000)
            for (pixel in 0 until pix.size) {
                pix[pixel] = 0
            }

            GUIMain.protocolService.GDI_Test_Write_Array(1000, 1000, pix, numBins,
                    2, details, xMin, xMax, values)

            imageSelectorFlowLastImage[fl.flowName + "_hist"] = STRIMMImage(
                    data.sourceCamera,
                    pix,
                    data.timeAcquired,
                    data.imageCount,
                    1000,
                    1000)
        }
        return imageSelectorFlowLastImage[fl.flowName + "_hist"] as STRIMMImage
    }

    private fun histogramFlow(data: STRIMMImage, min: Double, max: Double, numBins: Int): STRIMMImage {
        if (data.pix == null) {
            return STRIMMImage("", null, 0, 0, 0, 0)
        }

        val pix0 = data.pix as ShortArray
        val frequencies = IntArray(numBins)
        val newPix = ShortArray(pix0.size)
        val binWidth = (max - min) / numBins
        var freqMax = 0

        for (pixel in 0 until pix0.size) {
            var bin = (floor((pix0[pixel].toFloat() - min) / binWidth.toFloat())).toInt()
            if (pix0[pixel] > max) {
                bin = numBins - 1
            }

            if (bin > numBins - 1) {
                bin = numBins - 1
            }

            frequencies[bin]++
            if (frequencies[bin] > freqMax) {
                freqMax = frequencies[bin]
            }
            newPix[pixel] = 0
        }

        for (i in 0 until data.w) {
            var bin = (floor(i.toFloat() / data.w.toFloat() * (numBins - 1).toFloat())).toInt()
            if (bin > numBins - 1) {
                bin = numBins - 1
            }

            val y = (frequencies[bin].toFloat() / freqMax.toFloat() * (data.h - 100).toFloat()).toInt()
            for (j in 0 until y) {
                //TODO what is this 5000 value?
                newPix[i + (data.h - 100 - j) * data.w] = 5000
            }
        }
        return STRIMMImage(data.sourceCamera, newPix, data.timeAcquired, data.imageCount, data.w, data.h)
    }

    //TODO many hardcoded values
    private fun GDIFlowTest(data: List<List<TraceData>>): STRIMMImage {
        val pix = ShortArray(1000 * 1000)
        for (f in 0 until pix.size) {
            pix[f] = 0
        }

        val values = DoubleArray(20)
        for (f in 0..10) {
            values[f] = 1.0
        }

        for (f in 10..19) {
            values[f] = 2.0
        }

        GUIMain.protocolService.GDI_Test_Write_Array(1000, 1000, pix, 10,
                2, "Mean 604nm : 205,3, Mean 410nm : 120.9", 0.0, 10.0, values)

        return STRIMMImage("GDI_image", pix, data[0][0].timeAcquired, 0, 1000, 1000)
    }

    private fun GDIIndicatorPair(data: List<STRIMMImage>): STRIMMImage {
        if (data[0].pix == null || data[1].pix == null) {
            return STRIMMImage("", null, 0, 0, 0, 0)
        }

        val nums = IntArray(2)
        nums[0] = 100 //mean0
        nums[1] = 200 //mean1

        GUIMain.protocolService.GDI_Write_Numbers_onto_Array(nums)
        return data[0]
    }

    //TODO this is unused, can it be removed?
    private fun BinaryOp_Flow(data: List<STRIMMImage>, offset: Float, scale: Float): STRIMMImage {
        if (data[0].pix == null || data[1].pix == null) {
            return STRIMMImage("", null, 0, 0, 0, 0)
        }

        val pix0 = data[0].pix as ShortArray
        val pix1 = data[1].pix as ShortArray
        //binary operation
        val newPix = ShortArray(pix0.size)
        for (pixel in 0 until pix0.size) {
            var result = (pix0[pixel].toFloat() / pix1[pixel].toFloat()) * scale + offset
            if (result < 0) {
                result = 0.0F
            }

            if (result > 65000) {
                result = 0xFFFF.toFloat()
            }

            newPix[pixel] = result.toShort()
        }

        val ret = STRIMMImage(data[0].sourceCamera, newPix, data[0].timeAcquired, data[0].imageCount, data[0].w, data[0].h)
        return ret
    }

    //TODO this is not used, can it be removed?
    private fun SaveImageFromFlow(data: STRIMMImage): STRIMMImage {
        val pix = data.pix as ShortArray
        GUIMain.exportService.writeImageDataTmp(3200, 3200, pix);
        return data
    }

    //TODO this is not used, can it be removed?
    private fun SlowSaveFromFlow(data: STRIMMImage, szCamera: String, w: Long, h: Long): STRIMMImage {
        return data
    }

    //TODO can these variables go at the top of the class?
    var bFirst = true
    var overlay1 = ""
    var overlay2 = ""
    var curValue1 = 0.0
    var curValue2 = 0.0
    var overlayHash = hashMapOf<String, Overlay?>()
    var overlayFirst: String = ""
    var curValue = 0.0

    private fun Add(data: List<List<TraceData>>): List<List<TraceData>> {
        val pix = data[0]
        val overlay = pix[0].data.first
        val time = pix[0].timeAcquired

        if (bFirst) {
            overlayFirst = overlay.toString()
            bFirst = false
        }
        if (overlay.toString() == overlayFirst) {
            curValue1 = pix[0].data.second
        } else {
            curValue2 = pix[0].data.second
            curValue = curValue1 + curValue2
        }

        val tr = TraceData(Pair(overlay, curValue), time)
        val li1 = arrayListOf(tr)
        val traceDataList = arrayListOf<List<TraceData>>(li1) //we have a list of TraceData because we could have lots of data series
        traceDataList.flatten()
        return traceDataList
    }

    //TODO this function is functionally not used, can it be removed?
    private fun TestFn(data: List<List<TraceData>>): List<List<TraceData>> {
        var val1 = data[0][0].data.second
        return data
    }

    var countII: Int = 0
    //TODO this function is not used, can it be removed?
    private fun TraceToImage(data: List<List<TraceData>>): STRIMMImage {
        val pix = ShortArray(200 * 200)

        for (i in 0 until pix.size) {
            pix[i] = Random().nextInt((65000 + 1)).toShort()
        }
        val image = STRIMMImage("none", pix, GUIMain.softwareTimerService.getTime(), countII, 200, 200);
        countII++
        return image
    }

    private fun split(data: List<List<TraceData>>, flow: uk.co.strimm.experiment.Flow): List<List<TraceData>> {
        val overlay: Overlay?
        val time = data[0][0].timeAcquired
        if (flow.param1.toInt() == 0) {
            curValue = data[0][0].data.second
            overlay = data[0][0].data.first
        } else {
            curValue = data[0][1].data.second
            overlay = data[0][1].data.first
        }

        val tr = TraceData(Pair(overlay, curValue), time)
        val li1 = arrayListOf(tr)
        val traceDataList = arrayListOf<List<TraceData>>(li1) //we have a list of TraceData because we could have lots of data series
        return traceDataList
    }

    /**
     * Go through the experiment config (from json file) and create the appropriate flow objects that correspond to the
     * config. This uses custom wrapper classes for the akka objects. This also specifies types explicitly
     * @param expConfig The experiment config from the JSON config file
     * @param builder The graph building object
     */
    private fun populateFlows(expConfig: ExperimentConfiguration, builder: GraphDSL.Builder<NotUsed>) {
        try {
            for (flow in expConfig.flowConfig.flows) {
                if (flow.inputType.toLowerCase() == ExperimentConstants.ConfigurationProperties.IMAGE_INPUT_TYPE.toLowerCase() &&
                        flow.outputType.toLowerCase() == ExperimentConstants.ConfigurationProperties.IMAGE_OUTPUT_TYPE.toLowerCase()) {//IMAGE TO IMAGE FLOW
                    val expFlow = ExperimentImageImageFlow(flow.flowName)
                    val threshhold = 1200.0 //TODO hardcoded
                    when {
                        flow.flowType == "ThreshholdImage" -> { //TODO move string to constants
                            val akkaFlow = Flow.of(STRIMMImage::class.java)
                                    .map {
                                        threshholdImage(it, threshhold)
                                    }
                                    .async()
                                    .named(flow.flowName)
                            expFlow.flow = builder.add(akkaFlow)
                        }
                        flow.flowType == "FlowReduceSize" -> { //TODO move string to constants
                            val akkaFlow = Flow.of(STRIMMImage::class.java)
                                    .map {
                                        flowReduceSize(it)
                                    }
                                    .async()
                                    .named(flow.flowName)
                            expFlow.flow = builder.add(akkaFlow)
                        }
                        flow.flowType == "ReduceFlow" -> { //TODO move string to constants
                            val akkaFlow = Flow.of(STRIMMImage::class.java)
                                    .map {
                                        reduceFlow(it)
                                    }
                                    .async()
                                    .named(flow.flowName)
                            expFlow.flow = builder.add(akkaFlow)
                        }
                        flow.flowType == "ImageSelectorFlow" -> { //TODO move string to constants
                            imageSelectorFlowLastImage[flow.flowName] = STRIMMImage("", null, 0, 0, 0, 0)
                            val akkaFlow = Flow.of(STRIMMImage::class.java)
                                    .map {
                                        imageSelectorFlow(it, flow)
                                    }
                                    .async()
                                    .named(flow.flowName)
                            expFlow.flow = builder.add(akkaFlow)
                        }
                        flow.flowType == "ImageReducerFlow" -> { //TODO move string to constants
                            val akkaFlow = Flow.of(STRIMMImage::class.java)
                                    .map {
                                        imageReducerFlow(it, flow)
                                    }
                                    .async()
                                    .named(flow.flowName)
                            expFlow.flow = builder.add(akkaFlow)
                        }
                        flow.flowType == "ImageDualReducerCombineAdditionFlow" -> { //TODO move string to constants
                            val akkaFlow = Flow.of(STRIMMImage::class.java)
                                    .map {
                                        imageDualReducerCombineAdditionFlow(it, flow)
                                    }
                                    .async()
                                    .named(flow.flowName)
                            expFlow.flow = builder.add(akkaFlow)
                        }
                        flow.flowType == "ImageDualReducerCombineRatioFlow" -> { //TODO move string to constants
                            val akkaFlow = Flow.of(STRIMMImage::class.java)
                                    .map {
                                        imageDualReducerCombineRatioFlow(it, flow)
                                    }
                                    .async()
                                    .named(flow.flowName)
                            expFlow.flow = builder.add(akkaFlow)
                        }
                        flow.flowType == "BrightFlow" -> { //TODO move string to constants
                            val akkaFlow = Flow.of(STRIMMImage::class.java)
                                    .map {
                                        brightFlow(it)
                                    }
                                    .async()
                                    .named(flow.flowName)
                            expFlow.flow = builder.add(akkaFlow)
                        }
                        flow.flowType == "GDIIndicatorPair" -> { //TODO move string to constants
                            val akkaFlow = Flow.of(STRIMMImage::class.java)
                                    .grouped(2)
                                    .map {
                                        GDIIndicatorPair(it)
                                    }
                                    .async()
                                    .named(flow.flowName)
                            expFlow.flow = builder.add(akkaFlow)
                        }
                        flow.flowType == "HistogramFlow" -> { //TODO move string to constants
                            val akkaFlow = Flow.of(STRIMMImage::class.java)
                                    .map {
                                        histogramFlow(it, 0.0, 1000.0, 50)
                                    }
                                    .async()
                                    .named(flow.flowName)
                            expFlow.flow = builder.add(akkaFlow)
                        }
                        flow.flowType == "Ratio_Flow_Float" -> { //TODO move string to constants
                            imageSelectorFlowLastImage[flow.flowName] = STRIMMImage("", null, 0, 0, 0, 0)
                            imageSelectorFlowLastImage[flow.flowName + "_odd"] = STRIMMImage("", null, 0, 0, 0, 0)

                            val akkaFlow = Flow.of(STRIMMImage::class.java)
                                    .map {
                                        imageRatioFlowFloat(it, flow)
                                    }
                                    .async()
                                    .named(flow.flowName)
                            expFlow.flow = builder.add(akkaFlow)
                        }
                        flow.flowType == "Ratio_Flow_Short" -> { //TODO move string to constants
                            imageSelectorFlowLastImage[flow.flowName] = STRIMMImage("", null, 0, 0, 0, 0)
                            imageSelectorFlowLastImage[flow.flowName + "_odd"] = STRIMMImage("", null, 0, 0, 0, 0)

                            val akkaFlow = Flow.of(STRIMMImage::class.java)
                                    .map {
                                        imageRatioFlowShort(it, flow)
                                    }
                                    .async()
                                    .named(flow.flowName)
                            expFlow.flow = builder.add(akkaFlow)
                        }
                        flow.flowType == "Histogram_Flow_Short" -> { //TODO move string to constants
                            imageSelectorFlowLastImage[flow.flowName] = STRIMMImage("", null, 0, 0, 0, 0)
                            imageSelectorFlowLastImage[flow.flowName + "_odd"] = STRIMMImage("", null, 0, 0, 0, 0)
                            imageSelectorFlowLastImage[flow.flowName + "_hist"] = STRIMMImage("", null, 0, 0, 0, 0)

                            val akkaFlow = Flow.of(STRIMMImage::class.java)
                                    .map {
                                        imageHistogramFlowShort(it, flow)
                                    }
                                    .async()
                                    .named(flow.flowName)
                            expFlow.flow = builder.add(akkaFlow)
                        }
                        flow.flowType == "ConsecutiveImageRatio" -> { //TODO move string to constants
                            val akkaFlow = Flow.of(STRIMMImage::class.java)
                                    .map {
                                        pairImageRatio(it)
                                    }
                                    .async()
                                    .named(flow.flowName)
                            expFlow.flow = builder.add(akkaFlow)
                        }
                        flow.flowType == "DataTap" -> { //TODO move string to constants
                            val akkaFlow = Flow.of(STRIMMImage::class.java)
                                    .map {
                                        printFlow(it)
                                    }
                                    .async()
                                    .named(flow.flowName)
                            expFlow.flow = builder.add(akkaFlow)

                        }
                        else -> { // flow.flowType == "Identity"
                            val akkaFlow = Flow.of(STRIMMImage::class.java)
                                    .buffer(ExperimentConstants.ConfigurationProperties.IMAGE_BUFFER_SIZE, OverflowStrategy.dropTail())
                                    .async()
                                    .named(flow.flowName)
                            expFlow.flow = builder.add(akkaFlow)
                        }
                    }
                    expFlow.inputType = flow.inputType
                    expFlow.outputType = flow.outputType
                    experimentImageImageFlows.add(expFlow)
                } else if (flow.inputType.toLowerCase() == ExperimentConstants.ConfigurationProperties.IMAGE_INPUT_TYPE.toLowerCase() &&
                        flow.outputType.toLowerCase() == ExperimentConstants.ConfigurationProperties.TRACE_OUTPUT_TYPE.toLowerCase()) {
                    val expFlow = ExperimentImageTraceFlow(flow.flowName)
                    GUIMain.strimmUIService.traceFromROICounterPerDisplayROIFlow[flow.flowName] = 0
                    GUIMain.strimmUIService.traceColourByFlowAndOverlay[flow.flowName] = ArrayList()

                    //This flow will have the displaySink and allows all to be chosen from rois
                    val roiForThisFlow = GUIMain.experimentService.expConfig.roiConfig.rois.filter { x -> x.flowName.toLowerCase() == flow.flowName.toLowerCase() }

                    //There might be a set of rois which are associated with STRIMMImage
                    for (roi in roiForThisFlow) {
                        val overlay = ROIManager.createOverlayFromROIObject(roi)
                        //Find the associated sink for this flow
                        var displaySink = GUIMain.experimentService.experimentStream.expConfig.sinkConfig.sinks.filter { snk -> snk.roiFlowName == flow.flowName }.first()

                        //Setting colours for ROIs
                        val colNum = (GUIMain.strimmUIService.traceFromROICounterPerDisplayROIFlow[flow.flowName]) as Int
                        if (colNum < GUIMain.roiColours.size) {
                            val col: Color = GUIMain.roiColours[colNum]
                            val overlayColourPair = GUIMain.strimmUIService.traceColourByFlowAndOverlay[flow.flowName] as ArrayList<Pair<Overlay, Int>>
                            overlayColourPair.add(Pair(overlay as Overlay, colNum))
                            overlay.fillColor = ColorRGB((255.0 * col.red).toInt(), (255.0 * col.green).toInt(), (255.0 * col.blue).toInt())
                            overlay.lineColor = ColorRGB((255.0 * col.red).toInt(), (255.0 * col.green).toInt(), (255.0 * col.blue).toInt())
                        } else {
                            overlay!!.fillColor = ColorRGB(100, 100, 100)
                            overlay.lineColor = ColorRGB(100, 100, 100)
                        }

                        val curVal: Int = GUIMain.strimmUIService.traceFromROICounterPerDisplayROIFlow[flow.flowName] as Int
                        GUIMain.actorService.routedRoiOverlays[overlay] = flow.flowName
                        GUIMain.strimmUIService.traceFromROICounter++
                        GUIMain.strimmUIService.traceFromROICounterPerDisplayROIFlow[flow.flowName] = curVal + 1
                    }

                    val averageROIAcquisitionMethod =
                            GUIMain.acquisitionMethodService.getAcquisitionMethod(AVERAGE_ROI_METHOD_NAME) as AcquisitionMethodService.TraceMethod
                    val akkaFlow = Flow.of(STRIMMImage::class.java)
                            .map {
                                averageROIAcquisitionMethod.runMethod(it, flow.flowName)
                            }
                            .groupedWithin(ExperimentConstants.ConfigurationProperties.TRACE_GROUPING_AMOUNT,
                                    Duration.ofMillis(ExperimentConstants.ConfigurationProperties.TRACE_GROUPING_DURATION_MS))
                            .async()
                            .named(flow.flowName)

                    expFlow.inputType = flow.inputType
                    expFlow.outputType = flow.outputType
                    expFlow.flow = builder.add(akkaFlow)
                    experimentImageTraceFlows.add(expFlow)
                } else if (flow.inputType.toLowerCase() == ExperimentConstants.ConfigurationProperties.TRACE_INPUT_TYPE.toLowerCase() &&
                        flow.outputType.toLowerCase() == ExperimentConstants.ConfigurationProperties.IMAGE_OUTPUT_TYPE.toLowerCase())
                {
                    val expFlow = ExperimentTraceImageFlow(flow.flowName)
                    val generateImageMethod =
                            GUIMain.acquisitionMethodService.getAcquisitionMethod(ExperimentConstants.Acquisition.GENERATE_IMAGE_METHOD_NAME) as AcquisitionMethodService.ImageMethod

                    val akkaFlow = Flow.of(List::class.java)
//                        .groupedWithin(
//                            ExperimentConstants.ConfigurationProperties.TRACE_GROUPING_AMOUNT,
//                            Duration.ofMillis(ExperimentConstants.ConfigurationProperties.TRACE_GROUPING_DURATION_MS)
//                        )
                            .map { GDIFlowTest(it as List<List<TraceData>>) }
                            .async()
                            .named(flow.flowName) as Flow<List<ArrayList<TraceData>>, STRIMMImage, NotUsed> //The "groupedWithin" call turns this into a List of ArrayLists
                    expFlow.inputType = flow.inputType
                    expFlow.outputType = flow.outputType
                    expFlow.flow = builder.add(akkaFlow)
                    experimentTraceImageFlows.add(expFlow)
                } else if (flow.inputType.toLowerCase() == ExperimentConstants.ConfigurationProperties.TRACE_INPUT_TYPE.toLowerCase() &&
                        flow.outputType.toLowerCase() == ExperimentConstants.ConfigurationProperties.TRACE_OUTPUT_TYPE.toLowerCase())
                {
                    val expFlow = ExperimentTraceTraceFlow(flow.flowName)
                    when {
                        flow.flowType == "ChannelSelector" -> {
                            val numChannels = GUIMain.protocolService.GetTotalNumberOfChannels()
                            val channelIndices = GUIMain.protocolService.GetChannelList(flow.flowDetails)
                            val akkaFlow = Flow.of(List::class.java)
                                    .map {
                                        SelectChannels((it as List<List<TraceData>>), channelIndices.toIntArray(), numChannels)
                                    }
                                    .async()
                                    .named(flow.flowName) as Flow<List<ArrayList<TraceData>>, List<ArrayList<TraceData>>, NotUsed> //The "groupedWithin" call turns this into a List of ArrayLists
                            expFlow.inputType = flow.inputType
                            expFlow.outputType = flow.outputType
                            expFlow.flow = builder.add(akkaFlow)
                        }
                        flow.flowType == "Arduino_Digital_Output" -> {
                            val akkaFlow = Flow.of(List::class.java)
                                    .map {
                                        Arduino_Digital_Output((it as List<List<TraceData>>))
                                    }
                                    .async()
                                    .named(flow.flowName) as Flow<List<ArrayList<TraceData>>, List<ArrayList<TraceData>>, NotUsed> //The "groupedWithin" call turns this into a List of ArrayLists
                            expFlow.inputType = flow.inputType
                            expFlow.outputType = flow.outputType
                            expFlow.flow = builder.add(akkaFlow)
                        }
                        flow.flowType == "Threshold_Flow" -> {
                            val threshold = flow.param1
                            val channel = flow.param2.toInt()
                            val type = flow.param3.toInt()
                            val akkaFlow = Flow.of(List::class.java)
                                    .map {
                                        thresholdFlowController((it as List<List<TraceData>>), threshold, channel, type)
                                    }
                                    .async()
                                    .named(flow.flowName) as Flow<List<ArrayList<TraceData>>, List<ArrayList<TraceData>>, NotUsed> //The "groupedWithin" call turns this into a List of ArrayLists
                            expFlow.inputType = flow.inputType
                            expFlow.outputType = flow.outputType
                            expFlow.flow = builder.add(akkaFlow)
                        }
                        flow.flowType == "GlobalSourceStartTriggerFlow" -> {
                            val akkaFlow = Flow.of(List::class.java)
                                    .map {
                                        globalSourceStartTriggerFlow((it as List<List<TraceData>>))
                                    }
                                    .async()
                                    .named(flow.flowName) as Flow<List<ArrayList<TraceData>>, List<ArrayList<TraceData>>, NotUsed> //The "groupedWithin" call turns this into a List of ArrayLists
                            expFlow.inputType = flow.inputType
                            expFlow.outputType = flow.outputType
                            expFlow.flow = builder.add(akkaFlow)
                        }
                        flow.flowType == "ArduinoDO3" -> {
                            val akkaFlow = Flow.of(List::class.java)
                                    .map {
                                        arduinoDO3Controller((it as List<List<TraceData>>))
                                    }
                                    .async()
                                    .named(flow.flowName) as Flow<List<ArrayList<TraceData>>, List<ArrayList<TraceData>>, NotUsed> //The "groupedWithin" call turns this into a List of ArrayLists
                            expFlow.inputType = flow.inputType
                            expFlow.outputType = flow.outputType
                            expFlow.flow = builder.add(akkaFlow)
                        }
                        flow.flowType == "ArduinoDO4" -> {
                            val akkaFlow = Flow.of(List::class.java)
                                    .map {
                                        arduinoDO4Controller((it as List<List<TraceData>>))
                                    }
                                    .async()
                                    .named(flow.flowName) as Flow<List<ArrayList<TraceData>>, List<ArrayList<TraceData>>, NotUsed> //The "groupedWithin" call turns this into a List of ArrayLists
                            expFlow.inputType = flow.inputType
                            expFlow.outputType = flow.outputType
                            expFlow.flow = builder.add(akkaFlow)
                        }
                        flow.flowType == "TestFn" -> {
                            val akkaFlow = Flow.of(List::class.java)
                                    .map {
                                        TestFn((it as List<List<TraceData>>))
                                    }
                                    .async()
                                    .named(flow.flowName) as Flow<List<ArrayList<TraceData>>, List<ArrayList<TraceData>>, NotUsed> //The "groupedWithin" call turns this into a List of ArrayLists
                            expFlow.inputType = flow.inputType
                            expFlow.outputType = flow.outputType
                            expFlow.flow = builder.add(akkaFlow)
                        }
                        flow.flowType == "Add" -> {
                            val akkaFlow = Flow.of(List::class.java)
                                    .map {
                                        Add((it as List<List<TraceData>>))
                                    }
                                    .async()
                                    .named(flow.flowName) as Flow<List<ArrayList<TraceData>>, List<ArrayList<TraceData>>, NotUsed> //The "groupedWithin" call turns this into a List of ArrayLists
                            expFlow.inputType = flow.inputType
                            expFlow.outputType = flow.outputType
                            expFlow.flow = builder.add(akkaFlow)
                        }
                        flow.flowType == "Split" -> {
                            val akkaFlow = Flow.of(List::class.java)
                                    .map {
                                        split((it as List<List<TraceData>>), flow)
                                    }
                                    .async()
                                    .named(flow.flowName) as Flow<List<ArrayList<TraceData>>, List<ArrayList<TraceData>>, NotUsed> //The "groupedWithin" call turns this into a List of ArrayLists
                            expFlow.inputType = flow.inputType
                            expFlow.outputType = flow.outputType
                            expFlow.flow = builder.add(akkaFlow)
                        }
                        flow.flowType == "NIDAQ_inject_AO" -> {
                            val numSamples = GUIMain.protocolService.GetNextNumSamples()
                            val pInjectAO = DoubleArray(numSamples)
                            val channel = 0
                            val akkaFlow = Flow.of(List::class.java)
                                    .map {
                                        NIDAQInjectAO((it as List<List<TraceData>>), channel, pInjectAO)
                                    }
                                    .async()
                                    .named(flow.flowName) as Flow<List<ArrayList<TraceData>>, List<ArrayList<TraceData>>, NotUsed> //The "groupedWithin" call turns this into a List of ArrayLists
                            expFlow.inputType = flow.inputType
                            expFlow.outputType = flow.outputType
                            expFlow.flow = builder.add(akkaFlow)
                        }
                        flow.flowType == "NIDAQ_inject_DO" -> {
                            val numSamples = GUIMain.protocolService.GetNextNumSamples()
                            val pInjectDO = IntArray(numSamples)
                            val channel = 0
                            val akkaFlow = Flow.of(List::class.java)
                                    .map {
                                        NIDAQInjectDO((it as List<List<TraceData>>), channel, pInjectDO)
                                    }
                                    .async()
                                    .named(flow.flowName) as Flow<List<ArrayList<TraceData>>, List<ArrayList<TraceData>>, NotUsed> //The "groupedWithin" call turns this into a List of ArrayLists
                            expFlow.inputType = flow.inputType
                            expFlow.outputType = flow.outputType
                            expFlow.flow = builder.add(akkaFlow)
                        }
                        else -> {
                            //flowType = "Identity"
                            val akkaFlow = Flow.of(List::class.java)
                                    .async()
                                    .named(flow.flowName) as Flow<List<ArrayList<TraceData>>, List<ArrayList<TraceData>>, NotUsed> //The "groupedWithin" call turns this into a List of ArrayLists
                            expFlow.inputType = flow.inputType
                            expFlow.outputType = flow.outputType
                            expFlow.flow = builder.add(akkaFlow)
                        }
                    }
                    experimentTraceTraceFlows.add(expFlow)
                }
            }
        } catch (ex: Exception) {
            GUIMain.loggerService.log(Level.SEVERE, "Error creating flows from json experiment configuration. Error message: ${ex.message}")
            GUIMain.loggerService.log(Level.SEVERE, ex.stackTrace)
        }
    }

    private fun cameraFunction(cameraSz: String, fps: Double, interval: Double, w: Int, h: Int, pix: Any, bytesPerPixel: Int, timeAcquired: Double): Int {
        if (GUIMain.protocolService.GetCameraMapStatus() > 0) {
            val src = GUIMain.experimentService.expConfig.sourceConfig.sources.filter { x ->
                x.sourceName == cameraSz
            }.first()
            src.param1 = timeAcquired

            //throttle to previewInterval
            if (timeAcquired > src.timeLastCaptured + src.previewInterval) {
                src.timeLastCaptured = timeAcquired

                //Send images to cam if within the preview rate
                when (bytesPerPixel) {
                    1 -> return GUIMain.protocolService.Add8BitImageDataCameraMap(
                            cameraSz,
                            0.0,
                            0.0,
                            w,
                            h,
                            pix as ByteArray,
                            false)
                    2 -> return GUIMain.protocolService.Add16BitImageDataCameraMap(
                            cameraSz,
                            0.0,
                            0.0,
                            w,
                            h,
                            pix as ShortArray,
                            false)
                    4 -> return GUIMain.protocolService.AddARGBBitImageDataCameraMap(
                            cameraSz,
                            0.0,
                            0.0,
                            w,
                            h,
                            pix as ByteArray,
                            false)
                    else -> {
                        GUIMain.loggerService.log(Level.SEVERE, "Bytes per pixel: $bytesPerPixel not supported")
                        return 0
                    }
                }
            }
        }
        return -1
    }

    private fun populateSinks(expConfig: ExperimentConfiguration, builder: GraphDSL.Builder<NotUsed>) {
        try {
            for (sink in expConfig.sinkConfig.sinks) {
                if (sink.outputType.toLowerCase() == ExperimentConstants.ConfigurationProperties.IMAGE_OUTPUT_TYPE.toLowerCase()) {
                    if (sink.displayOrStore.toLowerCase() == ExperimentConstants.ConfigurationProperties.DISPLAY.toLowerCase()) {
                        when {
                            sink.sinkType == "Null" -> {
                                val expSink = ExperimentImageSink(sink.sinkName)
                                expSink.sink = builder.add(Sink.foreach { x: STRIMMImage ->
                                    val meanFrameTime: Double = x.timeAcquired.toDouble() / x.imageCount.toDouble()
                                    val rate = 1 / meanFrameTime * 1000.0
                                    if (x.imageCount % 100 == 0) {
                                        GUIMain.loggerService.log(Level.INFO, "source " + sink.primaryDevice + " intervalMs " + Math.round(meanFrameTime).toString() + "  " + "fps " + Math.round(rate).toString())
                                    }
                                })
                                expSink.outputType = sink.outputType
                                expSink.displayOrStore = sink.displayOrStore
                                experimentImageSinks.add(expSink)
                            }
                            sink.sinkType == "MultiCamera" -> {
                                val expSink = ExperimentImageSink(sink.sinkName)

                                val sourceCamera =
                                        expConfig.sourceConfig.sources.filter { x -> x.camera?.label == sink.primaryDevice }
                                                .first()

                                //CameraDevices, cameraSizeList use camera.label as the index
                                //Sink has a primaryDevice which will be used to decide the size of the display etc
                                val bytesPerPixel: Int = sourceCamera.camera!!.core.bytesPerPixel.toInt()
                                val rois = expConfig.roiConfig.rois.filter { x -> sourceCamera.camera!!.label == x.cameraDeviceLabel }
                                val roisX = rois.map { it.x.toInt() }.toIntArray()
                                val roisY = rois.map { it.y.toInt() }.toIntArray()
                                val roisW = rois.map { it.w.toInt() }.toIntArray()
                                val roisH = rois.map { it.h.toInt() }.toIntArray()

                                GUIMain.protocolService.RegisterCameraForCameraMap(
                                        sourceCamera.camera?.label as String,
                                        sourceCamera.camera?.core?.imageWidth!!.toInt(),
                                        sourceCamera.camera?.core?.imageHeight!!.toInt(),
                                        bytesPerPixel,
                                        1,
                                        rois.size,
                                        roisX,
                                        roisY,
                                        roisW,
                                        roisH)

                                bMultiCam = true

                                //Send each STRIMMImage off to cameraFunction, will render the images with GDIPlus
                                expSink.sink = builder.add(Sink.foreach { x: STRIMMImage ->
                                    val meanFrameTime = 0.0
                                    val rate = 0.0
                                    cameraFunction(sink.primaryDevice,
                                            rate,
                                            meanFrameTime,
                                            x.w,
                                            x.h,
                                            (x.pix) as Any,
                                            bytesPerPixel,
                                            x.timeAcquired.toDouble())
                                })

                                expSink.outputType = sink.outputType
                                expSink.displayOrStore = sink.displayOrStore
                                experimentImageSinks.add(expSink)
                            }
                            else -> {
                                val expSink = ExperimentImageSink(sink.sinkName)
                                //displayInfo contains information about how the surface is to be displayed along with
                                // the primaryDevice which might be used as a default
                                val displayInfo = DisplayInfo(sink.primaryDevice, sink.bitDepth.toLong(), sink.imageWidth.toLong(), sink.imageHeight.toLong(), sink.sinkName)
                                val plugin: CameraWindowPlugin = GUIMain.dockableWindowPluginService.createPlugin(CameraWindowPlugin::class.java, displayInfo, true, displayInfo.feedName)
                                plugin.cameraWindowController.roiSz = sink.roiFlowName

                                //Assign lookup table
                                plugin.cameraWindowController.lutSz = sink.lut
                                val cameraActor = GUIMain.actorService.getActorByName(displayInfo.feedName)

                                if (cameraActor != null) {
                                    cameraActors[cameraActor] = displayInfo.feedName
                                    cameraActor.tell(TellDisplaySinkName(sink.sinkName), cameraActor)
                                    cameraActor.tell(TellDisplayAutoscale(sink.autoscale), cameraActor)
                                }

                                plugin.dock(GUIMain.strimmUIService.dockableControl, GUIMain.strimmUIService.strimmFrame)
                                val akkaSink: Sink<STRIMMImage, NotUsed> = Sink.actorRefWithAck(
                                        cameraActor, StartStreamingCamera(),
                                        Acknowledgement.INSTANCE, CompleteCameraStreaming()
                                ) { ex -> FailCameraStreaming(ex) }
                                expSink.sink = builder.add(akkaSink)
                                expSink.outputType = sink.outputType
                                expSink.displayOrStore = sink.displayOrStore
                                experimentImageSinks.add(expSink)
                            }
                        }
                    } else if (sink.displayOrStore.toLowerCase() == ExperimentConstants.ConfigurationProperties.STORE.toLowerCase()) {
                        /**
                         * Create a cameraDataStoreActor - which will collect together all of the STRIMMImages which are
                         * sent to it into a large array, and then after a set number of acquisitions send them all to
                         * the FileWriterActor which will write to file. A side effect of this function is to fill
                         * the freshly created CameraDataStoreActor into the hash map cameraDataStoreActors
                         * which is HashMap<ActorRef, String>
                         */
                        val expSink = ExperimentImageSink(sink.sinkName)
                        val cameraDataStoreActor = createCameraDataStoreActor(sink.sinkName)
                        val akkaSink: Sink<STRIMMImage, NotUsed> = Sink.actorRefWithAck(cameraDataStoreActor, StartCameraDataStoring(),
                                Acknowledgement.INSTANCE, CompleteCameraDataStoring()) { ex -> FailCameraDataStoring(ex) }
                        expSink.sink = builder.add(akkaSink)
                        expSink.outputType = sink.outputType
                        expSink.displayOrStore = sink.displayOrStore
                        experimentImageSinks.add(expSink)
                    }
                } else if (sink.outputType.toLowerCase() == ExperimentConstants.ConfigurationProperties.TRACE_OUTPUT_TYPE.toLowerCase()) {
                    if (sink.displayOrStore.toLowerCase() == ExperimentConstants.ConfigurationProperties.DISPLAY.toLowerCase()) {
                        if (sink.sinkType == "Null") {
                            val expSink = ExperimentTraceSink(sink.sinkName)
                            expSink.sink = builder.add(Sink.foreach { x: List<ArrayList<TraceData>> ->
                                0.0
                            })
                            expSink.outputType = sink.outputType
                            expSink.displayOrStore = sink.displayOrStore
                            experimentTraceSinks.add(expSink)
                        } else {
                            val expSink = ExperimentTraceSink(sink.sinkName)
                            val pluginCreation = createTracePluginWithActor(sink.sinkName)
                            pluginCreation.second?.dock(
                                    GUIMain.strimmUIService.dockableControl,
                                    GUIMain.strimmUIService.strimmFrame)
                            val traceActor = pluginCreation.first
                            (pluginCreation.second as TraceWindowPlugin).traceWindowController.sinkName = sink.sinkName

                            traceActor?.tell(TellDisplaySinkName(sink.sinkName), traceActor)

                            if (experimentImageSources.isNotEmpty()) {
                                traceActor?.tell(TellDeviceSamplingRate(10.0), ActorRef.noSender())//TODO hardcoded
                                traceActor?.tell(TellSetNumDataPoints(), ActorRef.noSender())
                            }

                            val akkaSink: Sink<List<ArrayList<TraceData>>, NotUsed> = Sink.actorRefWithAck(
                                    traceActor, StartStreamingTraceROI(),
                                    Acknowledgement.INSTANCE, CompleteStreamingTraceROI()) {
                                ex -> FailStreamingTraceROI(ex)
                            }
                            expSink.sink = builder.add(akkaSink)
                            expSink.outputType = sink.outputType
                            expSink.displayOrStore = sink.displayOrStore
                            experimentTraceSinks.add(expSink)
                        }
                    } else if (sink.displayOrStore.toLowerCase() == ExperimentConstants.ConfigurationProperties.STORE.toLowerCase()) {
                        val expSink = ExperimentTraceSink(sink.sinkName)
                        val traceDataStoreActor = createTraceDataStoreActor(sink.sinkName)
                        if (sink.isROI) {
                            traceDataStoreActor.tell(TellIsTraceROIActor(), ActorRef.noSender())
                        }
                        traceDataStoreActor.tell(TellTraceSinkName(sink.sinkName), ActorRef.noSender())
                        val akkaSink: Sink<List<ArrayList<TraceData>>, NotUsed> = Sink.actorRefWithAck(traceDataStoreActor, StartTraceDataStoring(),
                                Acknowledgement.INSTANCE, CompleteTraceDataStoring()) { ex -> FailTraceDataStoring(ex) }
                        expSink.sink = builder.add(akkaSink)
                        expSink.outputType = sink.outputType
                        expSink.displayOrStore = sink.displayOrStore
                        experimentTraceSinks.add(expSink)
                    }
                }
            }
        } catch (ex: Exception) {
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
    private fun getOutlets(currentNode: ExperimentNode, expConfig: ExperimentConfiguration): ArrayList<ExperimentNode> {
        val outlets = arrayListOf<ExperimentNode>()
        try {
            for (flow in expConfig.flowConfig.flows) {
                flow.inputNames.filter { x -> x == currentNode.name }
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
                val sinksAttachedToNode = sink.inputNames.filter { x -> x == currentNode.name }
                sinksAttachedToNode.forEach {
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
        } catch (ex: Exception) {
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
    private fun getInlets(currentNode: ExperimentNode, expConfig: ExperimentConfiguration): ArrayList<ExperimentNode> {
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
        } catch (ex: Exception) {
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
    fun populateListOfCameraDevices(expConfig: ExperimentConfiguration) {
        for (src in expConfig.sourceConfig.sources) {
            if (src.camera != null) {
                val device = MMCameraDevice(src.camera) //this would set the label, libr, etc and the core
                device.label = src.deviceLabel
                cameraDevices.add(CameraDeviceInfo(device, false, src.exposureMs, src.intervalMs, "", label = src.deviceLabel))
                device.isActive = true
                noteCameraSize(device)
            }
        }
    }

    /**
     * When creating and using a camera device, make a note of its image size for future reference. It will also make
     * a note if a size different to full size has been specified
     * @param camera The camera device
     */
    private fun noteCameraSize(camera: MMCameraDevice) {
        val camSizes = Pair(camera.imageWidth, camera.imageHeight)
        GUIMain.strimmUIService.cameraSizeList[camera.label] = camSizes

        val camSourceConfig = expConfig.sourceConfig.sources.first { x -> x.sourceName == camera.label }

        if (camSourceConfig.x < 0.0 || camSourceConfig.y < 0.0) {
            GUIMain.loggerService.log(Level.WARNING, "x or y coordinate for camera device ${camera.label} is less than zero, using full size coordinates instead")
        } else if (camSourceConfig.w <= 0.0 || camSourceConfig.h <= 0.0) {
            GUIMain.loggerService.log(Level.WARNING, "w or h coordinate for camera device ${camera.label} is less than or equal to zero, using full size coordinates instead")
        } else if ((camSourceConfig.x >= 0 && camSourceConfig.y >= 0) && camSourceConfig.w > 0.0 && camSourceConfig.h > 0.0) {
            GUIMain.strimmUIService.cameraViewSizeList[camera.label] = ResizeValues(camSourceConfig.x.toLong(), camSourceConfig.y.toLong(), camSourceConfig.w.toLong(), camSourceConfig.h.toLong())
        }
    }

    /**
     * Tell all storage based actors to start acquiring data
     */
    private fun startStoringData() {
        cameraDataStoreActors.forEach { x -> x.key.tell(StartAcquiring(), ActorRef.noSender()) }
        traceDataStoreActors.forEach { x -> x.key.tell(StartTraceStore(), ActorRef.noSender()) }
    }

    /**
     * This method is called when stopping an experiment. It will send a TerminateActor() message to all store-based
     * actors
     */
    fun stopStoringData() {
        GUIMain.acquisitionMethodService.bCamerasAcquire = false;
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
    fun stopDisplayingData() {
        GUIMain.acquisitionMethodService.bCamerasAcquire = false;
        GUIMain.loggerService.log(Level.INFO, "Stopping display actors")

        //TODO review using SetEndSources as part of proper shutdown procedures
        GUIMain.protocolService.SetEndSources()

        cameraActors.forEach { x ->
            x.key.tell(TerminateActor(), ActorRef.noSender())
            GUIMain.actorService.removeActor(x.key)
        }

        traceActors.forEach { x ->
            x.key.tell(TerminateActor(), ActorRef.noSender())
            GUIMain.actorService.removeActor(x.key)
        }
    }

    /**
     * This method will create both a camera display dockable window plugin and an associated camera actor to go with it
     * @param cameraDeviceLabel The label of the camera whose data the plugin will be displaying
     * @return The newly created dockable window plugin
     */
    fun createCameraPluginWithActor(cameraDeviceLabel: String, pluginName: String): DockableWindowPlugin? {
        var cameraDeviceInfo = cameraDevices.filter { x -> x.device.label == cameraDeviceLabel }.first()
        cameraDeviceInfo = CameraDeviceInfo(cameraDeviceInfo.device, false, cameraDeviceInfo.exposureMillis, cameraDeviceInfo.intervalMillis, pluginName, cameraDeviceLabel)

        val plugin = GUIMain.dockableWindowPluginService.createPlugin(CameraWindowPlugin::class.java, cameraDeviceInfo, true, pluginName)
        val newCameraActor = GUIMain.actorService.getActorByName(pluginName)

        if (newCameraActor != null) {
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
    fun createTracePluginWithActor(traceDeviceLabel: String): Pair<ActorRef?, DockableWindowPlugin?> {
        //Make sure the actor name is truly unique
        val numStoreActorsForDevice = traceActors.count { x -> x.value == traceDeviceLabel }
        val traceActorName = if (numStoreActorsForDevice > 0) {
            "${traceDeviceLabel}TraceActor${numStoreActorsForDevice + 1}"
        } else {
            "${traceDeviceLabel}TraceActor1"
        }
        val plugin = GUIMain.dockableWindowPluginService.createPlugin(TraceWindowPlugin::class.java, "", true, traceActorName)

        val newTraceActor = GUIMain.actorService.getActorByName(traceActorName)
        if (newTraceActor != null) {
            traceActors[newTraceActor] = traceDeviceLabel
        } else {
            GUIMain.loggerService.log(Level.SEVERE, "Could not find the traceActor by its name and add to the traceActors hash")
        }

        return Pair(newTraceActor, plugin)
    }

    /**
     * Create a camera data store actor. There will be one camera data store actor per camera feed (providing the user
     * has chosen to store the camera feed's data)
     * @param cameraDataStoreActorName The name of the camera data store actor to create
     * @return The newly created camera data store actor
     */
    private fun createCameraDataStoreActor(cameraDataStoreActorName: String): ActorRef {
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
    private fun createTraceDataStoreActor(traceDeviceLabel: String): ActorRef {
        //Make sure the actor name is truly unique
        val numStoreActorsForDevice = traceDataStoreActors.count { x -> x.value == traceDeviceLabel }
        val traceDataStoreActorName = if (numStoreActorsForDevice > 0) {
            "${traceDeviceLabel}TraceDataStoreActor${numStoreActorsForDevice + 1}"
        } else {
            "${traceDeviceLabel}TraceDataStoreActor1"
        }

        val uniqueActorName = GUIMain.actorService.makeActorName(traceDataStoreActorName)

        val traceDataStoreActor = GUIMain.actorService.createActor(TraceDataStoreActor.props(), uniqueActorName, TraceDataStoreActor::class.java)
        traceDataStoreActors[traceDataStoreActor] = traceDeviceLabel
        return traceDataStoreActor
    }
}