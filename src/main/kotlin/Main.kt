
import hdf.hdf5lib.H5
import hdf.hdf5lib.HDF5Constants
import net.imagej.ImageJ
import uk.co.strimm.gui.GUIMain
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.script.ScriptEngine.FILENAME

class Main {
        companion object {
            /**
             * Main method.
             *
             * @param args the array of arguments
             */
            @JvmStatic
            fun main(args: Array<String>) {

//                var RANK      =  2
//                var FILENAME  =  "extend.h5"
//                var DATASETNAME = "ExtendibleArray"
//
//
//
//                val size1 = LongArray(2)
//                val dims    = longArrayOf(3,3)
//                val  data    = IntArray(9)
//                for (f in 0..data.size-1){
//                    data[f]=1
//                }
//
//                val maxdims = longArrayOf(HDF5Constants.H5S_UNLIMITED.toLong(), HDF5Constants.H5S_UNLIMITED.toLong())
//                val chunk_dims = longArrayOf(2, 5)
//
//
//                val dimsext    = LongArray(2)
//                dimsext[0] = 7
//                dimsext[1] = 2
//                var offset = LongArray(2)
//
//                var dataext = IntArray(14)
//                for (f in 0..dataext.size-1){
//                    dataext[f] = 10
//                }
//
//                //we would create a new h5 fileID, and each
//
//
//                //create a dataspace with min dims the starting (3,3) to an unlimited number of (-1,-1)
//                val dataspace = H5.H5Screate_simple(RANK, dims, maxdims)
//                //create file (which is a long/handle) with defaults
//                val file = H5.H5Fcreate(FILENAME, HDF5Constants.H5F_ACC_TRUNC, HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT)
//                //create handle to a creation property list
//                val prop   = H5.H5Pcreate(HDF5Constants.H5P_DATASET_CREATE)
//                //set chunk size on the creation property list - chunks are needed for an extensible array
//                var status = H5.H5Pset_chunk(prop, RANK, chunk_dims)

//                //create dataset, native int and pass the dataspace and creation property. So it knows it has chunking and that can be extended
//                val dataset = H5.H5Dcreate(file, DATASETNAME, HDF5Constants.H5T_NATIVE_INT, dataspace, HDF5Constants.H5P_DEFAULT, prop, HDF5Constants.H5P_DEFAULT);



////              //write to dataset
//                status = H5.H5Dwrite(dataset, HDF5Constants.H5T_NATIVE_INT, HDF5Constants.H5S_ALL, HDF5Constants.H5S_ALL, HDF5Constants.H5P_DEFAULT, data);
//                //change the size of the dataset
//                size1[0] = dims[0] + dimsext[0];
//                size1[1] = dims[1];
//                H5.H5Dset_extent(dataset, size1 )
//                //get the current dataspace from the dataset
//                var filespace = H5.H5Dget_space(dataset)
//                offset[0] = 3
//                offset[1] = 0
//                //select the slab of this new dataspace with offset and dimsext
//                status    = H5.H5Sselect_hyperslab(filespace, HDF5Constants.H5S_SELECT_SET, offset, null, dimsext, null)
//                //create a memory space based on dimsext
//                var memspace = H5.H5Screate_simple(RANK, dimsext, null);
//                //write data to extended part of the dataset
//                status = H5.H5Dwrite(dataset, HDF5Constants.H5T_NATIVE_INT, memspace, filespace, HDF5Constants.H5P_DEFAULT, dataext);
//                //close all
//                status = H5.H5Dclose(dataset)
//                status = H5.H5Pclose(prop)
//                status = H5.H5Sclose(dataspace)
//                status = H5.H5Sclose(memspace)
//                status = H5.H5Sclose(filespace)
//                status = H5.H5Fclose(file)



                    println("start")
                    val ij = ImageJ()
                    ij.launch("")
                    ij.command().run(GUIMain::class.java, true)


//
//                val dims2D = longArrayOf(20, 10)
//                val dataspaceFirstGroup = H5.H5Screate_simple(2, dims2D, null)
//
//                //create the file and return a fileID (longs and used as handles)
//                val fileId = H5.H5Fcreate(
//                    fname, HDF5Constants.H5F_ACC_TRUNC,
//                    HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT
//                )
//
//                //create a group - which must be the root group
//                val firstGroup = H5.H5Gcreate(fileId, "firstGroup", HDF5Constants.H5P_DEFAULT,
//                    HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT)
//
//                //create a dataset
//                val datasetId = H5.H5Dcreate(firstGroup, "2D 32-bit integer 20x10",
//                    HDF5Constants.H5T_NATIVE_INT32, dataspaceFirstGroup,
//                    HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT, HDF5Constants.H5P_DEFAULT)
//
//                var dset_data = IntArray(20*10)
//                dset_data[0] = 100
//
//                H5.H5Dwrite(datasetId, HDF5Constants.H5T_NATIVE_INT, HDF5Constants.H5S_ALL,
//                    HDF5Constants.H5S_ALL, HDF5Constants.H5P_DEFAULT, dset_data)
//
//                dset_data[0] = 0
//
//                H5.H5Dread(datasetId, HDF5Constants.H5T_NATIVE_INT, HDF5Constants.H5S_ALL,
//                    HDF5Constants.H5S_ALL, HDF5Constants.H5P_DEFAULT, dset_data)
//
//
//                H5.H5Dclose(datasetId)
//
//                H5.H5Sclose(dataspaceFirstGroup)
//
//                H5.H5Gclose(firstGroup)
//                H5.H5Fclose(fileId)



//                val COMPort = SerialPort.getCommPort("COM" + 4)
//                COMPort.setComPortParameters(9600, 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY)
//                COMPort!!.setComPortTimeouts(SerialPort.TIMEOUT_NONBLOCKING, 0, 0)
//                if (!COMPort!!.isOpen) {
//                    if (COMPort!!.openPort()) {
//                        println("Arduino:Opened port")
//                        // COMPort.outputStream.write(byteArrayOf('A'.toByte()))
//                    } else {
//                        println("Arduino:Failed to open port")
//                    }
//                } else {
//                    println("Arduino:Port is already open")
//                }
//
//                val writer = PrintWriter(COMPort.outputStream)
//                val inStReader = InputStreamReader(COMPort.inputStream)
//                val bufReader = BufferedReader(inStReader)
//
//                for (f in 0..1000){
//                    writer.println("hello")
//                    writer.flush()
//                    val inSz = bufReader.readLine()
//                    println(inSz)
//                }

//                val readBuffer = ByteArray(60)
//                val outputMessage = ByteArray(60)
//
//                for (f in 0..60-1){
//                    outputMessage[f] = f.toByte()
//                }
//
//
//                val serialPort = COMPort
//
//                println("Setting read timeout mode to blocking with no timeout")
//                serialPort.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 0, 0)
//                while (true){
//                    println("*******")
//
//                println("writing message to serial port")
////                serialPort.writeBytes(outputMessage, outputMessage.size.toLong())
////Thread.sleep(500)
//                val numBytesRead = serialPort.readBytes(readBuffer, readBuffer.size.toLong())
//                println(String(readBuffer))
//                println("Reading complete!\n")
               // println("Closing " + serialPort.descriptivePortName + ": " + serialPort.closePort())
//
//                    val by = ByteArray(8)
//                    serialPort.writeBytes(by, 8)
////                val bytes = ByteArray(4)
////               for (f in 0..3){
////                   bytes[f]=f.toByte()
////               }
//Thread.sleep(1000)
//
//                //COMPort!!.writeBytes(bytes, 4)
//                val buffer = ByteArray(200)
//
//                for (f in 0..63) buffer[f]=1
//
//                var cnt = 0
//
//                val hand = 0
//

//                    buffer[0]=cnt.toByte()
//                   // COMPort.outputStream.write(88)
//                    for (f in 0..98) {
//                        COMPort.outputStream.write(88)
//                    }
//                   // COMPort.outputStream.flush()
//                    println("sent buffer: ");
////                    for (i in 0..64-1) print(buffer[i].toString()+",")
////                    println("")
////Thread.sleep(500)
////                    cnt++
////                    if (cnt==254) cnt = 0
////
////                    for (f in 0..63) buffer[f]=0
////
////                    COMPort.readBytes(buffer, 64)
////                    println("read buffer:")
////                    for (i in 0..64-1) print(buffer[i].toString() + ",")
////                    println("")
//
//
//
//                    Thread.sleep(2000)
//              }
//
            }
        }
    }
