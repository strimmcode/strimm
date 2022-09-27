package uk.co.strimm.actors

import akka.actor.AbstractActor
import akka.actor.ActorRef
import akka.actor.PoisonPill
import akka.actor.Props
import uk.co.strimm.Acknowledgement
import uk.co.strimm.TraceData
import uk.co.strimm.TraceDataStore
import uk.co.strimm.actors.messages.Message
import uk.co.strimm.actors.messages.ask.AskIsTraceROI
import uk.co.strimm.actors.messages.complete.CompleteTraceDataStoring
import uk.co.strimm.actors.messages.fail.FailTraceDataStoring
import uk.co.strimm.actors.messages.start.StartTraceDataStoring
import uk.co.strimm.actors.messages.start.StartTraceStore
import uk.co.strimm.actors.messages.stop.AbortStream
import uk.co.strimm.actors.messages.tell.TellAnalogueDataStream
import uk.co.strimm.actors.messages.tell.TellIsTraceROIActor
import uk.co.strimm.actors.messages.tell.TellTraceData
import uk.co.strimm.actors.messages.tell.TellTraceSinkName
import uk.co.strimm.gui.GUIMain
import uk.co.strimm.services.AnalogueDataStream
import java.util.*
import java.util.logging.Level

class TraceDataStoreActor : AbstractActor() {
    companion object {
        fun props(): Props {
            return Props.create<TraceDataStoreActor>(TraceDataStoreActor::class.java) { TraceDataStoreActor() }
        }
    }

    val traceData = ArrayList<TraceDataStore>()
    var traceStore = false //This flag prevents data store actors acquiring during preview mode
    var dataPointCounters = hashMapOf<String,Int>()
    var associatedAnalogueDataStream : AnalogueDataStream? = null
    private var isTraceFromROI = false
    var acquiring = false
    var sinkName : String = ""

    /**
     * Note: data flow will be terminated if the actor doesn't acknowledge data messages from the sender
     */
    override fun createReceive(): Receive {
        return receiveBuilder()
            .match<Message>(Message::class.java) { message ->
                GUIMain.loggerService.log(Level.INFO, "Trace data store actor receiving message")
                sender().tell(Acknowledgement.INSTANCE, self())
            }
            .match<StartTraceDataStoring>(StartTraceDataStoring::class.java){ startTraceDataStoring ->
                GUIMain.loggerService.log(Level.INFO, "Trace data store actor starting data storing")
                sender().tell(Acknowledgement.INSTANCE, self())
            }
            .match<CompleteTraceDataStoring>(CompleteTraceDataStoring::class.java){ completeTraceDataStoring ->
                GUIMain.loggerService.log(Level.INFO, "Trace data store actor completing data storing")
                sender().tell(Acknowledgement.INSTANCE, self())
            }
            .match<FailTraceDataStoring>(FailTraceDataStoring::class.java){ failTraceDataStoring ->
                GUIMain.loggerService.log(Level.SEVERE, "Trace data store stream failed. Error message: ${failTraceDataStoring.ex.message}")
                GUIMain.loggerService.log(Level.SEVERE, failTraceDataStoring.ex.stackTrace)
                sender().tell(Acknowledgement.INSTANCE, self())
            }
            .match<AbortStream>(AbortStream::class.java){
                GUIMain.loggerService.log(Level.INFO, "Camera data store actor aborting (user invoked)")
                traceStore = false
                println("abort+++++++++++++++++++++")
                sendData()
            }
            .match<StartTraceStore>(StartTraceStore::class.java){
                traceStore = true
            }
            .match<TellAnalogueDataStream>(TellAnalogueDataStream::class.java){
                associatedAnalogueDataStream = it.analogueDataStream
                it.analogueDataStream.associatedTraceDataStoreActor = this.self()
            }
            .match<TellTraceSinkName>(TellTraceSinkName::class.java){
                sinkName = it.sinkName
            }
            .match<TellIsTraceROIActor>(TellIsTraceROIActor::class.java){
                isTraceFromROI = true
            }
            .match<AskIsTraceROI>(AskIsTraceROI::class.java){
                sender().tell(isTraceFromROI, self())
            }
            .matchAny {
                val incomingDataList = it as List<List<TraceData>>
                val incomingData = incomingDataList.flatten() //so just List<TraceData>
                if(incomingData.isNotEmpty() && traceStore) {
                    for(traceData in incomingData) { //this will be each TraceData  data = Pair<ROI, value>, timeAcquired
                        if(traceData.data.first!!.name == ""){
                            traceData.data.first!!.name = sinkName
                        }

                        //Add an entry for a new trace if the entry doesn't yet exist
                        if(traceData.data.first!!.name !in dataPointCounters.keys){
                            dataPointCounters[traceData.data.first!!.name] = 0
                        }

                        val newStoreObject = TraceDataStore(
                            timeAcquired = traceData.timeAcquired,
                            roi = traceData.data.first,
                            roiVal = traceData.data.second,
                            dataPointNumber = dataPointCounters[traceData.data.first!!.name]!!,
                            flowName = "", //is this a problem
                            roiNumber = 1 //is this a problem
                        )
                        this.traceData.add(newStoreObject)
                        dataPointCounters[traceData.data.first!!.name] = dataPointCounters[traceData.data.first!!.name]!!+1

                        val shouldStop = checkIfShouldStop(traceData.data.first!!.name)
                        if(shouldStop){
                            sendData()
                            GUIMain.loggerService.log(Level.INFO, "Trace data store actor no longer acquiring")
                            acquiring = false
                        }
                    }
                }

                sender().tell(Acknowledgement.INSTANCE, self())
            }
            .build()
    }

    private fun checkIfShouldStop(deviceLabel : String) : Boolean{
        for(dataPointNumberPair in GUIMain.experimentService.deviceDatapointNumbers){
            if(deviceLabel.contains(dataPointNumberPair.key)){
                if(dataPointCounters.all { x -> x.value >= dataPointNumberPair.value}){
                    GUIMain.loggerService.log(Level.INFO, "Stopping trace data store actor storing data")
                    return true
                }
            }
        }
        return false
//        return GUIMain.softwareTimerService.getTime() > GUIMain.experimentService.expConfig.experimentDurationMs/1000.0
    }

    private fun sendData(){
        GUIMain.loggerService.log(Level.INFO, "Trace data store actor sending data")
        GUIMain.actorService.fileWriterActor.tell(TellTraceData(traceData, isTraceFromROI), ActorRef.noSender())
        traceStore = false
    }

    private fun sendPoisonPill(){
        self.tell(PoisonPill.getInstance(), ActorRef.noSender())
    }
}