package uk.co.strimm.plugins

import akka.actor.Actor
import akka.actor.ActorRef
import bibliothek.gui.dock.common.*
import bibliothek.gui.dock.common.action.predefined.CCloseAction
import bibliothek.gui.dock.common.event.CFocusListener
import bibliothek.gui.dock.common.intern.CDockable
import bibliothek.gui.dock.common.intern.DefaultCDockable
import bibliothek.gui.dock.common.intern.DefaultCommonDockable
import bibliothek.gui.dock.station.split.SplitDockPathProperty
import bibliothek.gui.dock.station.split.SplitDockProperty
import net.imagej.ImageJPlugin
import uk.co.strimm.CAMERA_FEED_BACKGROUND_COLOUR
import uk.co.strimm.DockableWindowPosition
import uk.co.strimm.actors.messages.stop.DetatchController
import uk.co.strimm.gui.GUIMain
import uk.co.strimm.services.StrimmUIService
import java.awt.BorderLayout
import java.awt.Graphics
import java.awt.GridLayout
import java.awt.event.*
import java.util.logging.Level
import javax.swing.JFrame
import javax.swing.JPanel

/**
 * This class represents any dockable window plugin
 */
abstract class AbstractDockableWindow : DockableWindowPlugin {
    lateinit var windowPanel: JPanel
    val dockableFactoryId = "strimm-dockable-factory"
    var associatedActor : ActorRef? = null

    /**
     * In the docking frames framework, the grid is a static feature. It is meant to be a specification that doesn't
     * change once deployed. For STRIMM however, we need it to be more dynamic. Upon adding or removing a dockable
     * window, the grid will be updated to reflect this. It will keep the positions of the existing windows whilst
     * updating for a new addition/removal.
     * More info can be found at https://forum.byte-welt.net/t/common-finding-dockables-positioned-relative-to-dockable/1896/7
     * @param newDockable The new dockable to add to the grid, null if the redeployment follows a window being closed
     */
    override fun redeployGrid(newDockable : MultipleCDockable?) {
        //Go through the existing dockables, add them to a new grid

        val newGrid = CGrid(GUIMain.strimmUIService.dockableControl)
        if(GUIMain.strimmUIService.dockableControl.cDockableCount > 0) {
            for (i in 0 until GUIMain.strimmUIService.dockableControl.cDockableCount) {

                val currentDockable = GUIMain.strimmUIService.dockableControl.getCDockable(i)

                //Position only needs to be calculated for new dockables. Use the existing x,y,w,h properties for
                //the existing dockables
                if (currentDockable.baseLocation != null) {
                    var prop = currentDockable.baseLocation.findProperty()

                    if (prop is SplitDockPathProperty) {
                        prop = prop.toLocation()
                    }

                    if (prop is SplitDockProperty) {
                        newGrid.add(prop.x, prop.y, prop.width, prop.height, currentDockable)
                    }
                }
            }
        }

        if(newDockable != null){
            addNewDockableToGrid(newGrid, newDockable)
        }

        GUIMain.strimmUIService.dockableControl.contentArea.deploy(newGrid)
        GUIMain.strimmUIService.cGrid = newGrid
    }

    /**
     * Class so we can know which window is currently in focus based on it's title
     */
    class FocusListenerClass : CFocusListener{
        override fun focusLost(p0: CDockable?) {
        }

        override fun focusGained(p0: CDockable?) {
            val dockableWindow = p0!! as DefaultMultipleCDockable
            GUIMain.strimmUIService.currentFocusedDockableTitle = dockableWindow.titleText
        }
    }

    /**
     * This method calculates the position for the new dockable and then adds it to the new grid
     * @param newGrid The new dockable window grid
     * @param newDockable The new dockable window
     */
    private fun addNewDockableToGrid(newGrid : CGrid, newDockable: MultipleCDockable?){
        //Calculate the position of the new dockable and add it to the new grid
        val position = calculateDockablePosition(GUIMain.strimmUIService.dockableControl.cDockableCount)
        newGrid.add(position.x,position.y,position.width,position.height, newDockable)
    }

    /**
     * This method fills in new dockable windows into the grid. The grid dimensions are set to 2 x n.
     * Note - The x,y,width,height values are relative to a grid and NOT pixel values
     * @param currentNumDockables The current number of dockables (excludes new one)
     * @return A class representing the dockable window x,y,w,h values
     */
    private fun calculateDockablePosition(currentNumDockables : Int) : DockableWindowPosition{
        val x: Double
        val y: Double
        val w = 1.0
        val h = 2.0

        if(currentNumDockables % 2 == 0){ //First column
            x = 1.0
            y = currentNumDockables+1.toDouble()
        }
        else{ //Second column
            x = 2.0
            y = Math.ceil((currentNumDockables/2).toDouble())
        }

        //NOTE - Position values are relative and based on a grid
        return DockableWindowPosition(x,y,w,h)
    }

    /**
     * Custom close action to ensure when dockable windows are closed, they are properly deleted.
     * This then redeploys the grid to reflect the change
     * @param dockingControl The main docking frames control
     */
    inner class CustomCloseAction(dockingControl : CControl?) : CCloseAction(dockingControl) {
        override fun close(dockable: CDockable?) {
            GUIMain.loggerService.log(Level.INFO, "Closing multiple dockable window")
            val dockableFrame = dockable as DefaultMultipleCDockable

            //Detach the window controller from the actor
            associatedActor?.tell(DetatchController(), Actor.noSender())

            //TW 4_10_21 the next line causes a crash when changing experiment which has a different camera
            //removing it seems to stop the issue - but I dont know why.
            //dockableFrame.isVisible = false   //REMOVED 4_10_21


            super.close(dockable)
            GUIMain.strimmUIService.dockableControl.removeDockable(dockableFrame)
            GUIMain.dockableWindowPluginService.removeDockableWindowPlugin(dockableFrame.titleText)

            redeployGrid(null)
        }
    }

    /**
     * Create the dockable window. Create the panel within the dockable window.
     * @param title The title of the dockable window (must be unique)
     * @return The new dockable window
     */
    override fun createDock(title: String) : DefaultMultipleCDockable {
        windowPanel = JPanel()

        //Setting the layout to null prevents a bug where the image jolts when clicking/drawing an ROI
//        windowPanel.layout = null
        windowPanel.layout = GridLayout(0, 1)
        windowPanel.isOpaque = true
        windowPanel.background = CAMERA_FEED_BACKGROUND_COLOUR


        //MultipleDockables always require a factory
        if(GUIMain.strimmUIService.dockableControl.getMultipleDockableFactory(dockableFactoryId) == null) {
            GUIMain.loggerService.log(Level.INFO,"Dockable factory is null. Creating factory...")
            GUIMain.strimmUIService.dockableControl.addMultipleDockableFactory(dockableFactoryId, GUIMain.dockableWindowPluginService.multipleDockableFactory)
        }

        val factory = GUIMain.strimmUIService.dockableControl.getMultipleDockableFactory(dockableFactoryId)
        val dockable = DefaultMultipleCDockable(factory, title)
        dockable.isRemoveOnClose = true
        dockable.isCloseable = true
        GUIMain.loggerService.log(Level.INFO, "Created multiple dockable window")
        return dockable
    }

    /**
     * Dock the new dockable window. Also adds a custom close action for the new dockable window
     * @param control The main dockable control
     * @param frame STRIMM's main JFrame
     * @return Boolean value for successful or failed docking
     */
    override fun dock(control : CControl, frame : JFrame?) : Boolean{
        //Add the dockable content area to STRIMM's main JFrame (important)
        frame?.add(GUIMain.strimmUIService.dockableControl.contentArea)

        //Wrap the grid redeployment in an invoke later call. This will prevent a concurrent modification exception
        java.awt.EventQueue.invokeLater {
            run{
                //Now that a new window has been added, redeploy the grid so it can be added in a new position
                redeployGrid(dockableWindowMultiple)
            }
        }

        GUIMain.strimmUIService.strimmFrame = frame!! as StrimmUIService.CustomFrame

        GUIMain.loggerService.log(Level.INFO, "docked multiple dockable window")

        //Add a custom close action to ensure we do what we need when closing
        val action = CustomCloseAction(control)
        dockableWindowMultiple.putAction(MultipleCDockable.ACTION_KEY_CLOSE,action)
        dockableWindowMultiple.addFocusListener(FocusListenerClass())
        return true
    }

    override fun close(){
        this.CustomCloseAction(GUIMain.strimmUIService.dockableControl).close(dockableWindowMultiple)
    }

    fun toFocus(dockable: CDockable) {
        if (dockable.isVisible) {
            val location = dockable.baseLocation.findProperty()
        }
    }
}

/**
 * This is the interface for all dockable window plugins
 */
interface DockableWindowPlugin : ImageJPlugin{
    fun createDock(title : String) : DefaultMultipleCDockable
    fun dock(control : CControl, frame : JFrame?) : Boolean
    fun redeployGrid(newDockable : MultipleCDockable?)
    var dockableWindowMultiple : DefaultMultipleCDockable
    var title : String
    fun setCustomData(data : Any?) = Unit
    fun initialise() = Unit
    fun close() = Unit
}
