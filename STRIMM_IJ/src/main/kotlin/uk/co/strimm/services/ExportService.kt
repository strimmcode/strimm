package uk.co.strimm.services

import javafx.collections.FXCollections
import javafx.scene.control.*
import javafx.scene.layout.GridPane
import javafx.scene.layout.Pane
import javafx.scene.layout.VBox
import net.imagej.ImageJService
import net.imagej.axis.Axes
import net.imglib2.type.numeric.integer.UnsignedByteType
import net.imglib2.type.numeric.integer.UnsignedShortType
import org.scijava.plugin.Plugin
import org.scijava.service.AbstractService
import org.scijava.service.Service
import uk.co.strimm.*
import uk.co.strimm.gui.GUIMain
import uk.co.strimm.TraceSeries
import java.io.File
import java.io.FileWriter
import java.text.DecimalFormat
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.logging.Level

@Plugin(type = Service::class)
class ExportService : AbstractService(), ImageJService {
    var maxTime = 0.0
    var seriesExport = hashMapOf<TraceSeries, Pair<Double, Double>>()
    var allStoredTraceData = arrayListOf<ArrayList<TraceDataStore>>()
    var allStoredCameraMetaData = arrayListOf<ArrayList<CameraMetaDataStore>>()
    private var df = DecimalFormat("###.#########")

    /**
     * This method fires the export dialog and will call method to write to file if the result from the dialog is true
     * @param dataToExport A list of the data series for export
     * @param file The file that will be written to
     * @param maxTimeVal This is used to make sure the user does not go past the absolute maximum timeAcquired of all the data
     * series
     */
    fun exportTraceData(dataToExport: ArrayList<TraceSeries>, file: File, maxTimeVal: Double) {
        maxTime = maxTimeVal
        val exportOptions = showExportOptionsDialog(file, dataToExport)
        if (exportOptions.result) {
            writeTraceToFile(file, exportOptions)
        }
    }

    /**
     * This method builds the trace export dialog. It also contains any events associated with any of the dialog
     * components. Once options have been selected by the user, an ExportSettings data class will be returned
     * @param file The file that will be written to
     * @param dataToExport A list of the data series for export
     * @return A set of export settings based on the user's selection
     */
    private fun showExportOptionsDialog(file: File, dataToExport: ArrayList<TraceSeries>): ExportSettings {
        //Dialog window
        val dialog = Dialog<ButtonType>()
        dialog.title = ComponentTexts.TraceExportDialog.EXPORT_DIALOG_TITLE

        //Grid pane
        val gridPane = GridPane()
        gridPane.hgap = 5.0

        //Trace combo box
        val traces = dataToExport.map { traceSeries -> traceSeries.seriesName }
        val traceList = FXCollections.observableArrayList<String>("All traces")
        traceList.addAll(traces)
        val traceComboBox = ComboBox(traceList)
        traceComboBox.selectionModel.select(0)
        val traceLabel = Label("Trace: ")

        //To and from fields
        val fromLabel = Label(ComponentTexts.TraceExportDialog.FROM_LABEL)
        val textFieldFrom = TextField("0.0")
        val toLabel = Label(ComponentTexts.TraceExportDialog.TO_LABEL)
        val allXData = dataToExport.map { traceSeries -> traceSeries.series.data }.flatten()
        val maxVal = allXData.maxBy { x -> x.xValue.toFloat() }!!.xValue.toString()
        val textFieldTo = TextField(maxVal)
        textFieldFrom.isDisable = true
        textFieldTo.isDisable = true


        setTraceComboBoxListener(traceComboBox, textFieldFrom, textFieldTo, dataToExport)

        //All data checkbox
        val minMaxCheck = CheckBox("All data")
        minMaxCheck.isSelected = true
        //Disable the "to" and "from" text fields if the all data checkbox is checked
        minMaxCheck.selectedProperty().addListener { it, oldVal, newVal ->
            textFieldFrom.isDisable = newVal
            textFieldTo.isDisable = newVal
        }

        //Export summary pane
        val summaryPane = VBox()
        summaryPane.children.add(Label("Export summary"))
        summaryPane.prefHeight = 100.0
        summaryPane.prefWidth = 200.0

        //Add button
        val addButton = Button("Add")
        var fromTime = 0.0
        var toTime = 0.0
        addButton.setOnAction { it ->
            if (seriesExport.size < dataToExport.size) {
                fromTime = textFieldFrom.text.toDouble()
                toTime = textFieldTo.text.toDouble()
                if (traceComboBox.selectionModel.selectedIndex > 0) {
                    seriesExport[dataToExport[traceComboBox.selectionModel.selectedIndex - 1]] = Pair(fromTime, toTime)
                    updateSummaryArea(
                        dataToExport[traceComboBox.selectionModel.selectedIndex - 1],
                        fromTime,
                        toTime,
                        summaryPane
                    )
                } else {
                    for (i in dataToExport.indices) {
                        val seriesToExport = dataToExport[i]
                        val seriesMin =
                            seriesToExport.series.data.minBy { x -> x.xValue.toDouble() }!!.xValue.toDouble()
                        val seriesMax =
                            seriesToExport.series.data.maxBy { x -> x.xValue.toDouble() }!!.xValue.toDouble()
                        seriesExport[seriesToExport] = Pair(seriesMin, seriesMax)
                        updateSummaryArea(seriesToExport, seriesMin, seriesMax, summaryPane)
                    }
                }
            }
        }

        //Error label
        val errorLabel = Label(ComponentTexts.TraceExportDialog.TIME_EXPORT_RANGE_ERROR)
        errorLabel.isVisible = false

        //Add components to grid pane
        gridPane.add(errorLabel, 0, 0)
        gridPane.add(traceLabel, 0, 1)
        gridPane.add(traceComboBox, 1, 1)
        gridPane.add(minMaxCheck, 0, 2)
        gridPane.add(fromLabel, 0, 3)
        gridPane.add(textFieldFrom, 1, 3)
        gridPane.add(toLabel, 2, 3)
        gridPane.add(textFieldTo, 3, 3)
        gridPane.add(addButton, 0, 4)

        //Delimeter type
        val delimeterPane = Pane()
        val delimiterTypeLabel = Label(ComponentTexts.TraceExportDialog.DELIMITER_TYPE_LABEL)
        val delimiterTypes = FXCollections.observableArrayList(
            FileExtensions.COMMA_DELIMITER.first,
            FileExtensions.TAB_DELIMITER.first,
            FileExtensions.SEMICOLON_DELIMITER.first
        )
        val delimiterTypeComboBox = ComboBox(delimiterTypes)
        delimiterTypeComboBox.selectionModel.select(0)
        delimeterPane.children.addAll(delimiterTypeLabel, delimiterTypeComboBox)

        val parentBox = VBox()
        parentBox.children.addAll(gridPane, summaryPane, delimeterPane)

        //Dialog buttons
        val okButton = ButtonType(ComponentTexts.TraceExportDialog.OK_BUTTON, ButtonBar.ButtonData.OK_DONE)
        val cancelButton = ButtonType(ComponentTexts.TraceExportDialog.CANCEL_BUTTON, ButtonBar.ButtonData.CANCEL_CLOSE)

        //Listeners to validate
        textFieldFrom.textProperty().addListener { it ->
            if (!minMaxCheck.isSelected && traceComboBox.selectionModel.selectedIndex > 0) {//TODO for index = 0?
                val pertainingSeries = dataToExport[traceComboBox.selectionModel.selectedIndex - 1]
                val pertainingSeriesMinX =
                    pertainingSeries.series.data.minBy { x -> x.xValue.toDouble() }!!.xValue.toDouble()
                val pertainingSeriesMaxX =
                    pertainingSeries.series.data.maxBy { x -> x.xValue.toDouble() }!!.xValue.toDouble()
                val areFieldsValid = validateTraceExportTextFields(
                    listOf(textFieldFrom, textFieldTo),
                    pertainingSeriesMinX,
                    pertainingSeriesMaxX
                )
                dialog.dialogPane.lookupButton(okButton).isDisable = !areFieldsValid
                addButton.isDisable = !areFieldsValid
                errorLabel.isVisible = !areFieldsValid
            }
        }
        textFieldTo.textProperty().addListener { it ->
            if (!minMaxCheck.isSelected && traceComboBox.selectionModel.selectedIndex > 0) {//TODO for index = 0?
                val pertainingSeries = dataToExport[traceComboBox.selectionModel.selectedIndex - 1]
                val pertainingSeriesMinX =
                    pertainingSeries.series.data.minBy { x -> x.xValue.toDouble() }!!.xValue.toDouble()
                val pertainingSeriesMaxX =
                    pertainingSeries.series.data.maxBy { x -> x.xValue.toDouble() }!!.xValue.toDouble()
                val areFieldsValid = validateTraceExportTextFields(
                    listOf(textFieldFrom, textFieldTo),
                    pertainingSeriesMinX,
                    pertainingSeriesMaxX
                )
                dialog.dialogPane.lookupButton(okButton).isDisable = !areFieldsValid
                addButton.isDisable = !areFieldsValid
                errorLabel.isVisible = !areFieldsValid
            }
        }

        dialog.dialogPane.content = parentBox
        dialog.dialogPane.buttonTypes.addAll(okButton, cancelButton)

        val result = dialog.showAndWait()
        return if (result.get() == okButton) {
            return ExportSettings(
                true,
                file.extension,
                FileExtensions.DELIMITERS[delimiterTypeComboBox.value],
                seriesExport
            )
        } else {
            ExportSettings(false, file.extension, FileExtensions.DELIMITERS[delimiterTypeComboBox.value], seriesExport)
        }
    }

    /**
     * When the user clicks "add", this method will be called to update the summary area
     * @param series The series that has been added
     * @param from The from timeAcquired for the newly added series
     * @param to The to timeAcquired for the newly added series
     * @param summaryPane The pane to add the summary label to
     */
    private fun updateSummaryArea(series: TraceSeries, from: Double, to: Double, summaryPane: VBox) {
        val summaryLabel = Label(series.seriesName + " from: " + from + "s to: " + to + "s")
        summaryPane.children.add(summaryLabel)
    }

    /**
     * Not all series will start as zero timeAcquired and end at max timeAcquired. Therefore we must change the min and max values as
     * each series is selected from the combobox
     * @param traceComboBox The combobox for selecting all traces
     * @param textFieldFrom The text field displaying the "from" timeAcquired
     * @param textFieldTo The text field displaying the "to" timeAcquired
     * @param allTraceSeries All the data from all the series
     */
    private fun setTraceComboBoxListener(
        traceComboBox: ComboBox<String>,
        textFieldFrom: TextField,
        textFieldTo: TextField,
        allTraceSeries: ArrayList<TraceSeries>
    ) {
        traceComboBox.valueProperty().addListener { it, oldVal, newVal ->
            val index = traceComboBox.items.indexOf(it.value)
            if (index == 0) {
                textFieldFrom.text = "0.0"
                textFieldTo.text = maxTime.toString()
            } else {
                //Index needs to be index-1 as we inserted the "all traces" option at the beginning
                val selectedSeriesData = allTraceSeries[index - 1].series.data
                val seriesMin = selectedSeriesData.minBy { x -> x.xValue.toFloat() }!!.xValue
                textFieldFrom.text = seriesMin.toString()
                textFieldTo.text = maxTime.toString()
            }
        }
    }

    /**
     * Ensure that what the user has entered for both from and to fields is valid
     * @param fields The to and from fields
     * @param minVal The minimum x value in the series in question
     * @param maxVal The maximum x value in the series in question
     * @return If the field is valid or not
     */
    private fun validateTraceExportTextFields(fields: List<TextField>, minVal: Double, maxVal: Double): Boolean {
        for (field in fields) {
            val validateResult = validateNumberField(field.text, minVal, maxVal)
            if (!validateResult) {
                return false
            }
        }

        return true
    }

    /**
     * Validates that a text field is a number and is within a given range
     * @param text The text to validate
     * @param minVal The lower bound of the range
     * @param maxVal The upper bound of the range
     */
    private fun validateNumberField(text: String, minVal: Double, maxVal: Double): Boolean {
        val isMatches = text.matches(Regex("\\d+(\\.\\d+)?")) //Any number or decimal number
        return if (isMatches) {
            val textAsNumber = text.toDouble()
            textAsNumber in minVal..maxVal
        } else {
            false
        }
    }

    /**
     * Call methods to build the export string and then write to file. This will also handle any exceptions raised
     * @param file The file to be written to
     * @param exportSettings The export settings for all series that will be exported
     */
    private fun writeTraceToFile(file: File, exportSettings: ExportSettings) {
        try {
            exportStringToFile(FileWriter(file), exportSettings)
            val alert = Alert(Alert.AlertType.INFORMATION)
            alert.title = "Trace export"
            alert.headerText = null
            alert.contentText = "Trace data export succeeded"
            alert.showAndWait()
        } catch (ex: Exception) {
            GUIMain.loggerService.log(Level.SEVERE, "Error in exporting trace data. Error: ${ex.message}")
            GUIMain.loggerService.log(Level.SEVERE, ex.stackTrace)
            val alert = Alert(Alert.AlertType.INFORMATION)
            alert.title = "Trace export"
            alert.headerText = null
            alert.contentText = "Trace data export failed. See logs for error"
            alert.showAndWait()
        }
    }

    fun writeCameraMetaDataToFile(cameraMetaData: ArrayList<CameraMetaDataStore>) {
        val numCameraDataStoreActors = GUIMain.experimentService.experimentStream.cameraDataStoreActors.size
        allStoredCameraMetaData.add(cameraMetaData)

        val sb = StringBuilder()
        val delimiter = ",".toCharArray()

        if (allStoredCameraMetaData.size == numCameraDataStoreActors) {
            //Get a distinct list of sources
            val cameraSources =
                allStoredCameraMetaData.flatten().distinctBy { x -> x.cameraFeedName }.map { y -> y.cameraFeedName }
                    .sortedBy { x -> x }

            //Populate the column headers first
            cameraSources.forEach { camera ->
                sb.append(camera + "_times(milliseconds)")
                sb.append(delimiter)
                sb.append(camera + "_elapsedTimes(milliseconds)")
                sb.append(delimiter)
                sb.append(camera + "_interval(milliseconds)")
                sb.append(delimiter)
                sb.append(camera + "_frequency(Hz)")
                sb.append(delimiter)
                sb.append(camera + "_frameNumber")
                sb.append(delimiter)
            }

            sb.append("\n")

            //Allocate each source's data to its own entry to make it easier to deal with
            val dataForCameras = hashMapOf<String?, List<CameraMetaDataStore>>()
            var maxDataPoints = 0
            for (camera in cameraSources) {
                val dataForCamera = allStoredCameraMetaData.flatten().filter { x -> x.cameraFeedName == camera }
                dataForCameras[camera] = dataForCamera
                if (dataForCamera.size > maxDataPoints) {
                    maxDataPoints = dataForCamera.size
                }
            }

            //Go through all the data points and add them to the string builder
            for (i in 0..maxDataPoints) {
                for (camera in dataForCameras) {
                    if (i < camera.value.size) {
                        //Times
                        val timeAcquired = camera.value[i].timeAcquired.toDouble()
                        val timeStoreStarted = camera.value[0].timeAcquired.toDouble()
                        sb.append(timeAcquired)
                        sb.append(delimiter)

                        //Elapsed times
                        val elapsedTime = timeAcquired - timeStoreStarted
                        sb.append(elapsedTime)
                        sb.append(delimiter)

                        //Interval
                        var interval = 0.0
                        if (i == 0) {
                            sb.append(0)
                        } else {
                            val prevElapsedTime = camera.value[i - 1].timeAcquired.toDouble() - timeStoreStarted
                            interval = elapsedTime - prevElapsedTime
                            sb.append(df.format(interval))
                        }
                        sb.append(delimiter)

                        //Frequency
                        if (interval == 0.0) {
                            sb.append(0.0)
                        } else {
                            val frequency = df.format(1 / (interval / 1000))
                            sb.append(frequency)
                        }
                        sb.append(delimiter)

                        //Frame number
                        sb.append(camera.value[i].frameNumber)
                        sb.append(delimiter)
                    }
                }

                if (i < maxDataPoints) {
                    sb.append("\n")
                }
            }

            //This will remove a trailing carriage return and comma
            sb.setLength(sb.length - 2).toString()

            val timeStamp = ZonedDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME).replace(":", "-")

            try {
                val fileWriter =
                    FileWriter("${Paths.EXPERIMENT_OUTPUT_FOLDER}/${Paths.CAMERA_METADATA_PREFIX}_$timeStamp.csv")
                fileWriter.write(sb.toString())
                fileWriter.close()
            } catch (ex: Exception) {
                GUIMain.loggerService.log(
                    Level.SEVERE,
                    "Could not write trace output to file. Error message: ${ex.message}"
                )
                GUIMain.loggerService.log(Level.SEVERE, ex.stackTrace)
            }
        }
    }

    fun writeTracesToFile(traceData: ArrayList<TraceDataStore>) {
        val numTraceDataStoreActors = GUIMain.experimentService.experimentStream.traceDataStoreActors.size
        allStoredTraceData.add(traceData)

        val sb = StringBuilder()
        val delimiter = ",".toCharArray()

        if (allStoredTraceData.size != numTraceDataStoreActors) {
            GUIMain.loggerService.log(
                Level.WARNING,
                "Number of trace sources received does not match number of traces being stored"
            )
        }

        //Get a distinct list of sources
        val sources =
            allStoredTraceData.flatten().distinctBy { x -> x.roi?.name }.map { y -> y.roi }.sortedBy { x -> x?.name }

        //Allocate each source's data to its own entry to make it easier to deal with
        val dataForSources = hashMapOf<String?, List<TraceDataStore>>()
        var maxDataPoints = 0
        for (source in sources) {
            val dataForSource = allStoredTraceData.flatten().filter { x -> x.roi?.name == source?.name }
            dataForSources[source?.name] = dataForSource
            if (dataForSource.size > maxDataPoints) {
                maxDataPoints = dataForSource.size
            }
        }

        //Do a sort so the trace with the most points is first
        val sortedSources = dataForSources.toList()
            .sortedByDescending { (key, value) -> value.size }
            .toMap()

        for (source in sortedSources) {
            //Populate the column headers first
            sb.append(source.key + "_times(milliseconds)")
            sb.append(delimiter)
            sb.append(source.key + "_elapsedTimes(milliseconds)")
            sb.append(delimiter)
            sb.append(source.key + "_interval(milliseconds)")
            sb.append(delimiter)
            sb.append(source.key + "_frequency(Hz)")
            sb.append(delimiter)
            sb.append(source.key + "_dataPointNumber")
            sb.append(delimiter)
            sb.append(source.key + "_values")
            sb.append(delimiter)
        }
        sb.append("\n")

        //Go through all the data points and add them to the string builder
        for (i in 0..maxDataPoints) {
            for (source in sortedSources) {
                if (i < source.value.size) {
                    val timeAcquired = source.value[i].timeAcquired.toDouble()
                    val timeStoreStarted = source.value[0].timeAcquired.toDouble()

                    //Times
                    sb.append(timeAcquired)
                    sb.append(delimiter)


                    //Elapsed times
                    val elapsedTime = timeAcquired - timeStoreStarted
                    sb.append(elapsedTime)
                    sb.append(delimiter)

                    //Interval
                    var interval = 0.0
                    if (i == 0) {
                        sb.append(0)
                    } else {
                        val prevElapsedTime = source.value[i - 1].timeAcquired.toDouble() - timeStoreStarted
                        interval = elapsedTime - prevElapsedTime
                        sb.append(df.format(interval))
                    }
                    sb.append(delimiter)

                    //Frequency
                    if (interval == 0.0) {
                        sb.append(0.0)
                    } else {
                        val frequency = df.format(1 / (interval / 1000.0))
                        sb.append(frequency)
                    }
                    sb.append(delimiter)

                    //Data point number
                    sb.append(source.value[i].dataPointNumber)
                    sb.append(delimiter)

                    //ROI values
                    sb.append(source.value[i].roiVal)
                    sb.append(delimiter)
                }
            }

            sb.append("\n")
        }

        //This will remove a trailing carriage return and comma
        sb.setLength(sb.length - 2).toString()

        val timeStamp = ZonedDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME).replace(":", "-")

        try {
            val fileWriter = FileWriter("${Paths.EXPERIMENT_OUTPUT_FOLDER}/${Paths.TRACE_DATA_PREFIX}_$timeStamp.csv")
            fileWriter.write(sb.toString())
            fileWriter.close()
        } catch (ex: Exception) {
            GUIMain.loggerService.log(
                Level.SEVERE,
                "Could not write trace output to file. Error message: ${ex.message}"
            )
            GUIMain.loggerService.log(Level.SEVERE, ex.stackTrace)
        }
    }

    /**
     * This method builds the string for the file and then uses the fileWriter to write to file
     * @param fileWriter The FileWriter object
     * @param exportSettings The export settings for all series that will be exported
     */
    private fun exportStringToFile(fileWriter: FileWriter, exportSettings: ExportSettings) {
        val sb = StringBuilder()
        val delimiterChar = exportSettings.delimiter.toString().toCharArray()

        for (series in exportSettings.seriesExport) {
            sb.append(series.key.seriesName + "_" + "Times" + exportSettings.delimiter + series.key.seriesName + "_" + "Values" + exportSettings.delimiter)
        }

        sb.append("\n")

        val allXSeries = exportSettings.seriesExport.keys.map { x -> x.series.data }
        val maxSize = allXSeries.maxBy { x -> x.size }!!.size
        var rowCounter = 0
        while (rowCounter <= maxSize) {
            for (series in exportSettings.seriesExport) {
                val from = series.value.first
                val to = series.value.second
                if (rowCounter <= series.key.series.data.size - 1 &&
                    (series.key.series.data[rowCounter].xValue.toDouble() >= from) && (series.key.series.data[rowCounter].xValue.toDouble() <= to)
                ) {
                    sb.append(series.key.series.data[rowCounter].xValue.toString() + exportSettings.delimiter + series.key.series.data[rowCounter].yValue.toString() + exportSettings.delimiter)
                } else {
                    sb.append(exportSettings.delimiter + exportSettings.delimiter)
                }
            }

            sb.append("\n")
            rowCounter++
        }

        val finalString = sb.toString()
        finalString.trimEnd(delimiterChar[0])
        fileWriter.write(finalString)
        fileWriter.close()
    }


    var count = 0.toInt()
    fun writeImageDataTmp(xDim: Long, yDim: Long, shortData: ShortArray) {
        val datasetName = "tempDataset"
        var dataset = GUIMain.datasetService.create(
            UnsignedShortType(),
            longArrayOf(xDim, yDim),
            datasetName,
            arrayOf(Axes.X, Axes.Y)
        )
        dataset.setPlane(0, shortData)
        GUIMain.datasetIOService.save(
            dataset,
            "ExperimentResults\\tmpData\\" + count.toString() + ".tiff"
        )
        count++
    }

    fun writeImageAsTiffByte(xDim: Long, yDim: Long, Data: ByteArray, count: Int) {
        val datasetName = "tempDataset"
        var dataset = GUIMain.datasetService.create(
            UnsignedByteType(),
            longArrayOf(xDim, yDim),
            datasetName,
            arrayOf(Axes.X, Axes.Y)
        )

        dataset.setPlane(0, Data)
        GUIMain.datasetIOService.save(
            dataset,
            "ExperimentResults\\tmpData\\" + count.toString() + ".tiff"
        )
    }
    fun writeImageAsTiffShort(xDim: Long, yDim: Long, Data: ShortArray, count: Int) {
        val datasetName = "tempDataset"
        var dataset = GUIMain.datasetService.create(
            UnsignedShortType(),
            longArrayOf(xDim, yDim),
            datasetName,
            arrayOf(Axes.X, Axes.Y)
        )

        dataset.setPlane(0, Data)
        GUIMain.datasetIOService.save(
            dataset,
            "ExperimentResults\\tmpData\\" + count.toString() + ".tiff"
        )
    }
    fun writeImageAsTiff24Bit(xDim: Long, yDim: Long, Data: ByteArray, count: Int) {
        val datasetName = "tempDataset"
        val newData = ByteArray((xDim * yDim).toInt())
        for (f in 0 .. (newData.size - 1)){
            newData[f] = 255.toByte()
        }

        var cnt = 0
        var pixel = 0.0
        for (f in 0 .. (Data.size-5) step 4){
            pixel = ((Data[f].toInt() and 0xff).toDouble()+ (Data[f+1].toInt() and 0xff).toDouble() + (Data[f+2].toInt() and 0xff).toDouble())/3.0
            if (pixel > 255) pixel = 255.0
            else if (pixel < 0) pixel = 0.0

            newData[cnt] = pixel.toInt().toByte()
            cnt++
        }


        var dataset = GUIMain.datasetService.create(
            UnsignedByteType(),
            longArrayOf(xDim, yDim),
            datasetName,
            arrayOf(Axes.X, Axes.Y)
        )

        dataset.setPlane(0, newData)
        GUIMain.datasetIOService.save(
            dataset,
            "ExperimentResults\\tmpData\\" + count.toString() + ".tiff"
        )
    }




    fun writeImageDataTmp1(xDim : Long, yDim : Long, shortData : ShortArray, count : Int){
        val datasetName = "tempDataset"
        var dataset = GUIMain.datasetService.create(
                UnsignedShortType(),
                longArrayOf(xDim, yDim),
                datasetName,
                arrayOf(Axes.X, Axes.Y)
        )
        dataset.setPlane(0, shortData)
        GUIMain.datasetIOService.save(
                dataset,
                "ExperimentResults\\tmpData\\" + count.toString() + ".tiff"
        )

    }
    var camCnt = mutableMapOf<String, Int>()
    fun writeImageData(xDim : Long, yDim : Long, data : Any, bitDepth : Long, szCamera : String){
        var count = 0
        if (camCnt.containsKey(szCamera)){
            count  = camCnt[szCamera]!!.toInt() + 1
            camCnt[szCamera] = count
        }
        else{
            camCnt[szCamera] = 0
        }
        val datasetName = "tempDataset" + szCamera
        if (bitDepth == 1L) {
            var dataset = GUIMain.datasetService.create(
                    UnsignedByteType(),
                    longArrayOf(xDim, yDim),
                    datasetName,
                    arrayOf(Axes.X, Axes.Y)
            )
            dataset.setPlane(0, data)
            GUIMain.datasetIOService.save(
                    dataset,
                    "ExperimentResults\\" + szCamera + "\\" + count.toString() + ".tiff"
            )
        }
        else if (bitDepth == 2L){
            var dataset = GUIMain.datasetService.create(
                    UnsignedShortType(),
                    longArrayOf(xDim, yDim),
                    datasetName,
                    arrayOf(Axes.X, Axes.Y)
            )
            dataset.setPlane(0, data)
            GUIMain.datasetIOService.save(
                    dataset,
                    "ExperimentResults\\" + szCamera + "\\" + count.toString() + ".tiff"
            )
        }

        count++
    }



}