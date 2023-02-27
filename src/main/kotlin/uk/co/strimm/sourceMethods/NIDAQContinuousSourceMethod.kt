package uk.co.strimm.sourceMethods
import akka.actor.ActorRef
import com.opencsv.CSVReader
import uk.co.strimm.STRIMMBuffer
import uk.co.strimm.STRIMMNIDAQBuffer
import uk.co.strimm.experiment.Source
import uk.co.strimm.gui.GUIMain
import java.io.FileReader

class NIDAQContinousSourceMethod() : SourceMethod {
    open lateinit var source: Source
    override lateinit var properties: HashMap<String, String>
    override var actor: ActorRef? = null
    open var dataID: Int = 0

    var deviceID = 0

    var pTimes : DoubleArray? = DoubleArray(1)
    var dataAO : DoubleArray? = DoubleArray(1)
    var dataAI : DoubleArray? = DoubleArray(1)
    var dataDO : IntArray? = IntArray(1)
    var dataDI : IntArray? = IntArray(1)
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

    override fun init(source: Source) {
        this.source = source
        loadCfg()
        dataID = 0
        deviceID = properties["deviceID"]!!.toInt()
        println("before init")
        var ret = GUIMain.protocolService.NIDAQ_ContinuousSource_Init(
            properties["deviceID"]!!.toInt(),
            properties["szCsv"]!!,
            properties["bStartTrigger"]!!.toBoolean(),
            properties["bRisingEdge"]!!.toBoolean(),
            properties["pFIx"]!!.toInt(),
            properties["timeoutSec"]!!.toDouble(),
            properties["minV"]!!.toDouble(),
            properties["maxV"]!!.toDouble()
        )

        numSamples = GUIMain.protocolService.NIDAQ_ContinuousSource_GetNumSamples(deviceID)
        sampleFreq = GUIMain.protocolService.NIDAQ_ContinuousSource_GetSampleFreq(deviceID)
        numAOChannels = GUIMain.protocolService.NIDAQ_ContinuousSource_GetNumAOChannels(deviceID)
        numAIChannels = GUIMain.protocolService.NIDAQ_ContinuousSource_GetNumAIChannels(deviceID)
        numDOChannels = GUIMain.protocolService.NIDAQ_ContinuousSource_GetNumDOChannels(deviceID)
        numDIChannels = GUIMain.protocolService.NIDAQ_ContinuousSource_GetNumDIChannels(deviceID)
        if (numAOChannels > 0){
            AOChannels = IntArray(numAOChannels)
            for (ix in 0..numAOChannels-1){
                AOChannels!![ix] = GUIMain.protocolService.NIDAQ_ContinuousSource_GetChannelFromIndex(deviceID, 0, ix)
            }
            dataAO = DoubleArray(numSamples * numAOChannels)
        }
        if (numAIChannels > 0){
            AIChannels = IntArray(numAIChannels)
            for (ix in 0..numAIChannels-1){
                AIChannels!![ix] = GUIMain.protocolService.NIDAQ_ContinuousSource_GetChannelFromIndex(deviceID, 1, ix)
            }
            dataAI = DoubleArray(numSamples * numAIChannels)

        }
        if (numDOChannels > 0){
            DOChannels = IntArray(numDOChannels)
            for (ix in 0..numDOChannels-1){
                DOChannels!![ix] = GUIMain.protocolService.NIDAQ_ContinuousSource_GetChannelFromIndex(deviceID, 2, ix)
            }
            DOPort = GUIMain.protocolService.NIDAQ_ContinuousSource_GetDOPort(deviceID)
            dataDO = IntArray(numSamples * numDOChannels)
        }
        if (numDIChannels > 0){
            DIChannels = IntArray(numDIChannels)
            for (ix in 0..numDIChannels-1){
                DIChannels!![ix] = GUIMain.protocolService.NIDAQ_ContinuousSource_GetChannelFromIndex(deviceID, 3, ix)
            }
            DIPort = GUIMain.protocolService.NIDAQ_ContinuousSource_GetDIPort(deviceID)
            dataDI = IntArray(numSamples * numDIChannels)
        }

        pTimes = DoubleArray(numSamples)

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

        var ret = GUIMain.protocolService.NIDAQ_ContinuousSource_Run(deviceID, pTimes, dataAO, dataAI, dataDO, dataDI)

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

    override fun postStop() {
        GUIMain.protocolService.NIDAQ_ContinuousSource_Shutdown(deviceID)
    }
}