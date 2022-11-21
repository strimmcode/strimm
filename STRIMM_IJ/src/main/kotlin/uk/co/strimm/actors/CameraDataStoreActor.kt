package uk.co.strimm.actors

import akka.actor.AbstractActor
import akka.actor.ActorRef
import akka.actor.Props
import javafx.scene.input.KeyCode
import uk.co.strimm.Acknowledgement
import uk.co.strimm.CameraMetaDataStore
import uk.co.strimm.ExperimentConstants.Acquisition.Companion.DATA_TERMCOND
import uk.co.strimm.ExperimentConstants.Acquisition.Companion.KEYBOARD_TERMCOND
import uk.co.strimm.ExperimentConstants.Acquisition.Companion.TIME_TERMCOND
import uk.co.strimm.STRIMMImage
import uk.co.strimm.actors.messages.Message
import uk.co.strimm.actors.messages.complete.CompleteCameraDataStoring
import uk.co.strimm.actors.messages.fail.FailCameraDataStoring
import uk.co.strimm.actors.messages.start.StartAcquiring
import uk.co.strimm.actors.messages.start.StartCameraDataStoring
import uk.co.strimm.actors.messages.stop.AbortStream
import uk.co.strimm.actors.messages.tell.TellCameraData
import uk.co.strimm.actors.messages.tell.TellTerminatingCondition
import uk.co.strimm.gui.GUIMain
import java.awt.event.KeyEvent
import java.awt.event.KeyListener
import java.util.logging.Level
import scala.concurrent.duration.*
import uk.co.strimm.actors.messages.tell.TellKeyboardPress
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit


open class CameraImg(val type: String) //Superclass for generics help when dealing with the data
data class ByteImg(var stack : ByteArray, val xDim : Long, val yDim : Long, val sourceCamera : String) : CameraImg("Byte")
data class ShortImg(var stack : ShortArray, val xDim : Long, val yDim : Long, val sourceCamera : String): CameraImg("Short")
data class FloatImg(var stack : FloatArray, val xDim : Long, val yDim : Long, val sourceCamera : String): CameraImg("Float")

class ArrayImgStore{
    var byteStack = arrayListOf<ByteImg>()
    var shortStack = arrayListOf<ShortImg>()
    var floatStack = arrayListOf<FloatImg>()
}

class CameraDataStoreActor : AbstractActor(){
    companion object {
        fun props(): Props {
            return Props.create<CameraDataStoreActor>(CameraDataStoreActor::class.java) { CameraDataStoreActor() }
        }
    }

    var imageCounter = 0
    val imgStack = ArrayImgStore()
    val imgStackInfo = arrayListOf<CameraMetaDataStore>()
    var acquiring = false //This flag prevents data store actors acquiring during preview mode
    var terminatingCondition = TIME_TERMCOND //Elapsed time is the default terminating condition
    var terminatingKey = ""

    /**
     * Note: data flow will be terminated if the actor doesn't acknowledge data messages from the sender
     */
    override fun createReceive(): Receive {
        return receiveBuilder()
                .match<Message>(Message::class.java) {
                    GUIMain.loggerService.log(Level.INFO, "Camera data store actor ${self.path().name()} receiving message")
                    sender().tell(Acknowledgement.INSTANCE, self())
                }
                .match<StartCameraDataStoring>(StartCameraDataStoring::class.java) {
                    GUIMain.loggerService.log(Level.INFO, "Camera data store actor ${self.path().name()} starting")
                    sender().tell(Acknowledgement.INSTANCE, self())
                }
                .match<CompleteCameraDataStoring>(CompleteCameraDataStoring::class.java) {
                    GUIMain.loggerService.log(Level.INFO, "Camera data store actor ${self.path().name()} storing complete")
                    acquiring = false
                    sender().tell(Acknowledgement.INSTANCE, self())
                }
                .match<AbortStream>(AbortStream::class.java){
                    GUIMain.loggerService.log(Level.INFO, "Camera data store actor ${self.path().name()} aborting (user invoked)")
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
                .match<TellTerminatingCondition>(TellTerminatingCondition::class.java){
                    terminatingCondition = it.terminatingCondition
                    if(terminatingCondition == KEYBOARD_TERMCOND){
                        terminatingKey = it.terminatingKey
                    }
                }
                .match<TellKeyboardPress>(TellKeyboardPress::class.java){
                    val terminate = keyboardTerminate(it.keyCode)
                    if(terminate && (terminatingCondition == KEYBOARD_TERMCOND) && acquiring){
                        sendData()
                        GUIMain.loggerService.log(Level.INFO, "Camera data store actor terminated by keyboard, no longer acquiring")
                        acquiring = false
                    }
                }
                .match<STRIMMImage>(STRIMMImage::class.java) { image ->
                    if (acquiring) { //This flag prevents data store actors acquiring during preview mode

                        //Uncomment to check incomming frame times
                        //println(GUIMain.experimentService.experimentStream.cameraDataStoreActors[getSelf()]  + " " + "  time: " + image.timeAcquired)

                        //Make sure the correct data type is used for the incomming data
                        when (image.pix) {
                            is ByteArray -> {
                                imgStack.byteStack.add(ByteImg(image.pix,
                                        image.w.toLong(),
                                        image.h.toLong(),
                                        image.sourceCamera))
                            }
                            is ShortArray -> {
                                imgStack.shortStack.add(ShortImg(image.pix,
                                        image.w.toLong(),
                                        image.h.toLong(),
                                        image.sourceCamera))
                            }
                            is FloatArray -> {
                                imgStack.floatStack.add(FloatImg(image.pix,
                                        image.w.toLong(),
                                        image.h.toLong(),
                                        image.sourceCamera))
                            }
                        }

//                        println("Frame $imageCounter from ${image.sourceCamera} acquired at: ${image.timeAcquired}")
                        imgStackInfo.add(CameraMetaDataStore(image.timeAcquired, image.sourceCamera, imageCounter))
                        imageCounter++

                        val shouldStop = checkIfShouldStop(image.sourceCamera, image.timeAcquired)
                        if (shouldStop) {
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

    private fun keyboardTerminate(keyCode : Int) : Boolean {
        val keyCodeText = KeyEvent.getKeyText(keyCode)
        if(keyCodeText == terminatingKey){
            return true
        }
        return false
    }

    private fun checkIfShouldStop(deviceLabel : String, timeAcquired : Number) : Boolean {
        val bKeyPressed = GUIMain.protocolService.jdaq.GetKeyState(GUIMain.experimentService.expConfig.TerminateAcquisitionVirtualCode)
        if (bKeyPressed) {
            return true
        }

        /*
        Note that this when statement doesn't consider keyboard terminating condition. This is because the keyboard
        condition needs to handle a keypress at any point during the acuquisition, not just when data is being received
        */
        when(terminatingCondition){
            DATA_TERMCOND -> {
                return imageCounter >= GUIMain.experimentService.deviceDatapointNumbers[deviceLabel]!!
            }
            TIME_TERMCOND -> {
                return timeAcquired.toDouble() > GUIMain.experimentService.expConfig.experimentDurationMs/1000.0
            }
            else ->{
                return false
            }
        }


//        //Acquisitions can be stopped at any time from a keyboard press (key configured in the config property "TerminateAcquisitionVirtualCode")
//        val bKeyPressed = GUIMain.protocolService.jdaq.GetKeyState(GUIMain.experimentService.expConfig.TerminateAcquisitionVirtualCode)
//        if (bKeyPressed) {
//            return true
//        }
//
//
//        return if(GUIMain.protocolService.isEpisodic){
//            println("Camera $deviceLabel is using episodic")
//            GUIMain.softwareTimerService.getTime() > GUIMain.experimentService.expConfig.experimentDurationMs/1000.0
//        }
//        else{
//            //Continuous
//            println("Camera $deviceLabel is NOT using episodic")
//            imageCounter >= GUIMain.experimentService.deviceDatapointNumbers[deviceLabel]!!
//        }
    }

    private fun sendData(){
        GUIMain.loggerService.log(Level.INFO, "Camera data store actor ${self.path().name()} sending data to file writer actor")
        GUIMain.actorService.fileWriterActor.tell(TellCameraData(imgStack, imgStackInfo, GUIMain.experimentService.experimentStream.cameraDataStoreActors[self] as String), ActorRef.noSender())
    }
}