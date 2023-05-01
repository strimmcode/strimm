package uk.co.strimm.gui


import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.ActorMaterializerSettings
import bibliothek.gui.dock.common.CControl
import bibliothek.gui.dock.common.CGrid
import io.scif.services.DatasetIOService
import javafx.application.Platform
import javafx.scene.paint.Color
import net.imagej.DatasetService
import net.imagej.ImageJService
import net.imagej.app.QuitProgram
import net.imagej.display.ImageDisplayService
import net.imagej.display.OverlayService
import net.imagej.display.ZoomService
import net.imagej.lut.LUTService
import net.imagej.ops.OpService
import net.imagej.overlay.EllipseOverlay
import net.imagej.overlay.RectangleOverlay
import org.scijava.command.Command
import org.scijava.command.CommandService
import org.scijava.display.DisplayService
import org.scijava.plugin.Parameter
import org.scijava.plugin.Plugin
import org.scijava.plugin.PluginService
import org.scijava.thread.ThreadService
import org.scijava.ui.UIService
import org.scijava.ui.swing.SwingToolBar
import org.scijava.ui.swing.sdi.SwingSDIUI
import uk.co.strimm.ComponentTexts
import uk.co.strimm.actors.messages.ask.AskInitHDF5File
import uk.co.strimm.experiment.ROI
import uk.co.strimm.services.*
import uk.co.strimm.setIcon
import java.awt.BorderLayout
//import java.awt.Color
//import java.awt.Color
import java.awt.Dimension
import java.awt.event.WindowAdapter
import java.io.*
import java.net.ServerSocket
import java.net.Socket
import java.util.logging.Level
import javax.swing.*

//TODO  REMOTE CONTROL

//1) put in preset name for load JSON
//2) put option to turn off dialog boxes so that it will work remotely without waiting for input
//3  run with a Python client

/**
 * Main GUI for the STRIMM plugin
 */
@Plugin(type = Command::class, headless = true, menuPath = "Plugins>STRIMM")
class GUIMain : Command {
    override fun run() {
        initAndShowGUI()
    }

    private fun initAndShowGUI() {
        //TODO good place to load all dlls here
        //could load all dlls into address space here
        //initialiseLibrary(relativeLibraryPath("UHDF5"))
        System.loadLibrary("Test")
        // start the server thread
//        initRemoteControl()
//        controlThread = CommandServerThread("remote control", commandServerSocket!!)
//        if (controlThread != null){
//            controlThread!!.start()
//        }

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
        strimmUIService.strimmFrame.setSize(1280, 900)
        strimmUIService.strimmFrame.isVisible = true

        setOnClose(strimmUIService.strimmFrame)
        Platform.setImplicitExit(false)
    }

    //configure buttons, adds event handlers to the buttons, adds them to the toolbar and sets the STRIMM mode to IDLE
    private fun addAcquisitionButtons(imageJButtonBar: SwingToolBar) {
        val firstButton = imageJButtonBar.components[0] as JToggleButton
        imageJButtonBar.addSeparator()

        //saveJSON is legacy and will be replaced with saveROI which will save out user
        //selected ROIs into the format of imageJ ROI Manager so that they can be referenced in the JSON
        saveJSON.maximumSize = Dimension(firstButton.width + 15, firstButton.height + 15)
        // saveJSON.toolTipText = ComponentTexts.AcquisitionButtons.FULL_VIEW_TOOLTIP
        saveJSON.isEnabled = false
        saveJSON.icon = setIcon(firstButton.width, firstButton.height, "/icons/saveROI.png", "Save ROIs", loggerService)

        closeAllWindowsExistingExpButton.maximumSize = Dimension(firstButton.width + 15, firstButton.height + 15)
        closeAllWindowsExistingExpButton.toolTipText = "Close all windows"
        closeAllWindowsExistingExpButton.isEnabled = false
        closeAllWindowsExistingExpButton.icon =
            setIcon(firstButton.width, firstButton.height, "/icons/close_all_button.png", "Close all windows", loggerService)

        loadExistingExperimentButton.maximumSize = Dimension(firstButton.width + 15, firstButton.height + 15)
        loadExistingExperimentButton.toolTipText = "Load existing experiment"
        loadExistingExperimentButton.isEnabled = true
        loadExistingExperimentButton.icon =
            setIcon(firstButton.width, firstButton.height, "/icons/load_prev_experiment.png", "Load Existing Experiment", loggerService)

        loadExperimentConfigButton.maximumSize = Dimension(firstButton.width + 15, firstButton.height + 15)
        loadExperimentConfigButton.toolTipText = "Load an experiment configuration"
        loadExperimentConfigButton.isEnabled = true
        loadExperimentConfigButton.icon =
            setIcon(firstButton.width, firstButton.height, "/icons/load.png", "Load Experiment", loggerService)

        startPreviewExperimentButton.maximumSize = Dimension(firstButton.width + 15, firstButton.height + 15)
        startPreviewExperimentButton.toolTipText = "Start preview"
        startPreviewExperimentButton.isEnabled = false
        startPreviewExperimentButton.icon =
            setIcon(firstButton.width, firstButton.height, "/icons/startPreview.png", "StartPreview", loggerService)

        startAcquisitionExperimentButton.maximumSize = Dimension(firstButton.width + 15, firstButton.height + 15)
        startAcquisitionExperimentButton.toolTipText = "Start acquisition"
        startAcquisitionExperimentButton.isEnabled = false
        startAcquisitionExperimentButton.icon =
            setIcon(firstButton.width, firstButton.height, "/icons/startAcquisition.png", "StartPreview", loggerService)

        pauseExperimentButton.maximumSize = Dimension(firstButton.width + 15, firstButton.height + 15)
        pauseExperimentButton.toolTipText = "Pause/restart acquisition"
        pauseExperimentButton.isEnabled = false
        pauseExperimentButton.icon =
            setIcon(firstButton.width, firstButton.height, "/icons/pause.png", "Pause", loggerService)

        stopExperimentButton.maximumSize = Dimension(firstButton.width + 15, firstButton.height + 15)
        stopExperimentButton.toolTipText = "Stop experiment"
        stopExperimentButton.isEnabled = false
        stopExperimentButton.icon =
            setIcon(firstButton.width, firstButton.height, "/icons/stop.png", "Stop", loggerService)

        // mainWindowIcon = setIcon(firstButton.width, firstButton.height, Paths.Icons.STRIMM_LOGO_ICON, "Strimm Logo", loggerService, false)
        if (mainWindowIcon != null) strimmUIService.strimmFrame.iconImage = mainWindowIcon!!.image

        imageJButtonBar.add(loadExistingExperimentButton)
        addExistingExperimentButtonListener()

        imageJButtonBar.add(saveJSON)
        addSaveJSONButtonListener()

        imageJButtonBar.add(loadExperimentConfigButton)
        addLoadButtonListener()

        imageJButtonBar.addSeparator()

        imageJButtonBar.add(startPreviewExperimentButton)
        addStartPreviewButtonListener()

        imageJButtonBar.add(startAcquisitionExperimentButton)
        addStartAcquisitionButtonListener()

        imageJButtonBar.add(pauseExperimentButton)
        addPauseButtonListener()

        imageJButtonBar.add(stopExperimentButton)
        addStopButtonListener()

        imageJButtonBar.addSeparator()
        imageJButtonBar.addSeparator()
        imageJButtonBar.addSeparator()
        imageJButtonBar.add(closeAllWindowsExistingExpButton)
        addCloseAllWindowsExistinExpButtonListener()
        strimmUIService.state = UIstate.IDLE
    }

    private fun addCloseAllWindowsExistinExpButtonListener(){
        closeAllWindowsExistingExpButton.addActionListener {
            loggerService.log(Level.INFO, "Closing all open windows")
            dockableWindowPluginService.dockableWindows.forEach { x -> x.value.close() }
            closeAllWindowsExistingExpButton.isEnabled = false
        }
    }

    private fun addExistingExperimentButtonListener(){
        loadExistingExperimentButton.addActionListener {
            val path = "."
            val folder = File(path)
            val fileList = folder.listFiles { f -> f.extension == "h5" }
            if (fileList != null && fileList.isNotEmpty()) {
                val fileComboBox = JComboBox(fileList.map { f -> f.name.replace(".h5", "") }.toTypedArray())
                fileComboBox.selectedIndex = 0
                val fileChoice = JOptionPane.showConfirmDialog(strimmUIService.strimmFrame,
                    fileComboBox, "Select experiment file", JOptionPane.OK_CANCEL_OPTION)

                if(fileChoice == 0){ //user selects OK
                    val selectedConfigFile = fileComboBox.selectedItem as String
                    experimentService.loadH5File(path, "$selectedConfigFile.h5")
                }
            }
            else{
                JOptionPane.showMessageDialog(strimmUIService.strimmFrame, "No h5 files found")
            }
        }
    }

    private fun addSaveJSONButtonListener() {
        saveJSON.addActionListener {
            val overlays = overlayService.getOverlays()
            for (f in 0..experimentService.expConfig.sinkConfig.sinks.size - 1) {
                val sinkk = experimentService.expConfig.sinkConfig.sinks[f]
                val disp = experimentService.experimentStream.cameraDisplays[sinkk.sinkName]
                if (disp != null) {
                    val over = overlayService.getOverlays(disp) //overlays in disp but might be reapeted
                    val filteredOverlays = overlays.filter { it in over }
                    var combinedList = mutableListOf<ROI>()
                    for (over in filteredOverlays) {
                        var roi: ROI = ROI()
                        if (over is RectangleOverlay) {
                            roi.ROItype = "RECTANGLE"
                            roi.x = over.getOrigin(0)
                            roi.y = over.getOrigin(1)
                            roi.w = over.getExtent(0)
                            roi.h = over.getExtent(1)
                            roi.ROIName = over.name
                        } else if (over is EllipseOverlay) {
                            roi.ROItype = "ELLIPSE"
                            roi.x = over.getOrigin(0)
                            roi.y = over.getOrigin(1)
                            roi.w = over.getRadius(0)
                            roi.h = over.getRadius(1)
                            roi.ROIName = over.name
                        } else {
                        }
                        combinedList.add(roi)
                    }
                    val roiSz =
                        experimentService.experimentStream.sinkMethods[sinkk.sinkName]!!.properties["roiSz"]
                    if (roiSz != null) {
                        strimmROIService.EncodeROIReference(roiSz, combinedList)
                    }
                }
            }
        }

    }

    private fun addLoadButtonListener() {
        loadExperimentConfigButton.addActionListener {
            val folder = File(".")
            //get a list of JSONs from the ExperimentConfigurations folder in the Working Directory
            val fileList = folder.listFiles { f -> f.extension == "json" }
            if (fileList != null && fileList.isNotEmpty()) {
                //fill a Combo Box with these JSONs
                val fileComboBox = JComboBox(fileList.map { f -> f.name.replace(".json", "") }.toTypedArray())
                fileComboBox.selectedIndex = 0
                //User selects a JSON
                val configChoice = JOptionPane.showConfirmDialog(
                    strimmUIService.strimmFrame,
                    fileComboBox, "Select experiment configuration", JOptionPane.OK_CANCEL_OPTION
                )
                if (configChoice == 0) { //user selects OK
                    //todo check this
                    //experimentService.stopStream()
                    val selectedConfigFile = fileComboBox.selectedItem as String
                    //store the selectedFile : File so that it can be reloaded when the experiment is finished
                    //so that the user does not have to search for it again.
                    selectedFile = fileList.find { f -> f.name.replace(".json", "") == selectedConfigFile }!!
                    if (selectedFile != null) {
                        //read the JSON and inflate it into an ExperimentConfiguration - which has fields for each entry in the JSON
                        val loadSuccess = experimentService.convertGsonToConfig(selectedFile as File)
                        if (loadSuccess) {
                            //The 2 following functions the ExperimentConfiguration into a stream ready to go and put STRIMM into a WAITING mode
                            //the expConfig is used to create all of the Sources, Flows and Sinks - to make akka components along with actors
                            //and docking windows and wire them all together into an akka-graph which when this function finishes is primed and ready to go.
                            experimentService.createExperimentStream() //makes the initial ExperimentStream from the expConfig
                            createStreamGraph()  //creates the akka-graph and is ready to run - STRIMM in WAITING mode
                            startAcquisitionExperimentButton.isEnabled = true
                            startPreviewExperimentButton.isEnabled = true
                            loadExperimentConfigButton.isEnabled = true
                            GUIMain.experimentService.EndExperimentAndSaveData =
                                false //this is a flag which when set to true (in ACDQUISITION mode), will trigger the saving of data - this allows the user to press the stop button and everything be correctly saved.
                        } else {
                            JOptionPane.showMessageDialog(
                                strimmUIService.strimmFrame,
                                ComponentTexts.AcquisitionDialogs.ERROR_LOADING_EXPCONFIG
                            )
                            strimmUIService.state = UIstate.IDLE
                        }
                    }
                }
            }
            else {
                JOptionPane.showMessageDialog(
                    strimmUIService.strimmFrame,
                    "Error loading dialog"
                )
                strimmUIService.state = UIstate.IDLE
            }


        }
    }

    //Both of the following functions will run the stream, they define different STRIMM modes and also activate and grey out different buttons on the toolbar
    private fun addStartPreviewButtonListener() {
        startPreviewExperimentButton.addActionListener {


            saveJSON.isEnabled = true
            startPreviewExperimentButton.isEnabled = false
            startAcquisitionExperimentButton.isEnabled = false
            stopExperimentButton.isEnabled = true
            loadExperimentConfigButton.isEnabled = false
            //  GUIMain.experimentService.EndExperimentAndSaveData = false
            strimmUIService.state = UIstate.PREVIEW
            //run the stream
            experimentService.runStream()

        }
    }

    private fun addStartAcquisitionButtonListener() {
        startAcquisitionExperimentButton.addActionListener {

            actorService.fileManagerActor.tell(AskInitHDF5File(), null)
            startAcquisitionExperimentButton.isEnabled = false
            startPreviewExperimentButton.isEnabled = false
            stopExperimentButton.isEnabled = true
            pauseExperimentButton.isEnabled = true
            loadExperimentConfigButton.isEnabled = false
            //  GUIMain.experimentService.EndExperimentAndSaveData = false
            strimmUIService.state = UIstate.ACQUISITION
            //run the stream
            experimentService.runStream()

        }
    }

    //This function controls the logic of going to ACQUISITION_PAUSED state and manages relevant button states,  unless STRIMM is in ACQUISITION mode then it cannot save data, so by going into ACQUISITION_PAUSED mode the saving is suspended for the duration of the pause.
    private fun addPauseButtonListener() {
        pauseExperimentButton.addActionListener {
            if (strimmUIService.state == UIstate.ACQUISITION) {
                stopExperimentButton.isEnabled = false
                strimmUIService.state = UIstate.ACQUISITION_PAUSED
            } else {
                stopExperimentButton.isEnabled = true
                strimmUIService.state = UIstate.ACQUISITION
            }
        }
    }


    private fun addStopButtonListener() {
        stopExperimentButton.addActionListener {

            stopExperimentButton.isEnabled = false
            pauseExperimentButton.isEnabled = false
            startAcquisitionExperimentButton.isEnabled = true
            startPreviewExperimentButton.isEnabled = true
            loadExperimentConfigButton.isEnabled = true

            experimentService.stopStream()
            //selectedFile will be null if STRIMM is in an IDLE state so check for this
            //somebody just ran STRIMM and pressed stop.
            if (selectedFile != null) {

                //load the previously selected file
                val loadSuccess = experimentService.convertGsonToConfig(selectedFile as File)
                //create an ExperimentStream
                experimentService.createExperimentStream()
                //turn the JSON into a runnable graph.
                createStreamGraph()
            }

        }
    }

    //TODO might not work due to specific windows closing required

    //LEGACY  This is the response to pressing the close button
    //if in Acquisition mode it should save the data that has been acquired before closing
    //it also is set to shut down the NIDAQ and other special things from ProtocolService
    //again these may need to be placed to other places in the code.
    fun setOnClose(frame: JFrame) {
        val exitListener = object : WindowAdapter() {
            override fun windowClosing(e: java.awt.event.WindowEvent?) {
                if (strimmUIService.state == UIstate.PREVIEW || strimmUIService.state == UIstate.ACQUISITION || strimmUIService.state == UIstate.ACQUISITION_PAUSED || strimmUIService.state == UIstate.WAITING) {
                    //if PREVIEW destroy objects only otherwise savedata
                    //todo complete acquisition stop

                    //todo test safe exit
                    experimentService.stopStream()
                    println("WindowClose stop")
                }
                //shared exit code
                var bBusy = true
//                while (bBusy) {
//                    bBusy = false
//                    for (src in GUIMain.experimentService.expConfig.sourceConfig.sources) {
//                        if (src.sourceType == "NIDAQSource"){
//                            println("TERMINATE NIDAQ Thread")
//                            GUIMain.protocolService.TerminateNIDAQProtocol();
//                            src.isBusy = false;
//                            continue;
//                        }
//                    }
//                }
//                loggerService.log(Level.INFO, "Closing STRIMM")
//                protocolService.ShutdownCameraMap()
//                loggerService.fh.close()

                //
                //
                // end remote control
//                shutdownRemoteControl()
//                controlThread!!.EndThread()
                //
                //
                //
                commandService.run(QuitProgram::class.java, false)
                super.windowClosing(e)
                Platform.exit()
            }
        }
        frame.addWindowListener(exitListener)
    }

    companion object {
        var controlThread: CommandServerThread? = null
        val commandTCPPort = 6000
        var commandServerSocket: ServerSocket? = null
        var commandInputStream: InputStream? = null
        var commandOutputStream: OutputStream? = null
        private fun initRemoteControl() {
            commandServerSocket = ServerSocket(commandTCPPort)
        }

        private fun shutdownRemoteControl() {
//            commandServerSocket!!.close()
        }

        fun remoteLoadExperiment(selectedConfigFile: String) {
            println("Remotely load an experiment")
            //store the selectedFile : File so that it can be reloaded when the experiment is finished
            //so that the user does not have to search for it again.
            val folder = File(".")
            //get a list of JSONs from the ExperimentConfigurations folder in the Working Directory
            val fileList = folder.listFiles { f -> f.extension == "json" }
            selectedFile = fileList.find { f -> f.name.replace(".json", "") == selectedConfigFile }!!
            if (selectedFile != null) {
                //read the JSON and inflate it into an ExperimentConfiguration - which has fields for each entry in the JSON
                val loadSuccess = experimentService.convertGsonToConfig(selectedFile as File)
                if (loadSuccess) {
                    //The 2 following functions the ExperimentConfiguration into a stream ready to go and put STRIMM into a WAITING mode
                    //the expConfig is used to create all of the Sources, Flows and Sinks - to make akka components along with actors
                    //and docking windows and wire them all together into an akka-graph which when this function finishes is primed and ready to go.
                    experimentService.createExperimentStream() //makes the initial ExperimentStream from the expConfig
                    createStreamGraph()  //creates the akka-graph and is ready to run - STRIMM in WAITING mode
                    startAcquisitionExperimentButton.isEnabled = true
                    startPreviewExperimentButton.isEnabled = true
                    loadExperimentConfigButton.isEnabled = true
                    GUIMain.experimentService.EndExperimentAndSaveData =
                        false //this is a flag which when set to true (in ACDQUISITION mode), will trigger the saving of data - this allows the user to press the stop button and everything be correctly saved.
                } else {
                    JOptionPane.showMessageDialog(
                        strimmUIService.strimmFrame,
                        ComponentTexts.AcquisitionDialogs.ERROR_LOADING_EXPCONFIG
                    )
                    strimmUIService.state = UIstate.IDLE
                }
            }
        }

        private fun remoteClose() {
            println("Remotely close")
            if (strimmUIService.state == UIstate.PREVIEW || strimmUIService.state == UIstate.ACQUISITION || strimmUIService.state == UIstate.ACQUISITION_PAUSED || strimmUIService.state == UIstate.WAITING) {
                //if PREVIEW destroy objects only otherwise savedata
                //todo complete acquisition stop

                //todo test safe exit
                experimentService.stopStream()
                println("WindowClose stop")
            }
            var bBusy = true
            shutdownRemoteControl()
            //
            //
            // end remote control
            shutdownRemoteControl()
            controlThread!!.EndThread()
            //
            //
            //
            commandService.run(QuitProgram::class.java, false)
            Platform.exit()
        }

        var selectedFile: File? = null // the currently loaded JSON
        val loadExistingExperimentButton = JButton() // loads a h5 file containing an existing experiment. Will display all data for quick viewing
        val closeAllWindowsExistingExpButton = JButton() // Button for specifically closing windows opened from loadExistingExperimentButton functionality

        val loadExperimentConfigButton = JButton() // loads the JSON, brings STRIMM to a WAITING state
        val startPreviewExperimentButton =
            JButton() // runs the JSON, but does not store any frames, STRIMM is in a PREVIEW state
        val startAcquisitionExperimentButton = JButton() // runs the JSON, stores data, STRIMM is in an ACQUISITION mode
        val stopExperimentButton =
            JButton() // stops the JSON, destroys existing resources and then reloads the experiment and moves back to a WAITING mode
        val pauseExperimentButton =
            JButton() // will prevent an acquisition from saving data as long as paused, puts STRIMM into the ACQUISITION_PAUSED mode
        val saveJSON =
            JButton() //legacy - this will be changed to a ROISave button - allowing the user to select ROIs and then  save them into an ImageJ format - which is then reference in the JSON, it also means that ROIs could be made directly in ImageJ and imported to STRIMM
        var mainWindowIcon: ImageIcon? = null

        fun createStreamGraph() {
            //remove any previously loaded stream along with associated resourced eg docking windows

            GUIMain.experimentService.destroyStream()
            //make an ActorSystem along with akka Materializer
            GUIMain.actorService.mailboxConfig =
                com.typesafe.config.ConfigFactory.parseString("control-aware-dispatcher { mailbox-type = \"akka.dispatch.UnboundedControlAwareMailbox\" }")
            GUIMain.actorService.actorSystem =
                ActorSystem.create("STRIMMAkkaSystem", GUIMain.actorService.mailboxConfig)
            GUIMain.actorService.materializer = ActorMaterializer.create(
                ActorMaterializerSettings.create(GUIMain.actorService.actorSystem).withInputBuffer(
                    1,
                    1
                ), GUIMain.actorService.actorSystem
            )
            //create the main-actor (also called the StrimmActor) and also make the FileManagerActor which will write collected data to HDF5
            actorService.initStrimmAndFileManagerActors()
            //creates the graph from the JSON and then makes it ready to run - STRIMM is now in a WAITING mode
            val streamCreateSuccess = experimentService.createStreamGraph()
            if (!streamCreateSuccess) {
                //TODO decide if these JOptionPlane really improve user experience
                //JOptionPane.showMessageDialog(strimmUIService.strimmFrame, ComponentTexts.AcquisitionDialogs.ERROR_CREATING_GRAPH)
                strimmUIService.state = UIstate.IDLE
            } else {
                //JOptionPane.showMessageDialog(strimmUIService.strimmFrame, ComponentTexts.AcquisitionDialogs.STREAM_CREATE_SUCCESS)
                strimmUIService.state = UIstate.WAITING
            }
        }

        //Services
        @Parameter
        lateinit var lutService: LUTService

        @Parameter
        lateinit var actorService: ActorService

        @Parameter
        lateinit var imageJService: ImageJService

        @Parameter
        lateinit var uiService: UIService

        @Parameter
        lateinit var dockableWindowPluginService: DockableWindowPluginService

        @Parameter
        lateinit var displayService: DisplayService

        @Parameter
        lateinit var imageDisplayService: ImageDisplayService

        @Parameter
        lateinit var datasetService: DatasetService

        @Parameter
        lateinit var loggerService: LoggerService


        @Parameter
        lateinit var commandService: CommandService

        @Parameter
        lateinit var strimmUIService: StrimmUIService

        @Parameter
        lateinit var pluginService: PluginService

        @Parameter
        lateinit var threadService: ThreadService


        @Parameter
        lateinit var overlayService: OverlayService

        @Parameter
        lateinit var opService: OpService


        @Parameter
        lateinit var datasetIOService: DatasetIOService


        @Parameter
        lateinit var experimentService: ExperimentService

        @Parameter
        lateinit var softwareTimerService: SoftwareTimerService

        @Parameter
        lateinit var utilsService: UtilsService

        @Parameter
        lateinit var protocolService: ProtocolService


        @Parameter
        lateinit var strimmROIService: StrimmROIService

        @Parameter
        lateinit var zoomService: ZoomService


//there are only 8 different coloured legend symbols allowed in JavaFX bedore
        //it recycles, so do it all this way

        //this introduced some bugs

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
            Color.color(0.5, 0.5, 0.5)
        )
    }

    class CommandServerThread(name: String?, var commandServerSocket: ServerSocket) : Thread(name) {
        //keeps track of when the numOverlays for this display changes
        var bExit = false
        var commandInputStream: InputStream? = null
        fun EndThread() {
            bExit = true
        }

        fun processCommand(data: String): String {

            var retSz = "0"
            var dats = data.split(',')

            if (dats[0].toInt() == 1) {
                //load experiment
                try {

                    var exp_name = dats[1]

                    GUIMain.remoteLoadExperiment(exp_name)  //TODO return value

                } catch (ex: Exception) {
                    retSz = "1,Failed to load experiment"
                }


            } else if (dats[0].toInt() == 2) {
                //start preview
                try {
                    GUIMain.startPreviewExperimentButton.doClick()
                } catch (ex: Exception) {
                    retSz = "1,Start preview failed"
                }

            } else if (dats[0].toInt() == 3) {
                //start acquiring
                try {
                    GUIMain.startAcquisitionExperimentButton.doClick()
                } catch (ex: Exception) {
                    retSz = "1,Start acquisition failed"
                }
            } else if (dats[0].toInt() == 4) {
                //pause
                try {
                    GUIMain.pauseExperimentButton.doClick()
                } catch (ex: Exception) {
                    retSz = "1,Pause failed"
                }
            } else if (dats[0].toInt() == 5) {
                //stop
                try {
                    GUIMain.stopExperimentButton.doClick()
                } catch (ex: Exception) {
                    retSz = "1,Failed stop experiment"
                }
            } else {
                //close
                try {
                    GUIMain.remoteClose()
                } catch (ex: Exception) {
                    retSz = "1,Failed to close program"
                }

            }


            return retSz

        }

        override fun run() {

            while (!bExit) {
                //repeatedly do task
                var sock: Socket = commandServerSocket!!.accept()
                commandInputStream = sock.getInputStream()

                val reader = BufferedReader(
                    InputStreamReader(commandInputStream)
                )
                commandOutputStream = sock.getOutputStream();
                val writer = PrintWriter(commandOutputStream, true)

                val result: String = reader.readLine()
                if (result != null) {
                    var szRet = processCommand(result)
                    writer.println(szRet)
                    sock.close()
                }

            }
        }

    }
}



