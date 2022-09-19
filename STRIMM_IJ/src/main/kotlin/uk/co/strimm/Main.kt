package uk.co.strimm

//import com.fazecast.jSerialComm.SerialPort
import net.imagej.ImageJ
import uk.co.strimm.gui.GUIMain

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
            println("start of main ******************************")
//            val portToUse = SerialPort.getCommPort("COM5") //find in DeviceManager
//            portToUse.setComPortParameters(9600, 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY)
//            portToUse.setComPortTimeouts(SerialPort.TIMEOUT_NONBLOCKING, 0, 0)
//
//            if (!portToUse.isOpen) {
//                println("Port is not open")
//                if (portToUse.openPort()) {
//                    println("Opened port")
//                } else {
//                    println("Failed to open port")
//                }
//            } else {
//                println("Port is already open")
//            }
////for (f in 0..1000) {
////    println("Sending byte")
////    var data = 'A'.toByte()
////    portToUse.outputStream.write(byteArrayOf(data))
////    //if(portToUse.inputStream.available() > 0 && i > 0)
////
////    data = 'B'.toByte()
////    portToUse.outputStream.write(byteArrayOf(data))
////}
//
//
//            /*val tmp = (System.getProperty("user.dir") + "/DAQs/STRIMM_KotlinWrap").apply(::println)
//
//            val runtime = STRIMM_JNI(tmp)
//            if (!runtime.valid) return
//
//            println("\n*************** Test Init ***************")
//            var res = runtime.initialise()
//            if (res == TimerResult.Error)
//                println(runtime.getLastError())
//            else
//                println("Success!")
//
//            println("\n*************** Test Get Installed Devices ***************")
//            runtime.addInstallDirectory(System.getProperty("user.dir") + "/DAQs/")
//            val devices = runtime.getInstalledDevices()
//            devices?.forEach(::println)
//
//            println("\n*************** Test De-init ***************")
//            res = runtime.deinitialise()
//            if (res == TimerResult.Error)
//                println(runtime.getLastError())
//            else
//                println("Success!")*/

            val ij = ImageJ()
            ij.launch(".")
            ij.command().run(GUIMain::class.java, true)

            /*
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