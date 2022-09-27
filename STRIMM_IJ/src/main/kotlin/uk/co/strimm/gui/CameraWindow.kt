package uk.co.strimm.gui

import akka.actor.ActorRef
import bibliothek.gui.dock.common.DefaultMultipleCDockable
import net.imagej.Dataset
import net.imagej.axis.Axes
import net.imagej.display.DatasetView
import net.imagej.display.ImageDisplay
import net.imglib2.type.numeric.integer.UnsignedByteType
import net.imglib2.type.numeric.integer.UnsignedShortType
import net.imglib2.type.numeric.real.FloatType
import org.scijava.display.Display
import org.scijava.plugin.Plugin
import org.scijava.ui.swing.viewer.SwingDisplayWindow
import org.scijava.ui.viewer.DisplayWindow
import uk.co.strimm.CameraDeviceInfo
import uk.co.strimm.DisplayInfo
import uk.co.strimm.MicroManager.MMCameraDevice
import uk.co.strimm.plugins.AbstractDockableWindow
import uk.co.strimm.plugins.DockableWindowPlugin
import java.awt.Robot
import java.awt.event.*
import java.io.File
import java.util.*
import javax.swing.JPanel


@Plugin(type = DockableWindowPlugin::class, menuPath = "Window>Camera Feed")
class CameraWindowPlugin : AbstractDockableWindow() {
    override var title = ""
    lateinit var cameraWindowController : CameraWindow

    override fun
    setCustomData(data: Any?) {
        if(data is CameraDeviceInfo){
          cameraWindowController.cameraDevice = data.device
        }
        else if (data is DisplayInfo){
            cameraWindowController.displayInfo = data
            dockableWindowMultiple.titleText = data.feedName
        }
        else if(data is Dataset){
            cameraWindowController.dataset = data
       }
    }

    override var dockableWindowMultiple : DefaultMultipleCDockable = run{
        this.createDock(title).apply{
            add(windowPanel)
            this.titleText = title
            dockableWindowMultiple = this
            cameraWindowController = CameraWindow(windowPanel)
        }
    }
}

class CameraWindow constructor(val windowPanel: JPanel){

    var displayInfo : DisplayInfo? = null
    var dataset : Dataset? = null
    var display : Display<*>? = null
    var view : DatasetView? = null
    var displayWindow : DisplayWindow? = null
    var associatedActor : ActorRef? = null
    var cameraWidth = 100.toLong() //Dummy default TODO link to setting
    var cameraHeight = 100.toLong() //Dummy default TODO link to setting
    var cameraDevice : MMCameraDevice? = null
    var datasetName = "Dataset" + Random().nextInt(1000000)//This will eventually be
    var lutSz = "" //lut for this display
    var roiSz = "" //the roiFlow associated with this display

    /**
     * This method is called when a new camera display needs to be initialised, with a specific width an height however.
     * This will only ever be called after initialiseDisplay() (no size parameters) as we need something to resize in
     * the first place
     * @param width The new width of the camera feed
     * @param height The new height of the camera feed
     */
    fun initialiseDisplay(width : Long, height: Long){

        java.awt.EventQueue.invokeLater {
            run {
                if (windowPanel.componentCount > 0) {
                    windowPanel.remove(0)
                }

                windowPanel.updateUI()

            }
        }
    }

    /**
     * This is called when the camera feed is first created.
     */
    fun initialiseDisplay(){
         if(windowPanel.componentCount > 0) {
            windowPanel.remove(0)
        }

        windowPanel.updateUI()
        if(dataset == null) {
            println(displayInfo!!.width.toString() + "  " + displayInfo!!.height.toString() + "   " + displayInfo!!.feedName + "   " + displayInfo!!.bitDepth)
            createDatasetAndAddToPanel(displayInfo!!.width, displayInfo!!.height, displayInfo!!.feedName, displayInfo!!.bitDepth)
        }
        else{
            createDisplayAndAddToPanel()
        }

        windowPanel.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if(e.button == MouseEvent.BUTTON3){
                    println("RIGHT CLICK1")
                }
            }
        })


    }

    /**
     * This method is similar to createDatasetAndAddToPanel() however, it is used when the Camera Window's dataset
     * already exists. This is therefore only used when loading a previous experiment. This also contains a bug fix
     * where we need to force a keypress
     */
    private fun createDisplayAndAddToPanel(){
      //  println(this.toString() + " createDisplayAndAddToPanel()")
        display = GUIMain.displayService.createDisplayQuietly(dataset)

        view = (display as ImageDisplay).activeView as DatasetView

        //Create a display window to actually display the image
        displayWindow = GUIMain.uiService.defaultUI.createDisplayWindow(display)

        GUIMain.uiService.viewerPlugins
            .map { GUIMain.pluginService.createInstance(it) }
            .find { it != null && it.canView(display) && it.isCompatible(GUIMain.uiService.defaultUI) }
            ?.let {
                GUIMain.threadService.queue {
                    (displayWindow as SwingDisplayWindow).apply {
                        GUIMain.uiService.addDisplayViewer(it)
                        it.view(this, display)
                        pack()
                        val rootPane = this.rootPane
                        windowPanel.add(rootPane)
                    }
                }
            }
    }

    /**
     * Common method to create a dataset and associated ImageJ components to allow for image display. Note - as this is
     * only a display (not a store) the dataset is one planar image that is overwritten with every new image
     * @param cam The camera device for this feed
     * @param width The width of the camera feed
     * @param height The height of the camera feed
     */

    //could create a dataset/panel which is not attached to a camera
    private fun createDatasetAndAddToPanel(width: Long, height: Long, label : String, bytesPerPixel : Long) {

        datasetName = label
        // println(this.toString() + " createDatasetAndAddToPanel(width: Long, height: Long) " + datasetName)
        dataset = when (bytesPerPixel) {
            8L ->
                GUIMain.datasetService.create(
                    UnsignedByteType(),
                    longArrayOf(width, height),
                    datasetName,
                    arrayOf(Axes.X, Axes.Y)
                )
            16L ->
                GUIMain.datasetService.create(
                    UnsignedShortType(),
                    longArrayOf(width, height),
                    datasetName,
                    arrayOf(Axes.X, Axes.Y)
                )
            32L ->
                GUIMain.datasetService.create(
                    FloatType(),
                    longArrayOf(width, height),
                    datasetName,
                    arrayOf(Axes.X, Axes.Y)
                )
            else -> null
        }

        //Create a display in the background

        display = GUIMain.displayService.createDisplayQuietly(dataset)

        view = (display as ImageDisplay).activeView as DatasetView
      //  view = ((display as DefaultImageDisplay).activeView as DefaultDatasetView)

        if (lutSz != "") {
            var colorTable = GUIMain.lutService.loadLUT(File(".\\luts\\" + lutSz))
            (view as DatasetView).setColorTable(colorTable, 0)
        }
        //Create a display window to actually display the image
        displayWindow = GUIMain.uiService.defaultUI.createDisplayWindow(display)

        if (roiSz != "") {

            var overlays = GUIMain.actorService.routedRoiOverlays.filter { it.value == roiSz }.keys.toList()
            GUIMain.overlayService.addOverlays(display as ImageDisplay, overlays)

      }

        pressMinusButton()

        GUIMain.uiService.viewerPlugins
            .map {
                GUIMain.pluginService.createInstance(it) }
            .find {
                it != null && it.canView(display) && it.isCompatible(GUIMain.uiService.defaultUI) }
            ?.let {
                GUIMain.threadService.queue {
                    (displayWindow as SwingDisplayWindow).apply {
                        GUIMain.uiService.addDisplayViewer(it)
                        it.view(this, display)

                        pack()
                        val rootPane = this.rootPane
                        windowPanel.add(rootPane)
                        windowPanel.addFocusListener(object : FocusAdapter(){
                            override fun focusGained(e: FocusEvent?) {
//                                println("FOCUS GAINED")
                                super.focusGained(e)
                            }

                            override fun focusLost(e: FocusEvent?) {
//                                println("FOCUS LOST")
                                super.focusLost(e)
                            }
                        })
                    }
                }
            }
    }


    fun pressMinusButton(){
        /*
        * We have to force a minus sign keypress due to a Swing SDI UI bug otherwise the display
        * will just be white
        * Mentioned here: https://imagej.net/2012-08-01_-_Loading_and_displaying_a_dataset_with_the_ImageJ2_API
        */
        windowPanel.requestFocus()
        val robot = Robot()
        robot.keyPress(KeyEvent.VK_MINUS)
    }

}