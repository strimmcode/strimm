package uk.co.strimm.services

import net.imagej.ImageJService
import org.scijava.plugin.Plugin
import org.scijava.service.AbstractService
import org.scijava.service.Service
import uk.co.strimm.gui.GUIMain

@Plugin(type = Service::class)
/**
 * Use the setFirstTimeMeasurement() just prior to running the stream then all uses of getTime() will give the time in
 * sec from this initial time.
 *
 * The protocolService and the JNI dll uses its own timing based on when the daq started to run the protocol - so there
 * will be a difference between these 0s.  The DAQ has electronic timing and should be regarded as superior and also
 * used to callibrate other events.
 */
class SoftwareTimerService : AbstractService(), ImageJService {
    var startTime = 0.0

    //TODO Conceptually it doesn't make to have this method here as this service is intended only for software timing mode related functions
    fun setFirstTimeMeasurement(){
            startTime = GUIMain.protocolService.GetCurrentSystemTime()
    }

    fun getTime() : Double {
        return GUIMain.protocolService.GetCurrentSystemTime() - startTime
    }
}