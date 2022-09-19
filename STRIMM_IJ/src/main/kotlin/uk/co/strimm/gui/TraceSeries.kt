package uk.co.strimm.gui

import javafx.scene.chart.XYChart
import net.imagej.overlay.Overlay

data class TraceSeries(var xLowerTimeIndex : Int = 0,
                       var xUpperTimeIndex : Int = 0,
                       var xLowerDataIndex : Int = 0,
                       var xUpperDataIndex : Int = 0,
                       var series : XYChart.Series<Number,Number>, //javaFx series
                       var frameNumbers : ArrayList<Int>,
                       val roi : Overlay?, // IJ overlay
                       val seriesName : String)