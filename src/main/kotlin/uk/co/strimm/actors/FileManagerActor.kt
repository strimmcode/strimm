package uk.co.strimm.actors

import akka.actor.AbstractActor
import akka.actor.ActorRef
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


class FileManagerActor() : AbstractActor() {
    var file: Int = 0
    val datasetActors = mutableMapOf<String, ActorRef>()
    var buffers = hashMapOf<String, List<List<STRIMMBuffer>>>()
    var flush_cnt = 5
    var vec_size_map = hashMapOf<String, Int>()
    var vec_size_total_map = hashMapOf<String, IntArray>()
    var b_capturing_to_buffers = false


    var handles = hashMapOf<String, Int>()

    companion object {
        fun props(): Props {
            return Props.create<FileManagerActor>(FileManagerActor::class.java) { FileManagerActor() }
        }
    }

    override fun createReceive(): Receive {
        return receiveBuilder()
            .match<Message>(Message::class.java) { message ->
                println("FileManagerActor <MESSAGE>")
            }
            .match<AskInitHDF5File>(AskInitHDF5File::class.java) {
                GUIMain.loggerService.log(Level.INFO, "Creating h5 file")
                var filename = getOutputPathAndName("strimm_exp")
                val timeStampFull =
                    ZonedDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME).replace(":", "-")
                val timeStamp =
                    timeStampFull.substring(0, timeStampFull.length - 4) //Trim the end to not include milliseconds
                filename += timeStamp
                filename += ".h5"

                //create file (which is a long/handle) with defaults
                try {
                    file = H5.H5Fcreate(
                        filename,
                        HDF5Constants.H5F_ACC_TRUNC,
                        HDF5Constants.H5P_DEFAULT,
                        HDF5Constants.H5P_DEFAULT
                    )
                    handles["file"] = file
                    GUIMain.loggerService.log(Level.INFO, "Successfully created h5 file")
                } catch (ex: Exception) {
                    GUIMain.loggerService.log(Level.SEVERE, "Could not create h5 file")
                    GUIMain.loggerService.log(Level.SEVERE, ex.stackTrace)
                }
            }
            .match<AskShutdownHDF5File>(AskShutdownHDF5File::class.java) {
            }
            .match<TerminateActor>(TerminateActor::class.java) {
                GUIMain.loggerService.log(Level.INFO, "FileManagerActor actor ${self.path().name()} terminating")
                self.tell(Kill.getInstance(), self)
            }
            .match<TellSaveDatasets>(TellSaveDatasets::class.java) {
                if (GUIMain.strimmUIService.state == UIstate.ACQUISITION) {
                    GUIMain.loggerService.log(Level.INFO, "Saving data...")
                    //flush remaining data
                    //multithread issue
                    while (b_capturing_to_buffers == true) {
                        Thread.sleep(100)
                    } // errors are a thread issue

                    //the akka graph has not yet fully stopped and is still pumping out samples
                    for (keySz in buffers.keys) {
                        var data = buffers[keySz]
                        if (data != null) {
                            println("*******flush******")
                            //save matrix data
                            for (frame in 0 until data.size) {
                                var frames = data[frame]
                                var frm = frames[0] // STRIMMBuffer

                                val dims_frm = frm.getDims() //returns the dims of this STRIMMBuffer which can vary
                                val type_frm = frm.getType() //returns the type of each pixel
                                for (matrix_ix in 0 until data[frame].size) {
                                    println("******* create dataset at " + frame.toString() + "," + matrix_ix.toString())
                                    println("******* write dataset")
                                    val dataspaceMatrixData = H5.H5Screate_simple(dims_frm.size, dims_frm, null)
                                    val hand = handles["group_node_ix" + matrix_ix.toString() + "_matrix"]!!
                                    val datasetId = H5.H5Dcreate(
                                        hand,
                                        vec_size_total_map[keySz]!![matrix_ix].toString(),
                                        HDF5Constants.H5T_NATIVE_INT32,
                                        dataspaceMatrixData,
                                        HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT
                                    )
                                    H5.H5Dwrite(
                                        datasetId, type_frm,
                                        HDF5Constants.H5S_ALL, HDF5Constants.H5S_ALL, HDF5Constants.H5P_DEFAULT,
                                        frames[matrix_ix].matrix_data
                                    )

                                    vec_size_total_map[keySz]!![matrix_ix]++
                                    H5.H5Sclose(dataspaceMatrixData)
                                    H5.H5Dclose(datasetId)
                                }
                            }
                            //save the vector data
                            //get the vector data to extend the dataset
                            val curLength = vec_size_map[keySz]!!  ///
                            for (strimm_ix in 0 until data[0].size) {
                                println("strimm_ix $strimm_ix")
                                val strimm_1 = data[0][strimm_ix]
                                val vec1 = strimm_1.vector_data
                                //so to store as a 2d double array we need an array which is (frame x vec1.size)
                                var vec_data = DoubleArray(vec1.size * data.size)
                                //fill in array
                                for (frame in 0..data.size - 1) {
                                    var vec = data[frame][strimm_ix].vector_data
                                    for (vec_ix in 0..vec1.size - 1) {
                                        //print("hi")
                                        vec_data[vec_ix + vec1.size * frame] = vec[vec_ix]
                                    }
                                }

                                //extend and save array
                                val newLength: Long = (curLength + data.size).toLong()
                                val dataset_vector = handles["dataset_" + keySz + "_vector" + strimm_ix.toString()]!!

                                H5.H5Dset_extent(dataset_vector, longArrayOf(newLength, 2))
                                val newOffset: Long = curLength.toLong()
                                val extLength: Long = data.size.toLong()
                                var filespace = H5.H5Dget_space(dataset_vector)
                                H5.H5Sselect_hyperslab(
                                    filespace,
                                    HDF5Constants.H5S_SELECT_SET,
                                    longArrayOf(newOffset, 0),
                                    null,
                                    longArrayOf(extLength, 2),
                                    null
                                )
                                var memspace = H5.H5Screate_simple(2, longArrayOf(extLength, 2), null)
                                H5.H5Dwrite(
                                    dataset_vector,
                                    HDF5Constants.H5T_NATIVE_DOUBLE,
                                    memspace,
                                    filespace,
                                    HDF5Constants.H5P_DEFAULT,
                                    vec_data
                                )
                                H5.H5Sclose(memspace)
                                H5.H5Sclose(filespace)
                            }
                        }
                    }

                    GUIMain.loggerService.log(Level.INFO, "Closing H5 file")
                    for (ky in handles.keys) {
                        when {
                            ky == "file" -> {
                                H5.H5Fclose(handles[ky]!!)
                                file = 0
                            }
                            ky.substring(0, 5) == "group" -> {
                                H5.H5Gclose(handles[ky]!!)
                            }
                            ky.substring(0, 7) == "dataset" -> {
                                H5.H5Dclose(handles[ky]!!)
                            }
                        }

                    }

                    //explicitely clean up
                    handles = hashMapOf<String, Int>()
//                    vec_size_map[keySz] = vec_size_map[keySz]!! + flush_cnt
//                    buffers[keySz] = ArrayList<List<STRIMMBuffer>>()
                }
                sender().tell(Acknowledgement.INSTANCE, self())
            }
            .match<STRIMMSaveBuffer>(STRIMMSaveBuffer::class.java) {
                //only save the STRIMMSaveBuffer if in ACQUISITION mode (and not ACQUISITION_PAUSED or PREVIEW)
                if (GUIMain.strimmUIService.state == UIstate.ACQUISITION) {
                    b_capturing_to_buffers = true
                    //the STRIMMBuffer will contain matrix_data and vector_data
                    var data = buffers[it.name]
                    if (data != null) {
                        //add buffer to structure
                        data = data as ArrayList<List<STRIMMBuffer>>
                        data.add(it.data)   //// does this work passing by ref?????
                        //is a flush needed
                        if (data.size == flush_cnt) {
                            println("*******flush******")
                            //save matrix data
                            for (frame in 0..data.size - 1) {
                                var frames = data[frame]
                                var frm = frames[0] // STRIMMBuffer

                                var dims_frm = frm.getDims() //returns the dims of this STRIMMBuffer which can vary
                                var type_frm = frm.getType() //returns the type of each pixel
                                for (matrix_ix in 0..data[frame].size - 1) {
                                    println("******* create dataset at " + frame.toString() + "," + matrix_ix.toString())
                                    println("******* write dataset")
                                    val dataspaceMatrixData = H5.H5Screate_simple(dims_frm.size, dims_frm, null)
                                    val hand = handles["group_node_ix" + matrix_ix.toString() + "_matrix"]!!
                                    val datasetId = H5.H5Dcreate(
                                        hand,
                                        vec_size_total_map[it.name]!![matrix_ix].toString(),
                                        HDF5Constants.H5T_NATIVE_INT32,
                                        dataspaceMatrixData,
                                        HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT
                                    )
                                    H5.H5Dwrite(
                                        datasetId, type_frm,
                                        HDF5Constants.H5S_ALL, HDF5Constants.H5S_ALL, HDF5Constants.H5P_DEFAULT,
                                        frames[matrix_ix].matrix_data
                                    )

                                    vec_size_total_map[it.name]!![matrix_ix]++
                                    H5.H5Sclose(dataspaceMatrixData)
                                    H5.H5Dclose(datasetId)
                                }
                            }
                            //save the vector data/////////////////////////////////////////////
                            //get the vector data to extend the dataset
                            //
                            val curLength = vec_size_map[it.name]!!
                            for (strimm_ix in 0..data[0].size - 1) {
                                val strimm_1 = data[0][strimm_ix]
                                val vec1 = strimm_1.vector_data
                                //so to store as a 2d double array we need an array which is (frame x vec1.size)
                                var vec_data = DoubleArray(vec1.size * flush_cnt)
                                //fill in array
                                for (frame in 0..data.size - 1) {
                                    var vec = data[frame][strimm_ix].vector_data
                                    for (vec_ix in 0..vec1.size - 1) {
                                        vec_data[vec_ix + vec1.size * frame] = vec[vec_ix]
                                    }
                                }
                                //
                                //extend and save array
                                val newLength: Long = (curLength + flush_cnt).toLong()
                                val dataset_vector = handles["dataset_" + it.name + "_vector" + strimm_ix.toString()]!!

                                H5.H5Dset_extent(dataset_vector, longArrayOf(newLength, 2))
                                val newOffset: Long = curLength.toLong()
                                val extLength: Long = flush_cnt.toLong()
                                var filespace = H5.H5Dget_space(dataset_vector)
                                H5.H5Sselect_hyperslab(
                                    filespace,
                                    HDF5Constants.H5S_SELECT_SET,
                                    longArrayOf(newOffset, 0),
                                    null,
                                    longArrayOf(extLength, 2),
                                    null
                                )
                                var memspace = H5.H5Screate_simple(2, longArrayOf(extLength, 2), null)
                                H5.H5Dwrite(
                                    dataset_vector,
                                    HDF5Constants.H5T_NATIVE_DOUBLE,
                                    memspace,
                                    filespace,
                                    HDF5Constants.H5P_DEFAULT,
                                    vec_data
                                )
                                H5.H5Sclose(memspace)
                                H5.H5Sclose(filespace)
                            }
                            vec_size_map[it.name] = vec_size_map[it.name]!! + flush_cnt
                            buffers[it.name] = ArrayList<List<STRIMMBuffer>>()
                        }
                    } else {
                        //new data source
                        if (file > 0) { //make sure have true h5 file handle
                            println("***new data source " + it.name)
                            buffers[it.name] = ArrayList<List<STRIMMBuffer>>()
                            vec_size_map[it.name] = 0

                            val data = buffers[it.name] as ArrayList<List<STRIMMBuffer>>
                            data.add(it.data)
                            println("***create Group:Node")
                            println("**** file " + file.toString())
                            // file/it.name/0/matrix_data   file/it.name/0/vector_data/data
                            val group_node = H5.H5Gcreate(
                                file, it.name, HDF5Constants.H5P_DEFAULT,
                                HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT
                            )
                            handles["group_node"] = group_node
                            vec_size_total_map[it.name] = IntArray(data[0].size)
                            for (f in 0..it.data.size - 1) {
                                vec_size_total_map[it.name]!![f] = 0
                                println("****create Group:" + f.toString())
                                val group_node_ix = H5.H5Gcreate(
                                    group_node, f.toString(), HDF5Constants.H5P_DEFAULT,
                                    HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT
                                ) ////////////default
                                handles["group_node_ix" + f.toString()] = group_node_ix

                                println("******create Group: matrix_data")
                                val group_node_ix_matrix = H5.H5Gcreate(
                                    group_node_ix, "matrix_data", HDF5Constants.H5P_DEFAULT,
                                    HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT
                                )
                                handles["group_node_ix" + f.toString() + "_matrix"] = group_node_ix_matrix
                                println("******add attributes for matrix data")
                                //
                                //
                                //
                                val datapack = it.data[0]
                                val datamapm = datapack.getMatrixDataMap()
                                for (szAttr in datamapm.keys) {
                                    val dims = longArrayOf(1)
                                    val dataspace_id = H5.H5Screate_simple(1, dims, null);
                                    val group_node_ix_matrix_attributes = H5.H5Acreate(
                                        group_node_ix_matrix, szAttr, HDF5Constants.H5T_STD_I32BE, dataspace_id,
                                        HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT
                                    )
                                    H5.H5Awrite(
                                        group_node_ix_matrix_attributes,
                                        HDF5Constants.H5T_NATIVE_DOUBLE,
                                        doubleArrayOf(datamapm[szAttr]!!)
                                    )
                                    H5.H5Aclose(group_node_ix_matrix_attributes)
                                    H5.H5Sclose(dataspace_id)
                                }

                                print("******create Group: vector_data")
                                val group_node_ix_vector = H5.H5Gcreate(
                                    group_node_ix, "vector_data", HDF5Constants.H5P_DEFAULT,
                                    HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT
                                )
                                handles["group_node_ix" + f.toString() + "_vector"] = group_node_ix_vector

                                print("******create vectro attributes*****")
                                val datamapv = datapack.getVectorDataMap()
                                for (szAttr in datamapv.keys) {
                                    val dims = longArrayOf(1)
                                    val dataspace_id = H5.H5Screate_simple(1, dims, null);
                                    val group_node_ix_vector_attributes = H5.H5Acreate(
                                        group_node_ix_vector, szAttr, HDF5Constants.H5T_STD_I32BE, dataspace_id,
                                        HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT
                                    )
                                    H5.H5Awrite(
                                        group_node_ix_vector_attributes,
                                        HDF5Constants.H5T_NATIVE_DOUBLE,
                                        doubleArrayOf(datamapv[szAttr]!!)
                                    )
                                    H5.H5Aclose(group_node_ix_vector_attributes)
                                    H5.H5Sclose(dataspace_id)
                                }

                                //
                                //
                                print("*******size adjustible vector_data dataset")
                                val maxdims = longArrayOf(HDF5Constants.H5S_UNLIMITED.toLong(), 2)
                                val dims = longArrayOf(0, 2)
                                val chunk_dims = longArrayOf(flush_cnt.toLong(), 2)
                                val dataspace = H5.H5Screate_simple(2, dims, maxdims)
                                //create handle to a creation property list
                                val prop = H5.H5Pcreate(HDF5Constants.H5P_DATASET_CREATE)
                                //set chunk size on the creation property list - chunks are needed for an extensible array
                                var status = H5.H5Pset_chunk(prop, 2, chunk_dims)
                                //create dataset, native int and pass the dataspace and creation property. So it knows it has chunking and that can be extended
                                //
                                //
                                //type
                                val dataset_vector = H5.H5Dcreate(
                                    group_node_ix_vector,
                                    "data",
                                    HDF5Constants.H5T_NATIVE_DOUBLE,
                                    dataspace,
                                    HDF5Constants.H5P_DEFAULT,
                                    prop,
                                    HDF5Constants.H5P_DEFAULT
                                )
                                handles["dataset_" + it.name + "_vector" + f.toString()] = dataset_vector

                                H5.H5Sclose(dataspace)
                                H5.H5Pclose(prop)


                            }

                        }

                    }
                    b_capturing_to_buffers = false
                }

            }
            .matchAny { imm ->
                sender().tell(Acknowledgement.INSTANCE, self())
            }
            .build()
    }
}
