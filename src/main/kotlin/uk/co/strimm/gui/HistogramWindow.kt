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
import net.imagej.overlay.RectangleOverlay
import org.scijava.plugin.Plugin
import uk.co.strimm.STRIMMBuffer
import uk.co.strimm.STRIMMSignalBuffer
import uk.co.strimm.experiment.Sink
import uk.co.strimm.plugins.AbstractDockableWindow
import uk.co.strimm.plugins.DockableWindowPlugin
import java.util.logging.Level
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

@Plugin(type = DockableWindowPlugin::class, menuPath = "Window>Trace Feed")
class HistogramWindowPlugin : AbstractDockableWindow() {
    override var title = GUIMain.utilsService.createDockableWindowTitleText("", false)
    lateinit var histogramWindowController : HistogramWindow

    override fun setCustomData(data: Any?) {
        histogramWindowController.properties = data as HashMap<String, String>
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
                val controller = HistogramWindow()
                histogramWindowController = controller
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


class HistogramWindow {

    var sink: Sink? = null
    lateinit var properties : HashMap<String, String>
    var xMN = 0.0;
    var xMX = 20.0;
    var numBins: Int = 20;
    var xStep = 1.0

    //JavaFX structures
    var allSeries = hashMapOf<String, XYChart.Series<Number, Number>>()
    var freqArray = hashMapOf<String, MutableList<Double>>()
    var xAxis = NumberAxis()
    var yAxis = NumberAxis()
    var lineChart = LineChart<Number, Number>(xAxis, yAxis)
    var innerPane = VBox()

    fun furtherInit(){
        if (properties["xAxisLabel"] != null) xAxis.label = properties["xAxisLabel"]
        if (properties["yAxisLabel"] != null) yAxis.label = properties["yAxisLabel"]
        if (properties["Title"] != null) lineChart.setTitle(properties["Title"])

        if (properties["xMX"] != null) xMX = properties["xMX"]?.toDouble() ?: 20.0
        if (properties["xMN"] != null) xMN = properties["xMN"]?.toDouble() ?:0.0
        if (properties["numBins"] != null) numBins = properties["numBins"]?.toInt() ?: 20

        xStep = (xMX - xMN)/numBins.toDouble()

    }
    @FXML
    lateinit var tracePane: VBox
    @FXML
    fun initialize() {
        //println("HistogramWindow::initialize")
        try {
            lineChart.createSymbols = false
            lineChart.isLegendVisible = true
            lineChart.animated = false
            innerPane.children.add(lineChart)
            tracePane.children.add(innerPane)
            xAxis.isAutoRanging = true
            yAxis.isAutoRanging = true
            setTraceRenderFeatures()
        } catch (ex: Exception) {
            GUIMain.loggerService.log(Level.SEVERE, "Error in initializing trace window. Error: " + ex.message)
        }
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
                val name = dat!!.channelNames!!.get(f)
               // println(name)
                var series = allSeries[name]
                if (series == null) {
                    println("add new series")
                    //make and add a new series
                    series = XYChart.Series<Number, Number>()
                    series.name = name
                   // println("numBins " + numBins)
                    for (f in 1..numBins + 1) {
                        series.data.add(XYChart.Data(xMN + (f - 1) * xStep, 0.0))

                        series.data.add(XYChart.Data(xMN + (f - 1) * xStep, 0.0))

                    }
                    var array = DoubleArray(numBins)
                    freqArray[name] = array.toMutableList()
                    allSeries[name] = series
                    lineChart.data.add(series)
                }
                //add the new data
                for (ff in 0..dat!!.times!!.size - 1) {
                    //fill freq array
                    val value = dat!!.data!!.get(f + ff * (dat!!.channelNames!!.size))
                    if (value >= xMN && value < xMX) {
                        var ix = floor((value - xMN) / xStep).toInt()
                        freqArray[name]?.set(ix, (freqArray[name]?.get(ix))?.plus(1) ?: 0.0)

                    }
                }
                //fill the series
                var cnt = 0
                for (ff in 0..numBins - 1) {
                    series.data.set(cnt, XYChart.Data(xMN + ff * xStep, freqArray[name]?.get(ff)))
                    cnt = cnt + 1
                    series.data.set(cnt, XYChart.Data(xMN + (ff + 1) * xStep, freqArray[name]?.get(ff)))
                    cnt = cnt + 1
                }
            }
        }
    }
}


