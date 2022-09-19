package uk.co.strimm.gui

import akka.actor.Actor
import akka.actor.ActorRef
import bibliothek.gui.dock.common.CControl
import bibliothek.gui.dock.common.CGrid
import io.scif.services.DatasetIOService
import javafx.application.Platform
import javafx.scene.paint.Color
import net.imagej.DatasetService
import net.imagej.ImageJService
import net.imagej.ImgPlusService
import net.imagej.app.QuitProgram
import net.imagej.display.ImageDisplayService
import net.imagej.display.OverlayService
import net.imagej.lut.LUTService
import net.imagej.ops.OpService
import net.imagej.overlay.RectangleOverlay
import org.scijava.`object`.ObjectService
import org.scijava.command.Command
import org.scijava.command.CommandService
import org.scijava.convert.ConvertService
import org.scijava.display.DisplayService
import org.scijava.event.EventService
import org.scijava.io.IOService
import org.scijava.menu.MenuService
import org.scijava.module.ModuleService
import org.scijava.plugin.Parameter
import org.scijava.plugin.Plugin
import org.scijava.plugin.PluginInfo
import org.scijava.plugin.PluginService
import org.scijava.thread.ThreadService
import org.scijava.ui.UIService
import org.scijava.ui.swing.SwingToolBar
import org.scijava.ui.swing.sdi.SwingSDIUI
import uk.co.strimm.ComponentTexts
import uk.co.strimm.MicroManager.MMCameraDevice
import uk.co.strimm.Paths
import uk.co.strimm.Program
import uk.co.strimm.actors.CameraDataStoreActor
import uk.co.strimm.actors.messages.ask.AskIsWriting
import uk.co.strimm.actors.messages.stop.AbortStream
import uk.co.strimm.experiment.ExperimentConfiguration
import uk.co.strimm.experiment.ROI
import uk.co.strimm.plugins.DockableWindowPlugin
import uk.co.strimm.plugins.PipelinePluginService
import uk.co.strimm.services.*
import uk.co.strimm.setIcon
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.WindowAdapter
import java.io.File
import java.io.FileWriter
import java.nio.file.Files
import java.nio.file.Path
import java.util.logging.Level
import javax.swing.*

/**
 * Main GUI for the STRIMM plugin
 */
@Plugin(type = Command::class, headless = true, menuPath = "Plugins>STRIMM")
class GUIMain : Command {
    override fun run() {
        mmService.getLibraries()
        initAndShowGUI()
    }

    /**
     * Method to initialise and load the various GUI components of the main STRÏMM window
     */
    private fun initAndShowGUI() {
        strimmUIService.dockableControl = CControl(strimmUIService.strimmFrame)
        strimmUIService.strimmFrame.add(strimmUIService.dockableControl.contentArea)
        strimmUIService.cGrid = CGrid(strimmUIService.dockableControl)
        strimmUIService.dockableControl.contentArea.deploy(strimmUIService.cGrid)
        strimmUIService.strimmFrame.layout = BorderLayout()

        val defaultUI = (uiService.defaultUI as SwingSDIUI)
        uiService.defaultUI.applicationFrame.setVisible(false)
        defaultUI.toolBar.isFloatable = false

        val buttonToolBar = defaultUI.toolBar.rootPane.contentPane.components[0] as SwingToolBar
        addAcquisitionButtons(buttonToolBar)

        strimmUIService.strimmFrame.add(defaultUI.toolBar.rootPane, BorderLayout.NORTH)
        strimmUIService.strimmFrame.setSize(Program.PROGRAM_DEFAULT_HEIGHT, Program.PROGRAM_DEFAULT_WIDTH)
        strimmUIService.strimmFrame.isVisible = true
        setOnClose(strimmUIService.strimmFrame)

        addMenuDockableWindowPlugins(
                dockableWindowPluginService.plugins.filterNot { it -> it.menuPath.size == 0 },
                (uiService.defaultUI as SwingSDIUI).applicationFrame.rootPane.jMenuBar)

        strimmUIService.imageJMenuBar = (uiService.defaultUI as SwingSDIUI).applicationFrame.rootPane.jMenuBar
        Platform.setImplicitExit(false)
    }

    //region Experiment buttons
    /**
     * Add custom buttons relating to experiment acquisition
     * @param imageJButtonBar The ImageJ button bar where the new buttons will be added
     */
    private fun addAcquisitionButtons(imageJButtonBar: SwingToolBar) {
        val firstButton = imageJButtonBar.components[0] as JToggleButton
        imageJButtonBar.addSeparator()

        autoScaleImageButton.maximumSize = Dimension(firstButton.width + 105, firstButton.height + 15)
        autoScaleImageButton.toolTipText = ComponentTexts.AcquisitionButtons.SCALE_IMAGE_TOOLTIP
        autoScaleImageButton.isSelected = false
        autoScaleImageButton.text = "Auto scale image"

        expStartStopButton.maximumSize = Dimension(firstButton.width + 15, firstButton.height + 15)
        expStartStopButton.icon = setIcon(firstButton.width, firstButton.height, Paths.Icons.START_ICON, "Start/Stop default", loggerService)
        expStartStopButton.selectedIcon = setIcon(firstButton.width, firstButton.height, Paths.Icons.STOP_ICON, "Start/Stop selected", loggerService)
        expStartStopButton.isEnabled = false

        loadExperimentConfigButton.maximumSize = Dimension(firstButton.width + 15, firstButton.height + 15)
        loadExperimentConfigButton.toolTipText = ComponentTexts.AcquisitionButtons.LOAD_BUTTON_TOOLTIP
        loadExperimentConfigButton.icon = setIcon(firstButton.width, firstButton.height, Paths.Icons.LOAD_ICON, "Load Experiment", loggerService)

        roiSpecCompleteButton.maximumSize = Dimension(firstButton.width+15,firstButton.height+15)
        roiSpecCompleteButton.toolTipText = ComponentTexts.AcquisitionButtons.ROICOMPLETE_BUTTON_TOOLTIP
        roiSpecCompleteButton.isEnabled = false
        roiSpecCompleteButton.icon = setIcon(firstButton.width, firstButton.height, Paths.Icons.ROI_SPEC_COMPLETE_ICON, "ROI Complete", loggerService)

        startExperimentButton.maximumSize = Dimension(firstButton.width+15,firstButton.height+15)
        startExperimentButton.toolTipText = ComponentTexts.AcquisitionButtons.START_BUTTON_TOOLTIP
        startExperimentButton.isEnabled = experimentService.loadedConfigurationStream != null
        startExperimentButton.icon = setIcon(firstButton.width, firstButton.height,Paths.Icons.START_ICON, "Start", loggerService)

        stopExperimentButton.maximumSize = Dimension(firstButton.width+15,firstButton.height+15)
        stopExperimentButton.toolTipText = ComponentTexts.AcquisitionButtons.STOP_BUTTON_TOOLTIP
        stopExperimentButton.isEnabled = false
        stopExperimentButton.icon = setIcon(firstButton.width, firstButton.height, Paths.Icons.STOP_ICON, "Stop", loggerService)

        resizeToROIButton.maximumSize = Dimension(firstButton.width + 15, firstButton.height + 15)
        resizeToROIButton.toolTipText = ComponentTexts.AcquisitionButtons.RESIZE_ROI_TOOLTIP
        resizeToROIButton.isEnabled = false
        resizeToROIButton.icon = setIcon(firstButton.width, firstButton.height, Paths.Icons.RESIZE_ROI_ICON, "Resize ROI", loggerService)

        fullViewButton.maximumSize = Dimension(firstButton.width + 15, firstButton.height + 15)
        fullViewButton.toolTipText = ComponentTexts.AcquisitionButtons.FULL_VIEW_TOOLTIP
        fullViewButton.isEnabled = false
        fullViewButton.icon = setIcon(firstButton.width, firstButton.height, Paths.Icons.FULL_VIEW_ICON, "Full View", loggerService)

        loadPrevExperimentButton.maximumSize = Dimension(firstButton.width + 15, firstButton.height + 15)
        loadPrevExperimentButton.toolTipText = ComponentTexts.AcquisitionButtons.LOAD_PREV_EXPERIMENT_TOOLTIP
        loadPrevExperimentButton.icon = setIcon(firstButton.width, firstButton.height, Paths.Icons.LOAD_PREV_ICON, "Load Previous Experiment", loggerService)

        mainWindowIcon = setIcon(firstButton.width, firstButton.height, Paths.Icons.STRIMM_LOGO_ICON, "Strimm Logo", loggerService, false)
        if (mainWindowIcon != null) strimmUIService.strimmFrame.iconImage = mainWindowIcon!!.image

        //
        imageJButtonBar.add(saveJSON)
        saveJSON.maximumSize = Dimension(firstButton.width + 15, firstButton.height + 15)
        saveJSON.toolTipText = ComponentTexts.AcquisitionButtons.FULL_VIEW_TOOLTIP
        saveJSON.isEnabled = true
        saveJSON.icon = setIcon(firstButton.width, firstButton.height, Paths.Icons.SAVE_JSON_ICON, "Save JSON", loggerService)

        addSaveJSONButtonListener()
        //

        imageJButtonBar.add(loadExperimentConfigButton)
        //imageJButtonBar.add(loadPrevExperimentButton)
        //imageJButtonBar.addSeparator()

        //imageJButtonBar.add(resizeToROIButton)
        //imageJButtonBar.add(fullViewButton)
        //imageJButtonBar.addSeparator()

         imageJButtonBar.add(expStartStopButton)

        //imageJButtonBar.addSeparator()

        //imageJButtonBar.add(autoScaleImageButton)

        addLoadButtonListener()
//        addROISpecCompleteListener()
//        addStartButtonListener()
//        addStopButtonListener()
        addResizeToROIButtonListener()
        addFullViewButtonListener()
        addLoadPreviousExperimentButtonListener()
        addAutoScaleButtonListener()
        addExpStartStopListener()

    }

    private fun addSaveJSONButtonListener() {
        saveJSON.addActionListener {
            println("**********************press button********************************")

            val reloadChoice = JOptionPane.showConfirmDialog(strimmUIService.strimmFrame,
                    "Save JSON", "Save JSON to Temp.json",
                    JOptionPane.OK_CANCEL_OPTION)
            val pathAndName = "./ExperimentConfigurations/Temp.json"
            val newFile = File(pathAndName)
            val writer = FileWriter(newFile)
            var exp = ExperimentConfiguration()
            var exp_old = experimentService.experimentStream.expConfig
            //
            exp.experimentConfigurationName = exp_old.experimentConfigurationName
            exp.experimentMode = exp_old.experimentMode
            exp.experimentDurationMs = exp_old.experimentDurationMs
            exp.HDF5Save = exp_old.HDF5Save
            exp.ROIAdHoc = exp_old.ROIAdHoc
            for (src in exp_old.sourceConfig.sources) {
                var sss = uk.co.strimm.experiment.Source()
                sss.sourceName = src.sourceName
                sss.deviceLabel = src.deviceLabel
                sss.sourceCfg = src.sourceCfg
                sss.outputType = src.outputType
                sss.isImageSnapped = src.isImageSnapped
                sss.isTriggered = src.isTriggered
                sss.isTimeLapse = src.isTimeLapse
                sss.intervalMs = src.intervalMs
                sss.exposureMs = src.exposureMs
                sss.framesInCircularBuffer = src.framesInCircularBuffer
                sss.outputType = src.outputType
                sss.isKeyboardSnapEnabled = src.isKeyboardSnapEnabled
                sss.SnapVirtualCode = src.SnapVirtualCode

                exp.sourceConfig.sources.add(sss)
            }
            for (flw in exp_old.flowConfig.flows) {
                var fff = uk.co.strimm.experiment.Flow()
                fff.flowName = flw.flowName
                fff.inputType = flw.inputType
                fff.outputType = flw.outputType
                for (sz in flw.inputNames) {
                    fff.inputNames.add(sz)
                }
                exp.flowConfig.flows.add(fff)
            }
            for (snk in exp_old.sinkConfig.sinks) {
                var sss = uk.co.strimm.experiment.Sink()
                sss.sinkName = snk.sinkName
                sss.sinkType = snk.sinkType
                sss.outputType = snk.outputType
                sss.displayOrStore = snk.displayOrStore
                sss.imageWidth = snk.imageWidth
                sss.imageHeight = snk.imageHeight
                sss.bitDepth = snk.bitDepth
                sss.previewInterval = snk.previewInterval
                sss.roiFlowName = snk.roiFlowName
                sss.autoscale = snk.autoscale
                for (sz in snk.inputNames) {
                    sss.inputNames.add(sz)
                }
                exp.sinkConfig.sinks.add(sss)

            }

            //roi
            // traceColourByFlowAndOverlay<String, Pair<Overlay, Int>>
            //
            for (flw in exp_old.flowConfig.flows) {
                var roi_flow = strimmUIService.traceColourByFlowAndOverlay[flw.flowName]
                if (roi_flow != null) {

                    for (roi in roi_flow) {
                        var overlay = (roi.first) as RectangleOverlay
                        var rrr = ROI()
                        rrr.x = overlay.getOrigin(0)
                        rrr.y = overlay.getOrigin(1)
                        rrr.w = overlay.getExtent(0)
                        rrr.h = overlay.getExtent(1)
                        rrr.ROItype = "Rectangle"
                        rrr.ROIName = ""
                        rrr.flowName = flw.flowName
                        exp.roiConfig.rois.add(rrr)
                    }
                }
            }
            experimentService.gson.toJson(exp, writer)
            writer.flush()
            writer.close()
        }
    }

    /**
     * Specify logic for loading a new experiment configuration
     */
    private fun addLoadButtonListener() {
        loadExperimentConfigButton.addActionListener {
            //            GUIMain.protocolService.WinInitSpeechEngine()
//            GUIMain.protocolService.WinSpeak("Load new experiment")
//            GUIMain.protocolService.WinShutdownSpeechEngine()

            val folder = File(Paths.EXPERIMENT_CONFIG_FOLDER)
            val fileList = folder.listFiles { f -> f.extension == "json" }
            if (fileList != null && fileList.isNotEmpty()) {
                val fileComboBox = JComboBox(fileList.map { f -> f.name.replace(".json", "") }.toTypedArray())
                fileComboBox.selectedIndex = 0

                // confirm dialogue allows user to back out of selecting a config file
                val configChoice = JOptionPane.showConfirmDialog(strimmUIService.strimmFrame,
                        fileComboBox, "Select experiment configuration", JOptionPane.OK_CANCEL_OPTION)

                // only continue when user presses "OK"
                if (configChoice == 0) {
                    val selectedConfigFile = fileComboBox.selectedItem as String
                    val selectedFile = fileList.find { f -> f.name.replace(".json", "") == selectedConfigFile }!!
                    val loadSuccess = experimentService.convertGsonToConfig(selectedFile)
                    if (loadSuccess) {

                        // check state of program
                        if (strimmUIService.state == UIstate.PREVIEW) {
                            // User is attempting to load a new config while in PREVIEW mode.
                            // Confirm they want to continue
                            val reloadChoice = JOptionPane.showConfirmDialog(strimmUIService.strimmFrame,
                                    "Do you want to restart preview with new config?", "Load new configuration",
                                    JOptionPane.OK_CANCEL_OPTION)
                            if (reloadChoice == 0) {
                                // Cancel current running sequence acquisition
                                experimentService.exitPreview()
                            } else {
                                // back out
                                return@addActionListener
                            }
                        }

                        experimentService.createExperimentStream(true)
                        createStreamGraph()

                        if (experimentService.expConfig.ROIAdHoc.toLowerCase() == "true") {
                            showROISpecPrompt()
                        } else {
//                            roiSpecCompleteButton.isEnabled = false
                            resizeToROIButton.isEnabled = true
                            fullViewButton.isEnabled = true
                            expStartStopButton.isEnabled = true
                        }
                    } else {
                        JOptionPane.showMessageDialog(strimmUIService.strimmFrame, ComponentTexts.AcquisitionDialogs.ERROR_LOADING_EXPCONFIG)
                    }
                }
            } else {
                JOptionPane.showMessageDialog(strimmUIService.strimmFrame, ComponentTexts.AcquisitionDialogs.ERROR_FINDING_CONFIGURATIONS)
            }
        }

    }

    /**
     * Specify logic for the user finishing the specification of any traces from ROIs
     */
    private fun addExpStartStopListener() {
        expStartStopButton.addActionListener {
            //            GUIMain.protocolService.WinInitSpeechEngine()
//            GUIMain.protocolService.WinSpeak("add ROI specification")
//            GUIMain.protocolService.WinShutdownSpeechEngine()

            val isWriting = actorService.askFileWriterActorIfWriting()
            if (isWriting!!) {
                JOptionPane.showMessageDialog(strimmUIService.strimmFrame, "File from previous experiment is still being written")
            } else if (experimentService.hasRun) {
                //TODO add prompt check here
                abortExperiment()
            } else {
                var result = JOptionPane.YES_OPTION
                var noROIsSpecified = false
                if (experimentService.numNewTraceROIFeeds == 0) {
                    result = JOptionPane.showConfirmDialog(strimmUIService.strimmFrame, "No new traces from ROIs specified. Do you want to proceed?", "Trace ROIs",
                            JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE)
                    noROIsSpecified = true
                }

                if (result == JOptionPane.YES_OPTION) {
                    JOptionPane.showMessageDialog(strimmUIService.strimmFrame, "Trace from ROI specification complete. Loading new experiment...")
                    if (noROIsSpecified) {
                        //This line would normally be called in TraceFromROIContext
                        experimentService.createNewConfigFromExisting()
                    }
                    experimentService.stopAndLoadNewExperiment(true, true)
                    expStartStopButton.isSelected = true
                    expStartStopButton.isEnabled = true
                    experimentService.hasRun = true
                }
            }
        }
    }

    private fun abortExperiment() {
        experimentService.experimentStream.cameraDataStoreActors.forEach { x -> x.key.tell(AbortStream(), ActorRef.noSender()) }
        experimentService.experimentStream.traceDataStoreActors.forEach { x -> x.key.tell(AbortStream(), ActorRef.noSender()) }
    }

    /**
     * Specify logic for when the user resizes the camera feed to an ROI
     */
    private fun addResizeToROIButtonListener() {
        resizeToROIButton.addActionListener {
            val activeDisplays = imageDisplayService.activeImageDisplay
            strimmUIService.resizeCameraFeedToROI(activeDisplays)
        }
    }


    /**
     * Specify logic for when the user expands the camera feed to full view
     */
    private fun addFullViewButtonListener() {
        fullViewButton.addActionListener {
            strimmUIService.expandCameraFeedToFullView(imageDisplayService.activeImageDisplay)
        }
    }

    private fun addAutoScaleButtonListener() {
        autoScaleImageButton.addActionListener {
            strimmUIService.autoScaleCheck = autoScaleImageButton.isSelected
            loggerService.log(Level.INFO, "1 Autoscale is ${autoScaleImageButton.isSelected}")
        }
    }

    /**
     * Specify logic for when the user loads a previously acquired experiment
     */
    private fun addLoadPreviousExperimentButtonListener() {
        loadPrevExperimentButton.addActionListener {
            val chooser = JFileChooser()
            chooser.currentDirectory = File(".")
            chooser.dialogTitle = "Select folder"
            chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            val result = chooser.showDialog(strimmUIService.strimmFrame, "Select")
            if (result == JFileChooser.APPROVE_OPTION) {
                experimentService.loadPreviousExperiment(chooser.selectedFile)
            }
        }
    }

    /**
     * Show a specify ROI prompt to the user
     */
    private fun showROISpecPrompt() {
        JOptionPane.showMessageDialog(strimmUIService.strimmFrame, ComponentTexts.AcquisitionDialogs.SPECIFY_ROI_PROMPT)
//        roiSpecCompleteButton.isEnabled = true
        resizeToROIButton.isEnabled = true
        fullViewButton.isEnabled = true
        expStartStopButton.isEnabled = true
    }

    /**
     * This method will run an experiment stream based on the loaded specification. Note this method only loads into
     * preview mode
     */
    private fun createStreamGraph() {

        // GUIMain.actorService.actorSystem.terminate() caused everything to crash
        //the problem is that the ximea is still being called which means that
        //its akka source must still exist

        //what I really want is a hard reset of everything to do with akka


//        GUIMain.protocolService.WinInitSpeechEngine()
//        GUIMain.protocolService.WinSpeak("Create stream graph")
//        GUIMain.protocolService.WinShutdownSpeechEngine()
        val streamCreateSuccess = experimentService.createStreamGraph()
        println("************created stream graph GUIMain************")
        println("*****************still here about to make a statement about whether successful,but still not run stream*******")


        if (!streamCreateSuccess) {
            println("before dlg to say creation failed")
            JOptionPane.showMessageDialog(strimmUIService.strimmFrame, ComponentTexts.AcquisitionDialogs.ERROR_CREATING_GRAPH)
            println("dlg to say graph creation failed")
        } else {
            println("before dlg to say creation succeeded")
            JOptionPane.showMessageDialog(strimmUIService.strimmFrame, ComponentTexts.AcquisitionDialogs.STREAM_CREATE_SUCCESS)
            println("**************after option message****************")
            println("*************about to runStream****************")

            experimentService.runStream(false) //This is where the fun begins

            println("*****************after runStream********************")

        }
    }
    //endregion

    fun getMenuItemName(item: JComponent) =
            when (item) {
                is JMenu -> item.text
                is JMenuItem -> item.text
                else -> "Error"
            }

    tailrec fun addMenuItems(plugin: PluginInfo<DockableWindowPlugin>, depth: Int, currentMenu: JComponent) {
        val menuPath = plugin.menuPath

        if (menuPath.size == depth + 1) {
            val menuItem = JMenuItem(menuPath.leaf.name).apply {
                addActionListener {
                    var selectedCamera: MMCameraDevice? = null

                    //If its a camera window plugin we need to specify a camera
                    if (plugin.className == CameraWindowPlugin::class.java.name) {
                        val cameraDevices = mmService.getLoadedDevicesOfType(MMCameraDevice::class.java)
                        val cameraNames = cameraDevices.map { x -> x.label }.toTypedArray()
                        val optionList = JComboBox(cameraNames)
                        optionList.selectedIndex = 0
                        JOptionPane.showMessageDialog(null, optionList, "Select camera", JOptionPane.QUESTION_MESSAGE)
                        selectedCamera = cameraDevices.find { x -> x.label == optionList.selectedItem }!!
                    }

                    val dwPlugin = if (selectedCamera != null) {
                        dockableWindowPluginService.createPlugin(CameraWindowPlugin::class.java, selectedCamera, true,
                                "${selectedCamera.label}${ComponentTexts.CameraWindow.PLUGIN_TITLE_SUFFIX}")
                    } else {
                        dockableWindowPluginService.createPlugin(plugin, null, false)
                    }

                    dwPlugin?.dock(strimmUIService.dockableControl, strimmUIService.strimmFrame)
                            ?: loggerService.log(Level.WARNING, "Failed to dock plugin ${plugin.pluginClass}!")
                }
            }

            currentMenu.add(menuItem)
            return
        }

        var newMenu = currentMenu.components.find { getMenuItemName(it as JComponent) == menuPath[depth].name }
        when (newMenu) {
            is JMenu -> {
            }
            null, is JMenuItem -> {
                newMenu = JMenu(menuPath[depth].name)
                currentMenu.add(newMenu)
            }
            else -> {
            }
        }
        addMenuItems(plugin, depth + 1, newMenu as JComponent)
    }

    fun addMenuDockableWindowPlugins(plugins: List<PluginInfo<DockableWindowPlugin>>, menu: JMenuBar) {
        plugins.forEach {
            addMenuItems(it, 0, menu)
        }
    }

    fun setOnClose(frame: JFrame) {
        val exitListener = object : WindowAdapter() {
            override fun windowClosing(e: java.awt.event.WindowEvent?) {
                //timerService.closeTimerService()       //TW    2/7/21

//                GUIMain.experimentService.exitPreview()

//                GUIMain.protocolService.WinInitSpeechEngine()
//                GUIMain.protocolService.WinSpeak("Closing stream")
//                GUIMain.protocolService.WinShutdownSpeechEngine()

                //terminate a graph
                println("waiting for sources to finish")

                var bBusy = true
                while (bBusy) {
                    bBusy = false
                    for (src in GUIMain.experimentService.expConfig.sourceConfig.sources) {
                        if (src.sourceType == "NIDAQSource") {
                            println("TERMINATE NIDAQ Thread")
                            GUIMain.protocolService.TerminateNIDAQProtocol()
                            src.isBusy = false
                            continue
                        }
                    }
                }
                loggerService.log(Level.INFO, "Closing STRIMM")
                println("shutdown file map")
                protocolService.ShutdownCameraMap()
                loggerService.fh.close()

                commandService.run(QuitProgram::class.java, false)

                super.windowClosing(e)
                Platform.exit()
            }
        }
        frame.addWindowListener(exitListener)
    }

    companion object {
        val roiSpecCompleteButton = JButton()
        val loadExperimentConfigButton = JButton()
        val startExperimentButton = JButton()
        val stopExperimentButton = JButton()
        val resizeToROIButton = JButton()
        val fullViewButton = JButton()
        val loadPrevExperimentButton = JButton()
        val autoScaleImageButton = JCheckBox()
        val expStartStopButton = JToggleButton()

        val saveJSON = JButton()

        var mainWindowIcon: ImageIcon? = null

        //region Services
        @Parameter
        lateinit var lutService: LUTService

        @Parameter
        lateinit var actorService: ActorService

        @Parameter
        lateinit var imageJService: ImageJService

        @Parameter
        lateinit var uiService: UIService

        @Parameter
        lateinit var pipelinePluginService: PipelinePluginService

        @Parameter
        lateinit var dockableWindowPluginService: DockableWindowPluginService

        @Parameter
        lateinit var displayService: DisplayService

        @Parameter
        lateinit var imageDisplayService: ImageDisplayService

        @Parameter
        lateinit var ioService: IOService

        @Parameter
        lateinit var datasetService: DatasetService

        @Parameter
        lateinit var loggerService: LoggerService

        @Parameter
        lateinit var mmService: MMCoreService

        @Parameter
        lateinit var commandService: CommandService

        @Parameter
        lateinit var strimmUIService: StrimmUIService

        @Parameter
        lateinit var pluginService: PluginService

        @Parameter
        lateinit var threadService: ThreadService

        @Parameter
        lateinit var menuService: MenuService

        @Parameter
        lateinit var moduleService: ModuleService

        @Parameter
        lateinit var eventService: EventService

        @Parameter
        lateinit var objectService: ObjectService

        @Parameter
        lateinit var overlayService: OverlayService

        @Parameter
        lateinit var opService: OpService

        @Parameter
        lateinit var imgPlusService: ImgPlusService

        @Parameter
        lateinit var strimmSettingsService: StrimmSettingsService

        @Parameter
        lateinit var exportService: ExportService

        @Parameter
        lateinit var experimentCommandService: ExperimentCommandPluginService

        //@Parameter     // TW 2/7/21
        //lateinit var timerService: TimerService

        @Parameter
        lateinit var datasetIOService: DatasetIOService

        @Parameter
        lateinit var experimentService: ExperimentService

        @Parameter
        lateinit var softwareTimerService: SoftwareTimerService

        @Parameter
        lateinit var convertService: ConvertService

        @Parameter
        lateinit var importService: ImportService

        @Parameter
        lateinit var utilsService: UtilsService

        @Parameter
        lateinit var acquisitionMethodService: AcquisitionMethodService

        @Parameter
        lateinit var protocolService: ProtocolService

        @Parameter
        lateinit var debugService: DebugService
        //endregion
        val roiColours = arrayListOf<Color>(
                Color.color(1.0, 0.0, 0.0), //blue
                Color.color(0.0, 1.0, 0.0), //orange
                Color.color(0.00000, 0.0, 1.0), //yellow
                Color.color(1.0, 0.0, 1.0), //purple
                Color.color(0.0, 1.0, 1.0), //green
                Color.color(0.3010, 0.7450, 0.9330), //light blue
                Color.color(0.6350, 0.0780, 0.1840), //maroon
                Color.color(0.0, 0.4470, 0.7410), //blue
                Color.color(0.8500, 0.3250, 0.0980), //orange
                Color.color(0.9290, 0.6940, 0.1250), //yellow
                Color.color(0.4940, 0.1840, 0.5560), //purple
                Color.color(0.4660, 0.6740, 0.1880), //green
                Color.color(0.3010, 0.7450, 0.9330), //light blue
                Color.color(0.6350, 0.0780, 0.1840),
                Color.color(1.0, 1.0, 1.0)
        ) //maroon
    }
}

/**
 * Controller for the Main GUI for the STRIMM plugin
 */
class MainController {

}

