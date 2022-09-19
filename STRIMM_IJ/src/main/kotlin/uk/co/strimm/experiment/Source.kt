package uk.co.strimm.experiment


import uk.co.strimm.services.Camera
/**
 * This class represents any source in the experiment. It facilitates both image based and trace based sources
 */
class Source{
    var sourceName = ""
    var sourceType = ""
    var sourceCfg = ""
    var deviceLabel = ""
    var sourceDetails = ""
    var param1 = 0.0
    var param2 = 0.0
    var param3 = 0.0
    var param4 = 0.0
    var isGreyScale = true
    var description = ""
    var exposureMs = 0.0

    var isBusy = false


    var isTimeLapse = true
    var intervalMs = 0.0
    var isTriggered = true
    var isImageSnapped = true

    var isKeyboardSnapEnabled = false
    var SnapVirtualCode : Int = 0

    var framesInCircularBuffer = 20
    var timeLastCaptured = 0.0

    var previewInterval = 0.2


    var outputType = ""
    var samplingFrequencyHz = 0.0
    var x = 0.0
    var y = 0.0
    var w = 0.0
    var h = 0.0
    var channel = ""
    var sourceMMConfig = ""
    var camera : Camera? = null


}