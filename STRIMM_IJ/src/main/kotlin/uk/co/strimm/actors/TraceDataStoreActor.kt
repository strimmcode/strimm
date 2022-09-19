package uk.co.strimm.actors

import akka.actor.*
import net.imagej.overlay.Overlay
import uk.co.strimm.Acknowledgement
import uk.co.strimm.actors.messages.Message
import uk.co.strimm.actors.messages.complete.CompleteTraceDataStoring
import uk.co.strimm.actors.messages.fail.FailTraceDataStoring
import uk.co.strimm.actors.messages.start.StartTraceDataStoring
import uk.co.strimm.gui.GUIMain
import uk.co.strimm.TraceData
import uk.co.strimm.actors.messages.start.StartTraceStore
import java.util.logging.Level
import uk.co.strimm.TraceDataStore
import uk.co.strimm.actors.messages.ask.AskIsTraceROI
import uk.co.strimm.actors.messages.start.StartAcquiring
import uk.co.strimm.actors.messages.stop.AbortStream
import uk.co.strimm.actors.messages.tell.*
import uk.co.strimm.services.AnalogueDataStream
import java.util.*

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
                    println("incoming data")
                    for(traceData in incomingData) { //this will be each TraceData  data = Pair<ROI, value>, timeAcquired
                        traceData.data.first!!.name = sinkName
                        if(traceData.data.first!!.name !in dataPointCounters.keys){
                            dataPointCounters[traceData.data.first!!.name] = 0
                        }

                        val shouldStop = checkIfShouldStop(traceData.data.first!!.name)
                        //println("should stop trace = " + shouldStop)
                        if(!shouldStop) {
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
                        }
                        else{
                            //println("*******SEND DATA TRACE")
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
        return GUIMain.softwareTimerService.getTime() > GUIMain.experimentService.expConfig.experimentDurationMs/1000.0
//        for(dataPointNumberPair in GUIMain.experimentService.deviceDatapointNumbers){
//            if(deviceLabel.contains(dataPointNumberPair.key)){
//                if(dataPointCounters.all{ x -> x.value % 10000 == 0}){
//                    GUIMain.loggerService.log(Level.INFO, "Trace stored ${dataPointCounters.values.first()} points so far")
//                }
//
//                if(dataPointCounters.all { x -> x.value >= dataPointNumberPair.value}){
//                    return true
//                }
//            }
//        }
 //       return false
    }

    private fun sendData(){
        GUIMain.actorService.fileWriterActor.tell(TellTraceData(traceData, isTraceFromROI), ActorRef.noSender())
        traceStore = false
    }

    private fun sendPoisonPill(){
        self.tell(PoisonPill.getInstance(), ActorRef.noSender())
    }
}