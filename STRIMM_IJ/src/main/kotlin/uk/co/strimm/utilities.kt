package uk.co.strimm

import com.google.common.collect.HashBiMap
import com.google.gson.annotations.SerializedName
import net.imagej.overlay.Overlay
import org.scijava.module.*
import org.scijava.plugin.PluginInfo
import uk.co.strimm.MicroManager.MMCameraDevice
import uk.co.strimm.gui.TraceSeries
import uk.co.strimm.plugins.DataDescription
import uk.co.strimm.plugins.PipelinePlugin
import uk.co.strimm.services.LoggerService
import java.awt.Image
import java.util.logging.Level
import javax.imageio.ImageIO
import javax.swing.ImageIcon
import kotlin.reflect.KType

sealed class Result<T> {
    data class Success<T>(val result : T) : Result<T>()
    data class Failure<T>(val message: String) : Result<T>()
}

fun <T, U> hashBiMapOf(vararg pairs : Pair<T, U>) : HashBiMap<T, U> {
    val map = HashBiMap.create<T, U>()
    pairs.forEach { (a, b) -> map[a] = b }
    return map
}

fun pairsToDataDescriptionHashMap(vararg pairs : Pair<String, KType>) =
        pairs.map { (a, b) -> DataDescription(a, b) }

fun <T,U> hashBiMapToHashMap(input : HashBiMap<T, U>): HashMap<T, U> =
        hashMapOf(*input.toList().toTypedArray())

fun getPipelinePluginReadableName(info : PluginInfo<PipelinePlugin>) =
        info.pluginClass.getDeclaredMethod("getReadableName").invoke("null") as String?

/**
 * Utility function to specify icon for GUI component
 * @param width The width of the first button in ImageJ's toolbar as reference
 * @param height The height of the first button in ImageJ's toolbar as reference
 * @param path Relative path of the icon image
 * @param title Title of the GUI component
 * @param isButton Is the GUI component a button
 *  @param loggerService msg logger
 * @return an ImageIcon object or null if image not found in specified path
 */
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

class LambdaModuleInfo(val lambdaIn : () -> Unit) : DefaultMutableModuleInfo() {
    override fun createModule(): Module {
        return (super.createModule() as LambdaModule).apply { lambda = lambdaIn }
    }

    class LambdaModule : DefaultMutableModule() {
        lateinit var lambda : () -> Unit

        override fun run() {
            lambda.invoke()
            super.run()
        }
    }
}

enum class TraceRenderType {
    @SerializedName("TraceRenderType1")
    RENDER_AND_CLEAR,
    @SerializedName("TraceRenderType2")
    RENDER_AND_OVERWRITE,
    @SerializedName("TraceRenderType3")
    RENDER_AND_SCROLL,
    @SerializedName("TraceRenderType4")
    RESIZE_AS_NEEDED;
}

enum class ExperimentTreeType {
    INPUT_FEEDS,CONDITIONS,COMMANDS
}

enum class Acknowledgement {
    INSTANCE
}

/**
 * This class is used to specify the position of a dockable window. Remember all units here actually arbitrary values,
 * not pixel values. See docking frames documentation section 5.2
 */
data class DockableWindowPosition(val x : Double, val y: Double, val width: Double, val height: Double)

/**
 * This data class is used whenever dealing with images taken from a camera device
 * @param sourceCamera The label (device name) is of the source camera
 * @param pix The raw data of the image (as ByteArray, ShortArray, or FloatArray)
 * @param timeAcquired The time the image was acquired
 */
data class STRIMMImage(val sourceCamera : String, val pix : Any?, val timeAcquired : Number, val imageCount : Int, val w : Int, val h : Int)

/**
 * This data class is used for any ROI calculation
 * @param data A pair containing the ROI (overlay) object, and the calculated value
 * @param timeAcquired The corresponding time point (either from a camera device or trace device)
 */
data class TraceData(var data : Pair<Overlay?,Double>, val timeAcquired: Number)

/**
 * This data class is used when storing trace data
 * @param timeAcquired The time the trace data point was acquired
 * @param roi The overlay object if the data have from from an ROI calculation (null if it has come from a trace device)
 * @param roiVal The value for the trace data point
 * @param dataPointNumber The data point number (index) of the trace data point
 */
data class TraceDataStore(val timeAcquired : Number, val roi : Overlay?, val roiVal : Number, val dataPointNumber : Number, val flowName : String, val roiNumber : Int)

/**
 * This data class is used when storing camera data
 * @param timeAcquired The time the camera frame was acquired
 * @param cameraFeedName The name of the camera feed that was created
 * @param frameNumber The number (index) of the camera frame
 */
data class CameraMetaDataStore(val timeAcquired : Number, val cameraFeedName : String, val frameNumber : Number)

/**
 * This data class is used when loading camera devices from config
 * @param device The camera device
 * @param live Indicating if the camera should be live or not
 * @param exposureMillis The camera's exposure in milliseconds
 * @param intervalMillis The acquisition interval in milliseconds
 */
data class CameraDeviceInfo(var device : MMCameraDevice,  var live : Boolean, var exposureMillis : Double,
                            var intervalMillis : Double, var feedName : String, var label : String)

data class DisplayInfo(var primaryDevice : String, var bitDepth : Long,  var width : Long, var height : Long, var feedName : String)

data class CameraDeviceInfoTW(var bitDepth : Long,  var width : Long, var height : Long, var feedName : String)

/**
 * This data class is used when resizing a camera feed
 * @param x The origin x coordinate
 * @param y The origin y coordinate
 * @param w The new width
 * @param h The new height
 */
data class ResizeValues(val x : Long?, val y : Long?, val w : Long?, val h : Long?)

/**
 * Class used to gather data for export. This is used exclusively when saving a trace from the trace feed, as opposed
 * to saving data from an acquisition
 * @param result The result of the export dialog (ok or cancel)
 * @param extension The type of extension to save the data to
 * @param delimiter The type of delimiter to use for the output data
 * @param seriesExport The data to export as a HashMap, the pair corresponds to the start time and end time of the export
 */
data class ExportSettings(val result: Boolean, val extension: String, val delimiter: String?, val seriesExport : HashMap<TraceSeries, Pair<Double, Double>>)

data class TraceDataWithFrameNumbers(val data : Pair<List<Int>,List<ArrayList<TraceData>>>)