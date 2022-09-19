package uk.co.strimm.plugins

import org.scijava.plugin.Parameter
import org.scijava.plugin.Plugin
import uk.co.strimm.getPipelinePluginReadableName


@Plugin(type = PipelineProcessorPlugin::class)
class ProcessorDuplicator : PipelineProcessorPlugin {

    @Parameter
    lateinit var pipelineService : PipelinePluginService

    lateinit var processor : PipelineProcessorPlugin
    private var n = 0

    override fun process(input: HashMap<String, Any?>): HashMap<String, Any?> {
        val processorReturn = processor.process(input)

        return hashMapOf(*processorReturn.toList()
                .fold(emptyList<Pair<String, Any?>>()) { outList, res ->
                    outList + (0 until n).map { Pair("${res.first}_$it", res.second) } }
                .toTypedArray())
    }


    override fun addMetadata(data: Any?) {
        when (data) {
            is MetaData -> {
                when {
                    PipelinePluginService.processorPredicate(data.pluginDesc.info) -> {
                        processor = pipelineService
                                .createPlugin(StageDescription(data.pluginDesc.info, data.pluginDesc.metadata))
                                as PipelineProcessorPlugin

                        n = data.n
                    }

                    else -> throw InvalidPipelineStageMetadataException("Invalid pipeline stage for ProcessorDuplicator!")
                }
            }
        }
    }

    companion object {
        @JvmStatic
        fun getInputTypes(data: Any?) = when (data) {
                is MetaData ->
                    when {
                        PipelinePluginService.processorPredicate(data.pluginDesc.info) ->
                            Pipeline.PipelineDescription.getInputTypes(data.pluginDesc)
                        else -> throw InvalidPipelineStageMetadataException("Should be of type ProcessorDuplicator.MetaData!")
                    }
                else -> throw InvalidPipelineStageMetadataException("Should be of type ProcessorDuplicator.MetaData!")
            }

        @JvmStatic
        fun getOutputTypes(data: Any?) = when (data) {
            is MetaData ->
                when {
                    PipelinePluginService.processorPredicate(data.pluginDesc.info) ->
                        Pipeline.PipelineDescription.getOutputTypes(data.pluginDesc)
                                .fold(emptyList<DataDescription>()) { outList, desc ->
                                    outList + (0 until data.n)
                                            .map { DataDescription("${desc.name}_$it", desc.type) }
                                }

                    else -> throw InvalidPipelineStageMetadataException("Should be of type ProcessorDuplicator.MetaData!")

                }
            else -> throw InvalidPipelineStageMetadataException("Should be of type ProcessorDuplicator.MetaData!")
        }

        @JvmStatic
        fun getReadableName() = "Processor Duplicator"
    }

    data class MetaData(val pluginDesc : StageDescription, val n : Int)

}


@Plugin(type = PipelineProcessorPlugin::class)
class LineDuplicator : PipelineProcessorPlugin {

    private lateinit var meta : MetaData

    override fun process(input: HashMap<String, Any?>): HashMap<String, Any?> {
        val dataIn = input[meta.inputDesc.name]
        return hashMapOf(*List(meta.n) { Pair("${meta.inputDesc.name}_$it", dataIn) }.toTypedArray())
    }

    override fun addMetadata(data: Any?) {
        when (data) {
            is MetaData -> meta = data
            else -> throw InvalidPipelineStageMetadataException("Should be of type LineProcessor.MetaData")
        }
    }


    companion object {
        @JvmStatic
        fun getInputTypes(data : Any?) = when (data) {
            is MetaData -> listOf(data.inputDesc)
            else -> throw InvalidPipelineStageMetadataException("Should be of type LineProcessor.MetaData")
        }

        @JvmStatic
        fun getOutputTypes(data : Any?) = when (data) {
            is MetaData -> List(data.n) { DataDescription("${data.inputDesc.name}_$it", data.inputDesc.type) }
            else -> throw InvalidPipelineStageMetadataException("Should be of type LineProcessor.Metadata")
        }

        @JvmStatic
        fun getReadableName() = "Line Duplicator"
    }

    data class MetaData(val inputDesc : DataDescription, val n : Int)
}

@Plugin(type = PipelineProcessorPlugin::class)
class Identity : PipelineProcessorPlugin {


    override fun process(input: HashMap<String, Any?>): HashMap<String, Any?> {
        return input
    }


    companion object {
        @JvmStatic
        fun getInputTypes(data : Any?) = when (data) {
            is MetaData -> data.descs
            else -> throw InvalidPipelineStageMetadataException("Should be of type Identity.MetaData")
        }

        @JvmStatic
        fun getOutputTypes(data : Any?) = when (data) {
            is MetaData -> data.descs
            else -> throw InvalidPipelineStageMetadataException("Should be of type Identity.MetaData")
        }

        @JvmStatic
        fun getReadableName() = "Identity"
    }


    data class MetaData(val descs : List<DataDescription>)
}

@Plugin(type = PipelineProcessorPlugin::class)
class ProcessorCombiner : PipelineProcessorPlugin {

    @Parameter
    lateinit var pipelineService : PipelinePluginService

    var plugins : List<Pair<PipelineProcessorPlugin, String>> = emptyList()


    override fun process(input: HashMap<String, Any?>): HashMap<String, Any?> =
        plugins.map { (plugin, name) ->
                    plugin.process(input
                                    .filterKeys { it.startsWith(name) }
                                    .mapKeys { it.key.substringAfter('_') }
                                    as HashMap)
                            .mapKeys { "${name}_${it.key}" } as HashMap }
                .reduce { acc, m -> acc.putAll(m); acc }

    override fun addMetadata(data: Any?) = when (data) {
        is MetaData -> {
            plugins = data
                    .processors
                    .fold(Pair(HashMap<String, Int>(), plugins)) { (nameOccurrences, outList), processor ->
                        if (!PipelinePluginService.processorPredicate(processor.info))
                            throw InvalidPipelineStageMetadataException("Processor combiner can only combine processors!")
                        val stageName = getPipelinePluginReadableName(processor.info) ?: processor.info.className
                        val nOcc = nameOccurrences[stageName] ?: 0
                        val name = "$stageName$nOcc"
                        nameOccurrences[stageName] = nOcc + 1

                        val plugin = pipelineService.createPlugin(processor) as PipelineProcessorPlugin
                        Pair(nameOccurrences, outList + listOf(Pair(plugin, name))) }
                    .second
        }
        else -> throw InvalidPipelineStageMetadataException("Should be of type ProcessorCombiner.MetaData")
    }


    companion object {
        private fun folder(typeFun : (StageDescription) -> List<DataDescription>) = {
                    (nameOccurrences, outList) : Pair<HashMap<String, Int>, List<DataDescription>>,
                        processor : StageDescription ->
                            val stageName = getPipelinePluginReadableName(processor.info) ?: processor.info.className
                            val nOcc = nameOccurrences[stageName] ?: 0
                            val name = "$stageName$nOcc"
                            nameOccurrences[stageName] = nOcc + 1
                            if (!PipelinePluginService.processorPredicate(processor.info))
                                throw InvalidPipelineStageMetadataException("Processor combiner can only combine processors!")
                            Pair(nameOccurrences, typeFun(processor).fold(outList) {
                                outList, desc ->
                                    outList + listOf(DataDescription("${name}_${desc.name}", desc.type)) }
                            )
                }

        @JvmStatic
        fun getInputTypes(data : Any?) = when (data) {
            is MetaData ->
                data.processors
                        .fold(Pair(HashMap(), emptyList()), folder((Pipeline.PipelineDescription)::getInputTypes))
                        .second
            else -> throw InvalidPipelineStageMetadataException("Should be of type ProcessorCombiner.MetaData")
        }

        @JvmStatic
        fun getOutputTypes(data : Any?) = when (data) {
            is MetaData ->
                data.processors
                        .fold(Pair(HashMap(), emptyList()), folder((Pipeline.PipelineDescription)::getOutputTypes))
                        .second
            else -> throw InvalidPipelineStageMetadataException("Should be of type ProcessorCombiner.Metadata")
        }

        @JvmStatic
        fun getReadableName() = "Processor Combiner"
    }

    data class MetaData(val processors : List<StageDescription>)
}