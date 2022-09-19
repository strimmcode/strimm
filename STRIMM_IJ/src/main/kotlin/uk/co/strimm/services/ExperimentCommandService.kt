package uk.co.strimm.services

import akka.actor.ActorRef
import net.imagej.ImageJService
import org.scijava.plugin.AbstractPTService
import org.scijava.plugin.Plugin
import org.scijava.plugin.PluginInfo
import org.scijava.service.Service
import uk.co.strimm.gui.GUIMain
import uk.co.strimm.plugins.ExperimentCommandPlugin
import java.util.logging.Level

@Plugin(type = Service::class)
class ExperimentCommandPluginService : AbstractPTService<ExperimentCommandPlugin>(), ImageJService {
    override fun getPluginType(): Class<ExperimentCommandPlugin> = ExperimentCommandPlugin::class.java

    fun <P : ExperimentCommandPlugin?>createPlugin(pluginClass : Class<P>) : P{
        val plugin = create(pluginClass)
        if (plugin != null) {
            val mainActor = GUIMain.actorService.createStrimmActorIfNotExists()
            plugin.initialise()
            tellMainActor(mainActor,plugin)
        }
        else{
            GUIMain.loggerService.log(Level.WARNING, "Failed to create plugin $pluginClass!")
        }

        return plugin
    }

    /**
     * This method will tell the main actor to create an associated actor with the experiment command plugin being created.
     * To determine this, it will need to be sent the right kind of ActorMessage e.g. a CreateTraceActorMessage. Which
     * class to use is determined by the hash map "actorCreateMessages" in the actor service
     * @param mainActor The main STRIMM actor
     * @param plugin The experiment command plugin being created
     */
    private fun tellMainActor(mainActor : ActorRef, plugin : ExperimentCommandPlugin){
        val messageClass = GUIMain.actorService.actorCreateMessages[plugin::class.java]
        if(messageClass != null) { //Not all classes need an actor associated with them
            val constructors = messageClass!!.constructors
            val newInstance = constructors[0].newInstance(plugin)
            mainActor.tell(newInstance, mainActor)
        }
    }

    var pluginInfoMap = hashMapOf<Class<out ExperimentCommandPlugin>, PluginInfo<ExperimentCommandPlugin>>()
        private set


    override fun initialize() {
        pluginInfoMap = hashMapOf(*plugins.map { info -> Pair(info.pluginClass, info) }.toTypedArray())
    }
}