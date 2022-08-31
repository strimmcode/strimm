import mmcorej.CMMCore
import STRIMMImage
import javax.imageio.ImageIO
import java.awt.image.BufferedImage
import java.io.File

//this does not need to be runnable but does need to return a STRIMMImage
open class Camera {
    var name = ""
    var library = ""
    var label = ""
    //System.out.println(core.toString() + " getCore()");
//    var core: CMMCore? = CMMCore()
//        internal set
    var core = CMMCore()

    internal var count = 0

    internal var bSnapped = false
    internal var bTriggered = false
    internal var bTimeLapse = false
    internal var intervalMs = 100.0
    var exposureMs = 0.5
    internal var bActive = false
    internal var framesInCircularBuffer = 20

    internal var bGreyscale = false


    internal var bConfig = false
    fun SetGreyScale(bl: Boolean) {
        bGreyscale = bl
    }

    fun SetCameraActivation(bl: Boolean) {
        bActive = bl
    }

    fun StartAcquisition() {
        if (!bSnapped) {
            val memSize = core!!.bytesPerPixel * core!!.imageHeight * core!!.imageWidth * framesInCircularBuffer.toLong() / 1024 / 1024
            try {
                core!!.circularBufferMemoryFootprint = memSize

                core!!.initializeCircularBuffer() //circular buffer

                core!!.startContinuousSequenceAcquisition(0.0) //must be
            } catch (ex: Exception) {
                println(ex.message)
            }

        }
    }

    fun SaveAsTiff(pix1: ByteArray) {
        try {
            val bufImage = BufferedImage(512, 512,
                    BufferedImage.TYPE_BYTE_GRAY)
            var cnt = 0
            for (j in 0..511) {
                for (i in 0..511) {
                    val `val` = pix1[cnt].toInt()
                    bufImage.setRGB(i, j, `val` + `val` * 256 + `val` * 256 * 256)
                    cnt++
                }
            }

            ImageIO.write(bufImage, "tiff", File("Acquisitions/$name/$count.tiff"))
            count++
        } catch (ex: Exception) {
            println("error saving tiff")
        }

    }

    fun Reset() {
        try {
            println("reset the core")
            core!!.reset()
        } catch (e: Exception) {
            e.printStackTrace()
        }

    }

    open fun run(): STRIMMImage {
//        return STRIMMImage("Not recognised", null, GUIMain.softwareTimerService.getTime(), 0, 0, 0) //TODO commented out as we don't have services
        return STRIMMImage("Not recognised", null, 1, 0, 0, 0)
    }

    //
    @Throws(Exception::class)
    fun SetExposureMs(exp: Double) {
        exposureMs = exp
        if (core != null) core!!.exposure = exposureMs
    }

    fun SetSnapped(bSnap: Boolean) {
        bSnapped = bSnap
    }

    @Throws(Exception::class)
    fun SetTriggered(bTrig: Boolean) {
        bTriggered = bTrig
    }

    fun SetTimeLapse(bTimeL: Boolean) {
        bTimeLapse = bTimeL
    }

    fun SetIntervalMs(intMs: Double) {
        intervalMs = intMs
    }

    fun SetFramesInCircularBuffer(circ: Int) {
        framesInCircularBuffer = circ
    }

    fun SetROI(x: Int, y: Int, w: Int, h: Int) {
        if (w > 0 && h > 0) {
            try {
                core!!.setROI(x, y, w, h)
//                GUIMain.strimmUIService.SetROI(label, x, y, w, h)//TODO commented out as we don't have services
            } catch (e: Exception) {
                println("Error $label unable to set ROI")
                e.printStackTrace()
            }

        }
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            //        try {
            //
            //
            //            BufferedImage bufImage =
            //                    new BufferedImage(512, 512,
            //                            BufferedImage.TYPE_BYTE_GRAY);
            //            int val = 255;
            //            for (int j = 0; j<512; j++){
            //                for (int i = 0; i<200; i++){
            //                    bufImage.setRGB(i, j, val + val*256 + val*256*256);
            //                }
            //            }
            //
            //            ImageIO.write(bufImage, "tiff", new File("testzzz.tiff"));
            //        } catch(Exception ex){
            //            System.out.println(ex.getMessage());
            //        }
        }
    }
}
