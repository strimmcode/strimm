
package uk.co.strimm.actors

import akka.actor.AbstractActor
import akka.actor.Kill
import akka.actor.Props
import uk.co.strimm.*
import uk.co.strimm.actors.messages.Message
import uk.co.strimm.actors.messages.ask.AskMessageTest
import uk.co.strimm.gui.GUIMain
import uk.co.strimm.actors.messages.complete.CompleteStreaming
import uk.co.strimm.actors.messages.fail.FailStreaming
import uk.co.strimm.actors.messages.start.StartStreaming
import uk.co.strimm.actors.messages.stop.TerminateActor
import uk.co.strimm.actors.messages.tell.*
import uk.co.strimm.experiment.Sink
import java.util.*
import java.util.logging.Level


class SourceTestActor() : AbstractActor(){

    companion object {
        fun props(): Props {
            return Props.create<SourceTestActor>(SourceTestActor::class.java) { SourceTestActor() }
        }
    }


    var dataID = 0
    override fun createReceive(): Receive {
        return receiveBuilder()
            .match<Message>(Message::class.java) { message ->
                println("SourceTestActor <MESSAGE>")
                // use of self rather than this for realised actor plugin.cameraWindowController.associatedActor = self
            }
            .match<StartStreaming>(StartStreaming::class.java){
                GUIMain.loggerService.log(Level.INFO, "Source test actor completed")
                //sender().tell(Acknowledgement.INSTANCE, self())
            }
            .match<CompleteStreaming>(CompleteStreaming::class.java){
                GUIMain.loggerService.log(Level.INFO, "Source test actor completed")
            }
            .match<FailStreaming>(FailStreaming::class.java){
                GUIMain.loggerService.log(Level.SEVERE, "Source test actor failed")

            }
            .match<TerminateActor>(TerminateActor::class.java){
                GUIMain.loggerService.log(Level.INFO, "Source actor ${self.path().name()} terminating")
                self.tell(Kill.getInstance(), self)
            }
            .match<AskMessageTest>(AskMessageTest::class.java){
                //Thread.sleep(100)
                dataID++
                val pix = ByteArray(640*480*3)
                sender().tell(STRIMMPixelBuffer(pix,640,480,"Byte", 3,0, dataID, 1), self() )
            }
            .matchAny{ imm ->
                sender().tell(Acknowledgement.INSTANCE, self())
            }
            .build()
    }

}