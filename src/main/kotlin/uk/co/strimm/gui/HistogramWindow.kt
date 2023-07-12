package uk.co.strimm.gui

import bibliothek.gui.dock.common.DefaultMultipleCDockable
import javafx.application.Platform
import javafx.collections.FXCollections.observableArrayList
import javafx.embed.swing.JFXPanel
import javafx.event.ActionEvent
import javafx.event.EventHandler
import javafx.fxml.FXML
import javafx.fxml.FXMLLoader
import javafx.scene.Scene
import javafx.scene.chart.BarChart
import javafx.scene.chart.CategoryAxis
import javafx.scene.chart.NumberAxis
import javafx.scene.chart.XYChart
import javafx.scene.control.CheckBox
import javafx.scene.control.ScrollPane
import javafx.scene.control.TextField
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import javafx.scene.text.Text
import org.scijava.plugin.Plugin
import uk.co.strimm.STRIMMBuffer
import uk.co.strimm.STRIMMSignalBuffer
import uk.co.strimm.actors.messages.tell.TellAutoStretch
import uk.co.strimm.plugins.AbstractDockableWindow
import uk.co.strimm.plugins.DockableWindowPlugin
import java.util.*
import java.util.logging.Level


@Plugin(type = DockableWindowPlugin::class, menuPath = "Window>Histogram")
class HistogramWindowPlugin : AbstractDockableWindow() {
    override var title = GUIMain.utilsService.createDockableWindowTitleText("", false)
    lateinit var histogramWindowController: HistogramWindow

    override fun setCustomData(data: Any?) {
        histogramWindowController.properties = data as HashMap<String, String>
    }

    override var dockableWindowMultiple: DefaultMultipleCDockable = run {
        this.createDock(title).apply {
            val fxPanel = JFXPanel()
            add(fxPanel)

            try {
                val URL = this.javaClass.getResource("/fxml/HistogramWindow.fxml")
                val loader = FXMLLoader(URL)
                val controller = HistogramWindow()
                histogramWindowController = controller
                loader.setController(controller)
                val pane = loader.load() as VBox
                val scene = Scene(pane)
                Platform.runLater {
                    fxPanel.scene = scene
                }
                dockableWindowMultiple = this

            }
            catch (ex: Exception) {
                GUIMain.loggerService.log(Level.SEVERE, "Error loading HistogramWindow.fxml file. Message: ${ex.message}")
                GUIMain.loggerService.log(Level.SEVERE, ex.stackTrace)
            }
        }
    }
}

class HistogramWindow {
    lateinit var properties: HashMap<String, String>

    @FXML
    lateinit var scrollPaneVBox : VBox

    var scrollPane = ScrollPane()
    var histogramPane = VBox() //Each histogram will be added to this
    var histograms = hashMapOf<String, BarChart<String, Number>>()

    //The incoming data will already be binned, but we will further bin the data so it presents better
    var numPresentatonBins = 75

    val autoStretchInitialVal = 0.2

    //Record of the current min max of each image as the data comes in. Updated each time updateExistingHistogram() is called
    var currentMinMax = hashMapOf<String, Pair<Number, Number>>()
    var autoStretchComponents = arrayListOf<Pair<CheckBox, TextField>>()
    val imageFeedProperty = "imageFeed"

    @FXML
    fun initialize() {
        GUIMain.loggerService.log(Level.INFO, "Initialising histogram window")
        scrollPaneVBox.children.add(scrollPane)
        scrollPane.content = histogramPane
    }

    /**
     * This method is called by the HistogramActor and initially receives the data from the HistogramFlow
     * @param data The histogram data as List<STRIMMSignalBuffer>
     */
    fun updateChart(data: List<STRIMMBuffer>){
        for(buffer in data){
            val signalBuffer = buffer as STRIMMSignalBuffer
            val isNewChart = isNewChart(signalBuffer)
            if(isNewChart){
                createNewHistogram(signalBuffer)
            }
            else{
                updateExistingHistogram(signalBuffer)
            }
        }
    }

    /**
     * If a histogram already exists for the camera/flow then clear it's existing data and replace with new histogram
     * data
     * @param buffer The buffer containing the new histogram data
     */
    fun updateExistingHistogram(buffer: STRIMMSignalBuffer){
        Platform.runLater {
            for (histogram in histograms) {
                if (histogram.key in buffer.channelNames!!) {
                    val series = XYChart.Series<String, Number>()
                    var maxVal = 0.0
                    for (i in 0 until buffer.data!!.size) {
                        val index = i.toString()
                        val count = buffer.data!![i]
                        if(count > maxVal){
                            maxVal = count
                        }
                        series.data.add(XYChart.Data(index, count))
                    }

                    currentMinMax[histogram.key] = buffer.pixelMinMax
                    (histogram.value.yAxis as NumberAxis).upperBound = maxVal
                    histogram.value.data.clear()
                    histogram.value.data.add(series)

                    for(componentPair in autoStretchComponents){
                        val chk = componentPair.first
                        val imageFeedName = chk.properties[imageFeedProperty].toString()
                        if(imageFeedName == histogram.key){
                            //Keep performing autostretch if chk is selected and histogram is updated
                            tellActorAutoStretch(chk.isSelected, imageFeedName)
                        }
                    }
                }
            }
        }
    }

    /**
     * If no histogram already exists for the camera/flow then create one and associated components
     * @param buffer The first buffer containing histogram data
     */
    fun createNewHistogram(buffer : STRIMMSignalBuffer){
        GUIMain.loggerService.log(Level.INFO, "Creating histogram for ${buffer.channelNames!!.first()} image feed")

        val range = 0..255 step 255/numPresentatonBins
        val xAxis = CategoryAxis()
        xAxis.categories = observableArrayList(range.map{ x -> x.toString()}.toList())
        val yAxis = NumberAxis()
        val histogram = BarChart(xAxis, yAxis)

        val imageFeedName = buffer.channelNames.first()
        histograms[imageFeedName] = histogram

        yAxis.isTickLabelsVisible = true
        yAxis.isMinorTickVisible = false
        yAxis.isAutoRanging = false
        yAxis.tickUnit = 0.2

        xAxis.isAutoRanging = false
        xAxis.isTickLabelsVisible = false
        xAxis.isTickMarkVisible = false

        histogram.isLegendVisible = false
        histogram.animated = false
        histogram.prefHeight = 50.0
        histogram.maxHeight = 50.0
        histogram.prefWidth = 400.0
        histogram.title = buffer.channelNames.first()
        histogram.barGap = 0.0
        histogram.categoryGap = 0.0

        val series = XYChart.Series<String, Number>()
        var initialMax = 0.0
        for(i in 0 until buffer.data!!.size){
            val index = i.toString()
            val datum = buffer.data!![i]
            if(datum > initialMax){
                initialMax = datum
            }

            series.data.add(XYChart.Data(index, datum))
        }

        currentMinMax[imageFeedName] = buffer.pixelMinMax
        histogram.data.add(series)
        yAxis.upperBound = initialMax
        yAxis.lowerBound = 0.0

        Platform.runLater {
            val chk = CheckBox("Autostretch")
            //Property that ties the checkbox to the correct histogram
            chk.properties[imageFeedProperty] = imageFeedName
            addAutoStretchChkListener(chk)

            val txtIgnore = Text("  Ignore")
            val txtField = TextField(autoStretchInitialVal.toString())
            //Property that ties the textField to the correct histogram
            txtField.properties[imageFeedProperty] = imageFeedName
            txtField.prefWidth = 40.0
            txtField.isDisable = true
            addTextFieldChangeListener(txtField)
            addTextFieldFocusListener(txtField)

            val txtPct = Text("%")
            val autoStretchHBox = HBox()
            autoStretchHBox.children.add(chk)
            autoStretchHBox.children.add(txtIgnore)
            autoStretchHBox.children.add(txtField)
            autoStretchHBox.children.add(txtPct)

            //Add to a global list so it can be used in autostretching code
            autoStretchComponents.add(Pair(chk, txtField))

            histogramPane.children.add(autoStretchHBox)
            histogramPane.children.add(histogram)
        }
    }

    /**
     * Add a listener to each autostretch checkbox. If checked, perform autostretch based on corresponding text field.
     * If not checked, do not perform autostretch
     * @param checkBox The checkbox to add the listener to
     */
    fun addAutoStretchChkListener(checkBox: CheckBox){
        val handler : EventHandler<ActionEvent> = EventHandler<ActionEvent> { event ->
            if (event.source is CheckBox) {
                val chk = event.source as CheckBox
                val imageFeedName = chk.properties[imageFeedProperty].toString()
                tellActorAutoStretch(chk.isSelected, imageFeedName)

                try {
                    val txtField = autoStretchComponents.map { x -> x.second }
                        .first { y -> y.properties[imageFeedProperty].toString() == imageFeedName }
                    txtField.isDisable = !chk.isSelected
                }
                catch(ex : Exception){
                    GUIMain.loggerService.log(Level.SEVERE, "Could not find text field for checkbox for image feed $imageFeedName. Message: ${ex.message}")
                    GUIMain.loggerService.log(Level.SEVERE, ex.stackTrace)
                }
            }
        }

        checkBox.onAction = handler
    }

    /**
     * Using the imageFeedName, find the relevant camera actor, call method to calculate new min and max, and then
     * tell the camera actor to set the new range.
     * @param isSelected Flag representing if the corresponding autostretch checkbox has been checked
     * @param imageFeedName The name of the associated image feed
     */
    fun tellActorAutoStretch(isSelected : Boolean, imageFeedName : String){
        //Although this method will return a list, it should only contain one entry
        val displaySink = GUIMain.experimentService.getDownstreamDisplaySinksForImageFeed(imageFeedName)
        if(displaySink.isNotEmpty()) {
            val displayActor = GUIMain.actorService.getActorByName(displaySink.first())
            if (displayActor != null) {
                if (isSelected) {
                    try {
                        val txtField = autoStretchComponents.map { x -> x.second }
                            .first { y -> y.properties[imageFeedProperty].toString() == imageFeedName }
                        val minMax = calculateCutoffMinMax(imageFeedName, txtField.text.toDouble())
                        val message = TellAutoStretch(true, Pair(minMax.first, minMax.second))
                        displayActor.tell(message, null)
                    }
                    catch (ex: Exception) {
                        //It's unlikely this exception will occur as the imageFeedProperty on each text field is tightly
                        //coupled to each image feed
                    }
                } else {
                    //Pair can have any values as they won't be used if doAutoStretch=false
                    val message = TellAutoStretch(false, Pair(0.0, 0.0))
                    displayActor.tell(message, null)
                }
            }
        }
    }

    /**
     * Add a listener to parse and enact autostretching each time the text field's text changes. If the parse fails
     * an exception is thrown and caught. If the parse succeeds then call method to get the relevant camera actor
     * to do an autostretch
     * @param textField The autostretch percentage text field
     */
    fun addTextFieldChangeListener(textField: TextField){
        textField.textProperty().addListener { observable, oldValue, newValue ->
            try{
                newValue.toDouble() //Will throw an exception if not in parseable double format
                for(componentPair in autoStretchComponents){
                    val chk = componentPair.first
                    val imageFeedName = chk.properties[imageFeedProperty].toString()
                    if(imageFeedName == textField.properties[imageFeedProperty]){
                        tellActorAutoStretch(chk.isSelected, imageFeedName)
                    }
                }
            }
            catch(ex : Exception){
                //Logging anything here may fill up print statements too much so left blank
            }
        }
    }

    /**
     * Add a listener for when focus is changed on the autostretch percentage text field. This listener will add
     * some validation by attempting to parse the text field value to a double when focus is lost. If the parse is
     * unsuccessful an exception is thrown and caught and it defaults to an initial value
     * @param textField The autostretch percentage text field
     */
    fun addTextFieldFocusListener(textField : TextField){
        textField.focusedProperty().addListener { observable, oldVal, focused ->
            //Check the value when losing focus. If it's not a double, force it to be a double
            if(!focused){
                try{
                    textField.text.toDouble() //Will throw an exception if not in parseable double format
                }
                catch(ex : Exception){
                    textField.text = autoStretchInitialVal.toString()
                }
            }
        }
    }

    /**
     * Calculates the new min and max for pixel values for normalising (autostretching) an image feed
     * @param histogramName The name of the histogram
     * @param cutoffPercentage A percentage of the min/max pixel intensity to cut off
     * @return A pair containing min max values as Pair(min, max)
     */
    fun calculateCutoffMinMax(histogramName: String, cutoffPercentage: Double): Pair<Double, Double>{
        for(histogramEntry in histograms){
            if(histogramEntry.key == histogramName){
                if(currentMinMax[histogramName] != null) {
                    val currentMin = (currentMinMax[histogramName]!!.first).toDouble()
                    val currentMax = currentMinMax[histogramName]!!.second.toDouble()
                    val newMin = currentMin+(currentMin*(cutoffPercentage/100))
                    var newMax = currentMax-(currentMax*(cutoffPercentage/100))

                    //Guard in case newMax is somehow lower than newMin (unlikely to happen)
                    if(newMax <= newMin){
                        newMax = newMin+1
                    }

                    return Pair(newMin, newMax)
                }
            }
        }

        return Pair(-999.0,-999.0)
    }

    /**
     * Check if there is a histogram chart for the incoming data based on channel name (which is actually the
     * cameraLabel property or flow name, @see HistogramFlow).
     * @param buffer The STRIMMSignalBuffer object representing the incoming data
     * @return boolean which is true if there is no existing histogram chart, false if there is an existing histogram
     * chart
     */
    fun isNewChart(buffer : STRIMMSignalBuffer): Boolean{
        for(channelName in buffer.channelNames!!){
            if(channelName in histograms.keys){
                return false
            }
        }

        return true
    }
}