package uk.co.strimm.flowMethods

import akka.actor.ActorRef
import uk.co.strimm.STRIMMBuffer
import uk.co.strimm.STRIMMPixelBuffer
import uk.co.strimm.STRIMMSignalBuffer
import uk.co.strimm.experiment.Flow

class HistogramFlow : FlowMethod{
    lateinit var flow: Flow
    val binsBytes = Byte.MIN_VALUE ..Byte.MAX_VALUE
    val binsShorts = Short.MIN_VALUE .. Short.MAX_VALUE
    val binsFloats = Int.MIN_VALUE .. Int.MAX_VALUE
    var binSize = 1
    val numBins = 256 //The number of bins we want regardless of the bit depth
    private var isFirst = true

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
                binSize = (binsShorts.last/(numBins-1))
            }
            "Float" -> {
                binSize = ((Float.MAX_VALUE-1F)/(numBins-1)).toInt()
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
            is ByteArray -> {
                val min = (image.pix as ByteArray).min()
                val max = (image.pix as ByteArray).max()
                return Pair(min, max)
            }
            is ShortArray -> {
                val min = (image.pix as ShortArray).min()
                val max = (image.pix as ShortArray).max()
                return Pair(min, max)
            }
            is FloatArray -> {
                val min = (image.pix as FloatArray).min()
                val max = (image.pix as FloatArray).max()
                return Pair(min, max)
            }
        }

        return minMax
    }

    private fun getAvgPixelIntensity(image : STRIMMPixelBuffer) : Double{
        when(image.pix){
            is ByteArray -> {
                return (image.pix as ByteArray).average()
            }
            is ShortArray -> {
                return (image.pix as ShortArray).average()
            }
            is FloatArray -> {
                return (image.pix as FloatArray).average()
            }
        }

        return 0.0
    }

    /**
     * This method will actually make the histogram. It will go through the range of all possible pixel values (based
     * on bit depth) and count the pixels in each step range.
     * @param image The image that is the basis of the histogram
     * @return The counts (histogram) of the image
     */
    private fun makeHistogram(image: STRIMMPixelBuffer) : DoubleArray{
        val counts = arrayListOf<Double>()
        when(image.pix){
            is ByteArray ->{
//                val pixels : ByteArray = if(isSigned(image)){
//                    shiftToUnsignedBytes(image)
//                }
//                else{
//                    image.pix as ByteArray
//                }
                val pixels = image.pix as ByteArray

                for (bin in binsBytes.step(binSize)) {
                    val binStart = bin
                    val binEnd = bin + 1
                    val count = pixels.count { x -> x in binStart until binEnd }
                    counts.add(count.toDouble())
                }
            }
            is ShortArray ->{
//                val pixels : ShortArray = if(isSigned(image)){
//                    shiftToUnsignedShorts(image)
//                } else{
//                    image.pix as ShortArray
//                }
                val pixels = image.pix as ShortArray

                for(bin in binsShorts.step(binSize)){
                    val binStart = bin
                    val binEnd = bin+binSize
                    val count = pixels.count { x -> x in binStart until binEnd }
                    counts.add(count.toDouble())
                }
            }
            is FloatArray ->{
//                val pixels : FloatArray = if(isSigned(image)){
//                    shiftToUnsignedFloats(image)
//                } else{
//                    image.pix as FloatArray
//                }
                val pixels = image.pix as FloatArray

                for(bin in binsFloats.step(binSize)){
                    val binStart = bin
                    val binEnd = bin+1
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

    /**
     * Method to check if the values in an image are signed
     * @param image The image to check
     * @return boolean flag true if the image uses signed values
     */
    private fun isSigned(image: STRIMMPixelBuffer): Boolean{
        var isSigned = false
        when(image.pix) {
            is ByteArray -> {
                val pixels = image.pix as ByteArray
                if(pixels.any{x -> x < 0} && pixels.all{x -> x <= Byte.MAX_VALUE}){
                    isSigned = true
                }
            }
            is ShortArray -> {
                val pixels = image.pix as ShortArray
                if(pixels.any{x -> x < 0} && pixels.all{x -> x <= Short.MAX_VALUE}){
                    isSigned = true
                }
            }
            is FloatArray -> {
                val pixels = image.pix as FloatArray
                if(pixels.any{x -> x < 0} && pixels.all{x -> x <= Float.MAX_VALUE}){
                    isSigned = true
                }
            }
        }
        return isSigned
    }

    /**
     * Converts a signed byte array to an unsigned byte array
     * @param image The buffer containing the signed byte array
     * @return The unsigned byte array
     */
    private fun shiftToUnsignedBytes(image: STRIMMPixelBuffer) : ByteArray{
        val pixels = image.pix as ByteArray
        val newPixels = ByteArray(pixels.size)
        pixels.forEachIndexed{i, x ->
            newPixels[i] = x.plus(Byte.MAX_VALUE).toByte()
        }
        return newPixels
    }

    /**
     * Converts a signed short array to an unsigned short array
     * @param image The buffer containing the signed short array
     * @return The unsigned short array
     */
    private fun shiftToUnsignedShorts(image: STRIMMPixelBuffer) : ShortArray{
        val pixels = image.pix as ShortArray
        val newPixels = ShortArray(pixels.size)
        val test = 1.toShort()
        val test2 = test.toUShort()
        pixels.forEachIndexed{i, x ->
            newPixels[i] = x.toShort()
        }
        return newPixels
    }

    /**
     * Converts a signed float array to an unsigned float array
     * @param image The buffer containing the signed float array
     * @return The unsigned float array
     */
    private fun shiftToUnsignedFloats(image: STRIMMPixelBuffer) : FloatArray{
        val pixels = image.pix as FloatArray
        val newPixels = FloatArray(pixels.size)
        pixels.forEachIndexed{i, x ->
            newPixels[i] = x.plus(Float.MAX_VALUE)
        }
        return newPixels
    }

    override fun preStart() {
    }

    override fun postStop() {
    }
}