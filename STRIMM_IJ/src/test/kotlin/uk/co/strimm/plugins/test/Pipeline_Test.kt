package uk.co.strimm.plugins.test

import com.google.common.collect.HashBiMap
import net.imagej.Dataset
import net.imagej.ImageJ
import org.junit.Test
import org.junit.Assert
import uk.co.strimm.Result
import uk.co.strimm.hashBiMapOf
import uk.co.strimm.plugins.*
import uk.co.strimm.plugins.testplugins.*
import uk.co.strimm.services.MMCoreService
import kotlin.reflect.full.createType

class PipelineTest {

    companion object {
        private val services = getServicesTest()
        private val pipelineService = services.first
        private val mmCoreService = services.second
        private val sourcePlugins = pipelineService?.getSourcePlugins()
        private val processorPlugins = pipelineService?.getProcessorPlugins()
        private val sinkPlugins = pipelineService?.getSinkPlugins()

        private fun getServicesTest() = let {
            val ij = ImageJ()

            ij.launch()

            Pair(ij.context.getService(PipelinePluginService::class.java),
                    ij.context.getService(MMCoreService::class.java))
        }

        init {
            mmCoreService.initForTests()
        }

    }


    private fun getPipelineDesc(source: Pair<Class<*>, Any?>,
                                sink: Pair<Class<*>, Any?>,
                                processors: List<Pair<Class<*>, Any?>>,
                                connections: List<HashBiMap<String, String>>): Pipeline.PipelineDescription {
        listOf(pipelineService, sourcePlugins, processorPlugins, sinkPlugins).map(Assert::assertNotNull)

        val sourceInfo = pipelineService!!.pluginInfoMap[source.first]
        val sinkInfo = pipelineService.pluginInfoMap[sink.first]
        val processorInfo = processors.map { type -> pipelineService.pluginInfoMap[type.first] }

        Assert.assertNotNull(sourceInfo)
        Assert.assertNotNull(sinkInfo)
        processorInfo.forEach(Assert::assertNotNull)

        val desc = Pipeline.PipelineDescription(StageDescription(sourceInfo!!, source.second),
                StageDescription(sinkInfo!!, sink.second),
                pipelineService, connections)
        desc.addProcessorStages(processorInfo
                .zip(processors.map { it.second })
                .map { (info, meta) -> StageDescription(info!!, meta) })

        return desc
    }

    private fun validationTest(desc: Pipeline.PipelineDescription) {
        val result = desc.verifyPipeline()
        when (result) {
            is Result.Failure -> Assert.fail("Pipeline verification failed! ${result.message}")
        }
    }

    private fun executionTest(desc: Pipeline.PipelineDescription, testFun: (Pipeline) -> Unit) {
        validationTest(desc)
        val pipelineRes = desc.createPipeline()
        when (pipelineRes) {
            is Result.Failure -> Assert.fail("Pipeline creation failed! ${pipelineRes.message}")
            is Result.Success -> {
                val pipeline = pipelineRes.result
                pipeline.applyPipeline()

                testFun(pipeline)
            }
        }
    }


//    @Test
//    fun TestPipelineValidation1D_Successful() = validationTest(
//            getPipelineDesc(
//                    Pair(TestSourcePlugin_1D::class.java, null),
//                    Pair(TestSinkPlugin_1D::class.java, null),
//                    listOf(
//                            Pair(TestProcessorPlugin1_1D::class.java, null),
//                            Pair(TestProcessorPlugin2_1D::class.java, null)),
//                    listOf(
//                            hashBiMapOf("1DSourceOut" to "input"),
//                            hashBiMapOf("tenMore" to "input"),
//                            hashBiMapOf("asFloat" to "input"))))
//
//
//    @Test
//    fun TestPipelineValidation1D_Unsuccessful() {
//        val desc = getPipelineDesc(
//                Pair(TestSourcePlugin_1D::class.java, null),
//                Pair(TestSinkPlugin_1D::class.java, null),
//                emptyList(),
//                listOf(
//                        hashBiMapOf("1DSourceOut" to "input")
//                ))
//        val result = desc.verifyPipeline()
//        when (result) {
//            is Result.Success -> Assert.fail("Pipeline verification succeeded when it should fail!")
//        }
//    }
//
//    @Test
//    fun TestPipelineValidation1D_WithException() {
//        try {
//            getPipelineDesc(
//                    Pair(TestSinkPlugin_1D::class.java, null),
//                    Pair(TestSourcePlugin_1D::class.java, null),
//                    emptyList(),
//                    emptyList())
//            Assert.fail("Pipeline description creation did not raise an error")
//        } catch (e: InvalidPipelineStageException) {
//        }
//    }
//
//    @Test
//    fun TestInvalidPipeline_MissingInput() {
//        val desc = getPipelineDesc(
//                Pair(TestSourcePlugin_1D::class.java, null),
//                Pair(TestSinkPlugin_1D::class.java, null),
//                listOf(
//                        Pair(TestProcessorPlugin1_1D::class.java, null),
//                        Pair(TestProcessorPlugin2_1D::class.java, null)),
//                listOf(
//                        hashBiMapOf("1DSourceOut" to "input"),
//                        hashBiMapOf(),
//                        hashBiMapOf("asFloat" to "input")))
//
//
//        val result = desc.verifyPipeline()
//        when (result) {
//            is Result.Success -> Assert.fail("Pipeline verification succeeded when it should have failed!")
//        }
//    }
//
//    @Test
//    fun TestInvalidPipeline_MissingOutput() {
//        val desc = getPipelineDesc(
//                Pair(TestSourcePlugin_1D::class.java, null),
//                Pair(TestSinkPlugin_1D::class.java, null),
//                listOf(
//                        Pair(TestProcessorPlugin1_1D::class.java, null),
//                        Pair(TestProcessorPlugin2_1D::class.java, null)),
//                listOf(
//                        hashBiMapOf("1DSourceOut" to "input"),
//                        hashBiMapOf("elevenMore" to "input"),
//                        hashBiMapOf("asFloat" to "input")))
//
//        val result = desc.verifyPipeline()
//        when (result) {
//            is Result.Success -> Assert.fail("Pipeline verification succeeded when it should have failed!")
//        }
//    }
//
//
//    @Test
//    fun TestPipelineExecution1D() =
//            executionTest(getPipelineDesc(
//                    Pair(TestSourcePlugin_1D::class.java, null),
//                    Pair(TestSinkPlugin_1D::class.java, null),
//                    listOf(
//                            Pair(TestProcessorPlugin1_1D::class.java, null),
//                            Pair(TestProcessorPlugin2_1D::class.java, null)),
//                    listOf(
//                            hashBiMapOf("1DSourceOut" to "input"),
//                            hashBiMapOf("tenMore" to "input"),
//                            hashBiMapOf("asFloat" to "input"))))
//            { pipeline ->
//                val source = pipeline.source as TestSourcePlugin_1D
//                val sink = pipeline.sink as TestSinkPlugin_1D
//
//                Assert.assertEquals(source.output + 10.0f, sink.output)
//            }
//
//
//    @Test
//    fun TestPipelineValidation2D() = validationTest(
//            getPipelineDesc(
//                    Pair(TestSourcePlugin_2D::class.java, null),
//                    Pair(TestSinkPlugin_2D::class.java, null),
//                    listOf(
//                            Pair(TestProcessorPlugin_2D::class.java, null)),
//                    listOf(
//                            hashBiMapOf("imageOut" to "inputImage"),
//                            hashBiMapOf("tenMoreImage" to "imageToShow"))))
//
//    @Test
//    fun TestPipelineExecution2D() = executionTest(
//            getPipelineDesc(
//                    Pair(TestSourcePlugin_2D::class.java, null),
//                    Pair(TestSinkPlugin_2D::class.java, null),
//                    listOf(
//                            Pair(TestProcessorPlugin_2D::class.java, null)),
//                    listOf(
//                            hashBiMapOf("imageOut" to "inputImage"),
//                            hashBiMapOf("tenMoreImage" to "imageToShow"))))
//    { pipeline ->
//        val source = pipeline.source as TestSourcePlugin_2D
//        val sink = pipeline.sink as TestSinkPlugin_2D
//
//        val fromSource = source.output
//        val fromSink = sink.output
//
//        Assert.assertNotNull("Source produced null input!", fromSource)
//        Assert.assertNotNull("Sink produced null input!", fromSink)
//
//        if (fromSource != null && fromSink != null) {
//            (fromSource.getPlane(0) as ByteArray)
//                    .zip(fromSink.getPlane(0) as ByteArray)
//                    .forEachIndexed { i, (source, sink) ->
//                        Assert.assertEquals("Index $i: ", (source + 10).toByte(), sink)
//                    }
//
//            Thread.sleep(1000)
//        }
//    }
//
//    @Test
//    fun TestPipelineValidation_Combined() = validationTest(
//            getPipelineDesc(
//                    Pair(TestSourcePlugin_2D::class.java, null),
//                    Pair(TestSinkPlugin_1D::class.java, null),
//                    listOf(
//                            Pair(TestProcessorPlugin_2Dto1D::class.java, null)),
//                    listOf(
//                            hashBiMapOf("imageOut" to "inputImage"),
//                            hashBiMapOf("average" to "input"))))
//
//    @Test
//    fun TestPipelineExecution_Combined() = executionTest(
//            getPipelineDesc(
//                    Pair(TestSourcePlugin_2D::class.java, null),
//                    Pair(TestSinkPlugin_1D::class.java, null),
//                    listOf(
//                            Pair(TestProcessorPlugin_2Dto1D::class.java, null)),
//                    listOf(
//                            hashBiMapOf("imageOut" to "inputImage"),
//                            hashBiMapOf("average" to "input"))))
//    { pipeline ->
//        val source = pipeline.source as TestSourcePlugin_2D
//        val sink = pipeline.sink as TestSinkPlugin_1D
//
//        val fromSource = source.output
//        val fromSink = sink.output
//
//        Assert.assertNotNull("Source produced null input!", fromSource)
//        Assert.assertEquals((fromSource!!.getPlane(0) as ByteArray).average().toFloat(), fromSink)
//    }
//
//
//    @Test
//    fun ProcessorDuplicatorTest_Verify() = validationTest(let {
//        val testProcessorInfo = pipelineService!!.pluginInfoMap[TestProcessorPlugin2_1D::class.java]
//        val meta = ProcessorDuplicator.MetaData(StageDescription(testProcessorInfo!!, null), 3)
//        getPipelineDesc(
//                Pair(TestSourcePlugin_1D::class.java, null),
//                Pair(TestSinkPlugin_3x1D::class.java, null),
//                listOf(
//                        Pair(TestProcessorPlugin1_1D::class.java, null),
//                        Pair(ProcessorDuplicator::class.java, meta)),
//                listOf(
//                        hashBiMapOf(
//                                "1DSourceOut" to "input"),
//                        hashBiMapOf(
//                                "tenMore" to "input"),
//                        hashBiMapOf(
//                                "asFloat_0" to "input0",
//                                "asFloat_1" to "input1",
//                                "asFloat_2" to "input2"))) })
//
//    @Test
//    fun ProcessorDuplicatorTest_Execution() = executionTest(let {
//        val testProcessorInfo = pipelineService!!.pluginInfoMap[TestProcessorPlugin2_1D::class.java]
//        val meta = ProcessorDuplicator.MetaData(StageDescription(testProcessorInfo!!, null), 3)
//        getPipelineDesc(
//                Pair(TestSourcePlugin_1D::class.java, null),
//                Pair(TestSinkPlugin_3x1D::class.java, null),
//                listOf(
//                        Pair(TestProcessorPlugin1_1D::class.java, null),
//                        Pair(ProcessorDuplicator::class.java, meta)),
//                listOf(
//                        hashBiMapOf(
//                                "1DSourceOut" to "input"),
//                        hashBiMapOf(
//                                "tenMore" to "input"),
//                        hashBiMapOf(
//                                "asFloat_0" to "input0",
//                                "asFloat_1" to "input1",
//                                "asFloat_2" to "input2"))) })
//    { pipeline ->
//        val source = pipeline.source as TestSourcePlugin_1D
//        val sink = pipeline.sink as TestSinkPlugin_3x1D
//
//        val input = source.output
//        listOf(sink.output0, sink.output1, sink.output2).forEachIndexed { indx, op ->
//            Assert.assertEquals("Output$indx:", input + 10.0f, op)
//        }
//    }
//
//
//    @Test
//    fun LineDuplicatorTest() = executionTest(let {
//        val meta = LineDuplicator.MetaData(DataDescription("asFloat", Float::class.createType()), 3)
//        getPipelineDesc(
//                Pair(TestSourcePlugin_1D::class.java, null),
//                Pair(TestSinkPlugin_3x1D::class.java, null),
//                listOf(
//                        Pair(TestProcessorPlugin1_1D::class.java, null),
//                        Pair(TestProcessorPlugin2_1D::class.java, null),
//                        Pair(LineDuplicator::class.java, meta)),
//                listOf(
//                        hashBiMapOf(
//                                "1DSourceOut" to "input"),
//                        hashBiMapOf(
//                                "tenMore" to "input"),
//                        hashBiMapOf(
//                                "asFloat" to "asFloat"),
//                        hashBiMapOf(
//                                "asFloat_0" to "input0",
//                                "asFloat_1" to "input1",
//                                "asFloat_2" to "input2"))) } )
//    { pipeline ->
//        val source = pipeline.source as TestSourcePlugin_1D
//        val sink = pipeline.sink as TestSinkPlugin_3x1D
//
//        val input = source.output
//        listOf(sink.output0, sink.output1, sink.output2).forEachIndexed { indx, op ->
//            Assert.assertEquals("Output$indx:", input + 10.0f, op)
//        }
//    }

//    @Test
//    fun ProcessorCombinerTest() = executionTest(let {
//        val lineDupeMeta = LineDuplicator.MetaData(DataDescription("imageOut", Dataset::class.createType()), 2)
//        val identityMeta = Identity.MetaData(listOf(DataDescription("image", Dataset::class.createType())))
//        val combinerMeta = ProcessorCombiner.MetaData(listOf(
//                StageDescription(pipelineService!!.pluginInfoMap[TestProcessorPlugin_2Dto1D::class.java]!!,
//                        null),
//                StageDescription(pipelineService.pluginInfoMap[Identity::class.java]!!,
//                        identityMeta)))
//
//        getPipelineDesc(
//                Pair(TestSourcePlugin_2D::class.java, null),
//                Pair(TestSinkPlugin_2D::class.java, null),
//                listOf(
//                        Pair(LineDuplicator::class.java, lineDupeMeta),
//                        Pair(ProcessorCombiner::class.java, combinerMeta),
//                        Pair(TestProcessor_SubClamp::class.java, null)),
//                listOf(
//                        hashBiMapOf("imageOut" to "imageOut"),
//                        hashBiMapOf(
//                                "imageOut_0" to "Average0_inputImage",
//                                "imageOut_1" to "Identity0_image"),
//                        hashBiMapOf(
//                                "Average0_average" to "toSubtract",
//                                "Identity0_image" to "image"),
//                        hashBiMapOf("imageOut" to "imageToShow"))) } )
//    { pipeline ->
//        val source = pipeline.source as TestSourcePlugin_2D
//        val sink = pipeline.sink as TestSinkPlugin_2D
//
//        val input = source.output!!
//        val output = sink.output!!
//
//        val avg = (input.getPlane(0) as ByteArray).average().toByte()
//        val shifted = (input.getPlane(0) as ByteArray).map { if (it > avg) (it - avg).toByte() else 0 }
//
//        shifted.zip((output.getPlane(0) as ByteArray).toList()).forEachIndexed { idx, (exp, op) ->
//            Assert.assertEquals("Index $idx incorrect: ", exp, op)
//
//        }
//    }


}