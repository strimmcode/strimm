package uk.co.strimm.services

import akka.actor.ActorRef
import bibliothek.gui.dock.common.CControl
import bibliothek.gui.dock.common.CGrid
import net.imagej.ChannelCollection
import net.imagej.ImageJService
import net.imagej.display.ImageDisplay
import net.imagej.overlay.EllipseOverlay
import net.imagej.overlay.Overlay
import net.imagej.overlay.RectangleOverlay
import org.scijava.plugin.Plugin
import org.scijava.service.AbstractService
import org.scijava.service.Service
import org.scijava.util.ColorRGB
import uk.co.strimm.ComponentTexts
import uk.co.strimm.ResizeValues
import uk.co.strimm.actors.messages.tell.TellCameraResize
import uk.co.strimm.actors.messages.tell.TellFullView
import uk.co.strimm.gui.CameraWindowPlugin
import uk.co.strimm.gui.GUIMain
import java.util.logging.Level
import javax.swing.JFrame
import javax.swing.JMenuBar
import javax.swing.JOptionPane

@Plugin(type = Service::class)
class StrimmUIService : AbstractService(), ImageJService {
    var dockableControl = CControl()
    var cGrid = CGrid(dockableControl)
    var strimmFrame = JFrame("STRIMM")
    var imageJMenuBar = JMenuBar()
    var autoScaleCheck = false
    var state = UIstate.IDLE
    var currentFocusedDockableTitle = ""
    var traceFromROICounter = 0
    var traceFromROICounterPerDisplayROIFlow = hashMapOf<String, Int>() //indexed by ROIFlow name
    var traceColourByFlowAndOverlay = hashMapOf<String, List<Pair<Overlay, Int>>>()
    //indexed by ROIFlow to give the overlay and the color int

    var traceColourNonROI = 0

    fun SetROI(label : String, x : Long, y : Long, w : Long, h : Long) : Int {
        val camSizes = Pair(w, h)
        GUIMain.strimmUIService.cameraSizeList[label] = camSizes
        return 0
    }
    //cameraSizeList is a list of the full sizes for camera feeds
    public var cameraSizeList = hashMapOf<String?, Pair<Long?,Long?>>()

    //cameraViewSizeList is a list of the view sizes for each camera, this is used when the user resizes a camera feed
    public var cameraViewSizeList = hashMapOf<String?, ResizeValues>()

    /**
     * This method is called when the user resizes a camera feed to an ROI they have drawn. This only works for
     * rectangular ROIs
     * @param cameraFeedDisplay The image display corresponding to the camera feed
     */
    fun resizeCameraFeedToROI(cameraFeedDisplay : ImageDisplay){
        GUIMain.loggerService.log(Level.INFO, "Resizing camera feed to ROI")
        val selectedROI = GUIMain.overlayService.getActiveOverlay(cameraFeedDisplay)
        when (selectedROI) {
            is RectangleOverlay -> {
                val x = selectedROI.getOrigin(0)
                val y = selectedROI.getOrigin(1)
                val w = selectedROI.getExtent(0)
                val h = selectedROI.getExtent(1)
                val cameraActor = GUIMain.actorService.getPertainingCameraActorFromDisplay(cameraFeedDisplay)
                cameraActor!!.tell(TellCameraResize(x,y,w,h), ActorRef.noSender())
            }
            is EllipseOverlay -> JOptionPane.showMessageDialog(strimmFrame, ComponentTexts.AcquisitionDialogs.RESIZE_MUST_BE_RECT)
            else -> JOptionPane.showMessageDialog(strimmFrame, ComponentTexts.AcquisitionDialogs.COULD_NOT_FIND_ROI_FOR_RESIZE)
        }

    }

    fun enableStartStopButton(){
        GUIMain.expStartStopButton.isEnabled = true
    }
    /**
     * This method is called when the user expands an image feed to full size view
     * @param cameraFeedDisplay The image display corresponding to the camera feed
     */
    fun expandCameraFeedToFullView(cameraFeedDisplay : ImageDisplay){
        GUIMain.loggerService.log(Level.INFO, "Expanding camera feed to full view")
        val cameraActor = GUIMain.actorService.getPertainingCameraActorFromDisplay(cameraFeedDisplay)
        cameraActor!!.tell(TellFullView(), ActorRef.noSender())
    }

    fun redrawROIs(cameraWindowPlugin: CameraWindowPlugin, editable : Boolean){
        //function never used
        //get the display from the CameraWindow
        val display = (cameraWindowPlugin.cameraWindowController.display as ImageDisplay)
        println("Redrawing overlay")
        //create a RectangleOverlay and fill in details  text?
        val newROI = RectangleOverlay() // IJ structure
        newROI.setOrigin(250.0,0)
        newROI.setOrigin(700.0,1)
        newROI.setExtent(100.0,0)
        newROI.setExtent(100.0,1)
        newROI.regionOfInterest.setOrigin(250.0,0)
        newROI.regionOfInterest.setOrigin(700.0,1)
        newROI.regionOfInterest.setExtent(100.0,0)
        newROI.regionOfInterest.setExtent(100.0,1)
        newROI.lineColor = ColorRGB(125,125,125)
        newROI.fillColor = ColorRGB(255,255,255)
        newROI.lineStyle = Overlay.LineStyle.SOLID
        newROI.alpha = 100

        java.awt.EventQueue.invokeLater {
            val overlays1 = GUIMain.overlayService.getOverlays(display)
            println("1 ${overlays1.size}")
            GUIMain.overlayService.drawOverlay(newROI, display, ChannelCollection())
        }

        java.awt.EventQueue.invokeLater {
            val overlays2 = GUIMain.overlayService.getOverlays(display)
            println("2 ${overlays2.size}")
        }
    }
}

enum class UIstate {
    IDLE,
    PREVIEW,
    LIVE;
}