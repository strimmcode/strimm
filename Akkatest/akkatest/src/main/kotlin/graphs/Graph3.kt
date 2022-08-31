package graphs

import AkkaStream.Companion.materializer
import AkkaStream.Companion.sharedKillSwitch
import STRIMMImage
import akka.NotUsed
import akka.stream.javadsl.*
import messages.*
import java.time.Duration

class Graph3 {
    /**
     * This graph provides a minimal example of a graph with a sharedKillSwitch.
     * Note - killswitch.shutdown() is called from the camera actor
     * Camera source -> Killswitch flow -> Camera Sink
     */
    fun buildGraph(builder : GraphDSL.Builder<NotUsed>){
        println("Running graph 3")
        val acquisitionMethod = AcquisitionMethods.EmptyImage("Empty", "EmptyImage")
        val cameraSource = Source.tick(Duration.ZERO, Duration.ofMillis(100.0.toLong()), Unit)
                .map{ acquisitionMethod.runMethod() }
                .async()
                .named("CamSource") as Source<STRIMMImage, NotUsed>

        val cameraActorRef = AkkaStream.actorSystem.actorOf(CameraActor.props().withDispatcher("control-aware-dispatcher"),"cameraActor")
        val camSink : Sink<STRIMMImage, NotUsed> = Sink.actorRefWithAck(cameraActorRef,
                StartStreamingCamera(), Acknowledgement.INSTANCE, CompleteCameraStreaming())
        { ex -> FailCameraStreaming(ex) }

        val killSwitchGraph = cameraSource.viaMat(sharedKillSwitch.flow(), Keep.right()).toMat(camSink, Keep.right())
        killSwitchGraph.run(materializer)
    }
}