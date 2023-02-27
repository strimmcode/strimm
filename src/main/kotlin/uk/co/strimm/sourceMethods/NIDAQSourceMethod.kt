
package uk.co.strimm.sourceMethods

import akka.actor.ActorRef
import com.opencsv.CSVReader
import uk.co.strimm.STRIMMBuffer
import uk.co.strimm.STRIMMNIDAQBuffer
import uk.co.strimm.experiment.Source
import uk.co.strimm.gui.GUIMain
import java.io.FileReader


class NIDAQSourceMethod () : SourceMethod {
    open lateinit var source: Source
    override lateinit var properties: HashMap<String, String>
    override var actor: ActorRef? = null
    open var dataID: Int = 0
    var bFirst = true
    var pTimes : DoubleArray? = null
    var dataAO : DoubleArray? = null
    var dataAI : DoubleArray? = null
    var dataDO : IntArray? = null
    var dataDI : IntArray? = null
    var numSamples = 10
    var sampleFreq = 0.0
    var numAOChannels = 0
    var numAIChannels = 0
    var numDOChannels = 0
    var numDIChannels = 0
    var AOChannels : IntArray? = null
    var AIChannels : IntArray? = null
    var DOChannels : IntArray? = null
    var DIChannels : IntArray? = null
    var DIPort = 0
    var DOPort = 0

    var numSamples_old = 0
    var deviceID = 4
    var deviceName = "Dev"
    var szCsv = ""
    var bRepeat = false
    var bCompound = false
    var minV = -10.0
    var maxV = 10.0
    var bStartTrigger = false
    var bRisingEdge = false
    var pFIx = 0
    var timeoutSec = -1.0
    var timingMethod = 1

    override fun init(source: Source) {
        this.source = source
        loadCfg()
        deviceID = properties["deviceID"]!!.toInt()
        deviceName = properties["deviceName"]!!//some systems do not use Dev4 etc
        szCsv = properties["szCsv"]!!//this is the absolute path
        bCompound = properties["bCompound"]!!.toBoolean()
        bRepeat = properties["bRepeat"]!!.toBoolean()
        bStartTrigger = properties["bStartTrigger"]!!.toBoolean()
        bRisingEdge = properties["bRisingEdge"]!!.toBoolean()
        pFIx = properties["pFIx"]!!.toInt()
        minV = properties["minV"]!!.toDouble() //for accurate representation choose minV and maxV for the signal to be samples or generated
        maxV = properties["maxV"]!!.toDouble()
        timeoutSec = properties["timeoutSec"]!!.toDouble()//-1 means infinite timeout
        timingMethod = properties["timingMethod"]!!.toInt()//0, 1, 2  TODO define

        dataID = 0

        var ret = GUIMain.protocolService.NIDAQ_Source_Init(szCsv, bCompound, bRepeat, deviceID, deviceName, minV, maxV)
        ret = GUIMain.protocolService.NIDAQ_Source_SetStartTrigger(deviceID, bStartTrigger, pFIx, bRisingEdge, timeoutSec)
        ret = GUIMain.protocolService.NIDAQ_Source_SetTimingMethod(deviceID, timingMethod)

    }

    override fun preStart() {

    }

    fun loadCfg() {
        if (source.sourceCfg != "") {
            properties = hashMapOf<String, String>()
            var r: List<Array<String>>? = null
            try {
                val reader = CSVReader(FileReader(source.sourceCfg))
                r = reader.readAll()
                for (props in r!!) {
                    properties[props[0]] = props[1]
                }

            } catch (ex: Exception) {
                println(ex.message)
            }

        }
    }

    override fun run(): STRIMMBuffer? {

        //get the parameters of the loaded protocol (which will be the next SimpleProtocol)
        var numSamples = GUIMain.protocolService.NIDAQ_Source_GetNumSamples(deviceID)
        sampleFreq = GUIMain.protocolService.NIDAQ_Source_GetSampleFreq(deviceID)
        if (numSamples >0){
            if (bFirst  ||  numSamples != numSamples_old){ //the size of each SimpleProtocol has changed
                numAOChannels = GUIMain.protocolService.NIDAQ_Source_GetNumAOChannels(deviceID)
                numAIChannels = GUIMain.protocolService.NIDAQ_Source_GetNumAIChannels(deviceID)
                numDOChannels = GUIMain.protocolService.NIDAQ_Source_GetNumDOChannels(deviceID)
                numDIChannels = GUIMain.protocolService.NIDAQ_Source_GetNumDIChannels(deviceID)
                if (numAOChannels > 0){
                    AOChannels = IntArray(numAOChannels)
                    for (ix in 0..numAOChannels-1){
                        AOChannels!![ix] = GUIMain.protocolService.NIDAQ_Source_GetChannelFromIndex(deviceID, 0, ix)
                    }
                    dataAO = DoubleArray(numSamples * numAOChannels)
                }
                if (numAIChannels > 0){
                    AIChannels = IntArray(numAIChannels)
                    for (ix in 0..numAIChannels-1){
                        AIChannels!![ix] = GUIMain.protocolService.NIDAQ_Source_GetChannelFromIndex(deviceID, 1, ix)
                    }
                    dataAI = DoubleArray(numSamples * numAIChannels)

                }
                if (numDOChannels > 0){
                    DOChannels = IntArray(numDOChannels)
                    for (ix in 0..numDOChannels-1){
                        DOChannels!![ix] = GUIMain.protocolService.NIDAQ_Source_GetChannelFromIndex(deviceID, 2, ix)
                    }
                    DOPort = GUIMain.protocolService.NIDAQ_Source_GetDOPort(deviceID)
                    dataDO = IntArray(numSamples * numDOChannels)
                }
                if (numDIChannels > 0){
                    DIChannels = IntArray(numDIChannels)
                    for (ix in 0..numDIChannels-1){
                        DIChannels!![ix] = GUIMain.protocolService.NIDAQ_Source_GetChannelFromIndex(deviceID, 3, ix)
                    }
                    DIPort = GUIMain.protocolService.NIDAQ_Source_GetDIPort(deviceID)
                    dataDI = IntArray(numSamples * numDIChannels)
                }

                pTimes = DoubleArray(numSamples)
                bFirst = false
                numSamples_old = numSamples
            }

            var ret = GUIMain.protocolService.NIDAQ_Source_Run(deviceID, pTimes, dataAO, dataAI, dataDO, dataDI);
            var status = 1
            if (ret < 0) {
                status  = 0
            }
            dataID++
            return STRIMMNIDAQBuffer(
                numSamples,
                sampleFreq,
                pTimes,
                dataAO, AOChannels,
                dataAI, AIChannels,
                dataDO, DOChannels, DOPort,
                dataDI, DIChannels, DIPort,
                dataID, status)
        }
        else {
            Thread.sleep(100);
            return STRIMMBuffer(dataID, 0)
        }

    }

    override fun postStop() {

        GUIMain.protocolService.NIDAQ_Source_Shutdown(deviceID)
    }
}