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
    val pressedEventKeys = arrayListOf<Pair<Int, String>>()

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
}

enum class UIstate {
    IDLE,
    WAITING,
    PREVIEW,
    ACQUISITION,
    ACQUISITION_PAUSED;
}