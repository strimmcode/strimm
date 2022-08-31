import AkkaStream.Companion.sharedKillSwitch
import akka.actor.AbstractActor
import akka.actor.ActorRef
import akka.actor.PoisonPill
import akka.actor.Props
import messages.CompleteCameraDataStoring
import messages.FailCameraDataStoring
import messages.Message
import messages.StartCameraDataStoring

class CameraDataStoreActor : AbstractActor() {
    var numDatapointsReceived = 0
    val dataPointLimit = 50

    companion object {
        fun props(): Props {
            return Props.create<CameraDataStoreActor>(CameraDataStoreActor::class.java) { CameraDataStoreActor() }
        }
    }

    override fun createReceive(): Receive {
        return receiveBuilder()
            .match<Message>(Message::class.java) { message ->
                println("Camera data store actor received message")
            }
            .match<StartCameraDataStoring>(StartCameraDataStoring::class.java) {
                println("Camera data store actor starting")
                sender().tell(Acknowledgement.INSTANCE, self())
            }
            .match<CompleteCameraDataStoring>(CompleteCameraDataStoring::class.java) {
                println("Camera data store actor completing")
                sender().tell(Acknowledgement.INSTANCE, self())
            }
            .match<FailCameraDataStoring>(FailCameraDataStoring::class.java) { failCameraDataStoring ->
                println("Camera data storing failed")
                println(failCameraDataStoring.ex.message!!)
                var stackTraceString = ""
                failCameraDataStoring.ex.stackTrace.forEach { it2 -> stackTraceString += (it2.toString() + "\n") }
                println(stackTraceString)
            }
            .match<STRIMMImage>(STRIMMImage::class.java){ image ->
                if (numDatapointsReceived % 10 == 0 || numDatapointsReceived == 0){
                    println("Camera data store actor received STRIMM image $numDatapointsReceived")
                }

                numDatapointsReceived++
                if (numDatapointsReceived >= dataPointLimit) {
//                    sendPoisonPill()
                    sharedKillSwitch.shutdown()
//                    numDatapointsReceived = 0
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
//        println("Camera data store actor stopping")
//        super.postStop()
//    }
}