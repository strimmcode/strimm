package uk.co.strimm

import java.io.BufferedReader
import java.io.FileReader
import java.io.IOException


class ArduinoCompoundProtocol {
    @Throws(IOException::class)
    fun Init(COM: Int, csvProt: String, szFolder: String, bCompound: Boolean, bRepeat: Boolean): Int {
        //ShutdownProtocol();
        this.bCompound = bCompound
        this.bRepeat = bRepeat
        szProtocolFolder = szFolder
        if (bCompound) {
            //read the compound file
            val br = BufferedReader(FileReader(szFolder + csvProt))
            var line = br.readLine()
            while (line != null) {
                val szVals = line.split(" ".toRegex()).toTypedArray()
                vTiming.add(szVals[0].toInt())
                vNames.add(szVals[1])
                vRepeats.add(szVals[2].toInt())
                line = br.readLine()
            }
            br.close()
            //	// find the unique names
            val vUniqueNames = ArrayList<String>()
            for (f in vNames.indices) {
                var bUnique = true
                for (ff in vUniqueNames.indices) {
                    if (vNames[f] == vUniqueNames[ff]) {
                        bUnique = false
                        break
                    }
                }
                if (bUnique) vUniqueNames.add(vNames[f])
            }
            //	//
            // for each vUniqueName find a SimpleProtocol and store into vProtocols
            for (f in vUniqueNames.indices) {
                val pdef = ArduinoSimpleProtocol()
                try {
                    pdef.Init(szProtocolFolder + vUniqueNames[f])
                } catch (ex: Exception) {
                    println("Error loading SimpleProtocol")
                }
                vProtocols.add(pdef)
            }
            for (f in vNames.indices) {
                for (ff in vUniqueNames.indices) {
                    if (vNames[f] == vUniqueNames[ff]) {
                        vID.add(ff)
                        break
                    }
                }
            }
            //
            //so we have a list of ids vID and each id indexes in vProtocols, along with vTimings and vReps
            curProtocol = 0
            curRepeat = 0
        } else {
            //	load a simple protocol
            val prot = ArduinoSimpleProtocol()
            prot.Init(szProtocolFolder + csvProt)
            //!bCompound && bRepeat will use CONTSamps   ....note this not developed in NIDAQ yet
            if (bRepeat) {
                prot.SetRepeat(true)
            }
            vNames.add(csvProt)
            vProtocols.add(prot)
            vID.add(0)
            vRepeats.add(1)
            curProtocol = 0 //start at the beginning
            curRepeat = 0 // there is only 1 repeat
        }
        return 1 // has no meaning in this case  TO DO give 0 if parsing error
    }

    fun PrepareArduForNextRun(COM: Int): Int {
        var retVal = -1
        if (bCompound) {
            //cout << "curRepeat " << curRepeat << " curProtocol " << curProtocol << endl;

            //to simplify things the different timingMethods have been treated separately
            //
            if (timingMethod == 0 || timingMethod == 1) {
                //consecutive or triggered
                println(vNames[vID[curProtocol]] + "   " + curProtocol + "   " + curRepeat + "   " + vRepeats[curProtocol])
                vProtocols[vID[curProtocol]].PrepareArduForNextRun(COM)
            } else {
                //
            }
            retVal = 1
            curRepeat++
            if (curRepeat == vRepeats[curProtocol]) {
                curProtocol++
                curRepeat = 0
            }
            if (curProtocol == vID.size) {
                //this will repeat the entire compound protocol including the initial start trigger if there is one.
                if (bRepeat) {
                    curProtocol = 0
                } else retVal = -1
            }
        } else {
            //just run the only protocol in the vector of protocols
            //
            //this is where the function needs to change to do contSamps
            println(vNames[vID[curProtocol]] + "   " + curProtocol + "   " + curRepeat + "   " + vRepeats[curProtocol])
            if (!bRepeat) {
                vProtocols[vID[curProtocol]].PrepareArduForNextRun(COM)
                retVal = -1
            } else {
                if (bFirst) {
                    bFirst = false
                }
                vProtocols[vID[curProtocol]].PrepareArduForNextRun(COM)
                retVal = 1
                //ReleaseDAQ also occurs in ShutdownProtocol
            }
        }
        return retVal
    }

    fun ShutdownProtocol(): Int {
        vProtocols.clear()
        vID.clear()
        vRepeats.clear()
        vTiming.clear()
        return -1
    }

    fun GetNumSamples(): Int {
        return if (bCompound) {
            vProtocols[vID[curProtocol]].GetNumSamples()
        } else {
            vProtocols[0].GetNumSamples()
        }
    }

    fun GetNumberOfDataPoints(): Int {
        var num = 0
        if (bCompound) {
            for (f in vID.indices) {
                num += vRepeats[f] * vProtocols[vID[f]].GetNumSamples()
            }
        } else {
            num = vProtocols[0].GetNumSamples()
        }
        return num
    }

    fun GetCurrentDO(): IntArray? {
        return if (bCompound) {
            val sp = vProtocols[vID[curProtocol]]
            sp.getDataDO()
        } else {
            val sp = vProtocols[0]
            sp.getDataDO()
        }
    }

    fun GetChannels(type: Int): IntArray {
        return if (bCompound) {
            val sp = vProtocols[vID[curProtocol]]
            sp.GetChannels(type)
        } else {
            val sp = vProtocols[0]
            sp.GetChannels(type)
        }
    }

    fun GetNumChannels(type: Int): Int {
        return if (bCompound) {
            val sp = vProtocols[vID[curProtocol]]
            sp.GetNumChannels(type)
        } else {
            val sp = vProtocols[0]
            sp.GetNumChannels(type)
        }
    }

    fun GetCurrentRunSampleTime_us(): Double {
        //time for each sample in s
        return if (bCompound) {
            1.0 / vProtocols[vID[curProtocol]].GetSampleFreq() * 1000000.0
        } else {
            1.0 / vProtocols[0].GetSampleFreq() * 1000000.0
        }
    }

    fun GetNumberOfStages(): Int {
        return if (bCompound) {
            var tot = 0
            for (f in vID.indices) {
                tot += vRepeats[f]
            }
            tot
        } else 1 //because it is single
    }

    fun GetChannelFromIndex(type: Int, ix: Int): Int {
        return vProtocols[vID[curProtocol]].GetChannelFromIndex(type, ix)
    }

    fun GetPort(bIn: Boolean): Int {
        return vProtocols[vID[curProtocol]].GetPort(bIn)
    }

    fun GetDeviceID(): Int {
        return deviceID
    }

    private val deviceID = 0
    private val minV = 0.0
    private val maxV = 0.0
    private var bCompound: Boolean
    private var bRepeat: Boolean
    private var szProtocolFolder = ""
    private var bFirst = true
    private var curProtocol = 0
    private var curRepeat: Int
    private val bStartTrigger: Boolean
    private val bRisingEdge = false
    private val timeoutSec = 0.0
    private val pFIx: Int
    private val timingMethod: Int
    private val vID = ArrayList<Int>()
    private val vRepeats = ArrayList<Int>()
    private val vTiming = ArrayList<Int>()
    private val vNames = ArrayList<String>()

    //
    private val vProtocols = ArrayList<ArduinoSimpleProtocol>()
    private val startTime = 0.0

    init {
        szProtocolFolder = ""
        bRepeat = false
        bStartTrigger = false
        timingMethod = 0
        pFIx = 0
        bCompound = false
        curRepeat = 0
    }
}