package uk.co.strimm.services

import bibliothek.gui.dock.common.CControl
import bibliothek.gui.dock.common.CGrid
import net.imagej.ImageJService
import org.scijava.plugin.Plugin
import org.scijava.service.AbstractService
import org.scijava.service.Service
import javax.swing.JDialog
import javax.swing.JFrame
import javax.swing.JOptionPane

@Plugin(type = Service::class)
class StrimmUIService : AbstractService(), ImageJService {
    var state = UIstate.IDLE
    var dockableControl = CControl()
    var cGrid = CGrid(dockableControl)
    var strimmFrame = JFrame("STRIMM")
    var currentFocusedDockableTitle = ""

    val pane = JOptionPane()
    lateinit var dialog : JDialog
    var windowsLoaded = 0 //Used when loading existing experiment

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