package uk.co.strimm.sinkMethods

import akka.actor.ActorRef
import com.opencsv.CSVReader
import uk.co.strimm.STRIMMBuffer
import uk.co.strimm.STRIMMSequenceCameraDataBuffer
import uk.co.strimm.STRIMMSignalBuffer
import uk.co.strimm.STRIMMSignalBuffer1
import uk.co.strimm.actors.messages.complete.CompleteStreaming
import uk.co.strimm.actors.messages.start.StartStreaming
import uk.co.strimm.experiment.Sink
import java.io.FileReader



class SinkConsoleMethod() : SinkMethod {
    lateinit var sink : Sink
    var bUseActor = false
    override lateinit var properties : HashMap<String, String>
    override fun useActor(): Boolean {
        return bUseActor
    }
    override fun init(sink : Sink) {
        this.sink = sink
        if (sink.sinkCfg != ""){
            //todo this is in the wrong place it should be in Sink
            properties = hashMapOf<String, String>()
            var r: List<Array<String>>? = null
            try {
                CSVReader(FileReader(sink.sinkCfg)).use { reader ->
                    r = reader.readAll()
                    for (props in r!!) {
                        //specific properties are read from Cfg
                        // "intervalMs" : "10.0"  etc
                        properties[props[0]] = props[1]
                    }
                }
            } catch (ex: Exception) {
                println(ex.message)
            }
        }
    }
    override fun run(data : List<STRIMMBuffer>){
        if (data[0] is STRIMMSequenceCameraDataBuffer){
            println("************Image Burst*******")
            println(data[0].className)
            var dat = data[0] as STRIMMSequenceCameraDataBuffer
            for (f in 0.. dat.data.size - 1){
                println(f.toString())
            }

        }
        else {
            println("*****console********************")
            for (f in 0..data.size - 1) {
                val trace = data[f] as STRIMMSignalBuffer1
                val data = trace.data as Array<Double>
                val numChannels = trace.numChannels
                println("numChannels " + numChannels)
                val numSamples = data.size / numChannels
                println("numSamples " + numSamples)


            }
        }
    }
    override fun getActorRef() : ActorRef? {
        return null
    }
    override fun start() : StartStreaming {
        return StartStreaming()
    }
    override fun complete() : CompleteStreaming {
        return CompleteStreaming()
    }
    override fun fail(ex: Throwable) {
        println("FAIL")
    }
    override fun postStop() {

    }
    override fun preStart() {
    }
}