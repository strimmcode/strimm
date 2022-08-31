import AkkaStream.Companion.sharedKillSwitch
import akka.actor.AbstractActor
import akka.actor.ActorRef
import akka.actor.PoisonPill
import akka.actor.Props
import messages.CompleteCameraStreaming
import messages.FailCameraStreaming
import messages.Message
import messages.StartStreamingCamera
import java.util.logging.Level

class CameraActor : AbstractActor(){
    var numDatapointsReceived = 0
    val dataPointLimit = 50

    companion object {
        fun props(): Props {
            return Props.create<CameraActor>(CameraActor::class.java) { CameraActor() }
        }
    }

    override fun createReceive(): Receive {
        return receiveBuilder()
            .match<Message>(Message::class.java) { message ->
                println("Camera Actor received message")
            }
            .match<StartStreamingCamera>(StartStreamingCamera::class.java){
                println("Camera stream started")
                sender().tell(Acknowledgement.INSTANCE, self())
            }
            .match<CompleteCameraStreaming>(CompleteCameraStreaming::class.java){
                println("Camera stream completed")
            }
            .match<FailCameraStreaming>(FailCameraStreaming::class.java){
                println("Camera stream failed")
                println(it.ex.message!!)
                var stackTraceString = ""
                it.ex.stackTrace.forEach { it2 -> stackTraceString += (it2.toString() + "\n") }
                println(stackTraceString)
            }
            .match<STRIMMImage>(STRIMMImage::class.java){ image ->
                if (numDatapointsReceived % 10 == 0 || numDatapointsReceived == 0){
                    println("Camera actor received STRIMM image $numDatapointsReceived")
                }

                numDatapointsReceived++
                if (numDatapointsReceived >= dataPointLimit) {
//                    sendPoisonPill()
                    println("Total number of datapoints received: $numDatapointsReceived")
                    println("Using sharedKillSwitch.shutdown()")
                    sharedKillSwitch.shutdown()
//                    numDatapointsReceived = 0
//                    val akkaStream = AkkaStream()
//                    AkkaStream.globalFlag = false
//                    println("Global flag is currently ${AkkaStream.globalFlag}")

//                    akkaStream.runStream()
                }
                sender().tell(Acknowledgement.INSTANCE, self())
            }
            .build()
    }

//    private fun sendPoisonPill(){
//        self.tell(PoisonPill.getInstance(), ActorRef.noSender())
//    }
//
//    override fun postStop(){
//        println("Camera  actor stopping")
//        super.postStop()
//    }
}