package uk.co.strimm.services



import bibliothek.gui.dock.common.CControl
import bibliothek.gui.dock.common.CGrid
import net.imagej.ImageJService
import org.scijava.plugin.Plugin
import org.scijava.service.AbstractService
import org.scijava.service.Service
import javax.swing.JFrame

@Plugin(type = Service::class)
class StrimmUIService : AbstractService(), ImageJService {
    var state = UIstate.IDLE
    var dockableControl = CControl()
    var cGrid = CGrid(dockableControl)
    var strimmFrame = JFrame("STRIMM")
    var currentFocusedDockableTitle = ""
}

enum class UIstate {
    IDLE,
    WAITING,
    PREVIEW,
    ACQUISITION,
    ACQUISITION_PAUSED;
}