package uk.co.strimm.flowMethods



import akka.actor.ActorRef
import com.google.common.math.IntMath
import com.opencsv.CSVReader
import mmcorej.CMMCore
import mmcorej.StrVector
import uk.co.strimm.STRIMMBuffer
import uk.co.strimm.STRIMMPixelBuffer
import uk.co.strimm.STRIMMSequenceCameraDataBuffer
import uk.co.strimm.experiment.Flow
import uk.co.strimm.gui.GUIMain
import java.io.FileReader

//OK 22/02/23  TW
//MMSeqCameraFlow - in response to a STRIMMBuffer will take a burst of images defined by its config
//the list of images is then sent doenstream in a STRIMMSequenceCameraDataBuffer

class MMSeqCameraFlow : FlowMethod {


    //exposure time, number of frames to take in a burst and the interframe period (which has to be > exposure time)
    //come from the config

    open lateinit var flow: Flow
    override lateinit var properties: HashMap<String, String>
    override var actor: ActorRef? = null
    var name = ""
    var library = ""
    var label = ""
    var core : CMMCore? = CMMCore()
    var x : Int = 0
    var y : Int = 0
    var w : Int = 1000
    var h : Int = 1000
    var numFrames : Int = 0
    var intervalMs : Double = 0.0
    var pixelType : String = "Byte"
    var numChannels = 1
    var coreBytesPerPixel = 0
    var bSnapped = false
    var dataID : Int = 0

    override fun init(flow: Flow) {
        this.flow = flow
        loadCfg()
        val st = StrVector()
        st.add("./DeviceAdapters/")
        try{
            //println("load camera ******************")
            core!!.setDeviceAdapterSearchPaths(st)
            core!!.loadSystemConfiguration("./DeviceAdapters/CameraMMConfigs/" + properties["MMDeviceConfig"]) // MMDeviceConfig, Ximea.cfg#
            label = core!!.getCameraDevice()
            name = core!!.getDeviceName(label)
            library = core!!.getDeviceLibrary(label)
            coreBytesPerPixel = core!!.bytesPerPixel.toInt()
            //println("success loaded camera ****************")
        } catch(ex : Exception){
            println("Exception: !" + ex.message)
        }

        pixelType = properties["pixelType"]!!
        numChannels = properties["numChannels"]!!.toInt()
        x = properties["x"]!!.toInt()
        y = properties["y"]!!.toInt()
        w = properties["w"]!!.toInt()
        h = properties["h"]!!.toInt()

        numFrames = properties["numFrames"]!!.toInt()
        intervalMs = properties["intervalMs"]!!.toDouble()
        //MicroManager core supports:
        //Bytes    channels     BytesPerPixel
        //1         1               1
        //2         1               2
        //1         4               4 (RGB32)
        //2         4               8 (RGB64)
        //float     1               4 (FLOAT)
        //
        //imageJ supports more formats than MicroManager

        //(also it is 3 channel byte which is displayed as RGB in IJ)


        if (w != core!!.imageWidth.toInt() || h != core!!.imageHeight.toInt())
            core!!.setROI(x,y,w,h)
        bSnapped = properties["isImageSnapped"]!!.toBoolean()

        println(label.toString() + " " + library + " " + name + " " + pixelType + " " + numChannels)
        //configure the trigger for this camera
        if (properties["isTriggered"]!!.toBoolean()){
            var r: List<Array<String>>? = null
            try {
                CSVReader(FileReader("./DeviceAdapters/CameraMMConfigsTrigger/" + properties["MMDeviceConfig"])).use { reader ->
                    r = reader.readAll()
                    for (triggerCfg in r!!) {
                        try {
                            core!!.setProperty(label, triggerCfg[0], triggerCfg[1])
                        } catch (ex: java.lang.Exception) {
                            println(ex.message)
                        }
                    }
                }
            } catch (ex: java.lang.Exception) {
                println(ex.message)
            }
        }


    }

    override fun run(data: List<STRIMMBuffer>): STRIMMBuffer {
        //combination of the position in the list of images (for each burst)
        //and the dataID can be used to provide an index and order for each image.
        //Each STRIMMPixelBuffer contains its own time
        var timeCnt = GUIMain.softwareTimerService.getTime()
        var curTime = timeCnt
        var images = mutableListOf<STRIMMPixelBuffer>()


        //TODO need a better naming convention for batch images than this
        //at the moment hdf5 sinply takes the order than the batches and the images
        //within are received.  Acceptable, but probably needs to be better organised.
        for (f in 0..numFrames-1) {
            if (bSnapped) {
                images.add(runSnapped()!! as STRIMMPixelBuffer)
            } else {
                images.add(runCircBuffer()!! as STRIMMPixelBuffer)
            }
            images[f].dataID = f

            curTime = GUIMain.softwareTimerService.getTime()
            //spin until the start of the next image
            while (curTime < timeCnt + intervalMs){
                curTime = GUIMain.softwareTimerService.getTime()
            }
            timeCnt = curTime
        }

        return STRIMMSequenceCameraDataBuffer(images , dataID, 1)


    }

    fun loadCfg() {
        properties = hashMapOf<String, String>()
        if (flow.flowCfg != "") {

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
        else{

        }

    }
    override fun preStart(){
        if (!bSnapped) {
            StartAcquisition()
        }
    }
    override fun postStop(){
        //println("*****post stop****")
        if (!bSnapped){
            // core!!.stopSequenceAcquisition()   ////////////////is this needed?
        }
        core!!.reset()
        Thread.sleep(2000)  //TODO does it need this?
    }


    @Synchronized
    fun runSnapped() : STRIMMBuffer? {

        //snap acquisition
        if (IntMath.mod(dataID, 100) == 0){
            println("snap " + dataID + "  time: " + GUIMain.softwareTimerService.getTime())

        }
        core!!.snapImage()
        try {
            val pix = core!!.image
            //note that ImageJ display supports many more pixelTypes and num of channels than MMan Core
            if (pixelType == "Byte") {
                if (numChannels == 1) {
                    if (coreBytesPerPixel != 1) {
                        println("Core format clashes with cfg")
                    } else {
                        //8 Bit GREY
                        dataID++
                        return STRIMMPixelBuffer(
                            pix,
                            w,
                            h,
                            pixelType,
                            numChannels,
                            GUIMain.softwareTimerService.getTime(),
                            dataID,
                            1
                        )

                    }
                } else if (numChannels == 4) {
                    if (coreBytesPerPixel != 4) {
                        println("Core format clashes with cfg")
                    } else {
                        //RGB32
                        //OpenCV (webcam) provides interleaved BGRA this needs to be transformed into separate stacks
                        //TODO include in cfg and use a separate function for this as some cameras might issue the image as r,g,b images in a stack
                        val pix1 = pix as ByteArray
                        //convert into 3 layers - deinterleave
                        val pix2 = ByteArray(w * h * 3)
                        for (ch in 0..3 - 1) {
                            for (j in 0..h - 1) {
                                for (i in 0..w - 1) {
                                    pix2[i + j * w + (w * h * (2 - ch))] = pix1[ch + 4 * i + 4 * w * j]
                                }
                            }
                        }

                        dataID++
                        return STRIMMPixelBuffer(
                            pix2,
                            w,
                            h,
                            pixelType,
                            3,
                            GUIMain.softwareTimerService.getTime(),
                            dataID,
                            1
                        )
                    }
                } else {
                    println("Number of channels not supported")
                }
            } else if (pixelType == "Short") {
                if (numChannels == 1) {
                    if (coreBytesPerPixel != 2) {
                        println("Core format clashes with cfg")
                    } else {
                        //16bit GREY
                        dataID++
                        return STRIMMPixelBuffer(
                            pix,
                            w,
                            h,
                            pixelType,
                            numChannels,
                            GUIMain.softwareTimerService.getTime(),
                            dataID,
                            1
                        )

                    }
                } else if (numChannels == 4) {
                    if (coreBytesPerPixel != 8) {
                        println("Core format clashes with cfg")
                    } else {
                        //RGB64
                        dataID++
                        return STRIMMPixelBuffer(
                            pix,
                            w,
                            h,
                            pixelType,
                            numChannels,
                            GUIMain.softwareTimerService.getTime(),
                            dataID,
                            1
                        )
                    }
                } else {
                    println("Number of channels not supported")
                }
            } else if (pixelType == "Float") {
                if (numChannels == 1) {
                    if (coreBytesPerPixel != 4) {
                        println("Core format clashes with cfg")
                    } else {
                        //32bit float
                        dataID++
                        return STRIMMPixelBuffer(
                            pix,
                            w,
                            h,
                            pixelType,
                            numChannels,
                            GUIMain.softwareTimerService.getTime(),
                            dataID,
                            1
                        )
                    }
                } else {
                    println("Number of channels not supported")
                }
            } else {
                println("Pixel type not supported")
            }
            // GUIMain.protocolService.GDIPrintText(core.imageWidth.toInt(),core.imageHeight.toInt(), pix, "dataID : " + dataID.toString(),50.0, 50.0, 80)
        }catch(ex : Exception) {
            println(core.toString())
            println(ex.message)  //something is going on with the camera
        }
        return STRIMMPixelBuffer(null, 0, 0, "", 0, GUIMain.softwareTimerService.getTime(), dataID, 0)

    }

    @Synchronized
    fun runCircBuffer() : STRIMMBuffer? {
        //does not specify the type of pix
        //live acquisition from circular buffer - take the next image
        if (IntMath.mod(dataID, 1000) == 0){
            println("snap " + dataID + "  time: " + GUIMain.softwareTimerService.getTime())

        }
        var pix : Any? = null
        var x1 = 0
        var x2 = 0
        while (true) {
            //get the current index into the circular buffer store it in x1 and
            //then wait until that changes which means that a new image has been
            //put onto the circular buffer
            x1 = core!!.remainingImageCount
            x2 = x1
            //if the buffer is empty or the device is busy then spin
            while (x2 == x1 || x2 == 0 || core!!.deviceBusy(label)) {
                x2 = core!!.remainingImageCount
            }

            //
            //collect and remove (pop) the last image (to reduce the chances of overflow
            // which might happen more quickly if you kept the image on the circular buffer)
            //
            //between the end of the above while loop and the beginning of the section
            //which retieves the image the buffer could have reset so wrap the next section in
            //try/catch  to alert us to this happening and also to have another go at getting an
            //image when it does happen.
            //
            try {
                //collect the image
                val im = core!!.lastTaggedImage
                pix = im.pix
                dataID++
                break
            } catch (ex: java.lang.Exception) {
                //TW 2/8/21 the most likely reason for ending up here is that
                //we attempted to get an image from an circular buffer (it has reset)
                //so go around the while loop again and retrieve an image
                //This will mean that we have LOST an image unless the circular buffer
                //size is larger than the number of frames needed in the acquisition.
                print("*****CIRCULAR BUUFER: Found 0 frames in the buffer *****")
            }
        }

        //print("**********************")

        //note that ImageJ display supports many more pixelTypes and num of channels than MMan Core
        if (pixelType == "Byte"){
            if (numChannels == 1){
                if (coreBytesPerPixel != 1){
                    println("Core format clashes with cfg")
                }else{
                    //8 Bit GREY
                    dataID++
                    return STRIMMPixelBuffer(pix, w, h, pixelType, numChannels, GUIMain.softwareTimerService.getTime(), dataID, 1)

                }
            }
            else if (numChannels == 4){
                if (coreBytesPerPixel != 4){
                    println("Core format clashes with cfg")
                }else{
                    //RGB32
                    val pix1 = pix as ByteArray
                    //convert into 3 layers - deinterleave
                    val pix2 = ByteArray(w*h*3)
                    for (ch in 0..3-1){
                        for (j in 0..h-1){
                            for (i in 0..w-1){
                                pix2[i + j*w + (w*h*(2-ch))] = pix1[ch + 4*i + 4*w*j]
                            }
                        }
                    }

                    dataID++
                    return STRIMMPixelBuffer(pix2, w, h, pixelType, 3, GUIMain.softwareTimerService.getTime(), dataID, 1)
                }
            }
            else{
                println("Number of channels not supported")
            }
        }
        else if (pixelType == "Short"){
            if (numChannels == 1){
                if (coreBytesPerPixel != 2){
                    println("Core format clashes with cfg")
                }else{
                    //16bit GREY
                    dataID++
                    return STRIMMPixelBuffer(pix, w, h, pixelType, numChannels, GUIMain.softwareTimerService.getTime(), dataID, 1)

                }
            }
            else if (numChannels == 4){
                if (coreBytesPerPixel != 8){
                    println("Core format clashes with cfg")
                }else{
                    //RGB64
                    dataID++
                    return STRIMMPixelBuffer(pix, w, h, pixelType, numChannels, GUIMain.softwareTimerService.getTime(), dataID, 1)
                }
            }
            else{
                println("Number of channels not supported")
            }
        }
        else if (pixelType == "Float"){
            if (numChannels == 1){
                if (coreBytesPerPixel != 4){
                    println("Core format clashes with cfg")
                }else{
                    //32bit float
                    dataID++
                    return STRIMMPixelBuffer(pix, w, h, pixelType, numChannels, GUIMain.softwareTimerService.getTime(), dataID, 1)
                }
            }
            else{
                println("Number of channels not supported")
            }
        }
        else{
            println("Pixel type not supported")
        }
        // GUIMain.protocolService.GDIPrintText(core.imageWidth.toInt(),core.imageHeight.toInt(), pix, "dataID : " + dataID.toString(),50.0, 50.0, 80)

        return STRIMMPixelBuffer(null, 0, 0,"", 0, GUIMain.softwareTimerService.getTime(), dataID, 0)

    }



    private fun StartAcquisition() {
        val memSize: Long = core!!.bytesPerPixel * core!!.imageHeight * core!!.imageWidth * properties["framesInCircularBuffer"]!!.toLong() / 1024 / 1024
        try {
            println("If error here then problems caused by setCircularBufferMemoryFootprint(), so reduce the number of frames on circular buffer or use the default -1.")
            if (memSize > 0) core!!.circularBufferMemoryFootprint = memSize
            core!!.initializeCircularBuffer() //circular buffer
            core!!.startContinuousSequenceAcquisition(0.0) //must be



        } catch (ex: Exception) {
            println(ex.message)
        }
    }
}