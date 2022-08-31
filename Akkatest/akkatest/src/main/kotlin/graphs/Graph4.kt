package graphs

import AkkaStream.Companion.materializer
import AkkaStream.Companion.sharedKillSwitch
import akka.NotUsed
import java.time.Duration
import STRIMMImage
import akka.stream.javadsl.*
import messages.*
import java.util.*

class Graph4 {
    /**
     * This graph contains a broadcast hub going fanning out to a camera sink and a camera data store sink. Each
     * broadcast hub outlet goes via a flow. The broadcast hub also has a sharedKillSwitch
     * Camera source (broadcast hub) -> Killswitch flow -> Camera flow 1 -> Camera sink
     * Camera source (broadcast hub) -> Killswitch flow -> Camera flow 2 -> Camera data store sink
     * NOTE - there are other amendments to AkkaStream.kt and Main.kt so this graph can be run again after a
     * killswitch.shutdown() call
     */
    fun buildGraph(builder : GraphDSL.Builder<NotUsed>){
        println("Running graph 4")
        val acquisitionMethod = AcquisitionMethods.EmptyImage("Empty", "EmptyImage")
        val cameraSource = Source.tick(Duration.ZERO, Duration.ofMillis(100.0.toLong()), Unit)
                .map{ acquisitionMethod.runMethod() }
                .async()
                .named("CamSource") as Source<STRIMMImage, NotUsed>

        val cameraFlow1 = builder.add(Flow.of(STRIMMImage::class.java)
                // .buffer(ExperimentConstants.ConfigurationProperties.IMAGE_BUFFER_SIZE, OverflowStrategy.dropTail())
                .async()
                .named("BufferFlow1"))

        val cameraFlow2 = builder.add(Flow.of(STRIMMImage::class.java)
                // .buffer(ExperimentConstants.ConfigurationProperties.IMAGE_BUFFER_SIZE, OverflowStrategy.dropTail())
                .async()
                .named("BufferFlow2"))

        val cameraActorGuid = UUID.randomUUID().toString()
        val cameraActorName = "cameraActor-ID-$cameraActorGuid"
        val cameraActorRef = AkkaStream.actorSystem.actorOf(CameraActor.props().withDispatcher("control-aware-dispatcher"),cameraActorName)
        val camSink : Sink<STRIMMImage, NotUsed> = Sink.actorRefWithAck(cameraActorRef,
                StartStreamingCamera(), Acknowledgement.INSTANCE, CompleteCameraStreaming())
        { ex -> FailCameraStreaming(ex) }

        val cameraDataStoreActorGuid = UUID.randomUUID().toString()
        val cameraDataStoreActorName = "cameraDataStoreActor-ID-$cameraDataStoreActorGuid"
        val cameraDataStoreActorRef = AkkaStream.actorSystem.actorOf(CameraDataStoreActor.props().withDispatcher("control-aware-dispatcher"),cameraDataStoreActorName)
        val demoCamDataStoreSink : Sink<STRIMMImage, NotUsed> = Sink.actorRefWithAck(cameraDataStoreActorRef,
                StartCameraDataStoring(), Acknowledgement.INSTANCE, CompleteCameraDataStoring()
        )
        { ex -> FailCameraDataStoring(ex) }

        println("Creating broadcast hub")
        val killSwitchSource = cameraSource.viaMat(sharedKillSwitch.flow(), Keep.right())
        val bcastHubWithKillSwitch = killSwitchSource.toMat(BroadcastHub.of(STRIMMImage::class.java), Keep.right())
        println("Running broadcast hub")
        val bcastSource = bcastHubWithKillSwitch.run(materializer).async()

        println("Adding Akka objects")
        val demoCamSourceShape = builder.add(bcastSource)
        val demoCamSinkShape = builder.add(camSink)
        val demoCamDataStoreSinkShape = builder.add(demoCamDataStoreSink)
        val broadcastObject = builder.add(Broadcast.create<STRIMMImage>(2))

        println("Specifying connections")
        builder.from(demoCamSourceShape).viaFanOut(broadcastObject)
        builder.from(broadcastObject).via(cameraFlow1).to(demoCamSinkShape)
        builder.from(broadcastObject).via(cameraFlow2).to(demoCamDataStoreSinkShape)
    }
}