


package uk.co.strimm.flowMethods

import akka.actor.ActorRef
import com.google.common.math.IntMath
import com.opencsv.CSVReader
import mmcorej.CMMCore
import mmcorej.DoubleVector
import mmcorej.StrVector
import uk.co.strimm.STRIMMBuffer
import uk.co.strimm.STRIMMPixelBuffer
import uk.co.strimm.STRIMMXYStageBuffer
import uk.co.strimm.STRIMM_MMCommandBuffer
import uk.co.strimm.experiment.Flow
import uk.co.strimm.gui.GUIMain
import java.io.FileReader

//TODO Raw MicroManager Z stage
//Raw flows - are flows which take a string instruction with parameters
//which is then used to call, directly one of the MicroManager functions
//
//to be used where STRIMM is providing wrapper functionality

//TODO test this code


class MMZStageFlowRaw() : FlowMethod {



    open lateinit var flow: Flow
    override lateinit var properties: HashMap<String, String>
    override var actor: ActorRef? = null
    var name = ""
    var library = ""
    var label = ""
    var core : CMMCore? = CMMCore()

    var dataID : Int = 0

    override fun init(flow: Flow) {
        this.flow = flow
        loadCfg()
        val st = StrVector()
        st.add("./DeviceAdapters/")
        try{
            println("load ZStage ******************")
            core!!.setDeviceAdapterSearchPaths(st)
            core!!.loadSystemConfiguration("./DeviceAdapters/ZStageMMConfigs/" + properties["MMDeviceConfig"]) // MMDeviceConfig, Ximea.cfg#
            label = core!!.getXYStageDevice()
            name = core!!.getDeviceName(label)
            library = core!!.getDeviceLibrary(label)

        } catch(ex : Exception){
            println("Exception: !" + ex.message)
        }

    }

    override fun run(data: List<STRIMMBuffer>): STRIMMBuffer {

        var status = 1
        var ret = mutableListOf<Any>()
        var dat = data[0] as STRIMM_MMCommandBuffer

        if (dat.szCommand == "setPosition"){
            setPosition(dat.data as Double)
        }
        else if (dat.szCommand == "setRelativePosition"){
            setRelativePosition(dat.data as Double)
        }
        else if (dat.szCommand == "setOrigin"){
            setOrigin()
        }
        else if (dat.szCommand == "getPosition"){
            val pos = getPosition()
            ret.add(pos)
        }
        else if (dat.szCommand == "setAdapterOrigin"){
            setAdapterOrigin(dat.data as Double)
        }
        else if (dat.szCommand == "setFocusDirection"){
            setFocusDirection(dat.data as Int)

        }
        else if (dat.szCommand == "getFocusDirection "){
            val dir = getFocusDirection()
            ret.add(dir)
        }
        else if (dat.szCommand == "isStageSequenceable"){
            val bSequenceable = isStageSequenceable()
            ret.add(bSequenceable)
        }
        else if (dat.szCommand == "isStageLinearSequenceable"){
            val bLSequenceable = isStageLinearSequenceable()
            ret.add(bLSequenceable)

        }
        else if (dat.szCommand == "startStageSequence"){
            startStageSequence()
        }
        else if (dat.szCommand == "stopStageSequence"){
            stopStageSequence()
        }
        else if (dat.szCommand == "getStageSequenceMaxLength"){
            val maxLength = getStageSequenceMaxLength()
            ret.add(maxLength)
        }
        else if (dat.szCommand == "loadStageSequence"){
            loadStageSequence(dat.data as DoubleArray)
        }
        else if (dat.szCommand == "setStageLinearSequence"){
            val anyData = dat.data as List<Any>
            setStageLinearSequence(anyData[0] as Double, anyData[1] as Int)
        }
        else{
            println("command not recognised")
            status = 0
        }
        dataID++

        return STRIMM_MMCommandBuffer("Result", ret, dataID, status)

    }

    fun loadCfg() {
        properties = hashMapOf<String, String>()
        if (flow.flowCfg != "") {

            var r: List<Array<String>>? = null
            try {
                CSVReader(FileReader(flow.flowCfg)).use { reader ->
                    r = reader.readAll()
                    for (props in r!!) {
                        //specific properties are read from Cfg
                        // "intervalMs" : "10.0"  etc
                        properties[props[0]] = props[1]
                    }
                }
            } catch (ex: Exception) {
                println(ex.message)
            }
        }
        else{

        }

    }
    override fun preStart(){

    }
    override fun postStop(){

        core!!.reset()
        Thread.sleep(2000)  //TODO does it need this?
    }




    fun setPosition(z : Double){
        //Sets the position of the stage in microns.
        core!!.setPosition(label, z)
    }

    fun setRelativePosition(dz : Double){
        //Sets the relative position of the stage in microns.
        core!!.setRelativePosition(label, dz)
    }

    fun setOrigin(){
        //Zero the given focus/Z stage's coordinates at the current position.
        //The current position becomes the new origin (Z = 0).
        core!!.setOrigin(label)
    }

    fun getPosition () : Double{
        //Returns the current position of the stage in microns.
        return (core!!.getPosition(label))
    }

    fun setAdapterOrigin (newZUm : Double){
        //Enable software translation of coordinates for the current focus/Z stage.
        //The current position of the stage becomes Z = newZUm. Only some stages support this functionality; it is recommended that setOrigin() be used instead where available.
        core!!.setAdapterOrigin(label, newZUm)
    }

    fun setFocusDirection ( sign : Int ){
        //Set the focus direction of a stage.
        //The sign should be +1 (or any positive value), zero, or -1 (or any negative value)
        core!!.setFocusDirection(label, sign)
    }

    fun  getFocusDirection () : Int{
        //Get the focus direction of a stage.
        //Returns +1 if increasing position brings objective closer to sample, -1 if increasing position moves objective away from sample, or 0 if unknown. (Make sure to check for zero!)
        //The returned value is determined by the most recent call to setFocusDirection() for the stage, or defaults to what the stage device adapter declares (often 0, for unknown)
        return core!!.getFocusDirection(label)
    }

    fun isStageSequenceable () : Boolean{
        //Queries stage if it can be used in a sequence
        return core!!.isStageSequenceable(label)
    }

    fun isStageLinearSequenceable () : Boolean{
        //Queries if the stage can be used in a linear sequence A linear sequence is defined by a stepsize and number of slices
        return core!!.isStageLinearSequenceable(label)
    }

    fun startStageSequence (){
        //Starts an ongoing sequence of triggered events in a stage This should only be called for stages
        core!!.startStageSequence(label)
    }

    fun stopStageSequence () {
        //Stops an ongoing sequence of triggered events in a stage This should only be called for stages that are sequenceable
        core!!.stopStageSequence(label)
    }

    fun getStageSequenceMaxLength () : Int{
        //Gets the maximum length of a stage's position sequence. This should only be called for stages that are sequenceable
        return core!!.getStageSequenceMaxLength(label)
    }

    fun loadStageSequence (positionSequence : DoubleArray){
        //Transfer a sequence of events/states/whatever to the device This should only be called for device-properties that are sequenceable

        var dv = DoubleVector(positionSequence.size.toLong())
        for (f in 0..positionSequence.size-1){
            dv[f] = positionSequence[f]
        }
        core!!.loadStageSequence(label, dv)
    }

    fun setStageLinearSequence (dZ_um : Double, nSlices : Int) {
        //Loads a linear sequence (defined by stepsize and nr. of steps) into the device.
        core!!.setStageLinearSequence(label, dZ_um, nSlices)
    }


}

