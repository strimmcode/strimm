package uk.co.strimm.sourceMethods

import akka.actor.ActorRef
import com.opencsv.CSVReader
import uk.co.strimm.STRIMMBuffer
import uk.co.strimm.STRIMMSignalBuffer
import uk.co.strimm.experiment.Source
import java.io.FileReader
import java.lang.Math.cos
import java.lang.Math.sin


open class SignalSourceMethod() : SourceMethod {
    lateinit var source: Source
    override lateinit var properties : HashMap<String, String>
    override var actor: ActorRef? = null
    var dataID : Int = -1
    var numSamples : Int = 10
    var numChannels : Int = 2

    var cnt : Double = 0.0
    override fun init(source: Source) {
        this.source = source
        loadCfg()
        dataID = 0
    }
    override fun preStart() {
        //open all resources that the source might need
        //eg start the circular buffer, load a tiff stack etc
        println("preStart")
    }
    fun loadCfg() {
        if (source.sourceCfg != ""){
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
    override fun run() : STRIMMBuffer?{
        //simply sends an empty buffer
        val data = DoubleArray(numSamples * numChannels)
        val times = DoubleArray(numSamples)
        for (f in 0..numSamples-1){
            data[2*f] = sin(cnt)
            data[2*f+1] = cos(cnt)
            times[f] = cnt
            cnt += 0.1
        }
        dataID++
        return STRIMMSignalBuffer(data, times, numSamples, arrayListOf("SIN", "COS").toList(), dataID, 1)
    }
    override fun postStop() {
        //shut down and clean up all resources
        println("postStop")
    }
}