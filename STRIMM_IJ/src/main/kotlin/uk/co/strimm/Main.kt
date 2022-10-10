package uk.co.strimm

import net.imagej.ImageJ
import uk.co.strimm.gui.GUIMain
import java.io.FileOutputStream
import java.io.PrintStream

/**
 * Main class. Everything starts here
 */
open class Main {
    companion object {
        /**
         * Main method.
         *
         * @param args the array of arguments
         */
        @JvmStatic
        fun main(args: Array<String>) {
            System.setOut(PrintStream(FileOutputStream("startup.txt",true)))
            val ij = ImageJ()
            ij.launch(".")
            ij.command().run(GUIMain::class.java, true)

            /**
            It makes sense to create the STRIMM actor here as GUIMain will have been invoked (which contains services
            including the actor service). There will only ever be one instance of the STRIMM actor (it is the main actor).
            This will also create an instance of the File Writer actor, of which there is one of also. Long term
            it would be good to implement a singleton pattern for these actors and any other where there's only one
            instance
            */
            GUIMain.actorService.createStrimmActorIfNotExists()
        }
    }
}