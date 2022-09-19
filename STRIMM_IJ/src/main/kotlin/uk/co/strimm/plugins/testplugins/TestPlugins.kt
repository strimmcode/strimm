package uk.co.strimm.plugins.testplugins

import net.imagej.Dataset
import net.imagej.DatasetService
import net.imagej.axis.Axes
import net.imglib2.type.numeric.integer.UnsignedByteType
import org.scijava.display.DisplayService
import org.scijava.plugin.Parameter
import org.scijava.plugin.Plugin
import uk.co.strimm.MicroManager.MMCameraDevice
import uk.co.strimm.pairsToDataDescriptionHashMap
import uk.co.strimm.plugins.*
import uk.co.strimm.services.MMCoreService
import java.util.*
import kotlin.collections.HashMap
import kotlin.reflect.full.createType

///////////// 1D Test Pipeline: Adds 10 and converts to float /////////////////
@Plugin(type = PipelineSourcePlugin::class)
class TestSourcePlugin_1D : PipelineSourcePlugin
{

    private val random = Random()

    val output = random.nextInt(100)

    override fun produce() = hashMapOf("1DSourceOut" to output as Any?)
    override fun available(): Boolean = true

    companion object {
        @JvmStatic
        fun getOutputTypes(arg : Any?) = pairsToDataDescriptionHashMap("1DSourceOut" to Int::class.createType())

        @JvmStatic
        fun getReadableName() = "Random Integer"
    }
}

@Plugin(type = PipelineProcessorPlugin::class)
class TestProcessorPlugin1_1D : PipelineProcessorPlugin {
    override fun process(input : HashMap<String, Any?>) =
            hashMapOf("tenMore" to ((input["input"] as Int) + 10) as Any?)


    companion object {
        @JvmStatic
        fun getInputTypes(arg : Any?) = pairsToDataDescriptionHashMap("input" to Int::class.createType())

        @JvmStatic
        fun getOutputTypes(arg : Any?) = pairsToDataDescriptionHashMap("tenMore" to Int::class.createType())

        @JvmStatic
        fun getReadableName() = "Add Ten"
    }
}

@Plugin(type = PipelineProcessorPlugin::class)
class TestProcessorPlugin2_1D : PipelineProcessorPlugin {
    override fun process(input : HashMap<String, Any?>) =
            hashMapOf("asFloat" to (input["input"] as Int).toFloat() as Any?)


    companion object {
        @JvmStatic
        fun getInputTypes(arg : Any?) = pairsToDataDescriptionHashMap("input" to Int::class.createType())

        @JvmStatic
        fun getOutputTypes(arg : Any?) = pairsToDataDescriptionHashMap("asFloat" to Float::class.createType())

        @JvmStatic
        fun getReadableName(): String = "Int to Float"
    }
}

@Plugin(type = PipelineSinkPlugin::class)
class TestSinkPlugin_1D : PipelineSinkPlugin {
    var output = 0.0f

    override fun consume(input: HashMap<String, Any?>) {
        output = input["input"] as Float
    }


    companion object {
        @JvmStatic
        fun getInputTypes(arg : Any?) = pairsToDataDescriptionHashMap("input" to Float::class.createType())

        @JvmStatic
        fun getReadableName() = "Float Output"
    }
}
///////////////////////////////////////////////////////////////////////////////

/////////// 2D Test pipeline: Adds 10 to each pixel //////////////////////////
@Plugin(type = PipelineSourcePlugin::class)
class TestSourcePlugin_2D : PipelineSourcePlugin {

    @Parameter
    lateinit var mm : MMCoreService

    @Parameter
    lateinit var datasetService : DatasetService

    private var cameraDevice : MMCameraDevice? = null
    var output : Dataset? = null

    private fun <T>withCamera(func : (MMCameraDevice) -> T) =
        cameraDevice?.let { func(it) }
        ?: run {
            camFromCore().let { camera ->
                cameraDevice = camera

                camera.initialise()

                func(camera)
            }
        }

    private fun camFromCore() = mm.getLoadedDevicesOfType(MMCameraDevice::class.java)[0]

    override fun produce(): HashMap<String, Any?> =
        withCamera { camera ->
            cameraDevice = camera

            camera.isActive = true

            val width = camera.imageWidth
            val height = camera.imageHeight
            val img = camera.snapImage().first as ByteArray

            val ds = datasetService
                    .create(UnsignedByteType(), longArrayOf(width, height), "Snapped Image", arrayOf(Axes.X, Axes.Y))

            ds.setPlane(0, img)

            output = ds

            hashMapOf("imageOut" to ds)
    }

    override fun available(): Boolean = withCamera { !it.isBusy() }

    companion object {
        @JvmStatic
        fun getOutputTypes(arg : Any?) = pairsToDataDescriptionHashMap("imageOut" to Dataset::class.createType())

        @JvmStatic
        fun getReadableName() = "Demo Image"
    }
}

@Plugin(type = PipelineProcessorPlugin::class)
class TestProcessorPlugin_2D : PipelineProcessorPlugin {

    @Parameter
    lateinit var datasetService : DatasetService

    override fun process(input: HashMap<String, Any?>): HashMap<String, Any?> {
        val inputImage = input["inputImage"] as Dataset
        val inArr = inputImage.getPlane(0) as ByteArray


        val output = datasetService
                .create(UnsignedByteType(),
                        longArrayOf(inputImage.width, inputImage.height),
                        "Processed Image", arrayOf(Axes.X, Axes.Y))

        output.setPlane(0, inArr.map { i -> (i + 10).toByte() }.toByteArray())

        return hashMapOf("tenMoreImage" to output)
    }

    companion object {
        @JvmStatic
        fun getOutputTypes(arg : Any?) = pairsToDataDescriptionHashMap("tenMoreImage" to Dataset::class.createType())

        @JvmStatic
        fun getInputTypes(arg : Any?) = pairsToDataDescriptionHashMap("inputImage" to Dataset::class.createType())

        @JvmStatic
        fun getReadableName() = "Add Ten (Image)"
    }
}

@Plugin(type = PipelineSinkPlugin::class)
class TestSinkPlugin_2D : PipelineSinkPlugin {

    @Parameter
    lateinit var display : DisplayService

    var output : Dataset? = null

    override fun consume(input: HashMap<String, Any?>) {
        val imageToShow = input["imageToShow"] as Dataset
        display.createDisplay(imageToShow)
        output = imageToShow
    }


    companion object {
        @JvmStatic
        fun getInputTypes(arg : Any?) = pairsToDataDescriptionHashMap("imageToShow" to Dataset::class.createType())

        @JvmStatic
        fun getReadableName() = "Show Image"
    }
}

//////////////////////////////////////////////////////////////////////////////


//////////////// Combined Pipeline ///////////////////////////////////////////
@Plugin (type = PipelineProcessorPlugin::class)
class TestProcessorPlugin_2Dto1D : PipelineProcessorPlugin {
    override fun process(input: HashMap<String, Any?>) =
        hashMapOf("average" to ((input["inputImage"] as Dataset).getPlane(0) as ByteArray).average().toFloat() as Any?)


    companion object {
        @JvmStatic
        fun getInputTypes(arg : Any?) = pairsToDataDescriptionHashMap("inputImage" to Dataset::class.createType())

        @JvmStatic
        fun getOutputTypes(arg : Any?) = pairsToDataDescriptionHashMap("average" to Float::class.createType())

        @JvmStatic
        fun getReadableName() = "Average"
    }
}
//////////////////////////////////////////////////////////////////////////////


//////////// Multi I/O //////////////////////////////////////////////////////

@Plugin(type = PipelineSinkPlugin::class)
class TestSinkPlugin_3x1D : PipelineSinkPlugin {
    var output0 = 0.0f
    var output1 = 0.0f
    var output2 = 0.0f

    override fun consume(input: HashMap<String, Any?>) {
        output0 = input["input0"] as Float
        output1 = input["input1"] as Float
        output2 = input["input2"] as Float
    }

    companion object {
        @JvmStatic
        fun getInputTypes(arg : Any?) = pairsToDataDescriptionHashMap(
                "input0" to Float::class.createType(),
                "input1" to Float::class.createType(),
                "input2" to Float::class.createType()
        )

        @JvmStatic
        fun getReadableName() = "3x Float Output"
    }
}

@Plugin(type = PipelineProcessorPlugin::class)
class TestProcessor_SubClamp : PipelineProcessorPlugin {

    @Parameter
    lateinit var datasetService: DatasetService

    override fun process(input: HashMap<String, Any?>): HashMap<String, Any?> {
        val image = input["image"] as Dataset
        val toSubtract = (input["toSubtract"] as Float).toByte()

        val output = (image.getPlane(0) as ByteArray)
                .map { (if (it > toSubtract) it - toSubtract else 0).toByte() }.toByteArray()

        val outputImage = datasetService.create(UnsignedByteType(),
                longArrayOf(image.width, image.height), image.name, arrayOf(Axes.X, Axes.Y))
        outputImage.setPlane(0, output)

        return hashMapOf("imageOut" to outputImage)
    }


    companion object {
        @JvmStatic
        fun getInputTypes(arg: Any?) = pairsToDataDescriptionHashMap(
                "toSubtract" to Float::class.createType(),
                "image" to Dataset::class.createType()
        )

        @JvmStatic
        fun getOutputTypes(arg: Any?) = pairsToDataDescriptionHashMap(
                "imageOut" to Dataset::class.createType()
        )

        @JvmStatic
        fun getReadableName() = "Subtract with clamp (Image)"
    }
}
