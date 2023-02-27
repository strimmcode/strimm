package uk.co.strimm.sinkMethods

import akka.actor.ActorRef
import com.opencsv.CSVReader
import uk.co.strimm.STRIMMBuffer
import uk.co.strimm.STRIMMSignalBuffer
import uk.co.strimm.actors.messages.complete.CompleteStreaming
import uk.co.strimm.actors.messages.start.StartStreaming
import uk.co.strimm.experiment.Sink
import uk.co.strimm.gui.GUIMain
import java.io.FileReader


open class SinkBaseMethod() : SinkMethod {
    open lateinit var sink : Sink
    open var bUseActor = false
    override lateinit var properties : HashMap<String, String>
    override fun useActor(): Boolean {
        return bUseActor
    }
    override fun init(sink : Sink) {
        this.sink = sink
        loadCfg()
    }
    fun loadCfg() {
        if (sink.sinkCfg != ""){
            properties = hashMapOf<String, String>()
            var r: List<Array<String>>? = null
            try {
                val reader = CSVReader(FileReader(sink.sinkCfg))
                r = reader.readAll()
                for (props in r!!) {
                    properties[props[0]] = props[1]
                }

            } catch (ex: Exception) {
                println(ex.message)
            }

        }
    }
    override fun run(data : List<STRIMMBuffer>){
       println("Sink data")
        for (f in 0..data.size-1){
            val im = data[f]
            println("SinkBaseMethod:= STRIMMBuffer ix:" + f.toString() + " dataID:" + im.dataID + " statusID:" + im.status)
//            val tim = mutableListOf<kotlin.Pair<String, Double>>()
//            (data[f].timeStamps as MutableList<Pair<String, Double>>).add(kotlin.Pair(this.sink.sinkName, GUIMain.softwareTimerService.getTime()))
//
//            for (ff in 0..data[f].timeStamps!!.size-1){
//                println("dataID: " + data[f].dataID.toString() + "  " + data[f].timeStamps?.get(ff)!!.first + " " + data[f].timeStamps?.get(ff)!!.second)
//            }
        }
        println("data at sink end")
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
        println("FAIL_ERROR")
    }
    override fun postStop() {
        println("postStop ******************")
    }
    override fun preStart() {
        println("preStart ******************")
    }
}