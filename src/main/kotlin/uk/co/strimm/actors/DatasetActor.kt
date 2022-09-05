
package uk.co.strimm.actors


import akka.actor.AbstractActor
import akka.actor.ActorRef
import akka.actor.Kill
import akka.actor.Props

import uk.co.strimm.*
import uk.co.strimm.actors.messages.Message
import uk.co.strimm.actors.messages.ask.AskDatasetSubset
import uk.co.strimm.actors.messages.ask.AskFlushSTRIMMData
import uk.co.strimm.actors.messages.ask.AskJavaClassAndNumEntries
import uk.co.strimm.actors.messages.ask.AskTriggerFlushData
import uk.co.strimm.actors.messages.stop.TerminateActor
import uk.co.strimm.actors.messages.tell.TellCombineTmpDatasetFiles
import uk.co.strimm.actors.messages.tell.TellDisplayInfo
import uk.co.strimm.gui.GUIMain
import java.util.logging.Level
import java.io.File


class DatasetActor() : AbstractActor(){
    val maxSize = 10
    var curCnt = 0
    var buffer = arrayListOf< List<STRIMMBuffer> >()

    companion object {
        fun props(): Props {
            return Props.create<DatasetActor>(DatasetActor::class.java) { DatasetActor() }
        }
    }
    override fun createReceive(): Receive {
        return receiveBuilder()
            .match<Message>(Message::class.java) { message ->
                println("DatasetActor <MESSAGE>")
            }
            .match<TerminateActor>(TerminateActor::class.java){
                GUIMain.loggerService.log(Level.INFO, "DatasetActor actor ${self.path().name()} terminating")
                self.tell(Kill.getInstance(), self)
            }
            .match< AskTriggerFlushData>(AskTriggerFlushData::class.java){
                println("****** flush the remaining buffers")
                GUIMain.actorService.fileManagerActor.tell(AskFlushSTRIMMData(buffer), self)
                sender().tell(Acknowledgement.INSTANCE, self())
            }
            .match<List<*>>(List::class.java){
                println("****dataActor received STRIMMBuffer  " + curCnt.toString())
                buffer.add(it as List<STRIMMBuffer>)
                curCnt++

                if (curCnt == maxSize){
                    println("***** send flush message to FileManagerActor  ")
                    //send the List<STRIMMBuffer> to FileManagerActor
                    GUIMain.actorService.fileManagerActor.tell(AskFlushSTRIMMData(buffer), self)
                    curCnt = 0
                    buffer = arrayListOf< List<STRIMMBuffer> >()
                }
            }
            .matchAny{ im ->
                sender().tell(Acknowledgement.INSTANCE, self())
            }
            .build()
    }
}