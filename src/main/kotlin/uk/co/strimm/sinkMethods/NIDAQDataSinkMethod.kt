

package uk.co.strimm.sinkMethods



import akka.actor.ActorRef
import com.opencsv.CSVReader
import uk.co.strimm.STRIMMBuffer
import uk.co.strimm.STRIMMNIDAQBuffer
import uk.co.strimm.actors.messages.complete.CompleteStreaming
import uk.co.strimm.actors.messages.start.StartStreaming
import uk.co.strimm.experiment.Sink
import uk.co.strimm.gui.GUIMain
import java.io.FileReader

//NIDAQDataSinkMethod will execute a sequence on the NIDAQ determined by the STRIMMBuffer at runtime

class NIDAQDataSinkMethod() : SinkMethod {
    lateinit var sink : Sink
    var bUseActor = false
    var dataID: Int = 0
    var deviceID = 4
    var deviceName = "Dev"
    var minV = -10.0
    var maxV = 10.0


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
        minV = properties["minV"]!!.toDouble() //for accurate representation choose minV and maxV for the signal to be samples or generated
        maxV = properties["maxV"]!!.toDouble()

        dataID = 0

        var ret = GUIMain.protocolService.NIDAQ_DataSink_Init(deviceID,  minV, maxV)

    }
    override fun preStart() {
        //open all resources that the source might need
        //eg start the circular buffer, load a tiff stack etc
    }
    override fun run(data : List<STRIMMBuffer>){
        val prot = data[0] as STRIMMNIDAQBuffer
        var numSamples = prot.numSamples
        var sampleFreq = prot.sampleFreqMS

        var numAOChannels = 0
        var numDOChannels = 0
        if (prot.AOChannels != null){
            numAOChannels = prot.AOChannels!!.size
        }
        if (prot.DOChannels != null){
            numDOChannels = prot.DOChannels!!.size
        }



        var ret = GUIMain.protocolService.NIDAQ_DataSink_Run(
            deviceID,
            prot.pDataAO, prot.pDataDO, numSamples, sampleFreq ,
            numAOChannels,prot.AOChannels,
            numDOChannels, prot.DOChannels, prot.DOport)

    }
    override fun postStop() {
        //shut down and clean up all resources

        GUIMain.protocolService.NIDAQ_DataSink_Shutdown(deviceID)
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