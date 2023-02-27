

package uk.co.strimm.sinkMethods

import akka.actor.ActorRef
import uk.co.strimm.*
import uk.co.strimm.experiment.Sink
import uk.co.strimm.gui.GUIMain
import kotlin.math.pow

//ArduinoSink - can send out custom DO patterns on (8,9,10,11,12,13)
//the sink responds when it receives a STRIMMArduinoControlBuffer
//// arduinoMode = "continuousMode" (once started repeatedly plays the protocol), "simpleProtocolMode" (just plays the protocol once)
//// bFromFile (will load the protocol from a file)
//// szFolder, simpleProtocol  (constructs the location of the SimpleProtocol file)
//
/// dataOut (IntArray, which is an array of Bytes), dataOutChannels (DOChannels 8,9,10,11,12,13), timeMicros (for each sample)


class ARDUINOSinkMethod () : SinkBaseMethod(){
    val protocol = ArduinoCompoundProtocol()
    var COM = 0
    var baudRate = 0
    var dataID = 0
    var bContinuous = false //flag to say if ArduinoManager is currently continuously running or not, whether the sink responds to continuous or episodic depends on thr STRIMMBuffer

    override lateinit var sink : Sink
    override var bUseActor = false
    override lateinit var properties : HashMap<String, String>
    override fun useActor(): Boolean {
        return bUseActor
    }
    //
    //
    override fun init(sink : Sink) {
        this.sink = sink
        loadCfg()
        //the COM and baudRate has to be matched with the c++ style code running on the Arduino
        //CAUSE OF A VERY COMMON BUG IS THE WRONG BAUDRATE
        COM = properties["COM"]!!.toInt()
        baudRate = properties["baudRate"]!!.toInt()
        //create and initialise an Arduino with COM and baudRate
        GUIMain.protocolService.ARDUINO_Init(COM, baudRate)
    }
    override fun run(data : List<STRIMMBuffer>){
        //Received a List<STRIMMArduinoControlBuffer>
        //the [0] entry is the one which is used here
        val dat = data[0]
        if (dat is STRIMMArduinoControlBuffer){
            if (bContinuous == true){
                //any previously continuously running protocol will be stopped prior to running another one
                GUIMain.protocolService.ARDUINO_Stop_Continuous(COM)
                bContinuous = false
            }

            if (dat.arduinoMode == "continuousMode"){
                bContinuous = true
                //
                var dataDO : IntArray? = IntArray(0)
                var numDOChannels = 0
                var DOChannels = IntArray(0)
                var timeMicros = dat.timeMicros

                if (dat.bFromFile){
                    //load the DO data from file
                    //this uses the same file format at the NIDAQ code
                    //load a SimpleProtocol
                    protocol.Init(COM, dat.simpleProtocol, dat.szFolder, false,false) //change last few entries
                    dataDO = protocol.GetCurrentDO() //get the DO data from the protocol
                    numDOChannels = protocol.GetNumChannels(2)
                    DOChannels = protocol.GetChannels(2)
                    timeMicros = protocol.GetCurrentRunSampleTime_us().toLong()

                }
                else{
                    //use the data carried by the STRIMMArduinoControlBuffer
                    dataDO = dat.dataOut
                    DOChannels = dat.dataOutChannels
                    numDOChannels = DOChannels.size
                }
                //use the Pin number in DOChannels or else use the default PIN_NOT_USED
                val DOPins = ArrayList<Int>(6)
                for (f in 0..5) {
                    DOPins.add(ArduinoCommunicator.PIN_NOT_USED)
                }
                for (f in 0 until numDOChannels) {
                    DOPins[f] = DOChannels[f]
                }
                GUIMain.protocolService.ARDUINO_Set_Digital_Output_Pins(COM, DOPins)

                var numSamples = dataDO!!.size / numDOChannels


                var seqArray = ByteArray(numSamples)
                for (f in 0..numSamples-1){
                    var value = 0
                    for (ff in 0..numDOChannels-1){
                        value = value + dataDO[numDOChannels * f + ff]*((2.0).pow(ff)).toInt()
                    }
                    seqArray[f] = value.toByte()
                }
                //starts the sequence which repeats in a continous manner
                GUIMain.protocolService.ARDUINO_Run_Continuous(COM, seqArray, dat.timeMicros)

            }
            else if (dat.arduinoMode == "simpleProtocolMode"){
                //
                bContinuous = false
                //
                var dataDO :  IntArray? = IntArray(0)
                var numDOChannels = 0
                var DOChannels = IntArray(0)
                var timeMicros = dat.timeMicros

                //load up the DO data from a file
                if (dat.bFromFile){
                    protocol.Init(COM, dat.simpleProtocol, dat.szFolder, false,false) //change last few entries
                    dataDO = protocol.GetCurrentDO()
                    numDOChannels = protocol.GetNumChannels(2)
                    DOChannels = protocol.GetChannels(2)
                    timeMicros = protocol.GetCurrentRunSampleTime_us().toLong()

                }
                else{
                    //get the data from a STRIMMArduinoControlBuffer
                    dataDO = dat.dataOut
                    DOChannels = dat.dataOutChannels
                    numDOChannels = DOChannels.size
                }
                val DOPins = ArrayList<Int>(6)
                for (f in 0..5) {
                    DOPins.add(ArduinoCommunicator.PIN_NOT_USED)
                }
                for (f in 0 until numDOChannels) {
                    DOPins[f] = DOChannels[f]
                }
                GUIMain.protocolService.ARDUINO_Set_Digital_Output_Pins(COM, DOPins)

                var numSamples = dataDO!!.size / numDOChannels

                var seqArray = ByteArray(numSamples)
                for (f in 0..numSamples-1){
                    var value = 0
                    for (ff in 0..numDOChannels-1){
                        value = value + dataDO[numDOChannels * f + ff]*((2.0).pow(ff)).toInt()
                    }
                    seqArray[f] = value.toByte()
                }
                //play the protocol/data on the arduino
                GUIMain.protocolService.ARDUINO_Run(COM, seqArray, timeMicros)

            }
        }

    }
    override fun getActorRef() : ActorRef? {
        return null
    }

    override fun postStop() {

    }
    override fun preStart() {
    }
}