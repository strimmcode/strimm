package uk.co.strimm.flowMethods

import akka.actor.ActorRef
import com.opencsv.CSVReader
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
import uk.co.strimm.STRIMMBuffer
import uk.co.strimm.STRIMMImage
import uk.co.strimm.STRIMMPixelBuffer
import uk.co.strimm.STRIMMSignalBuffer
import uk.co.strimm.experiment.Flow
import uk.co.strimm.gui.GUIMain
import java.io.FileReader
import java.util.logging.Level
//Takes an image and find the ROIs in that image and output as a STRIMMSignalBuffer
//which can be plotted.
//The cfg has the name of the sink with the reference to the roi file
//this roi file can be in either csv or roiManager format from MicroManager
//
class ROIFlowMethod() : FlowMethod {


    var sinkName = ""
    open lateinit var flow: Flow
    override lateinit var properties: HashMap<String, String>
    override var actor: ActorRef? = null
    override fun init(flow: Flow) {
        this.flow = flow
        loadCfg()
        //will get the ROIs from the OverlayManager based on SinkName and then output them as a buffer to be plotted
        sinkName = properties["ImageJSinkName"]!!

    }

    override fun run(data: List<STRIMMBuffer>): STRIMMBuffer {
        //the flow function should know the

        //get the overlays


        //cant seem to get a list of overlays
        val overlays = GUIMain.overlayService.getOverlays()
        val disp = GUIMain.experimentService.experimentStream.cameraDisplays[sinkName]
        if (disp != null && overlays.size > 0) {
            val over = GUIMain.overlayService.getOverlays(disp) //overlays in disp but might be reapeted
            val filteredOverlays = overlays.filter { it in over }.filter{ it.name != null }
//            for (fff in 0..filteredOverlays.size - 1){
//                println(fff.toString() + "   " + filteredOverlays[fff].name)
//            }

            //apply each overlay to the pixels and calculate the mean(?)

            //assemble into a row of data, along with the SINK:IDs as names

            val image = data[0] as STRIMMPixelBuffer
            val roi_info = averageROI(filteredOverlays, image)

//            for (f in 0..roi_info.size-1){
//                val p = roi_info[f]
//                println(f.toString() + " " + p.first + " " + p.second)
//            }
            val data = DoubleArray(roi_info.size)
            for (f in 0..data.size - 1) {
                data[f] = roi_info[f].second  //Pair(string, val)
            }
            val times = DoubleArray(1)
            times[0] = image.timeAcquired
            val numSamples = 1
            val channelNames = mutableListOf<String>()
            for (f in 0..data.size - 1) {
                channelNames.add(roi_info[f].first)
            }
            //println(channelNames.toString())
            return STRIMMSignalBuffer(data, times, numSamples, channelNames, image.dataID, image.status)
        }
        else {
            return STRIMMBuffer(0,0)
        }

    }
    private fun averageROI(roiList: List<Overlay>, image: STRIMMPixelBuffer): List< Pair<String, Double> > {

        val results = arrayListOf<Pair<String, Double> >()
        var x: Double = 0.0
        var y: Double = 0.0
        var w: Double = 0.0
        var h: Double = 0.0
        var viewCursor: Cursor<out AbstractComplexType<*>>? = null

        for (fff in 0..roiList.size-1) {
            val roi = roiList[fff]
            //println(fff.toString() + "   " + roi.name)
            when (roi) {
                is EllipseOverlay -> {
                    try {
                        x = roi.getOrigin(0)
                        y = roi.getOrigin(1)
                        w = roi.getRadius(0) //remember its radius, not width
                        h = roi.getRadius(1) //remember its radius, not width

                        //Masks and interval lines below taken from https://forum.image.sc/t/examples-of-usage-of-imglib2-roi/22855/14
                        if (image.pixelType == "Byte") {
                            //The image provided in this method will come in full size, check if a resize is needed before beginning the ROI calculation

                            val pix_chan: ByteArray = (image.pix as ByteArray)

                            val arrayImg: ArrayImg<UnsignedByteType, net.imglib2.img.basictypeaccess.array.ByteArray>
                            arrayImg =
                                ArrayImgs.unsignedBytes(
                                    pix_chan.sliceArray(0..(image.w*image.h).toInt()),
                                    image.w.toLong(),  //w
                                    image.h.toLong()  //h
                                )


                                //Proceed with iterating over a cursor for the ROI
                                val ellipsoidMask = GeomMasks.closedEllipsoid(doubleArrayOf(x, y), doubleArrayOf(w, h))
                                val ellipsoidInterval = Intervals.largestContainedInterval(ellipsoidMask)
                                val maskRRA = Masks.toRealRandomAccessible(ellipsoidMask)
                                val interval = Views.interval(Views.raster(maskRRA), ellipsoidInterval)
                                val iterableRegion = Regions.iterable(interval)
                                viewCursor = Regions.sample(iterableRegion, arrayImg).cursor()

                                val average = calculateAverage(viewCursor)
                                results.add(Pair(roi.name, average))




                        }
                        else if (image.pixelType == "Short") {
                            val pix_chan: ShortArray = (image.pix as ShortArray)
                            val arrayImg: ArrayImg<UnsignedShortType, net.imglib2.img.basictypeaccess.array.ShortArray>
                            arrayImg =
                                ArrayImgs.unsignedShorts(
                                    pix_chan.sliceArray(0..(image.w*image.h).toInt()),
                                    image.w.toLong(),
                                    image.h.toLong()
                                )


                            //Proceed with iterating over a cursor for the ROI
                            val ellipsoidMask = GeomMasks.closedEllipsoid(doubleArrayOf(x, y), doubleArrayOf(w, h))
                            val ellipsoidInterval = Intervals.largestContainedInterval(ellipsoidMask)
                            val maskRRA = Masks.toRealRandomAccessible(ellipsoidMask)
                            val interval = Views.interval(Views.raster(maskRRA), ellipsoidInterval)
                            val iterableRegion = Regions.iterable(interval)
                            viewCursor = Regions.sample(iterableRegion, arrayImg).cursor()

                            val average = calculateAverage(viewCursor)
                            results.add(Pair(roi.name, average))
                        }
                        else if (image.pixelType == "Float"){
                            val pix_chan: FloatArray = (image.pix as FloatArray)
                            val arrayImg: ArrayImg<FloatType, net.imglib2.img.basictypeaccess.array.FloatArray>
                            arrayImg = ArrayImgs.floats(
                                    pix_chan,
                                    image.w.toLong(),
                                    image.h.toLong()
                            )

                            //Proceed with iterating over a cursor for the ROI
                            val ellipsoidMask = GeomMasks.closedEllipsoid(doubleArrayOf(x, y), doubleArrayOf(w, h))
                            val ellipsoidInterval = Intervals.largestContainedInterval(ellipsoidMask)
                            val maskRRA = Masks.toRealRandomAccessible(ellipsoidMask)
                            val interval = Views.interval(Views.raster(maskRRA), ellipsoidInterval)
                            val iterableRegion = Regions.iterable(interval)
                            viewCursor = Regions.sample(iterableRegion, arrayImg).cursor()

                            val average = calculateAverage(viewCursor)
                            results.add(Pair(roi.name, average))
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
                        //Masks and interval lines below taken from https://forum.image.sc/t/examples-of-usage-of-imglib2-roi/22855/14
                        if (image.pixelType == "Byte") {
                            //The image provided in this method will come in full size, check if a resize is needed before beginning the ROI calculation

                                //select out the f channels of data into pix_chan
                                val pix_chan: ByteArray = (image.pix as ByteArray)

                                val arrayImg: ArrayImg<UnsignedByteType, net.imglib2.img.basictypeaccess.array.ByteArray>
                                arrayImg =
                                    ArrayImgs.unsignedBytes(
                                        pix_chan.sliceArray(0..(image.w*image.h).toInt()),
                                        image.w.toLong(),  //w
                                        image.h.toLong()  //h
                                    )

                                val maskExtentX = (x + w) - 1
                                val maskExtentY = (h + y) - 1
                                val boxMask =
                                    GeomMasks.closedBox(doubleArrayOf(x, y), doubleArrayOf(maskExtentX, maskExtentY))
                                val boxInterval = Intervals.largestContainedInterval(boxMask)
                                val maskRRA = Masks.toRealRandomAccessible(boxMask)
                                val interval = Views.interval(Views.raster(maskRRA), boxInterval)
                                val iterableRegion = Regions.iterable(interval)
                                viewCursor = Regions.sample(iterableRegion, arrayImg).cursor()

                                val average = calculateAverage(viewCursor)
                                results.add(Pair(roi.name, average))




                        }
                        else if (image.pixelType == "Short") {
                            //The image provided in this method will come in full size, check if a resize is needed before beginning the ROI calculation

                            //select out the f channels of data into pix_chan
                            val pix_chan: ShortArray = (image.pix as ShortArray)

                            val arrayImg: ArrayImg<UnsignedShortType, net.imglib2.img.basictypeaccess.array.ShortArray>
                            arrayImg =
                                ArrayImgs.unsignedShorts(
                                    pix_chan.sliceArray(0..(image.w*image.h).toInt()),
                                    image.w.toLong(),
                                    image.h.toLong()
                                )

                            val maskExtentX = (x + w) - 1
                            val maskExtentY = (h + y) - 1
                            val boxMask =
                                GeomMasks.closedBox(doubleArrayOf(x, y), doubleArrayOf(maskExtentX, maskExtentY))
                            val boxInterval = Intervals.largestContainedInterval(boxMask)
                            val maskRRA = Masks.toRealRandomAccessible(boxMask)
                            val interval = Views.interval(Views.raster(maskRRA), boxInterval)
                            val iterableRegion = Regions.iterable(interval)
                            viewCursor = Regions.sample(iterableRegion, arrayImg).cursor()

                            val average = calculateAverage(viewCursor)
                            results.add(Pair(roi.name, average))




                        }
                        else if (image.pixelType == "Float") {
                            //The image provided in this method will come in full size, check if a resize is needed before beginning the ROI calculation

                            //select out the f channels of data into pix_chan
                            val pix_chan: FloatArray = (image.pix as FloatArray)

                            val arrayImg: ArrayImg<FloatType, net.imglib2.img.basictypeaccess.array.FloatArray>
                            arrayImg = ArrayImgs.floats(
                                pix_chan,
                                image.w.toLong(),
                                image.h.toLong()
                            )

                            val maskExtentX = (x + w) - 1
                            val maskExtentY = (h + y) - 1
                            val boxMask =
                                GeomMasks.closedBox(doubleArrayOf(x, y), doubleArrayOf(maskExtentX, maskExtentY))
                            val boxInterval = Intervals.largestContainedInterval(boxMask)
                            val maskRRA = Masks.toRealRandomAccessible(boxMask)
                            val interval = Views.interval(Views.raster(maskRRA), boxInterval)
                            val iterableRegion = Regions.iterable(interval)
                            viewCursor = Regions.sample(iterableRegion, arrayImg).cursor()

                            val average = calculateAverage(viewCursor)
                            results.add(Pair(roi.name, average))




                        }

                    } catch (ex: Exception) {
                        GUIMain.loggerService.log(
                            java.util.logging.Level.SEVERE,
                            "Could not read rectangle overlay data. Error: " + ex.message
                        )
                    }

                }

            }
        }

        //get the averages and package and return them
       return results  //for now
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

    }
    override fun postStop(){

    }
}