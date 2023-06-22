package uk.co.strimm.flowMethods

import akka.actor.ActorRef
import com.opencsv.CSVReader
import uk.co.strimm.STRIMMBuffer
import uk.co.strimm.STRIMMPixelBuffer
import uk.co.strimm.experiment.Flow
import uk.co.strimm.gui.GUIMain
import java.io.FileReader

//Forms a ratio flow from consecutive images - where images are taken
//in different wavelengths
//
//
open class RatioFlowMethod() : FlowMethod {
    open lateinit var flow: Flow
    override lateinit var properties: HashMap<String, String>
    override var actor: ActorRef? = null

    var im_old : STRIMMPixelBuffer? = null

    override fun init(flow: Flow) {
        //todo this is in the wrong place it should be in flow
        this.flow = flow
        if (flow.flowCfg != "") {
            properties = hashMapOf<String, String>()
            var r: List<Array<String>>? = null
            try {
                CSVReader(FileReader(flow.flowCfg)).use { reader ->
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

    override fun run(data: List<STRIMMBuffer>): STRIMMBuffer {
        //the flow function should know the

        val im = data[0] as STRIMMPixelBuffer
        var pix = ShortArray(im.w * im.h)
        var statusID = 0
        if (im.dataID % 2 == 0 && im_old != null){
            var pix0 = im_old!!.pix as ShortArray
            var pix1 = im.pix as ShortArray


            for (j in 0..im.w*im.h - 1) {
                pix[j] = (pix1[j].toDouble() / pix0[j].toDouble()).toInt().toShort()
            }
            statusID = 1
        }
        else{
            statusID = 0
        }


        im_old = im

        //var pix : Any?, val w : Int, val h : Int, val pixelType : String, val numChannels : Int, var timeAcquired : Double, dataID : Int, status : Int
        return STRIMMPixelBuffer(pix, im.w, im.h, im.pixelType, im.numChannels, GUIMain.softwareTimerService.getTime(), im.dataID, statusID )

    }
    override fun preStart(){

    }
    override fun postStop(){

    }

}