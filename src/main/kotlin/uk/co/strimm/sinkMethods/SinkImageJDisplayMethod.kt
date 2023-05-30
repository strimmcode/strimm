package uk.co.strimm.sinkMethods

import akka.actor.ActorRef
import com.opencsv.CSVReader
import uk.co.strimm.DisplayInfo
import uk.co.strimm.RoiInfo
import uk.co.strimm.STRIMMBuffer
import uk.co.strimm.actors.messages.complete.CompleteStreaming
import uk.co.strimm.actors.messages.start.StartStreaming
import uk.co.strimm.actors.messages.tell.TellDisplayInfo
import uk.co.strimm.actors.messages.tell.TellDisplayNormalise
import uk.co.strimm.actors.messages.tell.TellDisplaySink
import uk.co.strimm.experiment.ROI
import uk.co.strimm.experiment.ROIManager
import uk.co.strimm.experiment.Sink
import uk.co.strimm.gui.CameraWindowPlugin
import uk.co.strimm.gui.GUIMain
import java.io.FileReader
import java.util.logging.Level

class SinkImageJDisplayMethod : SinkMethod {
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

        //read roiSz
        val roiSz = properties["roiSz"]
        if (roiSz != "" && roiSz != null) {
            GUIMain.experimentService.loadtimeRoiList[sink.sinkName] = mutableListOf<RoiInfo>()
            GUIMain.experimentService.runtimeRoiList[sink.sinkName] = mutableListOf<RoiInfo>()
            val rois: List<ROI> = GUIMain.strimmROIService.DecodeROIReference(roiSz as String)
            for (f in 0..rois.size - 1) {
                GUIMain.loggerService.log(Level.INFO, "Creating ROI from $roiSz")
                val overlay = ROIManager.createOverlayFromROIObject(rois[f])
                if (overlay != null) {
                    overlay.name = f.toString()
                    val mutableRoiList = GUIMain.experimentService.loadtimeRoiList[sink.sinkName] as MutableList<RoiInfo>
                    mutableRoiList.add(RoiInfo(rois[f], overlay))
                }
            }
        }
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
//        GUIMain.loggerService.log(Level.SEVERE, "Error in SinkImageJDisplayMethod. Message: ${ex.message}")
//        GUIMain.loggerService.log(Level.SEVERE, ex.stackTrace)
//        GUIMain.loggerService.log(Level.SEVERE, ex.cause!!.stackTrace)
    }

    override fun postStop() {

    }

    override fun preStart() {
    }
}