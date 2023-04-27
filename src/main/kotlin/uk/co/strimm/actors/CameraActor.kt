package uk.co.strimm.actors

import akka.actor.AbstractActor
import akka.actor.Kill
import akka.actor.Props
import net.imagej.display.ImageDisplay
import net.imagej.overlay.EllipseOverlay
import net.imagej.overlay.Overlay
import net.imagej.overlay.RectangleOverlay
import org.scijava.util.ColorRGB
import org.scijava.util.IntCoords
import org.scijava.util.RealCoords
import scala.Byte
import uk.co.strimm.*
import uk.co.strimm.actors.messages.Message
import uk.co.strimm.actors.messages.ask.AskMessageTest
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
import javax.swing.JLayeredPane
import kotlin.math.max

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
            val can = display.canvas




           // println("camera thread " + can.zoomFactor)
            val overlays = GUIMain.overlayService.getOverlays() //total number of overlays

            val over = GUIMain.overlayService.getOverlays(display) //overlays in disp but might be reapeted
            val filteredOverlays = overlays.filter{it in over}

            if (overlays.size != numOverlays){
                numOverlays = overlays.size
                bNumOverlaysChanged = true



                println("number of overlays in " + display + " " + filteredOverlays.size)
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
                println("****")

                filteredOverlays.forEach{
                    println(it.name)
                    if (bColoured){

                        val col = GUIMain.roiColours[it.name.toInt() % GUIMain.roiColours.size]
                        it.fillColor = ColorRGB((255.0 * 0.0).toInt(), (255.0 * 0.0).toInt(), (255.0 * 0.0).toInt())
                        it.alpha = 0
                        it.lineColor = ColorRGB((255.0 * col.red).toInt(), (255.0 * col.green).toInt(), (255.0 * col.blue).toInt())


                    }
                }
                println("**")
                GUIMain.zoomService.zoomOriginalScale(display)
            }
            else{
                bNumOverlaysChanged = false

            }


            if (cam.layer != null) {
                val ic = can.dataToPanelCoords(RealCoords(3000.0, 3000.0))
                for (f in 0..filteredOverlays.size - 1) {
                    val com = cam.layer!!.getComponent(f) as JLabel
                    com.setBounds(ic.x, ic.y, 40, 20)
                }
//
//

                //number code using labels - buggy but mostly works
                //221224
                if (  cam.numChannels > 1) {
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

                        var zoom_level = can.zoomFactor



                        val ic = can.dataToPanelCoords(RealCoords(x_rc, y_rc))

                        if (zoom_level < 1.0) {

                            ic.x = (x_rc.toDouble() * zoom_level).toInt()
                            ic.y = (y_rc.toDouble() * zoom_level).toInt()

                        }

//                        println("*****AAAA")
//                        println("zoom " + zoom_level)
//                        println(ic.x.toString() + " " + ic.y.toString())
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

                        val ic = can.dataToPanelCoords(RealCoords(x_rc, y_rc))
                        val com = cam.layer!!.getComponent(f) as JLabel
                        com.text = txt
                        com.setBounds(ic.x, ic.y, 40, 20)


                    }
                }
            }







            Thread.sleep(50)
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

    var camThread : MonitorCameraThread? = null
    var imageSequenceIntervalMs : Long = 100 //delay between showing bursts of images in response to a software trigger


    override fun createReceive(): Receive {
        return receiveBuilder()
            .match<Message>(Message::class.java) { message ->
                println("cameraActor <MESSAGE>")
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
                bNormalise = normaliseMessage.bNormalise
            }
            .match<StartStreaming>(StartStreaming::class.java){
                timeLast = 0.0
                plugin.cameraWindowController.initialiseDisplay()
                camThread = MonitorCameraThread(sink!!.sinkName, plugin.cameraWindowController,  plugin.cameraWindowController.display as ImageDisplay)
                if (camThread != null){
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
                println("terminate actor ***********")
                if (camThread != null) {
                    camThread!!.EndThread()
                }
                GUIMain.loggerService.log(Level.INFO, "Camera actor ${self.path().name()} terminating")
                self.tell(Kill.getInstance(), self)
                println("success terminate actor *******")
            }
            .match<List<*>>(List::class.java){ imm ->
                if (GUIMain.softwareTimerService.getTime() > timeLast + displayInfo!!.previewInterval) {
                    //check if there are new runtime rois and give a new max number as an index
//                    val overlays = GUIMain.overlayService.getOverlays()
//                    val disp = GUIMain.experimentService.experimentStream.cameraDisplays[sink!!.sinkName]
//                    val over = GUIMain.overlayService.getOverlays(disp) //overlays in disp but might be reapeted
//                    val filteredOverlays = overlays.filter{it in over}
//                    val can = (plugin.cameraWindowController.display as ImageDisplay).canvas
                    //this 'switch' statement cannot distinguish types of List hence
                    //says List<*> so will match all lists
                    //Hence the addition of a className to STRIMMBuffer in order to differentiate
                    //type in situations like this

                    imm as List<STRIMMBuffer>
                    //STRIMMPixelBuffer is a single image
                    if (imm[0].className == "STRIMMPixelBuffer"){
                        val imageList1 = imm as List<STRIMMPixelBuffer>
                        var im1 =  imageList1[0]
                        //println("dataID " + im1.dataID)
                        val w = displayInfo!!.width.toInt()
                        val h = displayInfo!!.height.toInt()
                        val pixelType = displayInfo!!.pixelType
                        val numChannels = displayInfo!!.numChannels
                        if (w != im1.w || h != im1.h || im1.pixelType != pixelType || im1.numChannels != numChannels){
                            println("ERROR - the info carried by the STRIMMBuffer is different to the Sink's configured expectations")
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

                            //normalisation


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

                        dataset.isDirty = true   //what does this do?

                    }
                    //STRIMMSequenceCamwraDataBuffer is a group of images taken by a fast camera
                    else if (imm[0].className == "STRIMMSequenceCameraDataBuffer"){

                        val imageList1 = (imm[0] as STRIMMSequenceCameraDataBuffer).data
                        for (ffff in 0..imageList1.size-1){
                            var im1 =  imageList1[ffff]
                            //println("dataID " + im1.dataID)
                            val w = displayInfo!!.width.toInt()
                            val h = displayInfo!!.height.toInt()
                            val pixelType = displayInfo!!.pixelType
                            val numChannels = displayInfo!!.numChannels
                            if (w != im1.w || h != im1.h || im1.pixelType != pixelType || im1.numChannels != numChannels){
                                println("ERROR - the info carried by the STRIMMBuffer is different to the Sink's configured expectations")
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

                                //normalisation


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
            .matchAny{
            }
            .build()
    }

}