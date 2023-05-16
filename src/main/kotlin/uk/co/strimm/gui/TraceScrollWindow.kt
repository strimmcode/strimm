package uk.co.strimm.gui

import bibliothek.gui.dock.common.DefaultMultipleCDockable
import javafx.application.Platform
import javafx.collections.ObservableList
import javafx.embed.swing.JFXPanel
import javafx.fxml.FXML
import javafx.fxml.FXMLLoader
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.chart.LineChart
import javafx.scene.chart.NumberAxis
import javafx.scene.chart.XYChart
import javafx.scene.control.Button
import javafx.scene.image.ImageView
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import org.scijava.plugin.Plugin
import uk.co.strimm.plugins.AbstractDockableWindow
import uk.co.strimm.plugins.DockableWindowPlugin
import java.math.RoundingMode
import java.text.DecimalFormat
import java.util.logging.Level
import kotlin.math.abs
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

class TraceScrollWindow{
    lateinit var data : HashMap<String, FloatArray>

    @FXML
    lateinit var tracePane: VBox

    var borderPane = BorderPane()

    var xAxis = NumberAxis()
    var yAxis = NumberAxis()
    var lineChart = LineChart<Number, Number>(xAxis, yAxis)

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

    @FXML
    fun initialize(){
        tracePane.children.add(borderPane)
        borderPane.center = lineChart

        val yAxisButtonBox = VBox()
        yAxisButtonBox.children.addAll(shiftYUpButton, shiftYDownButton, zoomInYButton, zoomOutYButton)
        yAxisButtonBox.spacing = 5.0
        borderPane.left = yAxisButtonBox

        val xAxisButtonBox = HBox()
        xAxisButtonBox.alignment = Pos.TOP_RIGHT
        xAxisButtonBox.spacing = 5.0
        xAxisButtonBox.children.addAll(zoomInXButton, zoomOutXButton, shiftXLeftButton, shiftXRightButton)
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
        yAxis.label = "Value" //TODO get from file eventually
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

//                    val initialPlotLimit = trace.value.size
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
                    xAxis.upperBound = initialPlotLimit.toDouble()
                    lineChart.data.add(series)
                }
            }

            yAxis.lowerBound = yMin.toDouble() - (yMin.toDouble()*0.1)
            yAxis.upperBound = yMax.toDouble() + (yMin.toDouble()*0.1)
        }
        else{
            GUIMain.loggerService.log(Level.WARNING, "No trace data found in TraceScrollWindow")
        }
    }

    fun addShiftXRightButtonListener(){
        shiftXRightButton.setOnAction {
            val maxTime = times.max()!!
            if((xAxis.upperBound + shiftXAmount) <= maxTime) {
                xAxis.lowerBound += shiftXAmount
                xAxis.upperBound += shiftXAmount
            }
            else{
                xAxis.lowerBound = maxTime.toDouble()-shiftXAmount
                xAxis.upperBound = maxTime.toDouble()
            }

            redrawChart()
        }
    }

    fun addShiftXLeftButtonListener(){
        shiftXLeftButton.setOnAction {
            if((xAxis.lowerBound - shiftXAmount) >= 0) {
                xAxis.lowerBound -= shiftXAmount
                xAxis.upperBound -= shiftXAmount
            }
            else{
                xAxis.lowerBound = 0.0
                xAxis.upperBound = shiftXAmount
            }

            redrawChart()
        }
    }

    fun addShiftYUpButtonListener(){
        shiftYUpButton.setOnAction {
            yAxis.lowerBound += shiftYAmount
            yAxis.upperBound += shiftYAmount
        }
    }

    fun addShiftYDownButtonListener(){
        shiftYDownButton.setOnAction {
            //Unlike shifting the x axis, we don't have to guard against going into the negative because data on
            //the y axis could be negative
            yAxis.lowerBound -= shiftYAmount
            yAxis.upperBound -= shiftYAmount
        }
    }

    fun addZoomInYButtonListener(){
        zoomInYButton.setOnAction {
            yAxis.lowerBound += yAxis.lowerBound*changeFactor
            yAxis.upperBound -= yAxis.upperBound*changeFactor

            shiftYAmount -= (shiftYAmount*changeFactor).toInt()
        }
    }

    fun addZoomOutYButtonListener(){
        zoomOutYButton.setOnAction {
            yAxis.lowerBound -= yAxis.lowerBound*changeFactor
            yAxis.upperBound += yAxis.upperBound*changeFactor

            shiftYAmount += (shiftYAmount*changeFactor).toInt()
        }
    }

    fun addZoomInXButtonListener(){
        zoomInXButton.setOnAction {
            val range = xAxis.upperBound-xAxis.lowerBound
            xAxis.lowerBound = xAxis.lowerBound + (range*changeFactor)
            xAxis.upperBound = xAxis.upperBound -(range*changeFactor)

            shiftXAmount = range/2

            xAxis.tickUnit = range/5

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

            val newVal = xAxis.upperBound + (range * changeFactor)
            if(newVal > times.max()!!){
                xAxis.upperBound = times.max()!!.toDouble()
            }
            else {
                xAxis.upperBound  = newVal
            }

            shiftXAmount = range/2

            xAxis.tickUnit = range/5

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