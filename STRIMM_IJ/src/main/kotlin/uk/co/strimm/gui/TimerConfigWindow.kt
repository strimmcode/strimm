package uk.co.strimm.gui

import bibliothek.gui.dock.common.DefaultMultipleCDockable
import bibliothek.gui.dock.common.DefaultSingleCDockable
import javafx.application.Platform
import javafx.beans.property.SimpleObjectProperty
import javafx.beans.value.ChangeListener
import javafx.beans.value.ObservableValue
import javafx.collections.FXCollections
import javafx.embed.swing.JFXPanel
import javafx.fxml.FXML
import javafx.fxml.FXMLLoader
import javafx.scene.Scene
import javafx.scene.control.*
import javafx.scene.layout.*
import javafx.util.Callback
import javafx.util.StringConverter
import org.scijava.plugin.Plugin
import uk.co.strimm.TimerName
import uk.co.strimm.plugins.AbstractDockableWindow
import uk.co.strimm.plugins.DockableWindowPlugin
import uk.co.strimm.services.STRIMM_IJ_Timer

@Plugin(type = DockableWindowPlugin::class, menuPath = "Window>Timer Configuration")
class TimerConfigWindow : AbstractDockableWindow() {
    override var title = "Timer Configuration Window"

    override var dockableWindowMultiple = kotlin.run{
        this.createDock(title).apply {
            val fxPanel = JFXPanel()
            add(fxPanel)
            Platform.runLater {
                fxPanel.scene =
                        Scene(FXMLLoader(this.javaClass.getResource("/fxml/TimerConfig/TimerConfig.fxml")).load(),
                                100.0, 100.0)
            }
        }
    }
}

class TimerConfigController
{
    @FXML lateinit var cmbxTimer : ComboBox<STRIMM_IJ_Timer>
    @FXML lateinit var timerPropertiesParent : VBox

    @FXML lateinit var aoTab : Tab
    @FXML lateinit var aiTab : Tab
    @FXML lateinit var doTab : Tab
    @FXML lateinit var diTab : Tab

    @FXML val timers = FXCollections.observableArrayList<STRIMM_IJ_Timer>()
    private val currentTimer = SimpleObjectProperty<STRIMM_IJ_Timer>()

    private data class TimerUIComponents(val propertiesPane: TimerPropertiesPane,
                                         val analogueInputSettings: AnalogueInputSettings,
                                         val analogueOutputSettings: AnalogueOutputSettings,
                                         val digitalInputSettings: DigitalInputSettings,
                                         val digitalOutputSettings: DigitalOutputSettings)
    {
        init {
            analogueOutputSettings.initialisedProperty.bind(propertiesPane.initialisedProperty)
        }
    }


    private val timerUIMap = mutableMapOf<STRIMM_IJ_Timer, TimerUIComponents>()

    private data class NewTimerDesc(val timerName : TimerName, val label : String)

    fun initialize() {   // TW 2/7/21
//        cmbxTimer.converter = object : StringConverter<STRIMM_IJ_Timer>() {
//            override fun toString(timer : STRIMM_IJ_Timer?): String = timer?.displayName ?: ""
//            override fun fromString(string: String?): STRIMM_IJ_Timer? = null
//        }
//        cmbxTimer.valueProperty().bindBidirectional(currentTimer)
//        currentTimer.addListener { _, _, n -> updateUIForTimer(n) }
    }

    fun updateUIForTimer(timer : STRIMM_IJ_Timer?) {  // 2/7/21   TW
//        if (timer != null) {
//            timerPropertiesParent.children[0] = timerUIMap[timer]?.propertiesPane
//            aiTab.content = timerUIMap[timer]?.analogueInputSettings
//            aoTab.content = timerUIMap[timer]?.analogueOutputSettings
//            doTab.content = timerUIMap[timer]?.digitalOutputSettings
//            diTab.content = timerUIMap[timer]?.digitalInputSettings
//        } else {
//            timerPropertiesParent.children[0] = TimerPropertiesPane()
//            aiTab.content = AnalogueInputSettings()
//            aoTab.content = AnalogueOutputSettings()
//            doTab.content = DigitalOutputSettings()
//            diTab.content = DigitalInputSettings()
//        }
//
//        Alert(Alert.AlertType.INFORMATION, "Changed to ${timer?.displayName}").showAndWait()
    }

    private fun getDialogueGrid() = kotlin.run {
//        val txtTimerLabel = TextField()
//        val cmbxTimerName = ComboBox<TimerName>().apply {
//            items.addAll(*GUIMain.timerService.getAvailableTimers()!!.toTypedArray())
//            converter = object : StringConverter<TimerName>() {
//                override fun toString(name : TimerName?): String? = name?.name
//                override fun fromString(string: String?): TimerName? = null
//            }
//            minWidth = Region.USE_PREF_SIZE
//        }
//        val grid = GridPane().apply {
//            add(Label("Timer Type:").apply { minWidth = Region.USE_PREF_SIZE }, 0, 0)
//            add(cmbxTimerName, 1, 0)
//            add(Label("Timer Label:").apply { minWidth = Region.USE_PREF_SIZE }, 0, 1)
//            add(txtTimerLabel, 1, 1)
//            hgap = 10.0
//            vgap = 10.0
//        }
//
//        val converter = Callback { bt : ButtonType? ->
//            if (bt == ButtonType.OK && cmbxTimerName.value != null && !txtTimerLabel.text.isNullOrBlank())
//                NewTimerDesc(cmbxTimerName.value, txtTimerLabel.text)
//            else
//                null
//        }
//
//        Pair(grid, converter)
    }

    @FXML fun newTimer() {
        Dialog<NewTimerDesc>()
//                .apply {
//                    val (grid, converter) = getDialogueGrid()
//                    dialogPane.content = grid
//                    grid.prefWidthProperty().bind(widthProperty())
//                    grid.prefHeightProperty().bind(heightProperty())
//                    dialogPane.buttonTypes.add(ButtonType.OK)
//                    isResizable = true
//                    resultConverter = converter
//                    dialogPane.prefWidthProperty().bind(grid.prefWidthProperty())
//                    dialogPane.prefHeightProperty().bind(grid.prefHeightProperty())
//                }
                .showAndWait()
//                .ifPresent {
//                    GUIMain.timerService.createTimer(it.timerName, it.label)?.let { tmr ->
//                        timers.add(tmr)
//                        timerUIMap[tmr] = TimerUIComponents(TimerPropertiesPane(tmr),
//                                AnalogueInputSettings(tmr),
//                                AnalogueOutputSettings(tmr),
//                                DigitalInputSettings(tmr),
//                                DigitalOutputSettings(tmr))
//                        currentTimer.set(tmr)
//                    } ?: kotlin.run {
//                        Alert(Alert.AlertType.ERROR, "Failed to create timer!\nSee console for details")
//                    }
//                }
    }

    @FXML fun deleteTimer() =
        currentTimer.value?.also {
//            GUIMain.timerService.deleteTimer(it)
//            timers.remove(it)
//            timerUIMap.remove(it)
//            if (timers.size == 0) currentTimer.value = null
//            else currentTimer.value = timers[0]
        }
}

