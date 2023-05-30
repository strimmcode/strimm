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
import javafx.scene.layout.VBox
import javafx.scene.text.Text
import org.apache.commons.lang3.StringUtils.isNumeric
import org.scijava.plugin.Plugin
import uk.co.strimm.STRIMMBuffer
import uk.co.strimm.STRIMMSignalBuffer
import uk.co.strimm.STRIMMSignalBuffer1
import uk.co.strimm.experiment.Sink
import uk.co.strimm.plugins.AbstractDockableWindow
import uk.co.strimm.plugins.DockableWindowPlugin
import java.awt.Color
import java.awt.Font
import java.util.logging.Level
import javax.swing.JLabel
import javax.swing.JLayeredPane
import javax.swing.SwingConstants

var debugTW = false

@Plugin(type = DockableWindowPlugin::class, menuPath = "Window>Trace Feed")
class TraceWindowPlugin : AbstractDockableWindow() {
    override var title = GUIMain.utilsService.createDockableWindowTitleText("", false)
    lateinit var traceWindowController: TraceWindow

    override fun setCustomData(data: Any?) {
        traceWindowController.properties = data as HashMap<String, String>
    }

    override var dockableWindowMultiple: DefaultMultipleCDockable = run {
        this.createDock(title).apply {
            val fxPanel = JFXPanel()
            add(fxPanel)

            try {
                val URL = this.javaClass.getResource("/fxml/TraceChart.fxml")
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

            } catch (ex: Exception) {
                GUIMain.loggerService.log(Level.SEVERE, "Error loading TraceWindow. Message: ${ex.message}")
                GUIMain.loggerService.log(Level.SEVERE, ex.stackTrace)
            }
        }
    }
}


class TraceWindow {
    var sink: Sink? = null
    lateinit var properties: HashMap<String, String>
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

    fun furtherInit() {
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

    fun setSeriesColour(
        red: Double,
        green: Double,
        blue: Double,
        alpha: Double,
        series: XYChart.Series<Number, Number>
    ) {
        // println("TraceWindow::setSeriesColour")
        val line = series.node.lookup(".chart-series-line")
        line.style = "-fx-stroke: rgba(${red * 255},${green * 255},${blue * 255},$alpha);"
    }

    fun updateChart(data: List<STRIMMBuffer>) {
        Platform.runLater {
            if (data[0] is STRIMMSignalBuffer) {
                val dat = data[0] as STRIMMSignalBuffer
                for (f in 0..dat!!.channelNames!!.size - 1) {
                    var series = allSeries[dat!!.channelNames!!.get(f)]
                    if (series == null) {
                        //make and add a new series
                        lineChart.isLegendVisible = false
                        series = XYChart.Series<Number, Number>()
                        series.name = dat!!.channelNames!!.get(f)
                        allSeries[dat!!.channelNames!!.get(f)] = series
                        lineChart.data.add(series)

                        var colourToUse = GUIMain.roiColours[GUIMain.roiColours.size - 1]
                        var chVal = f % GUIMain.roiColours.size
                        if (isNumeric(dat!!.channelNames!!.get(f))) {
                            chVal = dat!!.channelNames!!.get(f).toInt()
                        }

                        if (chVal < GUIMain.roiColours.size) {
                            colourToUse = GUIMain.roiColours[chVal]
                        }
                        colourToUse = GUIMain.roiColours[chVal % GUIMain.roiColours.size]
                        setSeriesColour(colourToUse.red, colourToUse.green, colourToUse.blue, 1.0, series)

                        for (n in lineChart.getChildrenUnmodifiable()) {
                            if (n is Legend) {
                                for (legendItem in (n as Legend).items) {
                                    //do them all so that the loaded ones are done correctly
                                    if (isNumeric(legendItem.text)) {
                                        val ix = legendItem.text.toInt()
                                        val colourToUse = GUIMain.roiColours[ix % GUIMain.roiColours.size]
                                        val color =
                                            "rgba(${colourToUse.red * 255},${colourToUse.green * 255},${colourToUse.blue * 255},1.0)"
                                        legendItem.symbol.style =
                                            "-fx-background-color: " + color + "; -fx-background-radius: 0;"
                                    }
                                }
                            }
                        }
                        lineChart.isLegendVisible = true
                        lineChart
                    }

                    //add the new data
                    for (ff in 0..dat!!.times!!.size - 1) {
                        val time = dat!!.times!!.get(ff)
                        val value = dat!!.data!!.get(f + ff * (dat!!.channelNames!!.size))
                        series.data!!.add(XYChart.Data(time, value))
                    }
                }

            }
            else if (data[0] is STRIMMSignalBuffer1) {
                val dat = data[0] as STRIMMSignalBuffer1
                val numChannels = dat!!.channelNames!!.size
                val numSamples = dat!!.traceData.size / numChannels
                for (f in 0..dat!!.channelNames!!.size - 1) {
                    var series = allSeries[dat!!.channelNames!!.get(f)]
                    if (series == null) {
                        //make and add a new series
                        if (dat!!.channelNames!!.get(f) == "DO8" || dat!!.channelNames!!.get(f) == "DO9") {
                            series = XYChart.Series<Number, Number>()
                            series.name = dat!!.channelNames!!.get(f)
                            allSeries[dat!!.channelNames!!.get(f)] = series
                            lineChart.data.add(series)
                        }
                    }

                    //add the new data
                    series = allSeries[dat!!.channelNames!!.get(f)]
                    if (series != null) {
                        for (ff in 0..numSamples - 1) {
                            val time = dat!!.traceData!!.get(ff * numChannels)
                            var value = 0.0
                            if (dat!!.channelNames!!.get(f) == "DO8") {
                                value = dat!!.traceData!!.get(1 + ff * numChannels)
                            } else if (dat!!.channelNames!!.get(f) == "DO9") {
                                value = dat!!.traceData!!.get(2 + ff * numChannels)
                            }
                            series!!.data!!.add(XYChart.Data(time, value))
                        }
                    }
                }
            }
        }
    }
}
