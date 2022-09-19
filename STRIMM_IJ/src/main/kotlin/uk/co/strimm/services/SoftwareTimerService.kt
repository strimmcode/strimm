package uk.co.strimm.services

import net.imagej.ImageJService
import org.scijava.plugin.Plugin
import org.scijava.service.AbstractService
import org.scijava.service.Service
import uk.co.strimm.gui.GUIMain
import kotlin.concurrent.timer

@Plugin(type = Service::class)
//Use the setFirstTimeMeasurement() just prior to running the stream
//then all uses of getTime() will give the time in sec from this datum
//
//it might be better to think of 0 sec as when the timing started and close to when
//the stream started - what is meant by close
//
//the protocolService and the JNI dll uses its own timing based on when the daq started
//to run the protocol - so there will be a difference between these 0s.  The DAQ has electronic
//timing and should be regarded as superior and also used to callibrate other events - for example
//we could have a 010101010 output purely for timing
class SoftwareTimerService : AbstractService(), ImageJService {
    var startTime = 0.0 // stores the HPC datum value for 0 sec

    fun setFirstTimeMeasurement(){
            startTime = GUIMain.protocolService.GetCurrentSystemTime()
    }

    fun getTime() : Double {
        return GUIMain.protocolService.GetCurrentSystemTime() - startTime
    }
//    Java timer
//    fun currentTimeNano() = System.nanoTime()
}