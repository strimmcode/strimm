package uk.co.strimm

enum class TimerResult(val i : Int)
{
    Success(1),
    Error(0),
    Warning(-1)
}

enum class ChannelType(val i : Int)
{
    AnalogueIn(0),
    AnalogueOut(1),
    DigitalIn(2),
    DigitalOut(3)
}

class STRIMM_JVoidCallback<in T>(private val data : T, private val lambda : (T) -> Unit)
{
    fun run() = lambda(data)
}

class STRIMM_JArrayCallback<ArrayType, in T>( private val data : T, private val lambda : (ArrayType, T) -> Unit)
{
    fun run(arr : ArrayType) = lambda(arr, data)
}

data class ChannelName constructor(val name : String)

interface TimerStopListener
{
    fun stopEvent()
    fun errorEvent()
}

class Timer private constructor(private val instance : Long)
{
    var isInitialised = false
        private set

    val stopListeners = mutableListOf<TimerStopListener>()

    external fun getName() : String
    external fun getAPIVersion() : Int
    external fun getLastError() : String

    fun initialise() : TimerResult
    {
        fun registerCallbacks() {
            registerStopCallback(STRIMM_JVoidCallback(Unit){
                synchronized(stopListeners) { stopListeners.forEach { listener -> listener.stopEvent() }}
            })
            registerErrorCallback(STRIMM_JVoidCallback(Unit) {
                synchronized(stopListeners) {stopListeners.forEach { listener -> listener.errorEvent() }}
            })
        }

        return initialiseEXT().apply {
            isInitialised = when(this)
            {
                TimerResult.Success -> { registerCallbacks(); true }
                TimerResult.Error ->  false
                TimerResult.Warning -> { registerCallbacks(); true }
            }
        }

    }
    private external fun initialiseEXT() : TimerResult


    fun getAvailableChannels(channelType: ChannelType) = getAvailableChannelsEXT(channelType.i).map(::ChannelName)
    private external fun getAvailableChannelsEXT(channelType: Int) : Array<String>

    fun getChannels(channelType: ChannelType) = getChannelsEXT(channelType.i).map(::ChannelName)
    private external fun getChannelsEXT(channelType: Int) : Array<String>



    fun addAnalogueInput(channelName : ChannelName, clockDiv : Int, voltageMax : Double, voltageMin : Double, newDataCallback : STRIMM_JArrayCallback<DoubleArray, Any>) =
            addAnalogueInputEXT(channelName.name, clockDiv, voltageMax, voltageMin, newDataCallback)
    private external fun addAnalogueInputEXT(channelName : String, clockDiv : Int, voltageMax : Double, voltageMin : Double,
                                  newDataCallback : STRIMM_JArrayCallback<DoubleArray, Any>) : TimerResult

    fun addAnalogueOutput(channelName: ChannelName, clockDiv: Int, samples: DoubleArray) =
            addAnalogueOutputEXT(channelName.name, clockDiv, samples)
    private external fun addAnalogueOutputEXT(channelName: String, clockDiv: Int, samples : DoubleArray) : TimerResult

    fun addDigitalInput(channelName: ChannelName, clockDiv: Int, newDataCallback: STRIMM_JArrayCallback<ByteArray, Any>) =
            addDigitalInputEXT(channelName.name, clockDiv, newDataCallback)
    private external fun addDigitalInputEXT(channelName: String, clockDiv: Int,
                                 newDataCallback: STRIMM_JArrayCallback<ByteArray, Any>) : TimerResult

    fun addDigitalOutput(channelName : ChannelName, clockDiv : Int, samples : ByteArray) =
            addDigitalOutputEXT(channelName.name, clockDiv, samples)
    private external fun addDigitalOutputEXT(channelName: String, clockDiv: Int, samples: ByteArray) : TimerResult

    fun addStopListener(listener : TimerStopListener) = synchronized(stopListeners) { stopListeners.add(listener) }
    fun removeStopListener(listener : TimerStopListener) = synchronized(stopListeners) { stopListeners.remove(listener) }
    private external fun registerStopCallback(callback : STRIMM_JVoidCallback<*>)
    private external fun registerErrorCallback(callback: STRIMM_JVoidCallback<*>)

    external fun prime() : TimerResult
    external fun start() : TimerResult
    external fun stop() : TimerResult

    external fun getPropertyNames() : Array<String>


    external fun getProperty(name : String) : TimerProperty?
    external fun releaseProperty(prop : TimerProperty)
}