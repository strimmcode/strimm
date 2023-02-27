package uk.co.strimm.experiment


/**
 * This class represents any source in the experiment. It facilitates both image based and trace based sources
 */
class Source{
    var sourceName = ""
    var sourceType = ""
    var sourceCfg = ""

    var exposureMs = 0.0
    var isTimeLapse = true
    var intervalMs = 0.0
    var isTriggered = true
    var isImageSnapped = true

    var async = true

    var x = 0.0
    var y = 0.0
    var w = 0.0
    var h = 0.0

}