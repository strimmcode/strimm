package uk.co.strimm.gui

import bibliothek.gui.dock.common.DefaultMultipleCDockable
import javafx.embed.swing.JFXPanel
import javafx.fxml.FXML
import javafx.fxml.FXMLLoader
import javafx.geometry.Insets
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.image.Image
import javafx.scene.image.ImageView
import javafx.scene.layout.StackPane
import org.scijava.plugin.Plugin
import uk.co.strimm.ComponentTexts
import uk.co.strimm.plugins.AbstractDockableWindow
import uk.co.strimm.plugins.DockableWindowPlugin
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import uk.co.strimm.Paths
import uk.co.strimm.SettingKeys
import uk.co.strimm.settings.Setting
import java.util.logging.Level

@Plugin(type = DockableWindowPlugin::class, menuPath = "Window>STRIMM settings")
class SettingsWindowPlugin : AbstractDockableWindow() {
    override lateinit var dockableWindowMultiple: DefaultMultipleCDockable
    override var title = ComponentTexts.SettingsWindow.SETTINGS_WINDOW_TITLE

    override fun initialise() {
        this.createDock(title).apply{
            val fxPanel = JFXPanel()
            add(fxPanel)
            this.titleText = title

            val loader = FXMLLoader(this.javaClass.getResource("/fxml/SettingsWindow.fxml"))
            val controller = SettingsWindow()
            loader.setController(controller)
            val pane = loader.load() as HBox
            val scene = Scene(pane)
            fxPanel.scene = scene
            dockableWindowMultiple = this
        }
    }
}

class SettingsWindow{
    //Components
    var settingsPane = HBox()
    lateinit var treeView: TreeView<String>
    @FXML
    lateinit var mainContainer : HBox
    var settingsDisplay = StackPane()
    val generalSettingsPane = VBox()
    val traceSettingsPane = VBox()
    val cameraSettingsPane = VBox()
    val buttonsPane = HBox()
    val saveButton = Button(ComponentTexts.SettingsWindow.SAVE_BUTTON)
    val saveAndExitButton = Button(ComponentTexts.SettingsWindow.SAVE_AND_EXIT_BUTTON)
    val exitButton = Button(ComponentTexts.SettingsWindow.EXIT_BUTTON)
    val generalPadding = 10

    /**
     * Initialise the components of the settings window
     */
    @FXML
    fun initialize() {
        populateSettingsTree()
        settingsPane.children.add(treeView)
        settingsPane.children.add(settingsDisplay)
        populateSettingsGroup(GUIMain.strimmSettingsService.settings.generalSettings.settings,generalSettingsPane)
        populateSettingsGroup(GUIMain.strimmSettingsService.settings.traceSettings.settings,traceSettingsPane)
        populateSettingsGroup(GUIMain.strimmSettingsService.settings.cameraSettings.settings,cameraSettingsPane)
        generalSettingsPane.isVisible = true
        traceSettingsPane.isVisible = false
        cameraSettingsPane.isVisible = false

        addSaveButtonListener()
        addSaveAndExitButtonListener()

        //Add to vbox so the buttons are below the settings
        //TODO should this be a scroll pane?
        val contentVBox = VBox()
        contentVBox.children.addAll(settingsDisplay, buttonsPane)

        buttonsPane.children.addAll(saveButton,saveAndExitButton,exitButton)
        mainContainer.children.addAll(treeView,contentVBox)
    }

    /**
     * Method to add listener to save  button click
     */
    fun addSaveButtonListener(){
        saveButton.setOnMouseClicked {
            saveAllSettingsToFile()
        }
    }

    /**
     * Method to add listener to save and exit button click
     */
    fun addSaveAndExitButtonListener(){
        saveAndExitButton.setOnMouseClicked {
            saveAllSettingsToFile()
            //TODO programmatically close this settings window
        }
    }

    /**
     * The method to call to save all settings to file
     */
    fun saveAllSettingsToFile(){
        GUIMain.loggerService.log(Level.INFO, "Saving general settings")
        val generalSettingsSuccess = saveSettingsGroup(generalSettingsPane)
        if(!generalSettingsSuccess){
            GUIMain.loggerService.log(Level.INFO, "One or more settings from \"General\" may not have been saved")
        }
        GUIMain.loggerService.log(Level.INFO, "Saving trace settings")
        val traceSettingsSuccess = saveSettingsGroup(traceSettingsPane)
        if(!traceSettingsSuccess){
            GUIMain.loggerService.log(Level.INFO, "One or more settings from \"Trace\" may not have been saved")
        }
        GUIMain.loggerService.log(Level.INFO, "Saving camera settings")
        val cameraSettingsSuccess = saveSettingsGroup(cameraSettingsPane)
        GUIMain.strimmSettingsService.saveSettingsToFile(GUIMain.strimmSettingsService.settings)
        if(!cameraSettingsSuccess){
            GUIMain.loggerService.log(Level.INFO, "One or more settings from \"Camera\" may not have been saved")
        }

        val alert = Alert(Alert.AlertType.INFORMATION)
        alert.title = "Saving settings"
        alert.headerText = null
        if(!generalSettingsSuccess || !traceSettingsSuccess || !cameraSettingsSuccess){
            alert.contentText = "One or more of the settings could not be saved. See logs for details"
        }
        else{
            alert.contentText = "Saving settings succeeded"
        }

        alert.showAndWait()
    }

    /**
     * This method take the VBox containing the settings fields for a group and maps them to a setting. This is based on
     * the setting name (NOT display name) that is in the user data field of the first label. This method assumes that
     * the first two components are the label and the text field
     * @param settingsPane The settings pane representing the settings group
     * @return The outcome of updating settings for this group. True if all settings were found and updated. False if
     * one or more settings were not found or updated
     */
    fun saveSettingsGroup(settingsPane : VBox) : Boolean {
        var outcome = true
        val settingsChildren = settingsPane.children
        for(settingsChild in settingsChildren){
            val hbox = settingsChild as HBox
            var settingName : String? = null
            var settingValue : Any? = null
            for(component in hbox.children){
                if(component.userData != null){
                    settingName = component.userData as String?
                }

                if(component is TextField){
                    settingValue = component.text
                }

                if(settingName != null && settingValue != null) {
                    val updateSuccessful = GUIMain.strimmSettingsService.updateSetting(settingName, settingValue)
                    settingName = null
                    settingValue = null

                    if(!updateSuccessful){
                        outcome = false
                    }
                }
            }
        }

        return outcome
    }

    /**
     * Create the settings tree but hide the root so we have many "top level" nodes
     */
    fun populateSettingsTree() {
        treeView = TreeView()
        val rootItem = TreeItem<String>("SettingsWindow") //Root node won't be shown
        treeView.isShowRoot = false
        treeView.root = rootItem
        rootItem.children.addAll(getAllSettingCategories())
        treeView.selectionModel.select(0)
        addTreeViewSelectedListener()
    }

    /**
     * When a top level tree node is clicked, switch the view to the one that has been clicked
     */
    fun addTreeViewSelectedListener(){
        treeView.selectionModel.selectedItemProperty().addListener({ observable, oldValue, newValue ->
            val selectedItem = newValue as TreeItem
            when {
                selectedItem.value.toLowerCase() == SettingKeys.TraceSettings.GROUP_NAME.toLowerCase() -> {
                    traceSettingsPane.isVisible = true
                    generalSettingsPane.isVisible = false
                    cameraSettingsPane.isVisible = false
                }
                selectedItem.value.toLowerCase() == SettingKeys.CameraSettings.GROUP_NAME.toLowerCase() -> {
                    traceSettingsPane.isVisible = false
                    generalSettingsPane.isVisible = false
                    cameraSettingsPane.isVisible = true
                }
                selectedItem.value.toLowerCase() == SettingKeys.GeneralSettings.GROUP_NAME.toLowerCase() -> {
                    traceSettingsPane.isVisible = false
                    generalSettingsPane.isVisible = true
                    cameraSettingsPane.isVisible = false
                }
            }
        })
    }

    /**
     * Get as list of top level nodes for the settings groups
     * @return a list of tree nodes for the settings groups
     */
    fun getAllSettingCategories() : List<TreeItem<String>>{
        val settingGroupNames = arrayListOf<TreeItem<String>>()
        settingGroupNames.add(TreeItem(GUIMain.strimmSettingsService.settings.generalSettings.settingGroupName))
        settingGroupNames.add(TreeItem(GUIMain.strimmSettingsService.settings.traceSettings.settingGroupName))
        settingGroupNames.add(TreeItem(GUIMain.strimmSettingsService.settings.cameraSettings.settingGroupName))
        return settingGroupNames
    }

    /**
     * Go through each setting group and populate its pane with all available settings
     * @param settingsList The list of settings for a settings group
     * @param pane The graphical pane to add the settings to
     */
    fun populateSettingsGroup(settingsList : List<Setting>, pane: VBox){
        for(setting in settingsList){
            if(setting.value is Number){
                addNumericSetting(pane, setting)
            }
            else{
                addTextSetting(pane, setting)
            }
        }
        settingsDisplay.children.add(pane)
    }

    /**
     * Create the components for a numeric setting
     * @param settingPane The pane to add these components to
     * @param setting The setting
     */
    fun addNumericSetting(settingPane : VBox, setting : Setting){
        val settingLabel = Label("${setting.displayName}: ")
        settingLabel.userData = setting.name
        val numberField = TextField(setting.value.toString())
        val toolTipLabel = setToolTip(setting)
        val hBox = HBox()
        hBox.children.addAll(settingLabel,numberField,toolTipLabel)
        settingPane.children.add(hBox)
    }

    /**
     * Create the components for a text setting
     * @param settingPane The pane to add these components to
     * @param setting The setting
     */
    fun addTextSetting(settingPane : VBox, setting : Setting){
        val settingLabel = Label("${setting.displayName}: ")
        settingLabel.userData = setting.name
        val numberField = TextField(setting.value.toString())
        val toolTipLabel = setToolTip(setting)
        val hBox = HBox()
        hBox.children.addAll(settingLabel,numberField,toolTipLabel)
        settingPane.children.add(hBox)
    }

    /**
     * Add a tooltip to a blank label. Set its icon. The tooltip text will come from the setting
     * @param setting The setting containing the tooltip text
     * @return A label with the tooltip
     */
    fun setToolTip(setting : Setting): Label{
        val toolTipLabel = Label("")
        val toolTip = Tooltip(setting.toolTip)

        var image : Image? = null
        try {
            image = Image(javaClass.getResourceAsStream(Paths.Icons.TOOLTIP_ICON), 17.0,17.0,true,true)
        }
        catch (ex : Exception){
            GUIMain.loggerService.log(Level.WARNING, "Failed to load tool tip icon. Error: ${ex.message}")
            GUIMain.loggerService.log(Level.WARNING, ex.stackTrace)
        }

        if(image != null) {
            toolTipLabel.graphic = ImageView(image)
        }

        toolTipLabel.tooltip = toolTip
        return toolTipLabel
    }
}