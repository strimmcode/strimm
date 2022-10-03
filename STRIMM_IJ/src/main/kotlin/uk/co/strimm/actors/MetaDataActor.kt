package uk.co.strimm.actors

import akka.actor.AbstractActor
import akka.actor.Props
import uk.co.strimm.actors.messages.Message
import uk.co.strimm.gui.GUIMain
import uk.co.strimm.gui.MetaDataWindowPlugin
import java.util.logging.Level

//TODO will metadata actor be used in future?
class MetaDataActor(val plugin : MetaDataWindowPlugin) : AbstractActor(){
    companion object {
        fun props(plugin: MetaDataWindowPlugin): Props {
            return Props.create<MetaDataActor>(MetaDataActor::class.java) { MetaDataActor(plugin) }
        }
    }

    override fun createReceive(): Receive {
        return receiveBuilder().match<Message>(Message::class.java){
            GUIMain.loggerService.log(Level.INFO, "Metadata actor received message")
        }.build()
    }
}