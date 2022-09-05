package uk.co.strimm

import hdf.hdf5lib.HDF5Constants
import net.imagej.Main
import uk.co.strimm.gui.GUIMain
import java.awt.Image
import java.util.logging.Level
import javax.imageio.ImageIO
import javax.swing.ImageIcon

/*******FUNCTIONS*******/
/**
 * Set icons for custom buttons in the GUI
 * @param width The desired width
 * @param height The desired height
 * @param path The relative path to the image file
 * @param title Essentially the tooltip text
 * @return An ImageIcon object. This will be null if it cannot be found.
 */
fun setIcon(width: Int, height: Int, path: String, title: String = "") : ImageIcon? {
    return try {
        val img = ImageIO.read(Main::class.java.getResource(path))
        ImageIcon(img.getScaledInstance(width, height, Image.SCALE_SMOOTH))
    }
    catch (ex : Exception) {
        GUIMain.loggerService.log(Level.WARNING, "Could not load $title component icon from $path")
        GUIMain.loggerService.log(Level.WARNING, ex.stackTrace)
        null
    }
}

/**
 * Used to get the relative path to the experiment output folder
 * @param fileName The name of the file in the output folder
 * @return The relative path to the file in the output folder
 */
fun getOutputPathAndName(fileName: String) : String{
    val pathAndName = Paths.EXPERIMENT_OUTPUT_FOLDER + "/$fileName"
    return pathAndName
}

/**
 * Used to get the relative path to the experiment config folder
 * @param fileName The name of the file in the config folder
 * @return The relative path to the file in the config folder
 */
fun getConfigPathAndName(fileName: String) : String{
    val pathAndName = Paths.EXPERIMENT_CONFIG_FOLDER + "/$fileName"
    return pathAndName
}


/*******DATA CLASSES*******/
/**
 * Used by the Akka framework. Actors can acknowledge a message they have received
 */
enum class Acknowledgement {
    INSTANCE
}

/**
 * Used to specify the approximate layout of the dockable window. It will try to adhere to a 2 by x grid.
 * X, Y, Width, and Height coordinates will be relative and start at 1
 * @param x The x position of the window
 * @param y The y position of the window
 * @param width The width of the window
 * @param height The height of the window
 */
data class DockableWindowPosition(val x : Double, val y: Double, val width: Double, val height: Double)

/**
 * Base data transfer class that encapsulates data flowing through the program
 * @param dataID UniqueID of the data
 * @param status TODO is this even used?
 */
open class STRIMMBuffer(val dataID : Int, val status : Int){
    var matrixData = arrayOf(0,0,1,1)
    var vectorData = arrayOf(1.0,2.0)

    /**
     * Get the dimensions of the data
     * TODO this will vary depending on if trace or image data
     * @return The dimensions as a Long array
     */
    fun getDimensions(): LongArray{
        return longArrayOf(2,2)
    }

    /**
     * Get the data type in the context of the HDF5 implementation
     * TODO check that this will always be INT
     * @return The type which will be a HDF5 constant
     */
    fun getType(): Int{
        return HDF5Constants.H5T_NATIVE_INT
    }

    /**
     * Get all of the image data.
     * The key of the hashmap is TODO
     * @return The matrix data as a hashmap
     */
    fun getMatrixData(): HashMap<String , Double>{
        return hashMapOf()
    }

    /**
     * Get all of the trace data.
     * The key of the hashmap is TODO
     * @return The matrix data as a hashmap
     */
    fun getVectorData(): HashMap<String , Double>{
        return hashMapOf()
    }
}

/**
 * Class used to store data <i>before</i> writing to file
 */
open class STRIMMSaveBuffer(val data : List<STRIMMBuffer>, val name : String)

/**
 * Class used to store image data
 */
open class STRIMMImageBuffer(var pix : Any?, val w : Int, val h : Int, val bitDepth : Int, val timeAcquired : Number, dataID : Int, status : Int) :
    STRIMMBuffer( dataID, status)

/**
 * Class used to store pixel (trace) data
 */
open class STRIMMPixelBuffer(var pix : Any?, val w : Int, val h : Int, val pixelType : String, val numChannels : Int, var timeAcquired : Number, dataID : Int, status : Int) :
    STRIMMBuffer(dataID, status)

/**
 * TODO class description
 */
open class STRIMMSignalBuffer(var data : DoubleArray?, var times : DoubleArray?,  val numSamples : Int,  val channelNames : List<String>?,  dataID : Int, status : Int) :
    STRIMMBuffer( dataID, status)

/**
 * TODO class description
 */
class ImageData(var pix : Any?, val w : Int, val h : Int, val bitDepth : Int, val timeAcquired : Number, val imageCount : Int)

/**
 * TODO class description
 */
open class STRIMMImage(val images : List<ImageData>, dataID : Int, var messageSz :String , status : Int ) : STRIMMBuffer( dataID, status)

/**
 * TODO class description
 */
data class DisplayInfo(var displayName : String, var width : Long, var height : Long, var pixelType : String , var numChannels : Int, var previewInterval : Int )

/**
 * TODO class description
 */
data class ResizeValues(val x : Long?, val y : Long?, val w : Long?, val h : Long?)
