import akka.actor.AbstractActor
import akka.actor.Props
import messages.Message

class TraceActor : AbstractActor(){
    companion object {
        fun props(): Props {
            return Props.create<TraceActor>(TraceActor::class.java) { TraceActor() }
        }
    }
    override fun createReceive(): Receive {
        return receiveBuilder()
            .match<Message>(Message::class.java) { message ->
                println("Trace Actor received message")
            }
            .match<Any>(Any::class.java){
                println("Trace actor received Any message (which is actually TraceData class)")
            }
            .build()
    }
}