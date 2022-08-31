package graphs

import AkkaStream.Companion.materializer
import akka.NotUsed
import STRIMMImage
import akka.stream.javadsl.*
import messages.*
import java.time.Duration

class Graph2 {
    /**
     * This graph has a camera source that is made into a broadcast hub.
     * This broadcast hub them fans out to a camera sink and a camera data store sink
     * Camera source (broadcast hub) -> Camera sink
     * Camera source (broadcast hub) -> Camera data store sink
     */
    fun buildGraph(builder : GraphDSL.Builder<NotUsed>){
        println("Running graph 2")

        println("Creating actors")
        val acquisitionMethod = AcquisitionMethods.EmptyImage("Empty", "EmptyImage")
        val demoCameraSource = Source.tick(Duration.ZERO, Duration.ofMillis(100.0.toLong()), Unit)
                .map{ acquisitionMethod.runMethod() }
                .async()
                .named("DemoCamSource") as Source<STRIMMImage, NotUsed>

        val cameraActorRef = AkkaStream.actorSystem.actorOf(CameraActor.props().withDispatcher("control-aware-dispatcher"),"demoCamActor")
        val demoCamSink : Sink<STRIMMImage, NotUsed> = Sink.actorRefWithAck(cameraActorRef,
                StartStreamingCamera(), Acknowledgement.INSTANCE, CompleteCameraStreaming()
        )
        { ex -> FailCameraStreaming(ex) }

        val cameraDataStoreActorRef = AkkaStream.actorSystem.actorOf(CameraDataStoreActor.props().withDispatcher("control-aware-dispatcher"),"demoCamDataStoreActor")
        val demoCamDataStoreSink : Sink<STRIMMImage, NotUsed> = Sink.actorRefWithAck(cameraDataStoreActorRef,
                StartCameraDataStoring(), Acknowledgement.INSTANCE, CompleteCameraDataStoring()
        )
        { ex -> FailCameraDataStoring(ex) }

        println("Creating broadcast hub")
        val bcastHub = demoCameraSource.toMat(BroadcastHub.of(STRIMMImage::class.java), Keep.right())
        println("Running broadcast hub")
        val bcastSource = bcastHub.run(materializer).async()

        println("Adding Akka objects")
        val demoCamSourceShape = builder.add(bcastSource)
        val demoCamSinkShape = builder.add(demoCamSink)
        val demoCamDataStoreSinkShape = builder.add(demoCamDataStoreSink)
        val broadcastObject = builder.add(Broadcast.create<STRIMMImage>(2))

        println("Specifying connections")
        builder.from(demoCamSourceShape).viaFanOut(broadcastObject)
        builder.from(broadcastObject).to(demoCamSinkShape)
        builder.from(broadcastObject).to(demoCamDataStoreSinkShape)
    }
}