

package uk.co.strimm

import uk.co.strimm.gui.GUIMain
import java.io.IOException

class ArduinoSimpleProtocol() {
    fun SetRepeat(bRep: Boolean) {
        bRepeat = bRep
    }

    @Throws(IOException::class)
    fun Init(sz: String): Boolean {
        //reads a SimpleProtocol and loads up numDOChannels, DOChannels and dataDO
        //numAIChannels, AIChannels, numDIChannels, DIChannels
        //does not use AO as PWM
        var reader = ArduinoProtocolFileReader(sz)
        reader.init()
        numSamples = reader.numSamples
        sampleFreq = reader.sampleFreq
        numAOChannels = reader.AOChannels.size
        numDOChannels = reader.DOChannels.size
        numAOChannels = reader.DIChannels.size
        numDIChannels = reader.DIChannels.size
        AOChannels = reader.AOChannels.toIntArray()
        AIChannels = reader.AIChannels.toIntArray()
        DOChannels = reader.DOChannels.toIntArray()
        DIChannels = reader.DIChannels.toIntArray()
        dataAO = reader.AOData
        dataDO = reader.DOData

        return true
    }

    fun PrepareArduForNextRun(COM: Int): Boolean {
        //config ArduinoManagerActor
        val DOPins = ArrayList<Int>()
        val DIPins = ArrayList<Int>()
        val AIPins = ArrayList<Int>()
        for (f in 0..5) {
            DOPins.add(ArduinoCommunicator.PIN_NOT_USED)
            DIPins.add(ArduinoCommunicator.PIN_NOT_USED)
            AIPins.add(ArduinoCommunicator.PIN_NOT_USED)
        }
        for (f in 0 until numAIChannels) {
            AIPins[f] = AIChannels[f]
        }
        for (f in 0 until numDIChannels) {
            DIPins[f] = DIChannels[f]
        }
        for (f in 0 until numDOChannels) {
            DOPins[f] = DOChannels[f]
        }

        GUIMain.protocolService.ARDUINO_Set_Analog_Input_Pins(COM, AIPins)
        GUIMain.protocolService.ARDUINO_Set_Digital_Input_Pins(COM, DIPins)
        GUIMain.protocolService.ARDUINO_Set_Digital_Output_Pins(COM, DOPins)


        return true
    }

    fun GetNumSamples(): Int {
        return numSamples
    }

    fun GetSampleFreq(): Double {
        return sampleFreq
    }
    fun GetChannels(type: Int): IntArray {
        return if (type == 0) {
            //ao
            AOChannels
        } else if (type == 1) {
            //ai
            AIChannels
        } else if (type == 2) {
            //do
            DOChannels
        } else {
            //di
            DIChannels
        }
    }
    fun GetNumChannels(type: Int): Int {
        return if (type == 0) {
            //ao
            numAOChannels
        } else if (type == 1) {
            //ai
            numAIChannels
        } else if (type == 2) {
            //do
            numDOChannels
        } else {
            //di
            numDIChannels
        }
    }

    fun GetCurrentRunSampleTime(): Double {
        return 0.0
    }

    fun GetIndexFromChannel(type: Int, ch: Int): Int {
        return if (type == 0) {
            for (f in 0 until numAOChannels) {
                if (ch == AOChannels[f]) {
                    return f
                }
            }
            -1
        } else if (type == 1) {
            for (f in 0 until numAIChannels) {
                if (ch == AIChannels[f]) {
                    return f
                }
            }
            -1
        } else if (type == 2) {
            for (f in 0 until numDOChannels) {
                if (ch == DOChannels[f]) {
                    return f
                }
            }
            -1
        } else {
            for (f in 0 until numDIChannels) {
                if (ch == DIChannels[f]) {
                    return f
                }
            }
            -1
        }
    }

    fun GetChannelFromIndex(type: Int, ix: Int): Int {
        return if (type == 0) {
            AOChannels[ix]
        } else if (type == 1) {
            AIChannels[ix]
        } else if (type == 2) {
            DOChannels[ix]
        } else {
            DIChannels[ix]
        }
    }

    fun GetPort(bIn: Boolean): Int {
        return if (bIn) {
            DIPort
        } else {
            DOPort
        }
    }

    fun getDataDO() : IntArray?{
        return dataDO
    }
    fun getDataAO() : DoubleArray?{
        return dataAO
    }

    //
    private var numSamples = 0
    private var sampleFreq = 1000.0
    private var numAIChannels = 0
    private var numAOChannels = 0
    private var numDIChannels = 0
    private var numDOChannels = 0
    private var AIChannels: IntArray = IntArray(6)
    private var AOChannels: IntArray= IntArray(6)
    private var DIChannels: IntArray= IntArray(6)
    private var DOChannels: IntArray= IntArray(6)
    var DIPort = 0
    var DOPort = 0
    private var dataAO: DoubleArray? = null
    private var dataDO: IntArray? = null

    var bError = false
    var bRepeat = false



}