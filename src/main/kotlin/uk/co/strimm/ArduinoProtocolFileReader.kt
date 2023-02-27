
package uk.co.strimm


import java.io.BufferedReader
import java.io.FileReader

class ArduinoProtocolFileReader(var szProtocol : String) {
    //reads a SimpleProtocol csv
    var AOChannels = mutableListOf<Int>()
    var AIChannels = mutableListOf<Int>()
    var DOChannels = mutableListOf<Int>()  //port:line
    var DIChannels = mutableListOf<Int>()
    var AOData = DoubleArray(1)
    var DOData = IntArray(1)

    var numSamples = 0
    var sampleFreq = 0.0 //Hz
    var DOPort = 0
    var DIPort = 0
    fun init(){
        if (szProtocol.substring(szProtocol.length - 4 ) == ".csv"){
            println("Protocol format CSV")

            val br = BufferedReader(FileReader(szProtocol))
            var line: String
            line = br.readLine()
            var szLines = line.split(",".toRegex()).toTypedArray()
            //
            //
            AOChannels = mutableListOf<Int>()
            AIChannels = mutableListOf<Int>()
            DOChannels = mutableListOf<Int>()  //port:line
            DIChannels = mutableListOf<Int>()




            //
            //
            // <numSamples> <sampleFreq> <DOPort>  <DIPort>  <AO0> <AO1> <AO2> <DO0> <DO1> <DO2> <DI2> <AI0>
            var line1 : String
            line1 = br.readLine()
            var szLines1 = line1.split(",".toRegex()).toTypedArray()
            numSamples = szLines1[0].toInt()
            sampleFreq = szLines1[1].toDouble()
            DOPort = szLines1[2].toInt()
            DIPort = szLines1[3].toInt()
            //
            //
            var AOLoc = mutableListOf<Int>()
            var DOLoc = mutableListOf<Int>()

            for (f in 4..szLines1.size-1){
                if (szLines[f].substring(0,2) == "AO"){
                    AOChannels.add(szLines[f].substring(2).toInt())
                    AOLoc.add(f)
                }
                else if (szLines[f].substring(0,2) == "AI"){
                    AIChannels.add(szLines[f].substring(2).toInt())
                }
                else if (szLines[f].substring(0,2) == "DO"){
                    DOChannels.add(szLines[f].substring(2).toInt())
                    DOLoc.add(f)

                }
                else if (szLines[f].substring(0,2) == "DI"){
                    DIChannels.add(szLines[f].substring(2).toInt())
                }
                else{

                }
            }

            AOData = DoubleArray(numSamples * AOChannels.size)
            DOData = IntArray(numSamples * DOChannels.size)

            //process the contents of the 1st data row
            for (f in 0..AOChannels.size-1){
                AOData[f] = szLines1[AOLoc[f]].toDouble()
            }
            for (f in 0..DOChannels.size-1){
                DOData[f] = szLines1[DOLoc[f]].toInt()
            }

            for (f in 1..numSamples-1){
                line1 = br.readLine()
                var szLines1 = line1.split(",".toRegex()).toTypedArray()

                for (ff in 0..AOChannels.size-1){
                    AOData[ff + f*AOChannels.size] = szLines1[AOLoc[ff]].toDouble()
                }
                for (ff in 0..DOChannels.size-1){
                    DOData[ff + f*DOChannels.size] = szLines1[DOLoc[ff]].toInt()
                }
            }
            br.close()
        }
        else{
            println("Protocol file format not supported")
        }
    }
}