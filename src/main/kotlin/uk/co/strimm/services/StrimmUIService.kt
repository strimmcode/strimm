package uk.co.strimm.services

import bibliothek.gui.dock.common.CControl
import bibliothek.gui.dock.common.CGrid
import net.imagej.ImageJService
import org.scijava.plugin.Plugin
import org.scijava.service.AbstractService
import org.scijava.service.Service
import uk.co.strimm.gui.GUIMain
import java.awt.KeyEventDispatcher
import java.awt.KeyboardFocusManager
import java.awt.event.KeyEvent
import javax.swing.JDialog
import javax.swing.JFrame
import javax.swing.JOptionPane

@Plugin(type = Service::class)
class StrimmUIService : AbstractService(), ImageJService {
    /**
     * Class that attaches a global key pressed listener. Used for event markers but can be used for many other things
     * @param frameName The name of the CustomFrame
     */
    class CustomFrame(frameName: String) : JFrame(frameName){
        private class MyDispatcher : KeyEventDispatcher {
            var keyPressCounter = 0
            override fun dispatchKeyEvent(e: KeyEvent?): Boolean {
                when {
                    e!!.id == KeyEvent.KEY_PRESSED -> {
                        GUIMain.strimmUIService.pressedEventKeys.add(Pair(keyPressCounter, KeyEvent.getKeyText(e.keyCode)))
                        keyPressCounter++
                    }
                }
                return false
            }
        }

        init {
            val manager = KeyboardFocusManager.getCurrentKeyboardFocusManager()
            manager.addKeyEventDispatcher(MyDispatcher())
        }
    }

    var state = UIstate.IDLE
    var dockableControl = CControl()
    var cGrid = CGrid(dockableControl)
    var strimmFrame = CustomFrame("STRÏMM")
    var currentFocusedDockableTitle = ""
    val pane = JOptionPane()
    lateinit var dialog : JDialog
    var windowsLoaded = 0 //Used when loading existing experiment

    //Used with EventMarkerFlow flow
    var pressedEventKeys = arrayListOf<Pair<Int, String>>() //Pair(Index of key press, event key string)
    var eventMarkerLabelThread = EventMarkerLabelThread("EventMarkerLabelThread")

    /**
     * Used when loading existing experiment to show to the user data is being loaded.
     * Will show a non-blocking dialog message
     */
    fun showLoadingDataDialog(){
        pane.options = arrayOf<Any>()
        dialog = pane.createDialog(strimmFrame, "Loading data")
        pane.message = "Loading data..."
        dialog.isModal = false
        dialog.defaultCloseOperation = JDialog.DO_NOTHING_ON_CLOSE
        dialog.isVisible = true
        pane.isEnabled = true
    }

    /**
     * Will hide the dialog that is shown when loading an existing experiment
     */
    fun hideLoadingDataDialog(){
        dialog.isVisible = false
        pane.isEnabled = false
    }

    /**
     * Thread class for updating the event marker label.
     * @param name The name of the thread
     */
    class EventMarkerLabelThread(name : String) : Thread(name){
        private var timeLastUpdated = System.currentTimeMillis()
        private var runThread = true

        fun terminate(){
            runThread = false
        }

        override fun run() {
            while(runThread) {
                updateEventMarkerLabel("")
                sleep(200)
            }
        }

        /**
         * Conditionally update the event marker label. The event marker label is intended to give the user feedback
         * for an event marker they have just added via keypress. This method will either be called via this thread's
         * run method or in the EventMarkerFlow run() method.
         * @param updateText The update text that will either be an empty string or the event marker text
         */
        fun updateEventMarkerLabel(updateText : String){
            val currentTime = System.currentTimeMillis()
            val newText = "Event marker $updateText added"
            val displayTime = 2000

            val isTextSame = (newText == GUIMain.markerEventLabel.text) || updateText == ""
            val hasBeenOverTime = (currentTime-timeLastUpdated) >= displayTime

            if(!isTextSame){
                //A new event marker has been added. Update the label text and show
                GUIMain.markerEventLabel.text = newText
                GUIMain.markerEventLabel.isVisible = true
                timeLastUpdated = System.currentTimeMillis()
            }
            else if(isTextSame && updateText != ""){
                //Extend the timer if the same event marker as previous has been added
                timeLastUpdated = System.currentTimeMillis()
            }
            else if(hasBeenOverTime){
                //Hide after exceeding display time limit
                GUIMain.markerEventLabel.isVisible = false
                GUIMain.markerEventLabel.text = ""
            }
        }
    }
}

enum class UIstate {
    IDLE,
    WAITING,
    PREVIEW,
    ACQUISITION,
    ACQUISITION_PAUSED;
}