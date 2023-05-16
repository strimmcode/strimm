package uk.co.strimm.actors

import akka.actor.AbstractActor
import akka.actor.Kill
import akka.actor.Props
import uk.co.strimm.*
import uk.co.strimm.actors.messages.Message
import uk.co.strimm.actors.messages.stop.TerminateActor
import uk.co.strimm.actors.messages.tell.TellSaveDatasets
import uk.co.strimm.gui.GUIMain
import uk.co.strimm.services.UIstate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.logging.Level
import hdf.hdf5lib.H5
import hdf.hdf5lib.HDF5Constants
import uk.co.strimm.actors.messages.ask.*
import uk.co.strimm.actors.messages.tell.TellAllStop
import uk.co.strimm.actors.messages.tell.TellIsSaving
import uk.co.strimm.actors.messages.tell.TellStopReceived


class FileManagerActor : AbstractActor() {
    var file: Int = 0 //handle to the hdf5 file
    var buffers = hashMapOf<String, List<List<STRIMMBuffer>>>()  //each datasource that has send SaveBuffers to the FileManager will have its name added to this dictionary and will save a list of its buffers
    var flush_cnt = 5000 //number of SaveBuffers received before a flush, done per datasource
    var imageDataNumberMap = hashMapOf<String, Long>() //imageDataNumberMap is used to ensure that each datasource has the correct frame number for each buffer saved ie 0,1,2 etc
    var traceDataNumberMap = hashMapOf<String, LongArray>() //traceDataNumberMap keeps a record of the length of each STRIMMBuffer's trace data in each frame which is a List<STRIMMBuffer>
    var isCapturingBuffers = false
    var handles = hashMapOf<String, Int>()
    var isSaving = false
    var stopSignalCount = 0

    companion object {
        fun props(): Props {
            return Props.create<FileManagerActor>(FileManagerActor::class.java) { FileManagerActor() }
        }
    }

    override fun createReceive(): Receive {
        return receiveBuilder()
            .match<Message>(Message::class.java) { message ->
                GUIMain.loggerService.log(Level.INFO, "FileManagerActor received basic message")
            }
            .match<AskInitHDF5File>(AskInitHDF5File::class.java) {
                //TODO open and close the h5 file
                GUIMain.loggerService.log(Level.INFO, "Creating H5 file")
                try {
                    var filename = "strimm_exp"
                    val timeStampFull =
                        ZonedDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME).replace(":", "-")
                    val timeStamp =
                        timeStampFull.substring(0, timeStampFull.length - 4) //Trim the end to not include milliseconds
                    filename += timeStamp
                    filename += ".h5"

                    //create file (which is a long/handle) with defaults
                    file = H5.H5Fcreate(
                        filename,
                        HDF5Constants.H5F_ACC_TRUNC,
                        HDF5Constants.H5P_DEFAULT,
                        HDF5Constants.H5P_DEFAULT
                    )

                    handles["file"] = file
                    GUIMain.loggerService.log(Level.INFO, "Created H5 file $file")
                } catch (ex: Exception) {
                    GUIMain.loggerService.log(Level.SEVERE, "Could not create H5 file")
                    GUIMain.loggerService.log(Level.SEVERE, ex.stackTrace)
                }
            }
            .match<AskShutdownHDF5File>(AskShutdownHDF5File::class.java) {
                //TODO is this used anywhere?
            }
            .match<TerminateActor>(TerminateActor::class.java) {
                GUIMain.loggerService.log(Level.INFO, "FileManagerActor actor ${self.path().name()} terminating")
                self.tell(Kill.getInstance(), self)
            }
            .match<TellStopReceived>(TellStopReceived::class.java) {
                stopSignalCount++
                GUIMain.loggerService.log(Level.INFO, "One stop signal received. StopSignalCount=${stopSignalCount}, Number of buffers=${buffers.size}")
                if(stopSignalCount == buffers.size) {
                    GUIMain.loggerService.log(Level.INFO, "Stopping all cores")
                    GUIMain.experimentService.allMMCores.forEach { x -> x.stopSequenceAcquisition() }
                    GUIMain.loggerService.log(Level.INFO, "Setting UI state to IDLE and saving datasets")
                    GUIMain.strimmUIService.state = UIstate.IDLE
                    val saveSuccess = saveDatasets()
                    if(saveSuccess == 1) {
                        GUIMain.loggerService.log(Level.INFO, "Clicking stop experiment button")
                        GUIMain.stopExperimentButton.doClick()
                    }
                    else{
                        GUIMain.loggerService.log(Level.SEVERE, "Data was not able to be saved")
                    }
                }
            }
            .match<TellIsSaving>(TellIsSaving::class.java){
                //TODO need to check if this can be deleted. Was used as part of circular buffer issue fix
                GUIMain.loggerService.log(Level.INFO, "TellIsSaving $isSaving")
                sender().tell(isSaving, self())
            }
            .match<TellSaveDatasets>(TellSaveDatasets::class.java) {
                //TODO need to check if this can be deleted. Was used as part of circular buffer issue fix. Code moved to separate method "saveDatasets()"
                GUIMain.experimentService.isFileSaving = true
                GUIMain.loggerService.log(Level.INFO, "Stop message received. Saving data...")
                //Only save when in acquisition mode
                //Message received when acquisition is Stopped
//                if (GUIMain.strimmUIService.state == UIstate.ACQUISITION) {
                    //flush remaining data
                    //wait until this flag is set which indicates that no more buffers need to be captured
                    // (some might still be flowing through the akka graph but will be ignored and not saved)
                    while (isCapturingBuffers) {
                        GUIMain.loggerService.log(Level.INFO, "Is capturing buffers")
                        Thread.sleep(100)
                    }

                    //this means that the akkka graph might still be pumping out samples (but they can be ignored)
                    //buffers contains the flush data associated with each data source by name so is a map of String : List<List<STRIMMBuffer>>
                    for (keySz in buffers.keys) {
                        GUIMain.loggerService.log(Level.INFO, "buffer key ${keySz}")
                        //for each data source save the flush data, so this means save the matrix data as a new array and add the vector data onto the previous
                        // TODO change the name of the vector data because it could be a 2D matrix which is appened
                        var data = buffers[keySz]
                        //possible that the experiment has been stopped when there is no data??  TODO check this can it be null?
                        if (data != null) {
                            //println("*******flush******")
                            //save matrix data
                            //the 'outer' list is the frame
                            for (frame in 0..data.size - 1) { //for each frame
                                for (f in 0..data[frame].size - 1) {//go through each image source in List<STRIMMBuffer>
                                    if (data[frame][f].imageData != null) {
                                        var dims = data[frame][f].getImageDataDims()
                                        var type_frm = data[frame][f].getImageDataType()
                                        val dataspaceMatrixData = H5.H5Screate_simple(dims.size, dims, null)
                                        //retrieve the handle for this group
                                        val hand =
                                            handles["group_node_ix_" + keySz + "_" + f.toString() + "_imageData"]!!
                                        println(hand.toString() + "*******************")
                                        //create a dataset
                                        var datasetId = 0
                                        if (type_frm == 0) { //BYTE
                                            type_frm = HDF5Constants.H5T_NATIVE_INT8
                                            datasetId = H5.H5Dcreate(
                                                hand,
                                                imageDataNumberMap[keySz]!!.toString(),
                                                HDF5Constants.H5T_NATIVE_INT8, //TODO
                                                dataspaceMatrixData,
                                                HDF5Constants.H5P_DEFAULT,
                                                HDF5Constants.H5P_DEFAULT,
                                                HDF5Constants.H5P_DEFAULT
                                            )
                                        } else if (type_frm == 1) { //SHORT
                                            type_frm = HDF5Constants.H5T_NATIVE_INT16
                                            datasetId = H5.H5Dcreate(
                                                hand,
                                                imageDataNumberMap[keySz]!!.toString(),
                                                HDF5Constants.H5T_NATIVE_INT16, //TODO
                                                dataspaceMatrixData,
                                                HDF5Constants.H5P_DEFAULT,
                                                HDF5Constants.H5P_DEFAULT,
                                                HDF5Constants.H5P_DEFAULT
                                            )
                                        } else if (type_frm == 2) { //INT
                                            type_frm = HDF5Constants.H5T_NATIVE_INT32
                                            datasetId = H5.H5Dcreate(
                                                hand,
                                                imageDataNumberMap[keySz]!!.toString(),
                                                HDF5Constants.H5T_NATIVE_INT32, //TODO
                                                dataspaceMatrixData,
                                                HDF5Constants.H5P_DEFAULT,
                                                HDF5Constants.H5P_DEFAULT,
                                                HDF5Constants.H5P_DEFAULT
                                            )
                                        } else if (type_frm == 3) { //LONG
                                            type_frm = HDF5Constants.H5T_NATIVE_INT64
                                            datasetId = H5.H5Dcreate(
                                                hand,
                                                imageDataNumberMap[keySz]!!.toString(),
                                                HDF5Constants.H5T_NATIVE_INT64, //TODO
                                                dataspaceMatrixData,
                                                HDF5Constants.H5P_DEFAULT,
                                                HDF5Constants.H5P_DEFAULT,
                                                HDF5Constants.H5P_DEFAULT
                                            )
                                        } else if (type_frm == 4) { //FLOAT
                                            type_frm = HDF5Constants.H5T_NATIVE_FLOAT
                                            datasetId = H5.H5Dcreate(
                                                hand,
                                                imageDataNumberMap[keySz]!!.toString(),
                                                HDF5Constants.H5T_NATIVE_FLOAT, //TODO
                                                dataspaceMatrixData,
                                                HDF5Constants.H5P_DEFAULT,
                                                HDF5Constants.H5P_DEFAULT,
                                                HDF5Constants.H5P_DEFAULT
                                            )
                                        } else if (type_frm == 5) { //DOUBLE
                                            type_frm = HDF5Constants.H5T_NATIVE_DOUBLE
                                            datasetId = H5.H5Dcreate(
                                                hand,
                                                imageDataNumberMap[keySz]!!.toString(),
                                                HDF5Constants.H5T_NATIVE_DOUBLE, //TODO
                                                dataspaceMatrixData,
                                                HDF5Constants.H5P_DEFAULT,
                                                HDF5Constants.H5P_DEFAULT,
                                                HDF5Constants.H5P_DEFAULT
                                            )
                                        }
                                        H5.H5Dwrite(
                                            datasetId, type_frm,
                                            HDF5Constants.H5S_ALL, HDF5Constants.H5S_ALL, HDF5Constants.H5P_DEFAULT,
                                            data[frame][f].imageData
                                        )
                                        imageDataNumberMap[keySz] = imageDataNumberMap[keySz]!! + 1
                                        //close resources
                                        H5.H5Sclose(dataspaceMatrixData)
                                        H5.H5Dclose(datasetId)

                                        //////////////////////////
                                    }
                                    if (data[frame][f].traceData != null) {
                                        var dims = data[frame][f].getTraceDataDims()
                                        val curLength = traceDataNumberMap[keySz]!![f]
                                        val newLength: Long = (curLength + dims[0])
                                        val dataset_append =
                                            handles["dataset_" + keySz + "_traceData_" + f.toString()]!!

                                        H5.H5Dset_extent(dataset_append, longArrayOf(newLength, dims[1]))

                                        val newOffset: Long = curLength
                                        val extLength: Long = dims[0]
                                        var filespace = H5.H5Dget_space(dataset_append)

                                        H5.H5Sselect_hyperslab(
                                            filespace,
                                            HDF5Constants.H5S_SELECT_SET,
                                            longArrayOf(newOffset, 0),
                                            null,
                                            longArrayOf(extLength, dims[1]),
                                            null
                                        )

                                        var memspace = H5.H5Screate_simple(
                                            2,
                                            longArrayOf(extLength, dims[1]),
                                            null
                                        ) //TODO is the rank = 2

                                        H5.H5Dwrite(
                                            dataset_append,
                                            HDF5Constants.H5T_NATIVE_DOUBLE,
                                            memspace,
                                            filespace,
                                            HDF5Constants.H5P_DEFAULT,
                                            data[frame][f].traceData
                                        )

                                        H5.H5Sclose(memspace)
                                        H5.H5Sclose(filespace)
                                        traceDataNumberMap[keySz]!![f] += dims[0]
                                    }
                                }
                            }
                        }
                    }

                    //close handles
                    //TODO closes the handles but does it close the file each time
                    //TODO it might be better to be continuously opening and closing the HDF5 file handle
                    for (ky in handles.keys) {
                        if (ky == "file") {
                            H5.H5Fclose(handles[ky]!!)
                            file = 0
                        } else if (ky.substring(0, 5) == "group") {
                            H5.H5Gclose(handles[ky]!!)
                        } else if (ky.substring(0, 7) == "dataset") {
                            H5.H5Dclose(handles[ky]!!)
                        } else {

                        }
                    }

                    //explicitely clean up
                    handles = hashMapOf<String, Int>()
//                    vec_size_map[keySz] = vec_size_map[keySz]!! + flush_cnt
//                    buffers[keySz] = ArrayList<List<STRIMMBuffer>>()
//                }
                GUIMain.experimentService.isFileSaving = false
                sender().tell(Acknowledgement.INSTANCE, self())
            }
            //most of activity takes place here, creates the group structure and flushes buffers
            .match<STRIMMSaveBuffer>(STRIMMSaveBuffer::class.java) {
//                GUIMain.loggerService.log(Level.INFO, "In STRIMMSaveBuffer")
                //Note the order that data is displayed in HDF5View is not the same as ImageJ
                //so you will see a managled image in HDF5View

                //only save the STRIMMSaveBuffer if in ACQUISITION mode (and not ACQUISITION_PAUSED or PREVIEW)
                if (GUIMain.strimmUIService.state == UIstate.ACQUISITION) {
//                    if(it.data.all { x -> x.status != 0 }) {
                        //set a flag to show that processing a received save-buffer (which is a buffer which has the name of the dataset)
                        isCapturingBuffers = true
//                        GUIMain.loggerService.log(Level.INFO, "IsCapturingBuffers true")
                        if (it.data[0].className == "STRIMMSequenceCameraDataBuffer") {
                            //save a burst of images from a software trigger camera
                            var data = buffers[it.name]
                            if (data != null) {
                                //the dataset is currently registered
                                //add the STRIMMSaveBuffer to buffers
                                //retrieve the List<List<STRIMMBuffer>>
                                data = data as ArrayList<List<STRIMMBuffer>>
                                var dat = it.data[0] as STRIMMSequenceCameraDataBuffer
                                for (f in 0..dat.data.size - 1) {
                                    data.add(listOf<STRIMMBuffer>(dat.data[f]))
                                }

                                //
                                //
                                //is a flush needed when the number of frames == flush_cnt
                                if (data.size == flush_cnt) {
                                    println("*******flush to HDF5******")
                                    //
                                    //
                                    //save matrix data//
                                    for (frame in 0..data.size - 1) {
                                        for (f in 0..data[frame].size - 1) {
                                            //println("******* write dataset")
                                            //the subimages in each frame could have different dims and pixel type
                                            val dims_frm = data[frame][f].getImageDataDims()
                                            if (data[frame][f].imageData != null) {
                                                //imageDataNumberMap is used to ensure that each datasource has the correct frame number for each buffer saved ie 0,1,2 etc
                                                var type_frm = data[frame][f].getImageDataType()
                                                val dataspaceMatrixData =
                                                    H5.H5Screate_simple(dims_frm.size, dims_frm, null)
                                                val hand =
                                                    handles["group_node_ix_" + it.name + "_" + f.toString() + "_imageData"]!!
                                                var datasetId = 0
                                                if (type_frm == 0) { //BYTE
                                                    type_frm = HDF5Constants.H5T_NATIVE_INT8
                                                    datasetId = H5.H5Dcreate(
                                                        hand,
                                                        imageDataNumberMap[it.name]!!.toString(),
                                                        HDF5Constants.H5T_NATIVE_INT8,
                                                        dataspaceMatrixData,
                                                        HDF5Constants.H5P_DEFAULT,
                                                        HDF5Constants.H5P_DEFAULT,
                                                        HDF5Constants.H5P_DEFAULT
                                                    )
                                                } else if (type_frm == 1) { //SHORT
                                                    type_frm = HDF5Constants.H5T_NATIVE_INT16
                                                    datasetId = H5.H5Dcreate(
                                                        hand,
                                                        imageDataNumberMap[it.name]!!.toString(),
                                                        HDF5Constants.H5T_NATIVE_INT16, //TODO
                                                        dataspaceMatrixData,
                                                        HDF5Constants.H5P_DEFAULT,
                                                        HDF5Constants.H5P_DEFAULT,
                                                        HDF5Constants.H5P_DEFAULT
                                                    )
                                                } else if (type_frm == 2) { //INT
                                                    type_frm = HDF5Constants.H5T_NATIVE_INT32
                                                    datasetId = H5.H5Dcreate(
                                                        hand,
                                                        imageDataNumberMap[it.name]!!.toString(),
                                                        HDF5Constants.H5T_NATIVE_INT32, //TODO
                                                        dataspaceMatrixData,
                                                        HDF5Constants.H5P_DEFAULT,
                                                        HDF5Constants.H5P_DEFAULT,
                                                        HDF5Constants.H5P_DEFAULT
                                                    )
                                                } else if (type_frm == 3) { //LONG
                                                    type_frm = HDF5Constants.H5T_NATIVE_INT64
                                                    datasetId = H5.H5Dcreate(
                                                        hand,
                                                        imageDataNumberMap[it.name]!!.toString(),
                                                        HDF5Constants.H5T_NATIVE_INT64, //TODO
                                                        dataspaceMatrixData,
                                                        HDF5Constants.H5P_DEFAULT,
                                                        HDF5Constants.H5P_DEFAULT,
                                                        HDF5Constants.H5P_DEFAULT
                                                    )
                                                } else if (type_frm == 4) { //FLOAT
                                                    type_frm = HDF5Constants.H5T_NATIVE_FLOAT
                                                    datasetId = H5.H5Dcreate(
                                                        hand,
                                                        imageDataNumberMap[it.name]!!.toString(),
                                                        HDF5Constants.H5T_NATIVE_FLOAT, //TODO
                                                        dataspaceMatrixData,
                                                        HDF5Constants.H5P_DEFAULT,
                                                        HDF5Constants.H5P_DEFAULT,
                                                        HDF5Constants.H5P_DEFAULT
                                                    )
                                                } else if (type_frm == 5) { //DOUBLE
                                                    type_frm = HDF5Constants.H5T_NATIVE_DOUBLE
                                                    datasetId = H5.H5Dcreate(
                                                        hand,
                                                        imageDataNumberMap[it.name]!!.toString(),
                                                        HDF5Constants.H5T_NATIVE_DOUBLE, //TODO
                                                        dataspaceMatrixData,
                                                        HDF5Constants.H5P_DEFAULT,
                                                        HDF5Constants.H5P_DEFAULT,
                                                        HDF5Constants.H5P_DEFAULT
                                                    )
                                                }
                                                //
                                                //
                                                H5.H5Dwrite(
                                                    datasetId,
                                                    type_frm,
                                                    HDF5Constants.H5S_ALL,
                                                    HDF5Constants.H5S_ALL,
                                                    HDF5Constants.H5P_DEFAULT,
                                                    data[frame][f].imageData
                                                )

                                                imageDataNumberMap[it.name] = imageDataNumberMap[it.name]!! + 1
                                                H5.H5Sclose(dataspaceMatrixData)
                                                H5.H5Dclose(datasetId)
                                            }
                                            /////////////////////////////////////
                                            /////////////////////////////////////
                                            //directly append the trace matrix in data[frame][f]
                                            //f indexes each STRIMMBuffer within a frame (which is a List<STRIMMBuffer>)
                                            val dims = data[frame][f].getTraceDataDims()
                                            //it is possible that the different STRIMMBuffers in each frame have different lengths of traceData
                                            //each frame will always contain size(List<STRIMMBuffer>) but they might have come from different sources
                                            //and have different lenghts eg the source might be a car and each STRIMMBuffer traceData could be a particular sensor
                                            //might might have recorded a different number of measurements.
                                            //traceDataNumberMap keeps a record of the length of each STRIMMBuffer's trace data
                                            //
                                            //traceDataNumberMap is necessary in order to append in h5
                                            //in order to append new data in h5
                                            val curLength = traceDataNumberMap[it.name]!![f]
                                            val newLength: Long = (curLength + dims[0]).toLong()
                                            val dataset_append =
                                                handles["dataset_" + it.name + "_traceData_" + f.toString()]!!

                                            H5.H5Dset_extent(dataset_append, longArrayOf(newLength, dims[1]))

                                            val newOffset: Long = curLength.toLong()
                                            val extLength: Long = dims[0].toLong()
                                            var filespace = H5.H5Dget_space(dataset_append)


                                            H5.H5Sselect_hyperslab(
                                                filespace,
                                                HDF5Constants.H5S_SELECT_SET,
                                                longArrayOf(newOffset, 0),
                                                null,
                                                longArrayOf(extLength, dims[1]),
                                                null
                                            )
                                            var memspace = H5.H5Screate_simple(
                                                2,
                                                longArrayOf(extLength, dims[1]),
                                                null
                                            ) //TODO is the rank = 2
                                            H5.H5Dwrite(
                                                dataset_append,
                                                HDF5Constants.H5T_NATIVE_DOUBLE,
                                                memspace,
                                                filespace,
                                                HDF5Constants.H5P_DEFAULT,
                                                data[frame][f].traceData
                                            )
                                            H5.H5Sclose(memspace)
                                            H5.H5Sclose(filespace)
                                            traceDataNumberMap[it.name]!![f] += dims[0] //update traceDataNumberMap to allow for the appended data
                                        }
                                        buffers[it.name] = ArrayList<List<STRIMMBuffer>>()
                                    }
                                }
                            } else {
                                //NEW DATA SOURCE this is where all of the new groups etc are defined.
                                //
                                //
                                if (file > 0) { //make sure have true h5 file handle
                                    //println("***new data source " + it.name)
                                    buffers[it.name] = ArrayList<List<STRIMMBuffer>>()
                                    imageDataNumberMap[it.name] = 0
                                    //add this frame
                                    val data = buffers[it.name] as ArrayList<List<STRIMMBuffer>>
                                    var dat = it.data[0] as STRIMMSequenceCameraDataBuffer
                                    for (f in 0..dat.data.size - 1) {
                                        data.add(listOf<STRIMMBuffer>(dat.data[f]))
                                    }
                                    //
                                    val group_node = H5.H5Gcreate(
                                        file, it.name, HDF5Constants.H5P_DEFAULT,
                                        HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT
                                    )
                                    handles["group_" + it.name + "_node"] = group_node
                                    traceDataNumberMap[it.name] =
                                        LongArray(data[0].size) //data[0].size is the number of sub images

                                    for (f in 0..it.data.size - 1) {//go through the sub images
                                        traceDataNumberMap[it.name]!![f] = 0
                                        //println("****create Group:" + f.toString())
                                        val group_node_ix = H5.H5Gcreate(
                                            group_node, f.toString(), HDF5Constants.H5P_DEFAULT,
                                            HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT
                                        ) ////////////default
                                        handles["group_node_ix_" + it.name + "_" + f.toString()] = group_node_ix

                                        //println("******create Group: image_data")/////////////////////////////////////////////////////////////
                                        val group_node_ix_imageData = H5.H5Gcreate(
                                            group_node_ix, "imageData", HDF5Constants.H5P_DEFAULT,
                                            HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT
                                        )
                                        handles["group_node_ix_" + it.name + "_" + f.toString() + "_imageData"] =
                                            group_node_ix_imageData

                                        //println("******add attributes for image data")
                                        val datamapm = data[0][f].getImageDataMap()
                                        for (szAttr in datamapm.keys) {
                                            val dims = longArrayOf(1)
                                            val dataspace_id = H5.H5Screate_simple(1, dims, null);
                                            val group_node_ix_imageData_attributes = H5.H5Acreate(
                                                group_node_ix_imageData,
                                                szAttr,
                                                HDF5Constants.H5T_STD_I32BE,
                                                dataspace_id,
                                                HDF5Constants.H5P_DEFAULT,
                                                HDF5Constants.H5P_DEFAULT
                                            )
                                            H5.H5Awrite(
                                                group_node_ix_imageData_attributes,
                                                HDF5Constants.H5T_NATIVE_DOUBLE,
                                                doubleArrayOf(datamapm[szAttr]!!)
                                            )
                                            H5.H5Aclose(group_node_ix_imageData_attributes)
                                            H5.H5Sclose(dataspace_id)
                                        }

                                        //print("******create Group: vector_data")///////////////////////////////////////////////////////////////
                                        val group_node_ix_traceData = H5.H5Gcreate(
                                            group_node_ix, "traceData", HDF5Constants.H5P_DEFAULT,
                                            HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT
                                        )

                                        handles["group_node_ix_" + it.name + "_" + f.toString() + "_traceData"] =
                                            group_node_ix_traceData

                                        //print("******create vector attributes*****")
                                        val datamapv = data[0][f].getTraceDataMap()
                                        for (szAttr in datamapv.keys) {
                                            val dims = longArrayOf(1)
                                            val dataspace_id = H5.H5Screate_simple(1, dims, null);
                                            val group_node_ix_traceData_attributes = H5.H5Acreate(
                                                group_node_ix_traceData,
                                                szAttr,
                                                HDF5Constants.H5T_STD_I32BE,
                                                dataspace_id,
                                                HDF5Constants.H5P_DEFAULT,
                                                HDF5Constants.H5P_DEFAULT
                                            )
                                            H5.H5Awrite(
                                                group_node_ix_traceData_attributes,
                                                HDF5Constants.H5T_NATIVE_DOUBLE,
                                                doubleArrayOf(datamapv[szAttr]!!)
                                            )
                                            H5.H5Aclose(group_node_ix_traceData_attributes)
                                            H5.H5Sclose(dataspace_id)
                                        }

                                        //
                                        //have unlimited y dimension and x is the number of

                                        val traceDataDims = data[0][f].getTraceDataDims()
                                        val maxdims =
                                            longArrayOf(HDF5Constants.H5S_UNLIMITED.toLong(), traceDataDims[1])
                                        val dims = longArrayOf(0, traceDataDims[1])
                                        val chunk_dims = longArrayOf(flush_cnt.toLong(), traceDataDims[1])
                                        val dataspace = H5.H5Screate_simple(2, dims, maxdims) // the 2 is the rank
                                        //create handle to a creation property list
                                        val prop = H5.H5Pcreate(HDF5Constants.H5P_DATASET_CREATE)
                                        //set chunk size on the creation property list - chunks are needed for an extensible array
                                        var status = H5.H5Pset_chunk(prop, 2, chunk_dims) //2 is the rank
                                        //create dataset, native int and pass the dataspace and creation property. So it knows it has chunking and that can be extended
                                        val dataset_vector = H5.H5Dcreate(
                                            group_node_ix_traceData,
                                            "data",
                                            HDF5Constants.H5T_NATIVE_DOUBLE,
                                            dataspace,
                                            HDF5Constants.H5P_DEFAULT,
                                            prop,
                                            HDF5Constants.H5P_DEFAULT
                                        )
                                        handles["dataset_" + it.name + "_traceData_" + f.toString()] = dataset_vector

                                        H5.H5Sclose(dataspace)
                                        H5.H5Pclose(prop)


                                    }

                                }

                            }
                        }
                        else {
                            //see if the dataset it.name already is stored, every new datasoyrce is added and processed
                            var data = buffers[it.name]
                            if (data != null) {
                                //the dataset is currently registered
                                //add the STRIMMSaveBuffer to buffers
                                //retrieve the List<List<STRIMMBuffer>>
                                data = data as ArrayList<List<STRIMMBuffer>>
                                data.add(it.data)
                                //
                                //
                                //is a flush needed when the number of frames == flush_cnt
                                if (data.size == flush_cnt) {
                                    println("*******flush to HDF5******")
                                    //
                                    //
                                    //save matrix data//
                                    for (frame in 0..data.size - 1) {
                                        for (f in 0..data[frame].size - 1) {
                                            //println("******* write dataset")
                                            //the subimages in each frame could have different dims and pixel type
                                            val dims_frm = data[frame][f].getImageDataDims()
                                            if (data[frame][f].imageData != null) {
                                                //imageDataNumberMap is used to ensure that each datasource has the correct frame number for each buffer saved ie 0,1,2 etc
                                                var type_frm = data[frame][f].getImageDataType()
                                                val dataspaceMatrixData =
                                                    H5.H5Screate_simple(dims_frm.size, dims_frm, null)
                                                val hand =
                                                    handles["group_node_ix_" + it.name + "_" + f.toString() + "_imageData"]!!
                                                var datasetId = 0
                                                if (type_frm == 0) { //BYTE
                                                    type_frm = HDF5Constants.H5T_NATIVE_INT8
                                                    datasetId = H5.H5Dcreate(
                                                        hand,
                                                        imageDataNumberMap[it.name]!!.toString(),
                                                        HDF5Constants.H5T_NATIVE_INT8,
                                                        dataspaceMatrixData,
                                                        HDF5Constants.H5P_DEFAULT,
                                                        HDF5Constants.H5P_DEFAULT,
                                                        HDF5Constants.H5P_DEFAULT
                                                    )
                                                } else if (type_frm == 1) { //SHORT
                                                    type_frm = HDF5Constants.H5T_NATIVE_INT16
                                                    datasetId = H5.H5Dcreate(
                                                        hand,
                                                        imageDataNumberMap[it.name]!!.toString(),
                                                        HDF5Constants.H5T_NATIVE_INT16, //TODO
                                                        dataspaceMatrixData,
                                                        HDF5Constants.H5P_DEFAULT,
                                                        HDF5Constants.H5P_DEFAULT,
                                                        HDF5Constants.H5P_DEFAULT
                                                    )
                                                } else if (type_frm == 2) { //INT
                                                    type_frm = HDF5Constants.H5T_NATIVE_INT32
                                                    datasetId = H5.H5Dcreate(
                                                        hand,
                                                        imageDataNumberMap[it.name]!!.toString(),
                                                        HDF5Constants.H5T_NATIVE_INT32, //TODO
                                                        dataspaceMatrixData,
                                                        HDF5Constants.H5P_DEFAULT,
                                                        HDF5Constants.H5P_DEFAULT,
                                                        HDF5Constants.H5P_DEFAULT
                                                    )
                                                } else if (type_frm == 3) { //LONG
                                                    type_frm = HDF5Constants.H5T_NATIVE_INT64
                                                    datasetId = H5.H5Dcreate(
                                                        hand,
                                                        imageDataNumberMap[it.name]!!.toString(),
                                                        HDF5Constants.H5T_NATIVE_INT64, //TODO
                                                        dataspaceMatrixData,
                                                        HDF5Constants.H5P_DEFAULT,
                                                        HDF5Constants.H5P_DEFAULT,
                                                        HDF5Constants.H5P_DEFAULT
                                                    )
                                                } else if (type_frm == 4) { //FLOAT
                                                    type_frm = HDF5Constants.H5T_NATIVE_FLOAT
                                                    datasetId = H5.H5Dcreate(
                                                        hand,
                                                        imageDataNumberMap[it.name]!!.toString(),
                                                        HDF5Constants.H5T_NATIVE_FLOAT, //TODO
                                                        dataspaceMatrixData,
                                                        HDF5Constants.H5P_DEFAULT,
                                                        HDF5Constants.H5P_DEFAULT,
                                                        HDF5Constants.H5P_DEFAULT
                                                    )
                                                } else if (type_frm == 5) { //DOUBLE
                                                    type_frm = HDF5Constants.H5T_NATIVE_DOUBLE
                                                    datasetId = H5.H5Dcreate(
                                                        hand,
                                                        imageDataNumberMap[it.name]!!.toString(),
                                                        HDF5Constants.H5T_NATIVE_DOUBLE, //TODO
                                                        dataspaceMatrixData,
                                                        HDF5Constants.H5P_DEFAULT,
                                                        HDF5Constants.H5P_DEFAULT,
                                                        HDF5Constants.H5P_DEFAULT
                                                    )
                                                }
                                                //
                                                //
                                                H5.H5Dwrite(
                                                    datasetId,
                                                    type_frm,
                                                    HDF5Constants.H5S_ALL,
                                                    HDF5Constants.H5S_ALL,
                                                    HDF5Constants.H5P_DEFAULT,
                                                    data[frame][f].imageData
                                                )

                                                imageDataNumberMap[it.name] = imageDataNumberMap[it.name]!! + 1
                                                H5.H5Sclose(dataspaceMatrixData)
                                                H5.H5Dclose(datasetId)
                                            }
                                            /////////////////////////////////////
                                            /////////////////////////////////////
                                            //directly append the trace matrix in data[frame][f]
                                            //f indexes each STRIMMBuffer within a frame (which is a List<STRIMMBuffer>)
                                            val dims = data[frame][f].getTraceDataDims()
                                            //it is possible that the different STRIMMBuffers in each frame have different lengths of traceData
                                            //each frame will always contain size(List<STRIMMBuffer>) but they might have come from different sources
                                            //and have different lenghts eg the source might be a car and each STRIMMBuffer traceData could be a particular sensor
                                            //might might have recorded a different number of measurements.
                                            //traceDataNumberMap keeps a record of the length of each STRIMMBuffer's trace data
                                            //
                                            //traceDataNumberMap is necessary in order to append in h5
                                            //in order to append new data in h5
                                            val curLength = traceDataNumberMap[it.name]!![f]
                                            val newLength: Long = (curLength + dims[0]).toLong()
                                            val dataset_append =
                                                handles["dataset_" + it.name + "_traceData_" + f.toString()]!!

                                            H5.H5Dset_extent(dataset_append, longArrayOf(newLength, dims[1]))

                                            val newOffset: Long = curLength.toLong()
                                            val extLength: Long = dims[0].toLong()
                                            var filespace = H5.H5Dget_space(dataset_append)


                                            H5.H5Sselect_hyperslab(
                                                filespace,
                                                HDF5Constants.H5S_SELECT_SET,
                                                longArrayOf(newOffset, 0),
                                                null,
                                                longArrayOf(extLength, dims[1]),
                                                null
                                            )
                                            var memspace = H5.H5Screate_simple(
                                                2,
                                                longArrayOf(extLength, dims[1]),
                                                null
                                            ) //TODO is the rank = 2
                                            H5.H5Dwrite(
                                                dataset_append,
                                                HDF5Constants.H5T_NATIVE_DOUBLE,
                                                memspace,
                                                filespace,
                                                HDF5Constants.H5P_DEFAULT,
                                                data[frame][f].traceData
                                            )
                                            H5.H5Sclose(memspace)
                                            H5.H5Sclose(filespace)
                                            traceDataNumberMap[it.name]!![f] += dims[0] //update traceDataNumberMap to allow for the appended data
                                        }
                                        buffers[it.name] = ArrayList<List<STRIMMBuffer>>()
                                    }
                                }
                            } else {
                                //NEW DATA SOURCE this is where all of the new groups etc are defined.
                                if (file > 0) { //make sure have true h5 file handle
                                    //println("***new data source " + it.name)
                                    buffers[it.name] = ArrayList<List<STRIMMBuffer>>()
                                    imageDataNumberMap[it.name] = 0
                                    //add this frame
                                    val data = buffers[it.name] as ArrayList<List<STRIMMBuffer>>
                                    data.add(it.data)
                                    //
                                    val group_node = H5.H5Gcreate(
                                        file, it.name, HDF5Constants.H5P_DEFAULT,
                                        HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT
                                    )

                                    println("FAM: new dataset is size=${it.data.size}")
                                    handles["group_" + it.name + "_node"] = group_node
                                    traceDataNumberMap[it.name] =
                                        LongArray(data[0].size) //data[0].size is the number of sub images

                                    for (f in 0..it.data.size - 1) {//go through the sub images
                                        traceDataNumberMap[it.name]!![f] = 0
                                        //println("****create Group:" + f.toString())
                                        val group_node_ix = H5.H5Gcreate(
                                            group_node, f.toString(), HDF5Constants.H5P_DEFAULT,
                                            HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT
                                        ) ////////////default
                                        handles["group_node_ix_" + it.name + "_" + f.toString()] = group_node_ix

                                        //println("******create Group: image_data")/////////////////////////////////////////////////////////////
                                        val group_node_ix_imageData = H5.H5Gcreate(
                                            group_node_ix, "imageData", HDF5Constants.H5P_DEFAULT,
                                            HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT
                                        )
                                        handles["group_node_ix_" + it.name + "_" + f.toString() + "_imageData"] =
                                            group_node_ix_imageData

                                        //println("******add attributes for image data")
                                        val datamapm = data[0][f].getImageDataMap()
                                        for (szAttr in datamapm.keys) {
                                            val dims = longArrayOf(1)
                                            val dataspace_id = H5.H5Screate_simple(1, dims, null);
                                            val group_node_ix_imageData_attributes = H5.H5Acreate(
                                                group_node_ix_imageData,
                                                szAttr,
                                                HDF5Constants.H5T_STD_I32BE,
                                                dataspace_id,
                                                HDF5Constants.H5P_DEFAULT,
                                                HDF5Constants.H5P_DEFAULT
                                            )
                                            H5.H5Awrite(
                                                group_node_ix_imageData_attributes,
                                                HDF5Constants.H5T_NATIVE_DOUBLE,
                                                doubleArrayOf(datamapm[szAttr]!!)
                                            )
                                            H5.H5Aclose(group_node_ix_imageData_attributes)
                                            H5.H5Sclose(dataspace_id)
                                        }

                                        //print("******create Group: vector_data")///////////////////////////////////////////////////////////////
                                        val group_node_ix_traceData = H5.H5Gcreate(
                                            group_node_ix, "traceData", HDF5Constants.H5P_DEFAULT,
                                            HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT
                                        )

                                        handles["group_node_ix_" + it.name + "_" + f.toString() + "_traceData"] =
                                            group_node_ix_traceData

                                        //print("******create vector attributes*****")
                                        val datamapv = data[0][f].getTraceDataMap()
                                        for (szAttr in datamapv.keys) {
                                            val dims = longArrayOf(1)
                                            val dataspace_id = H5.H5Screate_simple(1, dims, null);
                                            val group_node_ix_traceData_attributes = H5.H5Acreate(
                                                group_node_ix_traceData,
                                                szAttr,
                                                HDF5Constants.H5T_STD_I32BE,
                                                dataspace_id,
                                                HDF5Constants.H5P_DEFAULT,
                                                HDF5Constants.H5P_DEFAULT
                                            )
                                            H5.H5Awrite(
                                                group_node_ix_traceData_attributes,
                                                HDF5Constants.H5T_NATIVE_DOUBLE,
                                                doubleArrayOf(datamapv[szAttr]!!)
                                            )
                                            H5.H5Aclose(group_node_ix_traceData_attributes)
                                            H5.H5Sclose(dataspace_id)
                                        }

                                        //
                                        //have unlimited y dimension and x is the number of

                                        val traceDataDims = data[0][f].getTraceDataDims()
                                        val maxdims =
                                            longArrayOf(HDF5Constants.H5S_UNLIMITED.toLong(), traceDataDims[1])
                                        val dims = longArrayOf(0, traceDataDims[1])
                                        val chunk_dims = longArrayOf(flush_cnt.toLong(), traceDataDims[1])
                                        val dataspace = H5.H5Screate_simple(2, dims, maxdims) // the 2 is the rank
                                        //create handle to a creation property list
                                        val prop = H5.H5Pcreate(HDF5Constants.H5P_DATASET_CREATE)
                                        //set chunk size on the creation property list - chunks are needed for an extensible array
                                        var status = H5.H5Pset_chunk(prop, 2, chunk_dims) //2 is the rank
                                        //create dataset, native int and pass the dataspace and creation property. So it knows it has chunking and that can be extended
                                        val dataset_vector = H5.H5Dcreate(
                                            group_node_ix_traceData,
                                            "data",
                                            HDF5Constants.H5T_NATIVE_DOUBLE,
                                            dataspace,
                                            HDF5Constants.H5P_DEFAULT,
                                            prop,
                                            HDF5Constants.H5P_DEFAULT
                                        )
                                        handles["dataset_" + it.name + "_traceData_" + f.toString()] = dataset_vector

                                        H5.H5Sclose(dataspace)
                                        H5.H5Pclose(prop)
                                    }
                                }
                            }
                        }
                        isCapturingBuffers = false
//                        GUIMain.loggerService.log(Level.INFO, "IsCapturingBuffers false")
//                    }
                }
                sender().tell(Acknowledgement.INSTANCE, self())
            }
            .matchAny { imm ->
                sender().tell(Acknowledgement.INSTANCE, self())
            }
            .build()
    }

    fun saveDatasets() : Int{
        try {
            GUIMain.experimentService.isFileSaving = true
            GUIMain.loggerService.log(Level.INFO, "Saving datasets...")
            //Only save when in acquisition mode
            //Message received when acquisition is Stopped
//                if (GUIMain.strimmUIService.state == UIstate.ACQUISITION) {
            //flush remaining data
            //wait until this flag is set which indicates that no more buffers need to be captured
            // (some might still be flowing through the akka graph but will be ignored and not saved)
            while (isCapturingBuffers) {
//            GUIMain.loggerService.log(Level.INFO, "Is capturing buffers")
                Thread.sleep(100)
            }

            //this means that the akkka graph might still be pumping out samples (but they can be ignored)
            //buffers contains the flush data associated with each data source by name so is a map of String : List<List<STRIMMBuffer>>
            for (keySz in buffers.keys) {
                GUIMain.loggerService.log(Level.INFO, "buffer key ${keySz}")
                //for each data source save the flush data, so this means save the matrix data as a new array and add the vector data onto the previous
                // TODO change the name of the vector data because it could be a 2D matrix which is appened
                var data = buffers[keySz]
                //possible that the experiment has been stopped when there is no data??  TODO check this can it be null?
                if (data != null) {
                    //println("*******flush******")
                    //save matrix data
                    //the 'outer' list is the frame
                    println("FAM: data size is: ${data.size}")
                    for (frame in 0..data.size - 1) { //for each frame
                        for (f in 0..data[frame].size - 1) {//go through each image source in List<STRIMMBuffer>
                            if (data[frame][f].imageData != null) {
                                var dims = data[frame][f].getImageDataDims()
                                var type_frm = data[frame][f].getImageDataType()
                                val dataspaceMatrixData = H5.H5Screate_simple(dims.size, dims, null)
                                //retrieve the handle for this group
                                val hand =
                                    handles["group_node_ix_" + keySz + "_" + f.toString() + "_imageData"]!!
                                println(hand.toString() + "*******************")
                                //create a dataset
                                var datasetId = 0
                                if (type_frm == 0) { //BYTE
                                    type_frm = HDF5Constants.H5T_NATIVE_INT8
                                    datasetId = H5.H5Dcreate(
                                        hand,
                                        imageDataNumberMap[keySz]!!.toString(),
                                        HDF5Constants.H5T_NATIVE_INT8, //TODO
                                        dataspaceMatrixData,
                                        HDF5Constants.H5P_DEFAULT,
                                        HDF5Constants.H5P_DEFAULT,
                                        HDF5Constants.H5P_DEFAULT
                                    )
                                } else if (type_frm == 1) { //SHORT
                                    type_frm = HDF5Constants.H5T_NATIVE_INT16
                                    datasetId = H5.H5Dcreate(
                                        hand,
                                        imageDataNumberMap[keySz]!!.toString(),
                                        HDF5Constants.H5T_NATIVE_INT16, //TODO
                                        dataspaceMatrixData,
                                        HDF5Constants.H5P_DEFAULT,
                                        HDF5Constants.H5P_DEFAULT,
                                        HDF5Constants.H5P_DEFAULT
                                    )
                                } else if (type_frm == 2) { //INT
                                    type_frm = HDF5Constants.H5T_NATIVE_INT32
                                    datasetId = H5.H5Dcreate(
                                        hand,
                                        imageDataNumberMap[keySz]!!.toString(),
                                        HDF5Constants.H5T_NATIVE_INT32, //TODO
                                        dataspaceMatrixData,
                                        HDF5Constants.H5P_DEFAULT,
                                        HDF5Constants.H5P_DEFAULT,
                                        HDF5Constants.H5P_DEFAULT
                                    )
                                } else if (type_frm == 3) { //LONG
                                    type_frm = HDF5Constants.H5T_NATIVE_INT64
                                    datasetId = H5.H5Dcreate(
                                        hand,
                                        imageDataNumberMap[keySz]!!.toString(),
                                        HDF5Constants.H5T_NATIVE_INT64, //TODO
                                        dataspaceMatrixData,
                                        HDF5Constants.H5P_DEFAULT,
                                        HDF5Constants.H5P_DEFAULT,
                                        HDF5Constants.H5P_DEFAULT
                                    )
                                } else if (type_frm == 4) { //FLOAT
                                    type_frm = HDF5Constants.H5T_NATIVE_FLOAT
                                    datasetId = H5.H5Dcreate(
                                        hand,
                                        imageDataNumberMap[keySz]!!.toString(),
                                        HDF5Constants.H5T_NATIVE_FLOAT, //TODO
                                        dataspaceMatrixData,
                                        HDF5Constants.H5P_DEFAULT,
                                        HDF5Constants.H5P_DEFAULT,
                                        HDF5Constants.H5P_DEFAULT
                                    )
                                } else if (type_frm == 5) { //DOUBLE
                                    type_frm = HDF5Constants.H5T_NATIVE_DOUBLE
                                    datasetId = H5.H5Dcreate(
                                        hand,
                                        imageDataNumberMap[keySz]!!.toString(),
                                        HDF5Constants.H5T_NATIVE_DOUBLE, //TODO
                                        dataspaceMatrixData,
                                        HDF5Constants.H5P_DEFAULT,
                                        HDF5Constants.H5P_DEFAULT,
                                        HDF5Constants.H5P_DEFAULT
                                    )
                                }
                                H5.H5Dwrite(
                                    datasetId, type_frm,
                                    HDF5Constants.H5S_ALL, HDF5Constants.H5S_ALL, HDF5Constants.H5P_DEFAULT,
                                    data[frame][f].imageData
                                )
                                imageDataNumberMap[keySz] = imageDataNumberMap[keySz]!! + 1
                                //close resources
                                H5.H5Sclose(dataspaceMatrixData)
                                H5.H5Dclose(datasetId)
                            }
                            if (data[frame][f].traceData != null) {
                                var dims = data[frame][f].getTraceDataDims()
                                val curLength = traceDataNumberMap[keySz]!![f]
                                val newLength: Long = (curLength + dims[0])
                                val dataset_append =
                                    handles["dataset_" + keySz + "_traceData_" + f.toString()]!!

                                H5.H5Dset_extent(dataset_append, longArrayOf(newLength, dims[1]))

                                val newOffset: Long = curLength
                                val extLength: Long = dims[0]
                                var filespace = H5.H5Dget_space(dataset_append)

                                H5.H5Sselect_hyperslab(
                                    filespace,
                                    HDF5Constants.H5S_SELECT_SET,
                                    longArrayOf(newOffset, 0),
                                    null,
                                    longArrayOf(extLength, dims[1]),
                                    null
                                )

                                var memspace = H5.H5Screate_simple(
                                    2,
                                    longArrayOf(extLength, dims[1]),
                                    null
                                ) //TODO is the rank = 2

                                H5.H5Dwrite(
                                    dataset_append,
                                    HDF5Constants.H5T_NATIVE_DOUBLE,
                                    memspace,
                                    filespace,
                                    HDF5Constants.H5P_DEFAULT,
                                    data[frame][f].traceData
                                )

                                H5.H5Sclose(memspace)
                                H5.H5Sclose(filespace)
                                traceDataNumberMap[keySz]!![f] += dims[0]
                            }
                        }
                    }
                }
            }

            //close handles
            //TODO closes the handles but does it close the file each time
            //TODO it might be better to be continuously opening and closing the HDF5 file handle
            for (ky in handles.keys) {
                if (ky == "file") {
                    H5.H5Fclose(handles[ky]!!)
                    file = 0
                } else if (ky.substring(0, 5) == "group") {
                    H5.H5Gclose(handles[ky]!!)
                } else if (ky.substring(0, 7) == "dataset") {
                    H5.H5Dclose(handles[ky]!!)
                } else {

                }
            }

            //explicitely clean up
            handles = hashMapOf<String, Int>()
//                    vec_size_map[keySz] = vec_size_map[keySz]!! + flush_cnt
//                    buffers[keySz] = ArrayList<List<STRIMMBuffer>>()
//                }
            GUIMain.experimentService.isFileSaving = false
            return 1
        }
        catch(ex : Exception){
            GUIMain.loggerService.log(Level.INFO, "Error in saving datasets. Message: ${ex.message}")
            GUIMain.loggerService.log(Level.INFO, ex.stackTrace)
            return 0
        }
    }
}