package uk.co.strimm.actors

import akka.actor.AbstractActor
import akka.actor.Kill
import akka.actor.Props
import uk.co.strimm.Acknowledgement
import uk.co.strimm.actors.messages.Message
import uk.co.strimm.actors.messages.complete.CompleteStreamingTraceROI
import uk.co.strimm.actors.messages.fail.FailStreamingTraceROI
import uk.co.strimm.actors.messages.start.AttachController
import uk.co.strimm.actors.messages.start.StartStreamingTraceROI
import uk.co.strimm.actors.messages.stop.DetatchController
import uk.co.strimm.gui.GUIMain
import uk.co.strimm.gui.TraceWindowPlugin
import uk.co.strimm.TraceData
import uk.co.strimm.actors.messages.stop.TerminateActor
import uk.co.strimm.actors.messages.tell.TellAnalogueDataStream
import uk.co.strimm.actors.messages.tell.TellDeviceSamplingRate
import uk.co.strimm.actors.messages.tell.TellDisplaySinkName
import uk.co.strimm.actors.messages.tell.TellSetNumDataPoints
import uk.co.strimm.services.AnalogueDataStream
import java.util.*
import java.util.logging.Level

class TraceActor(val plugin: TraceWindowPlugin) : AbstractActor(){
    companion object {
        fun props(plugin : TraceWindowPlugin): Props {
            return Props.create<TraceActor>(TraceActor::class.java) { TraceActor(plugin) }
        }

        //TODO hard limit or come from settings?
        const val dataPointDisplayAbsoluteLimit = 500
    }

    /**
     * The controllerAttach flag is used to attach or detach the trace window plugin controller.
     * This will be done when closing (deleting) a trace window or creating one
     */
    var controllerAttach = false
    var deviceSamplingRateHz = 0.0
    var doDownsample = false
    var downsampleInterval = 1
    var xAxisLengthSecs = 5.0
    var numReceivedDataPoints = 0
    var associatedAnalogueDataStream : AnalogueDataStream? = null
    var sinkName : String = ""

    override fun createReceive(): Receive {
        //println("TraceActor::createReceive")
        return receiveBuilder()
            .match<Message>(Message::class.java) { message ->
                GUIMain.loggerService.log(Level.INFO,"Trace actor receiving message")
            }
            .match<StartStreamingTraceROI>(StartStreamingTraceROI::class.java){ startStreamingTraceROIMessage ->
                GUIMain.loggerService.log(Level.INFO,"Trace actor starting trace ROI streaming")
                sender().tell(Acknowledgement.INSTANCE, self())
            }
            .match<CompleteStreamingTraceROI>(CompleteStreamingTraceROI::class.java){ stopStreamingTraceROIMessage ->
                GUIMain.loggerService.log(Level.INFO,"Trace actor complete trace ROI streaming")
                sender().tell(Acknowledgement.INSTANCE, self())
            }
            .match<FailStreamingTraceROI>(FailStreamingTraceROI::class.java){ it ->
                GUIMain.loggerService.log(Level.SEVERE, "Trace from ROI stream failed. Error message: ${it.ex.message}")
                GUIMain.loggerService.log(Level.SEVERE, it.ex.stackTrace)
                sender().tell(Acknowledgement.INSTANCE, self())
            }
            .match<AttachController>(AttachController::class.java){ attachControllerMessage ->
                GUIMain.loggerService.log(Level.INFO, "Attaching trace controller")
                controllerAttach = true
            }
            .match<TellDeviceSamplingRate>(TellDeviceSamplingRate::class.java){ devSampRateMsg ->
                deviceSamplingRateHz = devSampRateMsg.samplingRateHz
            }
            .match<TellDisplaySinkName>(TellDisplaySinkName::class.java){displaySinkNameMsg->
                sinkName = displaySinkNameMsg.sinkName  //should have been told this when it was created
            }
            .match<TellSetNumDataPoints>(TellSetNumDataPoints::class.java){ setNumDataPointsMsg ->
                val numIncommingPointsPerSec = deviceSamplingRateHz
                val dataPointDisplayAbsoluteLimitPerSec = dataPointDisplayAbsoluteLimit/xAxisLengthSecs
                if(numIncommingPointsPerSec > dataPointDisplayAbsoluteLimitPerSec){
                    downsampleInterval = Math.ceil(numIncommingPointsPerSec/dataPointDisplayAbsoluteLimitPerSec).toInt()
                    plugin.traceWindowController.setNumberOfPoints(dataPointDisplayAbsoluteLimitPerSec.toInt())
                    doDownsample = true
                }
                else{
                    plugin.traceWindowController.setNumberOfPoints(numIncommingPointsPerSec.toInt())
                }
            }
            .match<TellAnalogueDataStream>(TellAnalogueDataStream::class.java){
                associatedAnalogueDataStream = it.analogueDataStream
                it.analogueDataStream.associatedTraceActor = this.self()
            }
            .match<DetatchController>(DetatchController::class.java){ detatchMessage ->
                GUIMain.loggerService.log(Level.INFO, "Detatching trace controller")
                controllerAttach = false
            }
            .match<TerminateActor>(TerminateActor::class.java){
                GUIMain.loggerService.log(Level.INFO, "Trace actor ${self.path().name()} terminating")
                self.tell(Kill.getInstance(), self)
            }
            .match<Any>(Any::class.java){
                val incomingDataList = it as List<List<TraceData>>
                val incomingData = incomingDataList.flatten()
                if(incomingData.isNotEmpty()){
                    if(doDownsample){
                        val downSampledData = downsample(incomingDataList, downsampleInterval)
                        sendDataToController(downSampledData)
                    }
                    else{
                        sendDataToController(incomingDataList)
                    }
                }
                else{
                    //println("incoming data empty")
                }
                sender().tell(Acknowledgement.INSTANCE, self())
            }
            .matchAny{
                GUIMain.loggerService.log(Level.SEVERE, "Trace actor does not recognise incoming message")
            }.build()
    }

    private fun downsample(incomingDataList : List<List<TraceData>>, downsampleInterval : Int) : List<List<TraceData>>{
        val downsampledData = arrayListOf<ArrayList<TraceData>>()
        val allDataPoints = incomingDataList.flatten()
        val downsampledTraceData = arrayListOf<TraceData>()
        for(i in 0 until allDataPoints.size){
            if(numReceivedDataPoints % downsampleInterval == 0){
                downsampledTraceData.add(allDataPoints[i])
            }
            numReceivedDataPoints++
        }
        downsampledData.add(downsampledTraceData)
        return downsampledData.toList()
    }

    var dataPointCounter = 0
    var firstROIFrameStartTime = 0.0

    /**
     * This method sends the data to the trace window one data point at a timeAcquired. Due to the grouping of the data stream
     * this actor receives a list of lists. The inner list's size will be the number of ROIs that have been routed.
     * The trace windows indices are incremented here because of the list nesting.
     * @param incomingDataList The list of lists of ROI data.
     */
    fun sendDataToController(incomingDataList : List<List<TraceData>>){
        //println("TraceActor::sendDataToController")
        try {
            for (listOfTraceData in incomingDataList) {
                if(listOfTraceData.isNotEmpty()) {
                    for (traceData in listOfTraceData) {
                       if(dataPointCounter == 0){
                            firstROIFrameStartTime = traceData.timeAcquired.toDouble()
                            plugin.traceWindowController.updateChart(0.0, traceData, null)
                        }
                        else{
                            val time = (traceData.timeAcquired.toDouble())//-firstROIFrameStartTime)
                            plugin.traceWindowController.updateChart(time, traceData, null)
                        }
                    }
                    plugin.traceWindowController.incrementUpperIndices()
                    plugin.traceWindowController.incrementLowerIndices()
                    dataPointCounter++
                }
            }
        }
        catch(ex: Exception){
            GUIMain.loggerService.log(Level.SEVERE, "Error in trace actor receiving data. Error message: ${ex.message}")
            GUIMain.loggerService.log(Level.SEVERE,ex.stackTrace)
        }
    }
}
