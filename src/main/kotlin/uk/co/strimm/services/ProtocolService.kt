package uk.co.strimm.services


//import com.fazecast.jSerialComm.SerialPort
import net.imagej.ImageJService
import org.scijava.plugin.Plugin
import org.scijava.service.AbstractService
import org.scijava.service.Service

@Plugin(type = Service::class)
class ProtocolService : AbstractService(), ImageJService  {
    public var jdaq = JDAQ()
    fun WinBeep(freq: Int, dur: Int): Int {
        return jdaq.WinBeep(freq, dur)
    }
    fun WinInitSpeechEngine(): Int {
        return jdaq.WinInitSpeechEngine()
    }
    fun WinShutdownSpeechEngine(): Int {
        return jdaq.WinShutdownSpeechEngine()
    }
    fun WinSpeak(outSz1: String?): Int {
        return jdaq.WinSpeak(outSz1, true)
    }
    fun WinAlert() : Int {
        return jdaq.WinAlert()
    }
    fun GDIPrintText(
        data_w: Int, data_h: Int, data: ShortArray?,
        szText: String?, xPos: Double, yPos: Double, fontSize: Int
    ): Int {
        return jdaq.GDIPrintText(data_w,data_h,data,szText,xPos,yPos,fontSize)
    }
    fun GDI_Write_Numbers_onto_Array(nums : IntArray?) : Int {
        return jdaq.GDIWriteNumbersOntoArray(nums)
    }
    fun GDI_Test_Write_Array(data_w : Int, data_h : Int, data: ShortArray?, numBins : Int, numSeries : Int, seriesName : String, xMin : Double, xMax : Double, yValues : DoubleArray?): Int {
        return jdaq.GDITestWriteArray(data_w, data_h, data, numBins, numSeries, seriesName, xMin, xMax, yValues)
    }
    fun GDI_Create_FLFM_Image_File(id : Int, width : Int, height : Int, pix : ShortArray?, fileSz : String, count : Int) : Int{
        return jdaq.GDICreateFLFMImageFile(id, width, height, pix, fileSz, count)
    }
    fun GDIFLFMInit(): Int{
        return jdaq.GDIFLFMInit();
    }
    fun GDI_Create_FLFM_Montage(
        id: Int,
        data_w: Int,
        data_h: Int,
        data: ShortArray?,
        data_wt: Int,
        data_ht: Int,
        data1: ShortArray?
    ): Int {
        return jdaq.GDICreateFLFMMontage(id, data_w, data_h, data, data_wt, data_ht, data1)
    }
    fun GDI_Create_FLFM_Image(id: Int, data_w: Int, data_h: Int, data: ShortArray?): Int{
        return jdaq.GDICreateFLFMImage(id, data_w, data_h, data)
    }
}