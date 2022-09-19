package uk.co.strimm.experiment

class Flow{
    //this object is designed to place itself in the correct location in the akka graph
    //but does not deal with the functionality of the object
    //could either subclass it with a run function of some sort
    //the base flow would just do nothing, or if it receives a list of flows send through the 1st
    //all of the processing would be in thr subclass from Flow
    //this would be similar to Camera and CameraConfigured
    //
    //alternatively there could be a FlowProcess object which can be subclassed which is loaded into the
    //flow-these could be chained to deal with regular functionaslity, they are functions but they might need data.

    var flowName = ""
    var flowType = ""
    var flowDetails = ""
    var param1 = 0.0
    var param2 = 0.0
    var param3 = 0.0
    var param4 = 0.0
    var param5 = 0.0
    var param6 = 0.0
    var param7 = 0.0
    var param8 = 0.0
    var roiNumber = 0
    var description = ""
    var inputNames = arrayListOf<String>()
    var inputType = ""
    var outputType = ""
}