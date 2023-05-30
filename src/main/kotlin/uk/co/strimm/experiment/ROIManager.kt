package uk.co.strimm.experiment

import net.imagej.overlay.*
import uk.co.strimm.gui.GUIMain
import java.util.logging.Level

/**
 * The ROI manager will handle tasks relating to creating both ImageJ Overlays and STRIMM ROI objects for use in the
 * experiment stream
 */
class ROIManager{
    companion object {
        /**
         * From an ImageJ overlay, create an ROI object that can then be used in experiment configuration
         * @param overlay The ImageJ overlay,
         * @param overlayBaseName The base name for the overlay (comes from the source node in the experiment)
         * @param deviceLabel The label of the image device who's display the ImageJ overlay was drawn on
         * @return The newly created ROI object
         */
        fun createROIObjectFromOverlay(overlay : Overlay, overlayBaseName : String, deviceLabel : String, flowName: String) : ROI{
            //get information from an overlay and put into a ROI()
            val roiToReturn = uk.co.strimm.experiment.ROI()
            //roiToReturn.ROIName = GUIMain.experimentService.makeUniqueROIName(overlayBaseName)
            roiToReturn.cameraDeviceLabel = deviceLabel
            roiToReturn.flowName = flowName

            when(overlay){
                is EllipseOverlay -> {
                    roiToReturn.ROItype = "ELLIPSE"
                    roiToReturn.x = overlay.getOrigin(0)
                    roiToReturn.y = overlay.getOrigin(1)
                    roiToReturn.w = overlay.getRadius(0)
                    roiToReturn.h = overlay.getRadius(1)
                }
                is RectangleOverlay -> {
                    roiToReturn.ROItype = "RECTANGLE"
                    roiToReturn.x = overlay.getOrigin(0)
                    roiToReturn.y = overlay.getOrigin(1)
                    roiToReturn.w = overlay.getExtent(0)
                    roiToReturn.h = overlay.getExtent(1)
                }
                is PolygonOverlay -> {
                    //TODO
                }
            }

            return roiToReturn
        }

        /**
         * From an ROI object, create an ImageJ overlay to be drawn on a display
         * @param roi The ROI object from the experiment configuration
         * @return The newly create ImageJ overlay
         */
        fun createOverlayFromROIObject(roi: ROI) : Overlay?{
            when {
                roi.ROItype == "RECTANGLE" -> {
                    //make and fill in an Overlay
                    val rectangleOverlay = RectangleOverlay(GUIMain.imageJService.context)
                    rectangleOverlay.setOrigin(roi.x, 0)
                    rectangleOverlay.setOrigin(roi.y, 1)
                    rectangleOverlay.setExtent(roi.w, 0)
                    rectangleOverlay.setExtent(roi.h, 1)
                    val ds = GUIMain.overlayService.defaultSettings
                    rectangleOverlay.alpha = ds.alpha
                    rectangleOverlay.fillColor = ds.fillColor
                    rectangleOverlay.lineColor = ds.lineColor
                    rectangleOverlay.lineStyle = ds.lineStyle
                    rectangleOverlay.lineWidth = ds.lineWidth

                    //The roi name at this point should already be unique from earlier specification (see TraceFromROIContext)
                    rectangleOverlay.name = roi.ROIName

                    return rectangleOverlay
                }
                roi.ROItype == "ELLIPSE" -> {
                    val ellipseOverlay = EllipseOverlay(GUIMain.imageJService.context)
                    ellipseOverlay.setOrigin(roi.x, 0)
                    ellipseOverlay.setOrigin(roi.y, 1)
                    ellipseOverlay.setRadius(roi.w, 0)
                    ellipseOverlay.setRadius(roi.h, 1)
                    val ds = GUIMain.overlayService.defaultSettings
                    ellipseOverlay.alpha = ds.alpha
                    ellipseOverlay.fillColor = ds.fillColor
                    ellipseOverlay.lineColor = ds.lineColor
                    ellipseOverlay.lineStyle = ds.lineStyle
                    ellipseOverlay.lineWidth = ds.lineWidth
                    //The roi name at this point should already be unique from earlier specification (see TraceFromROIContext)
                    ellipseOverlay.name = roi.ROIName
                    return ellipseOverlay
                }
                roi.ROItype == "POLYGON" -> {
                    //TODO
                    return PolygonOverlay()
                }
                else -> {
                    GUIMain.loggerService.log(Level.SEVERE, "Could not create overlay. Property 'ROIType' not recognised: ${roi.ROItype}")
                    return null
                }
            }
        }
    }
}