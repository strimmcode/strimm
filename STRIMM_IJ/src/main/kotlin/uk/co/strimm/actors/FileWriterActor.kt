package uk.co.strimm.actors

import akka.actor.AbstractActor
import akka.actor.Props
import hdf.hdf5lib.H5
import hdf.hdf5lib.HDF5Constants
import uk.co.strimm.Acknowledgement
import uk.co.strimm.CameraMetaDataStore
import uk.co.strimm.ExperimentConstants.Acquisition.Companion.TIMES_DATASET_SUFFIX
import uk.co.strimm.FileExtensions.Companion.H5_FILE
import uk.co.strimm.Paths.Companion.EXPERIMENT_OUTPUT_FOLDER
import uk.co.strimm.Paths.Files.Companion.ACQUISITION_FILE_PREFIX
import uk.co.strimm.TraceDataStore
import uk.co.strimm.actors.messages.Message
import uk.co.strimm.actors.messages.ask.AskIsWriting
import uk.co.strimm.actors.messages.tell.*
import uk.co.strimm.gui.GUIMain
import uk.co.strimm.services.UIstate
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

    var isWriting = false

    override fun createReceive(): Receive {
        return receiveBuilder()
                .match<Message>(Message::class.java) { message ->
                    GUIMain.loggerService.log(Level.INFO, "FileWriter actor received message: $message")
                    sender().tell(Acknowledgement.INSTANCE, self())
                }
                .match<TellCreateFile>(TellCreateFile::class.java) { createFileMessage ->
                    //TODO functionally this is no longer being used. Need to decide if a file should be created
                    //at the beginning of an acquisition or when one has finished
                    GUIMain.loggerService.log(Level.INFO, "FileWriter actor received message to create file")
                    sender().tell(Acknowledgement.INSTANCE, self())
                }
                .match<AskIsWriting>(AskIsWriting::class.java){
                    sender().tell(isWriting, self())
                }
                .match<TellNewCameraDataStore>(TellNewCameraDataStore::class.java){ message ->
                    expectedNumCamDatasets++
                    GUIMain.loggerService.log(Level.INFO, "Expected number of camera datasets is now: $expectedNumCamDatasets")
                }
                .match<TellNewTraceDataStore>(TellNewTraceDataStore::class.java){ message ->
                    expectedNumTraceDatasets++
                    GUIMain.loggerService.log(Level.INFO, "Expected number of trace datasets is now: $expectedNumCamDatasets")
                }
                .match<TellNewTraceROIDataStore>(TellNewTraceROIDataStore::class.java){ message ->
                    expectedNumTraceROIDatasets++
                    GUIMain.loggerService.log(Level.INFO, "Expected number of trace from ROI datasets is now: $expectedNumCamDatasets")
                }
                .match<TellCameraData>(TellCameraData::class.java) { images ->
                    GUIMain.loggerService.log(Level.INFO, "FileWriterActor received camera data from ${sender.path().name()}")
                    val sourceCamera = images.dataName
                    cameraData[sourceCamera] = images.imageStore
                    cameraMetaData[sourceCamera] = images.metaData
                    numCamDatasetsReceived++

                    if(allDataReceived()){
                        GUIMain.loggerService.log(Level.INFO, "Received all expected datasets")
                        GUIMain.loggerService.log(Level.INFO, "Number of expected camera datasets: $expectedNumCamDatasets, number of received camera datasets: $numCamDatasetsReceived")
                        GUIMain.loggerService.log(Level.INFO, "Number of expected trace ROI datasets: $expectedNumTraceROIDatasets, number of received trace ROI datasets: $numTraceROIDatasetsReceived")
                        GUIMain.loggerService.log(Level.INFO, "Number of expected trace datasets: $expectedNumTraceDatasets, number of received trace datasets: $numTraceDatasetsReceived")

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
                    /**
                     * Separate out the List<TraceDataStore> for ROI and non ROI
                     * store in traceFromROIData and traceData which are: HashMap<String, List<TraceDataStore>>
                     * When all of the datasets have been received, the strimm is switched back to Preview mode
                     * and the datasets are saved as hdf5 files.
                     */
                    GUIMain.loggerService.log(Level.INFO, "FileWriterActor received trace data")
                    if(tracePoints.isTraceFromROI){
                        numTraceROIDatasetsReceived++

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
                        numTraceDatasetsReceived++

                        //TODO hardcoded trace ROI index
                        traceData[tracePoints.traceData[0].roi!!.name + numTraceDatasetsReceived] = tracePoints.traceData
                    }

                    if(allDataReceived()){
                        GUIMain.loggerService.log(Level.INFO, "Received all expected datasets")
                        GUIMain.loggerService.log(Level.INFO, "Number of expected camera datasets: $expectedNumCamDatasets, number of received camera datasets: $numCamDatasetsReceived")
                        GUIMain.loggerService.log(Level.INFO, "Number of expected trace ROI datasets: $expectedNumTraceROIDatasets, number of received trace ROI datasets: $numTraceROIDatasetsReceived")
                        GUIMain.loggerService.log(Level.INFO, "Number of expected trace datasets: $expectedNumTraceDatasets, number of received trace datasets: $numTraceDatasetsReceived")

                        createBasicHDFFile()

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
        return (numCamDatasetsReceived == expectedNumCamDatasets) &&
                (numTraceDatasetsReceived == expectedNumTraceDatasets) &&
                (numTraceROIDatasetsReceived == expectedNumTraceROIDatasets)
    }

    private fun clearDatasetsAndReset(){
        GUIMain.loggerService.log(Level.INFO, "Clearing data and resetting akka stream")
        cameraData = hashMapOf()
        cameraMetaData = hashMapOf()

        traceData = hashMapOf()
        traceFromROIData = hashMapOf()

        numCamDatasetsReceived = 0
        numTraceDatasetsReceived = 0
        numTraceROIDatasetsReceived = 0
        expectedNumCamDatasets = 0
        expectedNumTraceDatasets = 0
        expectedNumTraceROIDatasets = 0

        resetStream()
    }

    /**
     * Reset analogue data stream classes so preview mode can be resumed after an experiment has finished.
     */
    private fun resetStream(){
        GUIMain.loggerService.log(Level.INFO, "Resetting akka stream")
        //When you run an experiment, it is assumed you will always want to save to file (otherwise you'd use preview mode)
        //Therefore we can safely assume that this file writer actor will be created and therefore the call to
        //resetAnalogueDataStreams() will be made

        //GUIMain.timerService.resetAnalogueDataStreams() //This line was when using Elliot's code. Keep for now
        GUIMain.strimmUIService.state = UIstate.PREVIEW //TODO temporarily here until STRIMM actor takes control of states
        val stopSuccess = GUIMain.experimentService.stopStream()
        if (stopSuccess){
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
        var fname = "$EXPERIMENT_OUTPUT_FOLDER/$ACQUISITION_FILE_PREFIX"
        val timeStampFull =
                ZonedDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME).replace(":", "-")
        val timeStamp =
                timeStampFull.substring(0, timeStampFull.length - 4) //Trim the end to not include milliseconds
        fname += timeStamp
        fname += H5_FILE
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
            GUIMain.loggerService.log(Level.SEVERE, "Failed to close h5 file. Message: ${ex.message}")
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
        try {
            H5.H5Gclose(datasetGroupID)
        }
        catch(ex: Exception){
            GUIMain.loggerService.log(Level.SEVERE, "Error closing HDF group using H5GClose. Message: ${ex.message}")
            GUIMain.loggerService.log(Level.SEVERE, ex.stackTrace)
        }
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
    }

    private fun writeCameraDataset(datasetKey: String){
        if (GUIMain.experimentService.expConfig.HDF5Save) {
            GUIMain.loggerService.log(Level.INFO, "Writing camera data to HDF5 file")
            if (datasetKey in cameraData.keys) {
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

                    val timingDatasetGroupID = createHDFDatasetGroup(datasetKey + TIMES_DATASET_SUFFIX)
                    if(timingDatasetGroupID >= 0) {
                        val cameraTimingData = cameraMetaData[datasetKey]!!.map { y -> y.timeAcquired.toDouble() }.toDoubleArray()
                        val timingNumDims = 2
                        val timingDims = longArrayOf(cameraTimingData.size.toLong(), 1)
                        GUIMain.loggerService.log(Level.INFO, "Writing dataset ${datasetKey + TIMES_DATASET_SUFFIX}")
                        writeTimingHDFDataset(timingDatasetGroupID, cameraTimingData, HDF5Constants.H5T_NATIVE_DOUBLE, timingNumDims, timingDims, "Times")
                        closeHDFDatasetGroup(timingDatasetGroupID)
                    }
                    else{
                        GUIMain.loggerService.log(Level.WARNING, "Could not create group for dataset ${datasetKey + TIMES_DATASET_SUFFIX}")
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

                    val timingDatasetGroupID = createHDFDatasetGroup(datasetKey + TIMES_DATASET_SUFFIX)
                    if(timingDatasetGroupID >= 0) {
                        val cameraTimingData = cameraMetaData[datasetKey]!!.map { y -> y.timeAcquired.toDouble() }.toDoubleArray()
                        val timingNumDims = 2
                        val timingDims = longArrayOf(cameraTimingData.size.toLong(), 1)
                        GUIMain.loggerService.log(Level.INFO, "Writing dataset $datasetKey$TIMES_DATASET_SUFFIX")
                        writeTimingHDFDataset(timingDatasetGroupID, cameraTimingData, HDF5Constants.H5T_NATIVE_DOUBLE, timingNumDims, timingDims, "Times")
                        closeHDFDatasetGroup(timingDatasetGroupID)
                    }
                    else{
                        GUIMain.loggerService.log(Level.WARNING, "Could not create group for dataset $datasetKey$TIMES_DATASET_SUFFIX")
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

                    val timingDatasetGroupID = createHDFDatasetGroup(datasetKey + TIMES_DATASET_SUFFIX)
                    if(timingDatasetGroupID >= 0) {
                        val cameraTimingData = cameraMetaData[datasetKey]!!.map { y -> y.timeAcquired.toDouble() }.toDoubleArray()
                        val timingNumDims = 2
                        val timingDims = longArrayOf(cameraTimingData.size.toLong(), 1)
                        GUIMain.loggerService.log(Level.INFO, "Writing dataset $datasetKey$TIMES_DATASET_SUFFIX")
                        writeTimingHDFDataset(timingDatasetGroupID, cameraTimingData, HDF5Constants.H5T_NATIVE_DOUBLE, timingNumDims, timingDims, "Times")
                        closeHDFDatasetGroup(timingDatasetGroupID)
                    }
                    else{
                        GUIMain.loggerService.log(Level.WARNING, "Could not create group for dataset $datasetKey$TIMES_DATASET_SUFFIX")
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

            val timingDatasetGroupID = createHDFDatasetGroup(datasetKey + TIMES_DATASET_SUFFIX)
            if(timingDatasetGroupID >= 0) {
                val traceTimingDataToWrite = traceData[datasetKey]!!.map { x -> x.timeAcquired.toDouble() }.toDoubleArray()
                val numDims = 2
                val timingDims = longArrayOf(traceTimingDataToWrite.size.toLong(), 1)
                GUIMain.loggerService.log(Level.INFO, "Writing dataset $datasetKey$TIMES_DATASET_SUFFIX")
                writeTraceHDFDataset(timingDatasetGroupID, traceTimingDataToWrite, HDF5Constants.H5T_NATIVE_DOUBLE, numDims, timingDims, "Values")
                closeHDFDatasetGroup(timingDatasetGroupID)
            }
            else{
                GUIMain.loggerService.log(Level.WARNING, "Could not create group for dataset $datasetKey$TIMES_DATASET_SUFFIX")
            }
        }
    }
}