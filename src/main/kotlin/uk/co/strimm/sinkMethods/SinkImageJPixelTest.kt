package uk.co.strimm.sinkMethods


import akka.actor.ActorRef
import com.opencsv.CSVReader
import uk.co.strimm.DisplayInfo
import uk.co.strimm.STRIMMBuffer
import uk.co.strimm.actors.messages.complete.CompleteStreaming
import uk.co.strimm.actors.messages.start.StartStreaming
import uk.co.strimm.actors.messages.tell.TellDisplayInfo
import uk.co.strimm.actors.messages.tell.TellDisplayNormalise
import uk.co.strimm.actors.messages.tell.TellDisplaySink
import uk.co.strimm.experiment.Sink
import uk.co.strimm.gui.CameraWindowPlugin
import uk.co.strimm.gui.GUIMain
import java.io.FileReader

class SinkImageJPixelTest() : SinkMethod {
    var cameraActor : ActorRef? = null
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
                println(ex.message)
            }
        }
        //
        //
        var bNormalise = properties["normalise"]!!.toBoolean()
        var displayInfo: DisplayInfo = DisplayInfo(
            sink.sinkName,
            properties["w"]!!.toLong(),
            properties["h"]!!.toLong(),
            properties["pixelType"]!!,
            properties["numChannels"]!!.toInt(),
            properties["previewInterval"]!!.toInt(),
            properties["lut"]!!
        )
        val plugin: CameraWindowPlugin = GUIMain.dockableWindowPluginService.createPlugin(
            CameraWindowPlugin::class.java,
            displayInfo,
            true,
            sink.sinkName
        )
        plugin.cameraWindowController.sink = sink
        cameraActor = GUIMain.actorService.getActorByName(sink.sinkName)
        if (cameraActor != null) {
            GUIMain.experimentService.experimentStream.cameraActors[cameraActor!!] = sink.sinkName
            cameraActor!!.tell(TellDisplaySink(sink), cameraActor)
            cameraActor!!.tell(TellDisplayInfo(displayInfo), cameraActor)
            cameraActor!!.tell(TellDisplayNormalise(bNormalise), cameraActor)
        }
        plugin?.dock(GUIMain.strimmUIService.dockableControl, GUIMain.strimmUIService.strimmFrame)

    }
    override fun run(data : List<STRIMMBuffer>){

    }
    override fun getActorRef() : ActorRef? {
        return cameraActor
    }
    override fun start() : StartStreaming{
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