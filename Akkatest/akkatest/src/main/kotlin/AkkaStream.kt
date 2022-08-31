import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.*
import akka.stream.javadsl.*
import graphs.Graph1
import graphs.Graph2
import graphs.Graph3
import graphs.Graph4

class AkkaStream{
    companion object {
        //Stuff copied from STRÏMM
        val mailboxConfig = com.typesafe.config.ConfigFactory.parseString("control-aware-dispatcher { mailbox-type = \"akka.dispatch.UnboundedControlAwareMailbox\" }")
        var actorSystem = ActorSystem.create("STRIMMAkkaSystem", mailboxConfig)
        val materializer = ActorMaterializer.create(ActorMaterializerSettings.create(actorSystem).withInputBuffer(1, 1), actorSystem)
        var sharedKillSwitch = KillSwitches.shared("my-kill-switch") //ONE KILLSWITCH PER GRAPH INSTANCE
        var startTime = 0.0 // stores the HPC datum value for 0 sec
        fun setFirstTimeMeasurement(){
            startTime = System.nanoTime().toDouble()
        }
        fun getTime() : Double {
            return System.nanoTime() - startTime
        }

        var globalFlag = false
    }

    var stream : RunnableGraph<NotUsed>? = null

    fun runStream(){
        val graph = createStreamGraph()
        val streamGraph = RunnableGraph.fromGraph(graph)
        stream = streamGraph
        println("Running Akka graph")
        globalFlag = true
        stream?.run(materializer)
        setFirstTimeMeasurement()
    }

    private fun createStreamGraph() : Graph<ClosedShape, NotUsed> {
        return GraphDSL.create{ builder ->
            println("Building Akka graph")

            /*
            Comment/Uncomment the graph you want to use here
            */
//            val g1 = Graph1()
//            g1.buildGraph(builder)

//            val g2 = Graph2()
//            g2.buildGraph(builder)

//            val g3 = Graph3()
//            g3.buildGraph(builder)

            val g4 = Graph4()
            g4.buildGraph(builder)
            ClosedShape.getInstance()
        }
    }
}