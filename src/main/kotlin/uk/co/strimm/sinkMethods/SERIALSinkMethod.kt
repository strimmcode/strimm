package uk.co.strimm.sinkMethods

import akka.actor.ActorRef
import com.fazecast.jSerialComm.SerialPort
import uk.co.strimm.ArduinoCommunicator
import uk.co.strimm.STRIMMBuffer
import uk.co.strimm.experiment.Sink
import java.io.InputStream
import java.io.OutputStream


class SERIALSinkMethod () : SinkBaseMethod(){
    var COM = 9
    var baudRate = 115200
    var byteSize = 8
    var parity = 0
    var stopBits = 1
    var timeOut = 0.5
    var xonxoff = false
    var rtscts : Long = 0
    var dsrdtr = false
    var writeTimeout = 0.0

    val myComPort: SerialPort? = null
    val inStream: InputStream? = null
    val outStream: OutputStream? = null
    var port : SerialPort? = null

    var dataID = 0

    override lateinit var sink : Sink
    override var bUseActor = false
    override lateinit var properties : HashMap<String, String>
    override fun useActor(): Boolean {
        return bUseActor
    }
    //
    //
    override fun init(sink : Sink) {
        this.sink = sink
        loadCfg()
        //the COM and baudRate has to be matched with the c++ style code running on the Arduino
        //CAUSE OF A VERY COMMON BUG IS THE WRONG BAUDRATE
        COM = properties["COM"]!!.toInt()
        baudRate = properties["baudRate"]!!.toInt()
        byteSize = properties["byteSize"]!!.toInt()
        parity = properties["parity"]!!.toInt()
        stopBits = properties["stopBits"]!!.toInt()
        timeOut = properties["timeOut"]!!.toDouble()
        xonxoff = properties["xonxoff"]!!.toBoolean()
        rtscts = properties["rtscts"]!!.toLong()
        dsrdtr = properties["dsrdtr"]!!.toBoolean()
        writeTimeout = properties["writeTimeout"]!!.toDouble()

        //to do - get Serial port
        port = SerialPort.getCommPort("COM" + COM.toString())
        port!!.setBaudRate(baudRate)
        port!!.setNumDataBits(byteSize) //guess TODO
        port!!.setParity(parity)
        port!!.setNumStopBits(stopBits)
        port!!.setComPortTimeouts(0,0,0) //check this TODO




    }
    override fun run(data : List<STRIMMBuffer>){
        //Received a List<STRIMMArduinoControlBuffer>
        //the [0] entry is the one which is used here
        val dat = data[0]


    }
    override fun getActorRef() : ActorRef? {
        return null
    }

    override fun postStop() {

    }
    override fun preStart() {
    }
}