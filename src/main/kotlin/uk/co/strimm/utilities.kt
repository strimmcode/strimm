package uk.co.strimm

import akka.actor.ActorRef
import com.google.gson.annotations.SerializedName
import hdf.hdf5lib.HDF5Constants
import net.imagej.Main
//import com.google.common.collect.HashBiMap
//import com.google.gson.annotations.SerializedName
import net.imagej.overlay.Overlay
import org.scijava.module.*
import org.scijava.plugin.PluginInfo
import scala.Byte

import uk.co.strimm.experiment.ROI
//import uk.co.strimm.gui.TraceSeries
//import uk.co.strimm.plugins.DataDescription
//import uk.co.strimm.plugins.PipelinePlugin
import uk.co.strimm.services.LoggerService
import java.awt.Image
import java.util.logging.Level
import javax.imageio.ImageIO
import javax.swing.ImageIcon
import kotlin.reflect.KType

fun setIcon(width: Int, height: Int, path: String, title: String = "",
            loggerService: LoggerService, isButton: Boolean = true) : ImageIcon? {

    try {
        val img = ImageIO.read(Main::class.java.getResource(path))
        return ImageIcon(img.getScaledInstance(width, height, Image.SCALE_SMOOTH))
    }
    catch (ex : Exception) {
        val addMsg = if (isButton) { title.plus(" button icon") } else { title }
        loggerService.log(Level.WARNING, "Could not load ".plus(addMsg))
        loggerService.log(Level.WARNING, ex.stackTrace)
    }
    return null
}
enum class Acknowledgement {
    INSTANCE
}
data class DockableWindowPosition(val x : Double, val y: Double, val width: Double, val height: Double)

//base data transfer class - know how to create the correct kind of H5 dataset object
//write its entry and read its entry
//
//
//
//
//
//
//
open class STRIMMBuffer(var dataID : Int, val status : Int, val className : String? = "STRIMMBuffer"){
    open var imageData : Any? = null
    open var traceData = arrayOf<Double>(dataID.toDouble(),status.toDouble())
    open fun getImageDataDims() : LongArray {
        return longArrayOf(0)
    }
    open fun getTraceDataDims() : LongArray{
        return longArrayOf(1,2)
    }
    open fun getImageDataType() : Int {
        return -1 //Long
    }

    open fun getImageDataMap(): HashMap<String , Double>{
        return hashMapOf("w" to 0.0 , "h" to 0.0, "bitDepth" to -1.0)
    }

    open fun getTraceDataMap(): HashMap<String , Double>{
        return hashMapOf("dataID" to 9999.0 , "status" to 9999.0)
    }
}
open class STRIMMSaveBuffer(val data : List<STRIMMBuffer>, val name : String){
}
open class STRIMMPixelBuffer(var pix : Any?, val w : Int, val h : Int, val pixelType : String, val numChannels : Int, var timeAcquired : Double, dataID : Int, status : Int) :
    STRIMMBuffer( dataID, status, "STRIMMPixelBuffer"){
    override var imageData = pix
    override open var traceData = arrayOf<Double>(dataID.toDouble(), timeAcquired.toDouble())
    override open fun getImageDataDims() : LongArray {
        //h,w,ch originally
        return longArrayOf(numChannels.toLong(), h.toLong(), w.toLong())
    }
    override open fun getTraceDataDims() : LongArray{
        return longArrayOf(1,2)
    }
    override open fun getImageDataType() : Int {
        if (pixelType == "Byte"){
            return 0
        }
        else if (pixelType == "Short"){
            return 1
        }
        else if (pixelType == "Int"){
            return 2
        }
        else if (pixelType == "Long"){
            return 3
        }
        else if (pixelType == "Float"){
            return 4
        }
        else if (pixelType == "Double"){
            return 5
        }
        return -1
    }

    override open fun getImageDataMap(): HashMap<String , Double>{
        var bitDepth = 0
        if (pixelType == "Byte"){
            bitDepth = 8
        }
        else if (pixelType == "Short"){
            bitDepth = 16
        }
        else if (pixelType == "Int"){
            bitDepth = 32
        }
        else if (pixelType == "Long"){
            bitDepth = 64
        }
        else if (pixelType == "Float"){
            bitDepth = 32
        }
        else if (pixelType == "Double"){
            bitDepth = 64
        }
        return hashMapOf("0 'w'" to w.toDouble() , "1 'h'" to h.toDouble(), "2 'numChannels'" to numChannels.toDouble(), "3 'bitDepth'" to bitDepth.toDouble())
    }

    override open fun getTraceDataMap(): HashMap<String , Double>{
        return hashMapOf("0 'dataID'" to 9999.0 , "1 'timeAcquired'" to 9999.0)
    }

}




open class STRIMMImageBuffer(var pix : Any?, val w : Int, val h : Int, val bitDepth : Int, val timeAcquired : Number, dataID : Int, status : Int) :
    STRIMMBuffer( dataID, status, "STRIMMImageBuffer"){ //keep STRIMMBuffer like this - but make sure they are not saved
    //override var imageData = pix as List<Any>
    override open var traceData = arrayOf<Double>(dataID.toDouble(), timeAcquired.toDouble())
    override open fun getImageDataDims() : LongArray {
        return longArrayOf(w.toLong(),h.toLong())
    }
    override open fun getTraceDataDims() : LongArray{
        return longArrayOf(1,2)
    }
    override open fun getImageDataType() : Int {
        return 0 //Byte
    }

    override open fun getImageDataMap(): HashMap<String , Double>{
        return hashMapOf("w" to w.toDouble() , "h" to h.toDouble(), "bitDepth" to bitDepth.toDouble())
    }

    override open fun getTraceDataMap(): HashMap<String , Double>{
        return hashMapOf("dataID" to 9999.0 , "timeAcquired" to 9999.0)
    }
}
//continuous mode  (fixed mode is a subset)
//can set to 0 to reset, play a sequence over and over until it receives another STRIMMBuffer message
//simpleProtocol mode
//either from a file, or from data
open class STRIMMArduinoControlBuffer(
                                      var arduinoMode : String,
                                      var bFromFile : Boolean,
    //   "continousMode",  "fixedMode",  "simpleProtocolMode"
                                      //file protocol
                                      var szFolder : String,
                                      var simpleProtocol : String,
                                      //data protocol
                                      var dataOut : IntArray,
                                      var dataOutChannels : IntArray,
                                      var timeMicros : Long,
                                      //
                                      dataID:Int, status : Int) :
    STRIMMBuffer(dataID, status, "STRIMMArduinoControlBuffer"){
}
open class STRIMMNIDAQControlBuffer(
    var pTimes : DoubleArray,
    var pDataAO : DoubleArray, var AOChannels : List<String>, var pDataDO : IntArray, var DOport : Int, var DOChannels : List<String>,
    dataID : Int, status : Int) : STRIMMBuffer(dataID, status, "STRIMMNIDAQControlBuffer") {

}
open class STRIMMNIDAQBuffer(
    var numSamples : Int,
    var sampleFreqMS : Double,
    var pTimes : DoubleArray?,
    var pDataAO : DoubleArray?, var AOChannels : IntArray?,
    var pDataAI : DoubleArray?, var AIChannels : IntArray?,
    var pDataDO : IntArray?, var DOChannels : IntArray?, var DOport : Int,
    var pDataDI : IntArray?, var DIChannels : IntArray?, var DIport : Int,
    dataID : Int, status : Int) : STRIMMBuffer(dataID, status, "STRIMMNIDAQBuffer") {

}

open class STRIMMSignalBuffer1(var data : Any?, val numChannels : Int, val channelNames : List<String>,  dataID : Int, status : Int) :
    STRIMMBuffer(dataID, status, "STRIMMSignalBuffer1"){
    override open var traceData = data as Array<Double>
    override open fun getTraceDataDims() : LongArray{
        return longArrayOf((traceData.size / numChannels).toLong(), numChannels.toLong())
    }
    override open fun getTraceDataMap(): HashMap<String , Double>{
        val ret = hashMapOf<String, Double>()
        ret["numChannels"] = numChannels.toDouble()
        for (ch in channelNames){
            ret[ch] = 9999.0
        }
        return ret
    }
    }


open class STRIMMSignalBuffer(var data : DoubleArray?, var times : DoubleArray?,  val numSamples : Int,  val channelNames : List<String>?,  dataID : Int, status : Int) :
    STRIMMBuffer( dataID, status, "STRIMMSignalBuffer"){
        override var traceData = arrayOf<Double>()
        init{
            val cnt = 0
            val ret = mutableListOf<Double>()
            for (f in 0..numSamples-1){
                ret.add(times!![f])
                for (ff in 0..channelNames!!.size-1){
                    ret.add(data!![f*channelNames!!.size + ff])
                }

            }
            traceData = ret.toTypedArray()
        }
        override fun getTraceDataDims() : LongArray{
            return longArrayOf(numSamples.toLong(),(1 + channelNames!!.size).toLong())
        }
//        override fun getTraceDataMap(): HashMap<String , Double> {
//
//            val ret = hashMapOf<String, Double>()
//            ret["times"] = 0.0
//            for (ch in 0..channelNames!!.size-1){
//                ret[channelNames!![ch]] = 0.0
//            }
//            return ret
//        }
        override fun getTraceDataMap(): HashMap<String , Double> {
        //these are the names that will be used in the h5 attributes in form  1   'times'   done this way else h5 puts them into alpha order
            val ret = hashMapOf<String, Double>()
            var cnt = 0
            ret[cnt.toString() + " 'times'"] = 0.0
            for (ch in 0..channelNames!!.size-1){
                cnt++
                ret[cnt.toString() + " '" + channelNames!![ch] + "'"] = 0.0
            }
            return ret
        }
}

//Packages a group of images obtained by say a fast camera
open class STRIMMSequenceCameraDataBuffer(
    var data : List<STRIMMPixelBuffer>,
    dataID:Int, status : Int) :
    STRIMMBuffer(dataID, status, "STRIMMSequenceCameraDataBuffer"){
}

open class STRIMMXYStageBuffer(
    var szCommand : String,
    var data : List<Double>,
    dataID : Int,
    status : Int
) : STRIMMBuffer(dataID, status, "STRIMMXYStageBuffer"){

}

open class STRIMM_MMCommandBuffer(
    var szCommand : String,
    var data : Any,
    dataID : Int,
    status : Int
) : STRIMMBuffer(dataID, status, "STRIMMXYStageBuffer"){

}







class ImageData(var pix : Any?, val w : Int, val h : Int, val bitDepth : Int, val timeAcquired : Number, val imageCount : Int){

}
//
open class STRIMMImage(val images : List<ImageData>, dataID : Int, var messageSz :String , status : Int ) :
    STRIMMBuffer( dataID, status, "STRIMMImage"){
}
data class DisplayInfo(var displayName : String, var width : Long, var height : Long, var pixelType : String , var numChannels : Int, var previewInterval : Int, var lut : String )
//data class ResizeValues(val x : Long?, val y : Long?, val w : Long?, val h : Long?)
data class RoiInfo(val roi : ROI, val overlay : Overlay )
