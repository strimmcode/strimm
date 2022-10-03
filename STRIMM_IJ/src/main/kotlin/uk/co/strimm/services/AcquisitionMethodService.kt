package uk.co.strimm.services

import net.imagej.ImageJService
import net.imagej.overlay.EllipseOverlay
import net.imagej.overlay.Overlay
import net.imagej.overlay.PolygonOverlay
import net.imagej.overlay.RectangleOverlay
import net.imglib2.Cursor
import net.imglib2.img.array.ArrayImg
import net.imglib2.img.array.ArrayImgs
import net.imglib2.roi.Masks
import net.imglib2.roi.Regions
import net.imglib2.roi.geom.GeomMasks
import net.imglib2.type.numeric.complex.AbstractComplexType
import net.imglib2.type.numeric.integer.UnsignedByteType
import net.imglib2.type.numeric.integer.UnsignedShortType
import net.imglib2.type.numeric.real.FloatType
import net.imglib2.util.Intervals
import net.imglib2.view.Views
import org.scijava.plugin.Plugin
import org.scijava.service.AbstractService
import org.scijava.service.Service
import uk.co.strimm.ExperimentConstants
import uk.co.strimm.ResizeValues
import uk.co.strimm.STRIMMImage
import uk.co.strimm.TraceData
import uk.co.strimm.experiment.Source
import uk.co.strimm.gui.GUIMain
import java.io.File
import java.util.*
//import java.util.List
import java.util.logging.Level

@Plugin(type = Service::class)
class AcquisitionMethodService : AbstractService(), ImageJService {
    var bCamerasAcquire = false
    var bAquisitionProceed = false
    var configuredCameras = hashMapOf<String, Camera>()
    var acquisitionMethods: ArrayList<AcquisitionMethod> = arrayListOf()

    init {
        populateConfiguredCameras()
        registerDefaultMethods()
        registerCustomMethods()
    }

    private fun registerDefaultMethods() {
        //Image methods
        val averageROIMethod = AverageROI(ExperimentConstants.Acquisition.AVERAGE_ROI_METHOD_NAME, "Average the pixel intensity any ROIs (overlays) on a given image")
        val snapImageMethod = SnapImage("ConfiguredCamera", "")
        val generateImageMethod = GenerateImage(ExperimentConstants.Acquisition.GENERATE_IMAGE_METHOD_NAME, "Generate an image")

        //Trace methods
        val getTraceDataMethod = GetTraceData(ExperimentConstants.Acquisition.GET_TRACE_DATA_METHOD_NAME, "Get data from a trace source or flow")
        val getTraceDataMethodNIDAQ = GetTraceDataNIDAQ("Trace Data Method NIDAQ", "Get data from a trace source or flow")
        val getTraceDataMethodKeyboard = GetTraceDataKeyboard("Trace Data Method Keyboard", "Get data from a trace source or flow")
        val getGetTraceDataKeyboardA = GetTraceDataKeyboardA("KeyboardA", "")
        val getTraceDataMethodConstantTraceSource = GetTraceConstantTraceSource("ConstantTraceSource", "")
        val getTraceDataRandomTraceSource = GetTraceRandomTraceSource("RandomTraceSource", "")
        val getTraceDataMethodConstantVectorSource = GetTraceConstantVectorSource("ConstantVectorSource", "")
        val getTraceDataMethodSineWaveSource = GetTraceSineWaveSource("SineWaveSource", "")
        val getTraceDataMethodSquareWaveSource = GetTraceSquareWaveSource("SquareWaveSource", "")

        registerAcquisitionMethod(averageROIMethod)
        registerAcquisitionMethod(snapImageMethod)
        registerAcquisitionMethod(generateImageMethod)
        registerAcquisitionMethod(getTraceDataMethod)
        registerAcquisitionMethod(getTraceDataMethodNIDAQ)
        registerAcquisitionMethod(getTraceDataMethodKeyboard)
        registerAcquisitionMethod(getGetTraceDataKeyboardA)
        registerAcquisitionMethod(getTraceDataMethodConstantTraceSource)
        registerAcquisitionMethod(getTraceDataMethodConstantVectorSource)
        registerAcquisitionMethod(getTraceDataMethodSineWaveSource)
        registerAcquisitionMethod(getTraceDataMethodSquareWaveSource)
        registerAcquisitionMethod(getTraceDataRandomTraceSource)
    }

    private fun registerCustomMethods() {
        //TODO load classes for custom methods (as plugins) from a specified folder
    }

    private fun populateConfiguredCameras() {
        val pathnames: Array<String>
        val files = File("DeviceAdapters/CameraMMConfigs")
        pathnames = files.list()
        for (pathname in pathnames) {
            configuredCameras[pathname] = CameraConfigured(pathname)
        }
    }

    fun GetCamera(szConfig: String): Camera? {
        return configuredCameras[szConfig]
    }

    interface AcquisitionMethod {
        var name: String
        var description: String
        fun runMethod(vararg inputs: Any): Any
    }

    abstract class TraceMethod : AcquisitionMethod {
        override fun runMethod(vararg inputs: Any): ArrayList<TraceData> {
            return arrayListOf(TraceData(Pair(null, 0.0), 0))//TODO dummy
        }
    }

    abstract class ImageMethod : AcquisitionMethod {
        override fun runMethod(vararg inputs: Any): STRIMMImage {
            return STRIMMImage("", null, 0, 0, 0, 0) //TODO dummy
        }
    }

    /**
     * Calculate the average pixel intensity for any combination of ROIs (overlays) on an image
     * @param name The name used to identify this method. Must be unique
     * @param description A short description of what the method does
     */
    private class AverageROI(override var name: String, override var description: String) : TraceMethod() {
        private var clockCounter = 0.0
        /**
         * The top level method that is actually run for this acquisition method
         * @param inputs inputs[0] is the STRIMMImage object. Inputs [1] is the name of the Akka flow (@see ExperimentStream)
         */
        override fun runMethod(vararg inputs: Any): ArrayList<TraceData> {
            while (!GUIMain.acquisitionMethodService.bAquisitionProceed) {
                Thread.sleep(50) //TODO state why this is needed
            }
            val image = inputs[0] as STRIMMImage
            //The flowName is recorded in rois
            val flowName = inputs[1] as String

            //TODO why are both overlaysForDevice and routedROIsForDevice needed? What is the difference between them?
            val overlaysForDevice = GUIMain.actorService.routedRoiOverlays.filter { x -> x.value.toLowerCase() == flowName.toLowerCase() }
            //routedRoiList - loaded at runtime
            val routedROIsForDevice = GUIMain.actorService.routedRoiList.filter { x -> x.value.second.toLowerCase() == flowName.toLowerCase() }

            //This array of Overlays will be the json rois and the the user chosen
            val arrayListToReturn = arrayListOf<TraceData>()
            if (overlaysForDevice.isNotEmpty()) {
                for (deviceOverlay in overlaysForDevice) {
                    arrayListToReturn.add(averageROI(deviceOverlay.key, image))
                }
            }

            if (routedROIsForDevice.isNotEmpty()) {
                for (deviceOverlay in routedROIsForDevice.keys.toList()) {
                    arrayListToReturn.add(averageROI(deviceOverlay, image))
                }
            }
            return arrayListToReturn
        }

        /**
         * Take an ImageJ overlay and iterate through every pixel in this overlay. Take all the pixel values and then
         * calculate the average.
         * @param roi The ImageJ overlay
         * @param image The snapped image from the camera feed
         * @return A TraceData object containing the averaged value and the ROI object
         */
        private fun averageROI(roi: Overlay, image: STRIMMImage): TraceData {
            //Image knows the source camera name via image.sourceCamera
            if (image.pix == null) {
                return TraceData(Pair(roi, 0.0), image.timeAcquired)
            }

            val x: Double
            val y: Double
            val w: Double
            val h: Double
            var viewCursor: Cursor<out AbstractComplexType<*>>? = null

            when (roi) {
                is EllipseOverlay -> {
                    try {
                        x = roi.getOrigin(0)
                        y = roi.getOrigin(1)
                        w = roi.getRadius(0)
                        h = roi.getRadius(1)
                        val imgSizeFull = Pair(image.w, image.h)
                        //Masks and interval lines below taken from https://forum.image.sc/t/examples-of-usage-of-imglib2-roi/22855/14

                        //TODO resize functionality only seems to be applied to FloatArrays. Need to unify resizing for all data types
                        when (image.pix) {
                            is ByteArray -> {
                                //The image provided in this method will come in full size, check if a resize is needed before beginning the ROI calculation
                                val arrayImg: ArrayImg<UnsignedByteType, net.imglib2.img.basictypeaccess.array.ByteArray> = ArrayImgs.unsignedBytes(
                                        image.pix,
                                        imgSizeFull.first.toLong(),
                                        imgSizeFull.second.toLong())

                                //Proceed with iterating over a cursor for the ROI
                                val ellipsoidMask = GeomMasks.closedEllipsoid(doubleArrayOf(x, y), doubleArrayOf(w, h))
                                val ellipsoidInterval = Intervals.largestContainedInterval(ellipsoidMask)
                                val maskRRA = Masks.toRealRandomAccessible(ellipsoidMask)
                                val interval = Views.interval(Views.raster(maskRRA), ellipsoidInterval)
                                val iterableRegion = Regions.iterable(interval)
                                viewCursor = Regions.sample(iterableRegion, arrayImg).cursor()
                            }
                            is ShortArray -> {
                                //The image provided in this method will come in full size, check if a resize is needed before beginning the ROI calculation
                                val arrayImg: ArrayImg<UnsignedShortType, net.imglib2.img.basictypeaccess.array.ShortArray> = ArrayImgs.unsignedShorts(
                                        image.pix,
                                        imgSizeFull.first.toLong(),
                                        imgSizeFull.second.toLong())

                                //Proceed with iterating over a cursor for the ROI
                                val ellipsoidMask = GeomMasks.closedEllipsoid(doubleArrayOf(x, y), doubleArrayOf(w, h))
                                val ellipsoidInterval = Intervals.largestContainedInterval(ellipsoidMask)
                                val maskRRA = Masks.toRealRandomAccessible(ellipsoidMask)
                                val interval = Views.interval(Views.raster(maskRRA), ellipsoidInterval)
                                val iterableRegion = Regions.iterable(interval)
                                viewCursor = Regions.sample(iterableRegion, arrayImg).cursor()
                            }
                            is FloatArray -> {
                                //The image provided in this method will come in full size, check if a resize is needed before beginning the ROI calculation
                                val arrayImg: ArrayImg<FloatType, net.imglib2.img.basictypeaccess.array.FloatArray>
                                arrayImg = if (image.sourceCamera in GUIMain.strimmUIService.cameraViewSizeList.keys) {
                                    val resizeVals = GUIMain.strimmUIService.cameraViewSizeList[image.sourceCamera]!!
                                    val resizedImage =
                                            GUIMain.acquisitionMethodService.getImageSubsetFloatArray(image, resizeVals)
                                    ArrayImgs.floats(resizedImage, resizeVals.w!!.toLong(), resizeVals.h!!.toLong())
                                }
                                else {
                                    ArrayImgs.floats(image.pix, imgSizeFull.first.toLong(), imgSizeFull.second.toLong())
                                }

                                //Proceed with iterating over a cursor for the ROI
                                val ellipsoidMask = GeomMasks.closedEllipsoid(doubleArrayOf(x, y), doubleArrayOf(w, h))
                                val ellipsoidInterval = Intervals.largestContainedInterval(ellipsoidMask)
                                val maskRRA = Masks.toRealRandomAccessible(ellipsoidMask)
                                val interval = Views.interval(Views.raster(maskRRA), ellipsoidInterval)
                                val iterableRegion = Regions.iterable(interval)
                                viewCursor = Regions.sample(iterableRegion, arrayImg).cursor()
                            }
                        }
                    }
                    catch (ex: Exception) {
                        GUIMain.loggerService.log(Level.SEVERE, "Could not read ellipse overlay data. Message: " + ex.message)
                        GUIMain.loggerService.log(Level.SEVERE, ex.stackTrace)
                    }
                }
                is RectangleOverlay -> {
                    try {
                        x = roi.getOrigin(0)
                        y = roi.getOrigin(1)
                        w = roi.getExtent(0)
                        h = roi.getExtent(1)
                        val imgSizeFull = Pair(image.w, image.h)

                        //Masks and interval lines below taken from https://forum.image.sc/t/examples-of-usage-of-imglib2-roi/22855/14
                        when (image.pix) {
                            is ByteArray -> {
                                val arrayImg: ArrayImg<UnsignedByteType, net.imglib2.img.basictypeaccess.array.ByteArray> = ArrayImgs.unsignedBytes(
                                        image.pix,
                                        imgSizeFull.first.toLong(),
                                        imgSizeFull.second.toLong())

                                //Proceed with iterating over a cursor for the ROI
                                val maskExtentX = (x + w) - 1
                                val maskExtentY = (h + y) - 1
                                val boxMask =
                                        GeomMasks.closedBox(doubleArrayOf(x, y), doubleArrayOf(maskExtentX, maskExtentY))
                                val boxInterval = Intervals.largestContainedInterval(boxMask)
                                val maskRRA = Masks.toRealRandomAccessible(boxMask)
                                val interval = Views.interval(Views.raster(maskRRA), boxInterval)
                                val iterableRegion = Regions.iterable(interval)
                                viewCursor = Regions.sample(iterableRegion, arrayImg).cursor()
                            }
                            is ShortArray -> {
                                //The image provided in this method will come in full size, check if a resize is needed before beginning the ROI calculation
                                val arrayImg: ArrayImg<UnsignedShortType, net.imglib2.img.basictypeaccess.array.ShortArray> = ArrayImgs.unsignedShorts(
                                        image.pix,
                                        imgSizeFull.first.toLong(),
                                        imgSizeFull.second.toLong())

                                //Proceed with iterating over a cursor for the ROI
                                val maskExtentX = (x + w) - 1
                                val maskExtentY = (h + y) - 1
                                val boxMask =
                                        GeomMasks.closedBox(doubleArrayOf(x, y), doubleArrayOf(maskExtentX, maskExtentY))
                                val boxInterval = Intervals.largestContainedInterval(boxMask)
                                val maskRRA = Masks.toRealRandomAccessible(boxMask)
                                val interval = Views.interval(Views.raster(maskRRA), boxInterval)
                                val iterableRegion = Regions.iterable(interval)
                                viewCursor = Regions.sample(iterableRegion, arrayImg).cursor()
                            }
                            is FloatArray -> {
                                //20_3_22__TODO
                                //The image provided in this method will come in full size, check if a resize is needed before beginning the ROI calculation
                                val arrayImg: ArrayImg<FloatType, net.imglib2.img.basictypeaccess.array.FloatArray> = ArrayImgs.floats(
                                        image.pix,
                                        imgSizeFull.first.toLong(),
                                        imgSizeFull.second.toLong())

                                //Proceed with iterating over a cursor for the ROI
                                val maskExtentX = (x + w) - 1
                                val maskExtentY = (h + y) - 1
                                val boxMask =
                                        GeomMasks.closedBox(doubleArrayOf(x, y), doubleArrayOf(maskExtentX, maskExtentY))
                                val boxInterval = Intervals.largestContainedInterval(boxMask)
                                val maskRRA = Masks.toRealRandomAccessible(boxMask)
                                val interval = Views.interval(Views.raster(maskRRA), boxInterval)
                                val iterableRegion = Regions.iterable(interval)
                                viewCursor = Regions.sample(iterableRegion, arrayImg).cursor()
                            }
                        }
                    }
                    catch (ex: Exception) {
                        GUIMain.loggerService.log(Level.SEVERE, "Could not read rectangle overlay data. Message: " + ex.message)
                        GUIMain.loggerService.log(Level.SEVERE, ex.stackTrace)
                    }
                }
                is PolygonOverlay -> {
                    //TODO this is a bit more complicated
                }
            }

            val average = calculateAverage(viewCursor)
            return TraceData(Pair(roi, average), image.timeAcquired)
        }

        /**
         * Calculate the average of all the pixels in an ROI.
         * @param viewCursor The object representing a cursor that will iterate over all the pixels within the ROI
         * @return The average pixel intensity over the ROI. If pixel intensity is -999 then something has gone wrong
         */
        private fun calculateAverage(viewCursor: Cursor<out AbstractComplexType<*>>?): Double {
            var average = -999.0

            try {
                var total = 0.0
                var totalPixels = 0

                while (viewCursor!!.hasNext()) {
                    //.next() does a .fwd() and a .get()
                    val value = viewCursor.next()
                    val pixelValue = value.getRealDouble()
                    total += pixelValue
                    totalPixels++
                }

                average = (total / totalPixels)

            } catch (ex: Exception) {
                GUIMain.loggerService.log(Level.SEVERE, "Could not iterate through overlay data. Message: " + ex.message)
                GUIMain.loggerService.log(Level.SEVERE, ex.stackTrace)
            }

            return average
        }
    }

    /**
     * Get the next image from a camera device
     * @param name The name used to identify this method. Must be unique
     * @param description A short description of what the method does
     */
    private class SnapImage(override var name: String, override var description: String) : ImageMethod() {
        override fun runMethod(vararg inputs: Any): STRIMMImage {
            while (!GUIMain.acquisitionMethodService.bAquisitionProceed) {
                Thread.sleep(50)
            }
            val source = inputs[0] as Source
            val camToUse = source.camera //polymorphically runs the camera :: retiga etc
            val strimmimage = camToUse?.run()
            return strimmimage ?: STRIMMImage("", null, GUIMain.softwareTimerService.getTime(), 0, 0, 0)
        }
    }

    /**
     * Generate an image not necessarily from a camera device
     * @param name The name used to identify this method. Must be unique
     * @param description A short description of what the method does
     */
    private class GenerateImage(override var name: String, override var description: String) : ImageMethod() {
        override fun runMethod(vararg inputs: Any): STRIMMImage {
            //TODO Dummy for now. When might we use such a method?
            return STRIMMImage("", "", 0.0, 0, 0, 0)
        }
    }

    /**
     * Get trace data from a trace device
     * @param name The name used to identify this method. Must be unique
     * @param description A short description of what the method does
     */
    private class GetTraceData(override var name: String, override var description: String) : TraceMethod() {
        private var clockCounter = 0.0
        override fun runMethod(vararg inputs: Any): ArrayList<TraceData> {
            val dummyOverlay = inputs[0] as Overlay
            val intervalSecs = inputs[1] as Double
            val traceDataList = arrayListOf<TraceData>()

            //TODO this this comment still accurate?
            /**
             * NOTE - At the moment the acquisition method is in a method called "storeTraceData" in the class
             * "AnalogueDataStream" in the TimerService. Need to make sure this method uses that or vice-versa in some
             * way
             */
//            for(i in 0 until 100) {
//            try {
//                    val traceVal = GUIMain.experimentService.analogueBuffer.removeFirst()
//                    traceDataList.add(TraceData(Pair(dummyOverlay, traceVal), clockCounter*1000))
//            } catch (ex: Exception) {
                //println("Error. buffer size is ${GUIMain.experimentService.analogueBuffer.size}")
//            }

            clockCounter += intervalSecs
//            }
            return traceDataList
        }
    }

    /**
     * Get trace data from a trace device
     * @param name The name used to identify this method. Must be unique
     * @param description A short description of what the method does
     */
    private class GetTraceDataNIDAQ(override var name: String, override var description: String) : AcquisitionMethodService.TraceMethod() {
        var bFirst = true
        var dataAI = DoubleArray(1)
        var dataAO = DoubleArray(1)
        var dataDI = IntArray(1)
        var dataDO = IntArray(1)
        var pTimes = DoubleArray(1)
        var clockCounter = 0.0
        override fun runMethod(vararg inputs: Any): ArrayList<TraceData> {
            if (GUIMain.experimentService.experimentStream.expConfig.isGlobalStart) {
                while (!GUIMain.protocolService.bGlobalSourceStartTrigger) {
                    val bKeyPressed =
                            GUIMain.protocolService.jdaq.GetKeyState(GUIMain.experimentService.experimentStream.expConfig.GlobalStartVirtualCode)
                    if (bKeyPressed) {
                        GUIMain.protocolService.bGlobalSourceStartTrigger = true
                        GUIMain.softwareTimerService.setFirstTimeMeasurement() //mix up with time here TIME_CONFUSION
                        //this is used by experiment duration
                    }

                    //TODO explain why sleep is needed
                    Thread.sleep(100)
                }
            }

            val dummyAO = inputs[0] as MutableList<Overlay>
            val dummyAI = inputs[1] as MutableList<Overlay>
            val dummyDO = inputs[2] as MutableList<Overlay>
            val dummyDI = inputs[3] as MutableList<Overlay>
            val src = inputs[4] as Source

            val bKeyboardSnapEnabled = src.isKeyboardSnapEnabled
            val snapVirtualCode = src.SnapVirtualCode

            if (bKeyboardSnapEnabled) {
                var bKeyPressed = GUIMain.protocolService.jdaq.GetKeyState(snapVirtualCode)
                while (!bKeyPressed) {
                    Thread.sleep(50) //TODO explain why sleep is needed
                    bKeyPressed = GUIMain.protocolService.jdaq.GetKeyState(snapVirtualCode)
                }
            }

            src.isBusy = true

            val numAOChannels = GUIMain.protocolService.GetNumChannels(0)
            val numAIChannels = GUIMain.protocolService.GetNumChannels(1)
            val numDOChannels = GUIMain.protocolService.GetNumChannels(2)
            val numDIChannels = GUIMain.protocolService.GetNumChannels(3)

            var numSamples = GUIMain.protocolService.GetNextNumSamples()
            if (bFirst) {
                numSamples = GUIMain.protocolService.GetNextNumSamples()
                dataAI = DoubleArray(numSamples * numAIChannels)
                dataDI = IntArray(numSamples * numDIChannels)
                dataAO = DoubleArray(numSamples * numAOChannels)
                dataDO = IntArray(numSamples * numDOChannels)
                pTimes = DoubleArray(numSamples)
                bFirst = false

            }

            if (GUIMain.acquisitionMethodService.bAquisitionProceed) {
                GUIMain.protocolService.RunNext(pTimes, dataAO, dataAI, dataDO, dataDI);
            }

            //make up data here for dataAI, dataAO, dataDI, dataDO in order to test the graph drawing
            val traceDataList = arrayListOf<TraceData>()

            //Create dummy overlays
            var numAO = 0
            var numAI = 0
            var numDO = 0
            var numDI = 0
            val timeStart = GUIMain.experimentService.experimentStream.timeStart
            for (sample in 0 until numSamples) {
                //ProtocolManager in NIDAQ C++ code always responds in sec when describing times
                //as this is likely very well timed use the NIDAQ nominal timing
                clockCounter = pTimes[sample] - timeStart
                for (AOChannel in 0 until numAOChannels) {  // until does not include the last number
                    traceDataList.add(TraceData(Pair(dummyAO[AOChannel], dataAO[numAO]), clockCounter))
                    numAO++
                }
                for (AIChannel in 0 until numAIChannels) {
                    traceDataList.add(TraceData(Pair(dummyAI[AIChannel], dataAI[numAI]), clockCounter))
                    numAI++
                }
                for (DOChannel in 0 until numDOChannels) {
                    traceDataList.add(TraceData(Pair(dummyDO[DOChannel], dataDO[numDO].toDouble()), clockCounter))
                    numDO++
                }
                for (DIChannel in 0 until numDIChannels) {
                    traceDataList.add(TraceData(Pair(dummyDI[DIChannel], dataDI[numDI].toDouble()), clockCounter))
                    numDI++
                }
            }

            src.isBusy = false
            return traceDataList
        }
    }


    private class GetTraceDataKeyboard(override var name: String, override var description: String) : TraceMethod() {
        var clockCounter = 0.0

        override fun runMethod(vararg inputs: Any): ArrayList<TraceData> {
            val keysToPoll = inputs[0] as ArrayList<Int>
            val dummyOverlays = inputs[1] as MutableList<Overlay>
            val traceDataList = arrayListOf<TraceData>()

            for (f in 0..3) { //TODO magic numbers, what does this range represent?
                val keysResults = arrayListOf<Double>()
                for (key in keysToPoll) {
                    val keyState: Boolean = GUIMain.protocolService.jdaq.GetKeyState(key)
                    keysResults.add(if (keyState) 7.0 else 0.0)
                }

                val timeStamp = GUIMain.softwareTimerService.getTime()

                for (ff in 0 until keysToPoll.size) {
                    traceDataList.add(TraceData(Pair(dummyOverlays[ff], keysResults[ff]), timeStamp))
                }
                clockCounter++
            }

            try {
                //TODO is this required?
                Thread.sleep(333)
            } catch (e: InterruptedException) {
                //e.printStackTrace()
            }

            return traceDataList
        }
    }

    private class GetTraceDataKeyboardA(override var name: String, override var description: String) : TraceMethod() {
        var clockCounter = 0.0

        override fun runMethod(vararg inputs: Any): ArrayList<TraceData> {
            while (!GUIMain.acquisitionMethodService.bAquisitionProceed) { //spin
            }

            val key = inputs[0] as Int
            val dummyOverlay = inputs[1] as Overlay
            val pollPeriod = inputs[2] as Int
            val traceDataList = arrayListOf<TraceData>()
            val b: Boolean = GUIMain.protocolService.jdaq.GetKeyState(key)
            val keysResult = if (b) 100.0 else 0.0 //TODO magic numbers, what do this represent?
            val timeStamp = GUIMain.softwareTimerService.getTime()
            traceDataList.add(TraceData(Pair(dummyOverlay, keysResult), timeStamp))
            clockCounter++
            try {
                Thread.sleep(pollPeriod.toLong())
            } catch (e: InterruptedException) {
                //e.printStackTrace()
            }

            return traceDataList
        }
    }

    private class GetTraceConstantTraceSource(override var name: String, override var description: String) : TraceMethod() {
        override fun runMethod(vararg inputs: Any): ArrayList<TraceData> {
            val dummyOverlay = inputs[0] as Overlay
            val src = inputs[1] as Source
            val value = src.param1
            val timeStamp = GUIMain.softwareTimerService.getTime()
            val traceDataList = arrayListOf<TraceData>()
            val traceData = TraceData(Pair(dummyOverlay, value), timeStamp)
            traceDataList.add(traceData)

            //delay to control the rate at which it sends values into the akka graph
            try {
                Thread.sleep(src.param2.toLong()) //param2 was the time delay
            } catch (e: InterruptedException) {
                //e.printStackTrace()
            }
            return traceDataList
        }
    }

    private class GetTraceRandomTraceSource(override var name: String, override var description: String) : TraceMethod() {
        override fun runMethod(vararg inputs: Any): ArrayList<TraceData> {
            val dummyOverlay = inputs[0] as Overlay //the overlay is the ID of this dataset and is used for plotting all lines etc have the same ID
            val src = inputs[1] as Source //the source should contain all of the data needed to get the output value
            val amplitude = src.param1
            val interval = src.param2
            val timeStamp = GUIMain.softwareTimerService.getTime() //the time point
            val traceDataList = arrayListOf<TraceData>() //we have a list of TraceData because we could have lots of data series
            val rand = Random()
            val value = rand.nextInt(amplitude.toInt())
            val traceData = TraceData(Pair(dummyOverlay, value.toDouble()), timeStamp)

            traceDataList.add(traceData)

            //delay to control the rate at which it sends values into the akka graph
            try {
                Thread.sleep(interval.toLong()) //param2 was the time delay
            } catch (e: InterruptedException) {
                //e.printStackTrace()
            }
            return traceDataList
        }
    }

    private class GetTraceConstantVectorSource(override var name: String, override var description: String) : TraceMethod() {
        override fun runMethod(vararg inputs: Any): ArrayList<TraceData> {
            val overlays = inputs[0] as List<Overlay>
            val src = inputs[1] as Source
            val value1 = src.param1
            val value2 = src.param2
            val interval = src.param3
            val timeStamp = GUIMain.softwareTimerService.getTime()
            val traceDataList = arrayListOf<TraceData>()
            traceDataList.add(TraceData(Pair(overlays[0], value1), timeStamp))
            traceDataList.add(TraceData(Pair(overlays[1], value2), timeStamp))
            try {
                Thread.sleep(interval.toLong()) //param2 was the time delay
            } catch (e: InterruptedException) {
                //e.printStackTrace()
            }
            return traceDataList
        }
    }

    private class GetTraceSineWaveSource(override var name: String, override var description: String) : TraceMethod() {
        override fun runMethod(vararg inputs: Any): ArrayList<TraceData> {
            val dummyOverlay = inputs[0] as Overlay
            //TODO are these commented variable accurate?
//            var src = inputs[1] as Source
//            var amplitude = src.param1
//            var frequency = src.param2
//            var offset = src.param3
//            var interval = src.param4
            val timeStamp = GUIMain.softwareTimerService.getTime()
            val traceDataList = arrayListOf<TraceData>()
            val traceData = TraceData(Pair(dummyOverlay, 1.0), timeStamp)

            traceDataList.add(traceData)

            //delay to control the rate at which it sends values into the akka graph
            try {
                Thread.sleep(500L) //param2 was the time delay
            } catch (e: InterruptedException) {
                //e.printStackTrace()
            }
            return traceDataList
        }
    }

    private class GetTraceSquareWaveSource(override var name: String, override var description: String) : TraceMethod() {
        var lastValue = 0.0
        var bFirst = true

        override fun runMethod(vararg inputs: Any): ArrayList<TraceData> {
            val dummyOverlay = inputs[0] as Overlay //the overlay is the ID of this dataset and is used for plotting all lines etc have the same ID
            val src = inputs[1] as Source //the source should contain all of the data needed to get the output value
            val amplitude = src.param1
            val interval = src.param2
            val timeStamp = GUIMain.softwareTimerService.getTime() //the time point

            if (bFirst) {
                lastValue = amplitude
                bFirst = false
            }

            val value = -lastValue
            lastValue = value

            val traceDataList = arrayListOf<TraceData>() //we have a list of TraceData because we could have lots of data series
            val traceData = TraceData(Pair(dummyOverlay, value), timeStamp)
            traceDataList.add(traceData)

            //delay to control the rate at which it sends values into the akka graph
            try {
                Thread.sleep(interval.toLong()) //param2 was the time delay
            } catch (e: InterruptedException) {
                //e.printStackTrace()
            }
            return traceDataList
        }
    }

    fun registerAcquisitionMethod(acquisitionMethod: AcquisitionMethod) {
        //We have to use print statements here instead of the logger service because acquisition method registration
        //happens before the logger service has been initialised (a quirk of scijava and services)
        if (isAcquisitionMethodNameUnique(acquisitionMethod.name)) {
            acquisitionMethods.add(acquisitionMethod)
            println("Registered acquisition method ${acquisitionMethod.name}")
        } else {
            println("Acquisition method ${acquisitionMethod.name} does not have unique name and has not been registered")
        }
    }

    private fun isAcquisitionMethodNameUnique(acquisitionMethodName: String): Boolean {
        return !acquisitionMethods.any { x -> x.name == acquisitionMethodName }
    }

    fun getAcquisitionMethod(acquisitionMethodName: String): AcquisitionMethod? {
        return try {
            val method = acquisitionMethods.first { x -> x.name == acquisitionMethodName }
            val inst = method.javaClass.getConstructor(String::class.java, String::class.java)
            inst.newInstance(method.name, method.description)
        } catch (ex: Exception) {
            GUIMain.loggerService.log(Level.SEVERE, "Could not find acquisition method called $acquisitionMethodName")
            GUIMain.loggerService.log(Level.SEVERE, ex.stackTrace)
            null
        }
    }

    fun getImageSubsetByteArray(fullSizeImage: STRIMMImage, resizeValues: ResizeValues): kotlin.ByteArray? {
        val imgSize = GUIMain.strimmUIService.cameraSizeList[fullSizeImage.sourceCamera]
        when (fullSizeImage.pix) {
            is ByteArray -> {
                val arrayImg = ArrayImgs.unsignedBytes(fullSizeImage.pix, imgSize!!.first!!.toLong(), imgSize.second!!.toLong())
                val resizedArray = arrayListOf<Byte>()
                val maskExtentX = (resizeValues.w!!.toDouble() + resizeValues.x!!.toDouble()) - 1
                val maskExtentY = (resizeValues.h!!.toDouble() + resizeValues.y!!.toDouble()) - 1
                val boxMask = GeomMasks.closedBox(doubleArrayOf(resizeValues.x.toDouble(), resizeValues.y.toDouble()), doubleArrayOf(maskExtentX, maskExtentY))
                val boxInterval = Intervals.largestContainedInterval(boxMask)
                val maskRRA = Masks.toRealRandomAccessible(boxMask)
                val interval = Views.interval(Views.raster(maskRRA), boxInterval)
                val iterableRegion = Regions.iterable(interval)
                val viewCursor = Regions.sample(iterableRegion, arrayImg).cursor()
                var pixelCounter = 0
                while (viewCursor.hasNext()) {
                    val value = viewCursor.next()
                    resizedArray.add(value.realDouble.toByte())
                    pixelCounter++
                }
                return resizedArray.toByteArray()
            }
            else -> return null
        }
    }

    fun getImageSubsetShortArray(fullSizeImage: STRIMMImage, resizeValues: ResizeValues): kotlin.ShortArray? {
        val imgSize = GUIMain.strimmUIService.cameraSizeList[fullSizeImage.sourceCamera]
        when (fullSizeImage.pix) {
            is ShortArray -> {
                val arrayImg = ArrayImgs.unsignedShorts(fullSizeImage.pix, imgSize!!.first!!.toLong(), imgSize.second!!.toLong())
                val resizedArray = arrayListOf<Short>()
                val maskExtentX = (resizeValues.w!!.toDouble() + resizeValues.x!!.toDouble()) - 1
                val maskExtentY = (resizeValues.h!!.toDouble() + resizeValues.y!!.toDouble()) - 1
                val boxMask = GeomMasks.closedBox(doubleArrayOf(resizeValues.x.toDouble(), resizeValues.y.toDouble()), doubleArrayOf(maskExtentX, maskExtentY))
                val boxInterval = Intervals.largestContainedInterval(boxMask)
                val maskRRA = Masks.toRealRandomAccessible(boxMask)
                val interval = Views.interval(Views.raster(maskRRA), boxInterval)
                val iterableRegion = Regions.iterable(interval)
                val viewCursor = Regions.sample(iterableRegion, arrayImg).cursor()
                var pixelCounter = 0
                while (viewCursor.hasNext()) {
                    val value = viewCursor.next()
                    resizedArray.add(value.realDouble.toShort())
                    pixelCounter++
                }
                return resizedArray.toShortArray()
            }
            else -> return null
        }
    }

    fun getImageSubsetFloatArray(fullSizeImage: STRIMMImage, resizeValues: ResizeValues): kotlin.FloatArray? {
        val imgSize = GUIMain.strimmUIService.cameraSizeList[fullSizeImage.sourceCamera]
        when (fullSizeImage.pix) {
            is FloatArray -> {
                val arrayImg = ArrayImgs.floats(fullSizeImage.pix, imgSize!!.first!!.toLong(), imgSize.second!!.toLong())
                val resizedArray = arrayListOf<Float>()
                val maskExtentX = (resizeValues.w!!.toDouble() + resizeValues.x!!.toDouble()) - 1
                val maskExtentY = (resizeValues.h!!.toDouble() + resizeValues.y!!.toDouble()) - 1
                val boxMask = GeomMasks.closedBox(doubleArrayOf(resizeValues.x.toDouble(), resizeValues.y.toDouble()), doubleArrayOf(maskExtentX, maskExtentY))
                val boxInterval = Intervals.largestContainedInterval(boxMask)
                val maskRRA = Masks.toRealRandomAccessible(boxMask)
                val interval = Views.interval(Views.raster(maskRRA), boxInterval)
                val iterableRegion = Regions.iterable(interval)
                val viewCursor = Regions.sample(iterableRegion, arrayImg).cursor()
                var pixelCounter = 0
                while (viewCursor.hasNext()) {
                    val value = viewCursor.next()
                    resizedArray.add(value.realDouble.toFloat())
                    pixelCounter++
                }
                return resizedArray.toFloatArray()
            }
            else -> return null
        }
    }
}