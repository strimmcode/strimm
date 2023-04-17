package uk.co.strimm.services

import akka.NotUsed
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.pattern.PatternsCS
import akka.stream.javadsl.RunnableGraph
import com.google.gson.GsonBuilder
import net.imagej.ImageJService
import org.scijava.plugin.Plugin
import org.scijava.service.AbstractService
import org.scijava.service.Service
import uk.co.strimm.RoiInfo
import uk.co.strimm.actors.messages.stop.TerminateActor
import uk.co.strimm.actors.messages.tell.TellSaveDatasets
import uk.co.strimm.experiment.*
import uk.co.strimm.gui.*
import uk.co.strimm.streams.ExperimentStream
import java.io.File
import java.io.FileReader
import java.util.concurrent.CompletionStage
import java.util.logging.Level

@Plugin(type = Service::class)
class ExperimentService  : AbstractService(), ImageJService {
    var expConfig = ExperimentConfiguration() // a class manifestation of the elements in the JSON eg Sink, Flow, Source objects
    private val gson = GsonBuilder().setPrettyPrinting().create() //allows JSONs to be turned into expConfig
    var loadedConfigurationStream: RunnableGraph<NotUsed>? = null //handle to the runnable graph (which means that it can be started)
    var isStreamLoaded = false //stream graph is now loaded
    var isDataStored = false //flag to say is this json needs to save data
    var loadedExperimentConfigurationFile: File? = null //File reference for the currently selected/loaded JSON
    var EndExperimentAndSaveData = false //flag used to trigger the saving of data in acquisition mode. Datastore have a function CheckIfIShouldStop() which used this flag.
    lateinit var experimentStream: ExperimentStream //performs the akka-level tasks


    var loadtimeRoiList = hashMapOf<String, List<RoiInfo>>()
    var runtimeRoiList = hashMapOf<String, List<RoiInfo>>()




    //convertGsonToConfig()    destroy the existing stream, capture the configFile, then load the expConfig from the JSON
    fun convertGsonToConfig(configFile: File): Boolean {
        destroyStream()
        loadedExperimentConfigurationFile = configFile
        return try {
            expConfig = gson.fromJson(FileReader(configFile), ExperimentConfiguration::class.java)
            true
        } catch (ex: Exception) {
            GUIMain.loggerService.log(
                Level.SEVERE,
                "Failed to load experiment configuration ${configFile.absolutePath}, check file is present and syntax is correct"
            )
            GUIMain.loggerService.log(Level.SEVERE, "Error message: ${ex.message}")
            false
        }
    }
    //createExperimentStream()     create the experimentStream - which contols akka components, also init protocolService
    fun createExperimentStream() {
        experimentStream = ExperimentStream(expConfig)
    }
    //createStreamGraph()    use experimentStream to createStream from expConfig, flags to show stream is loaded, and that data needs to be stored
    fun createStreamGraph(): Boolean {
        //Shutdown Arduino ports which may have been left open when a previous graph automatically loaded - but was not run
        GUIMain.protocolService.ARDUINO_Shutdown_All()


        //
        val streamGraph = experimentStream.createStream(expConfig)
        return if (streamGraph != null) {
            loadedConfigurationStream = streamGraph
            isStreamLoaded = true
            //TODO is this needed?
            isDataStored =
                experimentStream.cameraDataStoreActors.keys.size != 0 || experimentStream.traceDataStoreActors.keys.size != 0
            true
        } else {
            GUIMain.loggerService.log(Level.SEVERE, "Failed to create Experiment stream")
            isStreamLoaded = false
            false
        }
    }
    //runStream()   set the AQM::bCamerasAcquire so all sources start, then runStream in experimentStream()
    fun runStream() {
       // GUIMain.acquisitionMethodService.bCamerasAcquire = true
        experimentStream.runStream()
    }
    //stopStream()   set AQM::bCamerasAquire to false to stop sources. Switch the killswitch, set experimentStream::isRunning to false
    //stop the acquisition of each camera and destroyStream() - which will destroy all of the actors and docking windows.
    fun stopStream(): Boolean {
        var future =  PatternsCS.ask(GUIMain.actorService.fileManagerActor, TellSaveDatasets(), 500000) as CompletionStage<Unit>
        future.toCompletableFuture().get()

        return try {
            GUIMain.experimentService.experimentStream.isRunning = false
            //specific shutdown behaviour
            experimentStream.sourceMethods.values.forEach{
                it.postStop()
            }
            experimentStream.flowMethods.values.forEach{
                it.postStop()
            }
            experimentStream.sinkMethods.values.forEach{
                it.postStop()
            }

            destroyStream()
            true
        } catch (ex: Exception) {
            GUIMain.loggerService.log(Level.SEVERE, "Error in stopping Akka stream")
            GUIMain.loggerService.log(Level.SEVERE, ex.stackTrace)
            false
        }
    }
    //destroyStream()    if isStreamLoaded, destroy camera, trace, histogram etc actors along with datastoe actors.
    //destroy fileWriter and main actor, close all docking windows, and then close the actorSystem.
    //Closing the actorSystem will also shutdown any akka-stream components. The flag isStreamLoaded is then set to false.
    fun destroyStream() {

        if (isStreamLoaded) {

            GUIMain.experimentService.experimentStream.cameraActors.forEach { x ->
                x.key.tell(TerminateActor(), ActorRef.noSender())
            }
            GUIMain.actorService.allActors.keys.removeAll(GUIMain.experimentService.experimentStream.cameraActors.keys)
            GUIMain.actorService.mainActor.tell(TerminateActor(), ActorRef.noSender())
            GUIMain.actorService.removeActor(GUIMain.actorService.mainActor)
            GUIMain.actorService.allActors.forEach { x ->
                x.key.tell(TerminateActor(), ActorRef.noSender())
            }
            GUIMain.actorService.allActors.keys.removeAll(GUIMain.actorService.allActors.keys)
            val cameraWindowPlugins =
                GUIMain.dockableWindowPluginService.getPluginsOfType(CameraWindowPlugin::class.java)
            cameraWindowPlugins.forEach { x -> x.value.close() }
            val traceWindowPlugins =
                GUIMain.dockableWindowPluginService.getPluginsOfType(TraceWindowPlugin::class.java)
            traceWindowPlugins.forEach { x -> x.value.close() }
            val histogramWindowPlugins =
                GUIMain.dockableWindowPluginService.getPluginsOfType(HistogramWindowPlugin::class.java)
            histogramWindowPlugins.forEach { x -> x.value.close() }
            if (GUIMain.actorService.actorSystem != null) {
                val actorSystem = GUIMain.actorService.actorSystem as ActorSystem
                actorSystem.terminate()
            }
            isStreamLoaded = false
        }

    }

}