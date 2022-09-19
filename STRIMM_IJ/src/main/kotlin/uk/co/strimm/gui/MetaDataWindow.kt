package uk.co.strimm.gui

import bibliothek.gui.dock.common.DefaultMultipleCDockable
import javafx.fxml.FXML
import javafx.scene.control.Label
import org.scijava.plugin.Plugin
import uk.co.strimm.plugins.AbstractDockableWindow
import uk.co.strimm.plugins.DockableWindowPlugin
import javax.swing.JPanel

@Plugin(type = DockableWindowPlugin::class, menuPath = "Window>Metadata")
class MetaDataWindowPlugin : AbstractDockableWindow(){
    override var title = "Experiment Metadata" //This will eventually be overridden
    lateinit var metaDataWindowController : MetaDataWindow

    override var dockableWindowMultiple: DefaultMultipleCDockable = run{
        this.createDock(title).apply{
            this.titleText = title
            dockableWindowMultiple = this
            add(windowPanel)
            metaDataWindowController = MetaDataWindow(windowPanel)
        }
    }

}

class MetaDataWindow constructor(val windowPanel : JPanel){
    @FXML
    lateinit var lblTest : Label

    @FXML
    fun initialize() {

    }
}