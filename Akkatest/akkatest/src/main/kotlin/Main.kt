import akka.stream.KillSwitches
import javafx.application.Application
import javafx.scene.Scene
import javafx.scene.control.Button
import javafx.scene.layout.StackPane
import javafx.stage.Stage

open class Main : Application() {
    override fun start(primaryStage: Stage?) {
        primaryStage!!.title = "Akkatest"
        val btn = Button()
        btn.text = "Run graph again"
        btn.setOnAction{
            println("Running graph again")
            AkkaStream.globalFlag = false
            AkkaStream.sharedKillSwitch = KillSwitches.shared("my-kill-switch")
            akkaStream.stream?.run(AkkaStream.materializer)
//            akkaStream = AkkaStream()
//            akkaStream.runStream()
        }

        val root = StackPane()
        root.children.add(btn)
        primaryStage.scene = Scene(root, 300.0, 250.0)
        primaryStage.show()
    }

    companion object {
        var akkaStream = AkkaStream()

        @JvmStatic
        fun main(args: Array<String>) {
            println("Starting akkatest")
//            val akkaStream = AkkaStream()
            akkaStream.runStream()
            launch(Main::class.java)
        }
    }
}