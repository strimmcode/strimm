package uk.co.strimm.services

import net.imagej.ImageJService
import org.scijava.plugin.Plugin
import org.scijava.service.AbstractService
import org.scijava.service.Service
import uk.co.strimm.gui.GUIMain

@Plugin(type = Service::class)
class DebugService : AbstractService(), ImageJService  {
    var bDebug = true
    //often useful for the code to say things at various points
    //as well as writing comments to the console
    fun State(sz : String){
        if (bDebug){
            GUIMain.protocolService.WinInitSpeechEngine()
            GUIMain.protocolService.WinSpeak(sz)
            println("DEBUGSERVICE: " + sz)
            GUIMain.protocolService.WinShutdownSpeechEngine()
        }
        else{

        }

    }

}