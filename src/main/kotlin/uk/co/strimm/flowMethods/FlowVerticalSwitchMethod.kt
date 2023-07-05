package uk.co.strimm.flowMethods



import akka.actor.ActorRef
import uk.co.strimm.ImageData
import uk.co.strimm.STRIMMBuffer
import uk.co.strimm.STRIMMImage
import uk.co.strimm.experiment.Flow
import java.util.concurrent.CompletionStage
//demonstrate combining images from 2 difference and independent image sources
//
open class FlowVerticalSwitchMethod() : FlowMethod {
    lateinit var flow : Flow
    override lateinit var properties : HashMap<String, String>
    override var actor: ActorRef? = null
    var count = 0
    override fun init(flow: Flow) {
        this.flow = flow
    }

    override fun run(image: List<STRIMMBuffer>): List<STRIMMBuffer> {
        //the flow function should know the
        count++
        //println("Flow processing image " + image.size + "  " + count)

        val im1 = image[0] as STRIMMImage
        val w = im1.images[0].w
        val h = im1.images[0].h
        val pix1 = im1.images[0].pix as ShortArray

        val im2 = image[1] as STRIMMImage
        val pix2 = im2.images[0].pix as ShortArray


       var cnt = 0
//       val pix = ShortArray(w*h)
//        for (f in 0..pix1.size/2 - 1){
//            pix[f] = pix1[f]
//            cnt++
//
//        }
//        for (f in 0..pix2.size/2 - 1){
//            pix[cnt] = pix2[f]
//            cnt++
//        }

        //im1.images[0].pix = pix
        return listOf(STRIMMImage( arrayListOf(ImageData(pix1,w,h,16,System.nanoTime(), count)),0, "", 1))
    }

    override fun preStart(){

    }
    override fun postStop(){

    }




}