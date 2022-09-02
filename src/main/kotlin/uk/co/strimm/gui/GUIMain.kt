
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
import net.imagej.lut.LUTService
import net.imagej.ops.OpService
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
import uk.co.strimm.*

import uk.co.strimm.actors.messages.ask.AskInitHDF5File
import uk.co.strimm.services.*
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.event.WindowAdapter
import java.io.File
import javax.swing.*


/**
 * uk.co.strimm.Main GUI for the STRIMM plugin
 */
@Plugin(type = Command::class, headless = true, menuPath = "Plugins>STRIMM")
class GUIMain : Command {

    override fun run() {

        initAndShowGUI()
    }
    private fun initAndShowGUI() {
        //could load all dlls into address space here
        //initialiseLibrary(relativeLibraryPath("UHDF5"))
        
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
    private fun addAcquisitionButtons(imageJButtonBar : SwingToolBar){
        val firstButton = imageJButtonBar.components[0] as JToggleButton
        imageJButtonBar.addSeparator()

        //saveJSON is legacy and will be replaced with saveROI which will save out user
        //selected ROIs into the format of imageJ ROI Manager so that they can be referenced in the JSON
        saveJSON.maximumSize = Dimension(firstButton.width+15,firstButton.height+15)
       // saveJSON.toolTipText = ComponentTexts.AcquisitionButtons.FULL_VIEW_TOOLTIP
        saveJSON.isEnabled = false
       // saveJSON.icon = setIcon(firstButton.width, firstButton.height, "/icons/saveJSON.png", "Save ROIs", loggerService)


        loadExperimentConfigButton.maximumSize = Dimension(firstButton.width+15,firstButton.height+15)
        loadExperimentConfigButton.toolTipText = "Load an experiment configuration"
        loadExperimentConfigButton.isEnabled = true
        loadExperimentConfigButton.icon = setIcon(firstButton.width, firstButton.height, "/icons/load.png", "Load Experiment", loggerService)

        startPreviewExperimentButton.maximumSize = Dimension(firstButton.width+15,firstButton.height+15)
        startPreviewExperimentButton.toolTipText = "Start preview"
        startPreviewExperimentButton.isEnabled = false
        startPreviewExperimentButton.icon = setIcon(firstButton.width, firstButton.height,"/icons/startPreview.png", "StartPreview", loggerService)

        startAcquisitionExperimentButton.maximumSize = Dimension(firstButton.width+15,firstButton.height+15)
        startAcquisitionExperimentButton.toolTipText = "Start acquisition"
        startAcquisitionExperimentButton.isEnabled = false
        startAcquisitionExperimentButton.icon = setIcon(firstButton.width, firstButton.height,"/icons/startAcquisition.png", "StartPreview", loggerService)

        pauseExperimentButton.maximumSize = Dimension(firstButton.width+15,firstButton.height+15)
        pauseExperimentButton.toolTipText = "Pause/restart acquisition"
        pauseExperimentButton.isEnabled = false
        pauseExperimentButton.icon = setIcon(firstButton.width, firstButton.height, "/icons/pause.png", "Pause", loggerService)


        stopExperimentButton.maximumSize = Dimension(firstButton.width+15,firstButton.height+15)
        stopExperimentButton.toolTipText = "Stop experiment"
        stopExperimentButton.isEnabled = false
        stopExperimentButton.icon = setIcon(firstButton.width, firstButton.height, "/icons/stop.png", "Stop", loggerService)



       // mainWindowIcon = setIcon(firstButton.width, firstButton.height, Paths.Icons.STRIMM_LOGO_ICON, "Strimm Logo", loggerService, false)
        if (mainWindowIcon != null) strimmUIService.strimmFrame.iconImage = mainWindowIcon!!.image

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

        strimmUIService.state = UIstate.IDLE
    }
    private fun addSaveJSONButtonListener(){
        //go through each sink with an roiSz
        saveJSON.addActionListener {
//                experimentService.expConfig.sinkConfig.sinks.forEach {
//                if (it.roiSz != null ) {
//                    var loadtime = experimentService.loadtimeRoiList[it.sinkName]?.map { it ->
//                        val roi = it.roi
//                        if (roi.ROItype == "RECTANGLE") {
//                            val overlay = it.overlay as RectangleOverlay
//                            roi.x = overlay.getOrigin(0)
//                            roi.y = overlay.getOrigin(1)
//                            roi.w = overlay.getExtent(0)
//                            roi.h = overlay.getExtent(1)
//                        }
//                        else if (roi.ROItype == "ELLIPSE"){
//                            val overlay = it.overlay as EllipseOverlay
//                            roi.x = overlay.getOrigin(0)
//                            roi.y = overlay.getOrigin(1)
//                            roi.w = overlay.getRadius(0)
//                            roi.h = overlay.getRadius(1)
//                        }
//                        else{
//
//                        }
//
//                    roi
//                    }
//                    var runtime = experimentService.runtimeRoiList[it.sinkName]?.map { it ->
//                        val roi = it.roi
//                        if (roi.ROItype == "RECTANGLE") {
//                            val overlay = it.overlay as RectangleOverlay
//                            roi.x = overlay.getOrigin(0)
//                            roi.y = overlay.getOrigin(1)
//                            roi.w = overlay.getExtent(0)
//                            roi.h = overlay.getExtent(1)
//                        }
//                        else if (roi.ROItype == "ELLIPSE"){
//                            val overlay = it.overlay as EllipseOverlay
//                            roi.x = overlay.getOrigin(0)
//                            roi.y = overlay.getOrigin(1)
//                            roi.w = overlay.getRadius(0)
//                            roi.h = overlay.getRadius(1)
//                        }
//                        else{
//
//                        }
//
//                        roi
//                    }
//                    var combinedList = mutableListOf<ROI>()
//                    loadtime?.forEach {
//                        combinedList.add(it)
//                    }
//                    runtime?.forEach {
//                        combinedList.add(it)
//                    }
//                    strimmROIService.EncodeROIReference(it.roiSz, combinedList)
//
//                }
//            }
        }

    }
    private fun addLoadButtonListener(){
        loadExperimentConfigButton.addActionListener{
            val folder = File(Paths.EXPERIMENT_CONFIG_FOLDER)
            //get a list of JSONs from the ExperimentConfigurations folder in the Working Directory
            val fileList = folder.listFiles { f -> f.extension == "json" }
            if(fileList != null && fileList.isNotEmpty()) {
                //fill a Combo Box with these JSONs
                val fileComboBox = JComboBox(fileList.map { f -> f.name.replace(".json", "") }.toTypedArray())
                fileComboBox.selectedIndex = 0
                //User selects a JSON
                val configChoice = JOptionPane.showConfirmDialog(strimmUIService.strimmFrame,
                        fileComboBox, "Select experiment configuration", JOptionPane.OK_CANCEL_OPTION)
                if (configChoice == 0) { //user selects OK

                    //todo check this
                    //experimentService.stopStream()
                    //

                    //
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
                            GUIMain.experimentService.EndExperimentAndSaveData = false //this is a flag which when set to true (in ACDQUISITION mode), will trigger the saving of data - this allows the user to press the stop button and everything be correctly saved.
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
    private fun addStartPreviewButtonListener(){
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
    private fun addStartAcquisitionButtonListener(){
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
    private fun addPauseButtonListener(){
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
    private fun addStopButtonListener(){
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
    //LEGACY  This is the response to pressing the close button
    //if in Acquisition mode it should save the data that has been acquired before closing
    //it also is set to shut down the NIDAQ and other special things from ProtocolService
    //again these may need to be placed to other places in the code.
    fun setOnClose(frame : JFrame){
        val exitListener = object : WindowAdapter() {
            override fun windowClosing(e: java.awt.event.WindowEvent?) {
                if (strimmUIService.state == UIstate.PREVIEW || strimmUIService.state == UIstate.ACQUISITION || strimmUIService.state == UIstate.ACQUISITION_PAUSED ){
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
                commandService.run(QuitProgram::class.java, false)
                super.windowClosing(e)
                Platform.exit()
            }
        }
        frame.addWindowListener(exitListener)
    }
    companion object {
        var selectedFile : File? = null // the currently loaded JSON
        val loadExperimentConfigButton = JButton() // loads the JSON, brings STRIMM to a WAITING state
        val startPreviewExperimentButton = JButton() // runs the JSON, but does not store any frames, STRIMM is in a PREVIEW state
        val startAcquisitionExperimentButton = JButton() // runs the JSON, stores data, STRIMM is in an ACQUISITION mode
        val stopExperimentButton = JButton() // stops the JSON, destroys existing resources and then reloads the experiment and moves back to a WAITING mode
        val pauseExperimentButton = JButton() // will prevent an acquisition from saving data as long as paused, puts STRIMM into the ACQUISITION_PAUSED mode
        val saveJSON = JButton() //legacy - this will be changed to a ROISave button - allowing the user to select ROIs and then  save them into an ImageJ format - which is then reference in the JSON, it also means that ROIs could be made directly in ImageJ and imported to STRIMM
        var mainWindowIcon: ImageIcon? = null

        fun createStreamGraph(){
            //remove any previously loaded stream along with associated resourced eg docking windows
            GUIMain.experimentService.destroyStream()
            //make an ActorSystem along with akka Materializer
            GUIMain.actorService.mailboxConfig = com.typesafe.config.ConfigFactory.parseString("control-aware-dispatcher { mailbox-type = \"akka.dispatch.UnboundedControlAwareMailbox\" }")
            GUIMain.actorService.actorSystem = ActorSystem.create("STRIMMAkkaSystem", GUIMain.actorService.mailboxConfig)
            GUIMain.actorService.materializer = ActorMaterializer.create(ActorMaterializerSettings.create(GUIMain.actorService.actorSystem).withInputBuffer(1, 1), GUIMain.actorService.actorSystem)
            //create the main-actor (also called the StrimmActor) and also make the FileManagerActor which will write collected data to HDF5
            actorService.initStrimmAndFileManagerActors()
            //creates the graph from the JSON and then makes it ready to run - STRIMM is now in a WAITING mode
            val streamCreateSuccess = experimentService.createStreamGraph()
            if (!streamCreateSuccess) {
                JOptionPane.showMessageDialog(strimmUIService.strimmFrame, ComponentTexts.AcquisitionDialogs.ERROR_CREATING_GRAPH)
                strimmUIService.state = UIstate.IDLE
            } else {
                JOptionPane.showMessageDialog(strimmUIService.strimmFrame, ComponentTexts.AcquisitionDialogs.STREAM_CREATE_SUCCESS)
                strimmUIService.state = UIstate.WAITING
            }
        }

        //Services
        @Parameter
        lateinit var lutService: LUTService

        @Parameter
        lateinit var actorService: ActorService

        @Parameter
        lateinit var imageJService : ImageJService

        @Parameter
        lateinit var uiService : UIService

        @Parameter
        lateinit var dockableWindowPluginService : DockableWindowPluginService

        @Parameter
        lateinit var displayService : DisplayService

        @Parameter
        lateinit var imageDisplayService : ImageDisplayService

        @Parameter
        lateinit var datasetService: DatasetService

        @Parameter
        lateinit var loggerService : LoggerService


        @Parameter
        lateinit var commandService : CommandService

        @Parameter
        lateinit var strimmUIService : StrimmUIService

        @Parameter
        lateinit var pluginService: PluginService

        @Parameter
        lateinit var threadService : ThreadService


        @Parameter
        lateinit var overlayService : OverlayService

        @Parameter
        lateinit var opService : OpService


        @Parameter
        lateinit var datasetIOService : DatasetIOService


        @Parameter
        lateinit var experimentService : ExperimentService

        @Parameter
        lateinit var softwareTimerService: SoftwareTimerService

        @Parameter
        lateinit var utilsService : UtilsService

        @Parameter
        lateinit var protocolService : ProtocolService
//


        //endregion
        val roiColours = arrayListOf<Color>(
            Color.color(1.0,0.0,0.0), //blue
        Color.color(0.0,1.0,0.0), //orange
        Color.color(0.00000,0.0,1.0), //yellow
        Color.color(1.0,0.0,1.0), //purple
        Color.color(0.0,1.0,1.0), //green
        Color.color(0.3010,0.7450,0.9330), //light blue
        Color.color(0.6350,0.0780,0.1840), //maroon
        Color.color(0.0,0.4470,0.7410), //blue
        Color.color(0.8500,0.3250,0.0980), //orange
         Color.color(0.9290,0.6940,0.1250), //yellow
         Color.color(0.4940,0.1840,0.5560), //purple
         Color.color(0.4660,0.6740,0.1880), //green
         Color.color(0.3010,0.7450,0.9330), //light blue
         Color.color(0.6350,0.0780,0.1840),
        Color.color(1.0,1.0,1.0)
        ) //maroon
    }
}
class MainController {

}

