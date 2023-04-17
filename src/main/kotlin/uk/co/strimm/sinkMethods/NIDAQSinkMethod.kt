

package uk.co.strimm.sinkMethods


import akka.actor.ActorRef
import com.opencsv.CSVReader
import uk.co.strimm.STRIMMBuffer
import uk.co.strimm.actors.messages.complete.CompleteStreaming
import uk.co.strimm.actors.messages.start.StartStreaming
import uk.co.strimm.experiment.Sink
import uk.co.strimm.gui.GUIMain
import java.io.FileReader
import java.util.logging.Level


//NIDAQSinkMethod will execute a protocol (csv) on receipt of any kind of STRIMMBuffer
//This way logic within the graph can trigger a particular protocol
//
class NIDAQSinkMethod() : SinkMethod {
    lateinit var sink : Sink
    var bUseActor = false
    var dataID: Int = 0
    var bFirst = true
    var pTimes = DoubleArray(1)
    var dataAO = DoubleArray(1)
    var dataAI = DoubleArray(1)
    var dataDO = IntArray(1)
    var dataDI = IntArray(1)
    var numSamples = 10
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


    override lateinit var properties : HashMap<String, String>
    override fun useActor(): Boolean {
        return bUseActor
    }
    override fun init(sink : Sink) {
        this.sink = sink
        if (sink.sinkCfg != ""){
            //todo this is in the wrong place it should be in Sink
            properties = hashMapOf<String, String>()
            var r: List<Array<String>>? = null
            try {
                CSVReader(FileReader(sink.sinkCfg)).use { reader ->
                    r = reader.readAll()
                    for (props in r!!) {
                        //specific properties are read from Cfg
                        // "intervalMs" : "10.0"  etc
                        properties[props[0]] = props[1]
                    }
                }
            } catch (ex: Exception) {
                println(ex.message)
            }
        }

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
        timingMethod = properties["timingMethod"]!!.toInt()

        dataID = 0


        GUIMain.loggerService.log(Level.INFO, "Calling protocol service NIDAQ_Source_Init")
        var ret = GUIMain.protocolService.NIDAQ_Source_Init(szCsv, bCompound, bRepeat, deviceID, deviceName, minV, maxV)
        GUIMain.loggerService.log(Level.INFO, "Calling protocol service NIDAQ_Source_SetStartTrigger")
        ret = GUIMain.protocolService.NIDAQ_Source_SetStartTrigger(deviceID,bStartTrigger, pFIx, bRisingEdge, timeoutSec)
        GUIMain.loggerService.log(Level.INFO, "Calling protocol service NIDAQ_Source_SetTimingMethod")
        ret = GUIMain.protocolService.NIDAQ_Source_SetTimingMethod(deviceID, timingMethod)
    }

    override fun preStart() {
        //open all resources that the source might need
        //eg start the circular buffer, load a tiff stack etc
    }
    override fun run(data : List<STRIMMBuffer>){
        println("IN NIDAQSINKMETHOD RUN")
        //receipt of a STRIMMBuffer of any kind triggers the protocol
        //get the parameters of the loaded protocol (which will be the next SimpleProtocol)
        var numSamples = GUIMain.protocolService.NIDAQ_Source_GetNumSamples(deviceID)
        if (numSamples >0){
            var numAOChannels = GUIMain.protocolService.NIDAQ_Source_GetNumAOChannels(deviceID)
            var numAIChannels = GUIMain.protocolService.NIDAQ_Source_GetNumAIChannels(deviceID)
            var numDOChannels = GUIMain.protocolService.NIDAQ_Source_GetNumDOChannels(deviceID)
            var numDIChannels = GUIMain.protocolService.NIDAQ_Source_GetNumDIChannels(deviceID)

            if (bFirst  ||  numSamples != numSamples_old){
                //the size of each SimpleProtocol has changed
                //and so need new buffers
                dataAI = DoubleArray(numSamples * numAIChannels)
                dataDI = IntArray(numSamples * numDIChannels)
                dataAO = DoubleArray(numSamples * numAOChannels)
                dataDO = IntArray(numSamples * numDOChannels)
                pTimes = DoubleArray(numSamples)
                bFirst = false
                numSamples_old = numSamples

            }
            //The AI and the DI will be ignored
            var ret = GUIMain.protocolService.NIDAQ_Source_Run(deviceID, pTimes, dataAO, dataAI, dataDO, dataDI);

        }
        else {
            Thread.sleep(100);
        }
    }
    override fun postStop() {
        //shut down and clean up all resources
        GUIMain.protocolService.NIDAQ_Source_Shutdown(deviceID)
    }
    override fun getActorRef() : ActorRef? {
        return null
    }
    override fun start() : StartStreaming {
        return StartStreaming()
    }
    override fun complete() : CompleteStreaming {
        return CompleteStreaming()
    }
    override fun fail(ex: Throwable) {
        println("FAIL")
    }
}