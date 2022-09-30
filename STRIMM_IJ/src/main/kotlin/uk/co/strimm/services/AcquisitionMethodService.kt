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
import java.util.List
import java.util.logging.Level

@Plugin(type = Service::class)
class AcquisitionMethodService : AbstractService(), ImageJService {
    var bCamerasAcquire = false
    var bAquisitionProceed = false

    var configuredCameras = hashMapOf<String, Camera>()
    private fun populateConfiguredCameras(){
        val pathnames: Array<String>
        val f = File("DeviceAdapters/CameraMMConfigs")
        pathnames = f.list()
        for (pathname in pathnames) {
            //JOptionPane.showMessageDialog(null, pathname)
            configuredCameras[pathname] = CameraConfigured(pathname)
        }
    }

    var acquisitionMethods : ArrayList<AcquisitionMethod> = arrayListOf()
    fun GetCamera(szConfig : String) : Camera? {
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
            return STRIMMImage("", null, 0,0, 0, 0) //TODO dummy
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
            while (!GUIMain.acquisitionMethodService.bAquisitionProceed){
                Thread.sleep(50)
            }
            val image = inputs[0] as STRIMMImage
            //the flowName is recorded in rois
            val flowName = inputs[1] as String

            //By doing this at runtime the elements could change and new rois could be added dynamically
            //another part of the code needs to update these RoiLists at runtime - and we will be able
            //to load roi from the file and move it and it would still work

            //Use the flowName : String in the hashmaps to find the Overlays
            //we will get all of the Overlays associated with a particular flowName i.e. camera
            //is this why we need the actor - in order to update its position?
            //
            //routedRoiOverlays - loaded from rois in json
            // the list of overlays will be relative to a flow and the image which flowed through it
            //
            var overlaysForDevice = GUIMain.actorService.routedRoiOverlays.filter { x -> x.value.toLowerCase() == flowName.toLowerCase() }
            //routedRoiList - loaded at runtime
            var overlaysForDevice1 = GUIMain.actorService.routedRoiList.filter { x -> x.value.second.toLowerCase() == flowName.toLowerCase() }

            //this array of Overlays will be the json rois and the the user chosen
            val arrayListToReturn = arrayListOf<TraceData>()
            if (overlaysForDevice.isNotEmpty()) {
                for (deviceOverlay in overlaysForDevice) {
                    //var ov = deviceOverlay.key as RectangleOverlay
                    // println(ov.getOrigin(0).toString() + "  " + ov.getOrigin(1).toString())
                    arrayListToReturn.add(averageROI(deviceOverlay.key, image))
                }
            }
            if (overlaysForDevice1.isNotEmpty()) {
                for (deviceOverlay in overlaysForDevice1.keys.toList()) {
                    //var ov = deviceOverlay as RectangleOverlay
                    //println(ov.getOrigin(0).toString() + "  " + ov.getOrigin(1).toString())

                    arrayListToReturn.add(averageROI(deviceOverlay, image))
                }
            }

//            //print out the Overlay.name
//            var td_cnt = 0
//            for (td in arrayListToReturn){
//                println(td_cnt.toString() + "   " + td.data.first!!.name.toString())
//                td_cnt++
//            }
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
            //image knows the source camera name via image.sourceCamera
            //roi is an Overlay and it has a name roi.name

            if (image.pix == null){
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
                        w = roi.getRadius(0) //remember its radius, not width
                        h = roi.getRadius(1) //remember its radius, not width
                        val imgSizeFull = Pair(image.w, image.h)
                        //Masks and interval lines below taken from https://forum.image.sc/t/examples-of-usage-of-imglib2-roi/22855/14
                        when (image.pix) {
                            is ByteArray -> {
                                //The image provided in this method will come in full size, check if a resize is needed before beginning the ROI calculation
                                val arrayImg: ArrayImg<UnsignedByteType, net.imglib2.img.basictypeaccess.array.ByteArray>
                                arrayImg = ArrayImgs.unsignedBytes(
                                        image.pix,
                                        imgSizeFull.first!!.toLong(),
                                        imgSizeFull.second!!.toLong()
                                    )

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
                                val arrayImg: ArrayImg<UnsignedShortType, net.imglib2.img.basictypeaccess.array.ShortArray>
                                arrayImg =
                                    ArrayImgs.unsignedShorts(
                                        image.pix,
                                        imgSizeFull.first!!.toLong(),
                                        imgSizeFull.second!!.toLong()
                                    )


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
                                } else {
                                    ArrayImgs.floats(
                                        image.pix,
                                        imgSizeFull.first!!.toLong(),
                                        imgSizeFull.second!!.toLong()
                                    )
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
                    } catch (ex: Exception) {
                        GUIMain.loggerService.log(
                            Level.SEVERE,
                            "Could not read ellipse overlay data. Error: " + ex.message
                        )
                    }
                }
                is RectangleOverlay -> {
                    try {
                        x = roi.getOrigin(0)
                        y = roi.getOrigin(1)
                        w = roi.getExtent(0)
                        h = roi.getExtent(1)

                        val imgSizeFull = Pair(image.w, image.h) // GUIMain.strimmUIService.cameraSizeList[image.sourceCamera]!!

                        //Masks and interval lines below taken from https://forum.image.sc/t/examples-of-usage-of-imglib2-roi/22855/14
                        when (image.pix) {
                            is ByteArray -> {
                                val arrayImg: ArrayImg<UnsignedByteType, net.imglib2.img.basictypeaccess.array.ByteArray>
                                arrayImg =
                                    ArrayImgs.unsignedBytes(
                                        image.pix,
                                        imgSizeFull.first!!.toLong(),
                                        imgSizeFull.second!!.toLong()
                                    )


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
                                val arrayImg: ArrayImg<UnsignedShortType, net.imglib2.img.basictypeaccess.array.ShortArray>
                                //this should be irrelevant, the STRIMMImage should be self documenting
                                arrayImg =
                                    ArrayImgs.unsignedShorts(
                                        image.pix,
                                        imgSizeFull.first!!.toLong(),
                                        imgSizeFull.second!!.toLong()
                                    )


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
                                val arrayImg: ArrayImg<FloatType, net.imglib2.img.basictypeaccess.array.FloatArray>
                                arrayImg =
                                    ArrayImgs.floats(
                                        image.pix,
                                        imgSizeFull.first!!.toLong(),
                                        imgSizeFull.second!!.toLong()
                                    )


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
                    } catch (ex: Exception) {
                        GUIMain.loggerService.log(
                            java.util.logging.Level.SEVERE,
                            "Could not read rectangle overlay data. Error: " + ex.message
                        )
                    }
                }
                is PolygonOverlay -> {
                    //TODO this is a bit more complicated
                }
            }

            val average = calculateAverage(viewCursor)

           // println("***** average  " + average.toString())
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
                GUIMain.loggerService.log(Level.SEVERE, "Could not iterate through overlay data. Error: " + ex.message)
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
    private class SnapImage(override var name : String ,override var description: String) : ImageMethod(){
        override fun runMethod(vararg inputs: Any): STRIMMImage {
            while (!GUIMain.acquisitionMethodService.bAquisitionProceed){
                Thread.sleep(50)
            }
            val source = inputs[0] as Source
            val camToUse = source.camera //polymorphically runs the camera :: retiga etc
            val strimmimage = camToUse?.run()
            return strimmimage ?: STRIMMImage("", null, GUIMain.softwareTimerService.getTime(),0, 0, 0)
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
        override fun runMethod(vararg inputs: Any): java.util.ArrayList<TraceData> {
            val dummyOverlay = inputs[0] as Overlay
            val intervalSecs = inputs[1] as Double
            val traceDataList = arrayListOf<TraceData>()

            /**
             * NOTE - At the moment the acquisition method is in a method called "storeTraceData" in the class
             * "AnalogueDataStream" in the TimerService. Need to make sure this method uses that or vice-versa in some
             * way
             */
//            for(i in 0 until 100) {
            try {
//                    val traceVal = GUIMain.experimentService.analogueBuffer.removeFirst()
//                    traceDataList.add(TraceData(Pair(dummyOverlay, traceVal), clockCounter*1000))
            } catch (ex: Exception) {
                //println("Error. buffer size is ${GUIMain.experimentService.analogueBuffer.size}")
            }

            clockCounter += intervalSecs
//            }

//            println("Got some data, clock counter: $clockCounter")
            return traceDataList
        }
    }

    /**
     * Get trace data from a trace device
     * @param name The name used to identify this method. Must be unique
     * @param description A short description of what the method does
     */
    private class GetTraceDataNIDAQ(override var name: String, override var description: String) : TraceMethod() {
        var bFirst = true
        var dataAI = DoubleArray(1)
        var dataAO = DoubleArray(1)
        var dataDI = IntArray(1)
        var dataDO = IntArray(1)
        var pTimes = DoubleArray(1)
        var clockCounter = 0.0
        override fun runMethod(vararg inputs: Any): java.util.ArrayList<TraceData> {
            if (GUIMain.experimentService.experimentStream.expConfig.isGlobalStart) {
                //System.out.println("Start trigger = " + GUIMain.protocolService.getBGlobalSourceStartTrigger());
                while (!GUIMain.protocolService.bGlobalSourceStartTrigger) {
                    //System.out.println(GUIMain.protocolService.getBGlobalSourceStartTrigger());
                    val bKeyPressed =
                        GUIMain.protocolService.jdaq.GetKeyState(GUIMain.experimentService.experimentStream.expConfig.GlobalStartVirtualCode)
                    //System.out.println(bKeyPressed);
                    if (bKeyPressed) {
                        GUIMain.protocolService.bGlobalSourceStartTrigger = true
                        GUIMain.softwareTimerService.setFirstTimeMeasurement() //mix up with time here TIME_CONFUSION
                        //this is used by experiment duration
                    }
                    Thread.sleep(100)
                }
            }

            //snap the image and also gather some information to
            //estimate the fps
            // println("AcquisitionMethodService::runMethod")
            //all we can guarantee is that AO,AI,DO,DI will each have the same number of rows
            var dummyAO = inputs[0] as MutableList<Overlay>
            var dummyAI = inputs[1] as MutableList<Overlay>
            var dummyDO = inputs[2] as MutableList<Overlay>
            var dummyDI = inputs[3] as MutableList<Overlay>

            var src = inputs[4] as Source

            var bKeyboardSnapEnabled = src.isKeyboardSnapEnabled
            var SnapVirtualCode = src.SnapVirtualCode

            if (bKeyboardSnapEnabled) {
                var bKeyPressed = GUIMain.protocolService.jdaq.GetKeyState(SnapVirtualCode)
                // System.out.println("Key pressed = " + bKeyPressed.toString());
                while (!bKeyPressed) {
                    Thread.sleep(50)
                    bKeyPressed = GUIMain.protocolService.jdaq.GetKeyState(SnapVirtualCode)
                }
            }

            src.isBusy = true

            var numAOChannels = GUIMain.protocolService.GetNumChannels(0)
            var numAIChannels = GUIMain.protocolService.GetNumChannels(1)
            var numDOChannels = GUIMain.protocolService.GetNumChannels(2)
            var numDIChannels = GUIMain.protocolService.GetNumChannels(3)

            var numSamples = GUIMain.protocolService.GetNextNumSamples()
            if (bFirst ){ // ||  numSamples != GUIMain.protocolService.GetNextNumSamples() ){
                //need new buffers
                numSamples = GUIMain.protocolService.GetNextNumSamples()
                dataAI = DoubleArray(numSamples * numAIChannels)
                dataDI = IntArray(numSamples * numDIChannels)
                dataAO = DoubleArray(numSamples * numAOChannels)
                dataDO = IntArray(numSamples * numDOChannels)
                pTimes = DoubleArray(numSamples)
                bFirst = false

            }

            if (GUIMain.acquisitionMethodService.bAquisitionProceed) {
                //println("set to proceed ***************")
                GUIMain.protocolService.RunNext(pTimes, dataAO, dataAI, dataDO, dataDI);
            }
            //make up data here for dataAI, dataAO, dataDI, dataDO in order to test the graph drawing

            val traceDataList = arrayListOf<TraceData>()
            //create dummy overlays
//            var expSource = inputs[1] as ExperimentTraceSource
            var cntAO = 0
            var cntAI = 0
            var cntDO = 0
            var cntDI = 0
            //here put in the different overlays for each channel
            //println("AcquisitionManager::NIDAQ::GetCurrentRunStartTime " + GUIMain.protocolService.GetCurrentRunStartTime())
            var timeStart =  GUIMain.experimentService.experimentStream.timeStart
            for (f in 0 until numSamples) {
                //ProtocolManager in test.dll always responds in sec when describing times
                // as this is likely very well timed use the NIDAQ nominal timing
                clockCounter = pTimes[f] - timeStart  //however it might be massive //(GUIMain.protocolService.GetCurrentRunStartTime() + f * GUIMain.protocolService.GetCurrentRunSampleTime())
                for (ff in 0 until numAOChannels) {  // until does not include the last number
                    traceDataList.add(TraceData(Pair(dummyAO[ff], dataAO[cntAO]), clockCounter))
                    cntAO++   //just print out a single column
                }
                for (ff in 0 until numAIChannels) {
                    traceDataList.add(TraceData(Pair(dummyAI[ff], dataAI[cntAI]), clockCounter))
                    cntAI++   //just print out a single column
                }
                for (ff in 0 until numDOChannels) {
                    traceDataList.add(TraceData(Pair(dummyDO[ff], dataDO[cntDO].toDouble()), clockCounter))
                    cntDO++   //just print out a single column
                }
                for (ff in 0 until numDIChannels) {
                    traceDataList.add(TraceData(Pair(dummyDI[ff], dataDI[cntDI].toDouble()), clockCounter))
                    cntDI++   //just print out a single column
                }
                //GUIMain.protocolService.GetCurrentRunStartTime()
                //+ f * GUIMain.protocolService.GetCurrentRunSampleTime()
                //println(GUIMain.protocolService.GetCurrentRunStartTime().toString())
            }
//val startT = GUIMain.protocolService.GetCurrentRunStartTime()
//            val samT = GUIMain.protocolService.GetCurrentRunSampleTime()

            src.isBusy = false
            return traceDataList
        }
    }


    private class GetTraceDataKeyboard(override var name: String, override var description: String) : TraceMethod() {
        var clockCounter = 0.0

        override fun runMethod(vararg inputs: Any): java.util.ArrayList<TraceData> {
            var keysToPoll  = inputs[0] as ArrayList<Int>
            var dummyOverlays = inputs[1] as MutableList<Overlay>
            val traceDataList = arrayListOf<TraceData>()

            for (f in 0 .. 3) {
                var keysResults = arrayListOf<Double>()
                for (VK in keysToPoll){
                    val b: Boolean = GUIMain.protocolService.jdaq.GetKeyState(VK)
                    keysResults.add(if(b) 7.0 else 0.0)
                }
                var timeStamp = GUIMain.softwareTimerService.getTime()
                //println(timeStamp)
                for (ff in 0 until keysToPoll.size) {
                    traceDataList.add(TraceData(Pair(dummyOverlays[ff], keysResults[ff]),
                        timeStamp ))
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

        override fun runMethod(vararg inputs: Any): java.util.ArrayList<TraceData> {
            while (!GUIMain.acquisitionMethodService.bAquisitionProceed){
                //spin
            }
            var key  = inputs[0] as Int
            var dummyOverlay = inputs[1] as Overlay
            var pollPeriod = inputs[2] as Int
            val traceDataList = arrayListOf<TraceData>()
            val b: Boolean = GUIMain.protocolService.jdaq.GetKeyState(key)
            var keysResult = if(b) 100.0 else 0.0
            var timeStamp = GUIMain.softwareTimerService.getTime()
            traceDataList.add(TraceData(Pair(dummyOverlay, keysResult), timeStamp ))
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
        override fun runMethod(vararg inputs: Any): java.util.ArrayList<TraceData> {
            var dummyOverlay = inputs[0] as Overlay
            var src = inputs[1] as Source
            var value = src.param1
            var timeStamp = GUIMain.softwareTimerService.getTime()
            val traceDataList = arrayListOf<TraceData>()
            var traceData = TraceData( Pair(dummyOverlay, value), timeStamp)
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

        override fun runMethod(vararg inputs: Any): java.util.ArrayList<TraceData> {
            var dummyOverlay = inputs[0] as Overlay //the overlay is the ID of this dataset and is used for plotting all lines etc have the same ID
            var src = inputs[1] as Source //the source should contain all of the data needed to get the output value
            var amplitude = src.param1
            var interval = src.param2

            var timeStamp = GUIMain.softwareTimerService.getTime() //the time point


            val traceDataList = arrayListOf<TraceData>() //we have a list of TraceData because we could have lots of data series

            val rand = Random()
            val value = rand.nextInt(amplitude.toInt())


            var traceData = TraceData( Pair(dummyOverlay, value.toDouble()), timeStamp)
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
        override fun runMethod(vararg inputs: Any): java.util.ArrayList<TraceData> {
            var overlays = inputs[0] as List<Overlay>
            var src = inputs[1] as Source
            var value1 = src.param1
            var value2 = src.param2
            var interval = src.param3
            var timeStamp = GUIMain.softwareTimerService.getTime()
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
        override fun runMethod(vararg inputs: Any): java.util.ArrayList<TraceData> {
            var dummyOverlay = inputs[0] as Overlay
//            var src = inputs[1] as Source
//            var amplitude = src.param1
//            var frequency = src.param2
//            var offset = src.param3
//            var interval = src.param4
//
            var timeStamp = GUIMain.softwareTimerService.getTime() //the time point
//            var value = offset + amplitude*Math.sin(frequency/(2*Math.PI)*timeStamp)

            val traceDataList = arrayListOf<TraceData>()
            var traceData = TraceData( Pair(dummyOverlay, 1.0), timeStamp)
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
        override fun runMethod(vararg inputs: Any): java.util.ArrayList<TraceData> {
            var dummyOverlay = inputs[0] as Overlay //the overlay is the ID of this dataset and is used for plotting all lines etc have the same ID
            var src = inputs[1] as Source //the source should contain all of the data needed to get the output value
            var amplitude = src.param1
            var interval = src.param2

            var timeStamp = GUIMain.softwareTimerService.getTime() //the time point
            if (bFirst){
                lastValue = amplitude
                bFirst = false
            }
            var value = -lastValue
            lastValue = value

            val traceDataList = arrayListOf<TraceData>() //we have a list of TraceData because we could have lots of data series
            var traceData = TraceData( Pair(dummyOverlay, value), timeStamp)
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

    init {
        populateConfiguredCameras()
        registerDefaultMethods()
        registerCustomMethods()
    }

    private fun registerDefaultMethods(){
        //image sources
        val averageROIMethod = AverageROI(ExperimentConstants.Acquisition.AVERAGE_ROI_METHOD_NAME,"Average the pixel intensity any ROIs (overlays) on a given image")
        val snapImageMethod = SnapImage("ConfiguredCamera", "")
        val generateImageMethod = GenerateImage(ExperimentConstants.Acquisition.GENERATE_IMAGE_METHOD_NAME,"Generate an image")
        //trace sources
        val getTraceDataMethod = GetTraceData(ExperimentConstants.Acquisition.GET_TRACE_DATA_METHOD_NAME,"Get data from a trace source or flow")
        val getTraceDataMethodNIDAQ = GetTraceDataNIDAQ( "Trace Data Method NIDAQ","Get data from a trace source or flow")
        val getTraceDataMethodKeyboard = GetTraceDataKeyboard( "Trace Data Method Keyboard","Get data from a trace source or flow")
        val getGetTraceDataKeyboardA = GetTraceDataKeyboardA("KeyboardA", "")
        val getTraceDataMethodConstantTraceSource = GetTraceConstantTraceSource( "ConstantTraceSource","")
        val getTraceDataRandomTraceSource = GetTraceRandomTraceSource( "RandomTraceSource","")
        val getTraceDataMethodConstantVectorSource = GetTraceConstantVectorSource( "ConstantVectorSource","")
        val getTraceDataMethodSineWaveSource = GetTraceSineWaveSource( "SineWaveSource","")
        val getTraceDataMethodSquareWaveSource = GetTraceSquareWaveSource( "SquareWaveSource","")



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

    private fun registerCustomMethods(){
        //TODO load classes for custom methods (as plugins) from a specified folder
    }

    fun registerAcquisitionMethod(acquisitionMethod: AcquisitionMethod){
        if(isAcquisitionMethodNameUnique(acquisitionMethod.name)) {
            acquisitionMethods.add(acquisitionMethod)
            println("Registered acquisition method ${acquisitionMethod.name}")
        }
        else{
            println("Acquisition method ${acquisitionMethod.name} does not have unique name and has not been registered")
        }
    }

    private fun isAcquisitionMethodNameUnique(acquisitionMethodName : String):Boolean{
        return !acquisitionMethods.any { x -> x.name ==  acquisitionMethodName}
    }

    fun getAcquisitionMethod(acquisitionMethodName : String) : AcquisitionMethod?{
        return try {
            val method = acquisitionMethods.first { x -> x.name == acquisitionMethodName }
            val inst = method.javaClass.getConstructor(String::class.java, String::class.java)
            inst.newInstance(method.name, method.description)
        }
        catch(ex : Exception){
            GUIMain.loggerService.log(Level.SEVERE, "Could not find acquisition method called $acquisitionMethodName")
            GUIMain.loggerService.log(Level.SEVERE, ex.stackTrace)
            null
        }
    }

    fun getImageSubsetByteArray(fullSizeImage : STRIMMImage, resizeValues : ResizeValues) : kotlin.ByteArray?{
        val imgSize = GUIMain.strimmUIService.cameraSizeList[fullSizeImage.sourceCamera]
        when(fullSizeImage.pix){
            is ByteArray ->{
                val arrayImg = ArrayImgs.unsignedBytes(fullSizeImage.pix, imgSize!!.first!!.toLong(), imgSize.second!!.toLong())
                val resizedArray = arrayListOf<Byte>()
                val maskExtentX = (resizeValues.w!!.toDouble()+resizeValues.x!!.toDouble())-1
                val maskExtentY = (resizeValues.h!!.toDouble()+resizeValues.y!!.toDouble())-1
                val boxMask = GeomMasks.closedBox(doubleArrayOf(resizeValues.x.toDouble(),resizeValues.y.toDouble()),doubleArrayOf(maskExtentX,maskExtentY))
                val boxInterval = Intervals.largestContainedInterval(boxMask)
                val maskRRA = Masks.toRealRandomAccessible(boxMask)
                val interval = Views.interval(Views.raster(maskRRA), boxInterval)
                val iterableRegion = Regions.iterable(interval)
                val viewCursor = Regions.sample(iterableRegion, arrayImg).cursor()
                var pixelCounter = 0
                while(viewCursor.hasNext()){
                    val value = viewCursor.next()
                    resizedArray.add(value.realDouble.toByte())
                    pixelCounter++
                }
                return resizedArray.toByteArray()
            }
            else -> return null
        }
    }

    fun getImageSubsetShortArray(fullSizeImage : STRIMMImage, resizeValues : ResizeValues) : kotlin.ShortArray?{
        val imgSize = GUIMain.strimmUIService.cameraSizeList[fullSizeImage.sourceCamera]
        when(fullSizeImage.pix){
            is ShortArray ->{
                val arrayImg = ArrayImgs.unsignedShorts(fullSizeImage.pix, imgSize!!.first!!.toLong(), imgSize.second!!.toLong())
                val resizedArray = arrayListOf<Short>()
                val maskExtentX = (resizeValues.w!!.toDouble()+resizeValues.x!!.toDouble())-1
                val maskExtentY = (resizeValues.h!!.toDouble()+resizeValues.y!!.toDouble())-1
                val boxMask = GeomMasks.closedBox(doubleArrayOf(resizeValues.x.toDouble(),resizeValues.y.toDouble()),doubleArrayOf(maskExtentX,maskExtentY))
                val boxInterval = Intervals.largestContainedInterval(boxMask)
                val maskRRA = Masks.toRealRandomAccessible(boxMask)
                val interval = Views.interval(Views.raster(maskRRA), boxInterval)
                val iterableRegion = Regions.iterable(interval)
                val viewCursor = Regions.sample(iterableRegion, arrayImg).cursor()
                var pixelCounter = 0
                while(viewCursor.hasNext()){
                    val value = viewCursor.next()
                    resizedArray.add(value.realDouble.toShort())
                    pixelCounter++
                }
                return resizedArray.toShortArray()
            }
            else -> return null
        }
    }

    fun getImageSubsetFloatArray(fullSizeImage : STRIMMImage, resizeValues : ResizeValues) : kotlin.FloatArray?{
        val imgSize = GUIMain.strimmUIService.cameraSizeList[fullSizeImage.sourceCamera]
        when(fullSizeImage.pix){
            is FloatArray ->{
                val arrayImg = ArrayImgs.floats(fullSizeImage.pix, imgSize!!.first!!.toLong(), imgSize.second!!.toLong())
                val resizedArray = arrayListOf<Float>()
                val maskExtentX = (resizeValues.w!!.toDouble()+resizeValues.x!!.toDouble())-1
                val maskExtentY = (resizeValues.h!!.toDouble()+resizeValues.y!!.toDouble())-1
                val boxMask = GeomMasks.closedBox(doubleArrayOf(resizeValues.x.toDouble(),resizeValues.y.toDouble()),doubleArrayOf(maskExtentX,maskExtentY))
                val boxInterval = Intervals.largestContainedInterval(boxMask)
                val maskRRA = Masks.toRealRandomAccessible(boxMask)
                val interval = Views.interval(Views.raster(maskRRA), boxInterval)
                val iterableRegion = Regions.iterable(interval)
                val viewCursor = Regions.sample(iterableRegion, arrayImg).cursor()
                var pixelCounter = 0
                while(viewCursor.hasNext()){
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