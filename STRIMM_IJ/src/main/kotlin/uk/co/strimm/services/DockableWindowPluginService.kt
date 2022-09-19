package uk.co.strimm.services

import akka.actor.ActorRef
import bibliothek.gui.dock.common.DefaultMultipleCDockable
import bibliothek.gui.dock.common.MultipleCDockableFactory
import bibliothek.gui.dock.common.MultipleCDockableLayout
import bibliothek.util.xml.XElement
import net.imagej.ImageJService
import org.scijava.plugin.AbstractPTService
import org.scijava.plugin.Plugin
import org.scijava.plugin.PluginInfo
import org.scijava.service.Service
import uk.co.strimm.ComponentTexts
import uk.co.strimm.gui.CameraWindowPlugin
import uk.co.strimm.gui.GUIMain
import uk.co.strimm.plugins.DockableWindowPlugin
import java.awt.Component
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.logging.Level

/*
 * TODO - comments
 */
@Plugin(type = Service::class)
class DockableWindowPluginService : AbstractPTService<DockableWindowPlugin>(), ImageJService {
    val multipleDockableFactory  = STRIMMDockableFactory()
    val dockableWindows = hashMapOf<String, DockableWindowPlugin>() //plugin title, plugin

    class STRIMMDockableLayout : MultipleCDockableLayout {
        override fun writeStream(p0: DataOutputStream?) {
            println("STRIMMDockableLayout writeStream")
            TODO("not implemented") //To change body of created functions use File | SettingsWindow | File Templates.
        }

        override fun readXML(p0: XElement?) {
            println("STRIMMDockableLayout readXML")
            TODO("not implemented") //To change body of created functions use File | SettingsWindow | File Templates.
        }

        override fun writeXML(p0: XElement?) {
            println("STRIMMDockableLayout writeXML")
            TODO("not implemented") //To change body of created functions use File | SettingsWindow | File Templates.
        }

        override fun readStream(p0: DataInputStream?) {
            println("STRIMMDockableLayout readStream")
            TODO("not implemented") //To change body of created functions use File | SettingsWindow | File Templates.
        }
    }

    class STRIMMDockableFactory : MultipleCDockableFactory<DefaultMultipleCDockable, STRIMMDockableLayout> {
        override fun match(p0: DefaultMultipleCDockable?, p1: STRIMMDockableLayout?): Boolean {
            println("STRIMMDockableFactory match")
            TODO("not implemented") //To change body of created functions use File | SettingsWindow | File Templates.
        }

        override fun write(p0: DefaultMultipleCDockable?): STRIMMDockableLayout {
            println("STRIMMDockableFactory write")
            TODO("not implemented") //To change body of created functions use File | SettingsWindow | File Templates.
        }

        override fun create(): STRIMMDockableLayout {
            println("STRIMMDockableFactory create")
            return STRIMMDockableLayout()
        }

        override fun read(p0: STRIMMDockableLayout?): DefaultMultipleCDockable {
            println("STRIMMDockableFactory read")
            TODO("not implemented") //To change body of created functions use File | SettingsWindow | File Templates.
        }
    }

    override fun getPluginType(): Class<DockableWindowPlugin> = DockableWindowPlugin::class.java

    fun getPluginsOfType(type : Class<out DockableWindowPlugin>) : HashMap<String, DockableWindowPlugin>{
        return HashMap(dockableWindows.filter { x -> x.value::class.java == type })
    }

    /**
     * This method will tell the main actor to create an associated actor with the dockable window plugin being created.
     * To determine this, it will need to be sent the right kind of ActorMessage e.g. a CreateTraceActorMessage. Which
     * class to use is determined by the hash map "actorCreateMessages" in the actor service
     * @param mainActor The main STRIMM actor
     * @param plugin The dockable window plugin being created
     */
    private fun tellMainActor(mainActor : ActorRef, plugin : DockableWindowPlugin){
        println("*TellMainActor")
        val messageClass = GUIMain.actorService.actorCreateMessages[plugin::class.java]
        if(messageClass != null) { //Not all classes need an actor associated with them
            val constructors = messageClass.constructors
            val newInstance = constructors[0].newInstance(plugin)
            //instructs the mainActor to make an Actor with this plugin
            mainActor.tell(newInstance, mainActor)
        }
    }

    /**
     * This method creates a dockable window plugin. This is usually used to programmatically create a plugin, usually
     * when there is some logic to run before actual creation
     * @param pluginClass Any class that derives from DockableWindowPlugin
     * @param data Any data to be given to the plugin itself
     * @param withActor Flag to say if this plugin should be created with an actor
     * @return The plugin of type DockableWindowPlugin
     */
    fun <P : DockableWindowPlugin?>createPlugin(pluginClass : Class<P>, data : Any?, withActor : Boolean, pluginTitle : String) : P{
//creates a plugin of type Class<P>


        //create and initialise the plugin
        //then create an actor with this plugin
        GUIMain.loggerService.log(
            Level.SEVERE,
            "TERRY :about to create(pluginClass)    " + pluginClass.toString()
        )
        val plugin = create(pluginClass) //factory scijava
        GUIMain.loggerService.log(
            Level.SEVERE,
            "TERRY :DID I GET HERE?" +
                    ""
        )
        if (plugin != null) {
            GUIMain.loggerService.log(
                Level.SEVERE,
                "TERRY :made a plugin"
            )
            val mainActor = GUIMain.actorService.createStrimmActorIfNotExists()
            GUIMain.loggerService.log(
                Level.SEVERE,
                "TERRY :made a main actor"
            )
            //will polymorphically call the correct type of plugin
            //so in the case of CameraPlugin will call these functions
            plugin.setCustomData(data)
            GUIMain.loggerService.log(
                Level.SEVERE,
                "TERRY :SetCustomData"
            )
            plugin.initialise()
            GUIMain.loggerService.log(
                Level.SEVERE,
                "TERRY :plugin.initialise"
            )
            plugin.title = pluginTitle
            //plugin now initialised
            GUIMain.loggerService.log(
                Level.SEVERE,
                "TERRY :made a plugin"
            )

            if(withActor) {
                //this creates the actor eg the CameraActor
                GUIMain.loggerService.log(
                    Level.SEVERE,
                    "TERRY :tellMainActor"
                )
                tellMainActor(mainActor, plugin)
                //this actor should now respond to its messages about its plugin
                //TODO not good to do this, creating an actor takes a bit of timeAcquired - need to wait a bit so it can be found by the stream later on
                Thread.sleep(750)
                GUIMain.loggerService.log(
                    Level.SEVERE,
                    "TERRY :should have created an actor"
                )
            }
        }
        else{
            GUIMain.loggerService.log(
                Level.SEVERE,
                "TERRY : failed to create a plugin"
            )
            GUIMain.loggerService.log(Level.WARNING, "Failed to create plugin $pluginClass!")
        }
        //add the dockableWindow to the register

        GUIMain.loggerService.log(
            Level.SEVERE,
            "TERRY : add to dockableWindows"
        )
        dockableWindows[pluginTitle] = plugin as DockableWindowPlugin

        return plugin
    }

    fun createPlugin(info : PluginInfo<DockableWindowPlugin>, data : Any?, withActor: Boolean) : DockableWindowPlugin?{

        val plugin = pluginService().createInstance(info)

        if (plugin != null) {
            if (!exists(plugin.title)) {
                val mainActor = GUIMain.actorService.createStrimmActorIfNotExists()
                plugin.setCustomData(data)
                plugin.initialise()

                if (withActor) {
                    tellMainActor(mainActor, plugin)
                }

                dockableWindows[plugin.title] = plugin as DockableWindowPlugin
                return plugin

            } else {
                pluginService().removePlugin(info)
            }

        }
        GUIMain.loggerService.log(Level.WARNING, "Failed to create plugin ${info.pluginClass}!")
        return null
    }

    /**
     *  Query if dockable window exists already using unique window title
     *  Used for single-instance dockable window controls to prevent opening of a second window
     *  @param dockableWindowName The title of the dockable item to be created
     *  @return
     */
    private fun exists(dockableWindowName : String) : Boolean {

        if (dockableWindowName == ComponentTexts.ExperimentBuilder.EXPERIMENT_BUILDER_WINDOW_TITLE
                || dockableWindowName == ComponentTexts.SettingsWindow.SETTINGS_WINDOW_TITLE) {

            return dockableWindows.any { x -> x.key == dockableWindowName }

        }
        return false
    }

    fun removeDockableWindowPlugin(pluginTitle : String){
        dockableWindows.remove(pluginTitle)
    }

    var pluginInfoMap = hashMapOf<Class<out DockableWindowPlugin>, PluginInfo<DockableWindowPlugin>>()
        private set

    override fun initialize() {
        pluginInfoMap = hashMapOf(*plugins.map { info -> Pair(info.pluginClass, info) }.toTypedArray())
    }
}