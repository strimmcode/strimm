package uk.co.strimm.actors

import akka.actor.AbstractActor
import akka.actor.Kill
import akka.actor.Props
import scala.Byte
import uk.co.strimm.*
import uk.co.strimm.actors.messages.Message
import uk.co.strimm.actors.messages.ask.AskMessageTest
import uk.co.strimm.gui.CameraWindowPlugin
import uk.co.strimm.gui.GUIMain
import uk.co.strimm.actors.messages.complete.CompleteStreaming
import uk.co.strimm.actors.messages.fail.FailStreaming
import uk.co.strimm.actors.messages.start.StartStreaming
import uk.co.strimm.actors.messages.stop.TerminateActor
import uk.co.strimm.actors.messages.tell.*
import uk.co.strimm.experiment.Sink
import java.util.logging.Level

//The CameraActor receives STRIMMImage samples from the akka graph
class CameraActor(val plugin: CameraWindowPlugin) : AbstractActor(){

    companion object {
        fun props(plugin: CameraWindowPlugin): Props {
            return Props.create<CameraActor>(CameraActor::class.java) { CameraActor(plugin) }
        }
    }

    var isResized = false
    var isAcquiring = true

    var timeLast  = 0.0
    var sinkName : String = ""
    var sink : Sink? = null
    var displayInfo : DisplayInfo? = null;

    override fun createReceive(): Receive {
        return receiveBuilder()
            .match<Message>(Message::class.java) { message ->
                println("cameraActor <MESSAGE>")
                plugin.cameraWindowController.associatedActor = self
                GUIMain.actorService.cameraActorDisplays[plugin.cameraWindowController.displayInfo!!.displayName] = self
            }
            .match<TellDisplaySink>(TellDisplaySink::class.java){sinkMessage->
                sink = sinkMessage.sink
            }
            .match<TellDisplayInfo>(TellDisplayInfo::class.java){displayInfoMessage->
                displayInfo = displayInfoMessage.displayInfo
            }
            .match<StartStreaming>(StartStreaming::class.java){
                timeLast = 0.0
                plugin.cameraWindowController.initialiseDisplay()
                sender().tell(Acknowledgement.INSTANCE, self())
            }
            .match<CompleteStreaming>(CompleteStreaming::class.java){
                GUIMain.loggerService.log(Level.INFO, "Camera actor completed")
            }
            .match<FailStreaming>(FailStreaming::class.java){
                GUIMain.loggerService.log(Level.SEVERE, "Camera actor failed")
                GUIMain.loggerService.log(Level.SEVERE, it.ex.message!!)
                var stackTraceString = ""
                it.ex.stackTrace.forEach { it2 -> stackTraceString += (it2.toString() + "\n") }
                GUIMain.loggerService.log(Level.SEVERE, stackTraceString)
            }
            .match<TerminateActor>(TerminateActor::class.java){
                GUIMain.loggerService.log(Level.INFO, "Camera actor ${self.path().name()} terminating")
                self.tell(Kill.getInstance(), self)
            }
            .match<List<*>>(List::class.java){ imm ->
                if (GUIMain.softwareTimerService.getTime() > timeLast + displayInfo!!.previewInterval) {
                    //sink is expecting STRIMMBuffer configured according to sink.cfg
                    val imageList1 = imm as List<STRIMMPixelBuffer>
                    var im1 =  imageList1[0]
                    //println("dataID " + im1.dataID)
                    val w = displayInfo!!.width.toInt()
                    val h = displayInfo!!.height.toInt()
                    val pixelType = displayInfo!!.pixelType
                    val numChannels = displayInfo!!.numChannels
                    if (w != im1.w || h != im1.h || im1.pixelType != pixelType || im1.numChannels != numChannels){
                        println("ERROR - the info carried by the STRIMMBuffer is different to the Sink's configured expectations")
                    }
                    val dataset = plugin.cameraWindowController.dataset!!
                    if (im1.pixelType == "Byte"){
                        val pix = im1.pix as ByteArray
                        for (ch in 0..numChannels-1) {
                            if (ch < numChannels-1) dataset.setPlaneSilently(ch, pix.sliceArray((ch * w * h)..(((ch + 1) * w * h) - 1)))
                            else dataset.setPlane(ch, pix.sliceArray((ch * w * h)..(((ch + 1) * w * h) - 1)))
                        }
                    }
                    else if (im1.pixelType == "Short"){

                        val pix = im1.pix as ShortArray
                        for (ch in 0..numChannels-1) {
                            if (ch < numChannels-1) dataset.setPlaneSilently(ch, pix.sliceArray((ch * w * h)..(((ch + 1) * w * h) - 1)))
                            else dataset.setPlane(ch, pix.sliceArray((ch * w * h)..(((ch + 1) * w * h) - 1)))
                        }
                        val minMax = pix.fold(
                            Pair(
                                Float.MAX_VALUE.toDouble(),
                                Float.MIN_VALUE.toDouble()
                            )
                        ) { acc, v -> Pair(kotlin.math.min(acc.first, v.toDouble()), kotlin.math.max(acc.second, v.toDouble())) }
                        plugin.cameraWindowController.view?.setChannelRange(0, minMax.first, minMax.second)
                    }
                    else if (im1.pixelType == "Int"){
                        val pix = im1.pix as IntArray
                        for (ch in 0..numChannels-1) {
                            if (ch < numChannels-1) dataset.setPlaneSilently(ch, pix.sliceArray((ch * w * h)..(((ch + 1) * w * h) - 1)))
                            else dataset.setPlane(ch, pix.sliceArray((ch * w * h)..(((ch + 1) * w * h) - 1)))
                        }
                    }
                    else if (im1.pixelType == "Float"){
                        val pix = im1.pix as FloatArray
                        dataset.setPlane(0, pix)
                        //ImageJ sets crazy values for MAX and MIN
                        val minMax = pix.fold(
                            Pair(
                                Float.MAX_VALUE.toDouble(),
                                Float.MIN_VALUE.toDouble()
                            )
                        ) { acc, v -> Pair(kotlin.math.min(acc.first, v.toDouble()), kotlin.math.max(acc.second, v.toDouble())) }
                        plugin.cameraWindowController.view?.setChannelRange(0, minMax.first, minMax.second)
                    }
                    else if (im1.pixelType == "Double"){
                        val pix = im1.pix as DoubleArray
                        dataset.setPlane(0, pix)
                        //ImageJ sets crazy values for MAX and MIN
                        val minMax = pix.fold(
                            Pair(
                                Float.MAX_VALUE.toDouble(),
                                Float.MIN_VALUE.toDouble()
                            )
                        ) { acc, v -> Pair(kotlin.math.min(acc.first, v.toDouble()), kotlin.math.max(acc.second, v.toDouble())) }
                        plugin.cameraWindowController.view?.setChannelRange(0, minMax.first, minMax.second)
                    }

                    dataset.isDirty = true

                    timeLast = GUIMain.softwareTimerService.getTime()
                    im1.timeAcquired = GUIMain.softwareTimerService.getTime()
                    //println("******send to fileManagerActor")
                    GUIMain.actorService.fileManagerActor.tell(STRIMMSaveBuffer(imageList1 , sink!!.sinkName),self)
                }
                sender().tell(Acknowledgement.INSTANCE, self())
            }
            .matchAny{
            }
            .build()
    }
}