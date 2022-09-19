package uk.co.strimm.services

import akka.NotUsed
import akka.actor.ActorRef
import akka.stream.KillSwitches
import akka.stream.javadsl.RunnableGraph
import com.google.gson.GsonBuilder
import mmcorej.CMMCore
import mmcorej.StrVector
import net.imagej.ImageJService
import net.imagej.overlay.Overlay
import org.apache.commons.io.FileUtils
import org.scijava.plugin.Plugin
import org.scijava.service.AbstractService
import org.scijava.service.Service
import uk.co.strimm.ExperimentConstants
import uk.co.strimm.Paths
import uk.co.strimm.experiment.*
import uk.co.strimm.gui.CameraWindowPlugin
import uk.co.strimm.gui.GUIMain
import uk.co.strimm.gui.TraceWindowPlugin
import uk.co.strimm.streams.ExperimentStream
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.lang.Thread.sleep
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.logging.Level
import javax.swing.JOptionPane

var debugTW = false
@Plugin(type = Service::class)
class ExperimentService  : AbstractService(), ImageJService {
    val gson = GsonBuilder().setPrettyPrinting().create()
    var expConfig = ExperimentConfiguration()
    var loadedConfigurationStream : RunnableGraph<NotUsed>? = null
    lateinit var experimentStream : ExperimentStream
    var configWithTraceROIs : ExperimentConfiguration? = null
    lateinit var loadedConfigFile : File
    private var flowsForTraceROIs = arrayListOf<Flow>()
    private var sinksForTraceROIs = arrayListOf<Sink>()
    private var roisForTraceROIs = arrayListOf<ROI>()
    var numNewTraceROIFeeds = 0
    var deviceDatapointNumbers = hashMapOf<String, Int>()

    //This is a flag used to STRIMM knows when the program has gone from preview -> live mode
    //This is then used if the users chooses to abort the acquisition
    var hasRun = false

    fun createExperimentStream(loadCameraConfig : Boolean){
        println("#####ExperimentService::createExperimentStream(loadCameraConfig : Boolean)")
        experimentStream = ExperimentStream(expConfig, loadCameraConfig)
        //
        //TO DO we need a better method of initialising the protocol service
        //The protocolService morphed into a general dll - test.dll which covers a lot of
        //non JNI tasks such as talking to NIDAQ and also Windows API for services
        //better to very clearly split them up.
        //it also activates the COM port for communication with the Arduino
        GUIMain.protocolService.Init(expConfig)   // 2_7_21__________________________

    }

    fun addCamerasToConfig(){
        println("####ExperimentService::addCamerasToConfig()")
        for (src in expConfig.sourceConfig.sources){
            if (src.sourceCfg != ""){
                src.camera =  GUIMain.acquisitionMethodService.GetCamera(src.sourceCfg)
            }
        }
        GUIMain.loggerService.log(Level.INFO, "Added camera sources to config")
    }

    fun convertGsonToConfig(configFile : File) : Boolean{
        println("####convertGsonToConfig(configFile : File) : Boolean")
        return try {
            expConfig = gson.fromJson(FileReader(configFile), ExperimentConfiguration::class.java)
            configWithTraceROIs = expConfig  //TW 5_10_21 fixed bug where when you load a new json and then go to live it runs the previous json live
            expConfig.sourceConfig.sources[0].samplingFrequencyHz = 30.0 // TODO is this needed?
            loadedConfigFile = configFile
            addCamerasToConfig()
            GUIMain.experimentService.expConfig

            true
        }
        catch(ex : Exception){
            GUIMain.loggerService.log(Level.SEVERE, "Failed to load experiment configuration ${configFile.absolutePath}, check file is present and syntax is correct")
            GUIMain.loggerService.log(Level.SEVERE, "Error message: ${ex.message}")
            false
        }
    }

    fun getChannelForSource(sourceChannelName : String) : Channel? {
        for(channel in expConfig.hardwareDevices.channelConfig.channels){
            if(sourceChannelName == channel.channelName){
                return channel
            }
        }

        return null
    }
    fun getSamplingFrequencyOfChannel(sourceChannelName : String) : Double{
        var sampFreq = 1.0
        for(channel in expConfig.hardwareDevices.channelConfig.channels){
            if(channel.channelName == sourceChannelName){
                val fullSampFreq = getSampleRateForTypeOfChannel(channel.type)
                sampFreq = fullSampFreq/channel.clockDiv //TODO consider rounding just in case?
                break
            }
        }

        return sampFreq
    }
    fun getSampleRateForTypeOfChannel(channelType : Int) : Double{
        println("####getSampleRateForTypeOfChannel(channelType : Int) : Double")
        return when(channelType){
            ExperimentConstants.ConfigurationProperties.ANALOGUE_IN_TYPE -> { return expConfig.hardwareDevices.postInitProperties.analogueInputSampleRate }
            ExperimentConstants.ConfigurationProperties.ANALOGUE_OUT_TYPE -> { return expConfig.hardwareDevices.postInitProperties.analogueOutputSampleRate }
            ExperimentConstants.ConfigurationProperties.DIGITAL_IN_TYPE -> { return expConfig.hardwareDevices.postInitProperties.digitalInputSampleRate }
            ExperimentConstants.ConfigurationProperties.DIGITAL_OUT_TYPE -> { return expConfig.hardwareDevices.postInitProperties.digitalOutputSampleRate }
            else -> 1.0
        }
    }

    fun getBlockSizeOfChannel(sourceChannelName : String) : Int{
        var blockSize = 1
        for(channel in expConfig.hardwareDevices.channelConfig.channels){
            if(channel.channelName == sourceChannelName){
                when(channel.type){
                    ExperimentConstants.ConfigurationProperties.ANALOGUE_IN_TYPE -> { blockSize = expConfig.hardwareDevices.postInitProperties.analogueInputBlockSize }
                    ExperimentConstants.ConfigurationProperties.ANALOGUE_OUT_TYPE -> { blockSize = expConfig.hardwareDevices.postInitProperties.analogueOutputBlockSize }
                    ExperimentConstants.ConfigurationProperties.DIGITAL_IN_TYPE -> { blockSize = expConfig.hardwareDevices.postInitProperties.digitalInputBlockSize }
                    ExperimentConstants.ConfigurationProperties.DIGITAL_OUT_TYPE -> { blockSize = expConfig.hardwareDevices.postInitProperties.digitalOutputBlockSize }
                }
                break
            }
        }

        return blockSize
    }

    fun createStreamGraph() : Boolean{
        println("####createStreamGraph() : Boolean")
        val streamGraph = experimentStream.createStream(expConfig)

        return if(streamGraph != null) {
            loadedConfigurationStream = streamGraph
            true
        }
        else{
            GUIMain.loggerService.log(Level.SEVERE, "Failed to create Experiment stream")
            false
        }
    }

    /**
     * Begin streaming of all data based on specifications
     */
    fun runStream(liveAcquisition : Boolean){
        println("####runStream(liveAcquisition : Boolean")
        GUIMain.acquisitionMethodService.bAquisitionProceed = true
        println("*********************proceed " + GUIMain.acquisitionMethodService.bAquisitionProceed)

        experimentStream.runStream(liveAcquisition)

        if (liveAcquisition) {
            GUIMain.strimmUIService.state = UIstate.LIVE
        } else {
            GUIMain.strimmUIService.state = UIstate.PREVIEW
        }

    }

    fun stopStream() : Boolean {
        println("####stopStream() : Boolean")
        return try {
            //wait until all sources are not busy
            GUIMain.acquisitionMethodService.bAquisitionProceed = false
            println("*********************proceed " + GUIMain.acquisitionMethodService.bAquisitionProceed)

            //causes a vector crash in test.dll
//            var bBusy = true
//            while (bBusy) {
//                bBusy = false
//                for (src in expConfig.sourceConfig.sources) {
//                    if (src.isBusy) bBusy = true;
//                }
//            }

            GUIMain.loggerService.log(Level.INFO, "Shutting down Akka stream")
            GUIMain.actorService.sharedKillSwitch.shutdown()
            GUIMain.actorService.sharedKillSwitch = KillSwitches.shared("strimm-shared-killswitch")
            GUIMain.experimentService.experimentStream.isRunning = false
            GUIMain.experimentService.expConfig.sourceConfig.sources.forEach { x -> x.camera?.core?.stopSequenceAcquisition()}
            println("shutdowncameramap*****************")
            GUIMain.protocolService.ShutdownCameraMap()
            GUIMain.protocolService.bGlobalSourceStartTrigger = false
            GUIMain.expStartStopButton.isEnabled = false
            true
        }
        catch(ex : Exception){
            GUIMain.loggerService.log(Level.SEVERE, "Error in stopping Akka stream")
            GUIMain.loggerService.log(Level.SEVERE, ex.stackTrace)
            false
        }
    }

    fun restartStreamInPreviewMode(){
        println("restartStreamInPreviewMode()")
        GUIMain.acquisitionMethodService.bCamerasAcquire = false
        //21_03_22_ single shot for now
//        stopAndLoadNewExperiment(true, false)
//
//        GUIMain.strimmUIService.state = UIstate.PREVIEW
//        GUIMain.strimmUIService.enableStartStopButton()
//
//        hasRun = false
    }

    /**
     * Exit from Preview Mode
     */
    fun exitPreview() {
        println("####ExperimentService::exitPreview()")
        //Stop the current stream's actors
        //This will send a kill message to each actor, thus effectively stopping the whole experiment stream

        //TW 3_10_21   the next line is needed to activate the kill-switch on the akka graph
        //the previous lines destroy the display machinary but not the graph
        //without the next line the graph will operate in the background and you will notice a general
        //slowing down of strimm until it crashes with too many graphs operating in tbe background.
        //stop the cameras from being able to produce any more frames
        GUIMain.acquisitionMethodService.bCamerasAcquire = false
        val stopSuccess = GUIMain.experimentService.stopStream()


        //sleep(5000)


        val mode = GUIMain.experimentService.expConfig.experimentMode
        if (mode == "Preview") {
            experimentStream.stopDisplayingData()
        }
        else {
            experimentStream.stopDisplayingData()
            experimentStream.stopStoringData()
        }

        //Close and remove the dockable window plugins
        val cameraWindowPlugins = GUIMain.dockableWindowPluginService.getPluginsOfType(CameraWindowPlugin::class.java)
       cameraWindowPlugins.forEach { x -> x.value.close() }

        val traceWindowPlugins = GUIMain.dockableWindowPluginService.getPluginsOfType(TraceWindowPlugin::class.java)
        traceWindowPlugins.forEach { x -> x.value.close() }

    }

    /**
     * Create a new experiment config representing an experiment but with any trace ROIs. This does so by coping the
     * previous config first
     */
    fun createNewConfigFromExisting(){
        println("####ExperimentService::createNewConfigFromExisting()")
        if(configWithTraceROIs == null) {
            configWithTraceROIs = expConfig
        }
    }

    /**
     * Add flow objects to list for use in creating new experiment config
     * @param flowList Any new flow objects to add
     */
    fun addFlowsToNewConfig(flowList : List<Flow>){
        println("####addFlowsToNewConfig(flowList : List<Flow>)")
        flowsForTraceROIs.addAll(flowList)
        GUIMain.loggerService.log(Level.INFO, "Adding trace from ROI flows to new experiment config")
    }

    /**
     * Add sink objects to list for use in creating new experiment config
     * @param sinkList Any new sink objects to add
     */
    fun addSinksToNewConfig(sinkList : List<Sink>, windowOption : String, isNewTraceFeed: Boolean){
        println("####addSinksToNewConfig(sinkList : List<Sink>, windowOption : String, isNewTraceFeed: Boolean)")
        if(windowOption == ExperimentConstants.ConfigurationProperties.NEW_WINDOW){
            sinksForTraceROIs.addAll(sinkList)
        }
        else{
            addInputNameToExistingSink(sinkList, windowOption, isNewTraceFeed)
        }
        GUIMain.loggerService.log(Level.INFO, "Adding trace from ROI sinks to new experiment config")
    }

    /**
     * This method will add information for newly added sinks to the new config (the new config will be used after the
     * user has finished specifying ROIs). It will determine what sink's input names to add to.
     * @param sinkList The list of newly created sinks
     * @param traceActorName The trace actor related to the new sinks
     * @param isNewTraceFeed Flag to say if the trace ROI is going to an existing trace feed sink or a newly created
     * trace feed sink
     */
    fun addInputNameToExistingSink(sinkList: List<Sink>, traceActorName : String, isNewTraceFeed : Boolean){
        println("####addInputNameToExistingSink(sinkList: List<Sink>, traceActorName : String, isNewTraceFeed : Boolean)")
        if(isNewTraceFeed) {
            //This block will be used if the sink (trace from roi) is going to an existing window that has been created
            //from trace from ROI specification
            for(newTraceActor in experimentStream.newTraceROIActors) {
                val actorPrettyName = GUIMain.actorService.getActorPrettyName(newTraceActor.key.path().name())
                if(actorPrettyName.toLowerCase() == traceActorName.toLowerCase()) {
                    val sinksForActor = sinksForTraceROIs.filter { x ->
                        x.primaryDevice.toLowerCase() == newTraceActor.value.toLowerCase() &&
                        x.outputType == ExperimentConstants.ConfigurationProperties.TRACE_OUTPUT_TYPE &&
                        x.actorPrettyName == actorPrettyName}

                    sinksForActor.forEach {
                        val additionalInputsForSink = sinkList.map { x -> x.inputNames }.flatten().distinct()
                        it.inputNames.addAll(additionalInputsForSink)
                    }
                }
            }
        }
        else{
            //This block will be used if the sink (trace from roi) is going to an existing trace window that existed
            //before any traces from ROIs were specified
            for (traceActor in experimentStream.traceActors) {
                val actorPrettyName = GUIMain.actorService.getActorPrettyName(traceActor.key.path().name())
                if (actorPrettyName.toLowerCase() == traceActorName.toLowerCase()) {
                    val sinksForActor = configWithTraceROIs!!.sinkConfig.sinks.filter { x ->
                        x.primaryDevice.toLowerCase() == traceActor.value.toLowerCase() &&
                        x.outputType == ExperimentConstants.ConfigurationProperties.TRACE_OUTPUT_TYPE &&
                        x.actorPrettyName == actorPrettyName}

                    sinksForActor.forEach {
                        val additionalInputsForSink = sinkList.map { x -> x.inputNames }.flatten().distinct()
                        it.inputNames.addAll(additionalInputsForSink)
                    }
                }
            }
        }
    }

    /**
     * Add ROI objects to list for use in creating new experiment config
     * @param roiList Any new ROI objects to add
     */
    fun addROIsToNewConfig(roiList : List<ROI>){
        println("####addROIsToNewConfig(roiList : List<ROI>)")
        //This is to prevent duplicate ROIs. This will happen when going from live mode back to preview mode
        for (roi in roiList){
            if(roi.ROIName !in roisForTraceROIs.map{ x -> x.ROIName }){
                roisForTraceROIs.add(roi)
            }
        }

        GUIMain.loggerService.log(Level.INFO, "Adding trace ROIs to new experiment config")
        numNewTraceROIFeeds++
    }

    fun addSizesToNewConfig(){
        println("####addSizesToNewConfig()")
        for(resizeSet in GUIMain.strimmUIService.cameraViewSizeList){
            val deviceSources = configWithTraceROIs!!.sourceConfig.sources.filter { x -> x.deviceLabel == resizeSet.key }
            for(deviceSource in deviceSources){
                deviceSource.x = resizeSet.value.x!!.toDouble()
                deviceSource.y = resizeSet.value.y!!.toDouble()
                deviceSource.w = resizeSet.value.w!!.toDouble()
                deviceSource.h = resizeSet.value.h!!.toDouble()
            }
        }
    }

    /**
     * From the new stream components that have been specified by the user, create a new experiment JSON config and
     * write this to file.
     * @return The newly created experiment config file
     */
    fun writeNewConfig(isLive : Boolean) : File {
        println("####writeNewConfig(isLive : Boolean) : File")
        val liveString = "_Live"
        val previewString = "_Preview"
        val pathAndName: String

        if (isLive) {
            configWithTraceROIs!!.experimentMode = "Live"
            configWithTraceROIs!!.ROIAdHoc = "False"
            configWithTraceROIs!!.experimentConfigurationName.replace(previewString, "")
            val fileName = loadedConfigFile.name.replace(previewString, "")
            pathAndName = loadedConfigFile.canonicalPath.replace(loadedConfigFile.name, fileName.replace(".json", "") + liveString + ".json")
        }
        else{
            configWithTraceROIs!!.experimentMode = "Preview"
            configWithTraceROIs!!.ROIAdHoc = "True"

            //We know that if this method is called and is going to preview mode, it has come from live mode
            configWithTraceROIs!!.experimentConfigurationName.replace(liveString, "")
            val fileName = loadedConfigFile.name.replace(liveString, "")
            pathAndName = loadedConfigFile.canonicalPath.replace(loadedConfigFile.name, fileName.replace(".json", "") + previewString + ".json")
        }

        val newFile = File(pathAndName)

        //Populate new flows
        if(configWithTraceROIs!!.flowConfig.flows.isEmpty()) {
            configWithTraceROIs!!.flowConfig.flows = flowsForTraceROIs
        }
        else{
            //This will remove duplicates. This usually occurs when going from live mode to preview mode
            for (flow in flowsForTraceROIs) {
                if (flow.flowName !in configWithTraceROIs!!.flowConfig.flows.map { x -> x.flowName }) {
                    configWithTraceROIs!!.flowConfig.flows.add(flow)
                }
            }
        }

        //Populate new sinks
        if(configWithTraceROIs!!.sinkConfig.sinks.isEmpty()) {
            configWithTraceROIs!!.sinkConfig.sinks = sinksForTraceROIs
        }
        else{
            //This will remove duplicates. This usually occurs when going from live mode to preview mode
            for (sink in sinksForTraceROIs){
                if (sink.sinkName !in configWithTraceROIs!!.sinkConfig.sinks.map { x -> x.sinkName}){
                    configWithTraceROIs!!.sinkConfig.sinks.add(sink)
                }
            }
        }

        //Populate new ROIs
        if(configWithTraceROIs!!.roiConfig.rois.isEmpty()) {
            configWithTraceROIs!!.roiConfig.rois = roisForTraceROIs
        }
        else{
            //This will remove duplicates. This usually occurs when going from live mode to preview mode
            for (roi in roisForTraceROIs){
                if (roi.ROIName !in configWithTraceROIs!!.roiConfig.rois.map{ x -> x.ROIName}){
                    configWithTraceROIs!!.roiConfig.rois.add(roi)
                }
            }
        }
        for (src in configWithTraceROIs!!.sourceConfig.sources){
            if (src.camera != null){
                src.camera = null
            }
        }

        //Account for any resizes that have been done
        addSizesToNewConfig()

        if(configWithTraceROIs != null) {
            try {
                val writer = FileWriter(newFile)
                GUIMain.loggerService.log(Level.INFO, "Writing experiment config with trace ROI info")
                gson.toJson(configWithTraceROIs!!, writer)
                writer.flush()
                writer.close()
            }
            catch(ex : Exception){
                GUIMain.loggerService.log(Level.SEVERE, "Error in writing new experiment config to file. Error message: ${ex.message}")
                GUIMain.loggerService.log(Level.SEVERE, ex.stackTrace)
            }
        }

        return newFile
    }

    /**
     * Method to make sure each ROI has a unique name
     * @param sourceName The name of the source the ROI is related to
     * @return A unique trace ROI name
     */
    fun makeUniqueROIName(sourceName : String) : String{
        println("####makeUniqueROIName(sourceName : String) : String")
        val roisForSource = roisForTraceROIs.filter { x -> x.ROIName.toLowerCase().contains(sourceName.toLowerCase()) }
        return sourceName + "TraceROI" + (roisForSource.size+1).toString()
    }

    /**
     * Method to make sure each flow created from a trace from ROI has a unique name
     * @param flowName The name of the flow the ROI is related to
     * @return A unique flow name
     */
    fun makeUniqueFlowName(flowName : String) : Pair<Int,String>{
        println("####makeUniqueFlowName(flowName : String) : Pair<Int,String>")
        val flows = flowsForTraceROIs.filter { x -> x.flowName.toLowerCase().contains(flowName.toLowerCase()) }
        return Pair((flows.size+1),flowName + (flows.size+1).toString())
    }

    /**
     * Method to make sure each sink relating to a trace from ROI has a unique name
     * @param sinkName The name of the sink the ROI is related to
     * @return A unique sink name
     */
    fun makeUniqueSinkName(sinkName : String) : String{
        println("makeUniqueSinkName(sinkName : String) : String")
        val sinks = sinksForTraceROIs.filter { x -> x.sinkName.toLowerCase().contains(sinkName.toLowerCase()) }
        return sinkName + (sinks.size+1).toString()
    }

    /**
     * This method is run when the trace from ROI specification is complete. This will write the newly modified config
     * (which includes any trace from ROI components), stop the current stream (stream, actors and windows), then go
     * through the experiment loading and running process again starting at the loading of the JSON config
     */
    fun stopAndLoadNewExperiment(writeNewConfig : Boolean, isLive : Boolean){
        println("####ExperimentService::stopAndLoadNewExperiment(writeNewConfig : Boolean)")
        GUIMain.loggerService.log(Level.INFO, "Loading new experiment with possible trace roi modifications")

        println("configFile: "  + loadedConfigFile.absolutePath)

        // close any running Camera + Trace actors and stop MMCameraDevice sequences
        exitPreview()

        if(writeNewConfig) {
            //Write the new config with the new trace from ROI changes
            val newFile = GUIMain.experimentService.writeNewConfig(isLive)
            //Load the new config
            convertGsonToConfig(newFile)
        }

        //Create the stream graph

        GUIMain.protocolService.Shutdown()
        GUIMain.actorService.routedRoiList = hashMapOf<Overlay, Pair<ActorRef, String>>()
        roisForTraceROIs = arrayListOf<ROI>()
        GUIMain.actorService.routedRoiOverlays = hashMapOf<Overlay, String>()

        GUIMain.loggerService.log(Level.INFO, "Loading experiment config from JSON")
        expConfig = gson.fromJson(FileReader(loadedConfigFile), ExperimentConfiguration::class.java)
        addCamerasToConfig()

        createExperimentStream(false)
        val createSuccess = createStreamGraph()

        //Run the stream graph
        runStream(true)
    }

    fun calculateNumberOfDataPointsFromInterval(deviceName : String, interval : Double){
        println("####calculateNumberOfDataPointsFromInterval(deviceName : String, interval : Double)")
        val numDataPoints = BigDecimal(experimentStream.durationMs/interval).setScale(1, RoundingMode.FLOOR).toInt()
        deviceDatapointNumbers[deviceName] = numDataPoints
        GUIMain.loggerService.log(Level.INFO, "Device $deviceName should have $numDataPoints data points")
    }

    fun calculateNumberOfDataPointsFromFrequency(deviceName : String, samplingFrequencyHz : Double){
        println("####calculateNumberOfDataPointsFromFrequency(deviceName : String, samplingFrequencyHz : Double)")
        val durationInSeconds = experimentStream.durationMs.toDouble()/1000.0
        val numDataPoints = BigDecimal(durationInSeconds*samplingFrequencyHz).setScale(1, RoundingMode.FLOOR).toInt()
        deviceDatapointNumbers[deviceName] = numDataPoints
        GUIMain.loggerService.log(Level.INFO, "Device $deviceName should have $numDataPoints data points")
    }

    fun loadPreviousExperiment(selectedDirectory : File){
//        val tifFiles = FileUtils.listFiles(selectedDirectory,arrayOf("tif"),true)
//        var tifFile : File? = null
//        if(tifFiles.isNotEmpty()){
//            if(tifFiles.size > 1){
//                JOptionPane.showMessageDialog(GUIMain.strimmUIService.strimmFrame, "Found multiple .tif files, choosing first one alphabetically", "Multiple .tif files", JOptionPane.WARNING_MESSAGE)
//            }
//
//            tifFile = tifFiles.first()
//        }
//
//        var cameraMetadataFile : File? = null
//        var traceDataFile : File? = null
//
//        val csvFiles = FileUtils.listFiles(selectedDirectory,arrayOf("csv"),true)
//        for(csvFile in csvFiles){
//            if(csvFile.name.contains(Paths.TRACE_DATA_PREFIX)){
//                traceDataFile = csvFile
//            }
//
//            if(csvFile.name.contains(Paths.CAMERA_METADATA_PREFIX)){
//                cameraMetadataFile = csvFile
//            }
//        }
//
//        var missingFile = false
//        var missingFilesMessage = "Could not find the following files:\n"
//        var loadTif = true
//        var loadCameraMetadata = true
//        var loadTraceData = true
//        if(tifFile == null){
//            missingFilesMessage += "- Image file (.tif).\n"
//            missingFile = true
//            loadTif = false
//        }
//        if(cameraMetadataFile == null){
//            missingFilesMessage += "- Camera metadata file (csv file with name containing \"${Paths.CAMERA_METADATA_PREFIX}\").\n"
//            missingFile = true
//            loadCameraMetadata = false
//        }
//        if(traceDataFile == null){
//            missingFilesMessage += "- Trace data file (csv file with name containing \"${Paths.TRACE_DATA_PREFIX}\").\n"
//            missingFile = true
//            loadTraceData = false
//        }
//
//        if(missingFile) {
//            missingFilesMessage += "If this is expected please ignore this message."
//            JOptionPane.showMessageDialog(GUIMain.strimmUIService.strimmFrame, missingFilesMessage, "Missing files", JOptionPane.WARNING_MESSAGE)
//        }
//
//        var newCameraWindowPlugin : CameraWindowPlugin? = null
//        if(loadTif) {
//            newCameraWindowPlugin = GUIMain.importService.importTif(tifFile!!)
//        }
//
//        if(loadTraceData) {
//            GUIMain.importService.importTraceData(traceDataFile!!)
//        }
//
//        if(loadCameraMetadata) {
//            GUIMain.importService.importCameraMetadata(cameraMetadataFile!!)
//        }
//
//        if(newCameraWindowPlugin != null){
//            java.awt.EventQueue.invokeLater {
//                Thread(Runnable {
//                    newCameraWindowPlugin.cameraWindowController.pressMinusButton()
//                    GUIMain.strimmUIService.redrawROIs(newCameraWindowPlugin, true)
//                    newCameraWindowPlugin.cameraWindowController.addSliderListener()
//                }).start()
//            }
//        }
    }
//to do
    fun SetNumberOfDataPoints(numPoints : Int){

    }
}