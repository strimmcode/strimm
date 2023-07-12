package uk.co.strimm.actors

import akka.actor.AbstractActor
import akka.actor.Kill
import akka.actor.Props
import net.imagej.display.ImageDisplay
import net.imagej.overlay.EllipseOverlay
import net.imagej.overlay.RectangleOverlay
import org.scijava.util.ColorRGB
import org.scijava.util.RealCoords
import uk.co.strimm.*
import uk.co.strimm.actors.messages.Message
import uk.co.strimm.gui.CameraWindowPlugin
import uk.co.strimm.gui.GUIMain
import uk.co.strimm.actors.messages.complete.CompleteStreaming
import uk.co.strimm.actors.messages.fail.FailStreaming
import uk.co.strimm.actors.messages.start.StartStreaming
import uk.co.strimm.actors.messages.stop.TerminateActor
import uk.co.strimm.actors.messages.tell.*
import uk.co.strimm.experiment.Sink
import uk.co.strimm.gui.CameraWindow
import java.util.logging.Level
import javax.swing.JLabel

class MonitorCameraThread(name: String?, var cam : CameraWindow, var display : ImageDisplay) : Thread(name) {
    //keeps track of when the numOverlays for this display changes
    var bExit = false
    var bColoured = true

    var numOverlays = 0
    var bNumOverlaysChanged = false
    var bStart = true

    fun EndThread(){
        bExit = true
    }

    override fun run() {
        while (!bExit) {
            val canvas = display.canvas
            val overlays = GUIMain.overlayService.overlays //total number of overlays
            val overlaysForDisplay = GUIMain.overlayService.getOverlays(display) //overlays in disp but might be reapeted
            val filteredOverlays = overlays.filter{it in overlaysForDisplay}

            if (overlays.size != numOverlays){
                numOverlays = overlays.size
                bNumOverlaysChanged = true

                GUIMain.loggerService.log(Level.INFO, "Number of overlays for " + display + " is now " + filteredOverlays.size)
                var max_val = -1
                filteredOverlays.forEach{
                    if (it.name != null){
                        if (it.name.toInt() > max_val){
                            max_val = it.name.toInt()
                        }
                    }
                }

                filteredOverlays.forEach{
                    if (it.name == null){
                        max_val++
                        it.name = max_val.toString()
                    }
                }

                filteredOverlays.forEach{
                    if (bColoured){
                        val col = GUIMain.roiColours[it.name.toInt() % GUIMain.roiColours.size]
                        it.fillColor = ColorRGB((255.0 * 0.0).toInt(), (255.0 * 0.0).toInt(), (255.0 * 0.0).toInt())
                        it.alpha = 0
                        it.lineColor = ColorRGB((255.0 * col.red).toInt(), (255.0 * col.green).toInt(), (255.0 * col.blue).toInt())
                    }
                }
                GUIMain.zoomService.zoomOriginalScale(display)
            }
            else{
                bNumOverlaysChanged = false
            }

            if (cam.layer != null) {
                val ic = canvas.dataToPanelCoords(RealCoords(3000.0, 3000.0))
                for (f in 0..filteredOverlays.size - 1) {
                    val com = cam.layer!!.getComponent(f) as JLabel
                    com.setBounds(ic.x, ic.y, 40, 20)
                }

                //number code using labels - buggy but mostly works
                //221224
                if (cam.numChannels > 1) {
                    val channelIx = cam.view!!.planePosition.getLongPosition(0)
                    for (f in 0..filteredOverlays.size - 1) {
                        var x_rc = 3000.0
                        var y_rc = 3000.0
                        var txt = "not used"
                        if (filteredOverlays[f] is RectangleOverlay) {
                            val rcc = filteredOverlays[f] as RectangleOverlay
                            if (channelIx == 0L) {
                                x_rc = rcc.getOrigin(0) + rcc.getExtent(0) / 2
                                y_rc = rcc.getOrigin(1) + rcc.getExtent(1) / 2
                            }

                            txt = rcc.name
                        } else if (filteredOverlays[f] is EllipseOverlay) {
                            val rcc = filteredOverlays[f] as EllipseOverlay
                            if (channelIx == 0L) {
                                x_rc = rcc.getOrigin(0)
                                y_rc = rcc.getOrigin(1)
                            }
                            txt = rcc.name
                        } else {

                        }

                        var zoom_level = canvas.zoomFactor

                        val ic = canvas.dataToPanelCoords(RealCoords(x_rc, y_rc))

                        if (zoom_level < 1.0) {

                            ic.x = (x_rc.toDouble() * zoom_level).toInt()
                            ic.y = (y_rc.toDouble() * zoom_level).toInt()
                        }

                        val com = cam.layer!!.getComponent(f) as JLabel
                        com.text = txt
                        com.setBounds(ic.x, ic.y, 40, 20)
                    }
                }
                else {
//                        val channelIx = cam.view!!.planePosition.getLongPosition(0)
                    for (f in 0..filteredOverlays.size - 1) {
                        var x_rc = 3000.0
                        var y_rc = 3000.0
                        var txt = "not used"
                        //get the pixel coordinates of each ROI overlay
                        if (filteredOverlays[f] is RectangleOverlay) {
                            val rcc = filteredOverlays[f] as RectangleOverlay
                            //in pixel coords
                            x_rc = rcc.getOrigin(0) + rcc.getExtent(0) / 2
                            y_rc = rcc.getOrigin(1) + rcc.getExtent(1) / 2
                            txt = rcc.name
                        } else if (filteredOverlays[f] is EllipseOverlay) {
                            val rcc = filteredOverlays[f] as EllipseOverlay
                            //in pixel coords
                            x_rc = rcc.getOrigin(0)
                            y_rc = rcc.getOrigin(1)
                            txt = rcc.name
                        } else {

                        }

                        val ic = canvas.dataToPanelCoords(RealCoords(x_rc, y_rc))
                        val com = cam.layer!!.getComponent(f) as JLabel
                        com.text = txt
                        com.setBounds(ic.x, ic.y, 40, 20)
                    }
                }
            }
            sleep(50)
        }
    }
}

//The CameraActor receives STRIMMImage samples from the akka graph
class CameraActor(val plugin: CameraWindowPlugin) : AbstractActor(){
    companion object {
        fun props(plugin: CameraWindowPlugin): Props {
            return Props.create<CameraActor>(CameraActor::class.java) { CameraActor(plugin) }
        }
    }

    var isResized = false
    var isAcquiring = true

    var timeLast  = 0.0
    var sinkName : String = ""
    var sink : Sink? = null
    var displayInfo : DisplayInfo? = null;
    var bColoured = true

    var zoomLevel = 1.0
    var panCenter : RealCoords? = null
    var viewportWidth = 0
    var viewportHeight = 0
    var bNormalise = false
    var doAutoStretch = false
    var autoStretchMinMax = Pair(0.0, 0.0)

    var camThread : MonitorCameraThread? = null
    var imageSequenceIntervalMs : Long = 100 //delay between showing bursts of images in response to a software trigger

    override fun createReceive(): Receive {
        return receiveBuilder()
            .match<Message>(Message::class.java) { message ->
                GUIMain.loggerService.log(Level.INFO, "Camera actor received basic message")
                plugin.cameraWindowController.associatedActor = self
                GUIMain.actorService.cameraActorDisplays[plugin.cameraWindowController.displayInfo!!.displayName] = self
            }
            .match<TellDisplaySink>(TellDisplaySink::class.java){sinkMessage->
                sink = sinkMessage.sink
            }
            .match<TellDisplayInfo>(TellDisplayInfo::class.java){displayInfoMessage->
                displayInfo = displayInfoMessage.displayInfo
            }
            .match<TellDisplayNormalise>(TellDisplayNormalise::class.java){normaliseMessage->
                GUIMain.loggerService.log(Level.INFO, "Setting normalise flag in ${self.path().name()} to ${normaliseMessage.bNormalise}")
                bNormalise = normaliseMessage.bNormalise
            }
            .match<TellAutoStretch>(TellAutoStretch::class.java){
                doAutoStretch = it.doAutoStretch
                if(doAutoStretch) {
                    autoStretchMinMax = it.minMax
                }
            }
            .match<StartStreaming>(StartStreaming::class.java){
                timeLast = 0.0
                plugin.cameraWindowController.initialiseDisplay()
                camThread = MonitorCameraThread(sink!!.sinkName, plugin.cameraWindowController,  plugin.cameraWindowController.display as ImageDisplay)
                if (camThread != null){
                    GUIMain.loggerService.log(Level.INFO, "Starting camera monitor thread for ${sink!!.sinkName}")
                    camThread!!.start()
                }
                sender().tell(Acknowledgement.INSTANCE, self())
            }
            .match<CompleteStreaming>(CompleteStreaming::class.java){
                if (camThread != null){
                    camThread!!.EndThread()
                }
                GUIMain.loggerService.log(Level.INFO, "Camera actor completed")
            }
            .match<FailStreaming>(FailStreaming::class.java){
                GUIMain.loggerService.log(Level.SEVERE, "Camera actor failed")
                if (camThread != null){
                    camThread!!.EndThread()
                }
                GUIMain.loggerService.log(Level.SEVERE, it.ex.message!!)
                var stackTraceString = ""
                it.ex.stackTrace.forEach { it2 -> stackTraceString += (it2.toString() + "\n") }
                GUIMain.loggerService.log(Level.SEVERE, stackTraceString)
            }
            .match<TerminateActor>(TerminateActor::class.java){
                GUIMain.loggerService.log(Level.INFO, "Camera actor received TerminateActor message")
                if (camThread != null) {
                    camThread!!.EndThread()
                }
                GUIMain.loggerService.log(Level.INFO, "Camera actor ${self.path().name()} terminating")
                self.tell(Kill.getInstance(), self)
                GUIMain.loggerService.log(Level.INFO, "Successfully terminated actor")
            }
            .match<List<*>>(List::class.java){ imm ->
                if (GUIMain.softwareTimerService.getTime() > timeLast + displayInfo!!.previewInterval) {
                    imm as List<STRIMMBuffer>
                    val flattenedData = flattenData(imm as List<List<STRIMMBuffer>>)
                    if (flattenedData[0].className == "STRIMMPixelBuffer"){
                        val im1 =  flattenedData[0] as STRIMMPixelBuffer
                        val w = displayInfo!!.width.toInt()
                        val h = displayInfo!!.height.toInt()
                        val pixelType = displayInfo!!.pixelType
                        val numChannels = displayInfo!!.numChannels

                        if ((w != im1.w || h != im1.h || im1.pixelType != pixelType || im1.numChannels != numChannels)){
                            GUIMain.loggerService.log(Level.SEVERE, "The info carried by the STRIMMBuffer is different to the Sink's configured expectations")
                            GUIMain.loggerService.log(Level.INFO, "STRIMMBuffer width=${im1.w} vs sink width=$w")
                            GUIMain.loggerService.log(Level.INFO, "STRIMMBuffer height=${im1.w} vs sink height=$h")
                            GUIMain.loggerService.log(Level.INFO, "STRIMMBuffer pixelType=${im1.pixelType} vs sink pixelType=$pixelType")
                            GUIMain.loggerService.log(Level.INFO, "STRIMMBuffer numChannels=${im1.numChannels} vs sink numChannels=$numChannels")
                        }

                        val dataset = plugin.cameraWindowController.dataset!!
                        //TODO in this when statement determine if "Int" and "Double" options are needed i.e. will images ever be of Int or Double pixel types?
                        when(im1.pixelType){
                            "Byte" -> {
                                val pix = im1.pix as ByteArray
                                for (ch in 0 until numChannels) {
                                    val imageSlice = pix.sliceArray((ch * w * h)..(((ch + 1) * w * h) - 1))
                                    if (ch < numChannels-1) {
                                        dataset.setPlaneSilently(ch, imageSlice)
                                    }
                                    else {
                                        dataset.setPlane(ch, imageSlice)
                                    }

                                    if (bNormalise) {
                                        val minMax : Pair<Double, Double> = if(doAutoStretch){
                                            autoStretchMinMax
                                        }
                                        else {
                                            val minPix = UByte.MIN_VALUE.toDouble()
                                            val maxPix = UByte.MAX_VALUE.toDouble()
                                            Pair(minPix, maxPix)
                                        }
                                        plugin.cameraWindowController.view?.setChannelRange(ch, minMax.first, minMax.second)
                                    }
                                    else{
                                        plugin.cameraWindowController.view?.setChannelRange(ch, UByte.MIN_VALUE.toDouble(), UByte.MAX_VALUE.toDouble())
                                    }
                                }
                            }
                            "Short" -> {
                                val pix = im1.pix as ShortArray
                                for (ch in 0 until numChannels) {
                                    val imageSlice = pix.sliceArray((ch * w * h)..(((ch + 1) * w * h) - 1))
                                    if (ch < numChannels-1) {
                                        dataset.setPlaneSilently(ch, imageSlice)
                                    }
                                    else {
                                        dataset.setPlane(ch, imageSlice)
                                    }

                                    if (bNormalise) {
                                        val minMax : Pair<Double, Double> = if(doAutoStretch){
                                            autoStretchMinMax
                                        }
                                        else {
                                            val minPix = UShort.MIN_VALUE.toDouble()
                                            val maxPix = UShort.MAX_VALUE.toDouble()
                                            Pair(minPix, maxPix)
                                        }

                                        plugin.cameraWindowController.view?.setChannelRange(ch, minMax.first, minMax.second)
                                    }
                                    else{
                                        plugin.cameraWindowController.view?.setChannelRange(ch, UShort.MIN_VALUE.toDouble(), UShort.MAX_VALUE.toDouble())
                                    }
                                }
                            }
                            "Float" -> {
                                //ImageJ sets crazy values for MAX and MIN
                                val pix = im1.pix as FloatArray
                                dataset.setPlane(0, pix)

                                if (bNormalise) {
                                    val minPix = pix.min().toDouble()
                                    val maxPix = pix.max().toDouble()
                                    plugin.cameraWindowController.view?.setChannelRange(0, minPix, maxPix)
                                }
                                else{
                                    plugin.cameraWindowController.view?.setChannelRange(0, Float.MIN_VALUE.toDouble(), Float.MAX_VALUE.toDouble())
                                }
                            }
                            "Int" -> {
                                val pix = im1.pix as IntArray
                                for (ch in 0..numChannels-1) {
                                    val imageSlice = pix.sliceArray((ch * w * h)..(((ch + 1) * w * h) - 1))
                                    if (ch < numChannels-1) {
                                        dataset.setPlaneSilently(ch, imageSlice)
                                    }
                                    else {
                                        dataset.setPlane(ch, imageSlice)
                                    }

                                    if (bNormalise) {
                                        val minPix = imageSlice.min().toDouble()
                                        val maxPix = imageSlice.max().toDouble()
                                        plugin.cameraWindowController.view?.setChannelRange(ch, minPix, maxPix)
                                    }
                                    else{
                                        plugin.cameraWindowController.view?.setChannelRange(ch, UInt.MIN_VALUE.toDouble(), UInt.MAX_VALUE.toDouble())
                                    }
                                }
                            }
                            "Double" -> {
                                val pix = im1.pix as DoubleArray
                                dataset.setPlane(0, pix)

                                if (bNormalise) {
                                    val minPix = pix.min()
                                    val maxPix = pix.max()
                                    plugin.cameraWindowController.view?.setChannelRange(0, minPix, maxPix)
                                }
                                else{
                                    plugin.cameraWindowController.view?.setChannelRange(0, Double.MIN_VALUE, Double.MAX_VALUE)
                                }
                            }
                        }

                        //Tells SciJava/ImageJ services dataset has been altered and needs to be updated
                        dataset.isDirty = true
                    }
                    //STRIMMSequenceCamwraDataBuffer is a group of images taken by a fast camera
                    else if (flattenedData[0].className == "STRIMMSequenceCameraDataBuffer"){
//                        val imageList1 = (imm[0] as STRIMMSequenceCameraDataBuffer).data
                        val imageList1 = flattenedData
                        for (ffff in 0..imageList1.size-1){
                            var im1 =  imageList1[ffff] as STRIMMPixelBuffer
                            //println("dataID " + im1.dataID)
                            val w = displayInfo!!.width.toInt()
                            val h = displayInfo!!.height.toInt()
                            val pixelType = displayInfo!!.pixelType
                            val numChannels = displayInfo!!.numChannels
                            if (w != im1.w || h != im1.h || im1.pixelType != pixelType || im1.numChannels != numChannels){
                                GUIMain.loggerService.log(Level.SEVERE, "The info carried by the STRIMMBuffer is different to the Sink's configured expectations")
                                GUIMain.loggerService.log(Level.INFO, "The info carried by the STRIMMBuffer is different to the Sink's configured expectations")
                                GUIMain.loggerService.log(Level.INFO, "STRIMMBuffer width=${im1.w} sink width=$w")
                                GUIMain.loggerService.log(Level.INFO, "STRIMMBuffer height=${im1.w} sink height=$h")
                                GUIMain.loggerService.log(Level.INFO, "STRIMMBuffer pixelType=${im1.pixelType} sink pixelType=$pixelType")
                                GUIMain.loggerService.log(Level.INFO, "STRIMMBuffer numChannels=${im1.numChannels} sink numChannels=$numChannels")
                            }
                            val dataset = plugin.cameraWindowController.dataset!!
                            if (im1.pixelType == "Byte"){
                                val pix = im1.pix as ByteArray
                                for (ch in 0..numChannels-1) {
                                    if (ch < numChannels-1) dataset.setPlaneSilently(ch, pix.sliceArray((ch * w * h)..(((ch + 1) * w * h) - 1)))
                                    else dataset.setPlane(ch, pix.sliceArray((ch * w * h)..(((ch + 1) * w * h) - 1)))

                                    if (bNormalise) {
                                        val minMax = pix.fold(
                                            Pair(
                                                Float.MAX_VALUE.toDouble(),
                                                Float.MIN_VALUE.toDouble()
                                            )
                                        ) { acc, v ->
                                            Pair(
                                                kotlin.math.min(acc.first, v.toDouble()),
                                                kotlin.math.max(acc.second, v.toDouble())
                                            )
                                        }
                                        plugin.cameraWindowController.view?.setChannelRange(ch, minMax.first, minMax.second)
                                    }
                                }
                            }
                            else if (im1.pixelType == "Short"){
                                val pix = im1.pix as ShortArray
                                for (ch in 0..numChannels-1) {
                                    if (ch < numChannels-1) dataset.setPlaneSilently(ch, pix.sliceArray((ch * w * h)..(((ch + 1) * w * h) - 1)))
                                    else dataset.setPlane(ch, pix.sliceArray((ch * w * h)..(((ch + 1) * w * h) - 1)))

                                    if (bNormalise) {
                                        val minMax = pix.fold(
                                            Pair(
                                                Float.MAX_VALUE.toDouble(),
                                                Float.MIN_VALUE.toDouble()
                                            )
                                        ) { acc, v ->
                                            Pair(
                                                kotlin.math.min(acc.first, v.toDouble()),
                                                kotlin.math.max(acc.second, v.toDouble())
                                            )
                                        }
                                        plugin.cameraWindowController.view?.setChannelRange(ch, minMax.first, minMax.second)
                                    }


                                }
                            }
                            else if (im1.pixelType == "Int"){
                                val pix = im1.pix as IntArray
                                for (ch in 0..numChannels-1) {
                                    if (ch < numChannels-1) dataset.setPlaneSilently(ch, pix.sliceArray((ch * w * h)..(((ch + 1) * w * h) - 1)))
                                    else dataset.setPlane(ch, pix.sliceArray((ch * w * h)..(((ch + 1) * w * h) - 1)))

                                    if (bNormalise) {
                                        val minMax = pix.fold(
                                            Pair(
                                                Float.MAX_VALUE.toDouble(),
                                                Float.MIN_VALUE.toDouble()
                                            )
                                        ) { acc, v ->
                                            Pair(
                                                kotlin.math.min(acc.first, v.toDouble()),
                                                kotlin.math.max(acc.second, v.toDouble())
                                            )
                                        }
                                        plugin.cameraWindowController.view?.setChannelRange(ch, minMax.first, minMax.second)
                                    }
                                }
                            }
                            else if (im1.pixelType == "Float"){
                                val pix = im1.pix as FloatArray
                                dataset.setPlane(0, pix)
                                //ImageJ sets crazy values for MAX and MIN
                                if (bNormalise) {
                                    val minMax = pix.fold(
                                        Pair(
                                            Float.MAX_VALUE.toDouble(),
                                            Float.MIN_VALUE.toDouble()
                                        )
                                    ) { acc, v ->
                                        Pair(
                                            kotlin.math.min(acc.first, v.toDouble()),
                                            kotlin.math.max(acc.second, v.toDouble())
                                        )
                                    }
                                    plugin.cameraWindowController.view?.setChannelRange(0, minMax.first, minMax.second)
                                }
                            }
                            else if (im1.pixelType == "Double"){
                                val pix = im1.pix as DoubleArray
                                dataset.setPlane(0, pix)
                                //ImageJ sets crazy values for MAX and MIN
                                if (bNormalise) {
                                    val minMax = pix.fold(
                                        Pair(
                                            Float.MAX_VALUE.toDouble(),
                                            Float.MIN_VALUE.toDouble()
                                        )
                                    ) { acc, v ->
                                        Pair(
                                            kotlin.math.min(acc.first, v.toDouble()),
                                            kotlin.math.max(acc.second, v.toDouble())
                                        )
                                    }
                                    plugin.cameraWindowController.view?.setChannelRange(0, minMax.first, minMax.second)
                                }
                            }

                            dataset.isDirty = true

                            Thread.sleep(imageSequenceIntervalMs)
                        }
                    }

                    timeLast = GUIMain.softwareTimerService.getTime()
                }
                sender().tell(Acknowledgement.INSTANCE, self())
            }
            .matchAny{}
            .build()
    }
}