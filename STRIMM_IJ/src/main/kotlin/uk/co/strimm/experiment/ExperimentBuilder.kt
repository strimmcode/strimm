package uk.co.strimm.experiment

import akka.actor.Actor
import bibliothek.gui.dock.common.DefaultMultipleCDockable
import bibliothek.gui.dock.common.DefaultSingleCDockable
import bibliothek.gui.dock.dockable.IconHandling
import javafx.embed.swing.JFXPanel
import javafx.fxml.FXML
import javafx.fxml.FXMLLoader
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.TreeItem
import javafx.scene.control.TreeView
import javafx.scene.layout.*
import org.scijava.plugin.Plugin
import uk.co.strimm.*
import uk.co.strimm.actors.*
import uk.co.strimm.gui.GUIMain
import uk.co.strimm.plugins.AbstractDockableWindow
import uk.co.strimm.plugins.DockableWindowPlugin
import java.awt.BorderLayout
import java.awt.Image
import java.util.logging.Level
import javax.imageio.ImageIO
import javax.swing.Icon
import javax.swing.ImageIcon
import javax.swing.JFrame
import javax.swing.JLabel

@Plugin(type = DockableWindowPlugin::class, menuPath = "Window>Experiment Builder")
class ExperimentBuilderPlugin : AbstractDockableWindow() {
    override lateinit var dockableWindowMultiple : DefaultMultipleCDockable
    override var title = ComponentTexts.ExperimentBuilder.EXPERIMENT_BUILDER_WINDOW_TITLE
    private lateinit var icon: Icon

    override fun initialise() {

        this.createDock(title).apply{
            val fxPanel = JFXPanel()
            add(fxPanel)
            this.titleText = title

            val loader = FXMLLoader(this.javaClass.getResource("/fxml/ExperimentBuilder.fxml"))
            val controller = ExperimentBuilderWindow()
            loader.setController(controller)
            val pane = loader.load() as VBox
            val scene = Scene(pane)
            fxPanel.scene = scene
            this.titleIcon = setIcon(15, 15, Paths.Icons.EXPERIMENT_BUILDER_WINDOW_ICON,
                    title, GUIMain.loggerService, false)
            dockableWindowMultiple = this
        }

    }
}


class ExperimentBuilderWindow{

    @FXML
    lateinit var experimentPane : VBox

    @FXML
    lateinit var treeGridPane : GridPane

    @FXML
    lateinit var outerSummaryPane : VBox

    lateinit var summaryPane : VBox

    @FXML
    fun initialize(){
        createInBuiltCommands()
        createUI()
    }

    fun createUI(){
        treeGridPane.prefHeightProperty().bind(experimentPane.heightProperty().multiply(0.5))
        outerSummaryPane.prefHeightProperty().bind(experimentPane.heightProperty().multiply(0.5))
        createTreeLists()
        createSummaryPanel()
        createButtonPanel()
    }

    //region Tree Lists
    fun createTreeLists(){
        val inputVBox = createTreeList(ComponentTexts.ExperimentBuilder.INPUT_TREE_TITLE, ExperimentTreeType.INPUT_FEEDS)
        val conditionVBox = createTreeList(ComponentTexts.ExperimentBuilder.CONDITION_TREE_TITLE, ExperimentTreeType.CONDITIONS)
        val commandVBox = createTreeList(ComponentTexts.ExperimentBuilder.COMMAND_TREE_TITLE, ExperimentTreeType.COMMANDS)
        treeGridPane.hgap = 5.0
        treeGridPane.vgap = 5.0
        val col0 = ColumnConstraints(200.0)//TODO specify as percentage?
        col0.hgrow = Priority.ALWAYS
        val col1 = ColumnConstraints(200.0)//TODO specify as percentage?
        col1.hgrow = Priority.ALWAYS
        val col2 = ColumnConstraints(200.0)//TODO specify as percentage?
        col2.hgrow = Priority.ALWAYS
        treeGridPane.columnConstraints.addAll(col0,col1,col2)
        treeGridPane.add(inputVBox,0,0)
        treeGridPane.add(conditionVBox,1,0)
        treeGridPane.add(commandVBox,2,0)
    }

    fun createTreeList(title : String, treeType : ExperimentTreeType) : VBox{
        val treeBox = VBox()
        treeBox.styleClass.add(CssClasses.EXPERIMENT_TREE_BOX)
        val treeLabel = Label(title)
        treeLabel.styleClass.add(CssClasses.EXPERIMENT_TREE_BOX_LABEL)
        treeBox.children.add(treeLabel)


        val treeView = TreeView<String>()
        treeView.style = "-fx-border-color: orange;-fx-border-width : 1;"
        treeView.isShowRoot = false
        val rootItem = TreeItem<String>("Root")
        when(treeType) {
            ExperimentTreeType.INPUT_FEEDS -> {
                getAllInputFeeds().map { rootItem.children.add(TreeItem<String>(it)) }
            }
            ExperimentTreeType.CONDITIONS -> {
                getAllConditions().map { rootItem.children.add(TreeItem<String>(it)) }
            }
            ExperimentTreeType.COMMANDS ->{
                getAllCommands().map { rootItem.children.add(TreeItem<String>(it)) }
            }
        }
        treeView.prefHeight = 200.0//TODO set this properly
        treeView.root = rootItem
        treeBox.children.add(treeView)
        return treeBox
    }

    fun getAllInputFeeds() : ArrayList<String>{ //TODO remember to put ROIs as children
        val allActors = GUIMain.actorService.allActors
        val feedsList = arrayListOf<String>()
        for(actor in allActors){
            if(actor.value.second == TraceActor::class.java || actor.value.second == CameraActor::class.java) {
                feedsList.add(actor.key.path().name())
            }
        }
        return feedsList
    }

    fun getAllConditions() : ArrayList<String>{

        return arrayListOf("Condition 1", "Condition 2", "Condition 3")
    }

    fun getAllCommands() : ArrayList<String>{
        val allActors = GUIMain.actorService.allActors
        val feedsList = arrayListOf<String>()
//        for(actor in allActors){
//            if(actor.value.second == MoveStageCommandActor::class.java || actor.value.second == TriggerCommandActor::class.java) {
//                feedsList.add(actor.key.path().name())
//            }
//        }
        return feedsList
    }

    fun createInBuiltCommands(){
        GUIMain.experimentCommandService.createPlugin(MoveStageCommandPlugin::class.java)
        GUIMain.experimentCommandService.createPlugin(TriggerCommandPlugin::class.java)
    }
    //endregion

    //region Summary Panel
    fun createSummaryPanel(){
        summaryPane = VBox()
        summaryPane.isFillWidth = true
        summaryPane.styleClass.add(CssClasses.EXPERIMENT_SUMMARY)
        summaryPane.prefHeightProperty().bind(outerSummaryPane.heightProperty())
        outerSummaryPane.children.add(summaryPane)
    }

    fun createButtonPanel(){
        val loadButton = Button("Load")
        val saveButton = Button("Save")
        val saveAndExitButton = Button("Save & Exit")
        val buttonPane = GridPane()
        buttonPane.add(loadButton,0,0)
        buttonPane.add(saveAndExitButton,1,0)
        buttonPane.add(saveButton,2,0)
        buttonPane.alignment = Pos.BOTTOM_RIGHT
        outerSummaryPane.children.add(buttonPane)
    }
    //endregion
}