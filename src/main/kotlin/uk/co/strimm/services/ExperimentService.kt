package uk.co.strimm.services

import akka.NotUsed
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.pattern.PatternsCS
import akka.stream.javadsl.RunnableGraph
import com.google.gson.GsonBuilder
import hdf.hdf5lib.H5
import hdf.hdf5lib.HDF5Constants
import javafx.application.Platform
import net.imagej.ImageJService
import org.scijava.plugin.Plugin
import org.scijava.service.AbstractService
import org.scijava.service.Service
import uk.co.strimm.HDFImageDataset
import uk.co.strimm.RoiInfo
import uk.co.strimm.actors.messages.stop.TerminateActor
import uk.co.strimm.actors.messages.tell.TellSaveDatasets
import uk.co.strimm.experiment.*
import uk.co.strimm.gui.*
import uk.co.strimm.streams.ExperimentStream
import java.io.File
import java.io.FileReader
import java.util.concurrent.CompletionStage
import java.util.logging.Level

@Plugin(type = Service::class)
class ExperimentService  : AbstractService(), ImageJService {
    var expConfig = ExperimentConfiguration() // a class manifestation of the elements in the JSON eg Sink, Flow, Source objects
    private val gson = GsonBuilder().setPrettyPrinting().create() //allows JSONs to be turned into expConfig
    var loadedConfigurationStream: RunnableGraph<NotUsed>? = null //handle to the runnable graph (which means that it can be started)
    var isStreamLoaded = false //stream graph is now loaded
    var isDataStored = false //flag to say is this json needs to save data
    var loadedExperimentConfigurationFile: File? = null //File reference for the currently selected/loaded JSON
    var EndExperimentAndSaveData = false //flag used to trigger the saving of data in acquisition mode. Datastore have a function CheckIfIShouldStop() which used this flag.
    lateinit var experimentStream: ExperimentStream //performs the akka-level tasks

    var loadtimeRoiList = hashMapOf<String, List<RoiInfo>>()
    var runtimeRoiList = hashMapOf<String, List<RoiInfo>>()

    val imageHDFDatasets = hashMapOf<String, ArrayList<HDFImageDataset>>()
    val traceHDFDatasets = hashMapOf<String, Array<FloatArray>>() //<name as path, data>


    //convertGsonToConfig()    destroy the existing stream, capture the configFile, then load the expConfig from the JSON
    fun convertGsonToConfig(configFile: File): Boolean {
        destroyStream()
        loadedExperimentConfigurationFile = configFile
        return try {
            expConfig = gson.fromJson(FileReader(configFile), ExperimentConfiguration::class.java)
            true
        } catch (ex: Exception) {
            GUIMain.loggerService.log(
                Level.SEVERE,
                "Failed to load experiment configuration ${configFile.absolutePath}, check file is present and syntax is correct"
            )
            GUIMain.loggerService.log(Level.SEVERE, "Error message: ${ex.message}")
            false
        }
    }
    //createExperimentStream()     create the experimentStream - which contols akka components, also init protocolService
    fun createExperimentStream() {
        experimentStream = ExperimentStream(expConfig)
    }
    //createStreamGraph()    use experimentStream to createStream from expConfig, flags to show stream is loaded, and that data needs to be stored
    fun createStreamGraph(): Boolean {
        //Shutdown Arduino ports which may have been left open when a previous graph automatically loaded - but was not run
        GUIMain.protocolService.ARDUINO_Shutdown_All()


        //
        val streamGraph = experimentStream.createStream(expConfig)
        return if (streamGraph != null) {
            loadedConfigurationStream = streamGraph
            isStreamLoaded = true
            //TODO is this needed?
            isDataStored =
                experimentStream.cameraDataStoreActors.keys.size != 0 || experimentStream.traceDataStoreActors.keys.size != 0
            true
        } else {
            GUIMain.loggerService.log(Level.SEVERE, "Failed to create Experiment stream")
            isStreamLoaded = false
            false
        }
    }
    //runStream()   set the AQM::bCamerasAcquire so all sources start, then runStream in experimentStream()
    fun runStream() {
       // GUIMain.acquisitionMethodService.bCamerasAcquire = true
        experimentStream.runStream()
    }
    //stopStream()   set AQM::bCamerasAquire to false to stop sources. Switch the killswitch, set experimentStream::isRunning to false
    //stop the acquisition of each camera and destroyStream() - which will destroy all of the actors and docking windows.
    fun stopStream(): Boolean {
        var future =  PatternsCS.ask(GUIMain.actorService.fileManagerActor, TellSaveDatasets(), 500000) as CompletionStage<Unit>
        future.toCompletableFuture().get()

        return try {
            GUIMain.experimentService.experimentStream.isRunning = false
            //specific shutdown behaviour
            experimentStream.sourceMethods.values.forEach{
                it.postStop()
            }
            experimentStream.flowMethods.values.forEach{
                it.postStop()
            }
            experimentStream.sinkMethods.values.forEach{
                it.postStop()
            }

            destroyStream()
            true
        } catch (ex: Exception) {
            GUIMain.loggerService.log(Level.SEVERE, "Error in stopping Akka stream")
            GUIMain.loggerService.log(Level.SEVERE, ex.stackTrace)
            false
        }
    }
    //destroyStream()    if isStreamLoaded, destroy camera, trace, histogram etc actors along with datastoe actors.
    //destroy fileWriter and main actor, close all docking windows, and then close the actorSystem.
    //Closing the actorSystem will also shutdown any akka-stream components. The flag isStreamLoaded is then set to false.
    fun destroyStream() {
        if (isStreamLoaded) {
            GUIMain.experimentService.experimentStream.cameraActors.forEach { x ->
                x.key.tell(TerminateActor(), ActorRef.noSender())
            }
            GUIMain.actorService.allActors.keys.removeAll(GUIMain.experimentService.experimentStream.cameraActors.keys)
            GUIMain.actorService.mainActor.tell(TerminateActor(), ActorRef.noSender())
            GUIMain.actorService.removeActor(GUIMain.actorService.mainActor)
            GUIMain.actorService.allActors.forEach { x ->
                x.key.tell(TerminateActor(), ActorRef.noSender())
            }
            GUIMain.actorService.allActors.keys.removeAll(GUIMain.actorService.allActors.keys)
            val cameraWindowPlugins =
                GUIMain.dockableWindowPluginService.getPluginsOfType(CameraWindowPlugin::class.java)
            cameraWindowPlugins.forEach { x -> x.value.close() }
            val traceWindowPlugins =
                GUIMain.dockableWindowPluginService.getPluginsOfType(TraceWindowPlugin::class.java)
            traceWindowPlugins.forEach { x -> x.value.close() }
            val histogramWindowPlugins =
                GUIMain.dockableWindowPluginService.getPluginsOfType(HistogramWindowPlugin::class.java)
            histogramWindowPlugins.forEach { x -> x.value.close() }
            if (GUIMain.actorService.actorSystem != null) {
                val actorSystem = GUIMain.actorService.actorSystem as ActorSystem
                actorSystem.terminate()
            }
            isStreamLoaded = false
        }
    }

    fun loadH5File(path : String, name: String){
        GUIMain.loggerService.log(Level.INFO, "Loading H5 file...")
        //TODO find a way of getting the group and dataset names programmatically instead of hardcoding
        val sinkGroupString = "retigaSave"
        val lowerLevelGroupString = "0"
        val traceFolderGroupString = "traceData"
        val imageFolderGroupString = "imageData"
        val traceDatasetString = "data"

        try {
            val fileID = H5.H5Fopen("$path/$name", HDF5Constants.H5F_ACC_RDWR, HDF5Constants.H5P_DEFAULT)
            if(fileID <= 0){
                throw Exception("Could not open HDF file $path/$name")
            }

            val sinkGroupID = H5.H5Gopen(fileID, sinkGroupString, HDF5Constants.H5P_DEFAULT)
            if(sinkGroupID <= 0){
                throw Exception("Could not find HDF group $sinkGroupString")
            }

            val lowerLevelGroupID = H5.H5Gopen(sinkGroupID, lowerLevelGroupString, HDF5Constants.H5P_DEFAULT)
            if(lowerLevelGroupID <= 0){
                throw Exception("Could not find HDF group $lowerLevelGroupString")
            }

            val imageDataFolderID = H5.H5Gopen(lowerLevelGroupID, imageFolderGroupString, HDF5Constants.H5P_DEFAULT)
            if(imageDataFolderID <= 0){
                throw Exception("Could not find HDF group $imageFolderGroupString")
            }

            val traceDataFolderID = H5.H5Gopen(lowerLevelGroupID, traceFolderGroupString, HDF5Constants.H5P_DEFAULT)
            if(traceDataFolderID <= 0){
                throw Exception("Could not find HDF group $traceFolderGroupString")
            }

            val traceDatasetHashMap = hashMapOf<String, Int>()
            val traceDatasetID = H5.H5Dopen(traceDataFolderID, traceDatasetString, HDF5Constants.H5P_DEFAULT)
            if(traceDatasetID <= 0){
                throw Exception("Could not find HDF dataset $traceDatasetString")
            }
            else{
                traceDatasetHashMap[traceDatasetString] = traceDatasetID
            }

            if(traceDatasetHashMap.size > 0) {
                readTraceDatasets(traceDatasetHashMap)
                createTracePlugins(traceDataFolderID)
            }

            val cameraDatasetHashMap = hashMapOf<Int, Int>() //Frame number, datasetID
            val numImages = H5.H5Gn_members(lowerLevelGroupID, imageFolderGroupString)
            val range = (0 until numImages).toList()
            for(i in 0 until range.size){
                val frameIndex = range[i]
                val cameraDatasetID = H5.H5Dopen(imageDataFolderID, frameIndex.toString(), HDF5Constants.H5P_DEFAULT)
                if(cameraDatasetID <= 0){
                    throw Exception("Could not find HDF image dataset in $lowerLevelGroupID/$imageFolderGroupString/${range[i]}")
                }
                else{
                    cameraDatasetHashMap[frameIndex] = cameraDatasetID
                }
            }

            if(cameraDatasetHashMap.size > 0) {
                readImageDatasets(cameraDatasetHashMap, imageDataFolderID)
                createImagePlugins()
            }
        }
        catch(ex : Exception){
            GUIMain.loggerService.log(Level.SEVERE, "Failed to load h5 file")
            GUIMain.loggerService.log(Level.SEVERE, ex.message!!)
            GUIMain.loggerService.log(Level.SEVERE, ex.stackTrace)
        }
    }

    fun readImageDatasets(datasetIDs : HashMap<Int, Int>, parentFolderID : Int){
        val bitDepthAttrString = "bitDepth"

        val allImages = ArrayList<HDFImageDataset>()
        for(datasetName in datasetIDs.keys){
            try{
                if(datasetName % 100 == 0){
                    GUIMain.loggerService.log(Level.INFO, "Read $datasetName images")
                }

                val datasetID = datasetIDs[datasetName]!!
                val datasetSpace = H5.H5Dget_space(datasetID)

                val folderInfo = H5.H5Oget_info(parentFolderID)
                val numAttributes = folderInfo.num_attrs
                var bitDepth = 8
                //Iterate through the attributes on the "imageData" folder and find the "bitDepth" attribute
                for(i in 0 until numAttributes) {
                    val attributeID = H5.H5Aopen_by_idx(
                        parentFolderID, ".", HDF5Constants.H5_INDEX_CRT_ORDER,
                        HDF5Constants.H5_ITER_NATIVE, i, HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT)

                    val attributeFullName = H5.H5Aget_name(attributeID) //This will return a string "<index> <attribute name>" e.g. "0 times"
                    val attributeInfo = attributeFullName.split(" ")
                    val name = attributeInfo[1].replace("\'", "")
                    if(name.toLowerCase() == bitDepthAttrString.toLowerCase()){
                        val attributeValue = IntArray(1)
                        H5.H5Aread(attributeID, HDF5Constants.H5T_NATIVE_INT32, attributeValue)
                        bitDepth = attributeValue.first()
                    }
                }

                when(bitDepth){
                    8 -> {
                        //TODO fully test data with 8bit bitdepth
                        val dataTypeConstant = HDF5Constants.H5T_NATIVE_B8

                        val dims = LongArray(3) //Assumption that this will only ever be 2D (reasonable)
                        val maxDims = LongArray(3) //Don't see a need to use this but needed for method call
                        H5.H5Sget_simple_extent_dims(datasetSpace, dims, maxDims) //Populates dims and maxDims

                        /**
                         * Image data is actually 3D in the file (1 x w x h) but reading into a 3D array is too slow
                         * Reading into a 1D array instead is much faster. We can then process the data into 2D arrays
                         * for images later
                         */
                        val dataRead = ByteArray(dims[0].toInt()*dims[1].toInt()*dims[2].toInt())
                        H5.H5Dread(datasetID, dataTypeConstant, HDF5Constants.H5S_ALL, HDF5Constants.H5S_ALL, HDF5Constants.H5P_DEFAULT, dataRead)

                        val image = HDFImageDataset()
                        image.bitDepth = 8
                        image.byteData = dataRead
                        image.frameNumber = datasetName
                        image.width = dims[1].toInt()
                        image.height = dims[2].toInt()
                        allImages.add(image)
                    }
                    16 ->{
                        val dataTypeConstant = HDF5Constants.H5T_NATIVE_SHORT
                        val dims = LongArray(3) //Assumption that this will only ever be 2D (reasonable)
                        val maxDims = LongArray(3) //Don't see a need to use this but needed for method call
                        H5.H5Sget_simple_extent_dims(datasetSpace, dims, maxDims) //Populates dims and maxDims

                        /**
                         * Image data is actually 3D in the file (1 x w x h) but reading into a 3D array is too slow
                         * Reading into a 1D array instead is much faster. We can then process the data into 2D arrays
                         * for images later
                         */
                        val dataRead = ShortArray(dims[0].toInt()*dims[1].toInt()*dims[2].toInt())
                        H5.H5Dread(datasetID, dataTypeConstant, HDF5Constants.H5S_ALL, HDF5Constants.H5S_ALL, HDF5Constants.H5P_DEFAULT, dataRead)

                        val image = HDFImageDataset()
                        image.bitDepth = 16
                        image.shortData = dataRead
                        image.frameNumber = datasetName
                        image.width = dims[1].toInt()
                        image.height = dims[2].toInt()
                        allImages.add(image)
                    }
                    32 -> {
                        //TODO fully test data with 32bit bitdepth
                        val dataTypeConstant = HDF5Constants.H5T_NATIVE_FLOAT
                        val dims = LongArray(3) //Assumption that this will only ever be 2D (reasonable)
                        val maxDims = LongArray(3) //Don't see a need to use this but needed for method call
                        H5.H5Sget_simple_extent_dims(datasetSpace, dims, maxDims) //Populates dims and maxDims

                        /**
                         * Image data is actually 3D in the file (1 x w x h) but reading into a 3D array is too slow
                         * Reading into a 1D array instead is much faster. We can then process the data into 2D arrays
                         * for images later
                         */
                        val dataRead = FloatArray(dims[0].toInt()*dims[1].toInt()*dims[2].toInt())
                        H5.H5Dread(datasetID, dataTypeConstant, HDF5Constants.H5S_ALL, HDF5Constants.H5S_ALL, HDF5Constants.H5P_DEFAULT, dataRead)

                        val image = HDFImageDataset()
                        image.bitDepth = 32
                        image.floatData = dataRead
                        image.frameNumber = datasetName
                        image.width = dims[1].toInt()
                        image.height = dims[2].toInt()
                        allImages.add(image)
                    }
                    else -> {
                        GUIMain.loggerService.log(Level.WARNING, "Bit depth of image data not recognised")
                    }
                }
            }
            catch(ex : Exception){
                GUIMain.loggerService.log(Level.SEVERE, "Failed to read image dataset $datasetName. Message: ${ex.message}")
                GUIMain.loggerService.log(Level.SEVERE, ex.stackTrace)
            }
        }

        imageHDFDatasets[parentFolderID.toString()] = allImages
    }

    fun readTraceDatasets(datasetIDs : HashMap<String, Int>){
        for(datasetName in datasetIDs.keys){
            GUIMain.loggerService.log(Level.INFO, "Reading trace dataset $datasetName")
            try{
                val datasetID = datasetIDs[datasetName]!!
                val datasetSpace = H5.H5Dget_space(datasetID)

                var dataTypeConstant = H5.H5Sget_simple_extent_type(datasetSpace)
                //Add to the when statement below if the datatype is expected to be something other than float
                when(dataTypeConstant){
                    HDF5Constants.H5T_FLOAT -> { dataTypeConstant = HDF5Constants.H5T_NATIVE_FLOAT}
                }

                val dims = LongArray(2) //Assumption that this will only ever be 2D (reasonable)
                val maxDims = LongArray(2) //Don't see a need to use this but needed for method call
                H5.H5Sget_simple_extent_dims(datasetSpace, dims, maxDims) //Populates dims and maxDims
                val dataRead : Array<FloatArray> = Array(dims[0].toInt()) { FloatArray(dims[1].toInt())} //Column, then row
                H5.H5Dread(datasetID, dataTypeConstant, HDF5Constants.H5S_ALL, HDF5Constants.H5S_ALL, HDF5Constants.H5P_DEFAULT, dataRead)

                traceHDFDatasets[datasetName] = dataRead
            }
            catch(ex : Exception){
                GUIMain.loggerService.log(Level.SEVERE, "Failed to read trace dataset $datasetName. Message: ${ex.message}")
                GUIMain.loggerService.log(Level.SEVERE, ex.stackTrace)
            }
        }
    }

    fun createImagePlugins(){
        for(imageDataset in imageHDFDatasets){
            val plugin = GUIMain.dockableWindowPluginService.createPlugin(CameraScrollWindowPlugin::class.java, imageDataset, false, "TraceScrollWindow")
            plugin.dock(GUIMain.strimmUIService.dockableControl, GUIMain.strimmUIService.strimmFrame)
            plugin.cameraScrollWindowController.populateImages()
        }
    }

    fun createTracePlugins(traceDataFolderID : Int){
        for(traceDataset in traceHDFDatasets){ //Each entry represents all the trace data from one sink
            val sortedData = sortTraces(traceDataFolderID, traceDataset)
            val plugin = GUIMain.dockableWindowPluginService.createPlugin(TraceScrollWindowPlugin::class.java, sortedData, false, "TraceScrollWindow")
            plugin.dock(GUIMain.strimmUIService.dockableControl, GUIMain.strimmUIService.strimmFrame)
            Platform.runLater {
                plugin.traceWindowController.populateChart() //Has to be done here because plugin initialisation needs to happen before the graph is populated
            }
        }
    }

    fun sortTraces(traceDataFolderID : Int, traceDataset: MutableMap.MutableEntry<String, Array<FloatArray>>) : HashMap<String, FloatArray>{
        val folderInfo = H5.H5Oget_info(traceDataFolderID)
        val numAttributes = folderInfo.num_attrs

        val sortedTraces = hashMapOf<String, FloatArray>()

        for(i in 0 until numAttributes){
            /**
             * H5Aopen_by_idx doesn't open in any particular order so just open as they come and sort them in a hashmap
             * HDF5Constants.H5_INDEX_CRT_ORDER means index on creation order
             * HDF5Constants.H5_ITER_NATIVE means no particular order, whatever is fastest
             * Search for constants here: https://docs.hdfgroup.org/hdf5/develop/_h5public_8h.html
             */
            val attributeID = H5.H5Aopen_by_idx(traceDataFolderID, ".", HDF5Constants.H5_INDEX_CRT_ORDER,
                HDF5Constants.H5_ITER_NATIVE, i, HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT)

            val attributeFullName = H5.H5Aget_name(attributeID) //This will return a string "<index> <attribute name>" e.g. "0 times"
            val attributeInfo = attributeFullName.split(" ")
            val index = attributeInfo[0].toInt() //TODO check type casting
            val name = attributeInfo[1].replace("\'", "")

            val data = traceDataset.value
            val dataForIndex = FloatArray(data.size)
            for(j in 0 until data.size){
                val row = data[j]
                val valueForIndex = row[index]
                dataForIndex[j] = valueForIndex
            }
            sortedTraces[name] = dataForIndex
        }
        return sortedTraces
    }
}