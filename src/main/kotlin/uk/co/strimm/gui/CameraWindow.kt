package uk.co.strimm.gui

import akka.actor.ActorRef
import bibliothek.gui.dock.common.DefaultMultipleCDockable
import net.imagej.*
import net.imagej.axis.Axes
import net.imagej.display.DatasetView
import net.imagej.display.ImageDisplay
import net.imglib2.type.numeric.integer.UnsignedByteType
import net.imglib2.type.numeric.integer.UnsignedIntType
import net.imglib2.type.numeric.integer.UnsignedLongType
import net.imglib2.type.numeric.integer.UnsignedShortType
import net.imglib2.type.numeric.real.DoubleType
import net.imglib2.type.numeric.real.FloatType
import org.scijava.display.Display
import org.scijava.plugin.Plugin
import org.scijava.ui.swing.viewer.SwingDisplayWindow
import org.scijava.ui.viewer.DisplayWindow
import uk.co.strimm.DisplayInfo
import uk.co.strimm.experiment.Sink
import uk.co.strimm.plugins.AbstractDockableWindow
import uk.co.strimm.plugins.DockableWindowPlugin
import java.awt.Color
import java.awt.Font
import java.awt.Robot
import java.awt.event.*
import java.io.File
import java.util.*
import javax.swing.*


@Plugin(type = DockableWindowPlugin::class, menuPath = "Window>Camera Feed")
class CameraWindowPlugin : AbstractDockableWindow() {
    override var title = ""
    lateinit var cameraWindowController: CameraWindow

    override fun
            setCustomData(data: Any?) {
        if (data is DisplayInfo) {
            cameraWindowController.displayInfo = data
            dockableWindowMultiple.titleText = data.displayName
        } else if (data is Dataset) {
            cameraWindowController.dataset = data
        }
    }

    override var dockableWindowMultiple: DefaultMultipleCDockable = run {
        this.createDock(title).apply {
            add(windowPanel)
            this.titleText = title
            dockableWindowMultiple = this
            cameraWindowController = CameraWindow(windowPanel)
        }
    }
}

class CameraWindow constructor(val windowPanel: JPanel) {

    var displayInfo: DisplayInfo? = null
    var dataset: Dataset? = null
    var display: Display<*>? = null
    var view: DatasetView? = null
    var displayWindow: DisplayWindow? = null
    var associatedActor: ActorRef? = null
    var datasetName = "Dataset" + Random().nextInt(1000000)//This will eventually be

    var numChannels = 0

    var sink: Sink? = null

    var layer: JLayeredPane? = null

    /**
     * This method is called when a new camera display needs to be initialised, with a specific width an height however.
     * This will only ever be called after initialiseDisplay() (no size parameters) as we need something to resize in
     * the first place
     * @param width The new width of the camera feed
     * @param height The new height of the camera feed
     */
    fun initialiseDisplay(width: Long, height: Long) {
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
    fun initialiseDisplay() {
        if (windowPanel.componentCount > 0) {
            windowPanel.remove(0)
        }

        windowPanel.updateUI()
        if (dataset == null) {
            println(displayInfo!!.width.toString() + "  " + displayInfo!!.height.toString() + "   " + displayInfo!!.displayName + "   " + displayInfo!!.pixelType + "  " + displayInfo!!.numChannels)
            createDatasetAndAddToPanel(
                displayInfo!!.width,
                displayInfo!!.height,
                displayInfo!!.displayName,
                displayInfo!!
            )
        }
        else {
            createDisplayAndAddToPanel()
        }

        windowPanel.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.button == MouseEvent.BUTTON3) {
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
    private fun createDisplayAndAddToPanel() {
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

    //could create a dataset/panel which is not attached to a camera
    private fun createDatasetAndAddToPanel(width: Long, height: Long, label: String, displayInfo: DisplayInfo) {
        datasetName = label
        var lutSz = displayInfo.lut
        numChannels = displayInfo.numChannels
        dataset = when (displayInfo.pixelType) {
            "Byte" ->
                if (displayInfo.numChannels == 1) {
                    GUIMain.datasetService.create(
                        UnsignedByteType(),
                        longArrayOf(width, height),
                        datasetName,
                        arrayOf(Axes.X, Axes.Y))
                }
                else {
                    GUIMain.datasetService.create(UnsignedByteType(),
                    longArrayOf(width, height, displayInfo.numChannels.toLong()),
                    datasetName,
                    arrayOf(Axes.X, Axes.Y, Axes.CHANNEL))
                }
            "Short" ->
                if (displayInfo.numChannels == 1) GUIMain.datasetService.create(
                    UnsignedShortType(),
                    longArrayOf(width, height),
                    datasetName,
                    arrayOf(Axes.X, Axes.Y))
                else GUIMain.datasetService.create(
                    UnsignedShortType(),
                    longArrayOf(width, height, displayInfo.numChannels.toLong()),
                    datasetName,
                    arrayOf(Axes.X, Axes.Y, Axes.CHANNEL)
                )
            "Int" ->
                if (displayInfo.numChannels == 1) GUIMain.datasetService.create(
                    UnsignedIntType(),
                    longArrayOf(width, height),
                    datasetName,
                    arrayOf(Axes.X, Axes.Y)
                ) else GUIMain.datasetService.create(
                    UnsignedIntType(),
                    longArrayOf(width, height, displayInfo.numChannels.toLong()),
                    datasetName,
                    arrayOf(Axes.X, Axes.Y, Axes.CHANNEL)
                )
            "Long" ->
                if (displayInfo.numChannels == 1) GUIMain.datasetService.create(
                    UnsignedLongType(),
                    longArrayOf(width, height),
                    datasetName,
                    arrayOf(Axes.X, Axes.Y)
                ) else GUIMain.datasetService.create(
                    UnsignedLongType(),
                    longArrayOf(width, height, displayInfo.numChannels.toLong()),
                    datasetName,
                    arrayOf(Axes.X, Axes.Y, Axes.CHANNEL)
                )
            "Float" ->
                if (displayInfo.numChannels == 1) GUIMain.datasetService.create(
                    FloatType(),
                    longArrayOf(width, height),
                    datasetName,
                    arrayOf(Axes.X, Axes.Y)
                ) else GUIMain.datasetService.create(
                    FloatType(),
                    longArrayOf(width, height, displayInfo.numChannels.toLong()),
                    datasetName,
                    arrayOf(Axes.X, Axes.Y, Axes.CHANNEL)
                )
            "Double" ->
                if (displayInfo.numChannels == 1) GUIMain.datasetService.create(
                    DoubleType(),
                    longArrayOf(width, height),
                    datasetName,
                    arrayOf(Axes.X, Axes.Y)
                ) else GUIMain.datasetService.create(
                    DoubleType(),
                    longArrayOf(width, height, displayInfo.numChannels.toLong()),
                    datasetName,
                    arrayOf(Axes.X, Axes.Y, Axes.CHANNEL)
                )
            else -> null
        }

        display = GUIMain.displayService.createDisplayQuietly(dataset)
        view = (display as ImageDisplay).activeView as DatasetView

        //todo color table for grey images and find out about channels>1
        if (lutSz != "") {
            var colorTable = GUIMain.lutService.loadLUT(File(".\\luts\\" + lutSz))
            (view as DatasetView).setColorTable(colorTable, 0)
        }

// add the rois to the displayWindow
        var overlaysROIInfo = GUIMain.experimentService.loadtimeRoiList[sink!!.sinkName]  //TODO is this init yet
        if (overlaysROIInfo != null) {
            val overlays = overlaysROIInfo.map { it ->
                it.overlay
            }

            GUIMain.overlayService.addOverlays(display as ImageDisplay, overlays)
            GUIMain.experimentService.experimentStream.cameraDisplays[sink!!.sinkName] = display as ImageDisplay
        }

        //Create a display window to actually display the image
        displayWindow = GUIMain.uiService.defaultUI.createDisplayWindow(display)
//        GUIMain.zoomService.zoomOriginalScale(display as ImageDisplay)
//        val can = (display as ImageDisplay).getCanvas()
//        val zzz = can.zoomFactor
//        val www = can.viewportHeight
//        val hhh = can.viewportWidth
//        val off = can.panOffset

        GUIMain.uiService.viewerPlugins
            .map {
                GUIMain.pluginService.createInstance(it)
            }
            .find {
                it != null && it.canView(display) && it.isCompatible(GUIMain.uiService.defaultUI)
            }
            ?.let {
                GUIMain.threadService.queue {
                    (displayWindow as SwingDisplayWindow).apply {
                        GUIMain.uiService.addDisplayViewer(it)
                        it.view(this, display)

                        pack()
                        val rootPane = this.rootPane
                        windowPanel.add(rootPane)

                        //although this allows buttons and things to be added they would have
                        //to track the movement of the rois etc - which will be very fiddly
                        //
                        //
                        /////////////////////////////  ROI_CODE
                        //number code - buggy this way 221224
//                        layer = rootPane.getLayeredPane() as JLayeredPane
//                        for (f in 0..200) {
//                            val jlab = JLabel("unused", SwingConstants.LEFT)
//                            jlab.font = Font("Serif", Font.BOLD, 20)
//                            jlab.foreground = Color.WHITE
//
//                            jlab.setBounds(2000, 2000, 40, 20)
//                            layer!!.add(jlab)
//                        }
                    }
                }
            }
    }

    fun pressMinusButton() {
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