
package uk.co.strimm.sourceMethods

import akka.actor.ActorRef
import com.google.common.math.IntMath.mod
import com.opencsv.CSVReader
import mmcorej.CMMCore
import mmcorej.StrVector
import uk.co.strimm.STRIMMBuffer
import uk.co.strimm.STRIMMPixelBuffer
import uk.co.strimm.experiment.Source
import uk.co.strimm.gui.GUIMain
import java.io.FileReader
import java.lang.Math.abs

open class MMCameraSource() : SourceMethod {
    //8/10/22 has the problem where repeated use causes a crash in core, this turned out to be that the core
    //needs to be reset
    var name = ""
    var library = ""
    var label = ""
    var core : CMMCore? = CMMCore()

    lateinit var source: Source
    override lateinit var properties : HashMap<String, String>
    override var actor: ActorRef?
        get() = TODO("Not yet implemented")
        set(value) {}
    var dataID : Int = 0
    //
    var x : Int = 0
    var y : Int = 0
    var w : Int = 1000
    var h : Int = 1000
    var exposureMs = 30.0
    var pixelType : String = "Byte"
    var numChannels = 1
    var coreBytesPerPixel = 0
    var bSnapped = false



    override fun init(source : Source){
        this.source = source
        //load the config file - inside (source.sourceName).cfg
        loadCfg()
        val st = StrVector()
        st.add("./DeviceAdapters/")
        try{
            println("load camera ******************")
            core!!.setDeviceAdapterSearchPaths(st)
            core!!.loadSystemConfiguration("./DeviceAdapters/CameraMMConfigs/" + properties["MMDeviceConfig"]) // MMDeviceConfig, Ximea.cfg#
            label = core!!.getCameraDevice()
            name = core!!.getDeviceName(label)
            library = core!!.getDeviceLibrary(label)
            coreBytesPerPixel = core!!.bytesPerPixel.toInt()
            println("success loaded camera ****************")
        } catch(ex : Exception){
            println("Exception: !" + ex.message)
        }

        pixelType = properties["pixelType"]!!
        numChannels = properties["numChannels"]!!.toInt()
        x = properties["x"]!!.toInt()
        y = properties["y"]!!.toInt()
        w = properties["w"]!!.toInt()
        h = properties["h"]!!.toInt()
        exposureMs = properties["exposureMs"]!!.toDouble()
        //0.0 measn use default exposure
        if (abs(exposureMs) > 1e-5) {
            core!!.setExposure(label, exposureMs)
        }
        val exp = core!!.getExposure(label)
        //println("Exposure is : " +  exp)
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


    override fun run() : STRIMMBuffer?{
        if (bSnapped){

            return runSnapped()
        }
        else {
            return runCircBuffer()
        }

    }


    @Synchronized
    fun runSnapped() : STRIMMBuffer? {
        //snap acquisition
        if (mod(dataID, 100) == 0){
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
                }
                else if (numChannels == 4) {
                    if (coreBytesPerPixel != 4) {
                        println("Core format clashes with cfg")
                    } else {
                        //RGB32
                        //OpenCV provides interleaved BGRA this needs to be transformed into separate stacks
                        //todo include in cfg and use a separate function for this as some cameras might issue the image as r,g,b images in a stack
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
                            GUIMain.softwareTimerService.getTime(), //System.nanoTime().toDouble()/1000000.0,
                            dataID,
                            1
                        )
                    }
                }
                else {
                    println("Number of channels not supported")
                }
            }
            else if (pixelType == "Short") {
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
                }
                else if (numChannels == 4) {
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
            }
            else if (pixelType == "Float") {
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
                }
                else {
                    println("Number of channels not supported")
                }
            }
            else {
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
        if (mod(dataID, 1000) == 0){
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

    override fun preStart() {
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
        //Unloads all devices from the core, clears all configuration data and property blocks.
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