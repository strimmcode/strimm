

package uk.co.strimm.sourceMethods

import akka.actor.ActorRef
import com.opencsv.CSVReader
import uk.co.strimm.STRIMMBuffer
import uk.co.strimm.STRIMMNIDAQBuffer
import uk.co.strimm.experiment.Source
import java.io.FileReader

class NIDAQDataGeneratorSourceMethod() : SourceMethod {
    open lateinit var source: Source
    override lateinit var properties: HashMap<String, String>
    override var actor: ActorRef? = null
    open var dataID: Int = 0

    var pTimes : DoubleArray? = null
    var dataAO : DoubleArray? = null
    var dataAI : DoubleArray? = null
    var dataDO : IntArray? = null
    var dataDI : IntArray? = null
    var numSamples = 10
    var sampleFreq = 0.0
    var numAOChannels = 0
    var numAIChannels = 0
    var numDOChannels = 0
    var numDIChannels = 0
    var AOChannels : IntArray? = null
    var AIChannels : IntArray? = null
    var DOChannels : IntArray? = null
    var DIChannels : IntArray? = null
    var DIPort = 0
    var DOPort = 0



    override fun init(source: Source) {
        this.source = source
        loadCfg()

        numSamples = 10
        sampleFreq = 20.0

        AOChannels = IntArray(1)
        for (f in 0..AOChannels!!.size-1){
            AOChannels!![f] = f
        }
        numAOChannels = AOChannels!!.size

        DOPort = 0
        DOChannels = IntArray(1)
        for (f in 0..DOChannels!!.size-1){
            DOChannels!![f] = f
        }
        numDOChannels = DOChannels!!.size


        dataAO = DoubleArray(numSamples * numAOChannels)
        for (f in 0..numSamples-1){
            dataAO!![f] = 0.5*f
        }
        dataDO = IntArray(numSamples * numDOChannels)
        for (f in 0..numSamples-1){
            dataDO!![f] = f%2
        }


    }

    override fun preStart() {

    }

    fun loadCfg() {
        if (source.sourceCfg != "") {
            properties = hashMapOf<String, String>()
            var r: List<Array<String>>? = null
            try {
                val reader = CSVReader(FileReader(source.sourceCfg))
                r = reader.readAll()
                for (props in r!!) {
                    properties[props[0]] = props[1]
                }

            } catch (ex: Exception) {
                println(ex.message)
            }

        }
    }

    override fun run(): STRIMMBuffer? {

            var status = 1

            dataID++
            return STRIMMNIDAQBuffer(
                numSamples,
                sampleFreq,
                null,
                dataAO, AOChannels,
                null, null,
                dataDO, DOChannels, DOPort,
                null, null, 0,
                dataID, status)
        }

    override fun postStop() {
    }


}

