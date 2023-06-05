package uk.co.strimm.flowMethods

import akka.actor.ActorRef
import uk.co.strimm.STRIMMBuffer
import uk.co.strimm.STRIMMPixelBuffer
import uk.co.strimm.STRIMMSignalBuffer
import uk.co.strimm.experiment.Flow
import uk.co.strimm.gui.GUIMain
import uk.co.strimm.services.UIstate
import java.util.logging.Level

class EventMarkerFlow : FlowMethod {
    override lateinit var properties: HashMap<String, String>
    override var actor: ActorRef? = null
    lateinit var flow: Flow
    var eventKeys = arrayListOf<String>()

    override fun init(flow: Flow) {
        this.flow = flow
        eventKeys = this.flow.eventKeys
    }

    override fun run(image: List<STRIMMBuffer>): STRIMMBuffer {
        if(GUIMain.strimmUIService.state == UIstate.ACQUISITION) {
            //Get an event key presses that match the event keys for this flow and order them
            val filteredKeyPresses =
                GUIMain.strimmUIService.pressedEventKeys.filter { x -> x.second.toLowerCase() in eventKeys.map { y -> y.toLowerCase() } }
            val orderedKeyPresses = filteredKeyPresses.sortedBy { x -> x.first }

            if(orderedKeyPresses.isNotEmpty()) {
                val press = orderedKeyPresses.first()
                GUIMain.loggerService.log(Level.INFO, "EventMarkerFlow ${flow.flowName} noting event key ${press.second} pressed")
                GUIMain.strimmUIService.eventMarkerLabelThread.updateEventMarkerLabel(press.second)
                //Remove the key press from the list so it isn't processed again next time this method is run
                GUIMain.strimmUIService.pressedEventKeys.remove(press)
                val newBuffer = makeNewBuffer(image.first(), true, press.second)
                return newBuffer
            }
        }

        val newBuffer = makeNewBuffer(image.first(), false, "")
        return newBuffer
    }

    /**
     * Will create a new STRIMMSignalBuffer (returned as a STRIMMBuffer) that will have the time and data of
     * the key being pressed. Event keys are currently only supported as single numbers 0-9. If no event key has been
     * pressed then still create a new STRIMMSignalBuffer but with data=0.0
     * @param oldBuffer The buffer with the incoming data
     * @param keyMarkerPressed Flag to say if a corresponding key press has been found
     * @param keyPressString The string of the key that has been pressed
     * @return A new STRIMMSignalBuffer containing the time of the oldBuffer but with new data based on a key being
     * pressed or not
     */
    private fun makeNewBuffer(oldBuffer : STRIMMBuffer, keyMarkerPressed : Boolean, keyPressString : String): STRIMMBuffer{
        val newBuffer: STRIMMBuffer

        var keyPressNumber = 0.0
        if(keyMarkerPressed) {
            try {
                keyPressNumber = keyPressString.toInt().toDouble()
            } catch (ex: Exception) {
                GUIMain.loggerService.log(Level.SEVERE, "Could not convert key string $keyPressString to a number")
            }
        }

        /**
         * Purpose of the when statement below is to convert the incoming data into a STRIMMSignalBuffer. This
         * STRIMMSignalBuffer will not contain the incomming data, but takes the incomming data's time. It adds new data
         * which corresponds to what event marker keys have been specified and pressed (or not pressed).
         *
         * Note - Event markers for data coming from a NIDAQ board (using NIDAQBuffer_to_SignalBufferFlow) are not
         * supported. This is because in episodic mode (the only mode currently supported) all data arrives at the
         * end of each episode, so placing event markers for this doesn't make much sense as you don't see the data
         * until the end
         */
        when(oldBuffer){
            is STRIMMPixelBuffer ->{
                val data = DoubleArray(eventKeys.size)
                val channelNames = mutableListOf<String>()

                eventKeys.forEachIndexed { index, d ->
                    if(eventKeys[index].toLowerCase() == keyPressString.toLowerCase()){
                        data[index] = keyPressNumber
                    }
                    else{
                        data[index] = 0.0
                    }

                    channelNames.add(index, d)
                }

                newBuffer = STRIMMSignalBuffer(
                    data = data,
                    times = DoubleArray(1){oldBuffer.timeAcquired},
                    numSamples = 1,
                    channelNames = channelNames,
                    dataID = oldBuffer.dataID,
                    status = oldBuffer.status
                )
            }
            is STRIMMSignalBuffer ->{
                val data = DoubleArray(eventKeys.size)
                val channelNames = mutableListOf<String>()

                eventKeys.forEachIndexed { index, d ->
                    if(eventKeys[index].toLowerCase() == keyPressString.toLowerCase()){
                        data[index] = keyPressNumber
                    }
                    else{
                        data[index] = 0.0
                    }

                    channelNames.add(index, d)
                }

                newBuffer = STRIMMSignalBuffer(
                    data = data,
                    times = DoubleArray(1){oldBuffer.times!![0]},
                    numSamples = 1,
                    channelNames = channelNames,
                    dataID = oldBuffer.dataID,
                    status = oldBuffer.status
                )
            }
            else -> {
                //Don't log warning message because it may print too many messages, but if here then something has
                //gone wrong
                newBuffer = STRIMMBuffer(0, 1)
            }
        }

        return newBuffer
    }

    override fun preStart() {
    }

    override fun postStop() {
    }
}