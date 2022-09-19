package uk.co.strimm.actors

import akka.actor.AbstractActor
import akka.actor.Props
import uk.co.strimm.actors.messages.Message
import uk.co.strimm.gui.MetaDataWindowPlugin

class MetaDataActor(val plugin : MetaDataWindowPlugin) : AbstractActor(){
    companion object {
        fun props(plugin: MetaDataWindowPlugin): Props {
            return Props.create<MetaDataActor>(MetaDataActor::class.java) { MetaDataActor(plugin) }
        }
    }

    override fun createReceive(): Receive {
        return receiveBuilder().match<Message>(Message::class.java){
            println("metadataactor!!!")
        }.build()
    }
}