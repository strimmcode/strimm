package uk.co.strimm.experiment

class ExperimentConfiguration{
    var author = ""
    var affiliation = ""
    var description = ""
    var experimentMode = "Preview"
    var experimentConfigurationName = ""
    var experimentDurationMs = 0

    var isGlobalStart = false
    var GlobalStartVirtualCode = 0

    var TerminateAcquisitionVirtualCode = 0;

    var MMDeviceConfigFile = ""
    var ROIAdHoc = ""
    var HDF5Save = false
    var COMPort = 9
    var NIDAQ = DAQDevice()
    var hardwareDevices = HardwareDevices()
    var sourceConfig = Sources()
    var flowConfig = Flows()
    var sinkConfig = Sinks()
    var roiConfig = ROIs()

}