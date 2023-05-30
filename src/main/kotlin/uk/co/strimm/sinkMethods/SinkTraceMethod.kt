package uk.co.strimm.sinkMethods

import akka.actor.ActorRef
import com.opencsv.CSVReader
import uk.co.strimm.STRIMMBuffer
import uk.co.strimm.actors.messages.complete.CompleteStreaming
import uk.co.strimm.actors.messages.start.StartStreaming
import uk.co.strimm.experiment.Sink
import uk.co.strimm.gui.GUIMain
import uk.co.strimm.gui.TraceWindowPlugin
import java.io.FileReader
import java.util.logging.Level

//this is the only component that has specificed Trace structure,
//as well as the return types from NIDAQ and Arduino
class SinkTraceMethod : SinkMethod {
    var traceActor : ActorRef? = null
    lateinit var sink : Sink
    override lateinit var properties : HashMap<String, String>
    var bUseActor = true

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
            }
            catch (ex: Exception) {
                GUIMain.loggerService.log(Level.SEVERE, "Error loading config file for SinkTraceMethod ${sink.sinkName}. Message: ${ex.message}")
                GUIMain.loggerService.log(Level.SEVERE, ex.stackTrace)
            }
        }

        val plugin: TraceWindowPlugin = GUIMain.dockableWindowPluginService.createPlugin(
            TraceWindowPlugin::class.java,
            properties, /// data
            true,
            sink.sinkName
        )
        plugin.traceWindowController.sink = sink
        plugin.traceWindowController.furtherInit()
        traceActor = GUIMain.actorService.getActorByName(sink.sinkName)
        plugin?.dock(GUIMain.strimmUIService.dockableControl, GUIMain.strimmUIService.strimmFrame)
    }

    override fun run(data : List<STRIMMBuffer>){
    }

    override fun getActorRef() : ActorRef? {
        return traceActor
    }

    override fun start() : StartStreaming{
        return StartStreaming()
    }

    override fun complete() : CompleteStreaming {
        return CompleteStreaming()
    }

    override fun fail(ex: Throwable) {
    }

    override fun postStop() {
    }

    override fun preStart() {
    }
}