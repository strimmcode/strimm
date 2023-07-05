
package uk.co.strimm.flowMethods

import akka.actor.ActorRef
import com.google.common.math.IntMath
import com.opencsv.CSVReader
import mmcorej.CMMCore
import mmcorej.StrVector
import uk.co.strimm.STRIMMBuffer
import uk.co.strimm.STRIMMPixelBuffer
import uk.co.strimm.STRIMMXYStageBuffer
import uk.co.strimm.experiment.Flow
import uk.co.strimm.gui.GUIMain
import java.io.FileReader

//MicroManager XYStage - using MMCore
//TODO test this code

class MMXYStageFlow() : FlowMethod {



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
            println("load XYStage ******************")
            core!!.setDeviceAdapterSearchPaths(st)
            core!!.loadSystemConfiguration("./DeviceAdapters/XYStageMMConfigs/" + properties["MMDeviceConfig"]) // MMDeviceConfig, Ximea.cfg#
            label = core!!.getXYStageDevice()
            name = core!!.getDeviceName(label)
            library = core!!.getDeviceLibrary(label)

        } catch(ex : Exception){
            println("Exception: !" + ex.message)
        }

    }

    override fun run(data: List<STRIMMBuffer>): List<STRIMMBuffer> {
        var status = 1
        var dat = data[0] as STRIMMXYStageBuffer
        if (dat.szCommand == "setXYPosition"){
            setXYPosition(dat.data[0], dat.data[1])
        }
        else if (dat.szCommand == "setRelativeXYPosition"){
            setRelativeXYPosition(dat.data[0], dat.data[1])
        }
        else if (dat.szCommand == "stop"){
            stop()
        }
        else if (dat.szCommand == "home"){
            home()
        }
        else if (dat.szCommand == "setOriginX"){
            setOriginX()
        }
        else if (dat.szCommand == "setOriginY"){
            setOriginY()

        }
        else{
            println("command not recognised")
            status = 0
        }
        dataID++
        //the buffer emitted once the command has finished (synchronous)
        return listOf(STRIMMBuffer(dataID, status))
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

    fun setXYPosition(x : Double, y : Double){
        core!!.setXYPosition(label, x, y)
    }

    fun setRelativeXYPosition(dx : Double, dy : Double){
        core!!.setRelativeXYPosition(label, dx, dy)
    }

    fun getXPosition() : Double {
        return core!!.getXPosition(label)
    }

    fun getYPosition() : Double {
        return core!!.getYPosition(label)
    }

    fun stop(){
        core!!.stop(label)
    }

    fun home(){
        core!!.home(label)
    }

    fun setOriginX(){
        core!!.setOriginX(label)
    }

    fun setOriginY(){
        core!!.setOriginY(label)
    }



}