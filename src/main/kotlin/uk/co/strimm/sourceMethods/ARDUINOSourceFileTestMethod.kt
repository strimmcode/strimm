

package uk.co.strimm.sourceMethods

import akka.actor.ActorRef
import com.opencsv.CSVReader
import uk.co.strimm.STRIMMArduinoControlBuffer
import uk.co.strimm.STRIMMBuffer
import uk.co.strimm.experiment.Source
import java.io.FileReader

class ARDUINOSourceFileTestMethod() : SourceBaseMethod() {
    var szFolder = ""
    var szProtCSV = ""
    var intervalMS = 100L
    var bContinuous = true
    var bFile = false


    override lateinit var source: Source
    override lateinit var properties : HashMap<String, String>
    override var actor: ActorRef? = null
    override var dataID : Int = 0
    override fun init(source: Source) {
        this.source = source
        loadCfg()
        dataID = 0
        szFolder = properties["szFolder"]!!
        szProtCSV = properties["szProtCSV"]!!
        intervalMS = properties["intervalMS"]!!.toLong()
        bContinuous = properties["bContinuous"]!!.toBoolean()
        bFile = properties["bFile"]!!.toBoolean()
    }
    override fun preStart() {
        //open all resources that the source might need
        //eg start the circular buffer, load a tiff stack etc
    }

    override fun run() : STRIMMBuffer?{
        //simply sends an empty buffer
        dataID++
        println("\n\n\n\nSource method *************")
        println(dataID.toString())
        //check time
        var szMode = "simpleProtocolMode"
        if (bContinuous) szMode = "continuousMode"

        var dataDO = IntArray(20){i->i%3}
        var DOChannels = IntArray(2)
        DOChannels[0] = 9
        DOChannels[1] = 10
        var timeMicros = (1/20.0 * 1000000.0).toLong()
        val ret = STRIMMArduinoControlBuffer(szMode, bFile, szFolder, szProtCSV, dataDO, DOChannels, timeMicros, dataID, 1)

        Thread.sleep(intervalMS)
        return ret
    }
    override fun postStop() {
        //shut down and clean up all resources
    }
}