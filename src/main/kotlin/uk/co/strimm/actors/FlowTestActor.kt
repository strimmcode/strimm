package uk.co.strimm.actors

import akka.actor.AbstractActor
import akka.actor.Kill
import akka.actor.Props
import uk.co.strimm.Acknowledgement
import uk.co.strimm.STRIMMPixelBuffer
import uk.co.strimm.actors.messages.Message
import uk.co.strimm.actors.messages.ask.AskMessageTest
import uk.co.strimm.actors.messages.complete.CompleteStreaming
import uk.co.strimm.actors.messages.fail.FailStreaming
import uk.co.strimm.actors.messages.start.StartStreaming
import uk.co.strimm.actors.messages.stop.TerminateActor
import uk.co.strimm.gui.GUIMain
import java.util.logging.Level


class FlowTestActor() : AbstractActor(){

    var dataID = 0
    companion object {
        fun props(): Props {
            return Props.create<FlowTestActor>(FlowTestActor::class.java) { FlowTestActor() }
        }
    }

    override fun createReceive(): Receive {
        return receiveBuilder()
            .match<Message>(Message::class.java) { message ->
                println("FlowTestActor <MESSAGE>")
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
                //println("in flow " + dataID)
                val pix = ByteArray(640*480*3)
                for (f in 0..pix.size-1){
                    pix[f] = (Math.random()*255).toInt().toByte()
                }
                sender().tell(STRIMMPixelBuffer(pix,640,480,"Byte", 3,0.0, dataID, 1), self() )
            }
            .matchAny{ imm ->
                sender().tell(Acknowledgement.INSTANCE, self())
            }
            .build()
    }

}