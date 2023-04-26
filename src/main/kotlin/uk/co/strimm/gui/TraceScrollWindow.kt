package uk.co.strimm.gui

import bibliothek.gui.dock.common.DefaultMultipleCDockable
import javafx.application.Platform
import javafx.embed.swing.JFXPanel
import javafx.fxml.FXML
import javafx.fxml.FXMLLoader
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.Scene
import javafx.scene.chart.LineChart
import javafx.scene.chart.NumberAxis
import javafx.scene.chart.XYChart
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.image.ImageView
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import org.scijava.plugin.Plugin
import uk.co.strimm.plugins.AbstractDockableWindow
import uk.co.strimm.plugins.DockableWindowPlugin
import uk.co.strimm.setIcon
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

    var yMin = 0.0F
    var yMax = 0.0F

    var expandYButton = Button()
    var contractYButton = Button()
    var expandXButton = Button()
    var contractXButton = Button()
    var shiftXLeftButton = Button()
    var shiftXRightButton = Button()

    @FXML
    fun initialize(){
        tracePane.children.add(borderPane)
        borderPane.center = lineChart

        val yAxisButtonBox = VBox()
        yAxisButtonBox.children.addAll(contractYButton, expandYButton)
        yAxisButtonBox.spacing = 5.0
        borderPane.left = yAxisButtonBox

        val xAxisButtonBox = HBox()
        xAxisButtonBox.alignment = Pos.TOP_RIGHT
        xAxisButtonBox.spacing = 5.0
        xAxisButtonBox.children.addAll(contractXButton, expandXButton, shiftXLeftButton, shiftXRightButton)
        borderPane.bottom = xAxisButtonBox

        setButtonProperties()

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
        contractYButton.graphic = ImageView("/icons/button_contract_y.png")
        expandYButton.graphic = ImageView("/icons/button_expand_y.png")

        contractXButton.graphic = ImageView("/icons/button_contract_x.png")
        expandXButton.graphic = ImageView("/icons/button_expand_x.png")

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

        xAxis.isMinorTickVisible = true
        xAxis.isTickLabelsVisible = true

        yAxis.isTickLabelsVisible = true
        yAxis.isMinorTickVisible = true
    }

    fun populateChart(){
        if(data.size > 0) {
            for (trace in data) {
                if(trace.key.toLowerCase() != "times") { //TODO hardcoded trace name
                    val xData = generateXdata(trace.value.size)
                    val series = XYChart.Series<Number, Number>()
                    series.name = trace.key

                    if (trace.value.min()!! < yMin) {
                        yMin = trace.value.min()!!
                    }

                    if (trace.value.max()!! > yMax) {
                        yMax = trace.value.max()!!
                    }

                    for (i in 0 until trace.value.size) {
                        val dataXYPoint = XYChart.Data<Number, Number>(xData[i], trace.value[i])
                        series.data.add(dataXYPoint)
                    }
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

    fun generateXdata(size: Int) : List<Int>{
        return (0 until size).toList()
    }
}