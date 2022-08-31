import mmcorej.StrVector
import mmcorej.TaggedImage
import STRIMMImage
import Camera

///NOT WORKING ISSUES WITH THE BITDEPTH I THINK STRIMM DOES NOT KNOW WHAT TO DO WITH THIS
class OpenCV : Camera() {
    internal var timeAcquired = 0.0
    internal var cnt = 0
    internal var timeStart: Long = 0
    var bCamerasAcquire = true

    init {
        try {

            label = "OpenCV"
            library = "OpenCVgrabber"
            name = "OpenCVgrabber"

            val st = StrVector()
            st.add("./DeviceAdapters")
            core.deviceAdapterSearchPaths = st
            core.loadDevice(label, library, name)
            core.initializeDevice(label)
            core.cameraDevice = label

        } catch (ex: Exception) {
            //System.out.println(core.toString() + " " + ex.getMessage());
        }

    }
    //no trigger override for OpenCV

    @Synchronized
    override fun run(): STRIMMImage {
        var pix: ByteArray? = null
        val fpix = FloatArray((core.imageWidth * core.imageHeight).toInt())
        try {
            //prevent taking images before runStream
            while (bCamerasAcquire === false) {
                Thread.sleep(100)
            }
            if (!bSnapped)
            // using the core's circular buffer
            {

                var x1 = 0
                var x2 = 0
                while (true) {
                    //get the current index into the circular buffer store it in x1 and
                    //then wait until that changes which means that a new image has been
                    //put onto the circular buffer
                    x1 = core.remainingImageCount
                    x2 = x1
                    //if the buffer is empty or the device is busy then spin
                    while (x2 == x1 || x2 == 0 || core.deviceBusy(label)) {
                        x2 = core.remainingImageCount
                    }

                    //System.out.println(x1);
                    //
                    //collect and remove (pop) the last image (to reduce the chances of overflow
                    // which might happen more quickly if you kept the image on the circular buffer)
                    //
                    //between the end of the above while loop and the beginning of the section
                    //which retieves the image the buffer could have reset so wrap the next section in
                    //try/catch  to alert us to this happening and also to have another go at getting an
                    //image when it does happen.
                    //
                    if (count === 0) timeStart = System.nanoTime()
                    try {
                        //collect the image
                        //
                        //can be done by popping off all of the images in reverse order
                        //until get the most recent - which is probably the most efficient way
                        //                    TaggedImage im = null;
                        //                    while (core.getRemainingImageCount() > 0){
                        //                        im = core.popNextTaggedImage();
                        //                    }
                        //or get memory location of the last image
                        val im = core.lastTaggedImage
                        timeAcquired = System.nanoTime().toDouble()
                        pix = im.pix as ByteArray
                        count++
                        break
                    } catch (ex: Exception) {
                        //TW 2/8/21 the most likely reason for ending up here is that
                        //we attempted to get an image from an circular buffer (it has reset)
                        //so go around the while loop again and retrieve an image
                        //This will mean that we have LOST an image unless the circular buffer
                        //size is larger than the number of frames needed in the acquisition.
                        print("*****CIRCULAR BUUFER: Found 0 frames in the buffer *****")
                    }

                }
            }
            else { //using the core's image buffer
                //snap the image and also gather some information to
                //estimate the fps
                if (count === 0) timeStart = System.nanoTime()
                core.snapImage()
                pix = core.image as ByteArray
                count++
                timeAcquired = System.nanoTime().toDouble()
            }
            for (j in 0 until core.imageHeight) {
                for (i in 0 until core.imageWidth) {
                    val b1 = pix!![(4 * i + 4 * j * core.imageWidth).toInt()]
                    val b2 = pix[(1 + 4 * i + 4 * j * core.imageWidth).toInt()]
                    val b3 = pix[(2 + 4 * i + 4 * j * core.imageWidth).toInt()]

                    fpix[(i + j * core.imageWidth).toInt()] = ((java.lang.Byte.toUnsignedInt(b1) +
                            java.lang.Byte.toUnsignedInt(b2) + java.lang.Byte.toUnsignedInt(b3)) / 3).toFloat()
                }
            }
        } catch (ex: Exception) {
            println("OpenCV exception")
            println(ex.message)
        }

        return if (bGreyscale)
//            STRIMMImage("OpenCV", fpix, GUIMain.softwareTimerService.getTime(), count, core.getImageWidth() as Int, core.getImageHeight() as Int)//TODO commented out as we don't have services
            STRIMMImage("OpenCV", fpix, AkkaStream.getTime(), count, core.imageWidth.toInt(), core.imageHeight.toInt())
        else
//            STRIMMImage("OpenCV", pix, GUIMain.softwareTimerService.getTime(), count, core.getImageWidth() as Int, core.getImageHeight() as Int)//TODO commented out as we don't have services
            STRIMMImage("OpenCV", pix, AkkaStream.getTime(), count, core.imageWidth.toInt(), core.imageHeight.toInt())
    }
}
