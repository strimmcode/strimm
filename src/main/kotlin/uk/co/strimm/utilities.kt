package uk.co.strimm

//import com.google.common.collect.HashBiMap
//import com.google.gson.annotations.SerializedName

//import uk.co.strimm.gui.TraceSeries
//import uk.co.strimm.plugins.DataDescription
//import uk.co.strimm.plugins.PipelinePlugin
import net.imagej.Main
import net.imagej.overlay.Overlay
import org.apache.commons.collections.iterators.SingletonListIterator
import uk.co.strimm.experiment.ROI
import uk.co.strimm.gui.GUIMain
import uk.co.strimm.services.LoggerService
import java.awt.Image
import java.util.logging.Level
import javax.imageio.ImageIO
import javax.swing.ImageIcon

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

fun flattenData(incommingData : Any) : ArrayList<STRIMMBuffer>{
    val dataAsList = arrayListOf<STRIMMBuffer>()
    try {
        val stream = (incommingData as List<STRIMMBuffer>).stream().toArray()
        for (i in stream) {
            val iter = SingletonListIterator(i)
            while (iter.hasNext()) {
                /**
                 * The incoming data will be a list of lists if the number of inlets to the node is >1
                 * The incoming data will be a single item if the number of inlets to the node is ==1
                 */
                when(val item = iter.next()){
                    is List<*> -> {
                        val itemAsList = item as List<STRIMMBuffer>
                        dataAsList.add(itemAsList.first())
                    }
                    is STRIMMBuffer -> {
                        dataAsList.add(item)
                    }
                }
            }
        }
    }
    catch(ex : Exception){
        GUIMain.loggerService.log(Level.SEVERE, "Error flattening data. ${ex.message}")
        GUIMain.loggerService.log(Level.SEVERE, ex.stackTrace)
    }

    return dataAsList
}

//base data transfer class - know how to create the correct kind of H5 dataset object
//write its entry and read its entry
open class STRIMMBuffer(var dataID : Int, val status : Int, val className : String? = "STRIMMBuffer"){
    open var imageData : Any? = null
    open var traceData = arrayOf<Double>(dataID.toDouble(),status.toDouble())
    private var xDim = 1.toLong()
    private var yDim = 2.toLong()

    open fun getImageDataDims() : LongArray {
        return longArrayOf(0)
    }
    open fun getTraceDataDims() : LongArray{
        return longArrayOf(xDim,yDim)
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

var pixelTypeHashMap = hashMapOf(
    Pair(0, "Byte"),
    Pair(1, "Short"),
    Pair(2, "Int"),
    Pair(3, "Long"),
    Pair(4, "Float"),
    Pair(5, "Double"))
open class STRIMMSaveBuffer(val data : List<STRIMMBuffer>, val name : String)

open class STRIMMPixelBuffer(var pix : Any?, var w : Int, var h : Int, val pixelType : String, val numChannels : Int, var timeAcquired : Double, var cameraLabel: String, dataID : Int, status : Int) :
    STRIMMBuffer( dataID, status, "STRIMMPixelBuffer"){
    override var imageData = pix
    override var traceData = arrayOf<Double>(dataID.toDouble(), timeAcquired.toDouble())

    override fun getImageDataDims() : LongArray {
        //h,w,ch originally
        return longArrayOf(numChannels.toLong(), h.toLong(), w.toLong())
    }
    override fun getTraceDataDims() : LongArray{
        return longArrayOf(1,2)
    }

    override fun getImageDataType() : Int {
        for(kvp in pixelTypeHashMap){
            if(pixelType == kvp.value){
                return kvp.key
            }
        }

        return -1
    }

    override fun getImageDataMap(): HashMap<String , Double>{
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

//TODO either safe delete this class (used in SinkConsoleMethod and possibly elsewhere) or rename to something more appropriate
open class STRIMMSignalBuffer1(var data : Any?, val numChannels : Int, val channelNames : List<String>,  dataID : Int, status : Int) : STRIMMBuffer(dataID, status, "STRIMMSignalBuffer1"){
    override var traceData = data as Array<Double>
    override fun getTraceDataDims() : LongArray{
        return longArrayOf((traceData.size / numChannels).toLong(), numChannels.toLong())
    }
    override fun getTraceDataMap(): HashMap<String , Double>{
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

    /**
     * Used if the STRIMMSignalBuffer has been derived from a STRIMMPixelBuffer and we need to keep the pixel type
     * @see HistogramFlow
     */
    var imagePixelType = -1

    init{
        val ret = mutableListOf<Double>()
        for (sampleNumber in 0 until numSamples){
            ret.add(times!![sampleNumber])
            for (channelNumber in 0 until channelNames!!.size){
                ret.add(data!![sampleNumber*channelNames.size + channelNumber])
            }
        }
        traceData = ret.toTypedArray()
    }
    override fun getTraceDataDims() : LongArray{
        return longArrayOf(numSamples.toLong(),(1 + channelNames!!.size).toLong())
    }

    override fun getTraceDataMap(): HashMap<String , Double> {
    //these are the names that will be used in the h5 attributes in form  1   'times'   done this way else h5 puts them into alpha order
        val ret = hashMapOf<String, Double>()
        var cnt = 0
        ret["$cnt 'times'"] = 0.0
        for (ch in 0 until channelNames!!.size){
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

open class STRIMMImage(val images : List<ImageData>, dataID : Int, var messageSz :String , status : Int ) :
    STRIMMBuffer( dataID, status, "STRIMMImage"){
}
data class DisplayInfo(var displayName : String, var width : Long, var height : Long, var pixelType : String , var numChannels : Int, var previewInterval : Int, var lut : String )
//data class ResizeValues(val x : Long?, val y : Long?, val w : Long?, val h : Long?)
data class RoiInfo(val roi : ROI, val overlay : Overlay )

class HDFImageDataset{
    var frameNumber = 0
    var bitDepth = 0
    var byteData : ByteArray? = null
    var shortData : ShortArray? = null
    var floatData : FloatArray? = null
    var width = 0
    var height = 0
}
