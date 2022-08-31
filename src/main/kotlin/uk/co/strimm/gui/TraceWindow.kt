package uk.co.strimm.gui


import bibliothek.gui.dock.common.DefaultMultipleCDockable
import com.sun.javafx.charts.Legend
import javafx.application.Platform
import javafx.embed.swing.JFXPanel
import javafx.fxml.FXML
import javafx.fxml.FXMLLoader
import javafx.scene.Scene
import javafx.scene.chart.LineChart
import javafx.scene.chart.NumberAxis
import javafx.scene.chart.XYChart
import javafx.scene.control.Label
import javafx.scene.layout.VBox
import org.scijava.plugin.Plugin
import uk.co.strimm.STRIMMBuffer
import uk.co.strimm.STRIMMSignalBuffer
import uk.co.strimm.experiment.Sink
import uk.co.strimm.plugins.AbstractDockableWindow
import uk.co.strimm.plugins.DockableWindowPlugin
import java.util.logging.Level


var debugTW = false

@Plugin(type = DockableWindowPlugin::class, menuPath = "Window>Trace Feed")
class TraceWindowPlugin : AbstractDockableWindow() {
    override var title = GUIMain.utilsService.createDockableWindowTitleText("", false)
    lateinit var traceWindowController : TraceWindow

    override fun setCustomData(data: Any?) {
        traceWindowController.properties = data as HashMap<String, String>
    }
    override var dockableWindowMultiple : DefaultMultipleCDockable = run {
        this.createDock(title).apply {
            val fxPanel = JFXPanel()
            add(fxPanel)

            try {
                val URL= this.javaClass.getResource("/fxml/TraceChart.fxml")
                val loader = FXMLLoader(URL)
                GUIMain.loggerService.log(
                    Level.SEVERE,
                    "loader path " + loader.location.path
                )
                val controller = TraceWindow()
                traceWindowController = controller
                loader.setController(controller)
                val pane = loader.load() as VBox
                val scene = Scene(pane)
                Platform.runLater {
                    fxPanel.scene = scene
                }
                dockableWindowMultiple = this

            } catch(ex : Exception){
                GUIMain.loggerService.log(
                    Level.SEVERE,
                    "TERRY :exception " + ex.message
                )
            }
        }
    }
}


class TraceWindow {
    var sink: Sink? = null
    lateinit var properties : HashMap<String, String>
    var xAxis = NumberAxis()
    var yAxis = NumberAxis()
    var allSeries = hashMapOf<String, XYChart.Series<Number, Number>>()


    @FXML
    lateinit var tracePane: VBox
    var innerPane = VBox()
    var lineChart = LineChart<Number, Number>(xAxis, yAxis)

    @FXML
    fun initialize() {
        try {
            initialiseChart()
            innerPane.children.add(lineChart)
            tracePane.children.add(innerPane)
            setTraceRenderFeatures()

        } catch (ex: Exception) {
            GUIMain.loggerService.log(Level.SEVERE, "Error in initializing trace window. Error: " + ex.message)
        }
    }

    fun initialiseChart() {
        lineChart.createSymbols = false//This prevents the default styling for the line being drawn
        lineChart.animated = false  //weird
        lineChart.isLegendVisible = true  // puts them at the bottom - looks good but then also shows one for the cursor
        lineChart.isHorizontalZeroLineVisible = false
    }

    fun furtherInit(){
        if (properties["xAxisLabel"] != null) xAxis.label = properties["xAxisLabel"]
        if (properties["yAxisLabel"] != null) yAxis.label = properties["yAxisLabel"]
        if (properties["Title"] != null) lineChart.setTitle(properties["Title"])
    }

    fun setTraceRenderFeatures() {
        innerPane.prefWidthProperty().bind(tracePane.widthProperty())
        innerPane.prefHeightProperty().bind(tracePane.heightProperty())
        lineChart.prefHeightProperty().bind(innerPane.heightProperty())
        setAxisFeatures()
    }

    fun setAxisFeatures() {
        xAxis.lowerBound = 0.0
        xAxis.upperBound = 20.0
        yAxis.lowerBound = 0.0
        yAxis.upperBound = 20.0

        var isXAutoRanging = true
        val isYAutoRanging = true

        xAxis.isAutoRanging = isXAutoRanging
        yAxis.isAutoRanging = isYAutoRanging


        xAxis.isMinorTickVisible = true
        xAxis.isTickLabelsVisible = true


        yAxis.isTickLabelsVisible = true
        yAxis.isMinorTickVisible = true

    }

    fun updateChart(data: List<STRIMMBuffer>) {
        Platform.runLater {
            val dat = data[0] as STRIMMSignalBuffer
            for (f in 0..dat!!.channelNames!!.size - 1) {
                var series = allSeries[dat!!.channelNames!!.get(f)]
                if (series == null) {
                    //make and add a new series
                    series = XYChart.Series<Number, Number>()
                    series.name = dat!!.channelNames!!.get(f)
                    allSeries[dat!!.channelNames!!.get(f)] = series
                    lineChart.data.add(series)
                }
                //add the new data
                for (ff in 0..dat!!.times!!.size - 1) {
                    val time = dat!!.times!!.get(ff)
                    val value = dat!!.data!!.get(f + ff * (dat!!.channelNames!!.size))
                    series.data!!.add(XYChart.Data(time, value))
                }
            }
        }
    }
}
