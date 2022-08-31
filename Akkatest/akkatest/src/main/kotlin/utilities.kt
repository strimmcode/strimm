import net.imagej.overlay.Overlay
import java.util.ArrayList

data class STRIMMImage(val sourceCamera : String, val pix : Any?, val timeAcquired : Number, val imageCount : Int, val w : Int, val h : Int)

data class TraceData(var data : Pair<Overlay?,Double>, val timeAcquired: Number)

enum class Acknowledgement {
    INSTANCE
}

interface AcquisitionMethod {
    var name: String
    var description: String
    fun runMethod(vararg inputs: Any): Any
}
abstract class TraceMethod : AcquisitionMethod {
    override fun runMethod(vararg inputs: Any): ArrayList<TraceData> {
        return arrayListOf(TraceData(Pair(null, 0.0), 0))//TODO dummy
    }
}
abstract class ImageMethod : AcquisitionMethod {
    override fun runMethod(vararg inputs: Any): STRIMMImage {
        return STRIMMImage("", null, 0,0, 0, 0) //TODO dummy
    }
}