package uk.co.strimm.plugins

import com.google.common.collect.HashBiMap
import net.imagej.ImageJPlugin
import net.imagej.ImageJService
import org.scijava.plugin.AbstractPTService
import org.scijava.plugin.Plugin
import org.scijava.plugin.PluginInfo
import org.scijava.service.Service
import uk.co.strimm.Result
import uk.co.strimm.hashBiMapToHashMap
import kotlin.math.min
import kotlin.reflect.KType
import kotlin.reflect.full.createType

/**
 * Pipeline Plugin Service class:
 * Allows retrieval of installed pipeline plugins and creation of pipeline plugin instances
 */
@Plugin(type = Service::class)
class PipelinePluginService : AbstractPTService<PipelinePlugin>(), ImageJService {

    var pluginInfoMap = hashMapOf<Class<out PipelinePlugin>, PluginInfo<PipelinePlugin>>()
        private set

    override fun initialize() {
        pluginInfoMap = hashMapOf(*plugins.map { info -> Pair(info.pluginClass, info) }.toTypedArray())

    }

    private fun testPluginPredicate(info : PluginInfo<*>) =
            info.pluginClass.name.startsWith("uk.co.strimm.plugins.testplugins")
    fun getSourcePlugins() = plugins.filter(sourcePredicate).filterNot(::testPluginPredicate)
    fun getProcessorPlugins() = plugins.filter(processorPredicate).filterNot(::testPluginPredicate)
    fun getSinkPlugins() = plugins.filter(sinkPredicate).filterNot(::testPluginPredicate)


    companion object {
        private fun pluginTypeFilter(type : Class<*>) =
                { info : PluginInfo<*> -> info.pluginClass.interfaces.contains(type) }

        val sourcePredicate = pluginTypeFilter(PipelineSourcePlugin::class.java)
        val processorPredicate = pluginTypeFilter(PipelineProcessorPlugin::class.java)
        val sinkPredicate = pluginTypeFilter(PipelineSinkPlugin::class.java)
    }

    override fun getPluginType(): Class<PipelinePlugin> = PipelinePlugin::class.java

    fun createPlugin(desc : StageDescription): PipelinePlugin? {
        val plugin = pluginService().createInstance(desc.info)
        plugin.addMetadata(desc.metadata)
        return plugin
    }
}

class InvalidPipelineStageException(message : String) : Exception("Invalid Pipeline Stage: $message!")
class InvalidPipelineStageMetadataException(message: String) : Exception("Invalid pipeline stage metadata: $message!")

data class DataDescription(val name : String, val type : KType)
data class StageDescription(val info : PluginInfo<PipelinePlugin>, val metadata: Any?)

/**
 * Pipline Class:
 * Holds instances of pipeline plugins and handles application of the pipeline
 */
class Pipeline private constructor(desc : PipelineDescription,
                                   pipelineService : PipelinePluginService,
                                   val connections : List<HashMap<String, String>>) {

    val source = pipelineService.createPlugin(desc.source) as PipelineSourcePlugin

    val processors = desc.getProcessors().map{ info ->
        pipelineService.createPlugin(info) as PipelineProcessorPlugin}

    val sink = pipelineService.createPlugin(desc.sink) as PipelineSinkPlugin

    private fun applyConnections(connections : HashMap<String, String>, values : HashMap<String, Any?>) =
        values.mapKeys { (key, _) -> connections[key] } as HashMap<String, Any?>


    fun applyPipeline() {
        if (source.available())
            sink.consume(applyConnections(connections.last(),
                    processors.zip(connections)
                        .fold(source.produce()) {
                            input, (stage, connections) -> stage.process(applyConnections(connections, input))
                        }
            ))

    }


    /**
     * Pipeline Description Class:
     * Holds a description of a pipeline and ensures that the pipeline is valid before creating it
     */
    class PipelineDescription(source: StageDescription,
                              sink: StageDescription,
                              private val pipelineService : PipelinePluginService,
                              private val connections : List<HashBiMap<String, String>>)
    {
        init {
            if (!PipelinePluginService.sourcePredicate(source.info))
                throw InvalidPipelineStageException("Expected ${PipelineSourcePlugin::class.qualifiedName} " +
                        "got ${source.info.pluginClass.interfaces[0].name}")

            if (!PipelinePluginService.sinkPredicate(sink.info))
                throw InvalidPipelineStageException("Expected ${PipelineSinkPlugin::class.qualifiedName} " +
                        "got ${sink.info.pluginClass.interfaces[0].name}")
        }


        var source : StageDescription = source
            set(value) {
                if (PipelinePluginService.sourcePredicate(value.info))
                    field = value
                else
                    print("Invalid source!") //TODO: log error properly
            }

        var sink : StageDescription = sink
            set(value) {
                if (PipelinePluginService.sinkPredicate(value.info))
                    field = value
                else
                    print("Invalid sink!") //TODO: log error properly
            }

        private var processors = mutableListOf<StageDescription>()

        fun getProcessors() = processors


        fun addProcessorStage(processor : StageDescription, position: Int) {
            val pos = min(position, processors.size)
            processors.add(pos, processor)
        }

        fun addProcessorStage(processor: StageDescription) {
            processors.add(processor)
        }

        fun addProcessorStages(processors : List<StageDescription>) {
            processors.forEach(::addProcessorStage)
        }


        fun verifyPipeline() : Result<Boolean> {
            if (connections.size != processors.size + 1)
                return Result.Failure("Number of connections does not match the number of processing stages")

            return (processors + listOf(sink))
                    .zip(connections)
                    .fold(Pair(Result.Success(true) as Result<Boolean>, source)) {
                        (result, prevStage), (stage, connections) ->
                        val res = getInputTypes(stage).fold(result) { res, desc ->
                            when (res) {
                                is Result.Failure -> res
                                is Result.Success -> {
                                    val connection = connections.inverse()[desc.name]
                                    val prevOutput = getOutputTypes(prevStage)
                                            .find { output -> output.name == connection }
                                    when {
                                        connection == null           ->
                                            Result.Failure("Missing input for ${desc.name}")
                                        prevOutput == null           ->
                                            Result.Failure("No output $connection")
                                        desc.type != prevOutput.type ->
                                            Result.Failure("Mismatched connection: ${prevOutput.type} " +
                                                    "(${prevOutput.name}) does not match ${desc.type} (${desc.name})")
                                        else -> Result.Success(true)
                                    }
                                }
                            }
                        }
                        Pair(res, stage)
                    }
                    .first
        }


        fun createPipeline() : Result<Pipeline> {
            val result = verifyPipeline()
            return when (result) {
                is Result.Failure -> Result.Failure(result.message)
                is Result.Success ->
                    Result.Success(Pipeline(this, pipelineService, connections.map(::hashBiMapToHashMap)))
            }
        }

        companion object {

            fun getInputTypes(pluginDesc: StageDescription) : List<DataDescription> =
                    pluginDesc
                            .info
                            .pluginClass
                            .getDeclaredMethod("getInputTypes", Any::class.java)
                            .invoke(null, pluginDesc.metadata) as List<DataDescription>
            fun getOutputTypes(pluginDesc : StageDescription) : List<DataDescription> =
                    pluginDesc
                            .info
                            .pluginClass
                            .getDeclaredMethod("getOutputTypes", Any::class.java)
                            .invoke(null, pluginDesc.metadata) as List<DataDescription>

        }
    }
}

/**
 * Pipeline plugin super-interface
 */
interface PipelinePlugin : ImageJPlugin {
    fun addMetadata(data : Any?) = Unit

    companion object {
        fun getReadableName() : String? = null
    }
}

interface PipelineProducer {
    companion object {
        fun getOutputTypes(arg : Any) : List<DataDescription> = emptyList()
    }
}

interface PipelineConsumer {
    companion object {
        fun getInputTypes(arg : Any) : List<DataDescription> = emptyList()
    }
}

/**
 * Source interface:
 * Sources return values of type T when via the produce method
 * Sources should block until data is available
 */
interface PipelineSourcePlugin : PipelinePlugin, PipelineProducer
{
    fun produce() : HashMap<String, Any?>
    fun available() : Boolean
}

/**
 * Processor interface:
 * Processors take input from either a source or another processor of type T and return a value of type U
 * If multiple values from the previous stage are required for correct operation (e.g., a 1D smoothing filter)
 * then the processor may buffer its input, however, it must always return a value.
 */
interface PipelineProcessorPlugin : PipelinePlugin, PipelineProducer, PipelineConsumer
{
    fun process(input : HashMap<String, Any?>) : HashMap<String, Any?>
}

/**
 * Sink interface:
 * Sinks receive a single input and use it to perform a task, e.g., data display
 * They do not return a value and as such represent a termination of the pipeline
 */
interface PipelineSinkPlugin : PipelinePlugin, PipelineConsumer
{
    fun consume(input : HashMap<String, Any?>)
}


