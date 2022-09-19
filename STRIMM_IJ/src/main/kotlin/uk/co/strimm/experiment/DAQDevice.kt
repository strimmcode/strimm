package uk.co.strimm.experiment

class DAQDevice {
    var deviceLabel = ""
    var deviceName = "Dev"
    var deviceID = 0
    var protocolName: String = ""
    var bCompound = false
    var pFIx = 0
    var bStartTrigger = false
    var bRisingEdge = true
    var timeoutSec = -1.0
    var bRepeat = false
    var minV = 0.0
    var maxV = 0.0
    var timingMethod = 0
    var virtualChannels = arrayListOf<VirtualChannel>()
}