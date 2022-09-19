package uk.co.strimm.services

import net.imagej.ImageJService
import org.scijava.plugin.Plugin
import org.scijava.service.AbstractService
import org.scijava.service.Service
import com.google.gson.GsonBuilder
import java.io.FileReader
import uk.co.strimm.settings.*
import uk.co.strimm.Paths
import uk.co.strimm.gui.GUIMain
import java.io.File
import java.io.FileWriter
import java.util.logging.Level

@Plugin(type = Service::class)
class StrimmSettingsService : AbstractService(), ImageJService {
    var settings = Settings()
    private val path = Paths.SETTINGS_FILE_PATH
    private val gson = GsonBuilder().setPrettyPrinting().create()

    init {
        try {
            createSettingsFileIfNotExists()
            settings = gson.fromJson(FileReader(path),Settings::class.java)
        }
        catch(ex : Exception){
            println("Could not load settings file. Error ${ex.message}")
        }
    }

    fun createSettingsFileIfNotExists(){
        val file = File(path)

        if (!file.exists()) {
            println("SettingsWindow file does not exist, creating...")
            createSettingsFile(file.absolutePath)
        }
    }

    fun createSettingsFile(filePath : String){
        val gson = GsonBuilder().setPrettyPrinting().create()
        val writer = FileWriter(filePath)
        gson.toJson(Settings(), writer)
        writer.flush()
        writer.close()
        println("Created settings file")
    }

    fun getSettingValueByName(name : String) : String{
        for(setting in settings.generalSettings.settings){
            if(setting.name.toLowerCase() == name.toLowerCase()){
                return setting.value.toString()
            }
        }

        for(setting in settings.traceSettings.settings){
            if(setting.name.toLowerCase() == name.toLowerCase()){
                return setting.value.toString()
            }
        }

        for(setting in settings.cameraSettings.settings){
            if(setting.name.toLowerCase() == name.toLowerCase()){
                return setting.value.toString()
            }
        }

        println("Could not find setting")
        return ""
    }

    fun updateSetting(name : String, newValue : Any) : Boolean{
        for(setting in settings.traceSettings.settings){
            if(setting.name.toLowerCase() == name.toLowerCase()){
                GUIMain.loggerService.log(Level.INFO, "Updating setting ${setting.name}")
                setting.value = newValue
                return true
            }
        }

        GUIMain.loggerService.log(Level.INFO, "Could not find and update setting $name")
        return false
    }

    fun saveSettingsToFile(settingsToSave : Settings){
        GUIMain.loggerService.log(Level.INFO,"Saving settings to file")
        val writer = FileWriter(path)

        try {
            gson.toJson(settingsToSave, writer)
        }
        catch(ex: Exception){
            GUIMain.loggerService.log(Level.SEVERE, "Could not save settings to file. Error: ${ex.message}")
            GUIMain.loggerService.log(Level.SEVERE, ex.stackTrace)
        }
        finally{
            writer.flush()
            writer.close()
        }
    }
}