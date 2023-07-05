package uk.co.strimm.sinkMethods

import akka.actor.ActorRef
import com.opencsv.CSVReader
import uk.co.strimm.STRIMMBuffer
import uk.co.strimm.actors.messages.complete.CompleteStreaming
import uk.co.strimm.actors.messages.start.StartStreaming
import uk.co.strimm.experiment.Sink
import uk.co.strimm.gui.GUIMain
import uk.co.strimm.gui.HistogramWindowPlugin
import java.io.FileReader
import java.util.logging.Level

class HistogramSink() : SinkMethod {
    var histogramActor : ActorRef? = null
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
            } catch (ex: Exception) {
                GUIMain.loggerService.log(Level.SEVERE, "Error reading CSV file for histogram sink. Message: ${ex.message}")
                GUIMain.loggerService.log(Level.SEVERE, ex.stackTrace)
            }
        }

        val plugin: HistogramWindowPlugin = GUIMain.dockableWindowPluginService.createPlugin(
            HistogramWindowPlugin::class.java,
            properties,
            true,
            sink.sinkName)

//        plugin.histogramWindowController.sink = sink
//        plugin.histogramWindowController.furtherInit()
        histogramActor = GUIMain.actorService.getActorByName(sink.sinkName)
        plugin.dock(GUIMain.strimmUIService.dockableControl, GUIMain.strimmUIService.strimmFrame)

    }
    override fun run(data : List<STRIMMBuffer>){

    }
    override fun getActorRef() : ActorRef? {
        return histogramActor
    }
    override fun start() : StartStreaming{
        return StartStreaming()
    }
    override fun complete() : CompleteStreaming {
        return CompleteStreaming()
    }
    override fun fail(ex: Throwable) {
        try{
            throw ex
        }
        catch(ex : Exception){
            GUIMain.loggerService.log(Level.SEVERE, "Stream failed in HistogramSink. Message: ${ex.message}")
            GUIMain.loggerService.log(Level.SEVERE, ex.stackTrace)
        }
    }
    override fun postStop() {

    }
    override fun preStart() {
    }
}