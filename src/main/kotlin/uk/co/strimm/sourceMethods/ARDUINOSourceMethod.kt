
package uk.co.strimm.sourceMethods

import uk.co.strimm.ArduinoCompoundProtocol
import uk.co.strimm.STRIMMBuffer
import uk.co.strimm.STRIMMSignalBuffer1
import uk.co.strimm.experiment.Source
import uk.co.strimm.gui.GUIMain
import kotlin.math.pow


class ARDUINOSourceMethod () : SourceBaseMethod() {
    //the main source of bugs with ArduinoSource is getting the COM, baudRate wrong (it has to match the c++ code on the
    //arduino. Also the arduino has very small memory and can only store 10-20 instructions.

    //the ArduinoSource allows the AI,DO,DI to be configured from file. The csv style file format is the same as
    //the NIDAQ code. It is just that the AO is ignored as it is PWM.

    //the csv file loaded is a Ccompound protocol - which contains a list of simple protocols along with repetitions of each.

    //TODO still issues about whether to include szFolder or to make everything global

    val protocol = ArduinoCompoundProtocol()
    var COM = 0
    var baudRate = 0


    override fun postStop() {
        GUIMain.protocolService.ARDUINO_Shutdown(COM)
    }
    override fun preStart() {
    }
    override fun init(source : Source){

        this.source = source
        //load the config file - inside (source.sourceName).cfg
        loadCfg()
        var bRepeat = properties["bRepeat"]!!.toBoolean()
        var bCompound = properties["bCompound"]!!.toBoolean()
        var szFolder = properties["szFolder"]!!
        var szProtCSV = properties["szProtCSV"]!!
        COM = properties["COM"]!!.toInt()
        baudRate = properties["baudRate"]!!.toInt() //this is also set inside the Arduino code

        protocol.Init(COM, szProtCSV, szFolder, bCompound, bRepeat)
        GUIMain.protocolService.ARDUINO_Init(COM, baudRate)
    }
    override fun run() : STRIMMBuffer? {

        val stepsMicros = protocol.GetCurrentRunSampleTime_us()
        //PrepareArduForNextRun will return -1 when protocol has finished
        if (protocol.PrepareArduForNextRun(COM) > 0){
            // for testing Thread.sleep(50)
            val dataDO = protocol.GetCurrentDO()
//change to explicit getNumAOChannels//////
            val numDOChannels = protocol.GetNumChannels(2)
            val DOChannels = protocol.GetChannels(2)
            val numDIChannels = protocol.GetNumChannels(3)
            val DIChannels = protocol.GetChannels(3)
            val numAIChannels = protocol.GetNumChannels(1)
            val AIChannels = protocol.GetChannels(1)

            val numSamples = protocol.GetNumSamples()

            //the DO is stored as the bits in a byte convert into the correct format
            var seqArray = ByteArray(numSamples)
            for (f in 0..numSamples-1){
                var value = 0
                for (ff in 0..numDOChannels-1){
                    value = value + dataDO!![numDOChannels * f + ff]*((2.0).pow(ff)).toInt()
                }
                seqArray[f] = value.toByte()
            }

            //do the measurement on the Arduino
            val chunk = GUIMain.protocolService.ARDUINO_Run(COM, seqArray,stepsMicros.toLong())
            //the timing of the DI results has to be inferred and is not transferred over by the arduino
            val times = chunk.first!! as Array<IntArray> //these times are the times when the AI results were obtained
            val retDI = chunk.second.first as IntArray
            val retAI = chunk.second.second as Array<IntArray>

            //Use the same channel names as for the NIDAQ
            val dataOut = arrayListOf<Double>()
            val channelOut = arrayListOf<String>()

            //channel names
            var channel_cnt = 0
            channelOut.add("DigitalTime")
            channel_cnt++
            for (f in 0..numDOChannels-1){
                channelOut.add("DO" + DOChannels[f].toString())
                channel_cnt++
            }
            for (f in 0..numDIChannels-1){
                channelOut.add("DI" + DIChannels[f].toString())
                channel_cnt++
            }
            for (f in 0..numAIChannels-1){
                channelOut.add("AI" + AIChannels[f].toString())
                channel_cnt++
            }
            for (f in 0..numAIChannels-1){
                channelOut.add("AnalogTime" + AIChannels[f].toString())
                channel_cnt++
            }


            //channel values
            var ditTime = GUIMain.softwareTimerService.getTime()
            var DOcnt = 0
            for (f in 0..numSamples-1){
                //digital times care calculated
                dataOut.add(ditTime)
                ditTime += stepsMicros/1000.0   //ms
                //DO
                for (ff in 0..numDOChannels-1) {
                    println(dataDO!![f].toString() + " " + (dataDO[f] shr ff).toString() + " " + ((dataDO[f] shr ff) and 1).toString())
                    dataOut.add(dataDO[DOcnt].toDouble())
                    DOcnt++
                }
                //DI
                var v = retDI[f]
                for (ff in 0..numDIChannels-1){
                    dataOut.add(((retDI[f] shr ff) and 1).toDouble())
                }
                //AI
                for (ff in 0..numAIChannels-1){
                    dataOut.add(retAI[f][ff].toDouble())
                }
                //AITimes gives the time when each of the AI samples was taken
                for (ff in 0..numAIChannels-1){
                    dataOut.add(times[f][ff].toDouble())
                }
            }



            dataID++
            // return STRIMMBuffer(dataID,1)

            val out =  STRIMMSignalBuffer1(dataOut.toTypedArray(), 1+numDOChannels + numDIChannels + 2 * numAIChannels, channelOut, dataID, 1)

            return out
            //package into a 2D buffer of the form s00, s01, s02, ... , s10, s11, s12, ... etc  with each column being a channel
        }
        else {
            //protocol finished - just sleep to not overflood the akka graph and send a null buffer
            Thread.sleep(500)
            return STRIMMBuffer(0,0)
        }


    }
}