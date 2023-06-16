package uk.co.strimm.gui

import bibliothek.gui.dock.common.DefaultMultipleCDockable
import javafx.application.Platform
import javafx.collections.ObservableList
import javafx.embed.swing.JFXPanel
import javafx.fxml.FXML
import javafx.fxml.FXMLLoader
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.chart.*
import javafx.scene.control.*
import javafx.scene.image.ImageView
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.layout.Pane
import javafx.scene.layout.VBox
import javafx.scene.text.Text
import org.scijava.plugin.Plugin
import uk.co.strimm.plugins.AbstractDockableWindow
import uk.co.strimm.plugins.DockableWindowPlugin
import java.awt.Panel
import java.math.RoundingMode
import java.text.DecimalFormat
import java.util.logging.Level
import javax.swing.JDialog
import javax.swing.JOptionPane
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.round

@Plugin(type = DockableWindowPlugin::class, menuPath = "Window>Trace Scroll Feed")
class TraceScrollWindowPlugin : AbstractDockableWindow() {
    override var title = GUIMain.utilsService.createDockableWindowTitleText("", false)
    lateinit var traceWindowController: TraceScrollWindow

    override fun setCustomData(data: Any?) {
        GUIMain.loggerService.log(Level.INFO, "Setting custom data in TraceScrollWindow")
        traceWindowController.data = data as HashMap<String, FloatArray>//<trace name, trace data>
    }

    override var dockableWindowMultiple: DefaultMultipleCDockable = run {
        this.createDock(title).apply {
            val fxPanel = JFXPanel()
            add(fxPanel)

            try {
                val URL = this.javaClass.getResource("/fxml/TraceScrollChart.fxml")
                val loader = FXMLLoader(URL)
                GUIMain.loggerService.log(Level.INFO, "Loading TraceScrollChart.fxml" + loader.location.path)

                val controller = TraceScrollWindow()
                traceWindowController = controller
                loader.setController(controller)
                val pane = loader.load() as VBox
                val scene = Scene(pane)
                Platform.runLater {
                    fxPanel.scene = scene
                }
                dockableWindowMultiple = this

            } catch (ex: Exception) {
                GUIMain.loggerService.log(Level.SEVERE, "Error loading TraceScrollWindow. Message: ${ex.message}")
                GUIMain.loggerService.log(Level.SEVERE, ex.stackTrace)
            }
        }
    }
}

/**
 * The TraceScrollWindow is used exclusively when reloading an already acquired experiment. Data from the H5 file
 * will be sent to the controller that interacts with this class.
 */
class TraceScrollWindow{
    lateinit var data : HashMap<String, FloatArray>

    @FXML
    lateinit var tracePane: VBox

    var borderPane = BorderPane()

    var xAxis = NumberAxis()
    var yAxis = NumberAxis()
    var lineChart = LineChart<Number, Number>(xAxis, yAxis)

    val markerXAxis = NumberAxis()
    var catYAxis = CategoryAxis()
    var markerChart = ScatterChart<Number, String>(markerXAxis, catYAxis)

    var shiftXAmount = 500.0 //When a shift button is clicked, how much will it shift by (unit is the time unit). This is the initial value and will change if the x axis range is changed
    var shiftYAmount = 500.0
    var xTickUnit = 100.0
    var yTickUnit = 100.0

    //Change factor is how much as a percent axes ranges should increase or decrease.
    var changeFactor = 0.25
    var times = floatArrayOf()

    val maxInitialDataPoints = 1000

    var zoomOutYButton = Button()
    var zoomInYButton = Button()
    var zoomInXButton = Button()
    var zoomOutXButton = Button()
    var shiftXLeftButton = Button()
    var shiftXRightButton = Button()
    var shiftYUpButton = Button()
    var shiftYDownButton = Button()

    var showHideButton = Button()

    var chartPane = VBox()

    var hasMarkers = false

    @FXML
    fun initialize(){
        tracePane.children.add(borderPane)
        borderPane.center = chartPane
        chartPane.children.add(lineChart)

        val yAxisButtonBox = VBox()
        yAxisButtonBox.children.addAll(shiftYUpButton, shiftYDownButton, zoomInYButton, zoomOutYButton)
        yAxisButtonBox.spacing = 5.0
        borderPane.left = yAxisButtonBox

        val xAxisButtonBox = HBox()
        xAxisButtonBox.alignment = Pos.TOP_RIGHT
        xAxisButtonBox.spacing = 5.0
        xAxisButtonBox.children.addAll(showHideButton, zoomInXButton, zoomOutXButton, shiftXLeftButton, shiftXRightButton)
        borderPane.bottom = xAxisButtonBox

        setButtonProperties()
        addShiftXRightButtonListener()
        addShiftXLeftButtonListener()
        addZoomOutYButtonListener()
        addZoomInYButtonListener()
        addZoomInXButtonListener()
        addZoomOutXButtonListener()
        addShiftYUpButtonListener()
        addShiftYDownButtonListener()

        addShowHideButtonListener()

        initialiseChart()
    }

    fun initialiseChart() {
        lineChart.createSymbols = false //This prevents the default styling for the line being drawn
        lineChart.animated = false
        lineChart.isLegendVisible = true
        lineChart.isHorizontalZeroLineVisible = false

        lineChart.prefWidthProperty().bind(tracePane.widthProperty())
        lineChart.prefHeightProperty().bind(tracePane.heightProperty())

        setAxisFeatures()
    }

    fun setButtonProperties(){
        //TODO hardcoded paths, move to constants file
        zoomInYButton.graphic = ImageView("/icons/button_expand_y.png")
        zoomOutYButton.graphic = ImageView("/icons/button_contract_y.png")

        zoomInXButton.graphic = ImageView("/icons/button_expand_x.png")
        zoomOutXButton.graphic = ImageView("/icons/button_contract_x.png")

        shiftXLeftButton.graphic = ImageView("/icons/button_left_arrow.png")
        shiftXRightButton.graphic = ImageView("/icons/button_right_arrow.png")

        shiftYUpButton.graphic = ImageView("/icons/button_up_arrow.png")
        shiftYDownButton.graphic = ImageView("/icons/button_down_arrow.png")
    }

    fun setAxisFeatures(){
        //Initial values
        xAxis.lowerBound = 0.0
        xAxis.upperBound = 20.0
        yAxis.lowerBound = 0.0
        yAxis.upperBound = 20.0

        xAxis.isAutoRanging = false
        yAxis.isAutoRanging = false

        xAxis.isMinorTickVisible = false
        xAxis.isTickMarkVisible = true
        xAxis.isTickLabelsVisible = true
        xAxis.tickUnit = xTickUnit
        xAxis.label = "Time (ms)" //TODO get from file eventually

        yAxis.isTickLabelsVisible = true
        yAxis.isMinorTickVisible = false
        yAxis.isTickMarkVisible = true
        yAxis.tickUnit = yTickUnit
//        yAxis.label = "Value" //TODO get from file eventually
        yAxis.tickLabelRotation = 270.0
        catYAxis.tickLabelRotation = 270.0
    }

    fun populateChart(){
        if(data.size > 0) {
            times = data["times"]!!
            var yMin = 0.0F
            var yMax = 0.0F

            for (trace in data) {
                if(trace.key.toLowerCase() != "times") { //TODO hardcoded trace name
                    val series = XYChart.Series<Number, Number>()
                    series.name = trace.key

                    var initialPlotLimit = maxInitialDataPoints
                    if(trace.value.size < maxInitialDataPoints){
                        initialPlotLimit = trace.value.size
                    }

                    GUIMain.loggerService.log(Level.INFO, "Adding trace ${trace.key} to chart...")
                    for(i in 0 until initialPlotLimit){
                        val dataPoint = trace.value[i]
                        if(dataPoint < yMin){
                            yMin = dataPoint
                        }

                        if(dataPoint > yMax){
                            yMax = dataPoint
                        }

                        series.data.add(XYChart.Data<Number, Number>(times[i], dataPoint))
                    }

                    xAxis.lowerBound = 0.0
                    xAxis.upperBound = times[initialPlotLimit-1].toDouble()
                    lineChart.data.add(series)
                }
            }

            yAxis.lowerBound = yMin.toDouble() - (yMin.toDouble()*0.1)
            yAxis.upperBound = yMax.toDouble() + (yMin.toDouble()*0.1)

            val xRange = xAxis.upperBound-xAxis.lowerBound
            xAxis.tickUnit = xRange/5

            val yRange = yAxis.upperBound-yAxis.lowerBound
            yAxis.tickUnit = yRange/5
        }
        else{
            GUIMain.loggerService.log(Level.WARNING, "No trace data found in TraceScrollWindow")
        }
    }

    fun populateEventMarkers(markerData: Array<FloatArray>){
        //Set a bunch of properties for appearance and functonality
        markerChart.maxWidth = lineChart.maxWidth
        markerChart.prefWidth = lineChart.prefWidth
        markerChart.maxHeight = 50.0
        markerChart.prefWidthProperty().bind(tracePane.widthProperty())
        markerChart.animated = false
        markerChart.isLegendVisible = false
        markerChart.isHorizontalZeroLineVisible = false
        markerXAxis.isMinorTickVisible = false
        markerXAxis.isTickMarkVisible = true
        markerXAxis.isTickLabelsVisible = false
        markerXAxis.isAutoRanging = false
        markerXAxis.tickUnit = xAxis.tickUnit
        markerChart.prefWidthProperty().bind(tracePane.widthProperty())

        hasMarkers = true
        //Populate the chart with event markers (an event marker will be anything greater than 0)
        for(i in 1 until markerData[0].size) {
            val series = XYChart.Series<Number, String>()
            val markerChannel = markerData.map { x ->
                var dataPoint = ""
                if(x[i] > 0.0){
                    dataPoint = x[i].toString()
                }

                Pair(x[0], dataPoint)
            }

            markerChannel.forEach { x ->
                if(x.second != ""){
                    series.data.add(XYChart.Data<Number, String>(x.first, x.second))
                }
            }

            markerChart.data.add(series)
        }

        chartPane.children.add(0, markerChart)

        markerXAxis.lowerBound = xAxis.lowerBound
        markerXAxis.upperBound = xAxis.upperBound
    }

    fun addShowHideButtonListener(){
        showHideButton.setOnAction {
            val dialog = Dialog<String>()
            dialog.title = "Show/Hide traces"
            val checkVBox = VBox()
            checkVBox.spacing = 5.0
            val checkBoxHeight = 20.0
            val checkBoxesPerColumn = 8
            val checkBoxColumnWidth = 60.0

            var checkBoxCount = 0
            var traceCount = 0
            var translateX = 0.0
            for(trace in data){
                val traceName = trace.key
                if(traceName != "times"){
                    if(checkBoxCount % checkBoxesPerColumn == 0 && (checkBoxCount > 0)){
                        translateX += checkBoxColumnWidth
                    }
                    val chk = CheckBox(traceName)
                    chk.minWidth = 50.0
                    chk.minHeight = checkBoxHeight
                    println("translateX is ${translateX}")
                    val translateY = (traceCount%checkBoxesPerColumn)*checkVBox.spacing
                    println("translateY is ${translateY}")
                    chk.translateX = translateX
                    chk.translateY = -translateY
                    checkVBox.children.add(chk)
                    checkBoxCount++
                    traceCount++
                }
            }

            dialog.dialogPane.children.add(checkVBox)

            val minWidth = checkBoxColumnWidth*ceil((checkBoxCount/checkBoxesPerColumn).toDouble())
            val minHeight = (checkBoxesPerColumn*checkVBox.spacing)+(checkBoxesPerColumn*checkBoxHeight)
            checkVBox.minWidth = minWidth
            checkVBox.minHeight = minHeight

            dialog.dialogPane.minWidth = minWidth
            dialog.dialogPane.minHeight = minHeight

//            checkBoxes.forEach {chk ->
//                chk.minWidth = 50.0
//                chk.minHeight = checkBoxHeight
//                checkVBox.children.add(chk)
//            }

//            checkVBox.children.forEach { x -> x.translateX += 25 }

            val okButton = Button("OK")
            dialog.dialogPane.children.add(okButton)
            dialog.showAndWait()
        }
    }

    fun addShiftXRightButtonListener(){
        shiftXRightButton.setOnAction {
            val maxTime = times.max()!!
            val range = xAxis.upperBound-xAxis.lowerBound
            if((xAxis.upperBound + (range*changeFactor)) <= maxTime) {
                xAxis.lowerBound += (range*changeFactor)
                xAxis.upperBound += (range*changeFactor)
                if(hasMarkers){
                    markerXAxis.lowerBound = xAxis.lowerBound
                    markerXAxis.upperBound = xAxis.upperBound
                }
            }
            else{
                xAxis.lowerBound = maxTime.toDouble()-range
                xAxis.upperBound = maxTime.toDouble()
                if(hasMarkers){
                    markerXAxis.lowerBound = xAxis.lowerBound
                    markerXAxis.upperBound = xAxis.upperBound
                }
            }

            redrawChart()
        }
    }

    fun addShiftXLeftButtonListener(){
        shiftXLeftButton.setOnAction {
            val range = xAxis.upperBound-xAxis.lowerBound
            if((xAxis.lowerBound - (range*changeFactor)) >= 0) {
                xAxis.lowerBound -= (range*changeFactor)
                xAxis.upperBound -= (range*changeFactor)
                if(hasMarkers){
                    markerXAxis.lowerBound = xAxis.lowerBound
                    markerXAxis.upperBound = xAxis.upperBound
                }
            }
            else{
                xAxis.lowerBound = 0.0
                xAxis.upperBound = range
                if(hasMarkers){
                    markerXAxis.lowerBound = xAxis.lowerBound
                    markerXAxis.upperBound = xAxis.upperBound
                }
            }

            redrawChart()
        }
    }

    fun addShiftYUpButtonListener(){
        shiftYUpButton.setOnAction {
            val range = yAxis.upperBound-yAxis.lowerBound
            yAxis.lowerBound += range*changeFactor
            yAxis.upperBound += range*changeFactor
        }
    }

    fun addShiftYDownButtonListener(){
        shiftYDownButton.setOnAction {
            //Unlike shifting the x axis, we don't have to guard against going into the negative because data on
            //the y axis could be negative
            val range = yAxis.upperBound-yAxis.lowerBound
            yAxis.lowerBound -= range*changeFactor
            yAxis.upperBound -= range*changeFactor
        }
    }

    fun addZoomInYButtonListener(){
        zoomInYButton.setOnAction {
            val range = yAxis.upperBound-yAxis.lowerBound
            yAxis.lowerBound += range*changeFactor
            yAxis.upperBound -= range*changeFactor

            val newRange = yAxis.upperBound-yAxis.lowerBound
            shiftYAmount = newRange/2
            yAxis.tickUnit = newRange/5
        }
    }

    fun addZoomOutYButtonListener(){
        zoomOutYButton.setOnAction {
            val range = yAxis.upperBound-yAxis.lowerBound
            yAxis.lowerBound -= range*changeFactor
            yAxis.upperBound += range*changeFactor

            val newRange = yAxis.upperBound-yAxis.lowerBound
            shiftYAmount = newRange/2
            yAxis.tickUnit = newRange/5
        }
    }

    fun addZoomInXButtonListener(){
        zoomInXButton.setOnAction {
            val range = xAxis.upperBound-xAxis.lowerBound
            xAxis.lowerBound = xAxis.lowerBound + (range*changeFactor)
            xAxis.upperBound = xAxis.upperBound -(range*changeFactor)
            if(hasMarkers){
                markerXAxis.lowerBound = xAxis.lowerBound
                markerXAxis.upperBound = xAxis.upperBound
            }

            val newRange = xAxis.upperBound-xAxis.lowerBound
            shiftXAmount = newRange/2
            xAxis.tickUnit = newRange/5
            if(hasMarkers){
                markerXAxis.tickUnit = xAxis.tickUnit
            }

            redrawChart()
        }
    }

    fun addZoomOutXButtonListener(){
        zoomOutXButton.setOnAction {
            val range = xAxis.upperBound-xAxis.lowerBound

            if((xAxis.lowerBound - (range*changeFactor)) < 0){
                xAxis.lowerBound = 0.0
            }
            else{
                xAxis.lowerBound -= range*changeFactor
            }

            if(hasMarkers){
                markerXAxis.lowerBound = xAxis.lowerBound
            }

            val newVal = xAxis.upperBound + (range * changeFactor)
            if(newVal > times.max()!!){
                xAxis.upperBound = times.max()!!.toDouble()
            }
            else {
                xAxis.upperBound  = newVal
            }

            if(hasMarkers){
                markerXAxis.upperBound = xAxis.upperBound
            }

            val newRange = xAxis.upperBound-xAxis.lowerBound
            shiftXAmount = newRange/2
            xAxis.tickUnit = newRange/5
            if(hasMarkers){
                markerXAxis.tickUnit = xAxis.tickUnit
            }

            redrawChart()
        }
    }

    /**
     * Method used to plot the correct subsets of traces based on UI button interactions. This is also to limit the
     * amount of data that is plotted as plotting too much data will slow everything down greatly. This will be called
     * if the x axis is zoomed in or zoomed out, or if the x axis is shifted left or right.
     */
    fun redrawChart(){
        GUIMain.loggerService.log(Level.INFO, "Redrawing chart")
        var newXLowerIndex = findClosestXIndex(xAxis.lowerBound, false) //Time index
        var newXUpperIndex = findClosestXIndex(xAxis.upperBound, true) //Time index

        //Clear all data
        lineChart.data.remove(0, lineChart.data.size)

        //Resolve rounding issue edge case (see findClosestXIndex())
        if(newXUpperIndex > times.size-1){
            newXUpperIndex -= 1
        }

        if(newXLowerIndex < 0){
            newXLowerIndex = 0
        }

        //Repopulate graph based on new lower and upper bounds
        val newTimes = times.slice(IntRange(newXLowerIndex, newXUpperIndex))
        for(trace in data) {
            if(trace.key != "times") {
                val newSeries = XYChart.Series<Number, Number>()
                val newTraceVals = trace.value.slice(IntRange(newXLowerIndex, newXUpperIndex))
                for (i in 0 until newTraceVals.size) {
                    newSeries.data.add(XYChart.Data<Number, Number>(newTimes[i], newTraceVals[i]))
                }
                newSeries.name = trace.key
                lineChart.data.add(newSeries)
            }
        }
    }

    /**
     * Find the closest index in the time series to a given time. Time is a double that will be the axis lower or upper
     * bound.
     * @param axisTme The time from the chart axis
     * @param isUpper Flag to indicate which rounding compensaton to do (add one or subtract one)
     * @return The closest index in the time series
     */
    fun findClosestXIndex(axisTime : Double, isUpper : Boolean) : Int{
        var minDifference = Pair(0, 9999999.0)
        for(i in 0 until times.size){
            val difference = abs(times[i]-axisTime)
            if (difference < minDifference.second){
                minDifference = Pair(i, difference)
            }
        }

        //Add or subtract an extra 1 to solve rounding issue (if the closest index is lower than the axisTime given)
        if(isUpper) {
            return minDifference.first + 1
        }

        if(!isUpper){
            return minDifference.first - 1
        }

        return minDifference.first
    }
}