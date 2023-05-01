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

    var imageHDFDatasets = hashMapOf<String, ArrayList<HDFImageDataset>>()
    var traceHDFDatasets = hashMapOf<String, Array<FloatArray>>() //<name as path, data>

    val imageDataGroupName = "imageData"
    val traceDataGroupName = "traceData"
    val datasetString = "data" //The final node will be a dataset called "data"
    var hdfFileID = 0

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

    /**
     * First point of entry for loading an existing experiment from a h5 file. This will read data from the h5 file
     * and create trace and image (camera) plugins to display the data
     * @param path The path to the h5 file
     * @param name The name of the h5 file with the extension
     */
    fun loadH5File(path : String, name: String){
        //First close any existing windows that are open
        closeOpenWindows()

        GUIMain.strimmUIService.showLoadingDataDialog()
        GUIMain.loggerService.log(Level.INFO, "Loading H5 file...")

        val rootPath = "/"

        try {
            //Open the main file
            val fileID = H5.H5Fopen("$path/$name", HDF5Constants.H5F_ACC_RDWR, HDF5Constants.H5P_DEFAULT)
            hdfFileID = fileID
            if(fileID <= 0){
                throw Exception("Could not open HDF file $path/$name")
            }

            //Start at the root and get all children at level below
            val fileAsGroupID = H5.H5Gopen(fileID, "/", HDF5Constants.H5P_DEFAULT)
            val numMembers = H5.H5Gn_members(fileAsGroupID, "/")
            val childNames = arrayOfNulls<String>(numMembers)
            val lTypes = IntArray(numMembers)
            val childRefs = LongArray(numMembers)
            val childTypes = IntArray(numMembers)
            H5.H5Gget_obj_info_all(fileAsGroupID, ".", childNames, childTypes, lTypes, childRefs, HDF5Constants.H5_INDEX_NAME)

            //Gets the IDs of the groups representing each type of dataset. I.e. the top level group for each trace, and each image dataset
            val groupIDs = getGroupIDs(childNames, rootPath, fileAsGroupID)
            val imageGroupPaths = groupIDs.first
            val traceGroupPaths = groupIDs.second

            //Get all the trace data in the trace groups
            val traceDatasetHashMap = hashMapOf<String, Int>()//<Dataset parent folder path string, node ID>
            for(i in 0 until traceGroupPaths.size){
                val groupNodeID = H5.H5Gopen(fileAsGroupID, traceGroupPaths[i], HDF5Constants.H5P_DEFAULT)
                if(groupNodeID <= 0){
                    throw Exception("Could not find HDF dataset ${traceGroupPaths[i]}")
                }
                else{
                    traceDatasetHashMap[traceGroupPaths[i]] = groupNodeID
                }
            }

            //Create dockable window plugins for valid trace datasets
            if(traceDatasetHashMap.size > 0) {
                readTraceDatasets(fileAsGroupID, traceDatasetHashMap)
                createTracePlugins(traceDatasetHashMap)
            }

            //Get all the image data in the image groups
            val imageDatasetHashMap = hashMapOf<String, Int>()//<Dataset parent folder path string, node ID>
            for(i in 0 until imageGroupPaths.size){
                val groupNodeID = H5.H5Gopen(fileAsGroupID, imageGroupPaths[i], HDF5Constants.H5P_DEFAULT)
                if(groupNodeID <= 0){
                    throw Exception("Could not find HDF dataset ${imageGroupPaths[i]}")
                }
                else{
                    imageDatasetHashMap[imageGroupPaths[i]] = groupNodeID
                }
            }

            if(imageDatasetHashMap.size > 0) {
                readImageDatasets(imageDatasetHashMap, fileAsGroupID)
                createImagePlugins()
            }
        }
        catch(ex : Exception){
            GUIMain.strimmUIService.hideLoadingDataDialog()
            GUIMain.loggerService.log(Level.SEVERE, "Failed to load h5 file")
            GUIMain.loggerService.log(Level.SEVERE, ex.message!!)
            GUIMain.loggerService.log(Level.SEVERE, ex.stackTrace)
        }
    }

    /**
     * Close all open windows. This is called before the h5 file is read and window plugins created
     */
    private fun closeOpenWindows(){
        GUIMain.loggerService.log(Level.INFO, "Closing all open windows")
        val cameraWindowPlugins =
            GUIMain.dockableWindowPluginService.getPluginsOfType(CameraWindowPlugin::class.java)
        cameraWindowPlugins.forEach { x -> x.value.close() }
        val traceWindowPlugins =
            GUIMain.dockableWindowPluginService.getPluginsOfType(TraceWindowPlugin::class.java)
        traceWindowPlugins.forEach { x -> x.value.close() }
    }

    /**
     * Get the group IDs for the image and trace datasets.
     * @param childNames The names of all children (groups) in the first level from the file root
     * @param rootPath The root path like a file, will almost always be "."
     * @param fileAsGroupID The ID of the top level node but read as a group
     * @return A pair containing the paths to the group IDs for the datasets, first is image group IDs, second is trace group IDs
     */
    fun getGroupIDs(childNames : Array<String?>, rootPath : String, fileAsGroupID: Int) : Pair<ArrayList<String>, ArrayList<String>>{
        val imageGroupIDs = arrayListOf<String>()
        val traceGroupIDs = arrayListOf<String>()
        //Determine if children contain image data or trace data
        for(i in 0 until childNames.size){
            val childName = childNames[i]
            val childPath = rootPath + childName
            val isImageDataset = isImageDataset(childPath, fileAsGroupID)
            if(isImageDataset.first){
                imageGroupIDs.add(isImageDataset.second)
            }
            else{
                traceGroupIDs.add(isImageDataset.second)
            }
        }
        return Pair(imageGroupIDs, traceGroupIDs)
    }

    /**
     * Goes through each image dataset and loads all image data with the appropriate bit depth. Doesn't return anything
     * but does populate the global variable imageHDFDatasets
     * @param datasetIDs A hashmap of the dataset IDs, key is the path, value is the ID of the node at the path
     * @param fileAsGroupID The ID of the top level node but read as a group
     */
    fun readImageDatasets(datasetIDs : HashMap<String, Int>, fileAsGroupID: Int){
        val bitDepthAttrString = "bitDepth"

        val allImages = ArrayList<HDFImageDataset>()
        for(datasetName in datasetIDs.keys){
            try{
                val folderID = datasetIDs[datasetName]!!
                val folderInfo = H5.H5Oget_info(folderID)
                val numAttributes = folderInfo.num_attrs
                var bitDepth = 8

                //Iterate through the attributes on the "imageData" folder and find the "bitDepth" attribute
                for(i in 0 until numAttributes) {
                    val attributeID = H5.H5Aopen_by_idx(
                        folderID, ".", HDF5Constants.H5_INDEX_CRT_ORDER,
                        HDF5Constants.H5_ITER_NATIVE, i, HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT)

                    val attributeFullName = H5.H5Aget_name(attributeID) //This will return a string "<index> <attribute name>" e.g. "0 times"
                    val attributeInfo = attributeFullName.split(" ")
                    val name = attributeInfo[1].replace("\'", "")
                    if(name.toLowerCase() == bitDepthAttrString.toLowerCase()){
                        val attributeValue = IntArray(1)
                        H5.H5Aread(attributeID, HDF5Constants.H5T_NATIVE_INT32, attributeValue)
                        bitDepth = attributeValue.first()
                    }

                    H5.H5Aclose(attributeID)
                }

                var dataTypeConstant = HDF5Constants.H5T_NATIVE_B8
                when(bitDepth){
                    8 -> { dataTypeConstant = HDF5Constants.H5T_NATIVE_B8}
                    16 -> { dataTypeConstant = HDF5Constants.H5T_NATIVE_SHORT}
                    32 -> { dataTypeConstant = HDF5Constants.H5T_NATIVE_FLOAT}
                    else -> {GUIMain.loggerService.log(Level.WARNING, "Bit depth of $bitDepth not recognised. Trying with bitDepth=8")}
                }

                val numMembers = H5.H5Gn_members(fileAsGroupID, datasetName)
                for(i in 0 until numMembers){
                    if(i % 100 == 0){
                        GUIMain.loggerService.log(Level.INFO, "Read $i images")
                    }
                    val datasetID = H5.H5Dopen(fileAsGroupID, "$datasetName/$i", HDF5Constants.H5P_DEFAULT)
                    val dims = LongArray(3) //Assumption that this will only ever be 2D (reasonable)
                    val maxDims = LongArray(3) //Don't see a need to use this but needed for method call
                    val datasetSpace = H5.H5Dget_space(datasetID)
                    H5.H5Sget_simple_extent_dims(datasetSpace, dims, maxDims) //Populates dims and maxDims

                    /**
                     * Image data is actually 3D in the file (1 x w x h) but reading into a 3D array is too slow
                     * Reading into a 1D array instead is much faster. We can then process the data into 2D arrays
                     * for images later
                     */
                    val dataRead = ShortArray(dims[0].toInt()*dims[1].toInt()*dims[2].toInt())
                    H5.H5Dread(datasetID, dataTypeConstant, HDF5Constants.H5S_ALL, HDF5Constants.H5S_ALL, HDF5Constants.H5P_DEFAULT, dataRead)

                    val image = HDFImageDataset()
                    image.bitDepth = bitDepth
                    image.shortData = dataRead
                    image.frameNumber = i
                    image.width = dims[1].toInt()
                    image.height = dims[2].toInt()
                    allImages.add(image)

                    H5.H5Dclose(datasetID)
                }

                imageHDFDatasets[datasetName] = allImages
            }
            catch(ex : Exception){
                GUIMain.loggerService.log(Level.SEVERE, "Failed to read image dataset $datasetName. Message: ${ex.message}")
                GUIMain.loggerService.log(Level.SEVERE, ex.stackTrace)
            }
        }
    }

    /**
     * Goes through each trace dataset and loads all trace data. Doesn't return anything but does populate the global
     * variable traceHDFDatasets
     * @param datasetIDs A hashmap of the dataset IDs, key is the path, value is the ID of the node at the path
     * @param fileAsGroupID The ID of the top level node but read as a group
     */
    fun readTraceDatasets(fileAsGroupID : Int, datasetIDs : HashMap<String, Int>){
        for(parentFolderPath in datasetIDs.keys){
            GUIMain.loggerService.log(Level.INFO, "Reading trace dataset $parentFolderPath/$datasetString")
            try{
                //Assumption that a traceData group will only have one child that is a dataset called "data"
                val datasetID = H5.H5Dopen(fileAsGroupID, "$parentFolderPath/$datasetString", HDF5Constants.H5P_DEFAULT)
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

                traceHDFDatasets["$parentFolderPath/$datasetString"] = dataRead

                H5.H5Dclose(datasetID)
            }
            catch(ex : Exception){
                GUIMain.loggerService.log(Level.SEVERE, "Failed to read trace dataset $parentFolderPath/$datasetString. Message: ${ex.message}")
                GUIMain.loggerService.log(Level.SEVERE, ex.stackTrace)
            }
        }
    }

    /**
     * Create dockable window plugins for each image dataset. The plugins for image dataset use CameraScrollWindowPlugin
     * class and are similar to the standard CameraWindowPlugin
     */
    fun createImagePlugins(){
        for(imageDataset in imageHDFDatasets){
            val plugin = GUIMain.dockableWindowPluginService.createPlugin(CameraScrollWindowPlugin::class.java, imageDataset, false, "ImageScrollWindow")
            plugin.dock(GUIMain.strimmUIService.dockableControl, GUIMain.strimmUIService.strimmFrame)
            plugin.cameraScrollWindowController.populateImages()
            GUIMain.strimmUIService.windowsLoaded += 1
            if(GUIMain.strimmUIService.windowsLoaded == (traceHDFDatasets.size + imageHDFDatasets.size)){
                GUIMain.closeAllWindowsExistingExpButton.isEnabled = true
                GUIMain.strimmUIService.hideLoadingDataDialog()
            }
        }
    }

    /**
     * Create dockable window plugins for each trace dataset. The plugins for trace dataset use TraceScrollWindowPlugin
     * class and are similar to the standard TraceWindowPlugin
     */
    fun createTracePlugins(traceDataFolderIDs : HashMap<String, Int>){
        for(traceDataset in traceHDFDatasets){ //Each entry represents all the trace data from one sink
            val parentFolderPath = traceDataset.key.replace("/$datasetString", "")
            val sortedData = sortTraces(traceDataFolderIDs[parentFolderPath]!!, traceDataset)
            val plugin = GUIMain.dockableWindowPluginService.createPlugin(TraceScrollWindowPlugin::class.java, sortedData, false, "TraceScrollWindow")
            plugin.dock(GUIMain.strimmUIService.dockableControl, GUIMain.strimmUIService.strimmFrame)
            Platform.runLater {
                plugin.traceWindowController.populateChart() //Has to be done here because plugin initialisation needs to happen before the graph is populated
                GUIMain.strimmUIService.windowsLoaded += 1
                if(GUIMain.strimmUIService.windowsLoaded == (traceHDFDatasets.size + imageHDFDatasets.size)){
                    GUIMain.closeAllWindowsExistingExpButton.isEnabled = true
                    GUIMain.strimmUIService.hideLoadingDataDialog()
                }
            }
        }
    }

    /**
     * Sorts the trace data read from the trace dataset in the h5 file. This is needed because traces are not read
     * in any particular order
     * @param traceDataParentID The parent folder (group) of the trace data
     * @param traceDataset The trace data as a hashmap entry. Key is path of the trace dataset, value is an array of all trace data
     * @return The trace data and associated names as a HashMap<name, data>
     */
    fun sortTraces(traceDataParentID : Int, traceDataset: MutableMap.MutableEntry<String, Array<FloatArray>>) : HashMap<String, FloatArray>{
        val folderInfo = H5.H5Oget_info(traceDataParentID)
        val numAttributes = folderInfo.num_attrs

        val sortedTraces = hashMapOf<String, FloatArray>()

        for(i in 0 until numAttributes){
            /**
             * H5Aopen_by_idx doesn't open in any particular order so just open as they come and sort them in a hashmap
             * HDF5Constants.H5_INDEX_CRT_ORDER means index on creation order
             * HDF5Constants.H5_ITER_NATIVE means no particular order, whatever is fastest
             * Search for constants here: https://docs.hdfgroup.org/hdf5/develop/_h5public_8h.html
             */
            val attributeID = H5.H5Aopen_by_idx(traceDataParentID, ".", HDF5Constants.H5_INDEX_CRT_ORDER,
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
            H5.H5Aclose(attributeID)
        }
        return sortedTraces
    }

    /**
     * Recursively goes through the children of a group in the HDF5 file. Both trace and image datasets will have two
     * groups - "imageData" and "traceData". In image datasets both of these groups will have children, in trace
     * datasets only the "traceData" group will have children. This method uses that logic to determine if a group
     * contains and image dataset or a trace dataset
     * @param groupNodePath The path to the node representing the top level group. This will come from the name of
     * the sink specified in the experiment config
     * @param fileAsGroupID The ID of the file, but read as a group using H5.H5GOpen()
     * @return Pair<isImageDataset, dataset path>. Key is true if the group node is an image datasets. False if the group node is a trace dataset
     */
    fun isImageDataset(groupNodePath : String, fileAsGroupID : Int) : Pair<Boolean, String>{
        val isImageDataset: Pair<Boolean, String>

        val numMembers = H5.H5Gn_members(fileAsGroupID, groupNodePath)
        if(numMembers == 0){
            GUIMain.loggerService.log(Level.WARNING, "HDF file group has no more members. Group: $groupNodePath")
            return Pair(false, "")
        }

        val childNames = arrayOfNulls<String>(numMembers)
        val lTypes = IntArray(numMembers)
        val childRefs = LongArray(numMembers)
        val childTypes = IntArray(numMembers)
        H5.H5Gget_obj_info_all(fileAsGroupID, groupNodePath, childNames, childTypes, lTypes, childRefs, HDF5Constants.H5_INDEX_NAME)

        if(childNames.size == 2 && childNames.any { x -> x == imageDataGroupName } && childNames.any { x -> x == traceDataGroupName }){
            var numImageDatasetMembers = 0
            var numTraceDatasetMembers = 0
            var imageDatasetPath = ""
            var traceDatasetPath = ""
            for(i in 0 until childNames.size){
                if(childNames[i]!! == imageDataGroupName) {
                    numImageDatasetMembers = H5.H5Gn_members(fileAsGroupID, groupNodePath + "/" + childNames[i])
                    imageDatasetPath = groupNodePath + "/" + childNames[i]
                }

                if(childNames[i]!! == traceDataGroupName) {
                    numTraceDatasetMembers = H5.H5Gn_members(fileAsGroupID, groupNodePath + "/" + childNames[i])
                    traceDatasetPath = groupNodePath + "/" + childNames[i]
                }
            }

            if(numImageDatasetMembers == 0 && numTraceDatasetMembers > 0){
                return Pair(false, traceDatasetPath)
            }

            return Pair(true, imageDatasetPath)
        }
        else{
            isImageDataset = isImageDataset(groupNodePath + "/" + childNames.first(), fileAsGroupID)
        }
        return isImageDataset
    }
}