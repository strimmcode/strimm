package uk.co.strimm.plugins.testplugins

import javafx.embed.swing.SwingFXUtils
import javafx.embed.swing.SwingNode
import javafx.fxml.FXML
import javafx.fxml.FXMLLoader
import net.imagej.Dataset
import net.imagej.display.DefaultDatasetView
import net.imagej.display.DefaultImageDisplay
import org.scijava.plugin.Parameter
import org.scijava.plugin.Plugin
import org.scijava.plugin.PluginService
import org.scijava.thread.ThreadService
import org.scijava.ui.UIService
import org.scijava.ui.swing.sdi.SwingSDIUI
import org.scijava.ui.swing.viewer.SwingDisplayWindow
import uk.co.strimm.gui.GUIMain
import uk.co.strimm.plugins.AbstractDockableWindow
import uk.co.strimm.plugins.DockableWindowPlugin
import java.io.File

/*

import com.anchorage.docks.node.DockNode
import com.anchorage.docks.stations.DockStation
import javafx.scene.layout.Pane
import org.scijava.display.DisplayService
import org.scijava.plugin.Parameter
import org.scijava.plugin.Plugin
import uk.co.strimm.plugins.*

@Plugin(type = DockableWindowPlugin::class)
class TestDockableWindowPlugin : DockableWindowPlugin
{
    override fun setHeightToParent(child: Pane, parent: Pane) {
        TODO("not implemented") //To change body of created functions use File | SettingsWindow | File Templates.
    }

    override fun setWidthToParent(child: Pane, parent: Pane) {
        TODO("not implemented") //To change body of created functions use File | SettingsWindow | File Templates.
    }

    override fun create(title: String, dockPane: Pane): DockNode {
        TODO("not implemented") //To change body of created functions use File | SettingsWindow | File Templates.
    }

    override fun dock(dockNode: DockNode, dockStation: DockStation, dockPos: DockNode.DockPosition): Boolean {
        TODO("not implemented") //To change body of created functions use File | SettingsWindow | File Templates.
    }
}*/
