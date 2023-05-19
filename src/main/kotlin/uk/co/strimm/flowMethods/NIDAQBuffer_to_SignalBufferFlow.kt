package uk.co.strimm.flowMethods

import akka.actor.ActorRef
import com.opencsv.CSVReader
import uk.co.strimm.STRIMMBuffer
import uk.co.strimm.STRIMMNIDAQBuffer
import uk.co.strimm.STRIMMSignalBuffer
import uk.co.strimm.experiment.Flow
import uk.co.strimm.gui.GUIMain
import java.io.FileReader
import java.util.logging.Level

//STRIMMBuffer format conversion from NIDAQBuffer which is the output of the NIDAQ
//to SignalBufferFlow - which is used by the TraceWindow

//TODO reanalyse and reclassify all of the different STRIMMBuffer types
//in order to minimise the number of conversion Flows like this - as they
//just use up cpu cycles

open class NIDAQBuffer_to_SignalBufferFlow() : FlowMethod {
    open lateinit var flow: Flow
    override lateinit var properties: HashMap<String, String>
    override var actor: ActorRef? = null
    var times_cnt = 0
    override fun init(flow: Flow) {
        GUIMain.loggerService.log(Level.INFO, "Initialising NIDAQBuffer to SignalBuffer flow")
        //todo this is in the wrong place it should be in flow
        this.flow = flow
        if (flow.flowCfg != "") {
            properties = hashMapOf<String, String>()
            var r: List<Array<String>>? = null
            try {
                CSVReader(FileReader(flow.flowCfg)).use { reader ->
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
    }

    override fun run(data: List<STRIMMBuffer>): STRIMMBuffer {
        //the flow function should know the
        var dataIn = data[0] as STRIMMNIDAQBuffer
        var numSamples = dataIn.numSamples
        var times = dataIn.pTimes

        for (f in 0..times!!.size-1){
            times[f] = times_cnt.toDouble()
            times_cnt++
        }

        var numAOChannels = 0
        if(dataIn.AOChannels != null){
            numAOChannels = dataIn.AOChannels!!.size
        }
        var numAIChannels = 0
        if(dataIn.AIChannels != null){
            numAIChannels = dataIn.AIChannels!!.size
        }
        var numDOChannels = 0
        if(dataIn.DOChannels != null){
            numDOChannels = dataIn.DOChannels!!.size
        }
        var numDIChannels = 0
        if(dataIn.DIChannels != null){
            numDIChannels = dataIn.DIChannels!!.size
        }
//        var numAOChannels = dataIn.AOChannels!!.size
//        var numAIChannels = dataIn.AIChannels!!.size
//        var numDOChannels = dataIn.DOChannels!!.size
//        var numDIChannels = dataIn.DIChannels!!.size


        //create an array of channel names
        var channelNames = mutableListOf<String>()
        for (f in 0..numAOChannels-1){
            channelNames.add("AO" + dataIn.AOChannels!![f].toString())
        }
        for (f in 0..numAIChannels-1){
            channelNames.add("AI" + dataIn.AIChannels!![f].toString())
        }
        for (f in 0..numDOChannels-1){
            channelNames.add("DO" + dataIn.DOChannels!![f].toString())
        }
        for (f in 0..numDIChannels-1){
            channelNames.add("DI" + dataIn.DIChannels!![f].toString())
        }

        //convert the data into a single array of doubles
        var data = DoubleArray( channelNames.size * numSamples)
        var cnt = 0
        var cnt_AO = 0
        var cnt_AI = 0
        var cnt_DO = 0
        var cnt_DI = 0
        for (f in 0..numSamples-1){
            for (ff in 0..numAOChannels-1){
                data[cnt] = dataIn.pDataAO!![cnt_AO]
                cnt_AO++
                cnt++
            }
            for (ff in 0..numAIChannels-1){
                data[cnt] = dataIn.pDataAI!![cnt_AI]
                cnt_AI++
                cnt++
            }
            for (ff in 0..numDOChannels-1){
                data[cnt] = dataIn.pDataDO!![cnt_DO].toDouble()
                cnt_DO++
                cnt++
            }
            for (ff in 0..numDIChannels-1){
                data[cnt] = dataIn.pDataDI!![cnt_DI].toDouble()
                cnt_DI++
                cnt++
            }
        }

        return STRIMMSignalBuffer(data, times, numSamples, channelNames, dataIn.dataID, dataIn.status)
    }

    override fun preStart(){}

    override fun postStop(){}
}