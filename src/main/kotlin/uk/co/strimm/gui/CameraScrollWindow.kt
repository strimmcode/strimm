package uk.co.strimm.gui

import bibliothek.gui.dock.common.DefaultMultipleCDockable
import net.imagej.Dataset
import net.imagej.display.DatasetView
import net.imagej.display.ImageDisplay
import net.imglib2.img.array.ArrayImg
import net.imglib2.img.array.ArrayImgs
import net.imglib2.type.numeric.integer.ByteType
import net.imglib2.type.numeric.integer.ShortType
import net.imglib2.type.numeric.real.FloatType
import net.imglib2.view.Views
import org.scijava.display.Display
import org.scijava.plugin.Plugin
import org.scijava.ui.swing.viewer.SwingDisplayWindow
import org.scijava.ui.viewer.DisplayWindow
import uk.co.strimm.DisplayInfo
import uk.co.strimm.HDFImageDataset
import uk.co.strimm.plugins.AbstractDockableWindow
import uk.co.strimm.plugins.DockableWindowPlugin
import java.awt.Robot
import java.awt.event.InputEvent
import java.util.*
import java.util.logging.Level
import javax.swing.JPanel

@Plugin(type = DockableWindowPlugin::class, menuPath = "Window>Camera Scroll Window")
class CameraScrollWindowPlugin : AbstractDockableWindow() {
    override var title = GUIMain.utilsService.createDockableWindowTitleText("", false)
    lateinit var cameraScrollWindowController: CameraScrollWindow

    override fun setCustomData(data: Any?) {
        when (data) {
            is DisplayInfo -> {
                cameraScrollWindowController.displayInfo = data
                dockableWindowMultiple.titleText = data.displayName
            }
            is Dataset -> {
                cameraScrollWindowController.dataset = data
            }
            else -> {
                try {
                    cameraScrollWindowController.data =
                        data as MutableMap.MutableEntry<String, ArrayList<HDFImageDataset>> //<Dataset name, images>
                }
                catch(ex : Exception){
                    GUIMain.loggerService.log(Level.SEVERE, "Error assigning data to camera scroll window controller. Message: ${ex.message}")
                    GUIMain.loggerService.log(Level.SEVERE, ex.stackTrace)
                }
            }
        }
    }

    override var dockableWindowMultiple : DefaultMultipleCDockable = run{
        this.createDock(title).apply{
            add(windowPanel)
            this.titleText = title
            dockableWindowMultiple = this
            cameraScrollWindowController = CameraScrollWindow(windowPanel)
        }
    }
}

/**
 * The CameraScrollWindow is used exclusively when reloading an already acquired experiment. Data from the H5 file
 * will be sent to the controller that interacts with this class.
 * @param windowPanel The JPanel component that the data is added to
 */
class CameraScrollWindow(val windowPanel: JPanel){
    lateinit var data : MutableMap.MutableEntry<String, ArrayList<HDFImageDataset>>

    var displayInfo : DisplayInfo? = null
    var dataset : Dataset? = null
    var display : Display<*>? = null
    var view : DatasetView? = null
    var displayWindow : DisplayWindow? = null

    fun populateImages(){
        val bitDepth = data.value.first().bitDepth
        GUIMain.loggerService.log(Level.INFO, "Processing image data into stack")

        dataset = createDataset(bitDepth)
        display = GUIMain.displayService.createDisplayQuietly(dataset)
        view = (display as ImageDisplay).activeView as DatasetView
        displayWindow = GUIMain.uiService.defaultUI.createDisplayWindow(display)

        GUIMain.loggerService.log(Level.INFO, "Creating swing display window for image stack")
        GUIMain.uiService.viewerPlugins
            .map { GUIMain.pluginService.createInstance(it) }
            .find { it != null && it.canView(display) && it.isCompatible(GUIMain.uiService.defaultUI) }
            ?.let {
                GUIMain.threadService.queue {
                    (displayWindow as SwingDisplayWindow).apply {
                        GUIMain.uiService.addDisplayViewer(it)
                        it.view(this, display)
                        pack()
                        val rootPane = this.rootPane
                        windowPanel.add(rootPane)

                        /**
                        * We have to force a mouse press due to a Swing SDI UI bug otherwise the display
                        * will just be white
                        * Mentioned here (the last sentence):
                        * https://imagej.net/2012-08-01_-_Loading_and_displaying_a_dataset_with_the_ImageJ2_API
                        */
                        //TODO click is not happening at the correct x and y at the moment
                        val robot = Robot()
                        robot.mouseMove(windowPanel.width/2, windowPanel.height/2)
                        robot.mousePress(InputEvent.BUTTON1_DOWN_MASK)
                        robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)
                    }
                }
            }

        windowPanel.updateUI()
    }

    /**
     * Creates an ImageJ dataset from a specified bit depth. Reads from data in CameraScrollWindowController
     * @param bitDepth The bitdepth of the iamge data
     * @return A nullable ImageJ dataset (image stack)
     */
    fun createDataset(bitDepth : Int) : Dataset?{
        //Note - This code may look simple but ImageJ processing/manipulation examples very hard to interpret/find
        val imageDataset = when(bitDepth){
            8 -> {
                val allImages : MutableList<ArrayImg<ByteType, net.imglib2.img.basictypeaccess.array.ByteArray>> = mutableListOf()
                for(i in 0 until data.value.size){
                    val imageData = data.value[i].byteData!!
                    val width = data.value[i].width
                    val height = data.value[i].height
                    val image = ArrayImgs.bytes(imageData, height.toLong(), width.toLong())
                    allImages.add(image)
                }

                val stack = Views.stack(allImages)
                GUIMain.datasetService.create(stack)
            }
            16 -> {
                val allImages : MutableList<ArrayImg<ShortType, net.imglib2.img.basictypeaccess.array.ShortArray>> = mutableListOf()
                for(i in 0 until data.value.size){
                    val imageData = data.value[i].shortData!!
                    val width = data.value[i].width
                    val height = data.value[i].height
                    val image = ArrayImgs.shorts(imageData, height.toLong(), width.toLong())
                    allImages.add(image)
                }

                val stack = Views.stack(allImages)
                GUIMain.datasetService.create(stack)
            }
            32 -> {
                val allImages : MutableList<ArrayImg<FloatType, net.imglib2.img.basictypeaccess.array.FloatArray>> = mutableListOf()
                for(i in 0 until data.value.size){
                    val imageData = data.value[i].floatData!!
                    val width = data.value[i].width
                    val height = data.value[i].height
                    val image = ArrayImgs.floats(imageData, height.toLong(), width.toLong())
                    allImages.add(image)
                }

                val stack = Views.stack(allImages)
                GUIMain.datasetService.create(stack)
            }
            else -> null
        }

        return imageDataset
    }
}