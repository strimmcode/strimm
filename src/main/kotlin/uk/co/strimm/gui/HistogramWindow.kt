package uk.co.strimm.gui

import bibliothek.gui.dock.common.DefaultMultipleCDockable
import javafx.application.Platform
import javafx.beans.Observable
import javafx.collections.FXCollections.observableArrayList
import javafx.collections.ObservableList
import javafx.embed.swing.JFXPanel
import javafx.event.ActionEvent
import javafx.event.EventHandler
import javafx.fxml.FXML
import javafx.fxml.FXMLLoader
import javafx.scene.Scene
import javafx.scene.chart.*
import javafx.scene.control.CheckBox
import javafx.scene.control.ScrollPane
import javafx.scene.control.TextField
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import javafx.scene.shape.Line
import javafx.scene.text.Text
import org.scijava.plugin.Plugin
import uk.co.strimm.STRIMMBuffer
import uk.co.strimm.STRIMMSignalBuffer
import uk.co.strimm.actors.messages.tell.TellAutoStretch
import uk.co.strimm.plugins.AbstractDockableWindow
import uk.co.strimm.plugins.DockableWindowPlugin
import java.util.logging.Level
import kotlin.math.abs
import kotlin.math.pow

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

/**
 * Class representing the CustomBarChart histogram. Made custom mostly for vertical markers on histograms
 * @param xAxis The x axis which is a category axis
 * @param yAxis The y axis
 */
class CustomBarChart(xAxis : CategoryAxis, yAxis : NumberAxis) : BarChart<String, Number>(xAxis, yAxis) {
    val markers = observableArrayList<Data<Double, Double>>()

    init{
        markers.addListener { observable: Observable? ->
            layoutPlotChildren()
        }
    }
    fun addVerticalMarker(xPos : Double){
        val line = Line()
        val dataToAdd = Data(xPos, 0.0)
        dataToAdd.node = line
        plotChildren.add(line)
        markers.add(dataToAdd)
    }

    fun removeAllVerticalMarkers(){
        markers.forEach { x ->
            plotChildren.remove(x.node)
            x.node = null
        }
        markers.clear()
        layoutPlotChildren()
    }

    override fun layoutPlotChildren(){
        Platform.runLater {
            super.layoutPlotChildren()
            for (marker in markers) {
                val line = marker.node as Line
                line.startX = marker.xValue
                line.endX = line.startX
                line.startY = 0.0
                line.endY = boundsInLocal.height
                line.toFront()
            }

            /**
             * For some reason the x axis (category axis) tick marks don't hide/show as expected. Documentation
             * on this is sparse. This will do for now
             */
            val tickMarks: ObservableList<Axis.TickMark<String>> = (xAxis as CategoryAxis).tickMarks
            for (i in 0 until tickMarks.size) {
                tickMarks[i].isTextVisible = tickMarks[i].value.toInt().mod(5) == 0
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
    var histograms = hashMapOf<String, CustomBarChart>()

    val binMin = 1.0
    val binMax = 256.0

    val autoStretchInitialVal = 0.2

    //Record of the current min max of each image as the data comes in. Updated each time updateExistingHistogram() is called
    var currentMinMax = hashMapOf<String, Pair<Number, Number>>()
    var autoStretchComponents = arrayListOf<Pair<CheckBox, AutoStretchUpdateComponents>>()
    val imageFeedProperty = "imageFeed"

    data class AutoStretchUpdateComponents(val pctTxtField: TextField, val txtMin : Text, val txtMax : Text, val txtAvg : Text)

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
                    var maxYVal = 0.0
                    for (i in 0 until buffer.data!!.size) {
                        val index = i.toString()
                        val count = buffer.data!![i]
                        if(count > maxYVal){
                            maxYVal = count
                        }
                        series.data.add(XYChart.Data(index, count))
                    }

                    currentMinMax[histogram.key] = buffer.pixelMinMax
                    (histogram.value.yAxis as NumberAxis).upperBound = maxYVal
                    histogram.value.data.clear()
                    histogram.value.data.add(series)

                    for(componentPair in autoStretchComponents){
                        val chk = componentPair.first
                        val imageFeedName = chk.properties[imageFeedProperty].toString()
                        if(imageFeedName == histogram.key){
                            //Keep performing autostretch if chk is selected and histogram is updated
                            tellActorAutoStretch(chk.isSelected, imageFeedName)

                            val minText = "  Min: " + currentMinMax[histogram.key]!!.first.toInt().toString() + " "
                            componentPair.second.txtMin.text = minText
                            val maxText = " Max: " + currentMinMax[histogram.key]!!.second.toInt().toString() + " "
                            componentPair.second.txtMax.text = maxText
                            val avgText = " Avg: " + buffer.avgPixelIntensity.toInt().toString()
                            componentPair.second.txtAvg.text = avgText

                            /**
                             * If the relevant autostretch checkbox is checked, currentMinMax will have been updated
                             * in the tellActorAutoStretch() method. So we can update markers here
                             */
                            if(chk.isSelected){
                                histogram.value.removeAllVerticalMarkers()
                                val lowerMarkerX = getMarkerX(currentMinMax[histogram.key]!!.first.toDouble(), buffer.imagePixelType)
                                val upperMarkerX = getMarkerX(currentMinMax[histogram.key]!!.second.toDouble(), buffer.imagePixelType)
                                histogram.value.addVerticalMarker(lowerMarkerX)
                                histogram.value.addVerticalMarker(upperMarkerX)
                            }
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

        val range = binMin.toInt()..binMax.toInt() step 1
        val xAxis = CategoryAxis()
        xAxis.categories = observableArrayList(range.map{ x -> x.toString()}.toList())

        val yAxis = NumberAxis()
        val histogram = CustomBarChart(xAxis, yAxis)

        val imageFeedName = buffer.channelNames.first()
        histograms[imageFeedName] = histogram

        yAxis.isTickLabelsVisible = true
        yAxis.isMinorTickVisible = false
        yAxis.isAutoRanging = false
        yAxis.tickUnit = 0.2

        xAxis.isAutoRanging = false
        xAxis.isTickLabelsVisible = true
        xAxis.isTickMarkVisible = false
        xAxis.label = "Bins"

        histogram.isLegendVisible = false
        histogram.animated = false //Slows down massively if set to true
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
            addAutoStretchComponents(imageFeedName)
            histogramPane.children.add(histogram)
        }
    }

    /**
     * Add UI components relating to autostretch feature
     * @param imageFeedName The name of the image feed (and therefore histogram)
     */
    private fun addAutoStretchComponents(imageFeedName : String){
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

        val txtMin = Text("     Min: ")
        val txtMax = Text(" Max: ")
        val txtAvg = Text(" Avg: ")
        autoStretchHBox.children.add(txtMin)
        autoStretchHBox.children.add(txtMax)
        autoStretchHBox.children.add(txtAvg)

        //Add to a global list so it can be used in autostretching code
        val components = AutoStretchUpdateComponents(txtField, txtMin, txtMax, txtAvg)
        autoStretchComponents.add(Pair(chk, components))
        histogramPane.children.add(autoStretchHBox)
    }

    /**
     * This method will map a pixel value to the range on bins 1 to 256 that is used for the vertical markers on
     * histograms
     * @param pixelValue The pixel value that is the basis for the marker position
     * @param pixelType The pixel type as specified in the STRIMMPixelBuffer class
     * @see STRIMMPixelBuffer
     * @return The X position of the marker on the histogram
     */
    fun getMarkerX(pixelValue : Double, pixelType : Int) : Double{
        var xVal = 0.0
        when(pixelType){
            0 ->{
                val unsignedValue = (pixelValue+Byte.MAX_VALUE)
                xVal = if(unsignedValue <= 1.0){
                    binMin
                }
                else{
                    unsignedValue
                }
            }
            1 -> {
                val unsignedValue = (pixelValue+Short.MAX_VALUE)
                val binValue = unsignedValue/256
                xVal = if(binValue <= 1.0){
                    binMin
                }
                else{
                    binValue
                }
            }
            4 -> {
                val unsignedValue = (pixelValue+Float.MAX_VALUE)
                val binValue = unsignedValue/(2.toDouble().pow(24))
                xVal = if(binValue <= 1.0){
                    binMin
                }
                else{
                    binValue
                }
            }
        }
        return xVal
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
                    val txtField = autoStretchComponents.map { x -> x.second.pctTxtField }
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
                        val txtField = autoStretchComponents.map { x -> x.second.pctTxtField }
                            .first { y -> y.properties[imageFeedProperty].toString() == imageFeedName }
                        val minMax = calculateCutoffMinMax(imageFeedName, txtField.text.toDouble())
                        val message = TellAutoStretch(true, Pair(minMax.first, minMax.second))
                        displayActor.tell(message, null)

                        //Update the current minMax so it can be used elsewhere
                        currentMinMax[imageFeedName] = minMax
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
                    var newMin = currentMin
                    var newMax = currentMax

                    if(cutoffPercentage > 0.0){
                        newMin = currentMin+(abs(currentMin)*(cutoffPercentage/100))
                        newMax = currentMax-(abs(currentMax)*(cutoffPercentage/100))
                    }

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