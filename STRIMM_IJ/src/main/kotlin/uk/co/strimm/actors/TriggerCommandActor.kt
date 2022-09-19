package uk.co.strimm.actors

import akka.actor.AbstractActor
import akka.actor.Props
import uk.co.strimm.actors.messages.Message
import uk.co.strimm.plugins.ExperimentCommandPlugin

class TriggerCommandActor(triggerCommandPlugin : ExperimentCommandPlugin) : AbstractActor(){
    override fun createReceive(): Receive {
        return receiveBuilder()
                .match<Message>(Message::class.java) { message ->
                    println("I am a trigger command actor")
                }
                .build()
    }

    companion object {
        fun props(triggerCommandPlugin : ExperimentCommandPlugin): Props {
            return Props.create<TriggerCommandActor>(TriggerCommandActor::class.java) { TriggerCommandActor(triggerCommandPlugin) }
        }
    }
}