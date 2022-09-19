package uk.co.strimm.common

abstract class AbstractFileFormat

class TraceDataFileFormat : AbstractFileFormat() {
    val elapsedTimeColIndex = 1
    val frameNumberColIndex = 4
    val roiValueColIndex = 5
    val totalColsPerTrace = 6
}

class MetadataFileFormat : AbstractFileFormat(){
    //TODO put columns here
}