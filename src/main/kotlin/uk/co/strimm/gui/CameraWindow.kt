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
import java.awt.*
import java.awt.event.KeyEvent
import java.awt.event.KeyListener
import java.io.File
import java.util.*
import java.util.logging.Level
import javax.swing.JLabel
import javax.swing.JLayeredPane
import javax.swing.JPanel
import javax.swing.JTextField
import kotlin.math.exp

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
     * TODO can this method be removed (where and when was it used?)
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
        windowPanel.layout = BorderLayout()

        //Add components for the setting the exposure
        val exposureLabel = JLabel("Exposure (ms): ")
        exposureLabel.preferredSize = Dimension(100, 25)
        exposureLabel.maximumSize = Dimension(100, 25)
        val exposureTextBox = JTextField("")
        exposureTextBox.isEnabled = GUIMain.strimmUIService.state == UIstate.PREVIEW
        exposureTextBox.preferredSize = Dimension(40, 25)
        exposureTextBox.maximumSize = Dimension(40, 25)
        val exposureSettingPanel = JPanel()
        exposureSettingPanel.layout = FlowLayout()
        exposureSettingPanel.add(exposureLabel, BorderLayout.NORTH)
        exposureSettingPanel.add(exposureTextBox, BorderLayout.NORTH)
        windowPanel.add(exposureSettingPanel, BorderLayout.NORTH)
        setExposureText(exposureTextBox)
        addExposureTextKeyListener(exposureTextBox)

        //Add the camera image display
        if (dataset == null) {
            GUIMain.loggerService.log(Level.INFO, "Initialised camera window for feed ${displayInfo!!.displayName} " +
                    "with: w=${displayInfo!!.width}, " +
                    "h=${displayInfo!!.height}, " +
                    "pixel type=${displayInfo!!.pixelType}, " +
                    "no. channels=${displayInfo!!.numChannels}")
            createDatasetAndAddToPanel(displayInfo!!)
        }
        else {
            createDisplayAndAddToPanel()
        }
    }

    /**
     * Adds a key listener to the exposure text box. The exposure setting will only be changed if the enter key is
     * pressed
     * @param exposureTextBox The text field where the exposure can be changed
     */
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

    /**
     * Method to set the text of the exposure text field based on the current exposure. The current exposure will come
     * from the associated MMCore object
     * @param textField The exposure text field for this camera window
     */
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

    /**
     * Sets the exposure for the associated camera. Note - currently if image splitting is used, changing the exposure
     * in one window will change the exposure for all windows reading from the same camera
     * @param exposure The new exposure, which will have been parsed from the exposure text field
     */
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

                        /**
                         * This is important as it keeps a record of any exposures that have been changed. This is then
                         * used to write the changes to file
                         */
                        GUIMain.experimentService.changedExposures[sink!!.primaryCamera] = exposure
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
                        windowPanel.add(rootPane, BorderLayout.CENTER)
                    }
                }
            }
    }

    private fun createDatasetAndAddToPanel(displayInfo: DisplayInfo) {
        val width = displayInfo.width
        val height = displayInfo.height
        val label = displayInfo.displayName
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
            val overlays = overlaysROIInfo.map { it.overlay }
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
                        windowPanel.add(rootPane, BorderLayout.CENTER)
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