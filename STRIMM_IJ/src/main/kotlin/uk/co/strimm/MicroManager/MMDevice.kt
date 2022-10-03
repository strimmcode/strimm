package uk.co.strimm.MicroManager

import mmcorej.CMMCore
import mmcorej.DeviceType
import mmcorej.PropertyType
import uk.co.strimm.gui.GUIMain
import uk.co.strimm.services.MMCoreService
import java.io.Closeable
import java.util.logging.Level

private fun rn(name: String) = name + java.util.UUID.randomUUID().toString()

sealed class MMDevice(val devName: String, val library: String, var label: String, val loaded: Boolean) : Closeable {
    var core = MMCoreService.core

    fun setCameraCore(cor: CMMCore) {
        core = cor
    }

    fun loadDevice() {
        core.loadDevice(label, library, devName)
    }

    fun loadConfiguration(szConfig: String) {
        core.loadSystemConfiguration(szConfig)
    }

    override fun close() {
        core.unloadDevice(label)
    }

    fun getProperties() =
        core.getDevicePropertyNames(label)
                .map { propName ->
                    val type = core.getPropertyType(label, propName)
                    when (type) {
                        PropertyType.Float -> MMFloatProperty(label, propName)
                        PropertyType.Integer -> MMIntProperty(label, propName)
                        PropertyType.String -> MMStringProperty(label, propName)
                        else -> MMUnknownProperty(label, propName)
                    }
                }

    fun initialise() {
        try {
            core.initializeDevice(label)

        }
        catch (ex: Exception) {
            GUIMain.loggerService.log(Level.SEVERE, "Error initialising camera device. Message: ${ex.message}")
            GUIMain.loggerService.log(Level.SEVERE, ex.stackTrace)
        }
    }

    fun isBusy() = core.deviceBusy(label)
}

class MMAutoFocusDevice(name: String, library: String, label: String = rn(name), loaded: Boolean = false)
    : MMDevice(name, library, label, loaded) {
    companion object {
        @JvmStatic
        val deviceType: DeviceType = DeviceType.AutoFocusDevice
    }
}

class MMCameraDevice(name: String, library: String, label: String = rn(name), loaded: Boolean = false)
    : MMDevice(name, library, label, loaded) {
    constructor(cam: uk.co.strimm.services.Camera?) : this(cam!!.name, cam.library, cam.label, false) {
        setCameraCore(cam.core)
    }

    enum class MMBytesPerPixel(val bytesPerPixel: Long) {
        Bit8(1),
        Bit16(2),
        Bit32(4),
        Unknown(-1)
    }

    private fun <T> tempActivate(func: () -> T): T =
        if (MMCoreService.core.cameraDevice == label) func()
        else MMCoreService.core.cameraDevice
                .let { prevCam ->
                    MMCoreService.core.cameraDevice = label

                    func().apply {
                        MMCoreService.core.cameraDevice = prevCam
                    }
                }

    private var live = false
    private var previewMode = false
    private var acquisitionCount = 0
    private var acquisitionInterval = 0.0
    private var MMMetadataTagName = "ElapsedTime-ms"

    var isActive = false

    val isImageAvailable
        get() = !isBusy() && MMCoreService.core.remainingImageCount > 0

    fun startLivePreview(interval: Double) {
    }

    fun startLiveAcquisition(interval: Double) {
        //        core.snapImage();
//            TaggedImage im = core.getTaggedImage();
//            short[] data = (short[])im.pix; //worked at 70 fps
//        val numFramesInBuffer = 20
//        val memSize = core.bytesPerPixel * core.imageHeight * core.imageWidth * numFramesInBuffer / 1024 / 1024
//        core.circularBufferMemoryFootprint = memSize
//        core.initializeCircularBuffer() //circular buffer
//        core.startContinuousSequenceAcquisition(0.0) //must be
    }

    fun stopLive() {
        // core.stopSequenceAcquisition()
        live = false
        GUIMain.expStartStopButton.isSelected = false
    }

    fun snapImage(): Pair<Any, Double> {
//        //TW this is not used
//        if(previewMode) {
//            while(core.remainingImageCount < 1){}
////TW this could be interesting to get the timestamps if available from the camera
//            //but does it have to be done this way?
//            val timeStampString = core.lastTaggedImage.tags[MMMetadataTagName] as String
//            return Pair(core.lastTaggedImage.pix, timeStampString.toDouble())
//        }
//        else{
//            //Wait a little so the buffer has enough images from an acquisition
//            while(MMCoreService.core.remainingImageCount < 1){}
//
//            /*
//                As the buffer has been cleared before the start of sequence acquisition, we can safely assume that
//                any images in the buffer are from the acquisition, we can now "pop" them from the buffer
//             */
//            val pop = MMCoreService.core.popNextTaggedImage()
//            val timeStampString = pop.tags[MMMetadataTagName] as String
//
//            acquisitionCount--
//
//            if(acquisitionCount < 1){
//                GUIMain.loggerService.log(Level.INFO, "Stopping sequence acquisition, starting continuous acquisition (preview mode)")
//                stopLive()
//                startLivePreview(acquisitionInterval)
//            }
//
//            return Pair(pop.pix, timeStampString.toDouble())
//        }
        return Pair(0, 0.0)
    }

    val imageWidth get() = core.imageWidth
    val imageHeight get() = core.imageHeight
    val numberOfChannels get() = core.numberOfCameraChannels
    val bytesPerPixel
        get() =
            MMBytesPerPixel.values().find { core.bytesPerPixel == it.bytesPerPixel } ?: MMBytesPerPixel.Unknown
    val bitDepth get() = core.imageBitDepth

    var exposure
        get() = core.exposure
        set(value) {
            core.exposure = value
            core.setExposure(label, value)
        }

    companion object {
        @JvmStatic
        val deviceType: DeviceType = DeviceType.CameraDevice
    }

}

class MMGalvoDevice(name: String, library: String, label: String = rn(name), loaded: Boolean = false)
    : MMDevice(name, library, label, loaded) {
    companion object {
        @JvmStatic
        val deviceType: DeviceType = DeviceType.GalvoDevice
    }
}

class MMSLMDevice(name: String, library: String, label: String = rn(name), loaded: Boolean = false)
    : MMDevice(name, library, label, loaded) {
    companion object {
        @JvmStatic
        val deviceType: DeviceType = DeviceType.SLMDevice
    }
}

class MMSerialDevice(name: String, library: String, label: String = rn(name), loaded: Boolean = false)
    : MMDevice(name, library, label, loaded) {
    companion object {
        @JvmStatic
        val deviceType: DeviceType = DeviceType.SerialDevice
    }
}

class MMShutterDevice(name: String, library: String, label: String = rn(name), loaded: Boolean = false)
    : MMDevice(name, library, label, loaded) {
    companion object {
        @JvmStatic
        val deviceType: DeviceType = DeviceType.ShutterDevice
    }
}

class MMSignalIODevice(name: String, library: String, label: String = rn(name), loaded: Boolean = false)
    : MMDevice(name, library, label, loaded) {
    companion object {
        @JvmStatic
        val deviceType: DeviceType = DeviceType.SignalIODevice
    }
}

class MMStageDevice(name: String, library: String, label: String = rn(name), loaded: Boolean = false)
    : MMDevice(name, library, label, loaded) {
    companion object {
        @JvmStatic
        val deviceType: DeviceType = DeviceType.StageDevice
    }
}

class MMStateDevice(name: String, library: String, label: String = rn(name), loaded: Boolean = false)
    : MMDevice(name, library, label, loaded) {
    companion object {
        @JvmStatic
        val deviceType: DeviceType = DeviceType.StageDevice
    }
}

class MMXYStageDevice(name: String, library: String, label: String = rn(name), loaded: Boolean = false)
    : MMDevice(name, library, label, loaded) {
    companion object {
        @JvmStatic
        val deviceType: DeviceType = DeviceType.XYStageDevice
    }
}

class MMAnyDevice(name: String, library: String, label: String = rn(name), loaded: Boolean = false)
    : MMDevice(name, library, label, loaded) {
    companion object {
        @JvmStatic
        val deviceType: DeviceType = DeviceType.AnyType
    }
}

class MMCoreDevice(name: String, library: String, label: String = rn(name), loaded: Boolean = false)
    : MMDevice(name, library, label, loaded) {
    companion object {
        @JvmStatic
        val deviceType: DeviceType = DeviceType.CoreDevice
    }
}

class MMGenericDevice(name: String, library: String, label: String = rn(name), loaded: Boolean = false)
    : MMDevice(name, library, label, loaded) {
    companion object {
        @JvmStatic
        val deviceType: DeviceType = DeviceType.GenericDevice
    }
}

class MMHubDevice(name: String, library: String, label: String = rn(name), loaded: Boolean = false)
    : MMDevice(name, library, label, loaded) {
    companion object {
        @JvmStatic
        val deviceType: DeviceType = DeviceType.HubDevice
    }
}

class MMImageProcessorDevice(name: String, library: String, label: String = rn(name), loaded: Boolean = false)
    : MMDevice(name, library, label, loaded) {
    companion object {
        @JvmStatic
        val deviceType: DeviceType = DeviceType.ImageProcessorDevice
    }
}

class MMMagnifierDevice(name: String, library: String, label: String = rn(name), loaded: Boolean = false)
    : MMDevice(name, library, label, loaded) {
    companion object {
        @JvmStatic
        val deviceType: DeviceType = DeviceType.MagnifierDevice
    }
}

class MMUnknownDevice(name: String, library: String, label: String = rn(name), loaded: Boolean = false)
    : MMDevice(name, library, label, loaded)
