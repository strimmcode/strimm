package uk.co.strimm.actors

import akka.actor.AbstractActor
import akka.actor.ActorRef
import akka.actor.Props
import uk.co.strimm.Acknowledgement
import uk.co.strimm.CameraMetaDataStore
import uk.co.strimm.STRIMMImage
import uk.co.strimm.actors.messages.Message
import uk.co.strimm.actors.messages.complete.CompleteCameraDataStoring
import uk.co.strimm.actors.messages.fail.FailCameraDataStoring
import uk.co.strimm.actors.messages.start.StartAcquiring
import uk.co.strimm.actors.messages.start.StartCameraDataStoring
import uk.co.strimm.actors.messages.stop.AbortStream
import uk.co.strimm.actors.messages.tell.TellCameraData
import uk.co.strimm.gui.GUIMain
import java.util.logging.Level

//data class ByteImg(var stack : ArrayImg<UnsignedByteType,net.imglib2.img.basictypeaccess.array.ByteArray>)
//data class ShortImg(var stack : ArrayImg<UnsignedShortType,net.imglib2.img.basictypeaccess.array.ShortArray>)
//data class FloatImg(var stack : ArrayImg<FloatType,net.imglib2.img.basictypeaccess.array.FloatArray>)
data class ByteImg(var stack : ByteArray, val xDim : Long, val yDim : Long, val sourceCamera : String)
data class ShortImg(var stack : ShortArray, val xDim : Long, val yDim : Long, val sourceCamera : String)
data class FloatImg(var stack : FloatArray, val xDim : Long, val yDim : Long, val sourceCamera : String)

class ArrayImgStore{
    var byteStack = arrayListOf<ByteImg>()
    var shortStack = arrayListOf<ShortImg>()
    var floatStack = arrayListOf<FloatImg>()
}

class CameraDataStoreActor : AbstractActor() {
    companion object {
        fun props(): Props {
            return Props.create<CameraDataStoreActor>(CameraDataStoreActor::class.java) { CameraDataStoreActor() }
        }
    }

    var imageCounter = 0
    val imgStack = ArrayImgStore()
    val imgStackInfo = arrayListOf<CameraMetaDataStore>()
    var acquiring = false //This flag prevents data store actors acquiring during preview mode
    val numDimensions = 3

    /**
     * Note: data flow will be terminated if the actor doesn't acknowledge data messages from the sender
     */
    override fun createReceive(): Receive {
        return receiveBuilder()
                .match<Message>(Message::class.java) {
                    GUIMain.loggerService.log(Level.INFO, "Camera data store actor receiving message")
                    sender().tell(Acknowledgement.INSTANCE, self())
                }
                .match<StartCameraDataStoring>(StartCameraDataStoring::class.java) {
                    GUIMain.loggerService.log(Level.INFO, "Camera data store actor starting")
                    sender().tell(Acknowledgement.INSTANCE, self())
                }
                .match<CompleteCameraDataStoring>(CompleteCameraDataStoring::class.java) {
                    GUIMain.loggerService.log(Level.INFO, "Camera data store actor storing complete")
                    acquiring = false
                    sender().tell(Acknowledgement.INSTANCE, self())
                }
                .match<AbortStream>(AbortStream::class.java){
                    GUIMain.loggerService.log(Level.INFO, "Camera data store actor aborting (user invoked)")
                    acquiring = false
                    sendData()
                }
                .match<FailCameraDataStoring>(FailCameraDataStoring::class.java) { failCameraDataStoring ->
                    GUIMain.loggerService.log(Level.SEVERE, "Camera data store stream failed. Error message: ${failCameraDataStoring.ex.message}")
                    GUIMain.loggerService.log(Level.SEVERE, failCameraDataStoring.ex.stackTrace)
                    sender().tell(Acknowledgement.INSTANCE, self())
                }
                .match<StartAcquiring>(StartAcquiring::class.java) {
                    GUIMain.loggerService.log(Level.INFO, "Starting camera data storing")
                    acquiring = true
                }
                .match<STRIMMImage>(STRIMMImage::class.java) { image ->
                    if (acquiring) { //This flag prevents data store actors acquiring during preview mode
                        val shouldStop = checkIfShouldStop(image.sourceCamera)
                        println("Camera should stop: " + shouldStop)
                        if (!shouldStop) {
                            println(GUIMain.experimentService.experimentStream.cameraDataStoreActors[getSelf()]  + " " + "  time: " + image.timeAcquired)
                            when (image.pix) {
                                is ByteArray -> {
                                    imgStack.byteStack.add(
                                        ByteImg(
                                            image.pix,
                                            image.w.toLong(),
                                            image.h.toLong(),
                                            image.sourceCamera
                                        )
                                    )
                                }
                                is ShortArray -> {
                                        imgStack.shortStack.add(
                                            ShortImg(
                                                image.pix,
                                                image.w.toLong(),
                                                image.h.toLong(),
                                                image.sourceCamera
                                            )
                                        )

                                }
                                is FloatArray -> {
                                    imgStack.floatStack.add(
                                        FloatImg(
                                            image.pix,
                                            image.w.toLong(),
                                            image.h.toLong(),
                                            image.sourceCamera
                                        )
                                    )
                                }
                            }
                            imgStackInfo.add(CameraMetaDataStore(image.timeAcquired, image.sourceCamera, imageCounter))
                            imageCounter++
                        } else {
                            println("CAMERA Send data")
                            sendData()
                            GUIMain.loggerService.log(Level.INFO, "Camera data store actor no longer acquiring")
                            acquiring = false
                        }
                    }
                    sender().tell(Acknowledgement.INSTANCE, self())
                }
                .matchAny {
                    sender().tell(Acknowledgement.INSTANCE, self())
                }
                .build()
    }

    private fun checkIfShouldStop(deviceLabel : String) : Boolean {
        //TODO configure this to be based on either an elapsed time or number of data points received


        val bKeyPressed = GUIMain.protocolService.jdaq.GetKeyState(GUIMain.experimentService.expConfig.TerminateAcquisitionVirtualCode)  //1
       // println("TERRY: checkIfShouldSop" + bKeyPressed.toString())
        if (bKeyPressed) return true
        return GUIMain.softwareTimerService.getTime() > GUIMain.experimentService.expConfig.experimentDurationMs/1000.0
        //return imageCounter >= GUIMain.experimentService.deviceDatapointNumbers[deviceLabel]!!
    }

    private fun sendData(){
        GUIMain.loggerService.log(Level.INFO, "Sending camera data to file writer actor")

//        println("****details**** " + imgStackInfo[0].cameraFeedName + "   b:" + imgStack.byteStack.size + "  f:" + imgStack.floatStack.size + "   s:" + imgStack.shortStack.size)
//        println("****metadetails***" + imgStackInfo[0].cameraFeedName + "size " + imgStackInfo.size)
        GUIMain.actorService.fileWriterActor.tell(TellCameraData(imgStack, imgStackInfo, GUIMain.experimentService.experimentStream.cameraDataStoreActors[getSelf()] as String), ActorRef.noSender())
    }
//    private fun sendPoisonPill(){
//        self.tell(PoisonPill.getInstance(), ActorRef.noSender())
//    }
}