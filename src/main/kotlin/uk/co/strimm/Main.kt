import net.imagej.ImageJ
import uk.co.strimm.gui.GUIMain

class Main {
        companion object {
            /**
             * Main method.
             *
             * @param args the array of arguments
             */
            @JvmStatic
            fun main(args: Array<String>) {
                val ij = ImageJ()
                ij.launch("")
                ij.command().run(GUIMain::class.java, true)
            }
        }
    }
