package uk.co.strimm.services

import com.fazecast.jSerialComm.SerialPort
import net.imagej.ImageJService
import org.scijava.plugin.Plugin
import org.scijava.service.AbstractService
import org.scijava.service.Service
import uk.co.strimm.Paths.Companion.PROTOCOLS_FOLDER
import uk.co.strimm.experiment.ExperimentConfiguration
import uk.co.strimm.gui.GUIMain
import java.util.logging.Level

@Plugin(type = Service::class)
class ProtocolService : AbstractService(), ImageJService {
    var bStartSources = false
    var bGlobalSourceStartTrigger = false // if sources are isGlobalStart then they cannot give out samples until this goes high
    var bNIDAQUsed = false
    var isEpisodic = false
    var jdaq = JDAQ()
    var bStartExperiment = false
    var experimentStartTime = 0.0
    private var COMPort: SerialPort? = null

    fun GetStartSources(): Boolean {
        return bStartSources
    }

    fun SetStartSources() {
        bStartSources = true
    }

    fun SetEndSources() {
        bStartSources = false
    }

    fun GetNIDAQUsed(): Boolean {
        return bNIDAQUsed
    }

    fun SetNIDAQUsed(bUsed: Boolean) {
        bNIDAQUsed = bUsed
    }

    /**
     * Singleton so an instance of the COM port is only created once
     * @return A SerialPort object that can be null. Need to check for null when this is called
     */
    fun COMPort(): SerialPort? {
        if (COMPort == null) {
            COMPort = SerialPort.getCommPort("COM5") //TODO hardcoded should come from experiment config
            return if (COMPort!!.openPort()) {
                GUIMain.loggerService.log(Level.INFO, "COM port opened")
                COMPort
            } else {
                null
            }
        } else {
            return COMPort
        }
    }

    fun Init(expConfig: ExperimentConfiguration): Int {
        bNIDAQUsed = false // reset each time an experiment is loaded
        val daq = expConfig.NIDAQ
        if (expConfig.NIDAQ.protocolName != "") {
            jdaq.deviceID = daq.deviceID
            jdaq.InitProtocol(daq.protocolName, PROTOCOLS_FOLDER, daq.bCompound, daq.bRepeat, daq.deviceID, daq.minV, daq.maxV, daq.deviceName)
            jdaq.SetStartTrigger(daq.bStartTrigger, daq.pFIx, daq.bRisingEdge, daq.timeoutSec)
            jdaq.SetTimingMethod(daq.timingMethod)
        }
        bStartExperiment = true
        return 0
    }

    fun RunNext(pTimes: DoubleArray, AOdata: DoubleArray, AIdata: DoubleArray, DOdata: IntArray, DIdata: IntArray): Int {
        val ret = jdaq.RunNext(pTimes, AOdata, AIdata, DOdata, DIdata)
        if (bStartExperiment) {
            experimentStartTime = jdaq.GetCurrentSystemTime()
            bStartExperiment = false
        }
        return ret
    }

    fun Shutdown(): Int {
        jdaq.ShutdownProtocol()
        return 1
    }

    fun TerminateNIDAQProtocol(): Int {
        val terminated = jdaq.TerminateProtocol()
        return 1
    }

    fun UpdateDOChannel(pDOData: IntArray?, line: Int): Int {
        return jdaq.UpdateDOChannel(pDOData, line)
    }

    fun UpdateAOChannel(pAOData: DoubleArray?, channel: Int): Int {
        return jdaq.UpdateAOChannel(pAOData, channel)
    }

    fun GetNextNumSamples(): Int {
        return jdaq.GetNextNumSamples()
    }

    fun GetNumberOfDataPoints(): Int {
        return jdaq.GetNumberOfDataPoints()
    }

    fun GetNumChannels(type: Int): Int {
        return jdaq.GetNumChannels(type)
    }

    fun GetChannelFromIndex(type: Int, ix: Int): Int {
        return jdaq.GetChannelFromIndex(type, ix)
    }

    fun GetIndexFromChannel(type: Int, ch: Int): Int {
        val numSubSysChannels = GetNumChannels(type)
        for (channelIndex in 0 until numSubSysChannels) {
            if (ch == GetChannelFromIndex(type, channelIndex)) {
                //found the channel
                return channelIndex
            }
        }
        return -1 //did not find
    }

    fun GetDeviceID(deviceLabel: String): Int {
        return jdaq.GetDeviceID()
    }

    fun GetCurrentSystemTime(): Double {
        return jdaq.GetCurrentSystemTime()
    }

    fun GetCurrentRunSampleTime(): Double {
        return jdaq.GetCurrentRunSampleTime()
    }

    fun GetNumberOfStages(): Long {
        return jdaq.GetNumberOfStages()
    }

    fun GetChannelList(chName: String): List<Int> {
        //find the offset into the tracedata block
        //which will be of the order AO, AI, DO, DI
        val exp = GUIMain.experimentService.expConfig
        val numAOChannels = GetNumChannels(0)
        val numAIChannels = GetNumChannels(1)
        val numDOChannels = GetNumChannels(2)
        var numDIChannels = GetNumChannels(3) //TODO why isn't DI channels used?
        val retChans = arrayListOf<Int>()

        for (channel in 0 until exp.NIDAQ.virtualChannels.size) {
            val virtualChannel = exp.NIDAQ.virtualChannels[channel]
            if (virtualChannel.channelName == chName) {
                for (physicalChannel in virtualChannel.physicalChannels) {
                    val bIsOutput = physicalChannel.bOutput
                    val bIsAnalog = physicalChannel.bAnalog
                    var offset: Int
                    var type: Int

                    if (bIsAnalog && bIsOutput) {
                        offset = 0
                        type = 0
                    } else if (bIsAnalog && !bIsOutput) {
                        offset = numAOChannels
                        type = 1
                    } else if (!bIsAnalog && bIsOutput) {
                        offset = numAOChannels + numAIChannels
                        type = 2
                    } else {
                        offset = numAOChannels + numAIChannels + numDOChannels
                        type = 3
                    }
                    val index = GetIndexFromChannel(type, physicalChannel.channel)
                    retChans.add(index + offset)
                }
                return retChans
            }
        }
        return retChans
    }

    fun GetTotalNumberOfChannels(): Int {
        return GetNumChannels(0) + GetNumChannels(1) + GetNumChannels(2) + GetNumChannels(3)
    }

    fun GetCameraMapStatus(): Int {
        return jdaq.GetCameraMapStatus()
    }

    fun RegisterCameraForCameraMap(
            cameraSz: String,
            w: Int,
            h: Int,
            bitDepth: Int,
            binning: Int,
            numRect: Int,
            rois_x: IntArray,
            rois_y: IntArray,
            rois_w: IntArray,
            rois_h: IntArray): Int {
        return jdaq.RegisterCameraForCameraMap(cameraSz, w, h, bitDepth, binning, numRect, rois_x, rois_y, rois_w, rois_h)
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

    //TODO can these functions starting with "Win" be removed?
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

    fun GDI_Write_Numbers_onto_Array(nums: IntArray?): Int {
        return jdaq.GDIWriteNumbersOntoArray(nums)
    }

    fun GDI_Test_Write_Array(data_w: Int, data_h: Int, data: ShortArray?, numBins: Int, numSeries: Int, seriesName: String, xMin: Double, xMax: Double, yValues: DoubleArray?): Int {
        return jdaq.GDITestWriteArray(data_w, data_h, data, numBins, numSeries, seriesName, xMin, xMax, yValues)
    }
}