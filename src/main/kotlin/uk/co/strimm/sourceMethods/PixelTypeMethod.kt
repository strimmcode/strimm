

package uk.co.strimm.sourceMethods

import uk.co.strimm.STRIMMBuffer
import uk.co.strimm.STRIMMPixelBuffer
import uk.co.strimm.experiment.Source
import uk.co.strimm.gui.GUIMain

//creates STRIMMPixelBuffers   very simple image generation
class PixelTypeMethod() : SourceBaseMethod() {
    override lateinit var properties : HashMap<String, String>
    var pixelType : String = "Byte"
    var numChannels = 1
    var w : Int = 1000
    var h : Int = 1000
    override fun init(source: Source) {
        this.source = source
        loadCfg()
        w = properties["w"]!!.toInt()
        h = properties["h"]!!.toInt()
        pixelType = properties["pixelType"].toString()
        numChannels = properties["numChannels"]!!.toInt()

    }
    override fun run(): STRIMMBuffer? {
        //this would be a blocking call - which could only be used when num Threads >> num actors
        Thread.sleep(30)//simulate blocking delay
       // println("snap")
        var pix : Any? = null
        if (pixelType == "Byte"){
            pix = ByteArray(w * h * numChannels)
            for (ch in 0..numChannels-1){
//                if (ch == testChannel)
//                {
                    for (j in 0..h-1){
                        for (i in 0..w-1){
                            pix[i + j*w + ch*w*h] = (Math.random()*255).toInt().toByte()
                        }
                    }

                //}
            }
        }
        else if (pixelType == "Short"){
            pix = ShortArray(w * h * numChannels)
            for (ch in 0..numChannels - 1){

                    for (j in 0..h-1){
                        for (i in 0..w-1){
                            pix[i + j*w + ch*w*h] =  (Math.random()*0xffff).toInt().toShort()
                        }
                    }


            }
        }
        else if (pixelType == "Int"){
            pix = IntArray(w * h * numChannels)
            for (ch in 0..numChannels-1){

                    for (j in 0..h-1){
                        for (i in 0..w-1){
                            pix[i + j*w + ch*w*h] = (Math.random()*0xffffffff).toInt()
                        }
                    }


            }
        }
//        else if (pixelType == "Long"){
        //error with IJ render
//            pix = LongArray(w * h * numChannels)
//            for (ch in 0..numChannels-1){
//
//                for (j in 0..h-1){
//                    for (i in 0..w-1){
//                        pix[i + j*w + ch*w*h] = (Math.random()*0xffffffffffffff).toInt().toLong()
//                    }
//                }
//
//
//            }
  //      }
        //cannot have more than 1 channel with Float and Double
        else if (pixelType == "Float"){
            pix = FloatArray(w * h * numChannels)
                    for (j in 0..h-1){
                        for (i in 0..w-1){
                            pix[i + j*w] = i*10.0f
                        }
                    }
        }
        else if (pixelType == "Double"){
            pix = DoubleArray(w * h * numChannels)
                    for (j in 0..h-1){
                        for (i in 0..w-1){
                            pix[i + j*w ] = i+1000.0
                        }
                    }
        }
        else {
            println("Pixel type is not supported")
        }
        dataID++
        return STRIMMPixelBuffer(pix, w, h, pixelType, numChannels, GUIMain.softwareTimerService.getTime(), dataID, 1)
    }
    override fun postStop() {
    }
    override fun preStart() {
    }
}