package uk.co.strimm.actors

import akka.actor.AbstractActor
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import akka.actor.Props
import hdf.hdf5lib.H5
import hdf.hdf5lib.HDF5Constants
import uk.ac.shef.*
import uk.ac.shef.planar.alias.PlanarCoordinate
import uk.ac.shef.planar.alias.PlanarDataset
import uk.ac.shef.planar.dsl.planar
import uk.co.strimm.Acknowledgement
import uk.co.strimm.CameraMetaDataStore
import uk.co.strimm.Paths.Companion.EXPERIMENT_OUTPUT_FOLDER
import uk.co.strimm.TraceDataStore
import uk.co.strimm.actors.messages.Message
import uk.co.strimm.actors.messages.ask.AskIsWriting
import uk.co.strimm.actors.messages.tell.*
import uk.co.strimm.gui.GUIMain
import uk.co.strimm.services.UIstate
import java.io.FileWriter
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.logging.Level
import kotlin.system.measureTimeMillis


class FileWriterActor : AbstractActor() {
    companion object {
        fun props(): Props {
            return Props.create<FileWriterActor>(FileWriterActor::class.java) { FileWriterActor() }
        }
    }

    private lateinit var hdfFile : File
    private var hdfFileID = -1
    var cameraData = hashMapOf<String, ArrayImgStore>()
    var cameraMetaData = hashMapOf<String, ArrayList<CameraMetaDataStore>>()

    var traceData = hashMapOf<String, ArrayList<TraceDataStore>>()
    var traceFromROIData = hashMapOf<String, ArrayList<TraceDataStore>>()

    var numCamDatasetsReceived = 0
    var numTraceDatasetsReceived = 0
    var numTraceROIDatasetsReceived = 0
    var expectedNumCamDatasets = 0
    var expectedNumTraceDatasets = 0
    var expectedNumTraceROIDatasets = 0

    //These are lists of the dataset objects created for the HDF5 file and NOT ImageJ or otherwise datasets
//    var cameraDatasets = arrayListOf<PlanarDataset>()
//    var cameraMetaDatasets = arrayListOf<PlanarDataset>()

//    var traceFromROIDatasets = arrayListOf<PlanarDataset>()
//    var traceDatasets = arrayListOf<PlanarDataset>()

    var isWriting = false

    override fun createReceive(): Receive {
        return receiveBuilder()
                .match<Message>(Message::class.java) { message ->
                    GUIMain.loggerService.log(Level.INFO, "FileWriter actor received message: $message")
                    sender().tell(Acknowledgement.INSTANCE, self())
                }
                .match<TellCreateFile>(TellCreateFile::class.java) { createFileMessage ->
                    GUIMain.loggerService.log(Level.INFO, "FileWriter actor received message to create file")
                    sender().tell(Acknowledgement.INSTANCE, self())
                    //TODO move the creation of the file to here (requires getting metadata earlier)
                }
                .match<AskIsWriting>(AskIsWriting::class.java){
                    sender().tell(isWriting, self())
                }
                .match<TellNewCameraDataStore>(TellNewCameraDataStore::class.java){ message ->
                    expectedNumCamDatasets++
                    println("Expected number of camera datasets is now: $expectedNumCamDatasets")
                }
                .match<TellNewTraceDataStore>(TellNewTraceDataStore::class.java){ message ->
                    expectedNumTraceDatasets++
                    println("Expected number of trace datasets is now: $expectedNumTraceDatasets")
                }
                .match<TellNewTraceROIDataStore>(TellNewTraceROIDataStore::class.java){ message ->
                    expectedNumTraceROIDatasets++
                    println("Expected number of trace ROI datasets is now: $expectedNumTraceROIDatasets")
                }
                .match<TellCameraData>(TellCameraData::class.java) { images ->
                   // println("filewriter TellCameraData")
                    var sourceCamera = images.dataName
                    cameraData[sourceCamera] = images.imageStore
                    cameraMetaData[sourceCamera] = images.metaData
                    numCamDatasetsReceived++

                    if(allDataReceived()){
                        GUIMain.loggerService.log(Level.INFO, "Received all expected datasets")
                        GUIMain.loggerService.log(Level.INFO, "Number of expected camera datasets: $expectedNumCamDatasets, number of received camera datasets: $numCamDatasetsReceived")
                        GUIMain.loggerService.log(Level.INFO, "Number of expected trace ROI datasets: $expectedNumTraceROIDatasets, number of received trace ROI datasets: $numTraceROIDatasetsReceived")
                        GUIMain.loggerService.log(Level.INFO, "Number of expected trace datasets: $expectedNumTraceDatasets, number of received trace datasets: $numTraceDatasetsReceived")

//                        createBasicFile()
                        createBasicHDFFile()

                        val writeSuccess = writeFile()

                        isWriting = if (!writeSuccess) {
                            GUIMain.loggerService.log(Level.INFO, "Failed to write to file")
                            false
                        } else {
                            clearDatasetsAndReset()
                            false
                        }
                    }
                }
                .match<TellTraceData>(TellTraceData::class.java){ tracePoints ->
                   // println("filewriter  telltracedata")
                    //separate out the List<TraceDataStore> for ROI and non ROI
                    //store in traceFromROIData and traceData which are: HashMap<String, List<TraceDataStore>>
                    //When all of the datasets have been received, the strimm is switched back to Preview mode
                    //and the datasets are saved as hdf5 files.
                    GUIMain.loggerService.log(Level.INFO, "FileWriterActor received trace data")
                    if(tracePoints.isTraceFromROI){
                        numTraceROIDatasetsReceived++
                        //use the roi name of the first entry into the List<TraceDataStore>
                        //this will cause an error if there are several roi in the same feed.
                        //
                        //using the roi : Overlay member .name as an ID which means the Overlay itself can be null
                        //TODO hardcoded trace ROI index
//                        traceFromROIData[tracePoints.traceData[0].roi!!.name] = tracePoints.traceData
                        val roiNames = arrayListOf<String>()
                        for(roi in tracePoints.traceData){
                            if (roi.roi!!.name !in roiNames) {
                                roiNames.add(roi.roi.name)
                            }
                        }


                        for(roiName in roiNames){
                            val dataForROI = tracePoints.traceData.filter{ x -> x.roi!!.name == roiName}
                            traceFromROIData[roiName] = dataForROI as ArrayList<TraceDataStore>
                        }
                    }
                    else{
                        //same problem here which means that we only save 1 trace......
                        numTraceDatasetsReceived++
                        //overwrites the roi!!.name
                        //TODO hardcoded trace ROI index
                        traceData[tracePoints.traceData[0].roi!!.name + numTraceDatasetsReceived] = tracePoints.traceData

                    //this will be ""+5 etc and will not be a unique key
                    }

                    if(allDataReceived()){
                        //once all of the datasets have been received now save to hdf5
                        GUIMain.loggerService.log(Level.INFO, "Received all expected datasets")
                        GUIMain.loggerService.log(Level.INFO, "Number of expected camera datasets: $expectedNumCamDatasets, number of received camera datasets: $numCamDatasetsReceived")
                        GUIMain.loggerService.log(Level.INFO, "Number of expected trace ROI datasets: $expectedNumTraceROIDatasets, number of received trace ROI datasets: $numTraceROIDatasetsReceived")
                        GUIMain.loggerService.log(Level.INFO, "Number of expected trace datasets: $expectedNumTraceDatasets, number of received trace datasets: $numTraceDatasetsReceived")

                        //Create the hdf5 file
                        createBasicHDFFile()
//                        createBasicFile()

                        //Populate it
                        val writeSuccess = writeFile()

                        isWriting = if(writeSuccess){
                            clearDatasetsAndReset()
                            false
                        } else{
                            GUIMain.loggerService.log(Level.INFO, "Failed to write to file")
                            false
                        }
                    }
                }
                .build()
    }

    /**
     * Method to check that the file writer actor has received all the data it was expecting.
     * @return True if the number of datasets received is equal to the expected number of datasets for each dataset type:
     * camera, trace from ROI, and trace.
     */
    private fun allDataReceived() : Boolean {
        //println("allDataReceived")
//        println("numCamDatasetsReceived " + numCamDatasetsReceived + "/" + expectedNumCamDatasets)
//        println("numTraceDatasetsReceived " + numTraceDatasetsReceived + "/" + expectedNumTraceDatasets)
//        println("numTraceROIDatasetsReceived " + numTraceROIDatasetsReceived + "/" + expectedNumTraceROIDatasets)
        return (numCamDatasetsReceived == expectedNumCamDatasets) &&
                (numTraceDatasetsReceived == expectedNumTraceDatasets) &&
                (numTraceROIDatasetsReceived == expectedNumTraceROIDatasets)
    }

    private fun clearDatasetsAndReset(){
        println("#####################clearDatasetsAndReset")
        cameraData = hashMapOf<String, ArrayImgStore>()
        cameraMetaData = hashMapOf<String, ArrayList<CameraMetaDataStore>>()

        traceData = hashMapOf<String, ArrayList<TraceDataStore>>()
        traceFromROIData = hashMapOf<String, ArrayList<TraceDataStore>>()

        numCamDatasetsReceived = 0
        numTraceDatasetsReceived = 0
        numTraceROIDatasetsReceived = 0
        expectedNumCamDatasets = 0
        expectedNumTraceDatasets = 0
        expectedNumTraceROIDatasets = 0

        //These are lists of the dataset objects created for the HDF5 file and NOT ImageJ or otherwise datasets
//        cameraDatasets = arrayListOf<PlanarDataset>()
//        cameraMetaDatasets = arrayListOf<PlanarDataset>()

//        traceFromROIDatasets = arrayListOf<PlanarDataset>()
//        traceDatasets = arrayListOf<PlanarDataset>()

        resetStream()
    }

    /**
     * Reset analogue data stream classes so preview mode can be resumed after an experiment has finished.
     */
    private fun resetStream(){
        println("#####resetStream()")
        //When you run an experiment, it is assumed you will always want to save to file (otherwise you'd use preview mode)
        //Therefore we can safely assume that this file writer actor will be created and therefore the call to
        //resetAnalogueDataStreams() will be made
        //GUIMain.timerService.resetAnalogueDataStreams()   TW    2/7/21
        GUIMain.strimmUIService.state = UIstate.PREVIEW //TODO temporarily here until STRIMM actor takes control of states
        println("####stopStream()")
        val stopSuccess = GUIMain.experimentService.stopStream()
        if (stopSuccess){
            println("####restartStreamInPreviewMode()")
            GUIMain.experimentService.restartStreamInPreviewMode()
        }
    }

    private fun createBasicHDFFile() : Boolean {
        GUIMain.loggerService.log(Level.INFO, "Creating new format of h5 file")
        return try{
            val fileName = generateFileName()
            hdfFileID = H5.H5Fcreate(
                    fileName, HDF5Constants.H5F_ACC_TRUNC,
                    HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT)
            true
        }
        catch(ex : Exception){
            GUIMain.loggerService.log(Level.SEVERE, "Failed to create h5 file")
            GUIMain.loggerService.log(Level.SEVERE, ex.stackTrace)
            false
        }
    }

    private fun generateFileName() : String{
        var fname = "$EXPERIMENT_OUTPUT_FOLDER/strimm_expTEST" //TODO remove "Test" once finished implementing
        val timeStampFull =
                ZonedDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME).replace(":", "-")
        val timeStamp =
                timeStampFull.substring(0, timeStampFull.length - 4) //Trim the end to not include milliseconds
        fname += timeStamp
        fname += ".h5"
        return fname
    }

    private fun closeHDFFile(){
        try {
            GUIMain.loggerService.log(Level.INFO,"Closing h5 file")
            if (hdfFileID >= 0) {
                H5.H5Fclose(hdfFileID)
            }
        }
        catch (ex: Exception) {
            GUIMain.loggerService.log(Level.SEVERE, "Failed to close h5 file")
            GUIMain.loggerService.log(Level.SEVERE, ex.stackTrace)
        }
    }

    private fun createHDFDatasetGroup(datasetName: String) : Int {
        return try {
            val group = H5.H5Gcreate(hdfFileID, datasetName, HDF5Constants.H5P_DEFAULT,
                    HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT)
            group
        }
        catch(ex: Exception){
            GUIMain.loggerService.log(Level.SEVERE, "Error creating HDF group using H5GCreate. Message: ${ex.message}")
            GUIMain.loggerService.log(Level.SEVERE, ex.stackTrace)
            -1
        }
    }

    private fun closeHDFDatasetGroup(datasetGroupID: Int){
        H5.H5Gclose(datasetGroupID)
    }

    private fun writeCameraHDFDataset(datasetGroupID: Int, dataArray : ArrayList<out CameraImg>, dataType: Int, numDims: Int, dims: LongArray, baseName: String){
        for((counter, data) in dataArray.withIndex()) {
            try {
                val customName = baseName + counter.toString()
                val dataSpaceID = H5.H5Screate_simple(numDims, dims, null)
                var dataID: Int
                dataID = H5.H5Dcreate(datasetGroupID, customName, dataType, dataSpaceID,
                        HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT)
                when (data) {
                    is ByteImg -> H5.H5Dwrite(dataID, dataType, HDF5Constants.H5S_ALL, HDF5Constants.H5S_ALL, HDF5Constants.H5P_DEFAULT, data.stack)
                    is ShortImg -> H5.H5Dwrite(dataID, dataType, HDF5Constants.H5S_ALL, HDF5Constants.H5S_ALL, HDF5Constants.H5P_DEFAULT, data.stack)
                    is FloatImg -> H5.H5Dwrite(dataID, dataType, HDF5Constants.H5S_ALL, HDF5Constants.H5S_ALL, HDF5Constants.H5P_DEFAULT, data.stack)
                    else -> {
                        GUIMain.loggerService.log(Level.WARNING, "Could not cast data to ByteImg, ShortImg, or FloatImg")
                    }
                }

                if (dataID >= 0) {
                    //We need to close straight after writing
                    H5.H5Dclose(dataID)
                    H5.H5Sclose(dataSpaceID)
                }
            }
            catch(ex : Exception){
                GUIMain.loggerService.log(Level.SEVERE, "Error in writing dataset to h5 file")
                GUIMain.loggerService.log(Level.SEVERE, ex.stackTrace)
            }
        }
    }

    private fun writeTimingHDFDataset(datasetGroupID: Int, times : DoubleArray, dataType: Int, numDims: Int, dims: LongArray, baseName: String){
        val dataSpaceID = H5.H5Screate_simple(numDims, dims, null)
        val dataID = H5.H5Dcreate(datasetGroupID, baseName, dataType, dataSpaceID,
            HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT)
        H5.H5Dwrite(dataID, dataType, HDF5Constants.H5S_ALL, HDF5Constants.H5S_ALL, HDF5Constants.H5P_DEFAULT, times)
        if (dataID >= 0) {
            //We need to close straight after writing
            H5.H5Dclose(dataID)
            H5.H5Sclose(dataSpaceID)
        }
    }

    private fun writeROIHDFDataset(datasetGroupID: Int, times : DoubleArray, dataType: Int, numDims: Int, dims: LongArray, baseName: String){
        val dataSpaceID = H5.H5Screate_simple(numDims, dims, null)
        val dataID = H5.H5Dcreate(datasetGroupID, baseName, dataType, dataSpaceID,
                HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT)
        H5.H5Dwrite(dataID, dataType, HDF5Constants.H5S_ALL, HDF5Constants.H5S_ALL, HDF5Constants.H5P_DEFAULT, times)
        if (dataID >= 0) {
            //We need to close straight after writing
            H5.H5Dclose(dataID)
            H5.H5Sclose(dataSpaceID)
        }
    }

    private fun writeTraceHDFDataset(datasetGroupID: Int, times : DoubleArray, dataType: Int, numDims: Int, dims: LongArray, baseName: String){
        val dataSpaceID = H5.H5Screate_simple(numDims, dims, null)
        val dataID = H5.H5Dcreate(datasetGroupID, baseName, dataType, dataSpaceID,
                HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT)
        H5.H5Dwrite(dataID, dataType, HDF5Constants.H5S_ALL, HDF5Constants.H5S_ALL, HDF5Constants.H5P_DEFAULT, times)
        if (dataID >= 0) {
            //We need to close straight after writing
            H5.H5Dclose(dataID)
            H5.H5Sclose(dataSpaceID)
        }
    }

    /**
     * This creates a structured H5 file based on the experiment but will not populate the data yet. Metadata will be
     * populated here however.
     */
//    private fun createBasicFile() {
//        println("####createBasicFile()")
//        GUIMain.loggerService.log(Level.INFO, "Creating empty h5 file...")
//        initialiseLibrary(relativeLibraryPath("UHDF5"))
//
//        val timeStampFull = ZonedDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME).replace(":", "-")
//        val timeStamp = timeStampFull.substring(0, timeStampFull.length - 4) //Trim the end to not include milliseconds
//        val softwareVersion = "STRÏMM v0.0.0" //TODO get software version from pom.xml
//
//        hdfFile = buildFileExperimentalContracts("$EXPERIMENT_OUTPUT_FOLDER/strimm_exp$timeStamp.h5") {
//            metadata {
//                author = GUIMain.experimentService.expConfig.author
//                affiliation = GUIMain.experimentService.expConfig.affiliation
//                description = GUIMain.experimentService.expConfig.description
//                software = softwareVersion
//            }
//            println("Saving cameraData h5 header")
//            cameraData.forEach { datasetName, data ->
//                val newDataset = planar(datasetName) {
//                    description = datasetName
//
//                    axes(1, "Time") {
//                        descriptions = arrayOf("Time")
//                        units = arrayOf("px")
//
//                        //The literal units of each dimension. So if the unit was a time based unit e.g. milliseconds
//                        //then a value of 100.0 would mean 100ms
//                        unitsPerSample = doubleArrayOf(1.0)
//
//                        namedAxisValues {
//                            axis("Frame")
//                        }
//                    }
//                    plane(2, "x", "y")
//                }
//                cameraDatasets.add(newDataset)
//            }
//            println("Saving cameraData meta-data h5 header")
//            cameraMetaData.forEach{ datasetName, data ->
//                val newDataSetName = datasetName + "Meta"
//                println("***** "+ newDataSetName)
//                val newDataset = planar(newDataSetName){
//                    axes(1, "Time")
//                    plane(1, "Time")
//                }
//                cameraMetaDatasets.add(newDataset)
//
////                val cameraMetaDatasetToUse = cameraMetaDatasets.first { x -> x.description.name == datasetName + "Meta" }
////                if (cameraMetaDatasetToUse == null){
////                    println("null")
////                }
////                else{
////                    println("not null")
////                }
//            }
//            println("Saving ROI trace h5 header")
//            traceFromROIData.forEach { datasetName, data ->
//                val newDataset = planar(datasetName) {
//                    description = "" //TODO pull in description from relevant flow
//                    axes(1, "Time")
//                    plane(1, "Time")
//                }
//                val newDataset1 = planar(datasetName + "_timeAcquired") {
//                    description = datasetName + "_timeAcquired" //sourceConfig.description
//                    axes(1, "Time")
//                    plane(1, "Time")
//                }
//                traceFromROIDatasets.add(newDataset)
//                traceFromROIDatasets.add(newDataset1)
//            }
//            println("Saving trace-data h5 header")
//            try {
//                traceData.forEach { datasetName, data ->
//                    val newDataset = planar(datasetName) {
//                        description = datasetName //sourceConfig.description
//                        axes(1, "Time")
//                        plane(1, "Time")
//                    }
//                    val newDataset1 = planar(datasetName + "_timeAcquired") {
//                        description = datasetName + "_timeAcquired" //sourceConfig.description
//                        axes(1, "Time")
//                        plane(1, "Time")
//                    }
//
//                    traceDatasets.add(newDataset)
//                    traceDatasets.add(newDataset1)
//
//                }
//            } catch (ex : Exception){
//                println(ex.message)
//            }
//        }
//    }

    /**
     * Top level method to write the HDF5 file
     */
    private fun writeFile() : Boolean{
        /**
         * ONE DATASET PER DATASOURCE. However, traces from ROIs are exceptions and will be treated as a datasource.
         */
        isWriting = true

        try {
            val time = measureTimeMillis {
                for (cameraDatasetKey in cameraData.keys) {
                    writeCameraDataset(cameraDatasetKey)
                }
                for (traceFromROIDatasetKey in traceFromROIData.keys) {
                    writeTraceFromROIDataset(traceFromROIDatasetKey)
                }
                for (traceDatasetKey in traceData.keys) {
                    writeTraceDataset(traceDatasetKey)
                }
            }

            closeHDFFile()
            GUIMain.loggerService.log(Level.INFO, "Time taken to write data to file: ${time}ms")
            return true
        }
        catch(ex : Exception){
            GUIMain.loggerService.log(Level.SEVERE,"Error saving data to HDF5 file. Error message: ${ex.message}")
            GUIMain.loggerService.log(Level.SEVERE,ex.stackTrace)
            return false
        }


//        try {
//            val time = measureTimeMillis {
//                hdfFile.use {
//                    for (cameraDatasetKey in cameraData.keys) {
//                        writeCameraDataset(cameraDatasetKey)
//                    }
//                    for (traceFromROIDatasetKey in traceFromROIData.keys) {
//                        writeTraceFromROIDataset(traceFromROIDatasetKey)
//                    }
//                    for (traceDatasetKey in traceData.keys) {
//                        writeTraceDataset(traceDatasetKey)
//                    }
//                }
//            }
//
//            GUIMain.loggerService.log(Level.INFO, "Time taken to write data to file: ${time}ms")
//            return true
//        }
//        catch(ex : Exception){
//            GUIMain.loggerService.log(Level.SEVERE,"Error saving data to HDF5 file. Error message: ${ex.message}")
//            GUIMain.loggerService.log(Level.SEVERE,ex.stackTrace)
//            return false
//        }
    }

    private fun writeCameraDataset(datasetKey: String){
        if (GUIMain.experimentService.expConfig.HDF5Save) {
            GUIMain.loggerService.log(Level.INFO, "Writing camera data to HDF5 file")
            if (datasetKey in cameraData.keys) {
                val cameraDatasetToUse = cameraData[datasetKey]
                var xDim: Long
                var yDim: Long
                val dataToWrite = cameraData[datasetKey]
                var dims: LongArray
                val numDims = 2

                if (dataToWrite!!.byteStack.size > 0){
                    xDim = dataToWrite.byteStack[0].xDim
                    yDim = dataToWrite.byteStack[0].yDim
                    dims = longArrayOf(yDim, xDim) //Dimensions have to be reversed as height comes first
                    val datasetGroupID = createHDFDatasetGroup(datasetKey)
                    if(datasetGroupID >= 0) {
                        GUIMain.loggerService.log(Level.INFO, "Writing dataset $datasetKey")
                        writeCameraHDFDataset(datasetGroupID, dataToWrite.byteStack, HDF5Constants.H5T_NATIVE_INT8, numDims, dims, "Frame")
                        closeHDFDatasetGroup(datasetGroupID)
                    }
                    else{
                        GUIMain.loggerService.log(Level.WARNING, "Could not create group for dataset $datasetKey")
                    }

                    val timingDatasetGroupID = createHDFDatasetGroup(datasetKey + "_Times")
                    if(timingDatasetGroupID >= 0) {
                        val cameraTimingData = cameraMetaData[datasetKey]!!.map { y -> y.timeAcquired.toDouble() }.toDoubleArray()
                        val timingNumDims = 2
                        val timingDims = longArrayOf(cameraTimingData.size.toLong(), 1)
                        GUIMain.loggerService.log(Level.INFO, "Writing dataset ${datasetKey + "_Times"}")
                        writeTimingHDFDataset(timingDatasetGroupID, cameraTimingData, HDF5Constants.H5T_NATIVE_DOUBLE, timingNumDims, timingDims, "Times")
                        closeHDFDatasetGroup(timingDatasetGroupID)
                    }
                    else{
                        GUIMain.loggerService.log(Level.WARNING, "Could not create group for dataset ${datasetKey + "_Times"}")
                    }
                }
                if (dataToWrite.shortStack.size > 0){
                    xDim = dataToWrite.shortStack[0].xDim
                    yDim = dataToWrite.shortStack[0].yDim
                    dims = longArrayOf(yDim, xDim) //Dimensions have to be reversed as height comes first
                    val datasetGroupID = createHDFDatasetGroup(datasetKey)
                    if(datasetGroupID >= 0) {
                        GUIMain.loggerService.log(Level.INFO, "Writing dataset $datasetKey")
                        writeCameraHDFDataset(datasetGroupID, dataToWrite.shortStack, HDF5Constants.H5T_NATIVE_INT16, numDims, dims, "Frame")
                        closeHDFDatasetGroup(datasetGroupID)
                    }
                    else{
                        GUIMain.loggerService.log(Level.WARNING, "Could not create group for dataset $datasetKey")
                    }

                    val timingDatasetGroupID = createHDFDatasetGroup(datasetKey + "_Times")
                    if(timingDatasetGroupID >= 0) {
                        val cameraTimingData = cameraMetaData[datasetKey]!!.map { y -> y.timeAcquired.toDouble() }.toDoubleArray()
                        val timingNumDims = 2
                        val timingDims = longArrayOf(cameraTimingData.size.toLong(), 1)
                        GUIMain.loggerService.log(Level.INFO, "Writing dataset $datasetKey" + "_Times")
                        writeTimingHDFDataset(timingDatasetGroupID, cameraTimingData, HDF5Constants.H5T_NATIVE_DOUBLE, timingNumDims, timingDims, "Times")
                        closeHDFDatasetGroup(timingDatasetGroupID)
                    }
                    else{
                        GUIMain.loggerService.log(Level.WARNING, "Could not create group for dataset $datasetKey" + "_Times")
                    }
                }
                if (dataToWrite.floatStack.size > 0){
                    xDim = dataToWrite.floatStack[0].xDim
                    yDim = dataToWrite.floatStack[0].yDim
                    dims = longArrayOf(yDim, xDim) //Dimensions have to be reversed as height comes first
                    val datasetGroupID = createHDFDatasetGroup(datasetKey)
                    if(datasetGroupID >= 0) {
                        GUIMain.loggerService.log(Level.INFO, "Writing dataset $datasetKey")
                        writeCameraHDFDataset(datasetGroupID, dataToWrite.floatStack, HDF5Constants.H5T_NATIVE_INT32, numDims, dims, "Frame")
                        closeHDFDatasetGroup(datasetGroupID)
                    }
                    else{
                        GUIMain.loggerService.log(Level.WARNING, "Could not create group for dataset $datasetKey")
                    }

                    val timingDatasetGroupID = createHDFDatasetGroup(datasetKey + "_Times")
                    if(timingDatasetGroupID >= 0) {
                        val cameraTimingData = cameraMetaData[datasetKey]!!.map { y -> y.timeAcquired.toDouble() }.toDoubleArray()
                        val timingNumDims = 2
                        val timingDims = longArrayOf(cameraTimingData.size.toLong(), 1)
                        GUIMain.loggerService.log(Level.INFO, "Writing dataset $datasetKey" + "_Times")
                        writeTimingHDFDataset(timingDatasetGroupID, cameraTimingData, HDF5Constants.H5T_NATIVE_DOUBLE, timingNumDims, timingDims, "Times")
                        closeHDFDatasetGroup(timingDatasetGroupID)
                    }
                    else{
                        GUIMain.loggerService.log(Level.WARNING, "Could not create group for dataset $datasetKey" + "_Times")
                    }
                }
            }
        }
    }

    private fun writeTraceFromROIDataset(datasetKey : String){
        if (datasetKey in traceFromROIData.keys) {
            traceFromROIData[datasetKey]!!.map { x -> x.roiVal.toDouble() }.toDoubleArray()
            val timingDatasetGroupID = createHDFDatasetGroup(datasetKey)
            if(timingDatasetGroupID >= 0) {
                val ROITimingData = traceFromROIData[datasetKey]!!.map { x -> x.roiVal.toDouble() }.toDoubleArray()
                val ROINumDims = 2
                val timingDims = longArrayOf(ROITimingData.size.toLong(), 1)
                GUIMain.loggerService.log(Level.INFO, "Writing dataset $datasetKey")
                writeROIHDFDataset(timingDatasetGroupID, ROITimingData, HDF5Constants.H5T_NATIVE_DOUBLE, ROINumDims, timingDims, "Values")
                closeHDFDatasetGroup(timingDatasetGroupID)
            }
            else{
                GUIMain.loggerService.log(Level.WARNING, "Could not create group for dataset $datasetKey")
            }
        }
    }

    private fun writeTraceDataset(datasetKey: String){
        if (datasetKey in traceData.keys) {
            traceData[datasetKey]!!.map { x -> x.roiVal.toDouble() }.toDoubleArray()
            val datasetGroupID = createHDFDatasetGroup(datasetKey)
            if(datasetGroupID >= 0) {
                val traceDataToWrite = traceData[datasetKey]!!.map { x -> x.roiVal.toDouble() }.toDoubleArray()
                val ROINumDims = 2
                val timingDims = longArrayOf(traceDataToWrite.size.toLong(), 1)
                GUIMain.loggerService.log(Level.INFO, "Writing dataset $datasetKey")
                writeTraceHDFDataset(datasetGroupID, traceDataToWrite, HDF5Constants.H5T_NATIVE_DOUBLE, ROINumDims, timingDims, "Values")
                closeHDFDatasetGroup(datasetGroupID)
            }
            else{
                GUIMain.loggerService.log(Level.WARNING, "Could not create group for dataset $datasetKey")
            }

            val timingDatasetGroupID = createHDFDatasetGroup(datasetKey + "_Times")
            if(timingDatasetGroupID >= 0) {
                val traceTimingDataToWrite = traceData[datasetKey]!!.map { x -> x.timeAcquired.toDouble() }.toDoubleArray()
                val numDims = 2
                val timingDims = longArrayOf(traceTimingDataToWrite.size.toLong(), 1)
                GUIMain.loggerService.log(Level.INFO, "Writing dataset $datasetKey" + "_Times")
                writeTraceHDFDataset(timingDatasetGroupID, traceTimingDataToWrite, HDF5Constants.H5T_NATIVE_DOUBLE, numDims, timingDims, "Values")
                closeHDFDatasetGroup(timingDatasetGroupID)
            }
            else{
                GUIMain.loggerService.log(Level.WARNING, "Could not create group for dataset $datasetKey" + "_Times")
            }
        }
    }

    //    private fun writeCameraDataset(datasetKey : String){
//        if (GUIMain.experimentService.expConfig.HDF5Save) {
//            GUIMain.loggerService.log(Level.INFO, "Writing camera data to h5 file")
//            for (cameraDatasetToUse in cameraDatasets) {
//                if (cameraDatasetToUse.description.name == datasetKey) {
//                    var byteData: ByteArray
//                    var shortData: ShortArray
//                    var floatData: FloatArray
//                    var size: LongArray
//                    var xDim: Long
//                    var yDim: Long
//                    val dataToWrite = cameraData[datasetKey]
//                    var count = 0
//                    if (dataToWrite!!.byteStack.size > 0) {
//                        cameraDatasetToUse.use {
//                            for (t in 0L until dataToWrite.byteStack.size) {
//                                xDim = dataToWrite.byteStack[t.toInt()].xDim
//                                yDim = dataToWrite.byteStack[t.toInt()].yDim
//                                byteData = dataToWrite.byteStack[t.toInt()].stack
//
//                                //The dimensions are the other way around here. Likely because of the way
//                                //images are read in HDF5 i.e. column then row or row then column
//                                size = longArrayOf(yDim, xDim)
//
//                                it.write(PlanarCoordinate(t), DataType.from(byteData, size))
//                                //dataset.write(PlanarCoordinate(t), DataType.from(byteData, size))
//                                count++
//                            }
//                        }
//                    }
//                    if (dataToWrite.shortStack.size > 0) {
//                        cameraDatasetToUse.use {
//                            for (i in 0L until dataToWrite.shortStack.size) {
//                                xDim = dataToWrite.shortStack[i.toInt()].xDim
//                                yDim = dataToWrite.shortStack[i.toInt()].yDim
//                                shortData = dataToWrite.shortStack[i.toInt()].stack
//
//                                //The dimensions are the other way around here. Likely because of the way
//                                //images are read in HDF5 i.e. column then row or row then column
//                                size = longArrayOf(yDim, xDim)
//                                it.write(PlanarCoordinate(i), DataType.from(shortData, size))
//                                //dataset.write(PlanarCoordinate(i), DataType.from(shortData, size))
//                                count++
//                            }
//                        }
//                    }
//                    if (dataToWrite.floatStack.size > 0) {
//                        cameraDatasetToUse.use{
//                            for (i in 0L until dataToWrite.floatStack.size) {
//                                xDim = dataToWrite.floatStack[i.toInt()].xDim
//                                yDim = dataToWrite.floatStack[i.toInt()].yDim
//                                floatData = dataToWrite.floatStack[i.toInt()].stack
//
//                                //The dimensions are the other way around here. Likely because of the way
//                                //images are read in HDF5 i.e. column then row or row then column
//                                size = longArrayOf(yDim, xDim)
//                                it.write(PlanarCoordinate(i), DataType.from(floatData, size))
//                                //dataset.write(PlanarCoordinate(i), DataType.from(floatData, size))
//                                count++
//                            }
//                        }
//                    }
//                }
//            }
//
//            println("#### write camera metadata data to h5")
//            for (cameraMetaDataset in cameraMetaDatasets){
//                println("####" + cameraMetaDataset)
//                if (cameraMetaDataset.description.name == datasetKey + "Meta"){
//                    val cameraMetaDataToWrite = cameraMetaData[datasetKey]!!.map{y -> y.timeAcquired.toDouble()  }.toDoubleArray()
//                    cameraMetaDataset.use{
//                        it.write(PlanarCoordinate(0), DataType.from(cameraMetaDataToWrite, longArrayOf(cameraMetaDataToWrite.size.toLong())))
//                    }
////                    cameraMetaDataset.write(PlanarCoordinate(0), DataType.from(cameraMetaDataToWrite, longArrayOf(cameraMetaDataToWrite.size.toLong())))
//                }
//            }
//        }
//        else{
//            println("#### writing data to tiff")
//            GUIMain.loggerService.log(Level.INFO, "Writing camera data to tiff files")
//            var byteData: ByteArray
//            var shortData: ShortArray
//            var floatData: FloatArray
//            var xDim: Long
//            var yDim: Long
//            val dataToWrite = cameraData[datasetKey]
//            var count = 0
//            val datasetName = "cameraDataset"
//
//            if (dataToWrite!!.byteStack.size > 0) {
//                for (t in 0L until dataToWrite.byteStack.size) {
//                    xDim = dataToWrite.byteStack[t.toInt()].xDim
//                    yDim = dataToWrite.byteStack[t.toInt()].yDim
//                    byteData = dataToWrite.byteStack[t.toInt()].stack
//                    val dataset = GUIMain.datasetService.create(
//                            UnsignedByteType(),
//                            longArrayOf(xDim, yDim),
//                            datasetName,
//                            arrayOf(Axes.X, Axes.Y)
//                    )
//                    dataset.setPlane(0, byteData)
//                    val szTime: LocalTime = LocalTime.now()
//                    GUIMain.datasetIOService.save(
//                            dataset,
//                            "ExperimentResults\\" + datasetKey + "\\" + t.toString() + ".tiff" //TODO hardcoded
//                    )
//                    count++
//
//                }
//            }
//            else if (dataToWrite.shortStack.size > 0) {
//                for (i in 0L until dataToWrite.shortStack.size) {
//                    xDim = dataToWrite.shortStack[i.toInt()].xDim
//                    yDim = dataToWrite.shortStack[i.toInt()].yDim
//                    shortData = dataToWrite.shortStack[i.toInt()].stack
//                    val dataset = GUIMain.datasetService.create(
//                            UnsignedShortType(),
//                            longArrayOf(xDim, yDim),
//                            datasetName,
//                            arrayOf(Axes.X, Axes.Y)
//                    )
//                    dataset.setPlane(0, shortData)
//                    val szTime: LocalTime = LocalTime.now()
//                    GUIMain.datasetIOService.save(
//                            dataset,
//                            "ExperimentResults\\" + datasetKey + "\\" + i.toString() + ".tiff" //TODO hardcoded
//                    )
//                    count++
//
//                }
//            }
//            else if (dataToWrite.floatStack.size > 0) {
//                for (i in 0L until dataToWrite.floatStack.size) {
//                    xDim = dataToWrite.floatStack[i.toInt()].xDim
//                    yDim = dataToWrite.floatStack[i.toInt()].yDim
//                    floatData = dataToWrite.floatStack[i.toInt()].stack
//                    val dataset = GUIMain.datasetService.create(
//                            FloatType(),
//                            longArrayOf(xDim, yDim),
//                            datasetName,
//                            arrayOf(Axes.X, Axes.Y)
//                    )
//                    dataset.setPlane(0, floatData)
//                    val szTime: LocalTime = LocalTime.now()
//                    GUIMain.datasetIOService.save(
//                            dataset,
//                            "ExperimentResults\\" + datasetKey + "\\" + i.toString() + ".tiff" //TODO hardcoded
//                    )
//                    count++
//                }
//            }
//            else{
//                //not recognised
//            }
//
//            //save the meta data - which is just the timestamp at the moment
//            val cameraMetaDataToWrite = cameraMetaData[datasetKey]!!.map{x -> x.timeAcquired.toDouble() }.toDoubleArray()
//            try {
//                println("Write $datasetKey Meta")
//
//                //TODO hardcoded
//                val myWriter = FileWriter("ExperimentResults\\" + datasetKey + "\\" + datasetKey + "_meta.txt")
//                for (e in cameraMetaDataToWrite){
//                    myWriter.write(e.toString() + "\n")
//                }
//
//                myWriter.close()
//
//            } catch (ex : Exception) {
//                println("Error writing meta-data.")
//                println(ex.message)
//            }
//            println("Finished writing dataset: $count frames")
//        }
//    }

    /**
     * Write a trace from ROI dataset
     * @param traceFromROIDatasetKey The name of the dataset
     */
//    private fun writeTraceFromROIDataset(traceFromROIDatasetKey : String){
//        if (GUIMain.experimentService.expConfig.HDF5Save) {
//            GUIMain.loggerService.log(Level.INFO, "Writing trace from ROI data to h5 file")
//            println("traceROI " + traceFromROIDatasetKey)
//
//            for (traceFromROIDatasetToUse in traceFromROIDatasets) {
//                if (traceFromROIDatasetToUse.description.name == traceFromROIDatasetKey) {
//                    val traceFromROIDataToWrite =
//                        traceFromROIData[traceFromROIDatasetKey]!!.map { x -> x.roiVal.toDouble() }.toDoubleArray()
//
////                    traceFromROIDatasetToUse.use{
////                        it.write(PlanarCoordinate(0),DataType.from(traceFromROIDataToWrite, longArrayOf(traceFromROIDataToWrite.size.toLong())))
////                    }
//                    traceFromROIDatasetToUse.write(
//                        PlanarCoordinate(0),
//                        DataType.from(traceFromROIDataToWrite, longArrayOf(traceFromROIDataToWrite.size.toLong())))
//                }
//                if (traceFromROIDatasetKey  + "_timeAcquired" == traceFromROIDatasetToUse.description.name) {
//                    val traceFromROIDataToWrite1 = traceFromROIData[traceFromROIDatasetKey]?.map{ x -> x.timeAcquired.toDouble()}?.toDoubleArray()
//                    if (traceFromROIDataToWrite1 != null) {
////                        traceFromROIDatasetToUse.use{
////                            it.write(PlanarCoordinate(0),
////                                DataType.from(traceFromROIDataToWrite1, longArrayOf(traceFromROIDataToWrite1.size.toLong())))
////                        }
//                        traceFromROIDatasetToUse.write(
//                            PlanarCoordinate(0),
//                            DataType.from(traceFromROIDataToWrite1, longArrayOf(traceFromROIDataToWrite1.size.toLong())))
//                    }
//                }
//            }
//        }
//        else
//        {
//            try {
//                println("Write " + traceFromROIDatasetKey)
//                //TODO hardcoded
//                val path: Path = Paths.get(".\\ExperimentResults\\" + traceFromROIDatasetKey)
//                Files.createDirectories(path)
//                //TODO hardcoded
//                val myWriter = FileWriter("ExperimentResults\\" + traceFromROIDatasetKey + "\\" + traceFromROIDatasetKey + ".txt")
//                for (e in traceFromROIData[traceFromROIDatasetKey]!!){
//                    myWriter.write(e.timeAcquired.toDouble().toString() + "," + e.roiVal.toDouble().toString() + "\n")
//                }
//                myWriter.close()
//
//            } catch (ex : Exception) {
//                println("Error writing roi-data.")
//                println(ex.message)
//            }
//
//
//        }
//    }

    /**
     * Write a trace dataset
     * @param traceDatasetKey The name of the dataset
     */
//    private fun writeTraceDataset(traceDatasetKey : String){
//        if (GUIMain.experimentService.expConfig.HDF5Save) {
//            GUIMain.loggerService.log(Level.INFO, "Writing trace data to h5 file")
//            try {
//                val traceDataToWrite = traceData[traceDatasetKey]!!.map { x -> x.roiVal.toDouble() }.toDoubleArray()
//                val traceDataToWrite1 = traceData[traceDatasetKey]!!.map{ x -> x.timeAcquired.toDouble()}.toDoubleArray()
//                for (traceDatasetToUse in traceDatasets) {
//                    if (traceDatasetKey == traceDatasetToUse.description.name) {
//                        traceDatasetToUse.use{
//                            it.write(PlanarCoordinate(0),
//                                    DataType.from(traceDataToWrite, longArrayOf(traceDataToWrite.size.toLong())))
//                        }
////                        traceDatasetToUse.write(
////                            PlanarCoordinate(0),
////                            DataType.from(traceDataToWrite, longArrayOf(traceDataToWrite.size.toLong())))
//                    }
//                    if (traceDatasetKey  + "_timeAcquired" == traceDatasetToUse.description.name) {
//                        traceDatasetToUse.use{
//                            it.write(
//                            PlanarCoordinate(0),
//                            DataType.from(traceDataToWrite1, longArrayOf(traceDataToWrite.size.toLong())))
//                        }
////                        traceDatasetToUse.write(
////                            PlanarCoordinate(0),
////                            DataType.from(traceDataToWrite1, longArrayOf(traceDataToWrite.size.toLong()))
////                        )
//                    }
//                }
//            } catch( ex : Exception){
//                println(ex.message)
//            }
//        }
//        else
//        {
//            try {
//                println("Write " + traceDatasetKey)
//                //TODO hardcoded
//                val path: Path = Paths.get(".\\ExperimentResults\\" + traceDatasetKey)
//                Files.createDirectories(path)
//                //TODO hardcoded
//                val myWriter = FileWriter("ExperimentResults\\" + traceDatasetKey + "\\" + traceDatasetKey + ".txt")
//                for (e in traceData[traceDatasetKey]!!){
//                    myWriter.write(e.timeAcquired.toDouble().toString() + "," + e.roiVal.toDouble().toString() + "\n")
//                }
//
//                myWriter.close()
//
//            } catch (ex : Exception) {
//                println("Error writing trace data.")
//                println(ex.message)
//            }
//        }
//    }
}