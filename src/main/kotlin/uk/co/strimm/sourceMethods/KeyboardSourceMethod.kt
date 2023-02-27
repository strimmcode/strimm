
package uk.co.strimm.sourceMethods

import akka.actor.ActorRef
import akka.japi.Pair
import com.opencsv.CSVReader
import uk.co.strimm.STRIMMBuffer
import uk.co.strimm.experiment.Source
import uk.co.strimm.gui.GUIMain
import java.io.FileReader
import java.util.*
import java.util.concurrent.CompletableFuture
import kotlin.collections.HashMap

//KeyboardSourceMethod
//
//Simple Keyboard Source, returns the state of an individual Virtual Key
//the key is in the cfg for this source
//result is sent through the akka-graph as a STRIMMBuffer
//
open class KeyboardSourceMethod() : SourceMethod {
    //
    // NB VK = 32 seems to mean restart to STRIMM....??????
    open lateinit var source: Source
    override lateinit var properties : HashMap<String, String>
    override var actor: ActorRef? = null
    open var dataID : Int = 0
    var VK : Int = 0
    //
    //
    override fun init(source: Source) {
        this.source = source
        loadCfg()
        dataID = 0
        //
        //
        VK = properties["VK"]!!.toInt()
    }
    override fun preStart() {
        //open all resources that the source might need
        //eg start the circular buffer, load a tiff stack etc
        println("preStart ************************")
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
        Thread.sleep(100) // should be run in timelapse - but just in case


        var bRet = GUIMain.protocolService.GetKeyState(VK)


        if (bRet){
            return STRIMMBuffer(dataID, 1)
        }
        else{
            return STRIMMBuffer(dataID, 0) // this should be filtered out
        }


    }
    override fun postStop() {
        //shut down and clean up all resources
        println("postStop *****************************")
    }
}