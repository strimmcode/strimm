package uk.co.strimm.services


import com.opencsv.CSVReader
import com.opencsv.CSVWriter
import net.imagej.ImageJService
import org.scijava.plugin.Plugin
import org.scijava.service.AbstractService
import org.scijava.service.Service
import uk.co.strimm.experiment.ROI
import uk.co.strimm.gui.GUIMain
import java.io.*
import java.util.logging.Level
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
//all working on 3/5/22
//tested with ImageJ ROIManager


//Format of .roi
//73,111,117,116
//0,228
//1,0  (or 2,0 foe ellipse)   ROItype
//0,102  (short top)
// (short left)
// (short bottom)
// (short right)
//zeros until 63rd byte which is = 64
//zeros until 83rd byte which is = 128
//zeros until 127
//from 128 name of the file in ascii where each character is a short (maybe unicode)
@Plugin(type = Service::class)
class StrimmROIService : AbstractService(), ImageJService {

    //both of the functions below decide what to do based on the file extension of refSz
    //DecodeROIReference    enter the file as zip, roi (ImageJ ROIManager formats), or csv to produce an array of ROI. The CSV requires a header and the [name (Int), type (1 | 2), x (double), y, w, h] per row.
    fun DecodeROIReference(refSz : String) : List<ROI>{
        val extension = refSz.substring(refSz.lastIndexOf(".") + 1).toLowerCase()
        if (extension == "csv"){
            return DecodeCSV(refSz)
        }
        else if (extension == "zip"){
            return DecodeZIP(refSz)
        }
        else if (extension == "roi"){
            return DecodeROI(refSz)
        }
        else{
            println("File extension not recognised")
        }
        return arrayListOf<ROI>()
    }
    //EncodeROIReference    enter array of ROI and will encode and save as ZIP, ROI (for 1 ROI), or CSV
    fun EncodeROIReference(refSz : String, rois : List<ROI>) : Boolean{
        val extension = refSz.substring(refSz.lastIndexOf(".") + 1).toLowerCase()
        when (extension) {
            "csv" -> return EncodeCSV(refSz, rois)
            "zip" -> return EncodeZIP(refSz, rois)
            "roi" -> return EncodeROI(refSz, rois)
            else -> GUIMain.loggerService.log(Level.SEVERE, "File extension for encoding ROI not recognised")
        }
        return false
    }


    private fun DecodeCSV(csvSz : String) : List<ROI>{
        var rois = mutableListOf<ROI>()
        val fileReader = BufferedReader(FileReader(csvSz))
        val csvReader = CSVReader(fileReader)
        //discard the header line
        //which is name, type, x,y,w,h
        csvReader.readNext()
        var csvRow = csvReader.readNext()
        while(csvRow != null) {
//            val name = csvRow[0].toInt()
            val type = csvRow[1].toInt()

            //fill in an ROI
            var roi : ROI = ROI()
            roi.x = csvRow[2].toDouble()
            roi.w = csvRow[4].toDouble()
            roi.y = csvRow[3].toDouble()
            roi.h = csvRow[5].toDouble()
            if (type.toInt() == 1){
                roi.ROItype = "RECTANGLE"
            }
            else if (type.toInt() == 2){
                roi.ROItype = "ELLIPSE"
            }
            rois.add(roi)
            csvRow = csvReader.readNext()
        }
        csvReader.close()
        return rois

    }

    private fun DecodeROI(roiSz : String) : List<ROI>{
        var rois = mutableListOf<ROI>()

        var dis = java.io.DataInputStream(FileInputStream(roiSz))
        dis.readShort()
        dis.readShort()
        dis.readShort()
        var type = dis.readByte()

        dis.readByte()
        var top = dis.readShort()
        var left = dis.readShort()
        var bottom = dis.readShort()
        var right = dis.readShort()

        println(type.toString() + " " + top + " " + left + " " + bottom + " " + right)
        var roi : ROI = ROI()
        roi.x = left.toDouble()
        roi.w = (right - left).toDouble()
        roi.y = top.toDouble()
        roi.h = (bottom - top).toDouble()
        if (type.toInt() == 1){
            roi.ROItype = "RECTANGLE"
        }
        else if (type.toInt() == 2){
            roi.ROItype = "ELLIPSE"
        }
        rois.add(roi)
        dis.close()
        return rois
    }

    private fun DecodeZIP(zipSz : String) : List<ROI> {
        var rois = mutableListOf<ROI>()
        try {
            val destDir = File("_TEMP_ROI_FOLDER")
            destDir.mkdir()

            val buffer = ByteArray(1024)
            val zis = ZipInputStream(FileInputStream(zipSz))
            var zipEntry = zis.nextEntry
            while (zipEntry != null) {
                val newFile = ROIDecoder_newFile(destDir, zipEntry)
                if (zipEntry.isDirectory) {
                    if (!newFile!!.isDirectory && !newFile.mkdirs()) {
                        throw IOException("Failed to create directory $newFile")
                    }
                } else {
                    // fix for Windows-created archives
                    val parent = newFile!!.parentFile
                    if (!parent.isDirectory && !parent.mkdirs()) {
                        throw IOException("Failed to create directory $parent")
                    }

                    // write file content
                    val fos = FileOutputStream(newFile)
                    var len: Int = 0
                    while (zis.read(buffer).also { len = it } > 0) {
                        fos.write(buffer, 0, len)
                    }
                    fos.close()
                }
                zipEntry = zis.nextEntry
            }
            zis.closeEntry()
            zis.close()


            val files: Array<File> = destDir.listFiles()
            for (i in files.indices) {
                if (files[i].isFile) { //this line weeds out other directories/folders
                    var dis = java.io.DataInputStream(FileInputStream(files[i]))
                    dis.readShort()
                    dis.readShort()
                    dis.readShort()
                    var type = dis.readByte()

                    dis.readByte()
                    var top = dis.readShort()
                    var left = dis.readShort()
                    var bottom = dis.readShort()
                    var right = dis.readShort()

                    println(type.toString() + " " + top + " " + left + " " + bottom + " " + right)
                    var roi : ROI = ROI()
                    roi.x = left.toDouble()
                    roi.w = (right - left).toDouble()
                    roi.y = top.toDouble()
                    roi.h = (bottom - top).toDouble()
                    if (type.toInt() == 1){
                        roi.ROItype = "RECTANGLE"
                    }
                    else if (type.toInt() == 2){
                        roi.ROItype = "ELLIPSE"
                    }
                    rois.add(roi)
                    dis.close()
                    files[i].delete()
                }
            }

            destDir.delete()
        } catch (ex: Exception) {
        }


        return rois

    }

    private fun EncodeZIP(zipSz : String, rois : List<ROI>) : Boolean {
        val destDir = File("_TEMP_ROI_FOLDER")
        destDir.mkdir()
        val fos = FileOutputStream(zipSz)
        val zipOut = ZipOutputStream(fos)
        for (f in 0..rois.size-1){
            var type = if (rois[f].ROItype=="RECTANGLE") 1 else 2
            var top = (rois[f].y).toInt()
            var left = (rois[f].x).toInt()
            var bottom = (top + rois[f].h).toInt()
            var right = (left + rois[f].w).toInt()
            //save as .f.rois
            var roiFileName = destDir.name + "\\" + f.toString() + ".roi"
            var dis = java.io.DataOutputStream(FileOutputStream(roiFileName))
            dis.writeByte(73)
            dis.writeByte(111)
            dis.writeByte(117)
            dis.writeByte(116)

            dis.writeShort(228)
            dis.writeByte(type)
            dis.writeByte(0)
            dis.writeShort(top)
            dis.writeShort(left)
            dis.writeShort(bottom)
            dis.writeShort(right)

            for (f in 16..62){
                dis.writeByte(0)
            }
            dis.writeByte(64)
            for (f in 64..82){
                dis.writeByte(0)
            }
            dis.writeByte(128)
            for (f in 84..127){
                dis.writeByte(0)
            }
            dis.writeShort(48+f)
            dis.close()
            val fileToZip = File(roiFileName)
            zipFile(fileToZip, fileToZip.name, zipOut)
            fileToZip.delete()
        }
        zipOut.close()
        fos.close()
        destDir.delete()

        return true
    }

    private fun EncodeROI(roiSz : String, rois : List<ROI>) : Boolean {
        val roi = rois[0]
        val type = if (roi.ROItype == "RECTANGLE") 1 else 2
        val top = roi.y
        val bottom = roi.h + top
        val left = roi.x
        val right = roi.w + left

        var dis = java.io.DataOutputStream(FileOutputStream(roiSz))
        dis.writeByte(73)
        dis.writeByte(111)
        dis.writeByte(117)
        dis.writeByte(116)

        dis.writeShort(228)
        dis.writeByte(type)
        dis.writeByte(0)
        dis.writeShort(top.toInt())
        dis.writeShort(left.toInt())
        dis.writeShort(bottom.toInt())
        dis.writeShort(right.toInt())

        for (f in 16..62){
            dis.writeByte(0)
        }
        dis.writeByte(64)
        for (f in 64..82){
            dis.writeByte(0)
        }
        dis.writeByte(128)
        for (f in 84..127){
            dis.writeByte(0)
        }
        dis.writeShort(48+0) //since .roi is only for 1 roi (else it is zipped) its name is 0
        dis.close()

        return true
    }

    private fun EncodeCSV(roiCSV : String, rois : List<ROI>) : Boolean{
        GUIMain.loggerService.log(Level.INFO, "Writing ROI data to CSV $roiCSV")
        try {
            val fileWriter = FileWriter("./$roiCSV")
            val csvWriter = CSVWriter(fileWriter)
            val header = arrayOf("name", "type", "x", "y", "w", "h")
            csvWriter.writeNext(header, false)
            for (f in 0 until rois.size) {
                val type = if (rois[f].ROItype == "RECTANGLE") 1 else 2
                val x = rois[f].x
                val y = rois[f].y
                val w = rois[f].w
                val h = rois[f].h
                val data =
                    arrayOf(f.toString(), type.toString(), x.toString(), y.toString(), w.toString(), h.toString())
                csvWriter.writeNext(data, false)
            }
            csvWriter.close()
            GUIMain.loggerService.log(Level.INFO, "Finished writing ROI data to CSV $roiCSV")
            return true
        }
        catch(ex : Exception){
            GUIMain.loggerService.log(Level.SEVERE, "Error writing ROI data to CSV. Message: ${ex.message}")
            GUIMain.loggerService.log(Level.SEVERE, ex.stackTrace)
            return false
        }
    }

    private fun ROIDecoder_newFile(destinationDir: File, zipEntry: ZipEntry): File? {
        val destFile = File(destinationDir, zipEntry.name)
        val destDirPath = destinationDir.canonicalPath
        val destFilePath = destFile.canonicalPath
        if (!destFilePath.startsWith(destDirPath + File.separator)) {
            throw IOException("Entry is outside of the target dir: " + zipEntry.name)
        }
        return destFile
    }
    private fun zipFile(fileToZip: File, fileName: String, zipOut: ZipOutputStream) {
        if (fileToZip.isHidden) {
            return
        }
        if (fileToZip.isDirectory) {
            if (fileName.endsWith("/")) {
                zipOut.putNextEntry(ZipEntry(fileName))
                zipOut.closeEntry()
            } else {
                zipOut.putNextEntry(ZipEntry("$fileName/"))
                zipOut.closeEntry()
            }
            val children = fileToZip.listFiles()
            for (childFile in children) {
                zipFile(childFile, fileName + "/" + childFile.name, zipOut)
            }
            return
        }
        val fis = FileInputStream(fileToZip)
        val zipEntry = ZipEntry(fileName)
        zipOut.putNextEntry(zipEntry)
        val bytes = ByteArray(1024)
        var length: Int = 0
        while (fis.read(bytes).also { length = it } >= 0) {
            zipOut.write(bytes, 0, length)
        }
        fis.close()
    }
}

