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
import java.util.logging.Level

open class MMCameraSource : SourceMethod {
    var name = ""
    var library = ""
    var label = ""
    var core: CMMCore? = CMMCore()

    lateinit var source: Source
    override lateinit var properties: HashMap<String, String>
    override var actor: ActorRef?
        get() = TODO("Not yet implemented")
        set(value) {}

    var dataID: Int = 0
    var x: Int = 0
    var y: Int = 0
    var w: Int = 1000
    var h: Int = 1000
    var exposureMs = 30.0
    var pixelType: String = "Byte"
    var numChannels = 1
    var coreBytesPerPixel = 0
    var bSnapped = false
    var stop = false

    //Number of frames that should be in the buffer before we start reading and sending
    //This is to prevent the acquisition stopping as soon as it's begun
    val initialBufferAmount = 5

    override fun init(source: Source) {
        this.source = source
        loadCfg()
        val st = StrVector()
        st.add("./DeviceAdapters/") //TODO hardcoded path
        try {
            GUIMain.loggerService.log(Level.INFO, "Loading MM camera from config ${properties["MMDeviceConfig"]}")
            core!!.deviceAdapterSearchPaths = st
            core!!.loadSystemConfiguration("./DeviceAdapters/CameraMMConfigs/" + properties["MMDeviceConfig"])
            label = core!!.cameraDevice
            name = core!!.getDeviceName(label)
            library = core!!.getDeviceLibrary(label)
            coreBytesPerPixel = core!!.bytesPerPixel.toInt()
            GUIMain.loggerService.log(Level.INFO, "Successfully loaded camera from config ${properties["MMDeviceConfig"]}")
        }
        catch (ex: Exception) {
            GUIMain.loggerService.log(Level.SEVERE,"Error trying to load MM camera from config files. Message: " + ex.message)
            GUIMain.loggerService.log(Level.SEVERE,ex.stackTrace)
        }

        pixelType = properties["pixelType"]!!
        numChannels = properties["numChannels"]!!.toInt()
        x = properties["x"]!!.toInt()
        y = properties["y"]!!.toInt()
        w = properties["w"]!!.toInt()
        h = properties["h"]!!.toInt()
        exposureMs = properties["exposureMs"]!!.toDouble()

        if (abs(exposureMs) > 1e-5) {
            GUIMain.loggerService.log(Level.INFO, "Setting exposure to $exposureMs")
            core!!.setExposure(label, exposureMs)
        }

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
            core!!.setROI(x, y, w, h)
        bSnapped = properties["isImageSnapped"]!!.toBoolean()

        GUIMain.loggerService.log(Level.INFO, "Camera loaded. Label=$label Library=$library Name=$name Pixel type=$pixelType Num channels=$numChannels")
        //configure the trigger for this camera
        if (properties["isTriggered"]!!.toBoolean()) {
            var r: List<Array<String>>? = null
            try {
                CSVReader(FileReader("./DeviceAdapters/CameraMMConfigsTrigger/" + properties["MMDeviceConfig"])).use { reader ->
                    r = reader.readAll()
                    for (triggerCfg in r!!) {
                        try {
                            core!!.setProperty(label, triggerCfg[0], triggerCfg[1])
                        } catch (ex: Exception) {
                            GUIMain.loggerService.log(Level.WARNING, "Unable to set trigger property. Message: ${ex.message} Name=${triggerCfg[0]},value=${triggerCfg[1]} for $label")
                            GUIMain.loggerService.log(Level.WARNING, ex.stackTrace)
                        }
                    }
                }
            } catch (ex: Exception) {
                GUIMain.loggerService.log(Level.INFO, "Error reading $label camera trigger config. Message: ${ex.message}")
                GUIMain.loggerService.log(Level.INFO, ex.stackTrace)
            }
        }

//        if(!bSnapped) {
            //This is done so when stopping the acquisiton, each core can be shut down appropriately using stopSequenceAcquistion()
            GUIMain.loggerService.log(Level.INFO, "Adding core to cores list")
            GUIMain.experimentService.allMMCores[source.sourceName] = Pair(label, core!!)
//        }
    }

    override fun run(): STRIMMBuffer? {
        if (bSnapped) {
            return runSnapped()
        } else {
            return runCircBuffer()
        }
    }

    @Synchronized
    fun runSnapped(): STRIMMBuffer? {
        core!!.snapImage()

        try {
            val pix = core!!.image //TODO should this use taggedImage instead?
//            println("Image min is: ${(pix as ByteArray).min()}, max is: ${(pix as ByteArray).max()}")
            //note that ImageJ display supports many more pixelTypes and num of channels than MMan Core
            if (pixelType == "Byte") {
                if (numChannels == 1) {
                    if (coreBytesPerPixel != 1) {
                        GUIMain.loggerService.log(Level.WARNING, "Core format clashes with cfg")
                    }
                    else {
                        //8 Bit GREY
                        dataID++
                        return STRIMMPixelBuffer(
                            pix,
                            w,
                            h,
                            pixelType,
                            numChannels,
                            GUIMain.softwareTimerService.getTime(),
                            label,
                            dataID,
                            1)
                    }
                }
                else if (numChannels == 4) {
                    if (coreBytesPerPixel != 4) {
                        GUIMain.loggerService.log(Level.WARNING,"Core format clashes with cfg")
                    }
                    else {
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
                            label,
                            dataID,
                            1)
                    }
                }
                else {
                    GUIMain.loggerService.log(Level.WARNING,"Number of channels not supported")
                }
            }
            else if (pixelType == "Short") {
                if (numChannels == 1) {
                    if (coreBytesPerPixel != 2) {
                        GUIMain.loggerService.log(Level.WARNING,"Core format clashes with cfg")
                    }
                    else {
                        //16bit GREY
                        dataID++

                        return STRIMMPixelBuffer(
                            pix,
                            w,
                            h,
                            pixelType,
                            numChannels,
                            GUIMain.softwareTimerService.getTime(),
                            label,
                            dataID,
                            1)
                    }
                }
                else if (numChannels == 2) {
                    dataID++
                    return STRIMMPixelBuffer(
                        pix,
                        w,
                        h,
                        pixelType,
                        numChannels,
                        GUIMain.softwareTimerService.getTime(),
                        label,
                        dataID,
                        1)
                }
                else if (numChannels == 4) {
                    if (coreBytesPerPixel != 8) {
                        GUIMain.loggerService.log(Level.WARNING,"Core format clashes with cfg")
                    }
                    else {
                        //RGB64
                        dataID++
                        return STRIMMPixelBuffer(
                            pix,
                            w,
                            h,
                            pixelType,
                            numChannels,
                            GUIMain.softwareTimerService.getTime(),
                            label,
                            dataID,
                            1)
                    }
                }
                else {
                    GUIMain.loggerService.log(Level.WARNING,"Number of channels not supported")
                }
            }
            else if (pixelType == "Float") {
                if (numChannels == 1) {
                    if (coreBytesPerPixel != 4) {
                        GUIMain.loggerService.log(Level.WARNING,"Core format clashes with cfg")
                    }
                    else {
                        //32bit float
                        dataID++
                        return STRIMMPixelBuffer(
                            pix,
                            w,
                            h,
                            pixelType,
                            numChannels,
                            GUIMain.softwareTimerService.getTime(),
                            label,
                            dataID,
                            1)
                    }
                }
                else {
                    GUIMain.loggerService.log(Level.WARNING, "Number of channels not supported")
                }
            }
            else {
                GUIMain.loggerService.log(Level.WARNING, "Pixel type not supported")
            }
            // GUIMain.protocolService.GDIPrintText(core.imageWidth.toInt(),core.imageHeight.toInt(), pix, "dataID : " + dataID.toString(),50.0, 50.0, 80)
        } catch (ex: Exception) {
            GUIMain.loggerService.log(Level.SEVERE, "Error reading core image")
            println(ex.message)  //something is going on with the camera
        }

        //If we're here then something went wrong. We just return an empty STRIMMPixelBuffer object
        return STRIMMPixelBuffer(null, 0, 0, "", 0, GUIMain.softwareTimerService.getTime(), "", dataID, 0)
    }

    @Synchronized
    fun runCircBuffer(): STRIMMBuffer? {
        if (mod(dataID, 1000) == 0) {
            GUIMain.loggerService.log(Level.INFO,"snap " + dataID + "  software time: " + GUIMain.softwareTimerService.getTime())
        }

        var pix: Any? = null
        var time = 0.0
//        var x1 = 0
//        var x2 = 0

//        x1 = core!!.remainingImageCount
//        GUIMain.loggerService.log(Level.INFO, "Remaining image count is $x1")
        while ((core!!.remainingImageCount < initialBufferAmount && !stop) || core!!.deviceBusy(label)) { //Spin waiting for images at the beginning
        }

        /**
         * There may be instances where this method cannot send images faster than they are being collected.
         * This while loop is designed to ensure all images collected through the MMCore eventually get sent and not
         * destroyed/lost when the MMcore is reset
         */
        while (core!!.remainingImageCount > 0) {
            try {
                val im = core!!.popNextTaggedImage()
                pix = im.pix
//                GUIMain.loggerService.log(Level.INFO, "MMCameraImage number=${im.tags["ImageNumber"]}")
//                    println("MMCameraImage elapsed time=${im.tags["ElapsedTime-ms"]}")
//                    println("MMCameraImage PVCam frame number=${im.tags["PVCAM-FrameNr"]}")
//                println("MMCameraImage PVCam timestamp BOF=${im.tags["PVCAM-TimeStampBOF"]}")
                time = im.tags["ElapsedTime-ms"].toString().toDouble()
                if (core!!.remainingImageCount == 0) {
                    GUIMain.loggerService.log(Level.INFO, "Remaining image count is 0. Stop=true")
                    stop = true
                    return STRIMMPixelBuffer(null, 0, 0, "", 0, -1.0, "", dataID, 0)
                }
            }
            catch (ex: Exception) {
                GUIMain.loggerService.log(Level.SEVERE, "Error when trying to read image from MMCore. Message: ${ex.message}")
                GUIMain.loggerService.log(Level.SEVERE, ex.stackTrace)
                break
            }

            //note that ImageJ display supports many more pixelTypes and num of channels than MMan Core
            if (pixelType == "Byte") {
                if (numChannels == 1) {
                    if (coreBytesPerPixel != 1) {
                        GUIMain.loggerService.log(Level.WARNING,"Core format clashes with cfg")
                    }
                    else {
                        //8 Bit GREY
                        dataID++
                        return STRIMMPixelBuffer(
                            pix,
                            w,
                            h,
                            pixelType,
                            numChannels,
                            time,
                            label,
                            dataID,
                            1)
                    }
                }
                else if (numChannels == 4) {
                    if (coreBytesPerPixel != 4) {
                        GUIMain.loggerService.log(Level.WARNING,"Core format clashes with cfg")
                    }
                    else {
                        //RGB32
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
                            time,
                            label,
                            dataID,
                            1)
                    }
                }
                else {
                    GUIMain.loggerService.log(Level.WARNING,"Number of channels not supported")
                }
            }
            else if (pixelType == "Short") {
                if (numChannels == 1) {
                    if (coreBytesPerPixel != 2) {
                        GUIMain.loggerService.log(Level.WARNING,"Core format clashes with cfg")
                    }
                    else {
                        //16bit GREY
                        dataID++
                        return STRIMMPixelBuffer(
                            pix,
                            w,
                            h,
                            pixelType,
                            numChannels,
                            time,
                            label,
                            dataID,
                            1)
                    }
                }
                else if(numChannels == 2){
                    dataID++
                    return STRIMMPixelBuffer(
                        pix,
                        w,
                        h,
                        pixelType,
                        numChannels,
                        time,
                        label,
                        dataID,
                        1)
                }
                else if (numChannels == 4) {
                    if (coreBytesPerPixel != 8) {
                        GUIMain.loggerService.log(Level.WARNING,"Core format clashes with cfg")
                    }
                    else {
                        //RGB64
                        dataID++
                        return STRIMMPixelBuffer(
                            pix,
                            w,
                            h,
                            pixelType,
                            numChannels,
                            time,
                            label,
                            dataID,
                            1)
                    }
                }
                else {
                    GUIMain.loggerService.log(Level.WARNING,"Number of channels not supported")
                }
            }
            else if (pixelType == "Float") {
                if (numChannels == 1) {
                    if (coreBytesPerPixel != 4) {
                        GUIMain.loggerService.log(Level.WARNING,"Core format clashes with cfg")
                    }
                    else {
                        //32bit float
                        dataID++
                        return STRIMMPixelBuffer(
                            pix,
                            w,
                            h,
                            pixelType,
                            numChannels,
                            time,
                            label,
                            dataID,
                            1)
                    }
                }
                else {
                    GUIMain.loggerService.log(Level.WARNING,"Number of channels not supported")
                }
            }
            else {
                GUIMain.loggerService.log(Level.WARNING,"Pixel type not supported")
            }
        }

        stop = true //The stop flag is used to stop this method from spinnng infinitely if there are no more images in the buffer
        return STRIMMPixelBuffer(null, 0, 0, "", 0, -1.0, "", dataID, 0)
    }

    override fun preStart() {
        if (!bSnapped) {
            StartAcquisition()
        }
    }

    override fun postStop() {
        if (!bSnapped) {
        }
        core!!.reset()
        Thread.sleep(2000)  //TODO does it need this?
    }

    fun loadCfg() {
        if (source.sourceCfg != "") {
            properties = hashMapOf<String, String>()
            var r: List<Array<String>>? = null
            try {
                val reader = CSVReader(FileReader(source.sourceCfg))
                r = reader.readAll()
                for (props in r!!) {
                    properties[props[0]] = props[1]
                }

            }
            catch (ex: Exception) {
                GUIMain.loggerService.log(Level.SEVERE, "Error in reading config ${source.sourceCfg} in MMCameraSource. Message: ${ex.message}")
                GUIMain.loggerService.log(Level.SEVERE, ex.stackTrace)
            }
        }
    }

    private fun StartAcquisition() {
        val memSize: Long = core!!.bytesPerPixel * core!!.imageHeight * core!!.imageWidth * properties["framesInCircularBuffer"]!!.toLong() / 1024 / 1024
        try {
            //If error here then problems caused by setCircularBufferMemoryFootprint(), so reduce the number of frames on circular buffer or use the default -1.
            GUIMain.loggerService.log(Level.INFO, "Size of circular buffer=$memSize mb")
            if (memSize > 0) core!!.circularBufferMemoryFootprint = memSize
            core!!.initializeCircularBuffer() //circular buffer
            core!!.startContinuousSequenceAcquisition(0.0)
        }
        catch (ex: Exception) {
            GUIMain.loggerService.log(Level.INFO, "Error initializing circular buffer and starting sequence. Message ${ex.message}")
            GUIMain.loggerService.log(Level.INFO, ex.stackTrace)
        }
    }
}