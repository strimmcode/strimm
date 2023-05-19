package uk.co.strimm.flowMethods

import akka.actor.ActorRef
import uk.co.strimm.STRIMMBuffer
import uk.co.strimm.STRIMMPixelBuffer
import uk.co.strimm.experiment.Coordinates
import uk.co.strimm.experiment.Flow
import uk.co.strimm.gui.GUIMain
import java.util.logging.Level

class ImageSplitterFlow : FlowMethod {
    lateinit var flow: Flow
    override lateinit var properties: HashMap<String, String>

    override var actor: ActorRef? = null

    var x = 0
    var y = 0
    var w = 0
    var h = 0

    override fun init(flow: Flow) {
        this.flow = flow
        x = flow.splitCoordinates[0].x
        y = flow.splitCoordinates[0].y
        w = flow.splitCoordinates[0].w
        h = flow.splitCoordinates[0].h
        GUIMain.loggerService.log(Level.INFO, "Image splitter flow configured with: x=$x, y=$y, w=$w, h=$h")
    }

    override fun run(image: List<STRIMMBuffer>): STRIMMBuffer {
        val originalImage = image[0] as STRIMMPixelBuffer
        val imageToReturn = STRIMMPixelBuffer(
            numChannels = originalImage.numChannels,
            dataID = originalImage.dataID,
            w = w,
            h = h,
            status = originalImage.status,
            pixelType = originalImage.pixelType,
            timeAcquired = originalImage.timeAcquired,
            pix = sliceImage(originalImage, flow.splitCoordinates[0])
        )
        return imageToReturn
    }

    fun sliceImage(image: Any, coordinates : Coordinates) : Any{
        if(image is STRIMMPixelBuffer) {
            val x = coordinates.x
            val y = coordinates.y
            val w = coordinates.w
            val h = coordinates.h

            when (image.pix) {
                is ByteArray -> {
                    val imageArray = image.pix as ByteArray
                    val slicedImageArray = ByteArray(w * h)
                    var pixelIndex = 0
                    var startRowIdx = y-1

                    if(startRowIdx<0){
                        startRowIdx=0
                    }

                    val endRowIdx = startRowIdx + (h - 1)

                    for (row in startRowIdx until endRowIdx) {
                        val wholeSliceStartIdx = (image.w) * row
                        val wholeSliceEndIdx = wholeSliceStartIdx + (image.w - 1)

                        //This wll get a whole row
                        var wholeRow = listOf<Byte>()
                        try {
                            wholeRow = imageArray.slice(IntRange(wholeSliceStartIdx, wholeSliceEndIdx))
                        }
                        catch(ex : Exception){
                            GUIMain.loggerService.log(Level.SEVERE, "Error taking row of image array (when slicing). Message: ${ex.message}")
                            GUIMain.loggerService.log(Level.SEVERE, ex.stackTrace)
                        }

                        val subSliceStartIdx = x
                        val subSliceEndIdx = subSliceStartIdx + (w - 1)
                        var rowSubset = listOf<Byte>()
                        try {
                            rowSubset = wholeRow.slice(IntRange(subSliceStartIdx, subSliceEndIdx))
                        }
                        catch (ex: Exception) {
                            GUIMain.loggerService.log(Level.SEVERE, "Error taking subset of image array row (when slicing). Message: ${ex.message}")
                            GUIMain.loggerService.log(Level.SEVERE, ex.stackTrace)
                        }

                        for (i in 0 until rowSubset.size) {
                            slicedImageArray[pixelIndex] = rowSubset[i]
                            pixelIndex++
                        }
                    }

                    return slicedImageArray
                }
                is ShortArray -> {
                    val imageArray = image.pix as ShortArray
                    val slicedImageArray = ShortArray(w * h)
                    var pixelIndex = 0
                    var startRowIdx = y-1

                    if(startRowIdx<0){
                        startRowIdx=0
                    }

                    val endRowIdx = startRowIdx + (h - 1)

                    for (row in startRowIdx until endRowIdx) {
                        val wholeSliceStartIdx = (image.w) * row
                        val wholeSliceEndIdx = wholeSliceStartIdx + (image.w - 1)

                        //This wll get a whole row
                        var wholeRow = listOf<Short>()
                        try {
                            wholeRow = imageArray.slice(IntRange(wholeSliceStartIdx, wholeSliceEndIdx))
                        }
                        catch(ex : Exception){
                            GUIMain.loggerService.log(Level.SEVERE, "Error taking row of image array (when slicing). Message: ${ex.message}")
                            GUIMain.loggerService.log(Level.SEVERE, ex.stackTrace)
                        }

                        val subSliceStartIdx = x
                        val subSliceEndIdx = subSliceStartIdx + (w - 1)
                        var rowSubset = listOf<Short>()
                        try {
                            rowSubset = wholeRow.slice(IntRange(subSliceStartIdx, subSliceEndIdx))
                        }
                        catch (ex: Exception) {
                            GUIMain.loggerService.log(Level.SEVERE, "Error taking subset of image array row (when slicing). Message: ${ex.message}")
                            GUIMain.loggerService.log(Level.SEVERE, ex.stackTrace)
                        }

                        for (i in 0 until rowSubset.size) {
                            slicedImageArray[pixelIndex] = rowSubset[i]
                            pixelIndex++
                        }
                    }

                    return slicedImageArray
                }
                is FloatArray -> {
                    val imageArray = image.pix as FloatArray
                    val slicedImageArray = FloatArray(w * h)
                    var pixelIndex = 0
                    var startRowIdx = y-1

                    if(startRowIdx<0){
                        startRowIdx=0
                    }

                    val endRowIdx = startRowIdx + (h - 1)

                    for (row in startRowIdx until endRowIdx) {
                        val wholeSliceStartIdx = (image.w) * row
                        val wholeSliceEndIdx = wholeSliceStartIdx + (image.w - 1)

                        //This wll get a whole row
                        var wholeRow = listOf<Float>()
                        try {
                            wholeRow = imageArray.slice(IntRange(wholeSliceStartIdx, wholeSliceEndIdx))
                        }
                        catch(ex : Exception){
                            GUIMain.loggerService.log(Level.SEVERE, "Error taking row of image array (when slicing). Message: ${ex.message}")
                            GUIMain.loggerService.log(Level.SEVERE, ex.stackTrace)
                        }

                        val subSliceStartIdx = x
                        val subSliceEndIdx = subSliceStartIdx + (w - 1)
                        var rowSubset = listOf<Float>()
                        try {
                            rowSubset = wholeRow.slice(IntRange(subSliceStartIdx, subSliceEndIdx))
                        }
                        catch (ex: Exception) {
                            GUIMain.loggerService.log(Level.SEVERE, "Error taking subset of image array row (when slicing). Message: ${ex.message}")
                            GUIMain.loggerService.log(Level.SEVERE, ex.stackTrace)
                        }

                        for (i in 0 until rowSubset.size) {
                            slicedImageArray[pixelIndex] = rowSubset[i]
                            pixelIndex++
                        }
                    }

                    return slicedImageArray
                }
                else -> {
                    GUIMain.loggerService.log(Level.WARNING, "Image was not of type ByteArray, ShortArray, or FloatArray")
                    return image
                }
            }
        }
        else{
            GUIMain.loggerService.log(Level.WARNING, "Image object was not of type STRIMMPixelBuffer")
            return image
        }
    }

    override fun preStart() {
    }

    override fun postStop() {
    }
}