
package uk.co.strimm.sourceMethods

import akka.actor.ActorRef
import com.opencsv.CSVReader
import uk.co.strimm.STRIMMBuffer
import uk.co.strimm.experiment.Source
import java.io.FileReader
import java.io.InputStream
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.*

//TODO move to TCPSource and TCPSourceRaw
//a general implementation of a source - not necessarily an image source
open class TCPMethod() : SourceMethod {
    open lateinit var source: Source
    override lateinit var properties : HashMap<String, String>
    override var actor: ActorRef? = null
    open var dataID : Int = 0

    var port = 5001
    var serverSocket : ServerSocket? = null
    var inputStream : InputStream? = null



    override fun init(source: Source) {
        this.source = source
        loadCfg()
        dataID = 0
    }
    override fun preStart() {
        //open all resources that the source might need
        //eg start the circular buffer, load a tiff stack etc
        println("preStart ************************")
        serverSocket = ServerSocket(port)

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

        port = properties["port"]!!.toInt()
    }
    override fun run() : STRIMMBuffer?{
        //simply sends an empty buffer
        var sock : Socket = serverSocket!!.accept()
        var inBuffer = sock.getInputStream()
        val outBuffer = PrintWriter(sock.getOutputStream(), true)

        var data = inBuffer!!.read()
        outBuffer.println(data)

        sock.close()
        //var data = 2

        println("got data")
        dataID++

        return STRIMMBuffer( dataID, 1)
    }
    override fun postStop() {
        //shut down and clean up all resources
        println("postStop *****************************")
        serverSocket!!.close()
    }
}