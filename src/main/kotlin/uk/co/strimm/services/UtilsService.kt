package uk.co.strimm.services

import net.imagej.ImageJService
import org.scijava.plugin.Plugin
import org.scijava.service.AbstractService
import org.scijava.service.Service
import uk.co.strimm.FileExtensions
import uk.co.strimm.gui.GUIMain
import java.io.File
import java.util.*
import java.util.logging.Level

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

    fun checkCSVFilePresent(path : String) : Boolean {
        val file = File(path)
        return if(file.exists() && file.extension == FileExtensions.CSV_FILE){
            GUIMain.loggerService.log(Level.INFO, ("Protocol CSV found at path $path"))
            true
        } else{
            GUIMain.loggerService.log(Level.SEVERE, ("Protocol CSV not found at path $path"))
            false
        }
    }

    fun changeSlashesInFilePath(path : String) : String{
        return path.replace("\\", "/")
    }
}