package uk.co.strimm.plugins

import akka.NotUsed
import akka.actor.Actor
import akka.actor.ActorRef
import org.scijava.command.Command
import org.scijava.command.ContextCommand
import org.scijava.plugin.*
import akka.stream.ClosedShape
import akka.stream.javadsl.*
import bibliothek.gui.Dockable
import javafx.scene.paint.Color
import net.imagej.display.DefaultDatasetView
import net.imagej.display.ImageDisplay
import net.imagej.overlay.Overlay
import net.imagej.overlay.RectangleOverlay
import org.scijava.util.ColorRGB
import uk.co.strimm.Acknowledgement
import uk.co.strimm.ExperimentConstants
import uk.co.strimm.TraceData
import uk.co.strimm.STRIMMImage
import uk.co.strimm.actors.CameraActor
import uk.co.strimm.actors.TraceActor
import uk.co.strimm.actors.messages.ask.AskTW
import uk.co.strimm.actors.messages.complete.CompleteStreamingTraceROI
import uk.co.strimm.actors.messages.fail.FailStreamingTraceROI
import uk.co.strimm.actors.messages.start.*
import uk.co.strimm.actors.messages.tell.TellCameraIsAcquiring
import uk.co.strimm.actors.messages.tell.TellDeviceSamplingRate
import uk.co.strimm.actors.messages.tell.TellSetNumDataPoints
import uk.co.strimm.experiment.ExperimentImageSource
import uk.co.strimm.experiment.ROI
import uk.co.strimm.experiment.ROIManager
import uk.co.strimm.gui.GUIMain
import uk.co.strimm.services.AcquisitionMethodService
import uk.co.strimm.streams.ExperimentStream
import java.awt.Dimension
import java.awt.FlowLayout
import java.time.Duration
import java.util.*
import java.util.logging.Level
import javax.swing.*
import javax.swing.plaf.basic.BasicArrowButton
import kotlin.collections.ArrayList

var debugTW = false
/**
 * This class will handle the "Trace from ROI" right click event from overlays on image feeds. Upon right clicking on an
 * image feed and selecting "Trace from ROI", either a new trace window will be created or the trace will be added to
 * an existing trace window. When this is run, it will generate a new akka graph who's source is the broadcast hub
 * of the main experiment graph
 */
@Plugin(type = Command::class,
        menu=[(Menu(label = "Trace from ROI"))],
        menuRoot = "context-ImageDisplay",
        headless = true,
        attrs = [Attr(name="no-legacy")])
class TraceFromROIContextDisplay : ContextCommand() {
    companion object {
        const val DIALOG_HEIGHT = 200
        const val DIALOG_WIDTH = 400
    }

    override fun run() {
        setUpDialog()
    }

    /**
     * This method will create and show a new dialog to specify the relevant settings for the new trace feed
     */
    fun setUpDialog(){
        val dialogLayout = FlowLayout()
        val panel = JPanel()
        panel.layout = dialogLayout
        panel.preferredSize = Dimension(150,50)

        val result = JOptionPane.showConfirmDialog(GUIMain.strimmUIService.strimmFrame, panel, "Confirm Trace from ROI", JOptionPane.OK_CANCEL_OPTION)
        if(result == JOptionPane.OK_OPTION){
            val displaySinkName = GUIMain.actorService.getPertainingDisplaySinkNameFromDockableTitle(GUIMain.strimmUIService.currentFocusedDockableTitle)
            println("Pertaining displaysink name " + displaySinkName)
            var curVal = 0
            var sink : uk.co.strimm.experiment.Sink? = null
            var sinkName = ""
            if (displaySinkName != null){
                //retrieve the Sink for associated with the docking window in focus
                sink = GUIMain.experimentService.experimentStream.expConfig.sinkConfig.sinks.filter{src->src.sinkName == displaySinkName}.first()
                //each sink knows its roiFlowName and then can find the current next colourValue
                println("sink associated found with name " + sink.sinkName)
                println("this is associated with roiFlow " + sink.roiFlowName)
                if (sink.roiFlowName != ""){
                curVal  = GUIMain.strimmUIService.traceFromROICounterPerDisplayROIFlow[sink.roiFlowName] as Int
                println("current number of traces with this roiFlow " + curVal.toString())
                //the overlayService maintains an array of overlays
                var ixLatestOverlay = GUIMain.overlayService.overlays.size - 1
                if (ixLatestOverlay < 0) ixLatestOverlay = 0
                var sel : Overlay?= GUIMain.overlayService.overlays[ixLatestOverlay ]
                    //println("Total  number of applied ROIs " + GUIMain.strimm]
                println("Total  number of applied ROIs " + GUIMain.strimmUIService.traceFromROICounter)
                println("Total number reported by the overlayService " + GUIMain.overlayService.overlays.size)
                println("Get the last attached Overlay from the overlayService")
                // can see a problem here with multiple traces this has not been changes
                if (curVal < GUIMain.roiColours.size) {
                    var col : Color = GUIMain.roiColours[curVal]
                    sel!!.fillColor = ColorRGB((255.0 * col.red).toInt(), (255.0 * col.green).toInt(), (255.0 * col.blue).toInt())
                   // sel!!.lineColor = ColorRGB(255,0,0)   //these clash at the moment
                }
                else {
                    sel!!.fillColor = ColorRGB(100, 100, 100)
                  // sel!!.lineColor = ColorRGB(100, 100, 100)
                }

                GUIMain.actorService.routedRoiOverlays[sel] = sink.roiFlowName
                var ddd = GUIMain.strimmUIService.traceColourByFlowAndOverlay[sink.roiFlowName] as ArrayList<Pair<Overlay, Int>>
                ddd.add(Pair(sel, curVal))
                GUIMain.strimmUIService.traceFromROICounterPerDisplayROIFlow[sink.roiFlowName] = curVal + 1
                }
                else {
                    println("No trace plot associated with this Display")
                }
            }

        }
    }

    /**
     * This method takes the selections from the user and carries out the necessary steps to display the trace from ROI
     * @param selectedFlowsModel A model containing the list of elements that correspond to the flows the user has chosen
     * @param windowOptionList The combobox showing which window to use to display
     * @param currentStream The currently loaded and running experiment stream
     * @param isStore The option of if the trace from ROI data should be stored also
     */
    fun createTraceFeedForROI(selectedFlowsModel : DefaultListModel<String>,
                              windowOptionList : JComboBox<String>,
                              currentStream : ExperimentStream,
                              isStore : Boolean){
        /*
            Note - we can't get the overlay from the active image display via imageDisplayService.activeImageDisplay.
            This is because activeImageDisplay is the most recently active image display. So when there is more than
            one camera feed this will become unreliable. Instead we can use the currently focused dockable window
            to get the display name
         */

        //Get the ROI that has been right click -> Trace from ROI
        var selectedROI : Overlay? = null
        try {
            //overlayService maintains a list of overlays, the last one must be the one selected
            selectedROI = GUIMain.overlayService.overlays[GUIMain.strimmUIService.traceFromROICounter]
            GUIMain.strimmUIService.traceFromROICounter++
        }
        catch(ex : IndexOutOfBoundsException){
            GUIMain.loggerService.log(Level.SEVERE, "traceFromROICounter out of bounds of overlayService.overlays list")
        }

        //Get the pertaining camera and camera device label
        //
        val cameraActor = GUIMain.actorService.getPertainingCameraActorFromDockableTitle(GUIMain.strimmUIService.currentFocusedDockableTitle)

//the camera actor has a device label which will be used to id the source
        //this is used to go upstream from the sink
        val cameraDeviceLabel = GUIMain.actorService.getPertainingCameraDeviceLabelForActor(cameraActor!!)
//
        //Get the corresponding source
        selectedROI!!.name = cameraDeviceLabel + "TraceROI" + Random().nextInt(1000) //TODO magic string
        var source = ExperimentImageSource("", "")
        try {
            source = currentStream.experimentImageSources.first { x -> cameraActor.path().name().contains(x.deviceLabel) }
        }
        catch(ex : Exception){
            GUIMain.loggerService.log(Level.SEVERE, "Could not find image source for camera actor ${cameraActor.path().name()}")
        }
//we will now add an roi to this source how we do this depends on whether we need to add another tracedisplay
        //or add it to an existing one also whether we need to save the trace
        val traceActor : ActorRef?

        if(windowOptionList.selectedItem.toString() == ExperimentConstants.ConfigurationProperties.NEW_WINDOW){
            //New window means new actor and new trace window plugin

            val pluginCreation = currentStream.createTracePluginWithActor(cameraDeviceLabel!!)
            //first is ActorRef and second is DockableWindowPlugin
            pluginCreation.second?.dock(GUIMain.strimmUIService.dockableControl, GUIMain.strimmUIService.strimmFrame)
            //traceActor is part of the pluginCreation  strange to search for it
            traceActor = currentStream.traceActors.filter { x -> x.key.path().name() == pluginCreation.first!!.path().name()}.keys.first()

            //now have the traceActor for the new plot as well as the plugin which will have the tracewindow
            //the trace actor is now set to receive messages from the akka graph
        }
        else {
            //Existing window means find the trace actor that already exists
            selectedROI.name = GUIMain.experimentService.makeUniqueROIName(cameraDeviceLabel!!)

            val selectedActorName = windowOptionList.selectedItem.toString().toLowerCase()
            val allTraceActors = GUIMain.actorService.getActorsOfType(TraceActor::class.java)
            //finds the traceActor via the name supplied in the dialog box

            traceActor = allTraceActors.first{ x -> GUIMain.actorService.getActorPrettyName(x.path().name()).toLowerCase() == selectedActorName }
        }
    //either way either have a traceactor which is already in the akka graph or one which is fully connected
        //to a window but is not in the graph


        //Due to image sources being broadcast hubs, we can dynamically create another stream from the existing
        //sinks. See populateSources() in ExperimentStream for where the broadcast hub is specified


        //so as expected we have found the source and then add another bit of a graph to it.
        //so does it suspend the current akka processing for the change and then resume?
        if(source.deviceLabel.isNotEmpty()){

            //this manages this
            runTraceROIGraph(traceActor, currentStream, selectedROI, source, isStore,
                cameraActor, windowOptionList.selectedItem.toString())
        }
        else{
            GUIMain.loggerService.log(Level.SEVERE, "Could not find the source node pertaining to the roi display")
        }
    }

    /**
     * Once the trace from ROI settings have been specified, create a simple graph to allow for it's display. This graph
     * is not the main experiment stream graph
     * @param traceActor The new trace actor that will serve the TraceWindowPlugin
     * @param currentStream The currently loaded (and running) experiment stream
     * @param selectedROI The selected ROI (ImageJ overlay)
     * @param source The source object of the camera source
     * @param isStore The option of if the trace from ROI data should be stored also
     * @param cameraActor The camera actor pertaining to the image feed where the ROI has been drawn
     */
    private fun runTraceROIGraph(traceActor : ActorRef,
                                 currentStream: ExperimentStream,
                                 selectedROI : Overlay,
                                 source : ExperimentImageSource,
                                 isStore: Boolean,
                                 cameraActor: ActorRef, windowOption : String){
        val baseFlowName = source.imgSourceName + "TraceROIFlow"
        val flowName = GUIMain.experimentService.makeUniqueFlowName(baseFlowName).second
        //Even though the experiment stream will be rebuilt once the user is done specifying ROIs, we will still use
        //the routed ROI list for reference
        GUIMain.actorService.routedRoiList[selectedROI] = Pair(cameraActor, flowName)

        val averageROIAcquisitionMethod = GUIMain.acquisitionMethodService.getAcquisitionMethod(ExperimentConstants.Acquisition.AVERAGE_ROI_METHOD_NAME) as AcquisitionMethodService.TraceMethod

        //so you could have another statistic
        val akkaFlow = Flow.of(STRIMMImage::class.java)
            .map { image -> averageROIAcquisitionMethod.runMethod(image, flowName) }
            .groupedWithin(ExperimentConstants.ConfigurationProperties.TRACE_GROUPING_AMOUNT,
                Duration.ofMillis(ExperimentConstants.ConfigurationProperties.TRACE_GROUPING_DURATION_MS))
            .async()

        val akkaSink: Sink<List<ArrayList<TraceData>>, NotUsed> = Sink.actorRefWithAck(traceActor, StartStreamingTraceROI(),
                Acknowledgement.INSTANCE, CompleteStreamingTraceROI()) { ex -> FailStreamingTraceROI(ex) }

        val graph = GraphDSL.create { builder ->
            val sourceShape = builder.add(source.roiSource)
            val flowShape = builder.add(akkaFlow)
            val sinkShape = builder.add(akkaSink)
            builder.from(sourceShape).via(flowShape).to(sinkShape)

            ClosedShape.getInstance()
        }

        traceActor.tell(TellDeviceSamplingRate(40.0), ActorRef.noSender())//TODO hardcoded
        traceActor.tell(TellSetNumDataPoints(), ActorRef.noSender())
        RunnableGraph.fromGraph(graph).run(GUIMain.actorService.materializer)
//        GUIMain.actorService.cameraActorDisplays.forEach { name, actorRef ->
//            actorRef.tell(TellCameraIsAcquiring(true), ActorRef.noSender())
//        }
//        GUIMain.experimentService.calculateNumberOfDataPointsFromInterval(selectedROI.name, source.intervalMs)
        GUIMain.experimentService.calculateNumberOfDataPointsFromFrequency(selectedROI.name, 10.0)//TODO hardcoded
        createAndAddToNewConfig(source, isStore, selectedROI, currentStream, windowOption, traceActor)
    }

    /**
     * Based on the user's specifications for the trace from ROI, store these specifications for use in creating a new
     * config
     * @param sourceNode The experiment image source that the trace ROI is reading from
     * @param isStore The flag to store or not store the trace ROI data in addition to displaying it
     * @param selectedROI The overlay (ROI) object
     * @param currentStream The currently loaded and running experiment stream
     * @param windowOption The destination trace feed of the new trace ROI (new window or existing)
     * @param traceActor The trace actor relating to the target trace feed display
     */
    fun createAndAddToNewConfig(sourceNode : ExperimentImageSource, isStore : Boolean, selectedROI : Overlay, currentStream: ExperimentStream, windowOption : String, traceActor: ActorRef){
        //Create a new config first by copying the existing one
        GUIMain.experimentService.createNewConfigFromExisting()

        //Important to do this so we keep track of any new trace ROIs that have been created and what devices they are
        //associated with
        currentStream.newTraceROIActors[traceActor] = sourceNode.deviceLabel

        //Specify new flows
        val flowToAdd = uk.co.strimm.experiment.Flow()
        val baseFlowName = sourceNode.imgSourceName + "TraceROIFlow"
        val flowInfo = GUIMain.experimentService.makeUniqueFlowName(baseFlowName)
        flowToAdd.flowName = flowInfo.second
        flowToAdd.roiNumber = flowInfo.first
        flowToAdd.inputNames = arrayListOf(sourceNode.imgSourceName)
        flowToAdd.inputType = ExperimentConstants.ConfigurationProperties.IMAGE_INPUT_TYPE
        flowToAdd.outputType = ExperimentConstants.ConfigurationProperties.TRACE_OUTPUT_TYPE
        flowToAdd.description = "Trace from ROI from ${sourceNode.imgSourceName}" //TODO is it ok for this to be hardcoded
        GUIMain.experimentService.addFlowsToNewConfig(listOf(flowToAdd))

        //Specify new sinks
        val displaySinkToAdd = uk.co.strimm.experiment.Sink()
        val baseDisplaySinkName = sourceNode.imgSourceName + "TraceROIDisplay"
        displaySinkToAdd.sinkName = GUIMain.experimentService.makeUniqueSinkName(baseDisplaySinkName)
        displaySinkToAdd.inputNames = arrayListOf(flowToAdd.flowName)
        displaySinkToAdd.displayOrStore = ExperimentConstants.ConfigurationProperties.DISPLAY
        displaySinkToAdd.outputType = ExperimentConstants.ConfigurationProperties.TRACE_OUTPUT_TYPE
        displaySinkToAdd.primaryDevice = sourceNode.deviceLabel
        displaySinkToAdd.actorPrettyName = GUIMain.actorService.getActorPrettyName(traceActor.path().name())

        val isNewTraceFeed = isAddingToNewTraceFeed(traceActor, currentStream)

        if(isStore) {
            val storeSinkToAdd = uk.co.strimm.experiment.Sink()
            val baseStoreSinkName = sourceNode.imgSourceName + "TraceROIStore"
            storeSinkToAdd.sinkName = GUIMain.experimentService.makeUniqueSinkName(baseStoreSinkName)
            storeSinkToAdd.inputNames = arrayListOf(flowToAdd.flowName)
            storeSinkToAdd.displayOrStore = ExperimentConstants.ConfigurationProperties.STORE
            storeSinkToAdd.outputType = ExperimentConstants.ConfigurationProperties.TRACE_OUTPUT_TYPE
            storeSinkToAdd.primaryDevice = sourceNode.deviceLabel
            storeSinkToAdd.actorPrettyName = GUIMain.actorService.getActorPrettyName(traceActor.path().name())

            GUIMain.experimentService.addSinksToNewConfig(listOf(displaySinkToAdd, storeSinkToAdd), windowOption, isNewTraceFeed)
        }
        else{
            GUIMain.experimentService.addSinksToNewConfig(listOf(displaySinkToAdd), windowOption, isNewTraceFeed)
        }

        //As we are closing down and loading up a modified experiment, create an ROI object so the ROI drawn can be recreated
        val roiToAdd = ROIManager.createROIObjectFromOverlay(selectedROI, sourceNode.imgSourceName, sourceNode.deviceLabel, flowToAdd.flowName)

        GUIMain.overlayService.removeOverlay(selectedROI)
        GUIMain.experimentService.addROIsToNewConfig(listOf(roiToAdd))
    }

    /**
     * Method used to determine if the trace from ROI is going to any trace feed that has been created in this trace from ROI context
     * @param traceActor The trace actor for the target trace feed
     * @param currentStream The current experiment stream
     * @return If the target feed has been created in a trace from ROI context
     */
    fun isAddingToNewTraceFeed(traceActor : ActorRef, currentStream: ExperimentStream) : Boolean{
        return traceActor.path().name() in currentStream.newTraceROIActors.map { x -> x.key.path().name() }
    }

    /**
     * When specifying a new trace from ROI, we specify what flows the trace from ROI source can be connected to.
     * This arrow button will allow the user to select which flows to use
     * @param arrowButton The arrow button
     * @param nodeList The list of available flows
     * @param selectedFlowsModel The list
     */
    private fun addArrowButtonListener(arrowButton: BasicArrowButton, nodeList : JList<String>, selectedFlowsModel : DefaultListModel<String>){
        arrowButton.addActionListener {
            //TODO specify enable/disable logic here i.e. what nodes can go to what other nodes
            val selectedNode = nodeList.selectedValue
            selectedFlowsModel.addElement(selectedNode)
        }
    }
}

