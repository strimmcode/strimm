package uk.co.strimm

import akka.actor.ActorRef
import com.google.gson.annotations.SerializedName
import hdf.hdf5lib.HDF5Constants
import net.imagej.Main
import net.imagej.overlay.Overlay
import org.scijava.module.*
import org.scijava.plugin.PluginInfo

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

fun getOutputPathAndName(fileName: String) : String{
    val pathAndName = Paths.EXPERIMENT_OUTPUT_FOLDER + "/$fileName"
    return pathAndName
}

fun getConfigPathAndName(fileName: String) : String{
    val pathAndName = Paths.EXPERIMENT_CONFIG_FOLDER + "/$fileName"
    return pathAndName
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
open class STRIMMBuffer(val dataID : Int, val status : Int){
    var matrix_data = arrayOf<Int>(0,0,1,1)
    var vector_data = arrayOf<Double>(1.0,2.0)

    fun getDims(): LongArray{
        return longArrayOf(2,2)
    }

    fun getType():Int{
        return HDF5Constants.H5T_NATIVE_INT
    }

    fun getMatrixDataMap(): HashMap<String , Double>{
        return hashMapOf("hi" to 0.0 , "there" to 1.0)
    }

    fun getVectorDataMap(): HashMap<String , Double>{
        return hashMapOf("v" to 0.0 , "w" to 1.0)
    }
}

open class STRIMMSaveBuffer(val data : List<STRIMMBuffer>, val name : String){
}

open class STRIMMImageBuffer(var pix : Any?, val w : Int, val h : Int, val bitDepth : Int, val timeAcquired : Number, dataID : Int, status : Int) :
    STRIMMBuffer( dataID, status){
    }

open class STRIMMPixelBuffer(var pix : Any?, val w : Int, val h : Int, val pixelType : String, val numChannels : Int, var timeAcquired : Number, dataID : Int, status : Int) :
    STRIMMBuffer(dataID, status){
}

open class STRIMMSignalBuffer(var data : DoubleArray?, var times : DoubleArray?,  val numSamples : Int,  val channelNames : List<String>?,  dataID : Int, status : Int) :
    STRIMMBuffer( dataID, status){
        //arrange data src1, src2, src3; etc and then they are easy to join

}
class ImageData(var pix : Any?, val w : Int, val h : Int, val bitDepth : Int, val timeAcquired : Number, val imageCount : Int){

}

open class STRIMMImage(val images : List<ImageData>, dataID : Int, var messageSz :String , status : Int ) :
    STRIMMBuffer( dataID, status){
}
data class DisplayInfo(var displayName : String, var width : Long, var height : Long, var pixelType : String , var numChannels : Int, var previewInterval : Int )
data class ResizeValues(val x : Long?, val y : Long?, val w : Long?, val h : Long?)
