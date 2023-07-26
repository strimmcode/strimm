package uk.co.strimm.flowMethods

import akka.actor.ActorRef
import uk.co.strimm.STRIMMBuffer
import uk.co.strimm.STRIMMPixelBuffer
import uk.co.strimm.STRIMMSignalBuffer
import uk.co.strimm.experiment.Flow
import kotlin.math.pow

class HistogramFlow : FlowMethod{
    lateinit var flow: Flow
    val binsBytes = 0 ..Byte.MAX_VALUE*2
    val binsShorts = 0 .. Short.MAX_VALUE*2
    val binsFloats = Int.MIN_VALUE .. Int.MAX_VALUE
    var binSize = 1
    val numBins = 256 //The number of bins we want regardless of the bit depth
    private var isFirst = true //Flag for if this is the first time the run method has been invoked

    override val properties: HashMap<String, String>
        get() = TODO("Not yet implemented")
    override var actor: ActorRef?
        get() = TODO("Not yet implemented")
        set(value) {}

    override fun init(flow: Flow) {
        this.flow = flow
    }

    /**
     * Sets the size of each bin for the histogram depending on the pixel type (bit depth)
     * @param pixelType The type of pixel (Byte, Short, or Float)
     */
    private fun setBinSize(pixelType : String){
        when(pixelType){
            "Byte" -> {
                binSize = 1
            }
            "Short" -> {
                binSize = 256
            }
            "Float" -> {
                binSize = (2.toDouble().pow(24)).toInt()//((Float.MAX_VALUE-1F)/(numBins-1)).toInt()
            }
        }
    }

    override fun run(images: List<STRIMMBuffer>): List<STRIMMBuffer> {
        val histogramBuffers = arrayListOf<STRIMMBuffer>()
        /**
         * This flow can receive inputs from 1 or more sources/flows. It is agnostic to this and just processes each
         * incoming image list as it comes. It is up to the HistogramWindow and HistogramSink to differentiate the images
         */
        for(image in images) {
            val buffer = image as STRIMMPixelBuffer

            if(isFirst) {
                setBinSize(buffer.pixelType)
                isFirst = false
            }

            val histogramData = makeHistogram(buffer)
            val times = DoubleArray(1)
            times[0] = buffer.timeAcquired
            val numSamples = 1
            val channelNames = mutableListOf<String>()
            channelNames.add(buffer.cameraLabel)

            val newSignalBuffer = STRIMMSignalBuffer(
                histogramData,
                times,
                numSamples,
                channelNames,
                buffer.dataID,
                buffer.status
            )

            //Set the pixel type so it can be used by the HistogramWindow
            val pixelType = buffer.getImageDataType()
            newSignalBuffer.imagePixelType = pixelType
            //Set the min and max pixel values for use in autostretching
            newSignalBuffer.pixelMinMax = getMinMaxPixelVals(buffer)
            newSignalBuffer.avgPixelIntensity = getAvgPixelIntensity(buffer)
            histogramBuffers.add(newSignalBuffer)
        }

        return histogramBuffers
    }

    /**
     * Get the minimum and maximum pixel values for the image. This is used for autostretching in the HistogramWindow
     * @see HistogramWindow
     * @param image The image as a STRIMMPixelBuffer
     * @return A pair with the min and max pixel values as Pair(min, max)
     */
    private fun getMinMaxPixelVals(image: STRIMMPixelBuffer) : Pair<Number, Number>{
        val minMax = Pair(0, 0)
        when(image.pix){
            //IMPORTANT - Add the max value of the number type to make it unsigned
            is ByteArray -> {
                val min = (image.pix as ByteArray).min() + Byte.MAX_VALUE + 1
                val max = (image.pix as ByteArray).max() + Byte.MAX_VALUE + 1
                return Pair(min, max)
            }
            is ShortArray -> {
                val min = (image.pix as ShortArray).min() + Short.MAX_VALUE + 1
                val max = (image.pix as ShortArray).max() + Short.MAX_VALUE + 1
                return Pair(min, max)
            }
            is FloatArray -> {
                val min = (image.pix as FloatArray).min() + Float.MAX_VALUE + 1
                val max = (image.pix as FloatArray).max() + Float.MAX_VALUE + 1
                return Pair(min, max)
            }
        }

        return minMax
    }

    private fun getAvgPixelIntensity(image : STRIMMPixelBuffer) : Double{
        when(image.pix){
            //IMPORTANT - Add the max value of the number type to make it unsigned
            is ByteArray -> {
                return (image.pix as ByteArray).average() + Byte.MAX_VALUE + 1
            }
            is ShortArray -> {
                return (image.pix as ShortArray).average() + Short.MAX_VALUE + 1
            }
            is FloatArray -> {
                return (image.pix as FloatArray).average() + Float.MAX_VALUE + 1
            }
        }

        return 0.0
    }

    /**
     * This method will make the histogram. It will go through the range of all possible pixel values (based
     * on bit depth) and count the pixels in each step range. Note this will convert all values to unsigned versions
     * before doing the counting
     * @param image The image that is the basis of the histogram
     * @return The counts (histogram) of the image
     */
    private fun makeHistogram(image: STRIMMPixelBuffer) : DoubleArray{
        val counts = arrayListOf<Double>()
        when(image.pix){
            is ByteArray ->{
                val pixels = (image.pix as ByteArray).map { x -> x.toUByte() }
                for (bin in binsBytes.step(binSize)) {
                    val binStart = bin.toUByte()
                    val binEnd = (bin + binSize).toUByte()
                    val count = pixels.count { x -> x in binStart until binEnd }
                    counts.add(count.toDouble())
                }
            }
            is ShortArray ->{
                val pixels = (image.pix as ShortArray).map { x -> x.toUShort() }
                for(bin in binsShorts.step(binSize)){
                    val binStart = bin.toUShort()
                    val binEnd = (bin + binSize).toUShort()
                    val count = pixels.count { x -> x in binStart until binEnd }
                    counts.add(count.toDouble())
                }
            }
            is FloatArray ->{
                val pixels = image.pix as FloatArray
                for(bin in binsFloats.step(binSize)){
                    val binStart = bin
                    val binEnd = bin + binSize
                    val count = pixels.count { x -> x.toLong() in binStart until binEnd}
                    counts.add(count.toDouble())
                }
            }
        }

        val normalisedCounts = normaliseCounts(counts)
        return normalisedCounts
    }

    /**
     * Normalise the values of the counts to be between 0 and 1
     * @param counts The unnormalised counts
     * @return The normalised counts (histogram)
     */
    private fun normaliseCounts(counts: ArrayList<Double>) : DoubleArray{
        val total = counts.sum()
        val normalisedCounts = counts.map { x -> (x/total) }
        return normalisedCounts.toDoubleArray()
    }

    override fun preStart() {
    }

    override fun postStop() {
    }
}