package uk.co.strimm.settings

import uk.co.strimm.SettingKeys

/**
 * Specify the default settings relating to the trace feed here.
 */
class TraceSettings : SettingGroup(SettingKeys.TraceSettings.GROUP_NAME){
    var settings = arrayListOf<Setting>()

    init {
        val traceRenderType = Setting()
        traceRenderType.name = "renderType"
        traceRenderType.displayName = "Default trace render type"
        traceRenderType.value = "2"
        traceRenderType.toolTip = "When you open a trace feed, this will be the render type used unless it is changed by the user"

        val xAxisLowerBound = Setting()
        xAxisLowerBound.name = "defaultXAxisLowerBound"
        xAxisLowerBound.displayName = "Default x axis lower bound"
        xAxisLowerBound.value = 0.0
        xAxisLowerBound.toolTip = "The lowest starting value of the x axis on the trace feed (seconds)"

        val xAxisUpperBound = Setting()
        xAxisUpperBound.name = "defaultXAxisUpperBound"
        xAxisUpperBound.displayName = "Default x axis upper bound"
        xAxisUpperBound.value = 3.0
        xAxisUpperBound.toolTip = "The highest starting value of the x axis on the trace feed (seconds)"

        val yAxisLowerBound = Setting()
        yAxisLowerBound.name = "defaultYAxisLowerBound"
        yAxisLowerBound.displayName = "Default y axis lower bound"
        yAxisLowerBound.value = 0.0
        yAxisLowerBound.toolTip = "The highest starting value of the y axis on the trace feed"

        val yAxisUpperBound = Setting()
        yAxisUpperBound.name = "defaultYAxisUpperBound"
        yAxisUpperBound.displayName = "Default y axis lower bound"
        yAxisUpperBound.value = 200.0
        yAxisUpperBound.toolTip = "The highest starting value of the y axis on the trace feed"

        val defaultXAxisNumPoints = Setting()
        defaultXAxisNumPoints.name = "defaultXAxisNumPoints"
        defaultXAxisNumPoints.displayName = "Default number of x axis points"
        defaultXAxisNumPoints.value = 500
        defaultXAxisNumPoints.toolTip = "Default number of points to display on the x axis"

        settings.add(traceRenderType)
        settings.add(xAxisLowerBound)
        settings.add(xAxisUpperBound)
        settings.add(yAxisLowerBound)
        settings.add(yAxisUpperBound)
        settings.add(defaultXAxisNumPoints)
    }
}