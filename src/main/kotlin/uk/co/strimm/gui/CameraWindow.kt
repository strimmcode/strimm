package uk.co.strimm.gui

import akka.actor.ActorRef
import bibliothek.gui.dock.common.DefaultMultipleCDockable
import javafx.scene.input.KeyCode
import net.imagej.Dataset
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
import uk.co.strimm.services.UIstate
import java.awt.Dimension
import java.awt.Robot
import java.awt.event.KeyEvent
import java.awt.event.KeyListener
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import java.util.*
import java.util.logging.Level
import javax.swing.JLabel
import javax.swing.JLayeredPane
import javax.swing.JPanel
import javax.swing.JTextField

@Plugin(type = DockableWindowPlugin::class, menuPath = "Window>Camera Feed")
class CameraWindowPlugin : AbstractDockableWindow() {
    override var title = ""
    lateinit var cameraWindowController: CameraWindow

    override fun setCustomData(data: Any?) {
        if (data is DisplayInfo) {
            cameraWindowController.displayInfo = data
            dockableWindowMultiple.titleText = data.displayName
            cameraWindowController.cameraFeedName = data.displayName
        } else if (data is Dataset) {
            cameraWindowController.dataset = data
        }
    }

    override var dockableWindowMultiple: DefaultMultipleCDockable = run {
        /**
         * Other GUI windows load an FXML file for the actual GUI elements. We don't load the camera window like that
         * because it uses Swing components instead of JavaFX components
         */
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
    var datasetName = "Dataset" + Random().nextInt(1000000) //TODO does this need to come from the image feed?
    var numChannels = 0
    var sink: Sink? = null
    var layer: JLayeredPane? = null
    var cameraFeedName : String? = null

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

        val exposureSettingPanel = JPanel()
        exposureSettingPanel.size = Dimension(windowPanel.width, 25)
        exposureSettingPanel.preferredSize = Dimension(windowPanel.width, 25)
        val exposureLabel = JLabel("Exposure (ms): ")
        val exposureTextBox = JTextField("")
        exposureTextBox.isEnabled = GUIMain.strimmUIService.state == UIstate.PREVIEW
        exposureSettingPanel.add(exposureLabel)
        exposureSettingPanel.add(exposureTextBox)
        windowPanel.add(exposureSettingPanel)
        setExposureText(exposureTextBox)
        addExposureTextKeyListener(exposureTextBox)

        windowPanel.updateUI()
        if (dataset == null) {
            GUIMain.loggerService.log(Level.INFO, "Initialised camera window for feed ${displayInfo!!.displayName} " +
                    "with: w=${displayInfo!!.width}, " +
                    "h=${displayInfo!!.height}, " +
                    "pixel type=${displayInfo!!.pixelType}, " +
                    "no. channels=${displayInfo!!.numChannels}")
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
                    //TODO is this needed?
                }
            }
        })
    }

    private fun addExposureTextKeyListener(exposureTextBox : JTextField){
        val keyListener: KeyListener = object : KeyListener {
            override fun keyPressed(keyEvent: KeyEvent) {
                val code = keyEvent.keyCode
                if(code == KeyCode.ENTER.code){
                    try {
                        val exposure = exposureTextBox.text.toDouble()
                        setExposure(exposure)
                    }
                    catch(ex : Exception){
                        GUIMain.loggerService.log(Level.SEVERE, "Exposure could not be parsed. Message ${ex.message}")
                        GUIMain.loggerService.log(Level.SEVERE, ex.stackTrace)
                        //We only need to set the exposure text here as the typed exposure text is invalid
                        setExposureText(exposureTextBox)
                    }
                }
            }

            override fun keyReleased(keyEvent: KeyEvent) {
            }

            override fun keyTyped(keyEvent: KeyEvent) {
            }
        }

        exposureTextBox.addKeyListener(keyListener)
    }

    private fun setExposureText(textField : JTextField){
        try {
            val associatedMMCore = GUIMain.experimentService.allMMCores.filter { x -> x.key == sink!!.primaryCamera }
            if (associatedMMCore.isNotEmpty()) {
                //Although a foreach is used here, there should only be one entry in associatedMMCore
                associatedMMCore.forEach { x ->
                    if (x.key == sink!!.primaryCamera) {
                        val cameraLabel = x.value.first
                        val mmCore = x.value.second
                        val exposure = mmCore.getExposure(cameraLabel).toString()
                        textField.text = exposure
                    }
                }
            }
        }
        catch(ex : Exception){
            GUIMain.loggerService.log(Level.SEVERE, "Error setting exposure text for primary camera ${sink!!.primaryCamera}. Message: ${ex.message}")
            GUIMain.loggerService.log(Level.SEVERE, ex.stackTrace)
        }
    }

    private fun setExposure(exposure : Double){
        try {
            val associatedMMCore = GUIMain.experimentService.allMMCores.filter { x -> x.key == sink!!.primaryCamera }
            if (associatedMMCore.isNotEmpty()) {
                associatedMMCore.forEach { x ->
                    if (x.key == sink!!.primaryCamera) {
                        val cameraLabel = x.value.first
                        val mmCore = x.value.second
                        GUIMain.loggerService.log(Level.INFO, "Setting exposure to $exposure")
                        mmCore.setExposure(cameraLabel, exposure)
                    }
                }
            } else {
                GUIMain.loggerService.log(
                    Level.WARNING,
                    "No associated MMCore found for primary camera ${sink!!.primaryCamera}"
                )
            }
        }
        catch(ex : Exception){
            GUIMain.loggerService.log(Level.SEVERE, "Error setting exposure for primary camera ${sink!!.primaryCamera}. Message ${ex.message}")
            GUIMain.loggerService.log(Level.SEVERE, ex.stackTrace)
        }
    }

//    private fun getExposure() : Double{
//        var exposure = 0.0
//
//        try {
//            val associatedMMCore = GUIMain.experimentService.allMMCores.filter { x -> x.key == sink!!.primaryCamera }
//            if (associatedMMCore.isNotEmpty()) {
//                //Although a foreach is used here, there should only be one entry in associatedMMCore
//                associatedMMCore.forEach { x ->
//                    if (x.key == sink!!.primaryCamera) {
//                        val cameraLabel = x.value.first
//                        val mmCore = x.value.second
//                        exposure = mmCore.getExposure(cameraLabel)
//                    }
//                }
//            }
//        }
//        catch(ex : Exception){
//            GUIMain.loggerService.log(Level.SEVERE, "Error getting exposure from mmcore for primary camera ${sink!!.primaryCamera}. Message: ${ex.message}")
//            GUIMain.loggerService.log(Level.SEVERE, ex.stackTrace)
//        }
//
//        return exposure
//    }

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

    private fun createDatasetAndAddToPanel(width: Long, height: Long, label: String, displayInfo: DisplayInfo) {
        datasetName = label
        val lutSz = displayInfo.lut
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
            val colorTable = GUIMain.lutService.loadLUT(File(".\\luts\\" + lutSz))
            (view as DatasetView).setColorTable(colorTable, 0)
        }

        // add the rois to the displayWindow
        val overlaysROIInfo = GUIMain.experimentService.loadtimeRoiList[sink!!.sinkName]  //TODO is this init yet
        if (overlaysROIInfo != null) {
            val overlays = overlaysROIInfo.map { it ->
                it.overlay
            }

            GUIMain.overlayService.addOverlays(display as ImageDisplay, overlays)
            GUIMain.experimentService.experimentStream.cameraDisplays[sink!!.sinkName] = display as ImageDisplay
        }

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