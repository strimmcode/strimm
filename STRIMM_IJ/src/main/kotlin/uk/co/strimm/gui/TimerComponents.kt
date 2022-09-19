package uk.co.strimm.gui

import javafx.beans.property.*
import javafx.beans.value.ObservableValue
import javafx.event.EventHandler
import javafx.fxml.FXML
import javafx.fxml.FXMLLoader
import javafx.scene.Parent
import javafx.scene.control.*
import javafx.scene.input.KeyCode
import javafx.scene.layout.*
import javafx.util.StringConverter
import javafx.util.converter.DoubleStringConverter
import javafx.util.converter.IntegerStringConverter
import javafx.util.converter.NumberStringConverter
import uk.co.strimm.*
import uk.co.strimm.services.STRIMM_IJ_Timer
import java.util.logging.Level
import kotlin.math.floor

class AnalogueInputSettings(private val timer: STRIMM_IJ_Timer? = null) : VBox()
{
    @FXML private lateinit var cmbxChannel : ComboBox<String>
    @FXML private lateinit var spnClockDiv : Spinner<Int>
    @FXML private lateinit var spnVMax : Spinner<Double>
    @FXML private lateinit var spnVMin : Spinner<Double>

    val channelProperty
        get() = cmbxChannel.valueProperty()
    var channel
        get() = channelProperty.get()
        set(value) = channelProperty.set(value)

    val clockDivProperty
        get() = spnClockDiv.valueProperty()
    val clockDiv
        get() = clockDivProperty.get()

    val voltageMaxProperty
        get() = spnVMax.valueProperty()
    val voltageMax
        get() = voltageMaxProperty.get()

    val voltageMinProperty
        get() = spnVMin.valueProperty()
    val voltageMin
        get() = voltageMinProperty.get()

    init {
        val loader = FXMLLoader(javaClass.getResource("/fxml/TimerConfig/AnalogueInputSettings.fxml"))
        loader.setRoot(this)
        loader.setController(this)

        loader.load<Parent>()
    }

    @FXML fun newChannel() {
        Alert(Alert.AlertType.INFORMATION, "New Analogue Input Channel!").showAndWait()
    }
}

class AnalogueOutputSettings(private val timer: STRIMM_IJ_Timer? = null) : VBox()
{
    @FXML private lateinit var cmbxChannel : ComboBox<String>
    @FXML private lateinit var txtCSVLoc : TextField
    @FXML private lateinit var settingsPane : GridPane

    val initialisedProperty = SimpleBooleanProperty(false)

    val channelProperty
        get() = cmbxChannel.valueProperty()
    var channel
        get() = channelProperty.get()
        set(value) = channelProperty.set(value)

    val CSVLocProperty
        get() = txtCSVLoc.textProperty()
    var CSVLoc
        get() = CSVLocProperty.get()
        set(value) = CSVLocProperty.set(value)

    init {
        val loader = FXMLLoader(javaClass.getResource("/fxml/TimerConfig/AnalogueOutputSettings.fxml"))
        loader.setRoot(this)
        loader.setController(this)

        loader.load<Parent>()
    }

    fun initialize() {
        settingsPane.disableProperty().bind(initialisedProperty.not())
    }

    @FXML fun newChannel() {
        Alert(Alert.AlertType.INFORMATION, "New Analogue Output Channel!").showAndWait()
    }
}

class DigitalOutputSettings(private val timer: STRIMM_IJ_Timer? = null) : VBox()
{
    @FXML private lateinit var cmbxChannel : ComboBox<String>
    @FXML private lateinit var txtCSVLoc : TextField

    val channelProperty
        get() = cmbxChannel.valueProperty()
    var channel
        get() = channelProperty.get()
        set(value) = channelProperty.set(value)

    val CSVLocProperty
        get() = txtCSVLoc.textProperty()
    var CSVLoc
        get() = CSVLocProperty.get()
        set(value) = CSVLocProperty.set(value)

    init {
        val loader = FXMLLoader(javaClass.getResource("/fxml/TimerConfig/DigitalOutputSettings.fxml"))
        loader.setRoot(this)
        loader.setController(this)

        loader.load<Parent>()
    }

    @FXML fun newChannel() {
        Alert(Alert.AlertType.INFORMATION, "New Digital Output Channel!").showAndWait()
    }
}


class DigitalInputSettings(private val timer: STRIMM_IJ_Timer? = null) : VBox()
{
    @FXML private lateinit var cmbxChannel : ComboBox<String>
    @FXML private lateinit var spnClockDiv : Spinner<Int>

    val channelProperty
        get() = cmbxChannel.valueProperty()
    var channel
        get() = channelProperty.get()
        set(value) = channelProperty.set(value)

    val clockDivProperty
        get() = spnClockDiv.valueProperty()
    val clockDiv
        get() = clockDivProperty.get()

    init {
        val loader = FXMLLoader(javaClass.getResource("/fxml/TimerConfig/DigitalInputSettings.fxml"))
        loader.setRoot(this)
        loader.setController(this)

        loader.load<Parent>()
    }

    @FXML fun newChannel() {
        Alert(Alert.AlertType.INFORMATION, "New Analogue Input Channel!").showAndWait()
    }
}

private typealias SVCL = (ObservableValue<out Number>, Number, Number) -> Unit

class SliderWithTextbox<T>(val max_val : T, val min_val : T, val initial : T, val onValueChanged : (T) -> Unit) : HBox()
    where T : Number,
          T :Comparable<T>
{
    @Suppress("Unchecked_Cast")
    private fun sliderValueAsT() : T = when(max_val) {
        is Int -> floor(slider.value).toInt() as T
        is Long -> floor(slider.value).toLong() as T
        is Short -> floor(slider.value).toShort() as T
        is Byte -> floor(slider.value).toByte() as T
        is Float -> slider.value.toFloat() as T
        is Double -> slider.value as T
        else -> throw IllegalArgumentException("Slider value must be either an integer or floating point type")
    }

    @Suppress("Unchecked_Cast")
    private fun textValueAsNullableT() : T? = when(max_val) {
        is Int -> textbox.text.toIntOrNull() as T?
        is Long -> textbox.text.toLongOrNull() as T?
        is Short -> textbox.text.toShortOrNull() as T?
        is Byte -> textbox.text.toByteOrNull() as T?
        is Float -> textbox.text.toFloatOrNull() as T?
        is Double -> textbox.text.toDoubleOrNull() as T?
        else -> throw IllegalArgumentException("Slider value must be either an integer or floating point type")
    }

    private val textbox = TextField().apply { text = initial.toString() }

    private val slider = Slider(min_val.toDouble(), max_val.toDouble(), initial.toDouble()).apply {
        val floorFirst : (SVCL) -> SVCL = { svcl ->
            { obv, ov, nv ->
                svcl(obv, ov, floor(nv as Double))
            }
        }

        val svcl : SVCL = { _, _, nv ->
            value = nv as Double
            textbox.text = sliderValueAsT().toString()
        }
        valueProperty().addListener(
                when (max_val) {
                    is Int -> floorFirst(svcl)
                    is Long -> floorFirst(svcl)
                    is Short -> floorFirst(svcl)
                    is Byte -> floorFirst(svcl)
                    else -> svcl
                })

        valueChangingProperty().addListener { _, _, nv ->
            if (!nv)
                onValueChanged(sliderValueAsT())
        }

        maxWidth = Double.MAX_VALUE

        HBox.setHgrow(this, Priority.ALWAYS)
    }

    init {
        textbox.focusedProperty().addListener { _, _, nv ->
            if (!nv) {
                val txtVal = textValueAsNullableT()
                when {
                    txtVal == null -> textbox.text = sliderValueAsT().toString()
                    txtVal > max_val -> {
                        textbox.text = max_val.toString()
                        slider.value = max_val.toDouble()
                        onValueChanged(sliderValueAsT())
                    }
                    txtVal < min_val -> {
                        textbox.text = min_val.toString()
                        slider.value = min_val.toDouble()
                        onValueChanged(sliderValueAsT())
                    }
                    else -> {
                        slider.value = txtVal.toDouble()
                        onValueChanged(sliderValueAsT())
                    }
                }
            }
        }


        textbox.onKeyPressed = EventHandler { ke ->
            if (ke.code == KeyCode.ENTER)
                this.requestFocus() //set focus to hbox
        }

        children.addAll( slider, textbox )
    }


    val valueProperty = slider.valueProperty()
    val valueChangingProperty = slider.valueChangingProperty()
}


class TimerPropertiesPane(private val timer: STRIMM_IJ_Timer? = null) : TabPane()
{
    @FXML lateinit var preInitTab : Tab
    @FXML lateinit var postInitTab : Tab

    private data class TimerPropertyDisp(val displayName : String, val property : TimerProperty)

    @FXML private lateinit var preinitTable : TableView<TimerPropertyDisp>
    @FXML private lateinit var postInitTable : TableView<TimerPropertyDisp>

    private val settableInitialisedProperty = SimpleBooleanProperty(timer?.timer?.isInitialised ?: false)
    val initialisedProperty : ReadOnlyBooleanProperty = SimpleBooleanProperty().apply { bind(settableInitialisedProperty) }


    init {
        val loader = FXMLLoader(javaClass.getResource("/fxml/TimerConfig/TimerPropertiesPane.fxml"))
        loader.setRoot(this)
        loader.setController(this)


        loader.load<Parent>()
    }

    private fun makeWidgetFromProperty(prop : TimerProperty) = when(prop) {
        is IntegerTimerProperty ->
            SliderWithTextbox(prop.max, prop.min, prop.value) { i -> prop.value = i }
        is DoubleTimerProperty ->
            SliderWithTextbox(prop.max, prop.min, prop.value) { i -> prop.value = i }
        is FloatTimerProperty ->
            SliderWithTextbox(prop.max, prop.min, prop.value) { i -> prop.value = i }
        is BooleanTimerProperty ->
            ComboBox<String>().apply {
                items.addAll("True", "False")
                selectionModel.select(if (prop.value) 0 else 1)

                valueProperty().addListener { _, ov, nv ->
                    if (ov != nv)
                        prop.value = nv == "True"
                }
            }
        is StringTimerProperty -> {
            val allowedValues = prop.getAllowedValues()
            if (allowedValues.isEmpty())
                TextField(prop.value).apply {
                    focusedProperty().addListener { _, _, nv ->
                        if (!nv)
                            prop.value = text
                    }

                    onKeyPressed = EventHandler { ke ->
                        if (ke.code == KeyCode.ENTER)
                            this.parent.requestFocus() //set focus to hbox
                    }
                }
            else
                ComboBox<String>().apply {
                    items.addAll(allowedValues)
                    selectionModel.select(prop.value)

                    valueProperty().addListener { _, ov, nv ->
                        if (ov != nv)
                            prop.value = nv
                    }
                }
        }
    }.apply { maxWidth = Double.MAX_VALUE }

    fun initialize() {
        preInitTab.disableProperty().bind(initialisedProperty)
        postInitTab.disableProperty().bind(initialisedProperty.not())

        val preInitNameColumn = TableColumn<TimerPropertyDisp, String>("Property").apply {
            setCellValueFactory {
                SimpleStringProperty(it.value.displayName)
            }
            prefWidthProperty().bind(preinitTable.widthProperty().multiply(0.3))
        }
        val postInitNameColumn = TableColumn<TimerPropertyDisp, String>("Property").apply {
            setCellValueFactory {
                SimpleStringProperty(it.value.displayName)
            }
            prefWidthProperty().bind(postInitTable.widthProperty().multiply(0.3))
        }

        val preInitValueColumn = TableColumn<TimerPropertyDisp, Any>("Value").apply {
            setCellValueFactory {
                SimpleObjectProperty(makeWidgetFromProperty(it.value.property))
            }
            isSortable = false

            prefWidthProperty().bind(preinitTable.widthProperty().multiply(0.7))
        }

        val postInitValueColumn = TableColumn<TimerPropertyDisp, Any>("Value").apply {
            setCellValueFactory {
                SimpleObjectProperty(makeWidgetFromProperty(it.value.property))
            }
            isSortable = false

            prefWidthProperty().bind(postInitTable.widthProperty().multiply(0.7))
        }
        preinitTable.columns.addAll(preInitNameColumn, preInitValueColumn)
        postInitTable.columns.addAll(postInitNameColumn, postInitValueColumn)

        preinitTable.columnResizePolicy = TableView.CONSTRAINED_RESIZE_POLICY
        postInitTable.columnResizePolicy = TableView.CONSTRAINED_RESIZE_POLICY


        timer?.timer
                ?.getPropertyNames()
                ?.map { Pair(it, timer.timer.getProperty(it)) }
                ?.groupBy { it.second?.isPreInit() }
                ?.forEach {
                    when (it.key) {
                        null -> {}
                        true -> it.value.forEach { prop ->
                                preinitTable.items.add(TimerPropertyDisp(prop.first, prop.second!!))
                            }
                        false -> it.value.forEach { prop ->
                                postInitTable.items.add(TimerPropertyDisp(prop.first, prop.second!!))
                            }
                    }
                }/*?.also {
                    preInitTab.isDisable = timer.timer.isInitialised
                    postInitTab.isDisable = !timer.timer.isInitialised
                }*/
    }

    @FXML fun initTimer() {
        when(timer?.timer?.initialise()) {
            null -> {}
            TimerResult.Success -> {
                Alert(Alert.AlertType.INFORMATION, "${timer.displayName} initialised!").showAndWait()

                /*preInitTab.isDisable = timer.timer.isInitialised
                postInitTab.isDisable = !timer.timer.isInitialised*/
                settableInitialisedProperty.set(true)

                selectionModel.select(postInitTab)
            }
            TimerResult.Error ->
                Alert(Alert.AlertType.ERROR, "${timer.displayName} failed to initialise!\nError:${timer.timer.getLastError()}").showAndWait()
            TimerResult.Warning -> {
                Alert(Alert.AlertType.WARNING, "${timer.displayName} initialised with warning!\nWarning:${timer.timer.getLastError()}").showAndWait()

                /*preInitTab.isDisable = timer.timer.isInitialised
                postInitTab.isDisable = !timer.timer.isInitialised*/
                settableInitialisedProperty.set(true)

                selectionModel.select(postInitTab)
            }
        }
    }
}
