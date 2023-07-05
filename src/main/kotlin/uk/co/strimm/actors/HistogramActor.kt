package uk.co.strimm.actors

import akka.actor.AbstractActor
import akka.actor.Kill
import akka.actor.Props
import uk.co.strimm.Acknowledgement
import uk.co.strimm.STRIMMBuffer
import uk.co.strimm.STRIMMSignalBuffer
import uk.co.strimm.actors.messages.Message
import uk.co.strimm.actors.messages.complete.CompleteStreaming
import uk.co.strimm.actors.messages.fail.FailStreaming
import uk.co.strimm.actors.messages.start.StartStreaming
import uk.co.strimm.actors.messages.stop.TerminateActor
import uk.co.strimm.gui.GUIMain
import uk.co.strimm.gui.HistogramWindowPlugin
import java.util.logging.Level

class HistogramActor(val plugin: HistogramWindowPlugin) : AbstractActor(){
    companion object {
        fun props(plugin : HistogramWindowPlugin): Props {
            return Props.create<HistogramActor>(HistogramActor::class.java) { HistogramActor(plugin) }
        }
    }
    override fun createReceive(): Receive {
        return receiveBuilder()
            .match<Message>(Message::class.java) { message ->
                GUIMain.loggerService.log(Level.INFO,"Histogram actor receiving message")
            }
            .match<StartStreaming>(StartStreaming::class.java){
                sender().tell(Acknowledgement.INSTANCE, self())
            }
            .match<CompleteStreaming>(CompleteStreaming::class.java){
            }
            .match<FailStreaming>(FailStreaming::class.java){
                GUIMain.loggerService.log(Level.INFO,"Histogram actor fail streaming (this may be expected)")
            }
            .match<TerminateActor>(TerminateActor::class.java){
                GUIMain.loggerService.log(Level.INFO, "Histogram actor ${self.path().name()} terminating")
                self.tell(Kill.getInstance(), self)
            }
            .match<List<*>>(List::class.java){
                plugin.histogramWindowController.updateChart(it as List<STRIMMBuffer>)
                sender().tell(Acknowledgement.INSTANCE, self())
            }
            .matchAny{
                GUIMain.loggerService.log(Level.SEVERE, "Histogram actor does not recognise incoming message")
            }.build()
    }
}
