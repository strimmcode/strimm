

package uk.co.strimm.sinkMethods

import akka.actor.ActorRef
import com.opencsv.CSVReader
import uk.co.strimm.STRIMMBuffer
import uk.co.strimm.STRIMMSaveBuffer
import uk.co.strimm.STRIMMSignalBuffer
import uk.co.strimm.STRIMMSignalBuffer1
import uk.co.strimm.actors.messages.complete.CompleteStreaming
import uk.co.strimm.actors.messages.start.StartStreaming
import uk.co.strimm.experiment.Sink
import uk.co.strimm.gui.GUIMain
import java.io.FileReader

//each sink will get its images saved in its own file
//however you can also save several sources to the save SinkSaveMethod but
//it will show repeats

class SinkSaveMethod() : SinkMethod {
    lateinit var sink : Sink
    var bUseActor = false
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
    }
    override fun run(data : List<STRIMMBuffer>){
        //println("******SaveData to HDF5:" + sink!!.sinkName)
        //send to FileManager
//        if (data[0] is STRIMMSignalBuffer){
//            println("save STRIMMSignalBuffer")
//        }
//        else if (data[0] is STRIMMSignalBuffer1){
//            println("save STRIMMSignalBuffer1")
//        }
        GUIMain.actorService.fileManagerActor.tell(STRIMMSaveBuffer(data , sink!!.sinkName),null)
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
    override fun postStop() {

    }
    override fun preStart() {
    }
}