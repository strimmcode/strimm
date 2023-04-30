package uk.co.strimm.gui

import bibliothek.gui.dock.common.DefaultMultipleCDockable
import javafx.application.Platform
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
import java.util.logging.Level

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

    var shiftAmount = 500 //When a shift button is clicked, how much will it shift by (unit is the time unit). This is the initial value and will change if the x axis range is changed
    var xTickUnit = 100.0
    var yTickUnit = 100.0
    var changeFactor = 0.25
    var times = floatArrayOf()

    val maxInitialDataPoints = 1000

    var zoomOutYButton = Button()
    var zoomInYButton = Button()
    var zoomInXButton = Button()
    var zoomOutXButton = Button()
    var shiftXLeftButton = Button()
    var shiftXRightButton = Button()

    @FXML
    fun initialize(){
        tracePane.children.add(borderPane)
        borderPane.center = lineChart

        val yAxisButtonBox = VBox()
        yAxisButtonBox.children.addAll(zoomInYButton, zoomOutYButton)
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
            if((xAxis.upperBound + shiftAmount) <= maxTime) {
                xAxis.lowerBound += shiftAmount
                xAxis.upperBound += shiftAmount
            }
            else{
                xAxis.lowerBound = maxTime.toDouble()-shiftAmount
                xAxis.upperBound = maxTime.toDouble()
            }
        }
    }

    fun addShiftXLeftButtonListener(){
        shiftXLeftButton.setOnAction {
            if((xAxis.lowerBound - shiftAmount) >= 0) {
                xAxis.lowerBound -= shiftAmount
                xAxis.upperBound -= shiftAmount
            }
            else{
                xAxis.lowerBound = 0.0
                xAxis.upperBound = shiftAmount.toDouble()
            }
        }
    }

    fun addZoomInYButtonListener(){
        zoomInYButton.setOnAction {
            yAxis.lowerBound += yAxis.lowerBound*changeFactor
            yAxis.upperBound -= yAxis.upperBound*changeFactor
        }
    }

    fun addZoomOutYButtonListener(){
        zoomOutYButton.setOnAction {
            yAxis.lowerBound -= yAxis.lowerBound*changeFactor
            yAxis.upperBound += yAxis.upperBound*changeFactor
        }
    }

    fun addZoomInXButtonListener(){
        zoomInXButton.setOnAction {
            println("Zoom in")
            xAxis.lowerBound += xAxis.lowerBound*changeFactor
            xAxis.upperBound -= xAxis.upperBound*changeFactor
        }
    }

    fun addZoomOutXButtonListener(){
        zoomOutXButton.setOnAction {
            println("Zoom out")
            if((xAxis.lowerBound + (xAxis.lowerBound*changeFactor)) < 0){
                xAxis.lowerBound = 0.0
            }
            else{
                xAxis.lowerBound -= xAxis.lowerBound*changeFactor
            }

            println("Max time is: ${times.max()!!}")
            println("Old upper bound value is: ${xAxis.upperBound}")
            val newVal = xAxis.upperBound + (xAxis.upperBound * changeFactor)
            println("New upper bound value is: $newVal")
            println("Max time is: ${times.max()!!}")
            if(newVal > times.max()!!){
                println("Max limit hit")
                xAxis.upperBound = times.max()!!.toDouble()
            }
            else {
                println("Max limit not hit")
                xAxis.upperBound  = newVal
            }
        }
    }
}