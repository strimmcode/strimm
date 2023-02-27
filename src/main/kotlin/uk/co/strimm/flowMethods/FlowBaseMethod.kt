package uk.co.strimm.flowMethods

import akka.actor.ActorRef
import com.opencsv.CSVReader
import uk.co.strimm.STRIMMBuffer
import uk.co.strimm.experiment.Flow
import java.io.FileReader
import java.util.concurrent.CompletionStage
//Simple implementation of interface FlowMethod
//which only prints the data_ix of a STRIMMBuffer

open class FlowBaseMethod() : FlowMethod {
    open lateinit var flow: Flow
    override lateinit var properties: HashMap<String, String>
    override var actor: ActorRef? = null
    override fun init(flow: Flow) {
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


        for (f in 0..data.size - 1) {
            println("FlowBaseMethod:= data_ix " + f)
        }
        return data[0]

    }
    override fun preStart(){

    }
    override fun postStop(){

    }

}