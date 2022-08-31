package uk.co.strimm.sourceMethods

import akka.actor.ActorRef
import akka.japi.Pair
import com.opencsv.CSVReader
import uk.co.strimm.STRIMMBuffer
import uk.co.strimm.experiment.Source
import java.io.FileReader
import java.util.*
import java.util.concurrent.CompletableFuture
import kotlin.collections.HashMap

//a general implementation of a source - not necessarily an image source
open class SourceBaseMethod() : SourceMethod {
    lateinit var source: Source
    override lateinit var properties : HashMap<String, String>
    override var actor: ActorRef? = null
    var dataID : Int = 0
    override fun init(source: Source) {
        this.source = source
        loadCfg()
        dataID = 0
    }
    override fun preStart() {
        //open all resources that the source might need
        //eg start the circular buffer, load a tiff stack etc
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
        dataID++
        println("\n\n\n\nSource method *************")
        println(dataID.toString() + " " + 1)
        return STRIMMBuffer(dataID, 1)
    }
    override fun postStop() {
        //shut down and clean up all resources
    }
}