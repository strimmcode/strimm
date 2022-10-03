package uk.co.strimm.services

import net.imagej.ImageJService
import org.scijava.plugin.Plugin
import org.scijava.service.AbstractService
import org.scijava.service.Service
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.time.LocalDate
import java.time.LocalTime
import java.util.ArrayList
import java.util.logging.FileHandler
import java.util.logging.Level
import java.util.logging.Logger
import java.util.logging.SimpleFormatter

@Plugin(type = Service::class)
class LoggerService : AbstractService(), ImageJService {

    private val logger = Logger.getLogger(LoggerService::class.java.name)
    var fh : FileHandler
//    private val logDirectory = "./log"
    val logFileLimit = 10

    init {
        //Create a timeAcquired stamped file name for the log file that's nearly ISO format
        val date = LocalDate.now()
        val time = LocalTime.now()
        val fileName = uk.co.strimm.Paths.LOG_FILE_DIRECTORY + "/" + date.toString() + "T" + time.withNano(0).toString().replace(":".toRegex(), "-") + ".log"

        val directory = File(uk.co.strimm.Paths.LOG_FILE_DIRECTORY)
        if(!directory.exists()){
            directory.mkdir()
        }

        //Add a file handler to the logger
        fh = FileHandler(fileName, true)
        fh.formatter = SimpleFormatter()
        logger.addHandler(fh)

        //Clear the log files so there are only a certain limit. Do this every timeAcquired the logger is initialised
        clearLogFiles()
    }

    fun log(level: Level, message : String){
        logger.log(level, message)
    }

    fun log(level: Level, stackTrace : Array<out StackTraceElement>){
        var stackTraceString = "Stack trace: "
        stackTrace.forEach { it -> stackTraceString += (it.toString() + "\n") }
        log(level,stackTraceString)
    }

    private fun clearLogFiles(){
        //Get all the log text files
        val files = File(uk.co.strimm.Paths.LOG_FILE_DIRECTORY).list()
        val justTextFiles = ArrayList<String>()
        var numLogFiles = 0
        for (i in files!!.indices) {
            if (!files!![i].contains(".lck") && files!![i].contains(".log")) {
                justTextFiles.add(files!![i])
                numLogFiles++
            }
        }

        //If there are more than 10 log files, delete the oldest ones
        if (numLogFiles > logFileLimit) {
            var fileCounter = 0
            while (numLogFiles > logFileLimit) {
                try {
                    Files.deleteIfExists(Paths.get(uk.co.strimm.Paths.LOG_FILE_DIRECTORY + "/" + justTextFiles[fileCounter]))
                    fileCounter++
                    numLogFiles--
                    println("Deleted a log file")
                } catch (e: IOException) {
                    println("Could not delete log file")
                    e.printStackTrace()
                    break
                }

            }
        }
    }
}