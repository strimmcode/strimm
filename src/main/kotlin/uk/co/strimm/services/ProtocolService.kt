package uk.co.strimm.services


//import com.fazecast.jSerialComm.SerialPort
import com.fazecast.jSerialComm.SerialPort
import net.imagej.ImageJService
import org.scijava.plugin.Plugin
import org.scijava.service.AbstractService
import org.scijava.service.Service
import uk.co.strimm.ArduinoCommunicator

@Plugin(type = Service::class)
class ProtocolService : AbstractService(), ImageJService  {
    public var jdaq = JDAQ()

//    fun Init(expConfig : ExperimentConfiguration) : Int {
//        //init the COMPort
//        bNIDAQUsed = false;
//        var daq = expConfig.NIDAQ
//        if (expConfig.NIDAQ.protocolName != "") {
//            jdaq.deviceID = daq.deviceID
//            jdaq.InitProtocol(daq.protocolName, szFolder, daq.bCompound, daq.bRepeat, daq.deviceID, daq.minV, daq.maxV, daq.deviceName)
//            jdaq.SetStartTrigger(daq.bStartTrigger, daq.pFIx, daq.bRisingEdge, daq.timeoutSec)
//            jdaq.SetTimingMethod(daq.timingMethod)
//        }
//        bStartExperiment = true
//        return 0;
//    }
    fun Test() : Int {
        var ret = jdaq.Test()
        return ret
    }
    fun TestStart() : Int {  //starts a thread of callbacks
        var ret = jdaq.TestStart()
        return ret
    }
    fun PreTestStart() : Int {  //starts a thread of callbacks
        var ret = jdaq.PreTestStart()
        return ret
    }
    fun TestStop() : Int {  //stops a thread of callbacks
        var ret = jdaq.TestStop()
        return ret
    }



















    // NIDAQ Continuous Source

    fun NIDAQ_ContinuousSource_Init(deviceID: Int, szCsv: String, bStartTrigger: Boolean, bRisingEdge: Boolean, pFIx: Int, timeoutSec: Double, minV: Double, maxV: Double): Int{
        var ret = jdaq.NIDAQContinuousSourceInit(deviceID,szCsv, bStartTrigger, bRisingEdge,pFIx, timeoutSec, minV, maxV)
        return ret;
    }

    fun NIDAQ_ContinuousSource_Run(deviceID:Int, pTimes: DoubleArray?, pAOData: DoubleArray?, pAIData: DoubleArray?, pDOData: IntArray?, pDIData: IntArray?): Int{
        var ret = jdaq.NIDAQContinuousSourceRun(deviceID, pTimes, pAOData, pAIData, pDOData, pDIData);
        return ret;
    }

    fun NIDAQ_ContinuousSource_Shutdown(deviceID : Int): Int{
        var ret = jdaq.NIDAQContinuousSourceShutdown(deviceID)
        return ret;

    }

    fun NIDAQ_ContinuousSource_GetNumSamples(deviceID : Int): Int{
        return jdaq.NIDAQContinuousSourceGetNumSamples(deviceID)
    }
    fun   NIDAQ_ContinuousSource_GetSampleFreq(deviceID : Int): Double{
        return jdaq.NIDAQContinuousSourceGetSampleFreq(deviceID)
    }
    fun NIDAQ_ContinuousSource_GetNumAOChannels(deviceID : Int): Int{
        return jdaq.NIDAQContinuousSourceGetNumAOChannels(deviceID)
    }
    fun NIDAQ_ContinuousSource_GetNumAIChannels(deviceID : Int): Int{
        return jdaq.NIDAQContinuousSourceGetNumAIChannels(deviceID)
    }
    fun NIDAQ_ContinuousSource_GetNumDOChannels(deviceID : Int): Int{
        return jdaq.NIDAQContinuousSourceGetNumDOChannels(deviceID)
    }
    fun NIDAQ_ContinuousSource_GetNumDIChannels(deviceID : Int): Int{
        return jdaq.NIDAQContinuousSourceGetNumDIChannels(deviceID)
    }
    fun NIDAQ_ContinuousSource_GetChannelFromIndex(deviceID : Int, type : Int, index : Int) : Int{
        return jdaq.NIDAQContinuousSourceGetChannelFromIndex(deviceID, type, index)
    }

    fun NIDAQ_ContinuousSource_GetDOPort(deviceID : Int) : Int{
        return jdaq.NIDAQContinuousSourceGetDOPort(deviceID)
    }
    fun NIDAQ_ContinuousSource_GetDIPort(deviceID : Int) : Int{
        return jdaq.NIDAQContinuousSourceGetDIPort(deviceID)
    }









    // NIDAQ Source

    fun NIDAQ_Source_Init(szCsv : String, bCompound : Boolean, bRepeat : Boolean, deviceID : Int, deviceName : String, minV : Double, maxV : Double) : Int{
        return jdaq.NIDAQSourceInit(szCsv, bCompound, bRepeat, deviceID, deviceName, minV, maxV)
    }
    fun NIDAQ_Source_SetStartTrigger(deviceID : Int, bStartTrigger : Boolean, pFIx : Int, bRisingEdge : Boolean, timeoutSec : Double) : Int{
        return jdaq.NIDAQSourceSetStartTrigger(deviceID, bStartTrigger,pFIx,bRisingEdge,timeoutSec)
    }
    fun NIDAQ_Source_SetTimingMethod(deviceID : Int, timingMethod : Int) : Int{
        return jdaq.NIDAQSourceSetTimingMethod(deviceID, timingMethod)
    }
    fun NIDAQ_Source_GetNumAOChannels(deviceID : Int) : Int{
        return jdaq.NIDAQSourceGetNumAOChannels(deviceID)
    }
    fun NIDAQ_Source_GetNumAIChannels(deviceID : Int) : Int{
        return jdaq.NIDAQSourceGetNumAIChannels(deviceID)
    }
    fun NIDAQ_Source_GetNumDOChannels(deviceID : Int) : Int{
        return jdaq.NIDAQSourceGetNumDOChannels(deviceID)
    }
    fun NIDAQ_Source_GetNumDIChannels(deviceID : Int) : Int{
        return jdaq.NIDAQSourceGetNumDIChannels(deviceID)
    }
    fun NIDAQ_Source_GetNumSamples(deviceID : Int) : Int{
        return jdaq.NIDAQSourceGetNumSamples(deviceID)
    }
    fun NIDAQ_Source_GetSampleFreq(deviceID : Int) : Double{
        return jdaq.NIDAQSourceGetSampleFreq(deviceID)
    }

    fun NIDAQ_Source_GetChannelFromIndex(deviceID : Int, type : Int, index : Int) : Int{
        return jdaq.NIDAQSourceGetChannelFromIndex(deviceID, type, index)
    }

    fun NIDAQ_Source_GetDOPort(deviceID : Int) : Int{
        return jdaq.NIDAQSourceGetDOPort(deviceID)
    }
    fun NIDAQ_Source_GetDIPort(deviceID : Int) : Int{
        return jdaq.NIDAQSourceGetDIPort(deviceID)
    }
    fun NIDAQ_Source_Run(deviceID : Int, pTimes : DoubleArray?, AOdata : DoubleArray?, AIdata : DoubleArray?, DOdata : IntArray?, DIdata : IntArray?) : Int {
        return jdaq.NIDAQSourceRun(deviceID, pTimes, AOdata, AIdata, DOdata, DIdata)
    }
    fun NIDAQ_Source_Shutdown(deviceID : Int) : Int {
        return jdaq.NIDAQSourceShutdown(deviceID);
    }




    //NIDAQ DataSink
    fun NIDAQ_DataSink_Init(deviceID: Int, minV: Double, maxV: Double): Int{
        return jdaq.NIDAQDataSinkInit(deviceID, minV, maxV)
    }
    fun NIDAQ_DataSink_Run(deviceID : Int, pAOData: DoubleArray?, pDOData: IntArray?, numSamples: Int, sampleFreq: Double,numAOChannels: Int,AOChannels: IntArray?,numDOChannels: Int,
        DOChannels: IntArray?, DOport: Int): Int{
        return jdaq.NIDAQDataSinkRun(deviceID, pAOData, pDOData, numSamples, sampleFreq, numAOChannels, AOChannels, numDOChannels, DOChannels, DOport)
    }

    fun NIDAQ_DataSink_Shutdown(deviceID : Int): Int{
        return jdaq.NIDAQDataSinkShutdown(deviceID)

    }



// Arduino
    private var arduinos = hashMapOf<Int, ArduinoCommunicator>()  //index with COM number
    fun ARDUINO_Init(COM : Int, baudRate : Int){
        val availableComPorts = SerialPort.getCommPorts()
        if (availableComPorts.isEmpty()) {
            println("ARDUINO ERROR: NO COM PORTS******")
        }
        else{
            //find the COM port
            for (f in 0..availableComPorts.size-1){
                val comPort :String = availableComPorts[f].systemPortName
                println(comPort)
                if (comPort == "COM" + COM){
                    val ardu = ArduinoCommunicator(
                        availableComPorts.get(f), baudRate
                    )
                    if (!ardu.connect()) {
                        println("ARDUINO ERROR: cannot connect. Terminated")
                    }
                    if (!ardu.isInCommandMode) {
                        println("ARDUINO ERROR: Board is not in command mode. Terminated")
                    }
                    arduinos[COM] = ardu
                    break
                }
            }

        }
    }
    fun ARDUINO_Shutdown(COM : Int){

        arduinos[COM]!!.closePort()
    }
    fun ARDUINO_Shutdown_All(){
        for (arduino in arduinos.values) {
            arduino.closePort();
        }
    }
    fun ARDUINO_Set_Digital_Output_Pins(COM : Int, pins : List<Int>){
        if (pins.size == 6){
            if (!arduinos[COM]!!.setOutputPins(pins[0],pins[1],pins[2],pins[3],pins[4],pins[5])){
                println("Error setting digital output pins")
            }
        }
        else{
            println("ERROR wrong length of digital output pins")
        }

    }
    fun ARDUINO_Set_Digital_Input_Pins(COM : Int, pins : List<Int>){
        if (pins.size == 6){
            if (!arduinos[COM]!!.setDigitalInputPins(pins[0],pins[1],pins[2],pins[3],pins[4],pins[5])){
                println("Error setting digital input pins")
            }
        }
        else{
            println("ERROR wrong length of digital input pins")
        }
    }
    fun ARDUINO_Set_Analog_Input_Pins(COM : Int, pins : List<Int>){
        if (pins.size == 6){
            if (!arduinos[COM]!!.setAnalogInputPins(pins[0],pins[1],pins[2],pins[3],pins[4],pins[5])){
                println("Error setting analog input pins")
            }

        }
        else{
            println("ERROR wrong length of analog input pins")
        }
    }
    fun ARDUINO_Run(COM : Int, seqArray : ByteArray, stepsMicros : Long): Pair<Array<IntArray>, Pair<IntArray, Array<IntArray>>> {

        val stepsNumber = seqArray.size
        val resultArraydigital = IntArray(stepsNumber)
        val resultArrayanalog = Array(stepsNumber) {
            IntArray(
                ArduinoCommunicator.ANALOG_INPUTS_NUMBER
            )
        }
        val analogTimingsArray = Array(stepsNumber) {
            IntArray(
                ArduinoCommunicator.ANALOG_INPUTS_NUMBER
            )
        }

        if (!arduinos[COM]!!.doMeasurement(
                seqArray,
                stepsMicros,
                resultArraydigital,
                resultArrayanalog,
                analogTimingsArray
            )
        ) {
            println("ARDUINO ERROR: No data returned, possibly exceeded Arduino memory")
        }
        if (!arduinos[COM]!!.isInCommandMode()) {
            println("Board is not in command mode. Terminated")

        }
        return Pair(analogTimingsArray, Pair(resultArraydigital,resultArrayanalog))

    }
    fun ARDUINO_Run_Continuous(COM : Int, infiniteSeqArray : ByteArray, stepsMicros : Long){
        if (!arduinos[COM]!!.startContinousSequence(infiniteSeqArray, stepsMicros)) {
            print("Error: Infinite sequence not started")
        }
        if (arduinos[COM]!!.isInCommandMode()) {
            println("PROBLEM: Board in command mode when should be in sequence.")

        }
    }
    fun ARDUINO_Stop_Continuous(COM : Int){
        if (!arduinos[COM]!!.stopSequence()) {
            println("PROBLEM: sequence seems not stopped.")
        }
        //println("Check if in command mode...")
        if (!arduinos[COM]!!.isInCommandMode()) {
            println("PROBLEM:Board is not in command mode. ")
        }

    }




    //
    fun GetKeyState(VK: Int): Boolean{
        return jdaq.GetKeyState(VK)
    }

    fun WinBeep(freq: Int, dur: Int): Int {
        return jdaq.WinBeep(freq, dur)
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