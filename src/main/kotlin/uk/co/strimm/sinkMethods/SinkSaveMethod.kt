package uk.co.strimm.sinkMethods

import akka.actor.ActorRef
import com.opencsv.CSVReader
import uk.co.strimm.*
import uk.co.strimm.actors.messages.complete.CompleteStreaming
import uk.co.strimm.actors.messages.start.StartStreaming
import uk.co.strimm.actors.messages.tell.TellAllStop
import uk.co.strimm.actors.messages.tell.TellStopReceived
import uk.co.strimm.experiment.Sink
import uk.co.strimm.gui.GUIMain
import java.io.FileReader
import java.util.logging.Level

//each sink will get its images saved in its own file
//however you can also save several sources to the save SinkSaveMethod but
//it will show repeats

class SinkSaveMethod : SinkMethod {
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
                GUIMain.loggerService.log(Level.SEVERE, "Error reading configuraton file ${sink.sinkCfg} for SinkSaveMethod. Message: ${ex.message}")
                GUIMain.loggerService.log(Level.SEVERE, ex.stackTrace)
            }
        }
    }

    override fun run(data : List<STRIMMBuffer>){
        GUIMain.actorService.fileManagerActor.tell(STRIMMSaveBuffer(data, sink.sinkName), null)

        if (data.any { x -> x.status == 0 }) {
            GUIMain.loggerService.log(Level.INFO, "SinkSaveMethod received status of 0")
            GUIMain.actorService.fileManagerActor.tell(TellStopReceived(false), null)
        } else if (data.any { x -> x.status == -1 }) {
            GUIMain.loggerService.log(Level.INFO, "SinkSaveMethod received status of -1")
            GUIMain.actorService.fileManagerActor.tell(TellStopReceived(true), null)
        }
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