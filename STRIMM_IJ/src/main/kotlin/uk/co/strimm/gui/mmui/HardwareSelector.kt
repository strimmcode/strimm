package uk.co.strimm.gui.mmui

import bibliothek.gui.dock.common.DefaultMultipleCDockable
import javafx.application.Platform
import javafx.beans.binding.Bindings
import javafx.beans.property.*
import javafx.embed.swing.JFXPanel
import javafx.event.EventHandler
import javafx.fxml.FXML
import javafx.fxml.FXMLLoader
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.input.MouseEvent
import javafx.scene.layout.*
import javafx.stage.FileChooser
import javafx.util.Callback
import mmcorej.DeviceType
import org.apache.commons.io.FilenameUtils
import org.scijava.plugin.Plugin
import uk.co.strimm.MicroManager.*
import uk.co.strimm.gui.GUIMain
import uk.co.strimm.plugins.AbstractDockableWindow
import uk.co.strimm.plugins.DockableWindowPlugin
import uk.co.strimm.services.MMDeviceDesc
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.nio.file.Paths
import java.util.logging.Level


@Plugin(type = DockableWindowPlugin::class, menuPath = "Window>uManager Configuration")
class MMHardwareSelector : AbstractDockableWindow() {
    override var title = "uManager Configuration"

    override var dockableWindowMultiple: DefaultMultipleCDockable = run{
        this.createDock(title).apply {
            val fxPanel = JFXPanel()
            add(fxPanel)
            Platform.runLater {
                fxPanel.scene = Scene(
                        FXMLLoader.load(this.javaClass.getResource("/fxml/mmui/HardwareSelector.fxml")), 100.0, 100.0)
            }
        }
    }
}

class HardwareSelectorController {
    data class DeviceDisplay(val type : StringProperty,
                             val name : StringProperty,
                             val description : StringProperty,
                             var preInitValues : Map<String, String>) {

        constructor(type: String, name: String, description: String)
                : this(SimpleStringProperty(type), SimpleStringProperty(name), SimpleStringProperty(description), emptyMap())

        constructor(type: String, name: String, description : String, preInitValues: Map<String, String>)
                : this(SimpleStringProperty(type), SimpleStringProperty(name), SimpleStringProperty(description), preInitValues)
    }

    private class RightClickMenu(val selected: Boolean,
                                 val node: TreeItem<HardwareSelectorController.DeviceDisplay>?,
                                 val selectedNode: TreeItem<HardwareSelectorController.DeviceDisplay>) : ContextMenu() {
        init {
            if (selected)
                items.addAll(
                        MenuItem("Configure")
                                .apply {
                                    onAction = EventHandler {
                                        val res = configure(node!!.value.preInitValues)
                                        if (res.isEmpty()) return@EventHandler
                                        node.value =
                                                DeviceDisplay(node.value.type,
                                                        SimpleStringProperty(res["Label"]!!),
                                                        node.value.description, res)
                                    } },
                        MenuItem("Remove")
                                .apply {
                                    onAction = EventHandler {
                                        selectedNode.children.remove(node)
                                    }
                                }
                )
            else
                items.addAll(
                        MenuItem("Add")
                                .apply {
                                    onAction = EventHandler {
                                        val res = configure(emptyMap())
                                        if (res.isEmpty()) return@EventHandler
                                        selectedNode.children.add(
                                                TreeItem(
                                                        DeviceDisplay(
                                                                "${node!!.value.name.value} (${node.value.type.value})",
                                                                res["Label"]!!,
                                                                "${node.value.description.value} " +
                                                                        "(From: ${node.parent.value.type.value})",
                                                                res)))
                                    }
                                }
                )

        }


        private fun configure(previousValues : Map<String, String>) = run {
            val (lib, name) =
                    if (!selected) {
                        Pair(node!!.parent.value.type.value,
                                node.value.name.value)
                    } else {
                        Pair(getDeviceLibrary(node!!), getDeviceName(node))
                    }

            val (table, map) = createTable(lib!!, name, previousValues)
            val dialog = Dialog<Map<String, String>>()
            dialog.dialogPane.content = table
            table.prefWidthProperty().bind(dialog.dialogPane.widthProperty())
            table.prefHeightProperty().bind(dialog.dialogPane.heightProperty())
            dialog.dialogPane.buttonTypes.add(ButtonType.OK)
            dialog.isResizable = true

            dialog.resultConverter = Callback { button ->
                if (button == ButtonType.OK)
                    map.mapValues { it.value.invoke() }
                else
                    null
            }

            dialog.showAndWait().orElse(emptyMap())
        }

        companion object {
            fun getDeviceLibrary(node : TreeItem<HardwareSelectorController.DeviceDisplay>) =
                    "\\(From: (\\w*)\\)$".toRegex().find(node.value.description.value)?.destructured?.component1()
            fun getDeviceName(node : TreeItem<HardwareSelectorController.DeviceDisplay>) =
                    node.value.type.value.substringBefore("(").dropLast(1)

            fun getPropertyControl(previousValues: Map<String, String>) =
                    { property : MMProperty<*> ->
                        Label(property.name) to when (property) {
                            is MMFloatProperty ->
                                Spinner<Double>(
                                        property.getLowerLimit().toDouble(),
                                        property.getUpperLimit().toDouble(),
                                        previousValues[property.name]?.toDoubleOrNull()
                                                ?: property.getValue().toDouble())
                                        .apply { isEditable = true}

                            is MMIntProperty ->
                                Spinner<Int>(
                                        property.getLowerLimit(),
                                        property.getUpperLimit(),
                                        previousValues[property.name]?.toIntOrNull()
                                                ?: property.getValue())
                                        .apply { isEditable = true }
                            is MMStringProperty ->
                                property.getAllowedValues()?.let {
                                    ComboBox<String>().apply {
                                        items.addAll(it)
                                        selectionModel.select(previousValues[property.name])
                                    }
                                } ?: TextField(previousValues[property.name] ?: property.getValue())
                            is MMUnknownProperty -> TextField()
                        }
                    }

            fun createGrid(controls : List<Pair<Label, Control>>) =
                    ScrollPane().also {
                        it.content =
                                GridPane().apply {
                                    controls.forEachIndexed { i, (label, cont) ->
                                        addRow(i, label, cont)
                                    }

                                    controls[0].second.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE)
                                    GridPane.setFillWidth(controls[0].second, true)

                                    controls.drop(1)
                                            .forEach {
                                                it.second.prefWidthProperty().bind(controls[0].second.widthProperty())
                                            }

                                    columnConstraints.add(ColumnConstraints().apply { percentWidth = 50.0 })
                                    columnConstraints.add(ColumnConstraints().apply { percentWidth = 50.0 })

                                    prefWidthProperty().bind(it.widthProperty().subtract(50))
                                    prefHeightProperty().bind(it.heightProperty())
                                }
                    }


            fun controlToProp(control : Control) =
                    when (control) {
                        is TextField -> { { control.textProperty().value } }
                        is ComboBox<*> -> { { control.valueProperty().value.toString() } }
                        is Spinner<*> -> { { control.valueProperty().value.toString() } }
                        else -> { { "" } }
                    }

            fun createTable(lib : String, name : String, previousValues: Map<String, String>) = run {
                MMGenericDevice(name, lib).use { dev ->
                    (listOf(Label("Label") to TextField(previousValues["Label"])) +
                            dev.getProperties()
                                    .filter { it.isPreinit() }
                                    .map(getPropertyControl(previousValues)))
                            .let {
                                Pair(createGrid(it), it.map { it.first.text to controlToProp(it.second) }.toMap() )
                            }
                }
            }
        }
    }

    private class DeviceDisplayCellFactory : TreeTableCell<DeviceDisplay, String>() {
        init {
            contextMenu = RightClickMenu(false, TreeItem(), TreeItem())
        }

        override fun updateItem(item: String?, empty: Boolean) {
            super.updateItem(item, empty)
            if (empty)
                text = null
            else {
                val node = treeTableRow?.treeItem
                val parent = treeTableRow?.treeItem?.parent
                val selectedNode = treeTableView.root.children.find { it.value.type.isEqualTo("Selected").value }!!
                contextMenuProperty().bind(Bindings
                        .`when`((node?.value?.name?.isNotEmpty ?: SimpleBooleanProperty(false))
                                .and(parent?.value?.type?.isNotEqualTo("Unavailable")
                                        ?: SimpleBooleanProperty(false)))
                        .then(RightClickMenu(parent?.value?.type?.isEqualTo("Selected")?.value ?: false,
                                node,
                                selectedNode))
                        .otherwise(null as RightClickMenu?))
                text = item
            }

        }
    }

    @FXML
    lateinit var treeTableView: TreeTableView<DeviceDisplay>

    @FXML
    lateinit var filenameField : TextField

    private fun columnMaker(title : String, toStrFun : (DeviceDisplay) -> String) =
            TreeTableColumn<DeviceDisplay, String>(title)
                    .apply {
                        setCellValueFactory { ReadOnlyStringWrapper(toStrFun(it.value.value)) }
                        setCellFactory { DeviceDisplayCellFactory() }
                    }

    private fun devDescToDisp(devDesc : MMDeviceDesc) =
            DeviceDisplay(DeviceType.swigToEnum(devDesc.type).toString(), devDesc.name, devDesc.description)

    fun initialize() {
        val (available, unavailable) = GUIMain.mmService.getLibraries()

        val typeColumn = columnMaker("Device Type") { it.type.value }
                .apply { maxWidth = Int.MAX_VALUE.toDouble() * 30.0 }
        val nameColumn = columnMaker("Device Name") { it.name.value }
                .apply { maxWidth = Int.MAX_VALUE.toDouble() * 30.0 }
        val descColumn = columnMaker("Device Description") { it.description.value }
                .apply { maxWidth = Int.MAX_VALUE.toDouble() * 40.0 }

        val selectedItems = TreeItem(DeviceDisplay("Selected", "", "")).apply { isExpanded = true }
        val availableItems = TreeItem(DeviceDisplay("Available", "", ""))
                .apply {
                    isExpanded = true
                    children.addAll(
                            *available
                                    .map {
                                        TreeItem(DeviceDisplay(it.name, "", ""))
                                                .apply {
                                                    children.addAll(it
                                                            .devices
                                                            .map { TreeItem(devDescToDisp(it)) }
                                                            .toTypedArray())
                                                }
                                    }.toTypedArray()) }

        treeTableView.columnResizePolicy = TreeTableView.CONSTRAINED_RESIZE_POLICY
        treeTableView.columns.addAll(typeColumn, nameColumn, descColumn)
        treeTableView.root = TreeItem(DeviceDisplay("Devices", "", ""))
                .apply {
                    isExpanded = true
                    children.addAll(
                            selectedItems,
                            availableItems,
                            TreeItem(DeviceDisplay("Unavailable", "", ""))
                                    .apply {
                                        children.addAll(
                                                *unavailable
                                                        .map { TreeItem(DeviceDisplay(it.name, "", "")) }
                                                        .toTypedArray()) }
                    )
                }
    }

    private fun addToSelected(
            deviceLabel: String,
            lib: String,
            dev: String,
            selectedNode: TreeItem<HardwareSelectorController.DeviceDisplay>,
            propertyMap: MutableMap<String, MutableMap<String, String>>): MutableMap<String, MutableMap<String, String>>? = run {
        val (libs, _) = GUIMain.mmService.getLibraries()
        (libs.find { it.name == lib }?.let {
            it.devices.find { it.name == dev } ?: run {
                GUIMain.loggerService.log(Level.WARNING, "$dev not found in $lib! Could not load device $dev!")
                null
            }
        } ?: run {
            GUIMain.loggerService.log(Level.WARNING, "$lib is not available! Could not load device $dev!")
            null
        })?.let { it ->
            selectedNode.children.add(
                    TreeItem(
                            HardwareSelectorController.DeviceDisplay(
                                    "${it.name} (${DeviceType.swigToEnum(it.type)})",
                                    deviceLabel,
                                    "${it.description} (From: $lib)")))

            propertyMap[deviceLabel] =
                    propertyMap[deviceLabel]?.apply { put("Label", deviceLabel) }
                    ?: mutableMapOf("Label" to deviceLabel)

            propertyMap
        }
    }

    private fun writeToFile(file : File) {
        val treeItems = treeTableView.root.children.find { it.value.type.value == "Selected" }!!.children
            val displays = treeItems.map { it.value }
            FileOutputStream(file, false).use {
                PrintWriter(it).use { out ->
                    out.println("# Generated by STRIMM Hardware Selector\n")
                    out.println("# Reset")
                    out.println("Property,Core,Initialize,0\n")
                    out.println("# Devices")

                    treeItems.forEach { disp ->
                        out.println("Device,${disp.value.name.value},${RightClickMenu.getDeviceLibrary(disp)},${RightClickMenu.getDeviceName(disp)}")
                    }

                    displays.forEach { disp ->
                        out.println("\n# Pre-init properties for ${disp.name.value}")
                        disp.preInitValues.forEach outputProperties@{ (prop, value) ->
                            if (prop == "Label") return@outputProperties
                            out.println("Property,${disp.name.value},$prop,$value")
                        }
                    }
                }
            }
            filenameField.text = file.absolutePath
    }

    fun loadFromFile(mouseEvent: MouseEvent) {
        val path = Paths.get(filenameField.text).toAbsolutePath()

        FileChooser().apply {
            initialFileName = FilenameUtils.getName(path.toString())
            initialDirectory = File(FilenameUtils.getFullPathNoEndSeparator(path.toString()))
            extensionFilters.add(FileChooser.ExtensionFilter("Config Files", "*.cfg"))
        }.run {
            showOpenDialog(null)
        }?.let { file ->
            val selectedNode = treeTableView.root.children.find { it.value.type.value == "Selected" }!!
            val propertyMap = mutableMapOf<String, MutableMap<String, String>>()

            selectedNode.children.clear()

            file.bufferedReader().lines().forEach {
                "^(.+),(.+),(.+),(.+)$".toRegex().find(it)?.destructured
                        ?.let { (entryType, deviceLabel, property, value) ->
                            if (entryType == "Property" && deviceLabel == "Core") return@forEach

                            when (entryType) {
                                "Device" -> addToSelected(deviceLabel, property, value, selectedNode, propertyMap)
                                "Property" ->  {
                                    propertyMap[deviceLabel]?.apply { put(property, value) }
                                            ?: run {
                                                GUIMain.loggerService.log(Level.WARNING, "Could not set property" +
                                                        " $property of $deviceLabel! Device" +
                                                        " not included in config file!")
                                                propertyMap
                                            }
                                }
                                "Label" -> propertyMap //TODO
                                else -> propertyMap
                            }
                        }
            }

            selectedNode.children.forEach {
                it.value.preInitValues = propertyMap[it.value.name.value] ?: emptyMap()
            }
        }
    }

    fun saveToFile(mouseEvent: MouseEvent) {
        val path = Paths.get(filenameField.text).toAbsolutePath()

        FileChooser().apply {
            initialFileName = FilenameUtils.getName(path.toString())
            initialDirectory = File(FilenameUtils.getFullPathNoEndSeparator(path.toString()))
            extensionFilters.add(FileChooser.ExtensionFilter("Config Files", "*.cfg"))
        }.run {
            showSaveDialog(null)
        }?.let { file ->
            writeToFile(file)
        }
    }

    fun initialiseMMConfig(mouseEvent: MouseEvent) {
        writeToFile(File("tmpSTRIMMConfig.cfg"))
        GUIMain.mmService.loadConfigurationFile("tmpSTRIMMConfig.cfg")
    }
}
