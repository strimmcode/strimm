

package uk.co.strimm.flowMethods

import akka.actor.ActorRef
import com.google.common.math.IntMath
import com.opencsv.CSVReader
import mmcorej.CMMCore
import mmcorej.StrVector
import uk.co.strimm.STRIMMBuffer
import uk.co.strimm.STRIMMPixelBuffer
import uk.co.strimm.STRIMMXYStageBuffer
import uk.co.strimm.STRIMM_MMCommandBuffer
import uk.co.strimm.experiment.Flow
import uk.co.strimm.gui.GUIMain
import java.io.FileReader

//TODO Raw MicroManager SLM
//Raw flows - are flows which take a string instruction with parameters
//which is then used to call, directly one of the MicroManager functions
//
//to be used where STRIMM is providing wrapper functionality
//
//TODO test this code

class MMSLMFlowRaw () : FlowMethod {



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
            println("load SLM ******************")
            core!!.setDeviceAdapterSearchPaths(st)
            core!!.loadSystemConfiguration("./DeviceAdapters/SLMStageMMConfigs/" + properties["MMDeviceConfig"]) // MMDeviceConfig, Ximea.cfg#
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
        if (dat.szCommand == "setSLMImage_Byte"){
            setSLMImage((dat.data) as ByteArray)
        }
        else if (dat.szCommand == "setSLMImage_RBG"){
            setSLMImage((dat.data) as IntArray)
        }
        else if (dat.szCommand == "setSLMPixelsTo_Byte"){
            setSLMPixelsTo((dat.data) as Short)
        }
        else if (dat.szCommand == "setSLMPixelsTo_RGB"){
            var cols = dat.data as ShortArray
            setSLMPixelsTo(cols[0], cols[1], cols[2])
        }
        else if (dat.szCommand == "displaySLMImage"){
            displaySLMImage()
        }
        else if (dat.szCommand == "setSLMExposure"){
            setSLMExposure(dat.data as Double)
        }
        else if (dat.szCommand == "getSLMWidth"){
            val width = getSLMWidth()
            ret.add(width)
        }
        else if (dat.szCommand == "getSLMHeight"){
            val height = getSLMHeight()
            ret.add(height)
        }
        else if (dat.szCommand == "getSLMNumberOfComponents"){
            val numComponents = getSLMNumberOfComponents()
            ret.add(numComponents)
        }
        else if (dat.szCommand == "getSLMBytesPerPixel"){
            val bytesPerPixel = getSLMBytesPerPixel()
            ret.add(bytesPerPixel)
        }
        else if (dat.szCommand == "getSLMSequenceMaxLength"){
            val seqMaxLength = getSLMSequenceMaxLength()
            ret.add(seqMaxLength)
        }
        else if (dat.szCommand == "startSLMSequence"){
            startSLMSequence()
        }
        else if (dat.szCommand == "stopSLMSequence"){
            stopSLMSequence()
        }
        else if (dat.szCommand == "loadSLMSequence"){
            loadSLMSequence(dat.data as List<ByteArray>)
        }
        else{
            println("SLM operation not supported")
            status = 0
        }
        dataID++
        //the buffer emitted once the command has finished (synchronous)
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

    }


    fun setSLMImage (pixelsByte : ByteArray) {
        //Write an 8-bit monochrome image to the SLM.
        core!!.setSLMImage(label, pixelsByte)

    }

    fun	setSLMImage (pixelsInt : IntArray) {
        //Write a 32-bit color image to the SLM.
        core!!.setSLMImage(label, pixelsInt)
    }

    fun setSLMPixelsTo (intensity : Short) {
        //Set all SLM pixels to a single 8-bit intensity.
        core!!.setSLMPixelsTo(label, intensity)
    }

    fun	setSLMPixelsTo (red : Short, green : Short, blue : Short) {
        //Set all SLM pixels to an RGB color.
        core!!.setSLMPixelsTo(label, red, green, blue)
    }

    fun	displaySLMImage() {
        //Display the waiting image on the SLM.
        core!!.displaySLMImage(label)
    }

    fun setSLMExposure (exposure_ms : Double) {
        //For SLM devices with build-in light source (such as projectors) this will set the exposure time, but not (yet) start the illumination
        core!!.setSLMExposure(label, exposure_ms)
    }

    fun	getSLMExposure() : Double
    {
        //Returns the exposure time that will be used by the SLM for illumination
        return core!!.getSLMExposure(label)
    }

    fun	getSLMWidth() : Long{
        //Returns the width (in "pixels") of the SLM
        return core!!.getSLMWidth(label)
    }

    fun	getSLMHeight() : Long{
        //Returns the height (in "pixels") of the SLM
        return core!!.getSLMHeight(label)
    }

    fun	getSLMNumberOfComponents() : Long{
        //Returns the number of components (usually these depict colors) of the SLM For instance, an RGB projector will return 3, but a grey scale SLM returns 1
        return core!!.getSLMNumberOfComponents(label)
    }

    fun getSLMBytesPerPixel() : Long
    {
        //Returns the number of bytes per SLM pixel
        return core!!.getSLMBytesPerPixel(label)
    }

    fun getSLMSequenceMaxLength() : Int
    {
        //For SLMs that support sequences, returns the maximum length of the sequence that can be uploaded to the device
        return core!!.getSLMSequenceMaxLength(label)
    }

    fun startSLMSequence () {
        //Starts the sequence previously uploaded to the SLM
        core!!.startSLMSequence(label)
    }

    fun stopSLMSequence () {
        //Stops the sequence previously uploaded to the SLM
        core!!.stopSLMSequence(label)
    }

    fun loadSLMSequence (imageSequence : List<ByteArray>){
        //Load a sequence of images into the SLM
        core!!.loadSLMSequence(label, imageSequence)
    }


}


