
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




                    println("start")

                        System.out.println("LIBRARY PATH :" + System.getProperty("java.library.path"));
        System.out.println("WORKINGDIRECTORY : " + System.getProperty("user.dir"));

        System.out.println("*****************here***************");
        //System.loadLibrary("Test");
        System.out.println("*****************here***************");

                   val ij = ImageJ()
                    ij.launch("")
                    ij.command().run(GUIMain::class.java, true)








            }




        }
    }
