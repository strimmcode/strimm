package uk.co.strimm.gui

import bibliothek.gui.dock.common.DefaultMultipleCDockable
import javafx.application.Platform
import javafx.collections.FXCollections.observableArrayList
import javafx.embed.swing.JFXPanel
import javafx.fxml.FXML
import javafx.fxml.FXMLLoader
import javafx.scene.Scene
import javafx.scene.chart.BarChart
import javafx.scene.chart.CategoryAxis
import javafx.scene.chart.NumberAxis
import javafx.scene.chart.XYChart
import javafx.scene.control.ScrollPane
import javafx.scene.layout.VBox
import org.scijava.plugin.Plugin
import uk.co.strimm.STRIMMBuffer
import uk.co.strimm.STRIMMSignalBuffer
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

                    (histogram.value.yAxis as NumberAxis).upperBound = maxVal

                    histogram.value.data.clear()
                    histogram.value.data.add(series)
                }
            }
        }
    }

    /**
     * If no histogram already exists for the camera/flow then create one
     * @param buffer The first buffer containing histogram data
     */
    fun createNewHistogram(buffer : STRIMMSignalBuffer){
        GUIMain.loggerService.log(Level.INFO, "Creating histogram for ${buffer.channelNames!!.first()} image feed")

        val range = 0..255 step 255/numPresentatonBins
        val xAxis = CategoryAxis()
        xAxis.categories = observableArrayList(range.map{ x -> x.toString()}.toList())
        val yAxis = NumberAxis()
        val histogram = BarChart(xAxis, yAxis)

        histograms[buffer.channelNames.first()] = histogram

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

        histogram.data.add(series)
        yAxis.upperBound = initialMax
        yAxis.lowerBound = 0.0

        Platform.runLater {
            histogramPane.children.add(histogram)
        }
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

//    fun determineRange(buffer : STRIMMSignalBuffer) : LongProgression{
//        val pixelType = pixelTypeHashMap[buffer.imagePixelType]
//        var range = 0L..255L step 1
//        if(pixelType != null){
//            when(pixelType){
//                "Byte" -> {
//                    range = 0L..255L step 1
//                }
//                "Short" -> {
//                    range = 0L..((Short.MAX_VALUE*2)+1).toLong() step 256L
//                }
//                "Float" -> {
//                    range = 0L..(2.0.pow(32)-1).toLong() step 16777216L
//                }
//            }
//        }
//
//        return range
//    }
}


