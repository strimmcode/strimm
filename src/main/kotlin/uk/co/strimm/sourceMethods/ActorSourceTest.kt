package uk.co.strimm.sourceMethods

import akka.actor.ActorRef
import akka.japi.Pair
import akka.pattern.PatternsCS
import com.opencsv.CSVReader
import mmcorej.StrVector
import scala.concurrent.Await
import uk.co.strimm.STRIMMBuffer
import uk.co.strimm.STRIMMPixelBuffer
import uk.co.strimm.actors.SourceTestActor
import uk.co.strimm.actors.messages.ask.AskMessageTest
import uk.co.strimm.experiment.Source
import uk.co.strimm.gui.GUIMain
import uk.co.strimm.plugins.DockableWindowPlugin
import java.io.FileReader
import java.util.*
import java.util.concurrent.CompletableFuture
//
//ActorSource
//
//Get a sample from an Actor which is external to the akka-graph
//so you could have hardware which is contained in an external actor which
//might say be on a different host
//the actor might contain MicroManager - and therefore could have
//its parameters changed
//
//
//
//TODO clean this up
class ActorSourceTest() : SourceBaseMethod() {

    override fun init(source : Source){
        this.source = source
        //load the config file - inside (source.sourceName).cfg
        loadCfg()

//        //make SourceTestActor
        val messageClass = GUIMain.actorService.actorCreateMessages[SourceTestActor::class.java] //select CreateSourceTestActor::class.java
        if(messageClass != null) { //Not all classes need an actor associated with them
            val mainActor = GUIMain.actorService.mainActor
            val constructors = messageClass.constructors
            val newInstance = constructors[0].newInstance("SourceTestActor")
            mainActor.tell(newInstance, mainActor)
        }
        Thread.sleep(750) //need a better way eg a Future
        actor = GUIMain.actorService.getActorByName("SourceTestActor")
    }

    override fun run() : STRIMMBuffer? {
        val future = PatternsCS.ask(actor, AskMessageTest(), 500)
        val chunk = future.toCompletableFuture().get() as STRIMMBuffer
        return chunk
    }
}