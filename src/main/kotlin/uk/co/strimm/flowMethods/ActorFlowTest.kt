package uk.co.strimm.flowMethods

import akka.actor.ActorRef
import akka.pattern.PatternsCS
import uk.co.strimm.STRIMMBuffer
import uk.co.strimm.STRIMMPixelBuffer
import uk.co.strimm.actors.FlowTestActor
import uk.co.strimm.actors.messages.ask.AskMessageTest
import uk.co.strimm.experiment.Flow
import uk.co.strimm.gui.GUIMain
import java.util.concurrent.CompletionStage

class ActorFlowTest  : FlowMethod {
    lateinit var flow: Flow
    override lateinit var properties: HashMap<String, String>
    override var actor : ActorRef? = null
    var dataID = 0
    override fun init(flow: Flow) {
        //todo this is in the wrong place it should be in flow
        this.flow = flow
//        if (flow.flowCfg != "") {
//            properties = hashMapOf<String, String>()
//            var r: List<Array<String>>? = null
//            try {
//                CSVReader(FileReader(flow.flowCfg)).use { reader ->
//                    r = reader.readAll()
//                    for (props in r!!) {
//                        //specific properties are read from Cfg
//                        // "intervalMs" : "10.0"  etc
//                        properties[props[0]] = props[1]
//                    }
//                }
//            } catch (ex: Exception) {
//                println(ex.message)
//            }
//        }

        val messageClass = GUIMain.actorService.actorCreateMessages[FlowTestActor::class.java] //select CreateSourceTestActor::class.java
        if(messageClass != null) { //Not all classes need an actor associated with them
            val constructors = messageClass.constructors
            val newInstance = constructors[0].newInstance("FlowTestActor")
            println("before making actor")
            GUIMain.actorService.mainActor.tell(newInstance, GUIMain.actorService.mainActor)
            println("after making actor")
        }
        Thread.sleep(750) //need a better way eg a Future
        actor = GUIMain.actorService.getActorByName("FlowTestActor")
        println("flowtestsctor " + actor)
    }

    override fun run(image: List<STRIMMBuffer>): STRIMMBuffer {
        val future =  PatternsCS.ask(actor, AskMessageTest(), 500000) as CompletionStage<STRIMMBuffer>
        val chunk = future.toCompletableFuture().get() as STRIMMBuffer
        return chunk

    }
}