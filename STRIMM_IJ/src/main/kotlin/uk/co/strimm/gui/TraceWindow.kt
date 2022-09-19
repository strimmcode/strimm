package uk.co.strimm.gui

import akka.actor.ActorRef
import bibliothek.gui.dock.common.DefaultMultipleCDockable
import javafx.application.Platform
import javafx.embed.swing.JFXPanel
import javafx.event.ActionEvent
import javafx.event.EventHandler
import javafx.fxml.FXML
import javafx.fxml.FXMLLoader
import javafx.scene.Cursor.*
import javafx.scene.ImageCursor
import javafx.scene.Scene
import javafx.scene.chart.LineChart
import javafx.scene.chart.NumberAxis
import javafx.scene.chart.XYChart
import javafx.scene.control.*
import javafx.scene.image.Image
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import javafx.stage.FileChooser
import net.imagej.overlay.Overlay
import org.scijava.plugin.Plugin
import uk.co.strimm.*
import uk.co.strimm.actors.CameraActor
import uk.co.strimm.actors.messages.tell.TellCameraChangeZ
import uk.co.strimm.plugins.AbstractDockableWindow
import uk.co.strimm.plugins.DockableWindowPlugin
import java.math.RoundingMode
import java.net.URL
import java.text.DecimalFormat
import java.util.*
import java.util.logging.Level
import javax.swing.JOptionPane
import kotlin.math.max
import kotlin.math.min

var debugTW = false

@Plugin(type = DockableWindowPlugin::class, menuPath = "Window>Trace Feed")
class TraceWindowPlugin : AbstractDockableWindow() {
    override var title = GUIMain.utilsService.createDockableWindowTitleText("", false)
    lateinit var traceWindowController : TraceWindow

    //called 3x for 3 virtual channels in each case had no data so just passed through - do we need this function?
    override fun setCustomData(data: Any?) {
        GUIMain.loggerService.log(
            Level.SEVERE,
            "TERRY :setCustomData"
        )
       // println("TraceWindowPlugin::setCustonData")
        //TODO get and set title from Nidaq (trace device class when implemented
        if(data is TraceDataWithFrameNumbers){
            val traceData = data.data.second
            var isIncremented = false

            for(j in 0 until traceData.size) {
                val roiDataSeries = traceData[j]
                val frameSeries = data.data.first
                for (i in 0 until roiDataSeries.size) {
                    traceWindowController.updateChart((roiDataSeries[i].timeAcquired.toDouble() / 1000), roiDataSeries[i], frameSeries[i])

                    //We only need to do the increments for one series
                    if (!isIncremented) {
                        traceWindowController.incrementUpperIndices()
                        traceWindowController.incrementLowerIndices()
                    }
                }

                isIncremented = true
            }

            traceWindowController.followToggle.isDisable = false
            traceWindowController.reloadMode = true
        }
    }
    override var dockableWindowMultiple : DefaultMultipleCDockable = run {
        this.createDock(title).apply {
            GUIMain.loggerService.log(
                Level.SEVERE,
                "TERRY :inside cstr()"
            )
            GUIMain.loggerService.log(
                Level.SEVERE,
                "TERRY :before fxPanel"
            )

            val fxPanel = JFXPanel()

            GUIMain.loggerService.log(
                Level.SEVERE,
                "TERRY :made fxPanel"
            )
            add(fxPanel)
            GUIMain.loggerService.log(
                Level.SEVERE,
                "TERRY :add(fxPanel)"
            )
            this.titleText = title
            GUIMain.loggerService.log(
                Level.SEVERE,
                "TERRY :" + this.titleText
            )
            GUIMain.loggerService.log(
                Level.SEVERE,
                "TERRY :about to FXMLLoader"
            )

            try {
                val URL= this.javaClass.getResource("/fxml/TraceChart.fxml")
                val loader = FXMLLoader(URL)

                GUIMain.loggerService.log(
                    Level.SEVERE,
                    "loader path " + loader.location.path
                )

                GUIMain.loggerService.log(
                    Level.SEVERE,
                    "TERRY :after FXMLLoader"
                )
                GUIMain.loggerService.log(

                    Level.SEVERE,
                    "TERRY :before TraceWindow()"
                )
                val controller = TraceWindow()
                GUIMain.loggerService.log(
                    Level.SEVERE,
                    "TERRY : after TraceWindow()"
                )
                traceWindowController = controller
                GUIMain.loggerService.log(
                    Level.SEVERE,
                    "TERRY : before loader.setController(controller)"
                )
                loader.setController(controller)
                GUIMain.loggerService.log(
                    Level.SEVERE,
                    "TERRY :after loader.setController(controller)"
                )
                val pane = loader.load() as VBox
                GUIMain.loggerService.log(
                    Level.SEVERE,
                    "TERRY :after  loader.load() as VBox " + pane.toString()
                )


                val scene = Scene(pane)
                GUIMain.loggerService.log(
                    Level.SEVERE,
                    "TERRY :after Scene(pane)" + scene.toString()
                )
                GUIMain.loggerService.log(
                    Level.SEVERE,
                    "TERRY : before fxPanel.scene = scene"
                )
                Platform.runLater {
                    fxPanel.scene = scene
                }


                GUIMain.loggerService.log(
                    Level.SEVERE,
                    "TERRY :after fxPanel.scene = scene"
                )
                dockableWindowMultiple = this
                GUIMain.loggerService.log(
                    Level.SEVERE,
                    "TERRY :dockableWindowMultiple = this"
                )
            } catch(ex : Exception){
                GUIMain.loggerService.log(
                    Level.SEVERE,
                    "TERRY :exception " + ex.message
                )
            }
        }
    }
}


/**
 * The trace feed will receive incoming trace data from a trace actor. This will be data directly from a piece of hardware
 * or from an ROI in a image feed. To render the incoming data, data is stored in chart series that are then added to a line chart.
 * These series are manipulated based on the render mode and are for display purposes only. A separate, "master" copy is kept
 * of all incoming data. This can then be used when exporting the trace data. The trace feed will have four main display modes.
 * 1. RENDER_AND_CLEAR - This will render data from the start up to a fixed amount, it will then clear the
 * rendered data and render the next batch - like an oscilloscope.
 * 2. RENDER_AND_OVERWRITE - This will work in the same way as Render and clear except when the program goes back
 * to the beginning, it will not clear all the old data, but instead will overwrite it one data point at a timeAcquired. Data to
 * the left of the vertical cursor will be new, data to the right of the vertical cursor will be old. This is akin to an
 * ECG monitor
 * 3. RENDER_AND_SCROLL - The view will have a fixed width with a slider. When the rendered data length exceeds
 * the fixed length, the view will automatically keep up with the latest data being rendered. To view older data that is
 * off screen, you can uncheck a "follow" checkbox that will allow you to scroll back and forward along the series.
 * 4. RESIZE_AS_NEEDED - Here there will be no fixed amount of data to render, when the view boundaries are
 * exceeded the graph will resize automatically to fit the new data
 */
class TraceWindow{
    var associatedActor : ActorRef? = null

    //This series in seriesList pertains to only the data currently in view

    //each trace is a series

    //all on this window , each sink gets a window
    var seriesList = arrayListOf<TraceSeries>() //
    var seriesListMap = hashMapOf<TraceSeries?,Overlay?>() //multiple traces per window
    var debugTW = false
    //This seriesList pertains to all the data received, not just the data currently being displayed
    var entireDataSeries = arrayListOf<TraceSeries>()

    //This seriesList represents the vertical cursor that follows the most up-to-date data point
    //It is here as a series but acts as a vertical bar
    var vCursorSeries = XYChart.Series<Number,Number>()

    var sinkName : String = ""

    //TODO link this up to a setting

    var renderMode = DEFAULT_TRACE_RENDER_TYPE
    val defaultXAxisLowerBound = GUIMain.strimmSettingsService.getSettingValueByName(SettingKeys.TraceSettings.DEFAULT_X_AXIS_LOWERBOUND).toDouble()
    val defaultXAxisUpperBound = GUIMain.strimmSettingsService.getSettingValueByName(SettingKeys.TraceSettings.DEFAULT_X_AXIS_UPPERBOUND).toDouble()
    val defaultYAxisLowerBound = GUIMain.strimmSettingsService.getSettingValueByName(SettingKeys.TraceSettings.DEFAULT_Y_AXIS_LOWERBOUND).toDouble()
    val defaultYAxisUpperBound = GUIMain.strimmSettingsService.getSettingValueByName(SettingKeys.TraceSettings.DEFAULT_Y_AXIS_UPPERBOUND).toDouble()
    val defaultXAxisTickUnit = Math.floor((defaultXAxisUpperBound-defaultXAxisLowerBound)/5.0)
    val defaultYAxisTickUnit = 20.0
    var xAxis = NumberAxis()
    var yAxis = NumberAxis()
    val axisMinimumMaxValue = 0.01
    var dragStart = 0.0

//    [ERROR] Cannot create plugin: class='uk.co.strimm.gui.TraceWindowPlugin', menu='Window > Trace Feed', priority=0.0, enabled=true, pluginType=DockableWindowPlugin
//    java.lang.NumberFormatException: For input string: "500.0"

    //what is this?

//number of x-axis datapoints
    var numPoints = 300  //GUIMain.strimmSettingsService.getSettingValueByName(SettingKeys.TraceSettings.DEFAULT_X_AXIS_NUM_POINTS).toInt()
    val period : Float get() = ((xAxis.upperBound-xAxis.lowerBound)/numPoints).toFloat() //ticks?
    val yAxisPeriod = 5
    var reloadMode = false

    //Components
    @FXML
    lateinit var tracePane : VBox
    var lineChart = LineChart<Number,Number>(xAxis,yAxis)
    var innerPane = VBox()
    var toolBar = ToolBar()
    var slider = Slider(0.0,defaultXAxisUpperBound,defaultXAxisUpperBound)
    var followToggle = CheckBox()

    //Indices (mainly used when scrolling)
    var globalXUpperTimeIndex = 0
    var globalXLowerTimeIndex = 0
    var globalFreezeMaxTimeIndex = 0
    var globalFreezeMinTimeIndex = 0

    //All incoming data will be added to this.
    var masterTimeSeries = listOf<Number>()
    var masterSet = false

    //This flag is used to determine the first timeAcquired we have incoming data so we can set the y-axis range properly
    var firstTime = true

    /**
     * Initialise and add the graphical elements for the trace feed
     */
    @FXML
    fun initialize() {
        // println("TraceWindow::initialize")
        try {
            initialiseChart()
            addTraceToolbarButtons()
            innerPane.children.add(toolBar)
            innerPane.children.add(lineChart)
            tracePane.children.add(innerPane)
            addSliderListener()
            tracePane.children.add(slider)

            setTraceRenderFeatures()
        }
        catch(ex : Exception){
            GUIMain.loggerService.log(Level.SEVERE,"Error in initializing trace window. Error: " + ex.message)
        }
    }

    fun setNumberOfPoints(numOfPoints : Int){
       //println("TraceWindow::setNumberOfPoints") //TW 12/7/21 get this value wrong at it wont render nasty bug
        numPoints = 40 //numOfPoints   //this needs to be calculated based on the sample rate and thinning etc
    }

    /**
     * This method will be called in RENDER_AND_SCROLL mode, scrolling from right to left. It will decrement the
     * necessary indices and adjust the view data accordingly.
     */
    fun shiftChartDown(){
        // println("TraceWindow::shiftChartDown")
        globalFreezeMinTimeIndex--
        globalFreezeMaxTimeIndex--

        val newFrameIndex = entireDataSeries[0].frameNumbers.elementAt(globalFreezeMaxTimeIndex)//TODO hardcoded
        allcameraActors[0].tell(TellCameraChangeZ(intArrayOf(0,0,newFrameIndex)), ActorRef.noSender())

        for (traceSeries in seriesList) {
            val series = traceSeries.series
            val entireSeries = entireDataSeries.find { x -> x.seriesName == traceSeries.seriesName }

            try {
                if(series.data.size > 0) { //Only do something if there is something in the series
                    val entireSeriesFirstElementIndex = entireSeries!!.series.data.map { x -> x.xValue }.indexOf(series.data[0].xValue)
                    if (entireSeriesFirstElementIndex != -1 && entireSeriesFirstElementIndex > 0) { //Check there is still data to display
                        val newMinDatum = entireSeries.series.data[entireSeriesFirstElementIndex - 1]
                        series.data.removeAt(series.data.size - 1)
                        series.data.add(0, newMinDatum)


                    } else {
                        //If we are here, it means there is less than one screen's worth of data left
                        series.data.removeAt(series.data.size - 1)
                    }
                }
            } catch (ex: Exception) {
                GUIMain.loggerService.log(Level.SEVERE, "Error in adding new view data during sliding from " +
                        "right to left. Error message: ${ex.message}")
                GUIMain.loggerService.log(Level.SEVERE, ex.stackTrace)
            }
        }

        xAxis.lowerBound = seriesList[0].series.data[0].xValue.toDouble()
        xAxis.upperBound = seriesList[0].series.data[seriesList[0].series.data.size-1].xValue.toDouble()
    }

    val allcameraActors = GUIMain.actorService.getActorsOfType(CameraActor::class.java)

    /**
     * This method will be called in RENDER_AND_SCROLL mode, scrolling from left to right. It will increment the
     * necessary indicies and adjust the view data accordingly.
     */
    fun shiftChartUp(){
        // println("TraceWindow::shiftChartUp")
        globalFreezeMinTimeIndex++
        globalFreezeMaxTimeIndex++

        val newFrameIndex = entireDataSeries[0].frameNumbers.elementAt(globalFreezeMaxTimeIndex)//TODO hardcoded
        allcameraActors[0].tell(TellCameraChangeZ(intArrayOf(0,0,newFrameIndex)), ActorRef.noSender())

        for (traceSeries in seriesList) {
            val series = traceSeries.series
            val entireSeries = entireDataSeries.find { x -> x.seriesName == traceSeries.seriesName }

            try {
                val entireSeriesStartValue = entireSeries!!.series.data[0].xValue.toDouble()
                val entireSeriesEndValue = entireSeries.series.data[entireSeries.series.data.size-1].xValue.toDouble()
                val currentViewMaxXValue = seriesList[0].series.data[seriesList[0].series.data.size-1].xValue.toDouble()
                val currentViewMinXValue = seriesList[0].series.data[0].xValue.toDouble()
                if(currentViewMinXValue >= entireSeriesStartValue && currentViewMaxXValue < entireSeriesEndValue){
                    val currentViewIndex = entireSeries.series.data.map { x -> x.xValue }.indexOf(currentViewMaxXValue)
                    if(currentViewIndex < entireSeries.series.data.size-1) {
                        series.data.removeAt(0)
                        series.data.add(entireSeries.series.data[currentViewIndex+1])
                    }
                }
                else if(currentViewMaxXValue >= entireSeriesStartValue && (series.data.size-1 < globalFreezeMaxTimeIndex-globalFreezeMinTimeIndex)){
                    val currentViewIndex = entireSeries.series.data.map { x -> x.xValue }.indexOf(currentViewMaxXValue)
                    series.data.add(entireSeries.series.data[currentViewIndex+1])
                }
            } catch (ex: Exception) {
                GUIMain.loggerService.log(Level.SEVERE, "Error in adding new data during sliding from " +
                        "right to left. Error message: ${ex.message}")
                GUIMain.loggerService.log(Level.SEVERE, ex.stackTrace)
            }
        }

        xAxis.lowerBound = seriesList[0].series.data[0].xValue.toDouble()
        xAxis.upperBound = seriesList[0].series.data[seriesList[0].series.data.size-1].xValue.toDouble()
    }

    /**
     * This method is used when scrolling in RENDER_AND_SCROLL mode. The x-axis range values do not match up with the slider
     * values when using a mouse. As such we need to determine how much to change the x-axis range by based on how much
     * the slider has been scrolled, and then update the displayed data accordingly. This method will only be called
     * when the slider is not following, as such it uses a different set of data indices to the normal render loop
     * @param newValue The new slider value
     * @param oldValue The old slider value
     */
    fun adjustAxis(newValue: Float, oldValue : Float){
        // println("TraceWindow::adjustAxis")
        val df = setDecimalFormat()

        //Find the nearest axis value
        val newValRounded = df.format(newValue).toFloat()
        val oldValRounded = df.format(oldValue).toFloat()

        df.roundingMode = RoundingMode.FLOOR
        if(oldValRounded > newValRounded){ //Going from right to left (backwards in time)
            val difference = df.format(oldValRounded-newValRounded).toDouble()
            if (difference >= period) {
                val numIncrements = (difference / period).toInt()
                for (inc in 1..numIncrements) {
                    if(globalFreezeMinTimeIndex > 0) {
                        shiftChartDown()
                    }
                }
            }

            //Hacky way of making sure we reach the end. Need to find a better way at some point
            if(newValue == 0.0f){
                while(globalFreezeMinTimeIndex > 0){
                    shiftChartDown()
                }
            }
        }
        else if(newValRounded > oldValRounded){//Going from left to right (forwards in time)
            val difference = df.format(newValRounded-oldValRounded).toDouble()
            if(difference >= period){
                val numIncrements = (difference/period).toInt()
                for(inc in 1..numIncrements) {
                    shiftChartUp()
                }
            }

            //Hacky way of making sure we reach the end. Need to find a better way at some point
            if(newValue >= slider.max.toFloat()){
                while(globalFreezeMaxTimeIndex < entireDataSeries[0].series.data.size-1){
                    shiftChartUp()
                }
            }
        }
    }

    /**
     * This method is used to work out how precise to be with each increment/decrement when adjusting the x axis in
     * RENDER_AND_SCROLL mode
     */
    fun setDecimalFormat() : DecimalFormat{
        //  println("TraceWindow::setDecimalFormat")
        var pattern = "#."
        val periodString = period.toString().split(".")
        val decimalLength = periodString.last().length
        for(i in 0 until decimalLength){
            pattern += "0"
        }
        val df = DecimalFormat(pattern)
        df.roundingMode = RoundingMode.FLOOR
        return df
    }

    /**
    * This function initialises the chart and sets various properties relating to the chart
    */
    fun initialiseChart(){
        // println("TraceWindow::initialiseChart")
        lineChart.createSymbols = false//This prevents the default styling for the line being drawn
        lineChart.animated = false  //weird
        lineChart.isLegendVisible = false  // puts them at the bottom - looks good but then also shows one for the cursor
        lineChart.isHorizontalZeroLineVisible = false
        //lineChart.title = "Title"
       // lineChart.xAxis.label = "time(s)"
        //lineChart.yAxis.label = "Voltage(V)"

       // addVerticalCursor()
    }

    /**
     * If an overlay has been mapped, return the corresponding TraceSeries. Otherwise create a new series to map to the
     * overlay and return this.
     * @param overlay The incoming overlay associated with the data
     * @return The existing or newly created TraceSeries. This can be null if there has been a failure to create the series
     */

    //TW thuis also has lineChart.data.add - without which the data is not rendered. So this is where the information
    //is added to the chart
    fun getSeriesPertainingToOverlay(overlay : Overlay?) : TraceSeries?{
        // println("TraceWindow::getSeriesPertainingToOverlay(overlay : Overlay?) : TraceSeries?")

        //already in the plot
        for(series in seriesListMap) {
            if (series.value == overlay) {
               // println("TraceWindow::found series in seriesListMap")
                return series.key
            }
        }
        // println("TraceWindow::not found series in seriesListMap")
        //if there was no series in seriesListMap
        if(overlay != null){ //This means there is a new trace from an overlay and thus a new seriesList must be added
            //println("TraceWindow::overlay != null")
            val newSeries = createNewSeries(overlay)
            seriesListMap[newSeries] = overlay
            seriesList.add(newSeries)

            //Add a duplicate series to the list of all series data
            val allSeries = TraceSeries(0,0,0,0,XYChart.Series(),newSeries.frameNumbers,newSeries.roi,newSeries.seriesName)
            entireDataSeries.add(allSeries)

           // newSeries.series.name = "hi1"
            lineChart.data.add(newSeries.series)
//get the sink
            var displaySink  = GUIMain.experimentService.experimentStream.expConfig.sinkConfig.sinks.filter{snk-> snk.sinkName== sinkName}.first()
            var flowName = displaySink.inputNames[0]
            var colNum : Int = 0
            if (GUIMain.strimmUIService.traceColourByFlowAndOverlay[flowName] == null){
                colNum = min(GUIMain.strimmUIService.traceColourNonROI, GUIMain.roiColours.size - 1) ;
                GUIMain.strimmUIService.traceColourNonROI++

            } else {
                colNum = GUIMain.strimmUIService.traceColourByFlowAndOverlay[flowName]!!.filter{x->x.first == overlay}.first().second

            }

            var col : Color = GUIMain.roiColours[colNum]
            setSeriesColour(col.red, col.green, col.blue,1.0,newSeries.series)


//            val numSeries = seriesList.size-1
//            if(numSeries <= GUIMain.roiColours.size-1) {
//                val colourToUse = GUIMain.roiColours[0]   ///shows this sets the colour
//                setSeriesColour(colourToUse.red,colourToUse.green,colourToUse.blue,1.0,newSeries.series)
//            }
//            else{
//                GUIMain.loggerService.log(Level.WARNING,"Ran out of colours for traces. Using black")
//               setSeriesColour(0.0,0.0,0.0,1.0,newSeries.series)
//            }

            GUIMain.loggerService.log(Level.INFO, "Created new series for line chart")


            return seriesList[seriesList.size-1]
        }
        else{
            //println("TraceWindow::overlay == null")
            val newSeries = createNewSeries()
            seriesListMap[newSeries] = null
            seriesList.add(newSeries)

            //Add a duplicate series to the list of all series data
            val allSeries = TraceSeries(0,0,0,0,XYChart.Series(),newSeries.frameNumbers,newSeries.roi,newSeries.seriesName)
            entireDataSeries.add(allSeries)

           // newSeries.series.name = "hi2"
            lineChart.data.add(newSeries.series)

            val numSeries = seriesList.size-1
            if(numSeries <= GUIMain.roiColours.size-1) {
                val colourToUse = GUIMain.roiColours[numSeries]
                setSeriesColour(colourToUse.red,colourToUse.green,colourToUse.blue,1.0,newSeries.series)
            }
            else{
                GUIMain.loggerService.log(Level.WARNING,"Ran out of colours for traces. Using black")
                setSeriesColour(0.0,0.0,0.0,1.0,newSeries.series)
            }

            GUIMain.loggerService.log(Level.INFO, "Created new series for line chart")

            return seriesList[seriesList.size-1]
        }
    }

    /**
     * Creates a new TraceSeries
     */
    fun createNewSeries(): TraceSeries{
        //println("TraceWindow::createNewSeries(): TraceSeries")
        when(renderMode){
            TraceRenderType.RENDER_AND_SCROLL ->{
                return TraceSeries(globalXLowerTimeIndex,
                        globalXUpperTimeIndex,
                        0,
                        0,
                        XYChart.Series(),
                        arrayListOf(),
                        null,
                        "trace" + Random().nextInt(1000))
            }
            else -> {
                if(seriesList.size == 0){
                    return TraceSeries(0,
                            0,
                            0,
                            0,
                            XYChart.Series(),
                            arrayListOf(),
                            null,
                            "trace" + Random().nextInt(1000))
                }
                else{
                    return TraceSeries(seriesList[0].xLowerTimeIndex,
                            seriesList[0].xUpperTimeIndex,
                            0,
                            0,
                            XYChart.Series(),
                            arrayListOf(),
                            null,
                            "trace" + Random().nextInt(1000))
                }

            }
        }
    }

    /**
     * Creates a new TraceSeries from an overlay
     */
    fun createNewSeries(overlay: Overlay): TraceSeries{
        // println("TraceWindow::createNewSeries(overlay: Overlay): TraceSeries")
        when(renderMode){
            TraceRenderType.RENDER_AND_SCROLL ->{
                return TraceSeries(globalXLowerTimeIndex,
                                   globalXUpperTimeIndex,
                                   0,
                                   0,
                                   XYChart.Series(),
                                   arrayListOf(),
                                   overlay,
                        "traceOverlay" + Random().nextInt(1000))
            }
            else -> {
                if(seriesList.size == 0){
                    return TraceSeries(0,
                                       0,
                                       0,
                                       0,
                                       XYChart.Series(),
                            arrayListOf(),
                                       overlay,
                            "traceOverlay" + Random().nextInt(1000))
                }
                else{
                    return TraceSeries(seriesList[0].xLowerTimeIndex,
                                       seriesList[0].xUpperTimeIndex,
                                       0,
                                       0,
                                       XYChart.Series(),
                            arrayListOf(),
                                       overlay,
                            "traceOverlay" + Random().nextInt(1000))
                }

            }
        }
    }

    /**
     * Set the series colour based on pre-defined colours
     */
    fun setSeriesColour(red: Double, green: Double, blue: Double, alpha: Double, series : XYChart.Series<Number,Number>){
        // println("TraceWindow::setSeriesColour")
        val line = series.node.lookup(".chart-series-line")
        line.style = "-fx-stroke: rgba(${red * 255},${green * 255},${blue * 255},$alpha);"

    }

    /**
     * This method is called by the trace actor each timeAcquired there is new data. The appropriate logic is then called based
     * on the render mode
     */
    fun updateChart(timeVal : Number, traceData : TraceData, frameNumber: Int?){
        // println("TraceWindow::UpdateChart(timeVal : Number, traceData : TraceData, frameNumber: Int?)")
        //traceData is { data : Pair<Overlay?, timeAcquired : Double>, Number }

        //Platform.runLater is used for creating little threads to update features of a GUI in JavaFx
        Platform.runLater {
            //curve
            val roi = traceData.data.first // this identifies the trace on this plot
            //y - value
            val dataVal = traceData.data.second
            //x - value
            var timeAcq = traceData.timeAcquired

//println("update chart")
//          println(roi.toString())
//            if (roi.toString()[0] == 'N'){
//                println("send nidaq data")
//                }
            //if(!firstTime){ //We don't know the max and min of the data until it comes in
//            readjustYAxisRange(dataVal)

                //firstTime = true
            //}
           // println("getSeriesPertainingToOverlay")
            val pertainingSeries = getSeriesPertainingToOverlay(roi)

            if (pertainingSeries != null) {
                addDataToSeries(timeAcq, dataVal, frameNumber, pertainingSeries)
                when(renderMode){
                    TraceRenderType.RENDER_AND_CLEAR -> {
                        if (pertainingSeries.series.data.size >= numPoints || (pertainingSeries.series.data.size < numPoints && pertainingSeries.xUpperTimeIndex >= numPoints)) {
                            //SettingsWindow need to be updated when we've rendered one full "screen" of chart data
                            updateChartParameters(pertainingSeries)
                        }
                    }
                    TraceRenderType.RENDER_AND_OVERWRITE -> {
                        if (pertainingSeries.xUpperTimeIndex >= numPoints || (pertainingSeries.series.data.size < numPoints && pertainingSeries.xUpperTimeIndex >= numPoints)) {
                            //SettingsWindow need to be updated when we've rendered one full "screen" of chart data
                            updateChartParameters(pertainingSeries)
                        }
                    }
                    TraceRenderType.RENDER_AND_SCROLL, TraceRenderType.RESIZE_AS_NEEDED -> {
                        if (pertainingSeries.series.data.size-1 >= numPoints) {
                            //SettingsWindow need to be updated when we've rendered one full "screen" of chart data
                           // println("updatechartparameters")
                                updateChartParameters(pertainingSeries)
                        }
                    }
                }
                //auto-scale the y-axis
                readjustYAxisRange(dataVal, pertainingSeries)
            }
            //redraws the vertical black line which indicates the end of the data acquired from the NIDAQ so far
            updateVerticalCursor()
        }
    }

    /**
     * This method is used when series data has reached the end of the chart. Depending on the render mode the indices may be
     * reset or the axis ranges may be moved
     * @param traceSeries The TraceSeries we are dealing with
     */
    fun updateChartParameters(traceSeries : TraceSeries) {
        // println("TraceWindow::updateChartParameters(traceSeries : TraceSeries)")
        val series = traceSeries.series
        //We use this as an x axis range template for RENDER_AND_CLEAR and RENDER_AND_OVERWRITE modes.
        // When data is cleared we simply want to go back to the beginning, so we use the x-axis values up to the first
        // "clear"
        if (!masterSet) {
            masterTimeSeries = entireDataSeries[0].series.data.map{ x -> x.xValue }
            masterSet = true
        }

        when (renderMode) {
            TraceRenderType.RENDER_AND_CLEAR -> {
                traceSeries.series.data.clear()
                traceSeries.xLowerTimeIndex = 0
                traceSeries.xUpperTimeIndex = 0
                followToggle.isDisable = true
            }
            TraceRenderType.RENDER_AND_OVERWRITE -> {
                traceSeries.xLowerTimeIndex = 0
                traceSeries.xUpperTimeIndex = 0
                followToggle.isDisable = true
            }
            TraceRenderType.RENDER_AND_SCROLL -> {
                followToggle.isDisable = false

                if (followToggle.isSelected) {

                    if(series.data.size > 0 && series.data.size > numPoints) {
 //                       try {
                            series.data.removeAt(0) //This ensures that we never render more than the what we can see
//                        } catch(ex : Exception){
//                            println("here")
//                        }
                    }

                    //Adjust the axes and sliders now that we're moving
//                    if(seriesList[0].series.data.size > 0) {
                    try {
                        xAxis.lowerBound = seriesList[0].series.data[0].xValue.toDouble()
                        xAxis.upperBound =
                            seriesList[0].series.data[seriesList[0].series.data.size - 1].xValue.toDouble()
                    } catch (ex : Exception){
                       // println("hi there")
                    }

                    slider.max = xAxis.lowerBound
                    slider.value = slider.max
                    traceSeries.xLowerTimeIndex++
                    traceSeries.xLowerDataIndex++

                } else {
                    slider.isDisable = false
//                    slider.max = entireDataSeries[0].series.data[entireDataSeries[0].series.data.size-1].xValue.toDouble()
                }
            }
            TraceRenderType.RESIZE_AS_NEEDED -> {
                traceSeries.xLowerTimeIndex++
                traceSeries.xLowerDataIndex++
                xAxis.isAutoRanging = true
                followToggle.isDisable = true
            }
        }
    }

    /**
     * This method will add data to the trace series based on the current render mode
     * @param timeVal The new timeAcquired (x) value to add to the seriesList
     * @param dataVal The new data (y) value to add to the seriesList
     * @param traceSeries The TraceSeries we are dealing with
     */
    fun addDataToSeries(timeVal: Number, dataVal: Number, frameNumber : Int?, traceSeries : TraceSeries){
       // println("TraceWindow::addDataToSeries(timeVal: Number, dataVal: Number, frameNumber : Int?, traceSeries : TraceSeries)")
        val series = traceSeries.series //XYChart

       // println("roi " + traceSeries.roi.toString())
        when (renderMode) {
            TraceRenderType.RENDER_AND_CLEAR -> {
                if(!masterSet){
                    series.data.add(XYChart.Data(timeVal, dataVal))
                } else {
                    series.data.add(XYChart.Data(masterTimeSeries[traceSeries.xUpperTimeIndex],dataVal))
                }

                traceSeries.xUpperTimeIndex++
            }
            TraceRenderType.RENDER_AND_OVERWRITE -> {
                if(!masterSet && series.data.size < numPoints) {
                    series.data.add(XYChart.Data(timeVal, dataVal))
                }
                else if(masterSet && series.data.size < numPoints){
                    series.data.add(XYChart.Data(masterTimeSeries[traceSeries.xUpperTimeIndex], dataVal))
                }
                else{
                    //Remove old point at the current cursor, add new point at the current cursor
                    series.data.removeAt(traceSeries.xUpperTimeIndex)
                    series.data.add(traceSeries.xUpperTimeIndex, XYChart.Data(masterTimeSeries[traceSeries.xUpperTimeIndex], dataVal))
                }

                traceSeries.xUpperTimeIndex++
            }
            TraceRenderType.RENDER_AND_SCROLL -> {
                if(followToggle.isSelected) {
                    series.data.add(XYChart.Data(timeVal, dataVal))
                    traceSeries.xUpperTimeIndex++
                    traceSeries.xUpperDataIndex++
                }
            }
            TraceRenderType.RESIZE_AS_NEEDED -> {
                var item = XYChart.Data(timeVal, dataVal)
                series.data.add(item)


                traceSeries.xUpperTimeIndex++
                traceSeries.xUpperDataIndex++
            }
        }

        //This is important and is the "master" series that all incoming data is added to
        val entireSeries = entireDataSeries.find{ x -> x.seriesName == traceSeries.seriesName}
        entireSeries!!.series.data.add(XYChart.Data<Number,Number>(timeVal,dataVal))
        if(frameNumber != null) {
            entireSeries.frameNumbers.add(frameNumber)
        }
        entireSeries.xLowerTimeIndex = traceSeries.xLowerTimeIndex
        entireSeries.xLowerDataIndex = traceSeries.xLowerDataIndex
        entireSeries.xUpperTimeIndex = traceSeries.xUpperTimeIndex
        entireSeries.xUpperDataIndex = traceSeries.xUpperDataIndex
    }

    /**
     * This updates the vertical line seen at the most recent data point
     */
    fun updateVerticalCursor(){
        //  println("TraceWindow::updateVerticalCursor()")
        vCursorSeries.data.clear()

        var maxTimeVal = 0.0
        if(seriesList[0].series.data.size > 0) {
            maxTimeVal = if(renderMode == TraceRenderType.RENDER_AND_OVERWRITE) {
                seriesList[0].series.data[seriesList[0].xUpperTimeIndex-1].xValue.toDouble()
            } else{
                seriesList[0].series.data[seriesList[0].series.data.size-1].xValue.toDouble()
            }
        }

        vCursorSeries.data.add(XYChart.Data(maxTimeVal, yAxis.lowerBound))
        vCursorSeries.data.add(XYChart.Data(maxTimeVal, yAxis.upperBound))
    }

    /**
     * Increment the upper global indices
     */
    fun incrementUpperIndices(){
        // println("TraceWindow::incrementUpperIndices()")
        globalXUpperTimeIndex++
    }

    /**
     * Increment the lower global indices
     */
    fun incrementLowerIndices(){
        // println("TraceWindow::incrementLowerIndices()")
        if (globalXUpperTimeIndex >= numPoints && renderMode != TraceRenderType.RESIZE_AS_NEEDED) {
            globalXLowerTimeIndex++
        }
    }

    fun setYAxisRanges(dataVal : Number){
        // println("TraceWindow::setYAxisRanges(dataVal : Number)")
        val data = dataVal.toDouble()
        yAxis.lowerBound = data - (data/10)
        yAxis.upperBound = data + (data/10)
    }

    /**
     * Set the y axis range based on a data point. This will add 10 percent of the data point to each end
     * @param dataPoint The data point to be used as a reference
     */
    fun readjustYAxisRange(dataPoint : Number, pertainingSeries : TraceSeries?){
        // println("TraceWindow::readjustYAxisRange(dataPoint : Number, pertainingSeries : TraceSeries?)")
        //TODO review if changing based on a percentage is appropriate, especially with very large or very small scales
//        val changePct = 0.20
//        if(pertainingSeries!!.series.data.size > 0) {
////            if(dataPoint.toDouble() <= 0.1 || dataPoint.toDouble() >= -0.1){
////                yAxis.upperBound = 1.0
////                yAxis.lowerBound = -1.0
////            }
////            else {
//            val prev = pertainingSeries.series.data[pertainingSeries.series.data.size - 1].yValue.toDouble()
//            val change = (dataPoint.toDouble() * changePct)
//            val above = dataPoint.toDouble() + change
//            val below = dataPoint.toDouble() - abs(change)
//
//            if (firstTime) {
//                if(above-below < 2){
//                    yAxis.upperBound = 1.0
//                    yAxis.lowerBound = -1.0
//                }
//                else{
//                    yAxis.upperBound = dataPoint.toDouble() + change
//                    yAxis.lowerBound = dataPoint.toDouble() - abs(change)
//                }
//
//                firstTime = false
//            } else if (dataPoint.toDouble() >= prev + change || dataPoint.toDouble() <= prev - abs(change)) {
//                if(above-below < 2){
//                    yAxis.upperBound = 1.0
//                    yAxis.lowerBound = -1.0
//                }
//                else {
//                    yAxis.upperBound = dataPoint.toDouble() + change
//                    yAxis.lowerBound = dataPoint.toDouble() - abs(change)
//                }
//            }
////            }
//        }
        //TW 4/7/21 OK for now

        if(firstTime){
            yAxis.upperBound = 1.0
            yAxis.lowerBound = -1.0
            firstTime = false;
        }
        else {
            yAxis.upperBound = max(yAxis.upperBound.toDouble(), dataPoint.toDouble())
            yAxis.lowerBound = min(yAxis.lowerBound.toDouble(), dataPoint.toDouble())
        }
  //      println(yAxis.upperBound.toString() + "  " + dataPoint.toDouble() + "   " + yAxis.lowerBound.toString())
//        val change = (dataPoint.toDouble()*changePct)
//        yAxis.upperBound = dataPoint.toDouble() + change
//        yAxis.lowerBound = dataPoint.toDouble() - abs(change)
    }

    /**
     * This method sets the various properties of the components for rendering
     */
    fun setTraceRenderFeatures(){
        //  println("TraceWindow::setTraceRenderFeatures()")
        innerPane.prefWidthProperty().bind(tracePane.widthProperty())
        innerPane.prefHeightProperty().bind(tracePane.heightProperty())
        toolBar.prefWidthProperty().bind(innerPane.widthProperty())
        lineChart.prefHeightProperty().bind(innerPane.heightProperty())

        setAxisFeatures()

        slider.isDisable = true
        slider.isSnapToPixel = false
        slider.isSnapToTicks = false
    }

    /**
     * This method sets default settings for the trace feed's axes. Axes will behave differently based on the render
     * mode.
     * */
    fun setAxisFeatures(){
        // println("TraceWindow::setAxisFeatures()")
        xAxis.lowerBound = defaultXAxisLowerBound
        xAxis.upperBound = defaultXAxisUpperBound
        yAxis.lowerBound = defaultYAxisLowerBound
        yAxis.upperBound = defaultYAxisUpperBound

        var isXAutoRanging = false
        val isYAutoRanging = false

        when(renderMode){
            TraceRenderType.RENDER_AND_CLEAR -> {}
            TraceRenderType.RENDER_AND_OVERWRITE -> {}
            TraceRenderType.RENDER_AND_SCROLL -> {}
            TraceRenderType.RESIZE_AS_NEEDED -> {
                isXAutoRanging = true
            }
        }

        xAxis.isAutoRanging = isXAutoRanging
        yAxis.isAutoRanging = isYAutoRanging

        xAxis.tickUnit = defaultXAxisTickUnit
        xAxis.isMinorTickVisible = true
        xAxis.isTickLabelsVisible = true

        yAxis.tickUnit = defaultYAxisTickUnit
        yAxis.isTickLabelsVisible = true
        yAxis.isMinorTickVisible = true

        if(renderMode != TraceRenderType.RESIZE_AS_NEEDED) { //XAxis resizing cannot occur in this mode
            addAxisListeners(xAxis, true)
        }

        addAxisListeners(yAxis,false)
    }

    /**
     * Add a vertical cursor to the chart. This is actually a series but with only two points to draw a line
     */
    fun addVerticalCursor(){
        // println("TraceWindow::addVerticalCursor()")
        vCursorSeries.data.add(XYChart.Data(0.0, yAxis.lowerBound))
        vCursorSeries.data.add(XYChart.Data(0.0, yAxis.upperBound))
        //vCursorSeries.name = "hi3"
        lineChart.data.add(vCursorSeries)
        setSeriesColour(0.0, 0.0, 0.0, 1.0, vCursorSeries)
        vCursorSeries.node.styleClass.add(CssClasses.VERTICAL_CURSOR)
    }

    /**
     * Add various components to the trace toolbar
     */
    fun addTraceToolbarButtons(){
        //  println("TraceWindow::addTraceToolbarButtons()")
        val menuBar = MenuBar()
        val renderModeMenu = Menu(ComponentTexts.TraceWindow.RENDER_MODE_MENU)
        val tg = ToggleGroup()

        addRadioMenuItem(renderModeMenu, ComponentTexts.TraceWindow.RENDER_AND_CLEAR, TraceRenderType.RENDER_AND_CLEAR, tg)
        addRadioMenuItem(renderModeMenu, ComponentTexts.TraceWindow.RENDER_AND_OVERWRITE, TraceRenderType.RENDER_AND_OVERWRITE, tg)
        addRadioMenuItem(renderModeMenu, ComponentTexts.TraceWindow.RENDER_AND_SCROLL, TraceRenderType.RENDER_AND_SCROLL, tg)
        addRadioMenuItem(renderModeMenu, ComponentTexts.TraceWindow.RESIZE_AS_NEEDED, TraceRenderType.RESIZE_AS_NEEDED, tg)

        followToggle = CheckBox(ComponentTexts.TraceWindow.SCROLLBAR_FOLLOW_TOGGLE)
        //Follow is checked by default
        followToggle.isSelected = true
        followToggle.isDisable = true
        addFollowSelectedListener()

        val saveButton = Button(ComponentTexts.TraceWindow.SAVE_BUTTON_TEXT)
        addSaveButtonListener(saveButton)

        menuBar.menus.add(renderModeMenu)
        toolBar.items.add(menuBar)
        toolBar.items.add(followToggle)
        toolBar.items.add(saveButton)
    }

    //region Listeners
    /**
     * Add a listener to the "follow" checkbox (RENDER_AND_SCROLL only). When it is unchecked, a note of the latest
     * indices is taken and used as a reference. When it is re-checked, the chart is redrawn to reflect the latest data
     */
    fun addFollowSelectedListener(){
        followToggle.selectedProperty().addListener({ observable, oldValue, newValue ->
            if(!newValue) { //Unfollow
                val activeDisplays = GUIMain.imageDisplayService.activeImageDisplay
                //selectedROI not used
                val selectedROI = GUIMain.overlayService.getActiveOverlay(activeDisplays)

               // println(selectedROI.alpha)

                globalFreezeMinTimeIndex = globalXLowerTimeIndex
                globalFreezeMaxTimeIndex = globalXUpperTimeIndex
            }
            else{ //Follow
                xAxis.lowerBound = seriesList[0].series.data[0].xValue.toDouble()
                xAxis.upperBound = seriesList[0].series.data[seriesList[0].series.data.size-1].xValue.toDouble()
                slider.max = entireDataSeries[0].series.data[entireDataSeries[0].series.data.size-1].xValue.toDouble()
                redrawChart()
            }

            slider.isDisable = newValue
            slider.value = slider.max
        })
    }

    /**
     * Adds listeners to an axis. Both axes can be dragged to expand or contract, adjusting their ranges
     * @param axis The axis to add the listener to
     * @param isX Flag to say if this is in x axis or not. This is because both axes will have different logic for
     * the listeners
     */
    fun addAxisListeners(axis : NumberAxis, isX : Boolean){
        axis.setOnMouseEntered {
            if(it.isShiftDown) { //Shifting axis
                val bestSize = ImageCursor.getBestSize(32.0,3.0)
                tracePane.cursor = if (isX) {
                    val image = Image(Paths.Icons.SHIFT_X_AXIS_ICON,bestSize.width,bestSize.height,true,true)
                    ImageCursor(image)
                } else {
                    val image = Image(Paths.Icons.SHIFT_Y_AXIS_ICON,bestSize.width,bestSize.height,true,true)
                    ImageCursor(image)
                }
            }
            else{ //Rescaling axis
                tracePane.cursor = if (isX) {
                    H_RESIZE
                } else {
                    V_RESIZE
                }
            }
        }
        axis.setOnMouseExited { tracePane.cursor = DEFAULT }
        axis.setOnMousePressed {
            dragStart = if(isX) {
                it.x
            } else{
                it.y
            }
        }

        axis.setOnMouseDragged{
            val mouseVal = if (isX) {
                it.x
            } else {
                it.y
            }

            val periodVal = if (isX) {
                period
            } else {
                yAxisPeriod.toFloat()
            }

            if(it.isShiftDown){
                shiftAxis(axis, mouseVal, periodVal, isX)
            }
            else {
                rescaleAxis(axis, mouseVal, periodVal)
            }
        }
    }

    fun rescaleAxis(axis : NumberAxis, mouseVal : Double, periodVal : Float){
        if (mouseVal > dragStart) { //Going from left to right (expand axis)
            val difference = mouseVal - dragStart
            val periodsPerInterval = (axis.upperBound - axis.lowerBound) / periodVal
            val numIncrements = Math.ceil(difference / periodsPerInterval).toInt()
            for (inc in 1..numIncrements) {
                axis.upperBound += periodVal
            }

            dragStart = mouseVal
        }
        else if (dragStart > mouseVal) { //Going from right to left (contract axis)
            val difference = dragStart - mouseVal
            val periodsPerInterval = (axis.upperBound - axis.lowerBound) / periodVal
            val numIncrements = Math.ceil(difference / periodsPerInterval).toInt()
            for (inc in 1..numIncrements) {
                if (axis.upperBound - axis.lowerBound > axisMinimumMaxValue * 2) {
                    axis.upperBound -= periodVal
                }
            }

            dragStart = mouseVal
        }
    }

    fun shiftAxis(axis : NumberAxis, mouseVal : Double, periodVal : Float, isXAxis : Boolean){
        if(mouseVal > dragStart){ //Going from left to right (forward)
            val difference = mouseVal - dragStart
            val periodsPerInterval = (axis.upperBound - axis.lowerBound) / periodVal
            val numIncrements = Math.ceil(difference / periodsPerInterval).toInt()
            for (inc in 1..numIncrements) {
                axis.upperBound += periodVal
                axis.lowerBound += periodVal
            }

            dragStart = mouseVal
        }
        else if(dragStart > mouseVal){ //Going from left to right (backward)
            val difference = dragStart - mouseVal
            val periodsPerInterval = (axis.upperBound - axis.lowerBound) / periodVal
            val numIncrements = Math.ceil(difference / periodsPerInterval).toInt()
            for (inc in 1..numIncrements) {
                if (axis.lowerBound >= axisMinimumMaxValue || !isXAxis) {
                    axis.upperBound -= periodVal
                    axis.lowerBound -= periodVal
                }
            }

            dragStart = mouseVal
        }
    }

    /**
     * Add a listener to the slider so when the slider is changed by the user, adjust the view accordingly
     */
    fun addSliderListener(){
        slider.valueProperty().addListener { observable, oldValue, newValue ->
            if(!followToggle.isSelected){
                val oldVal = oldValue.toFloat()
                val newVal = newValue.toFloat()
                adjustAxis(newVal, oldVal)
            }
        }
    }

    /**
     * Add a listener to the save button in the toolbar. This will bring up a save prompt and then call the appropriate
     * logic
     * @param saveButton The button to add the listener to
     */
    fun addSaveButtonListener(saveButton : Button){
        saveButton.setOnAction {
            val fileChooser = FileChooser()
            val txtFilter = FileChooser.ExtensionFilter("Text files (*.txt)", "*.txt")
            fileChooser.extensionFilters.add(txtFilter)
            val csvFilter = FileChooser.ExtensionFilter("Comma Separated Values (*.csv)", "*.csv")
            fileChooser.extensionFilters.add(csvFilter)

            //We have to use null due to this being in a JFXPanel
            val file = fileChooser.showSaveDialog(null)

            val allXData = entireDataSeries.map{ traceSeries ->  traceSeries.series.data}.flatten()
            val maxTimeVal = allXData.maxBy { x -> x.xValue.toFloat() }!!.xValue.toDouble()
            GUIMain.exportService.exportTraceData(entireDataSeries,file,maxTimeVal)
        }
    }
    //endregion

    /**
     * This method redraws the chart view data. As the data that has been viewed will be different to the latest data,
     * we clear the view data and redraw it with the latest according to the indices
     */
    fun redrawChart(){
        //  println("TraceWindow::redrawChart()")
        //Clear all the view data
        seriesList.forEach { series -> series.series.data.clear() }

        if(renderMode != TraceRenderType.RESIZE_AS_NEEDED) {
            //Repopulate the view data with the latest data
            for (entireTraceSeries in entireDataSeries) {
                val correspondingSeries = seriesList.find { x -> x.seriesName == entireTraceSeries.seriesName }
                var start = 0
                if (entireTraceSeries.series.data.size >= numPoints) {
                    start = (entireTraceSeries.series.data.size - 1) - numPoints.toInt()
                }

                correspondingSeries!!.series.data.addAll(entireTraceSeries.series.data.subList(start, entireTraceSeries.series.data.size - 1))
            }
        }
        else{
            for (entireTraceSeries in entireDataSeries) {
                val correspondingSeries = seriesList.find { x -> x.seriesName == entireTraceSeries.seriesName }
                correspondingSeries!!.series.data.addAll(entireTraceSeries.series.data.subList(0, entireTraceSeries.series.data.size-1))
            }
        }
    }

    /**
     * Add a radio menu item to the toolbar menu.
     * @param menu The menu the radio button is added to
     * @param itemRenderMode The TraceRenderType associated with this radio menu item
     * @param tg The toggle group the radio menu item should be a part of
     */
    fun addRadioMenuItem(menu : Menu,itemText : String, itemRenderMode : TraceRenderType, tg: ToggleGroup){
       // println("TraceWindow::addRadioMenuItem")
        val radioItem = RadioMenuItem(itemText)
        radioItem.userData = itemRenderMode
        radioItem.isSelected = renderMode == itemRenderMode
        radioItem.toggleGroup = tg
        radioItem.onAction = EventHandler{ e ->
            setRenderMode(e)
        }
        menu.items.add(radioItem)
    }

    /**
     * Click event when the render mode has been changed.
     * @param e The click event
     */
    fun setRenderMode(e: ActionEvent) {
        // println("TraceWindow::setRenderMode(e: ActionEvent)")
        val radioItem = e.target as RadioMenuItem
        val newRenderMode = radioItem.userData as TraceRenderType
        renderMode = newRenderMode
        resetChart()
    }

    /**
     * This method is called when the render mode is changed. It will clear and redraw the series appropriately depending
     * on the new render mode
     */
    fun resetChart(){
        // println("TraceWindow::resetChart()")
        if(renderMode == TraceRenderType.RENDER_AND_CLEAR || renderMode == TraceRenderType.RENDER_AND_OVERWRITE) {
            seriesList.forEach { series ->
                series.series.data.clear()
                series.xUpperDataIndex = 0
                series.xLowerDataIndex = 0
                series.xLowerTimeIndex = 0
                series.xUpperTimeIndex = 0
            }

            xAxis.lowerBound = defaultXAxisLowerBound
            xAxis.upperBound = defaultXAxisUpperBound
            xAxis.isAutoRanging = false
        }
        else if(renderMode == TraceRenderType.RENDER_AND_SCROLL){
            globalXLowerTimeIndex = (entireDataSeries[0].series.data.size-1)-numPoints
            xAxis.lowerBound = entireDataSeries[0].series.data[globalXLowerTimeIndex].xValue.toDouble()
            xAxis.upperBound = entireDataSeries[0].series.data[entireDataSeries[0].series.data.size-1].xValue.toDouble()
            xAxis.isAutoRanging = false
            redrawChart()
            slider.max = xAxis.lowerBound
            slider.value = slider.max
        }
        else if(renderMode == TraceRenderType.RESIZE_AS_NEEDED){
            globalXLowerTimeIndex = 0
            redrawChart()
            xAxis.isAutoRanging = true
        }

        vCursorSeries.data.clear()
        masterSet = true
    }
}
