package uk.co.strimm.services

import net.imagej.ImageJService
import org.scijava.plugin.Plugin
import org.scijava.service.AbstractService
import org.scijava.service.Service
import uk.co.strimm.gui.GUIMain
import java.util.*

@Plugin(type = Service::class)
class UtilsService : AbstractService(), ImageJService {
    fun sanitiseNameForPlugin(nameToSanitise: String) : String{
        return nameToSanitise.replace(")","")
                             .replace("(","")
                             .replace("-", "")
                             .replace(" ", "")

    }

    fun createDockableWindowTitleText(label : String, isCameraFeed : Boolean) : String{
        var titleToReturn = label
        titleToReturn += if (isCameraFeed){
            " camera feed"
        } else{
            "Trace feed" + Random().nextInt(1000000) //TODO get name from device and channel
        }
        return titleToReturn
    }
}