package graphs

import akka.NotUsed
import akka.stream.javadsl.GraphDSL
import akka.stream.javadsl.Sink
import akka.stream.javadsl.Source
import messages.CompleteCameraStreaming
import messages.FailCameraStreaming
import messages.StartStreamingCamera
import AkkaStream
import STRIMMImage
import java.time.Duration

class Graph1 {
    /**
     * This is the most basic graph you can do.
     * Camera source -> Camera sink
     */
    fun buildGraph(builder : GraphDSL.Builder<NotUsed>){
        println("Running graph 1")
        val acquisitionMethod = AcquisitionMethods.EmptyImage("Empty", "EmptyImage")
        val cameraSource = Source.tick(Duration.ZERO, Duration.ofMillis(100.0.toLong()), Unit)
                .map{ acquisitionMethod.runMethod() }
                .async()
                .named("CamSource") as Source<STRIMMImage, NotUsed>

        val cameraActorRef = AkkaStream.actorSystem.actorOf(CameraActor.props().withDispatcher("control-aware-dispatcher"),"cameraActor")
        val camSink : Sink<STRIMMImage, NotUsed> = Sink.actorRefWithAck(cameraActorRef,
                StartStreamingCamera(), Acknowledgement.INSTANCE, CompleteCameraStreaming()
        )
        { ex -> FailCameraStreaming(ex) }

        val camSourceShape = builder.add(cameraSource)
        val camSinkShape = builder.add(camSink)

        builder.from(camSourceShape).to(camSinkShape)
    }
}