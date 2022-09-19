package uk.co.strimm.services

import akka.actor.ActorRef
import net.imagej.ImageJService
import net.imagej.overlay.EllipseOverlay
import net.imagej.overlay.Overlay
import org.scijava.plugin.Parameter
import org.scijava.plugin.Plugin
import org.scijava.service.AbstractService
import org.scijava.service.Service
import uk.co.strimm.*
import uk.co.strimm.Timer
import uk.co.strimm.gui.GUIMain
import java.util.*
import java.util.logging.Level
import kotlin.math.sin

class AnalogueDataStream(timer: Timer, val desc: AIChannelDesc) : TimerStopListener
{
    var associatedTraceActor : ActorRef? = null
    var associatedTraceDataStoreActor : ActorRef? = null
    var clockCounter = 0.0
    var intervalSecs = 0.0
    var dummyOverlay : Overlay? = null

    init {
        timer.addAnalogueInput(desc.channelName, desc.clockDiv, desc.voltageMax, desc.voltageMin,
                STRIMM_JArrayCallback(Unit) { arr, _ -> storeTraceData(arr) })
        dummyOverlay = EllipseOverlay()
        dummyOverlay!!.name = "NiDAQAIn" + Random().nextInt(1000)//TODO hardcoded
    }

    fun storeTraceData(arr : DoubleArray){
        val data = arrayListOf<TraceData>()

        arr.forEach { x ->
            data.add(TraceData(Pair(dummyOverlay, x), clockCounter))
            clockCounter += intervalSecs
        }

        //Send the data to the relevant actors
        //Each sink can have either a trace actor (display), trace data store actor, or both
        if(associatedTraceActor != null) {
            associatedTraceActor!!.tell(listOf(data), ActorRef.noSender())
        }

        if(associatedTraceDataStoreActor != null){
            associatedTraceDataStoreActor!!.tell(listOf(data), ActorRef.noSender())
        }
    }

    override fun stopEvent() {}

    override fun errorEvent() {}
}

data class AIChannelDesc(val channelName: ChannelName, val clockDiv: Int, val voltageMax: Double, val voltageMin: Double)
fun Timer.createAIStream(desc : AIChannelDesc) = AnalogueDataStream(this, desc)

data class STRIMM_IJ_Timer(val timer : Timer, val displayName : String)

@Plugin(type = Service::class)
class TimerService : AbstractService(), ImageJService
{
    @Parameter
    lateinit var log : LoggerService

    private val timers = mutableListOf<STRIMM_IJ_Timer>()

    val analogueDataStreams = mutableListOf<AnalogueDataStream>()

    private val runtime by lazy {
        val daqsDir = System.getProperty("user.dir") + "/DAQs/STRIMM_KotlinWrap"
        val rt = STRIMM_JNI(daqsDir)
        when (rt.initialise())
        {
            TimerResult.Success -> rt
            TimerResult.Error -> {
                log.log(Level.SEVERE, rt.getLastError())
                null
            }
            TimerResult.Warning -> {
                log.log(Level.WARNING, rt.getLastError())
                rt
            }
        }?.apply { addInstallDirectory(System.getProperty("user.dir") + "/DAQs/") }
    }

    fun getAvailableTimers() =
            runtime?.installedDevices
                    ?: run { log.log(Level.SEVERE, "Failed to get installed devices"); null }


    fun createTimer(deviceType : TimerName, displayName: String) =
            runtime?.createTimer(deviceType)
                    ?.let { STRIMM_IJ_Timer(it, displayName )}
                    ?.also { timers.add(it) }
                    ?: kotlin.run {
                        log.log(Level.SEVERE, "Failed to create timer")
                        runtime?.run { log.log(Level.SEVERE, runtime!!.getLastError()) }
                                ?: kotlin.run { log.log(Level.SEVERE, "Runtime is null") }
                        null
                    }

    fun deleteTimer(timer : STRIMM_IJ_Timer) {
        runtime?.deleteTimer(timer.timer)
        timers.remove(timer)
    }

    fun closeTimerService(){
        timers.forEach { tmr ->
            /*if (tmr.timer.isInitialised)*/ runtime?.deleteTimer(tmr.timer)
        }

        when(runtime?.deinitialise() ?: TimerResult.Success) {
            TimerResult.Error -> log.log(Level.SEVERE, runtime!!.getLastError())
            TimerResult.Success -> Unit
            TimerResult.Warning -> log.log(Level.WARNING, runtime!!.getLastError())
        }
    }

    fun Boolean.toByte() : Byte = if (this) 1 else 0

    fun getAnalogueDataStreamForSource(deviceChannelName : String) : AnalogueDataStream?{
        return analogueDataStreams.first { x -> x.desc.channelName.name == deviceChannelName}
    }

    fun resetAnalogueDataStreams(){
        GUIMain.loggerService.log(Level.INFO, "Zeroing analogue data streams")
        analogueDataStreams.forEach { x ->
            x.clockCounter = 0.0
            x.associatedTraceActor = null
            x.associatedTraceDataStoreActor = null
            x.dummyOverlay = EllipseOverlay()
            x.dummyOverlay!!.name = "NiDAQAIn" + "DummyOverlay" + Random().nextInt(1000)
        }
    }

    fun CreateDummyTimer() {
        val timer = createTimer(getAvailableTimers()!![0], "TestTimer1")!!
        val devProp = timer.timer.getProperty("Device Name")!! as StringTimerProperty
        devProp.value = devProp.getAllowedValues()[0]

        fun makeMyTimer() {
            val aiSR = timer.timer.getProperty("Analogue Input Sample Rate") as DoubleTimerProperty
            val aiBS = timer.timer.getProperty("Analogue Input Block Size") as IntegerTimerProperty
            val doSR = timer.timer.getProperty("Digital Output Sample Rate") as DoubleTimerProperty
            val doBS = timer.timer.getProperty("Digital Output Block Size") as IntegerTimerProperty
            val aoSR = timer.timer.getProperty("Analogue Output Sample Rate") as DoubleTimerProperty
            val aoBS = timer.timer.getProperty("Analogue Output Block Size") as IntegerTimerProperty

            //TODO hardcoded
            aiSR.value = 10000.0
            aiBS.value = 1000
            doSR.value = 1000.0
            doBS.value = 1000
            aoSR.value = 1000.0
            aoBS.value = 1000

            timer.timer.addDigitalOutput(timer.timer.getAvailableChannels(ChannelType.DigitalOut)[0].apply { println(name) },
                    1, ByteArray(1000) {i -> (i % 100 == 0).toByte() } )

            timer.timer.addAnalogueOutput(timer.timer.getAvailableChannels(ChannelType.AnalogueOut)[0].apply { println(name) },
                    1, DoubleArray(1000) { i -> sin(i.toDouble()) })

            val aiDS0 = timer.timer.createAIStream(AIChannelDesc(timer.timer.getAvailableChannels(ChannelType.AnalogueIn)[0].apply { println(name) },
                    1, 0.3, -0.3))

            val aiDS1 = timer.timer.createAIStream(AIChannelDesc(timer.timer.getAvailableChannels(ChannelType.AnalogueIn)[0].apply { println(name) },
                    1, 6.0, -1.0))

            analogueDataStreams.add(aiDS0)
            analogueDataStreams.add(aiDS1)
        }
        when (timer.timer.initialise()) {
            TimerResult.Success -> {
                makeMyTimer()
            }
            TimerResult.Error -> { println(timer.timer.getLastError()) }
            TimerResult.Warning -> {
                makeMyTimer()
            }
        }
    }

    fun GetFirstTimerTEMP() = timers[0]
}