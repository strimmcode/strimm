package uk.co.strimm.sourceMethods

import akka.actor.ActorRef
import com.opencsv.CSVReader
import uk.co.strimm.STRIMMBuffer
import uk.co.strimm.experiment.Source
import java.io.FileReader
import com.fazecast.jSerialComm.SerialPort
import uk.co.strimm.STRIMMSignalBuffer

class ArduinoSourceMethod() : SourceMethod {
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
        dataID++



        return null
    }
    override fun postStop() {
    }

}