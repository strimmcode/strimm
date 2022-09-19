package uk.co.strimm.services


//import com.fazecast.jSerialComm.SerialPort
import net.imagej.ImageJService
import org.scijava.plugin.Plugin
import org.scijava.service.AbstractService
import org.scijava.service.Service
import uk.co.strimm.experiment.ExperimentConfiguration
import uk.co.strimm.gui.GUIMain

@Plugin(type = Service::class)
class ProtocolService : AbstractService(), ImageJService  {
    ///TW hack to make sure no frames are pumped out by the source
    ///before it is ready
    //
    //
    //this needs to be improved and tidies  TW
    var bStartSources = false
    fun GetStartSources() : Boolean {
        return bStartSources
    }
    fun SetStartSources() {
        bStartSources = true
    }
    fun SetEndSources() {
        bStartSources = false
    }
    fun GetNIDAQUsed() : Boolean{
        return bNIDAQUsed
    }
    fun SetNIDAQUsed(bUsed : Boolean){
        bNIDAQUsed = bUsed
    }


    var bGlobalSourceStartTrigger = false // if sources are isGlobalStart then they cannot give out samples until this goes high
    //
    //
    //
    var bNIDAQUsed = false;
    public var jdaq = JDAQ()
    var bStartExperiment = false
    public var experimentStartTime = 0.0
   // var COMPort =  SerialPort.getCommPort("COM5")
    var szFolder = ".\\Protocols\\" //TO DO

    init{

    }
    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            println("hi there")



        }
    }
    fun Init(expConfig : ExperimentConfiguration) : Int {
        //init the COMPort
        bNIDAQUsed = false; // reset each time an experiment is loaded
//        COMPort = SerialPort.getCommPort("COM" + expConfig.COMPort.toString())
//        COMPort.setComPortParameters(9600, 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY)
//        COMPort.setComPortTimeouts(SerialPort.TIMEOUT_NONBLOCKING, 0, 0)
//        if (!COMPort.isOpen) {
//            if (COMPort.openPort()) {
//                println("Arduino:Opened port")
//               // COMPort.outputStream.write(byteArrayOf('A'.toByte()))
//            } else {
//                println("Arduino:Failed to open port")
//            }
//        } else {
//            println("Arduino:Port is already open")
//        }
        var daq = expConfig.NIDAQ
        if (expConfig.NIDAQ.protocolName != "") {
            jdaq.deviceID = daq.deviceID
            jdaq.InitProtocol(daq.protocolName, szFolder, daq.bCompound, daq.bRepeat, daq.deviceID, daq.minV, daq.maxV, daq.deviceName)
            jdaq.SetStartTrigger(daq.bStartTrigger, daq.pFIx, daq.bRisingEdge, daq.timeoutSec)
            jdaq.SetTimingMethod(daq.timingMethod)
        }
        bStartExperiment = true
        return 0;
    }
    fun RunNext(pTimes : DoubleArray, AOdata : DoubleArray, AIdata : DoubleArray, DOdata : IntArray, DIdata : IntArray) : Int {
        var ret = jdaq.RunNext(pTimes, AOdata, AIdata, DOdata, DIdata)
        //println("RunNext  " + ret)
        if (bStartExperiment){
            experimentStartTime = jdaq.GetCurrentSystemTime()  //  TO DO CHECK THIS    jdaq.GetCurrentRunStartTime()
            bStartExperiment = false
        }
        return ret
    }
    fun Shutdown() : Int {
        //COMPort.closePort()
        jdaq.ShutdownProtocol();
        return 1;
    }

    fun TerminateNIDAQProtocol() : Int {
        println("Calling jdaq.TerminateProtocol()")
        val test = jdaq.TerminateProtocol()
        println("Finished calling jdaq.TerminateProtocol()")
        return 1
    }

    fun UpdateDOChannel(pDOData: IntArray?, line: Int): Int {
        return jdaq.UpdateDOChannel(pDOData, line)
    }
    fun UpdateAOChannel(pAOData: DoubleArray?, channel: Int): Int {
        println("UpdateAOChannel")
        return jdaq.UpdateAOChannel(pAOData, channel)
    }
    fun GetNextNumSamples() : Int {
        return jdaq.GetNextNumSamples()
    }
    fun GetNumberOfDataPoints() : Int {
        return jdaq.GetNumberOfDataPoints()
    }
    fun GetNumChannels(type : Int) : Int {
        return jdaq.GetNumChannels(type)
    }
    fun GetChannelFromIndex(type : Int, ix : Int) : Int{
        return jdaq.GetChannelFromIndex(type, ix)
    }
    fun GetIndexFromChannel(type : Int, ch : Int) : Int{
        val numSubSysChannels = GetNumChannels( type)
        for (f in 0 until numSubSysChannels){
            if (ch == GetChannelFromIndex(type, f)){
                //found the channel
                return f
            }
        }
        return -1 //did not find
    }
    fun GetDeviceID(deviceLabel : String) : Int {
        return jdaq.GetDeviceID()
    }
    fun GetCurrentSystemTime() : Double {
        return jdaq.GetCurrentSystemTime()
    }
    fun GetCurrentRunSampleTime() : Double {
        return jdaq.GetCurrentRunSampleTime()
    }
    fun GetNumberOfStages() : Long{
        return jdaq.GetNumberOfStages()
    }
    fun GetChannelList(chName : String) : List<Int>{
        //find the offset into the tracedata block
        //which will be of the order AO, AI, DO, DI
        var exp = GUIMain.experimentService.expConfig
        var numAOChannels = GetNumChannels(0)
        var numAIChannels = GetNumChannels(1)
        var numDOChannels = GetNumChannels(2)
        var numDIChannels = GetNumChannels(3)
        var retChans = arrayListOf<Int>()
        for (f in 0 until exp.NIDAQ.virtualChannels.size){
            var virtChannel = exp.NIDAQ.virtualChannels[f]
            if (virtChannel.channelName == chName){
                for (physChannel in virtChannel.physicalChannels){
                        var bIsOutput = physChannel.bOutput
                        var bIsAnalog = physChannel.bAnalog
                        var ix_offset = 0
                        var type = 0
                        if (bIsAnalog && bIsOutput){
                            ix_offset = 0
                            type = 0
                        }
                        else if (bIsAnalog && !bIsOutput){
                            ix_offset = numAOChannels
                            type = 1
                        }
                        else if (!bIsAnalog && bIsOutput){
                            ix_offset = numAOChannels + numAIChannels
                            type = 2
                        }
                        else {
                            ix_offset = numAOChannels + numAIChannels + numDOChannels
                            type = 3
                        }
                        var ix = GetIndexFromChannel(type, physChannel.channel)
                        retChans.add(ix + ix_offset)
                    }
                return retChans
            }
        }
        return retChans
    }

    fun GetTotalNumberOfChannels() : Int {
        return GetNumChannels(0) + GetNumChannels(1) + GetNumChannels( 2) + GetNumChannels(3)
    }
    //
    //
    //
    //
    //image filemap
    fun GetCameraMapStatus() : Int{
        return jdaq.GetCameraMapStatus()
    }
    fun RegisterCameraForCameraMap(
            cameraSz : String,
            w: Int,
            h: Int,
            bitDepth: Int,
            binning: Int,
            numRect: Int,
            rois_x : IntArray,
            rois_y : IntArray,
            rois_w : IntArray,
            rois_h : IntArray
    ): Int {
        return jdaq.RegisterCameraForCameraMap(cameraSz, w,h,bitDepth,binning,numRect, rois_x, rois_y, rois_w, rois_h)
    }
    fun StartCameraMap(): Int {
        return jdaq.StartCameraMap()
    }
    fun ShutdownCameraMap(): Int {
        return jdaq.ShutdownCameraMap()
    }
    fun Add16BitImageDataCameraMap( //called by cameraFunction
        cameraSz: String?,
        fps: Double,
        interval: Double,
        w: Int,
        h: Int,
        pix: ShortArray?,
        bSave: Boolean
    ): Int {

        return jdaq.Add16BitImageDataCameraMap(cameraSz, fps, interval, w, h, pix, bSave)
    }
    fun Add8BitImageDataCameraMap(
        cameraSz: String?,
        fps: Double,
        interval: Double,
        w: Int,
        h: Int,
        pix: ByteArray?,
        bSave: Boolean
    ): Int {
        return jdaq.Add8BitImageDataCameraMap(cameraSz, fps, interval, w, h, pix, bSave)
    }
    fun AddARGBBitImageDataCameraMap(
        cameraSz: String?,
        fps: Double,
        interval: Double,
        w: Int,
        h: Int,
        pix: ByteArray?,
        bSave: Boolean
    ): Int {
       return jdaq.AddARGBBitImageDataCameraMap(cameraSz, fps, interval, w, h, pix, bSave)
    }
    //
    //
    //
    //
    //
    //useful winapi functions
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

    fun GDI_Write_Numbers_onto_Array(nums : IntArray?) : Int {
        return jdaq.GDIWriteNumbersOntoArray(nums)
    }
    fun GDI_Test_Write_Array(data_w : Int, data_h : Int, data: ShortArray?, numBins : Int, numSeries : Int, seriesName : String, xMin : Double, xMax : Double, yValues : DoubleArray?): Int {
        return jdaq.GDITestWriteArray(data_w, data_h, data, numBins, numSeries, seriesName, xMin, xMax, yValues)
    }
}