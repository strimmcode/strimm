package uk.co.strimm
//NOT USED///////////////////////////////////////////////////////////
/////////////////////////////////////////////////////////////////////
import com.opencsv.CSVReader
import com.opencsv.CSVWriter

import java.io.File
import java.io.FileReader
import java.io.FileWriter

//hdf5 codes
//            //0 == long type
//            //1 == int type
//            //2 == short type
//            //3 == byte type
//            //4 == double type
//            //5 == float type
class STRIMMBufferFileLoader {
//    fun SaveHDF5(buffer : List<STRIMMBuffer>, szH5File : String, szDesc : String){
//        val file = File(szDesc)
//        val outfile = FileWriter(file)
//        val writer = CSVWriter(outfile)
//        val data = arrayOf(buffer[0].javaClass.toString(), buffer.size.toString())
//        writer.writeNext(data)
//        writer.close()
//        //each type of STRIMMBuffer will need its own writer
//        if (data[0] == "class uk.co.strimm.STRIMMPixelBuffer") {
//            val w_list = IntArray(buffer.size)
//            val h_list = IntArray(buffer.size)
//            val numChannels_list = IntArray(buffer.size)
//            val pixelTypeCode_list = IntArray(buffer.size)
//            val dataID_list = IntArray(buffer.size)
//            val status_list = IntArray(buffer.size)
//            val timeAcquired_list = DoubleArray(buffer.size)
//
//            for (f in 0..buffer.size - 1) {
//                val im = (buffer[f] as STRIMMPixelBuffer)
//                w_list[f] = im.w
//                h_list[f] = im.h
//                numChannels_list[f] = im.numChannels
//                if (im.pixelType == "Byte"){
//                    pixelTypeCode_list[f] = 3
//                }
//                else if (im.pixelType == "Short"){
//                    pixelTypeCode_list[f] = 2
//                }
//                else if (im.pixelType == "Int"){
//                    pixelTypeCode_list[f] = 1
//                }
//                else if (im.pixelType == "Long"){
//                    pixelTypeCode_list[f] = 0
//                }
//                else if (im.pixelType == "Float"){
//                    pixelTypeCode_list[f] = 5
//                }
//                else if (im.pixelType == "Double"){
//                    pixelTypeCode_list[f] = 4
//                }
//                else {
//                    pixelTypeCode_list[f] = 10 // not supported
//                }
//                dataID_list[f] = im.dataID
//                status_list[f] = im.status
//                timeAcquired_list[f] = im.timeAcquired.toDouble()
//            }
//
//            lateinit var dataset_pix: Dataset
//            lateinit var dataset_w: Dataset
//            lateinit var dataset_h: Dataset
//            lateinit var dataset_numChannels: Dataset
//            lateinit var dataset_pixelTypeCode: Dataset
//            lateinit var dataset_dataID: Dataset
//            lateinit var dataset_status: Dataset
//            lateinit var dataset_timeAcquired: Dataset
//
//            val hdf_file = buildFileExperimentalContracts(szH5File) {
//                metadata {
//                    author = "author"
//                    affiliation = "affiliation"
//                    description = "description"
//                    software = "software version"
//                }
//                dataset_pix = planar("pix") {
//                    description = ""
//                    axes(1, "time")
//                    plane(3, "z", "y", "x")
//                }
//                dataset_w = planar("w") {
//                    description = ""
//                    axes(1, "Time")
//                    plane(1, "x")
//                }
//                dataset_h = planar("h") {
//                    description = ""
//                    axes(1, "Time")
//                    plane(1, "x")
//                }
//                dataset_numChannels = planar("numChannels") {
//                    description = ""
//                    axes(1, "Time")
//                    plane(1, "x")
//                }
//                dataset_pixelTypeCode = planar("pixelTypeCode") {
//                    description = ""
//                    axes(1, "Time")
//                    plane(1, "x")
//                }
//                dataset_dataID = planar("dataID") {
//                    description = ""
//                    axes(1, "Time")
//                    plane(1, "x")
//                }
//                dataset_status = planar("status") {
//                    description = ""
//                    axes(1, "Time")
//                    plane(1, "x")
//                }
//                dataset_timeAcquired = planar("timeAcquired") {
//                    description = ""
//                    axes(1, "Time")
//                    plane(1, "x")
//                }
//            }
//            for (f in 0..buffer.size - 1) {
//                if (pixelTypeCode_list[f] == 3) {
//                    dataset_pix.write(
//                        PlanarCoordinate(f.toLong()),
//                        uk.ac.shef.DataType.from(
//                            (buffer[f] as STRIMMPixelBuffer).pix as ByteArray,
//                            kotlin.longArrayOf(w_list[f].toLong(), h_list[f].toLong(), numChannels_list[f].toLong())
//                        )
//                    )
//                }
//                else if (pixelTypeCode_list[f] == 2){
//                    dataset_pix.write(
//                        PlanarCoordinate(f.toLong()),
//                        uk.ac.shef.DataType.from(
//                            (buffer[f] as STRIMMPixelBuffer).pix as ShortArray,
//                            kotlin.longArrayOf(w_list[f].toLong(), h_list[f].toLong(), numChannels_list[f].toLong())
//                        )
//                    )
//                }
//                else if (pixelTypeCode_list[f] == 1){
//                    dataset_pix.write(
//                        PlanarCoordinate(f.toLong()),
//                        uk.ac.shef.DataType.from(
//                            (buffer[f] as STRIMMPixelBuffer).pix as IntArray,
//                            kotlin.longArrayOf(w_list[f].toLong(), h_list[f].toLong(), numChannels_list[f].toLong())
//                        )
//                    )
//                }
//                else if (pixelTypeCode_list[f] == 0){
//                    dataset_pix.write(
//                        PlanarCoordinate(f.toLong()),
//                        uk.ac.shef.DataType.from(
//                            (buffer[f] as STRIMMPixelBuffer).pix as LongArray,
//                            kotlin.longArrayOf(w_list[f].toLong(), h_list[f].toLong(), numChannels_list[f].toLong())
//                        )
//                    )
//                }
//                else if (pixelTypeCode_list[f] == 5){
//                    dataset_pix.write(
//                        PlanarCoordinate(f.toLong()),
//                        uk.ac.shef.DataType.from(
//                            (buffer[f] as STRIMMPixelBuffer).pix as FloatArray,
//                            kotlin.longArrayOf(w_list[f].toLong(), h_list[f].toLong(), numChannels_list[f].toLong())
//                        )
//                    )
//                }
//                else if (pixelTypeCode_list[f] == 4){
//                    dataset_pix.write(
//                        PlanarCoordinate(f.toLong()),
//                        uk.ac.shef.DataType.from(
//                            (buffer[f] as STRIMMPixelBuffer).pix as DoubleArray,
//                            kotlin.longArrayOf(w_list[f].toLong(), h_list[f].toLong(), numChannels_list[f].toLong())
//                        )
//                    )
//                }
//                else{
//                    //not supported
//                }
//            }
//            dataset_w.write(
//                PlanarCoordinate(0),
//                uk.ac.shef.DataType.from(w_list, kotlin.longArrayOf(buffer.size.toLong()))
//            )
//            dataset_h.write(
//                PlanarCoordinate(0),
//                uk.ac.shef.DataType.from(h_list, kotlin.longArrayOf(buffer.size.toLong()))
//            )
//            dataset_numChannels.write(
//                PlanarCoordinate(0),
//                uk.ac.shef.DataType.from(numChannels_list, kotlin.longArrayOf(buffer.size.toLong()))
//            )
//            dataset_pixelTypeCode.write(
//                PlanarCoordinate(0),
//                uk.ac.shef.DataType.from(pixelTypeCode_list, kotlin.longArrayOf(buffer.size.toLong()))
//            )
//            dataset_dataID.write(
//                PlanarCoordinate(0),
//                uk.ac.shef.DataType.from(dataID_list, kotlin.longArrayOf(buffer.size.toLong()))
//            )
//            dataset_status.write(
//                PlanarCoordinate(0),
//                uk.ac.shef.DataType.from(status_list, kotlin.longArrayOf(buffer.size.toLong()))
//            )
//            dataset_timeAcquired.write(
//                PlanarCoordinate(0),
//                uk.ac.shef.DataType.from(timeAcquired_list, kotlin.longArrayOf(buffer.size.toLong()))
//            )
//            dataset_w.close()
//            dataset_h.close()
//            dataset_numChannels.close()
//            dataset_pixelTypeCode.close()
//            dataset_dataID.close()
//            dataset_status.close()
//            dataset_timeAcquired.close()
//            dataset_pix.close()
//            hdf_file.close()
//        }
//        else if (data[0] == "class uk.co.strimm.STRIMMSignalBuffer"){
//
//            val times = arrayListOf<Double>()
//            val data = arrayListOf<Double>()
//            val channelNames = (buffer[0] as STRIMMSignalBuffer).channelNames
//            var numSamples = buffer.size * (buffer[0] as STRIMMSignalBuffer).numSamples
//            for (j in 0..channelNames!!.size-1) {
//                for (f in 0..buffer.size - 1) {
//                    val im = (buffer[f] as STRIMMSignalBuffer)
//                    for (i in 0..im.times!!.size - 1) {
//                        data!!.add(im!!.data!!.get(j*im!!.times!!.size + i))
//                    }
//                }
//            }
//            for (f in 0..buffer.size-1){
//                val im = (buffer[f] as STRIMMSignalBuffer)
//                for (ff in 0..im.times!!.size-1){
//                    times.add(im!!.times!!.get(ff)  )
//                }
//            }
//
//            val hdf_file = buildFileExperimentalContracts(szH5File) {
//                metadata {
//                    author = "author"
//                    affiliation = "affiliation"
//                    description = "description"
//                    software = "software version"
//                }
//            }
//
//            //save as temp file
//            hdf_file.close()
//        }
//        else if (data[0] == "class different STRIMMBuffer"){
//
//        }
//    }
//    fun LoadDatasetFromHDF5(HF5sz : String, HF5desc : String, HF5dataset : String) : List<STRIMMBuffer>?{
//        var classSz = ""
//        var numRows = 0
//        val reader = CSVReader(FileReader(HF5desc))
//            val r = reader.readAll()
//            for (rr in r){
//               if (rr[0] == HF5dataset){
//                   println(rr[0])
//                   classSz = rr[1] // class such as STRIMMPixelBuffer
//                   numRows = rr[2].toInt() // number of above structures
//                   val buffer = arrayOfNulls<STRIMMPixelBuffer?>(numRows)
//                   if (classSz == "class uk.co.strimm.STRIMMPixelBuffer") {
//                       println(classSz)
//                       val h5 = uk.ac.shef.File.open(HF5sz)
//
//                       val readData_w: DataType = Dataset.parse(h5.get(HF5dataset + "_" + "w")).use{it.read(PlanarCoordinate(0))}
//                       val w_list = readData_w.dat as IntArray
//                       //println("dataw")
//                       val readData_h: DataType = Dataset.parse(h5.get(HF5dataset + "_" + "h")).use{it.read(PlanarCoordinate(0))}
//                       val h_list = readData_h.dat as IntArray
//                       //println("datah")
//                       val readData_numChannels: DataType = Dataset.parse(h5.get(HF5dataset + "_" + "numChannels")).use{it.read(PlanarCoordinate(0))}
//                       val numChannels_list = readData_numChannels.dat as IntArray
//                       //println("datanumChannels")
//                       val readData_pixelTypeCode: DataType = Dataset.parse(h5.get(HF5dataset + "_" + "pixelTypeCode")).use{it.read(PlanarCoordinate(0))}
//                       val pixelTypeCode_list = readData_pixelTypeCode.dat as IntArray
//                       //println("datapixeltypeCode")
//                       val readData_dataID: DataType = Dataset.parse(h5.get(HF5dataset + "_" + "dataID")).use{it.read(PlanarCoordinate(0))}
//                       val dataID_list = readData_dataID.dat as IntArray
//                       //println("datadataID")
//                       val readData_status: DataType = Dataset.parse(h5.get(HF5dataset + "_" + "status")).use{it.read(PlanarCoordinate(0))}
//                       val status_list = readData_status.dat as IntArray
//                       //println("datastatus")
//                       val readData_timeAcquired: DataType = Dataset.parse(h5.get(HF5dataset + "_" + "timeAcquired")).use{it.read(PlanarCoordinate(0))}
//                       val timeAcquired_list = readData_timeAcquired.dat as DoubleArray
//                       //println("datatimeAcquired")
//                       val dset_pix = Dataset.parse(h5.get(HF5dataset + "_" + "pix"))
//                       for (ff in 0..numRows - 1) {
//                           val readData_pix: DataType = dset_pix.read(PlanarCoordinate(ff.toLong()))
//                           //println("readData_pix")
//                           //todo
//                           if (pixelTypeCode_list[ff] == 3) {
//                               val pix = readData_pix.dat as ByteArray
//                               buffer[ff] = STRIMMPixelBuffer(
//                                   pix,
//                                   w_list[ff],
//                                   h_list[ff],
//                                   "Byte",
//                                   numChannels_list[ff],
//                                   timeAcquired_list[ff],
//                                   dataID_list[ff],
//                                   status_list[ff]
//                               )
//                           }
//                           else if (pixelTypeCode_list[ff] == 2) {
//                               val pix = readData_pix.dat as ShortArray
//                               buffer[ff] = STRIMMPixelBuffer(
//                                   pix,
//                                   w_list[ff],
//                                   h_list[ff],
//                                   "Short",
//                                   numChannels_list[ff],
//                                   timeAcquired_list[ff],
//                                   dataID_list[ff],
//                                   status_list[ff]
//                               )
//                           }
//                           else if (pixelTypeCode_list[ff] == 1) {
//                               val pix = readData_pix.dat as IntArray
//                               buffer[ff] = STRIMMPixelBuffer(
//                                   pix,
//                                   w_list[ff],
//                                   h_list[ff],
//                                   "Int",
//                                   numChannels_list[ff],
//                                   timeAcquired_list[ff],
//                                   dataID_list[ff],
//                                   status_list[ff]
//                               )
//                           }
//                           else if (pixelTypeCode_list[ff] == 0) {
//                               val pix = readData_pix.dat as LongArray
//                               buffer[ff] = STRIMMPixelBuffer(
//                                   pix,
//                                   w_list[ff],
//                                   h_list[ff],
//                                   "Long",
//                                   numChannels_list[ff],
//                                   timeAcquired_list[ff],
//                                   dataID_list[ff],
//                                   status_list[ff]
//                               )
//                           }
//                           else if (pixelTypeCode_list[ff] == 5) {
//                               val pix = readData_pix.dat as FloatArray
//                               buffer[ff] = STRIMMPixelBuffer(
//                                   pix,
//                                   w_list[ff],
//                                   h_list[ff],
//                                   "Float",
//                                   numChannels_list[ff],
//                                   timeAcquired_list[ff],
//                                   dataID_list[ff],
//                                   status_list[ff]
//                               )
//                           }
//                           else if (pixelTypeCode_list[ff] == 4) {
//                               val pix = readData_pix.dat as DoubleArray
//                               buffer[ff] = STRIMMPixelBuffer(
//                                   pix,
//                                   w_list[ff],
//                                   h_list[ff],
//                                   "Double",
//                                   numChannels_list[ff],
//                                   timeAcquired_list[ff],
//                                   dataID_list[ff],
//                                   status_list[ff]
//                               )
//                           }
//                           else  {
//                               buffer[ff] = STRIMMPixelBuffer(
//                                   null,
//                                   0,
//                                   0,
//                                   "NotSupported",
//                                   0,
//                                   0.0,
//                                   0,
//                                   0
//                               )
//                           }
//                       }
//                       dset_pix.close()
//                       h5.close()
//                   }
//                   else if (classSz == "class different STRIMMBuffer"){
//
//                   }
//                   return buffer.toList() as List<STRIMMBuffer>
//               }
//            }
//        reader.close()
//
//        return null
//    }
//    fun LoadHDF5(szH5File : String, szDesc : String) :  List<STRIMMBuffer>? {
//        var classSz = ""
//        var f = 0
//        val reader = CSVReader(FileReader(szDesc)).use { reader ->
//            val r = reader.readAll()
//            val rr = r[0]
//            classSz = rr[0]
//            f = rr[1].toInt()
//        }
//
//        val buffer = arrayOfNulls<STRIMMPixelBuffer?>(f)
//        if (classSz == "class uk.co.strimm.STRIMMPixelBuffer") {
//            val h5 = uk.ac.shef.File.open(szH5File)
//
//            val readData_w: DataType = Dataset.parse(h5["w"]).use{ it.read(PlanarCoordinate(0))}
//            val w_list = readData_w.dat as IntArray
//
//
//            val readData_h: DataType = Dataset.parse(h5["h"]).use{it.read(PlanarCoordinate(0))}
//            val h_list = readData_h.dat as IntArray
//
//
//            val readData_numChannels: DataType = Dataset.parse(h5["numChannels"]).use{it.read(PlanarCoordinate(0))}
//            val numChannels_list = readData_numChannels.dat as IntArray
//
//
//            val readData_pixelTypeCode: DataType = Dataset.parse(h5["pixelTypeCode"]).use{it.read(PlanarCoordinate(0))}
//            val pixelTypeCode_list = readData_pixelTypeCode.dat as IntArray
//
//
//            val readData_dataID: DataType = Dataset.parse(h5["dataID"]).use{it.read(PlanarCoordinate(0))}
//            val dataID_list = readData_dataID.dat as IntArray
//
//
//            val readData_status: DataType = Dataset.parse(h5["status"]).use{it.read(PlanarCoordinate(0))}
//            val status_list = readData_status.dat as IntArray
//
//
//            val readData_timeAcquired: DataType = Dataset.parse(h5["timeAcquired"]).use{it.read(PlanarCoordinate(0))}
//            val timeAcquired_list = readData_timeAcquired.dat as DoubleArray
//
//            val dset_pix = Dataset.parse(h5["pix"])
//            for (ff in 0..f - 1) {
//                val readData_pix: DataType = dset_pix.read(PlanarCoordinate(ff.toLong()))
//                //println("readData_pix")
//                //todo
//                if (pixelTypeCode_list[ff] == 3) {
//                    val pix = readData_pix.dat as ByteArray
//                    buffer[ff] = STRIMMPixelBuffer(
//                        pix,
//                        w_list[ff],
//                        h_list[ff],
//                        "Byte",
//                        numChannels_list[ff],
//                        timeAcquired_list[ff],
//                        dataID_list[ff],
//                        status_list[ff]
//                    )
//                }
//                else if (pixelTypeCode_list[ff] == 2) {
//                    val pix = readData_pix.dat as ShortArray
//                    buffer[ff] = STRIMMPixelBuffer(
//                        pix,
//                        w_list[ff],
//                        h_list[ff],
//                        "Short",
//                        numChannels_list[ff],
//                        timeAcquired_list[ff],
//                        dataID_list[ff],
//                        status_list[ff]
//                    )
//                }
//                else if (pixelTypeCode_list[ff] == 1) {
//                    val pix = readData_pix.dat as IntArray
//                    buffer[ff] = STRIMMPixelBuffer(
//                        pix,
//                        w_list[ff],
//                        h_list[ff],
//                        "Int",
//                        numChannels_list[ff],
//                        timeAcquired_list[ff],
//                        dataID_list[ff],
//                        status_list[ff]
//                    )
//                }
//                else if (pixelTypeCode_list[ff] == 0) {
//                    val pix = readData_pix.dat as LongArray
//                    buffer[ff] = STRIMMPixelBuffer(
//                        pix,
//                        w_list[ff],
//                        h_list[ff],
//                        "Long",
//                        numChannels_list[ff],
//                        timeAcquired_list[ff],
//                        dataID_list[ff],
//                        status_list[ff]
//                    )
//                }
//                else if (pixelTypeCode_list[ff] == 5) {
//                    val pix = readData_pix.dat as FloatArray
//                    buffer[ff] = STRIMMPixelBuffer(
//                        pix,
//                        w_list[ff],
//                        h_list[ff],
//                        "Float",
//                        numChannels_list[ff],
//                        timeAcquired_list[ff],
//                        dataID_list[ff],
//                        status_list[ff]
//                    )
//                }
//                else if (pixelTypeCode_list[ff] == 4) {
//                    val pix = readData_pix.dat as DoubleArray
//                    buffer[ff] = STRIMMPixelBuffer(
//                        pix,
//                        w_list[ff],
//                        h_list[ff],
//                        "Double",
//                        numChannels_list[ff],
//                        timeAcquired_list[ff],
//                        dataID_list[ff],
//                        status_list[ff]
//                    )
//                }
//                else  {
//                    buffer[ff] = STRIMMPixelBuffer(
//                        null,
//                        0,
//                        0,
//                        "NotSupported",
//                        0,
//                        0.0,
//                        0,
//                        0
//                    )
//                }
//            }
//            dset_pix.close()
//            h5.close()
//        }
//        else if (classSz == "class different STRIMMBuffer"){
//
//        }
//        return buffer.toList() as List<STRIMMBuffer>
//
//    }
}