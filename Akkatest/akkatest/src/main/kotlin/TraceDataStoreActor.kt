import akka.actor.AbstractActor
import akka.actor.ActorRef
import akka.actor.PoisonPill
import akka.actor.Props
import messages.Message

class TraceDataStoreActor : AbstractActor() {
    companion object {
        fun props(): Props {
            return Props.create<TraceDataStoreActor>(TraceDataStoreActor::class.java) { TraceDataStoreActor() }
        }
    }

    override fun createReceive(): AbstractActor.Receive {
        return receiveBuilder()
            .match<Message>(Message::class.java) { message ->
                println("Trace data store actor received message")
            }
            .match<Any>(Any::class.java){
                println("Trace data store actor received Any message (which is actually TraceData class)")
            }
            .build()
    }

    private fun sendPoisonPill(){
        self.tell(PoisonPill.getInstance(), ActorRef.noSender())
    }

    override fun postStop(){
        println("Trace data store actor stopping")
        super.postStop()
    }
}