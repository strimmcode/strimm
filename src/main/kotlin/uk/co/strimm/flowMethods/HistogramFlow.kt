package uk.co.strimm.flowMethods

import akka.actor.ActorRef
import uk.co.strimm.STRIMMBuffer
import uk.co.strimm.STRIMMPixelBuffer
import uk.co.strimm.STRIMMSignalBuffer
import uk.co.strimm.experiment.Flow

class HistogramFlow : FlowMethod{
    lateinit var flow: Flow
    var imageFeedMap = hashMapOf<String, Int>()
    val binsBytes = 0..255
    val binsShorts = 0..65535
    val binsFloats = 0..4294967295
    val binSize = 256

    override val properties: HashMap<String, String>
        get() = TODO("Not yet implemented")
    override var actor: ActorRef?
        get() = TODO("Not yet implemented")
        set(value) {}

    override fun init(flow: Flow) {
        this.flow = flow
    }

    override fun run(images: List<STRIMMBuffer>): List<STRIMMBuffer> {
        val histogramBuffers = arrayListOf<STRIMMBuffer>()
//        for(image in images) {
//            when (image) {
//                is STRIMMPixelBuffer -> {
//                    val imageObject = image as STRIMMPixelBuffer
//                    println(imageObject.cameraLabel)
//                    when (imageObject.pix) {
//                        is ByteArray -> {
//                            val dataToSend = makeHistogram(imageObject)
//                            val times = DoubleArray(1)
//                            times[0] = imageObject.timeAcquired
//                            val numSamples = 1
//                            val channelNames = mutableListOf<String>()
//                            channelNames.add("")
//
//                            val buffer = STRIMMSignalBuffer(
//                                dataToSend,
//                                times,
//                                numSamples,
//                                channelNames,
//                                imageObject.dataID,
//                                imageObject.status
//                            )
//                            histogramBuffers.add(buffer)
//                        }
//                    }
//                }
//            }
//        }

        return images
    }

    private fun makeHistogram(image: STRIMMPixelBuffer) : DoubleArray{
        val counts = arrayListOf<Double>()
        when(image.pix){
            is ByteArray ->{
                for(bin in binsBytes){
                    val binStart = bin
                    val binEnd = bin+1
                    val pixels = image.pix as ByteArray
                    val count = pixels.count { x -> x in binStart until binEnd }
                    counts.add(count.toDouble())
                }
            }
            is ShortArray ->{
                for(bin in binsShorts.step(binSize)){
                    val binStart = bin
                    val binEnd = bin+binSize
                    val pixels = image.pix as ShortArray
                    val count = pixels.count { x -> x in binStart until binEnd }
                    counts.add(count.toDouble())
                }
            }
            is FloatArray ->{
                for(bin in binsShorts){
                    val binStart = bin
                    val binEnd = bin+1
                    val pixels = image.pix as ShortArray
                    val count = pixels.count { x -> x in binStart until binEnd }
                    counts.add(count.toDouble())
                }
            }
        }

        return counts.toDoubleArray()
    }

    override fun preStart() {
    }

    override fun postStop() {
    }
}