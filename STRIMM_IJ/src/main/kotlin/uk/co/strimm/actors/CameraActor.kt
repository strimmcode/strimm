package uk.co.strimm.actors

import akka.actor.AbstractActor
import akka.actor.Kill
import akka.actor.Props
import net.imglib2.RandomAccessibleInterval
import net.imglib2.type.numeric.integer.UnsignedByteType
import net.imglib2.view.Views
import uk.co.strimm.Acknowledgement
import uk.co.strimm.ResizeValues
import uk.co.strimm.actors.messages.Message
import uk.co.strimm.actors.messages.complete.CompleteCameraStreaming
import uk.co.strimm.actors.messages.fail.FailCameraStreaming
import uk.co.strimm.actors.messages.start.StartStreamingCamera
import uk.co.strimm.gui.CameraWindowPlugin
import uk.co.strimm.gui.GUIMain
import uk.co.strimm.STRIMMImage
import uk.co.strimm.actors.messages.ask.*
import uk.co.strimm.actors.messages.stop.TerminateActor
import uk.co.strimm.actors.messages.tell.*
import java.util.logging.Level
import kotlin.math.max
import kotlin.math.min

/**
 * This actor deals with incomming images in STRIMMImage classes. It's main function is to send images to the
 * camera window controller. This actor is not involved in storing data.
 */
class CameraActor(val plugin: CameraWindowPlugin) : AbstractActor() {

    companion object {
        fun props(plugin: CameraWindowPlugin): Props {
            return Props.create<CameraActor>(CameraActor::class.java) { CameraActor(plugin) }
        }
    }

    var autoscale = true
    var isResized = false
    var isAcquiring = true
    var x = 0.toLong()
    var y = 0.toLong()
    var w = 0.toLong()
    var h = 0.toLong()
    var timeLast = 0.0
    var sinkName: String = ""

    override fun createReceive(): Receive {
        return receiveBuilder()
                .match<Message>(Message::class.java) { message ->
                    plugin.cameraWindowController.associatedActor = self
                    GUIMain.actorService.cameraActorDisplays[plugin.cameraWindowController.displayInfo!!.feedName] = self
                }
                .match<AskDisplayName>(AskDisplayName::class.java) { sendDisplayNameMessage ->
                    sender().tell(plugin.cameraWindowController.display!!.name, self())
                }
                .match<AskDisplaySinkName>(AskDisplaySinkName::class.java) { sendDisplaySinkMessage ->
                    sender().tell(sinkName, self())
                }
                .match<AskCameraDeviceLabel>(AskCameraDeviceLabel::class.java) {
                    sender.tell(plugin.cameraWindowController.cameraDevice!!.label, self())
                }
                .match<AskDatasetName>(AskDatasetName::class.java) {
                    sender.tell(plugin.cameraWindowController.dataset!!.name, self())
                }
                .match<AskCameraStreamSource>(AskCameraStreamSource::class.java) { askCameraStreamSourceMessage ->
                    sender().tell(plugin.cameraWindowController.cameraDevice?.label, self())
                }
                .match<TellDisplaySinkName>(TellDisplaySinkName::class.java) { sinkNameMessage ->
                    sinkName = sinkNameMessage.sinkName

                }
                .match<TellDisplayAutoscale>(TellDisplayAutoscale::class.java) { autoscaleMessage ->
                    autoscale = autoscaleMessage.bAutoscale
                }
                .match<TellCameraResize>(TellCameraResize::class.java) { resizeMessage ->
                    x = resizeMessage.x.toLong()
                    y = resizeMessage.y.toLong()
                    w = resizeMessage.w.toLong()
                    h = resizeMessage.h.toLong()

                    //First step is to crop the existing dataset, however the crop operation is then used to create a new dataset
                    resizeDataset(resizeMessage)

                    //Update the view size list
                    GUIMain.strimmUIService.cameraViewSizeList[plugin.cameraWindowController.cameraDevice!!.label] = ResizeValues(x, y, w, h)

                    //Second step is to re-initialise the display with the new width and height of the dataset
                    reinitialiseDisplay()

                    isResized = true
                }
                .match<TellFullView>(TellFullView::class.java) { fullViewMessage ->
                    plugin.cameraWindowController.dataset = null
                    plugin.cameraWindowController.initialiseDisplay()
                    isResized = false
                }
                .match<TellCameraChangeZ>(TellCameraChangeZ::class.java) {
                    plugin.cameraWindowController.view!!.setPosition(it.newValue)
                    plugin.cameraWindowController.view!!.update()
                }
                .match<StartStreamingCamera>(StartStreamingCamera::class.java) {
                    timeLast = 0.0
                    plugin.cameraWindowController.initialiseDisplay()
                    val camViewSize = GUIMain.strimmUIService.cameraViewSizeList.filter { x -> x.key == plugin.cameraWindowController.displayInfo!!.feedName }.toList()
                    if (camViewSize.isNotEmpty()) {
                        val camView = camViewSize.first().second
                        //We can then resize the camera feed (dataset)
                        self.tell(TellCameraResize(camView.x!!.toDouble(), camView.y!!.toDouble(), camView.w!!.toDouble(), camView.h!!.toDouble()), self)
                    }
                    sender().tell(Acknowledgement.INSTANCE, self())
                }
                .match<CompleteCameraStreaming>(CompleteCameraStreaming::class.java) {
                    GUIMain.loggerService.log(Level.INFO, "Camera actor completed")
                }
                .match<FailCameraStreaming>(FailCameraStreaming::class.java) {
                    GUIMain.loggerService.log(Level.SEVERE, "Camera actor failed. Message: ${it.ex.message}")
                    GUIMain.loggerService.log(Level.SEVERE, it.ex.stackTrace)
                }
                .match<TellCameraIsAcquiring>(TellCameraIsAcquiring::class.java) {
                    isAcquiring = it.isAcquiring
                }
                .match<STRIMMImage>(STRIMMImage::class.java) { image ->
                    if (isAcquiring) {
                        val dataset = plugin.cameraWindowController.dataset!!
                        for (snk in GUIMain.experimentService.expConfig.sinkConfig.sinks) {
                            //identify the src from the sourceCamera field in the STRIMMImage
                            if (snk.sinkName == plugin.cameraWindowController.displayInfo!!.feedName) {
//                                println(image.imageCount.toString() + "   " + image.timeAcquired  )
//                                if (image.timeAcquired.toDouble() > timeLast + snk.previewInterval) { //TODO why is this check needed?
                                    when (image.pix) {
                                        is ByteArray -> dataset.apply {
                                            var imgToUse = image.pix as ByteArray?
                                            if (isResized) {
                                                imgToUse = GUIMain.acquisitionMethodService.getImageSubsetByteArray(image, ResizeValues(x, y, w, h))
                                            }

                                            setPlane(0, imgToUse)

                                            if (GUIMain.strimmUIService.autoScaleCheck) {
                                                normaliseImageByte(image.pix)
                                            }
                                            else {
                                                val minMax = imgToUse!!.fold(Pair(Byte.MAX_VALUE.toDouble(), Byte.MIN_VALUE.toDouble())) { acc, v -> Pair(min(acc.first, v.toDouble()), max(acc.second, v.toDouble())) }
                                                plugin.cameraWindowController.view?.setChannelRange(0, minMax.first, minMax.second)
                                            }

                                            //This tells the dataset things have changed and it needs to refresh
                                            isDirty = true
                                        }
                                        is ShortArray -> dataset.apply {
                                            var imgToUse = image.pix as ShortArray?
                                            if (isResized) {
                                                imgToUse = GUIMain.acquisitionMethodService.getImageSubsetShortArray(image, ResizeValues(x, y, w, h))
                                            }

                                            setPlane(0, imgToUse)

                                            if (autoscale) {
                                                normaliseImageShort(image.pix)
                                            }
                                            else{
                                                val minMax = image.pix.fold(Pair(Short.MAX_VALUE.toDouble(), Short.MIN_VALUE.toDouble())) {
                                                    acc, v -> Pair(min(acc.first, v.toDouble()), max(acc.second, v.toDouble()))
                                                }
                                                plugin.cameraWindowController.view?.setChannelRange(0, minMax.first, minMax.second)
                                            }

                                            //This tells the dataset things have changed and it needs to refresh
                                            isDirty = true
                                        }
                                        is FloatArray -> dataset.apply {
                                            var imgToUse = image.pix as FloatArray?
                                            if (isResized) {
                                                imgToUse = GUIMain.acquisitionMethodService.getImageSubsetFloatArray(image, ResizeValues(x, y, w, h))
                                            }

                                            setPlane(0, imgToUse)

                                            if (GUIMain.strimmUIService.autoScaleCheck) {
                                                normaliseImageFloat(image.pix)
                                            }
                                            else {
                                                val minMax = image.pix.fold(Pair(Float.MAX_VALUE.toDouble(), Float.MIN_VALUE.toDouble())) {
                                                    acc, v -> Pair(min(acc.first, v.toDouble()), max(acc.second, v.toDouble()))
                                                }
                                                plugin.cameraWindowController.view?.setChannelRange(0, minMax.first, minMax.second)
                                            }

                                            //This tells the dataset things have changed and it needs to refresh
                                            isDirty = true
                                        }
                                    }

                                    timeLast = image.timeAcquired.toDouble()
//                                }
                            }
                        }
                    }
                    sender().tell(Acknowledgement.INSTANCE, self())
                }
                .match<TerminateActor>(TerminateActor::class.java) {
                    GUIMain.loggerService.log(Level.INFO, "Camera actor ${self.path().name()} terminating")
                    self.tell(Kill.getInstance(), self)
                }
                .matchAny {
                    GUIMain.loggerService.log(Level.WARNING, "Camera actor ${self.path().name()} does not recognise message")
                }
                .build()
    }

    private fun resizeDataset(resizeMessage: TellCameraResize) {
        val maxX = resizeMessage.x.toLong() + resizeMessage.w.toLong()
        val maxY = resizeMessage.y.toLong() + resizeMessage.h.toLong()
        val interval = Views.interval(plugin.cameraWindowController.dataset,
                longArrayOf(resizeMessage.x.toLong(), resizeMessage.y.toLong()),
                longArrayOf(maxX, maxY))
        val croppedImage = GUIMain.opService.transform().crop(plugin.cameraWindowController.dataset!!.imgPlus, interval) as RandomAccessibleInterval<UnsignedByteType>
        val newStack = GUIMain.opService.create().img(croppedImage)
        val newDataset = GUIMain.datasetService.create(newStack)
        plugin.cameraWindowController.dataset = newDataset
    }

    private fun reinitialiseDisplay() {
        plugin.cameraWindowController.initialiseDisplay(w, h)
    }

    private fun normaliseImageByte(pix: ByteArray){
        val imgMax = pix.max()
        val imgMin = pix.min()
        plugin.cameraWindowController.view?.setChannelRange(0, imgMin!!.toDouble(), imgMax!!.toDouble())
    }

    private fun normaliseImageShort(pix: ShortArray){
        val imgMax = pix.max()
        val imgMin = pix.min()
        plugin.cameraWindowController.view?.setChannelRange(0, imgMin!!.toDouble(), imgMax!!.toDouble())
    }

    private fun normaliseImageFloat(pix: FloatArray){
        val imgMax = pix.max()
        val imgMin = pix.min()
        plugin.cameraWindowController.view?.setChannelRange(0, imgMin!!.toDouble(), imgMax!!.toDouble())
    }
}