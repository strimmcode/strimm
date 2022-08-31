package uk.co.strimm.services

import net.imagej.ImageJService
import org.scijava.plugin.Plugin
import org.scijava.service.AbstractService
import org.scijava.service.Service
import uk.co.strimm.gui.GUIMain
import kotlin.concurrent.timer

@Plugin(type = Service::class)

class SoftwareTimerService : AbstractService(), ImageJService {
    var startTime = 0L // stores the HPC datum value for 0 sec
    fun setFirstTimeMeasurement(){
           startTime = System.nanoTime()/1000000;      //GUIMain.protocolService.GetCurrentSystemTime()
    }
    fun getTime() : Double {
       return (System.nanoTime()/1000000 - startTime).toDouble()
        //return 0.0
    }

}