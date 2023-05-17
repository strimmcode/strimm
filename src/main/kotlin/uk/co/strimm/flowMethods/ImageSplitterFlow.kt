package uk.co.strimm.flowMethods

import akka.actor.ActorRef
import mmcorej.CMMCore
import uk.co.strimm.STRIMMBuffer
import uk.co.strimm.STRIMMPixelBuffer
import uk.co.strimm.experiment.Flow
import uk.co.strimm.gui.GUIMain
import java.util.logging.Level

class ImageSplitterFlow : FlowMethod {
    lateinit var flow: Flow
    override lateinit var properties: HashMap<String, String>
    var core : CMMCore? = CMMCore()

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
        val slicedImage = sliceImage(image[0])
        val imageToReturn = image[0] as STRIMMPixelBuffer
        imageToReturn.pix = slicedImage
        imageToReturn.w = w
        imageToReturn.h = h
        return imageToReturn
    }

    fun sliceImage(image: STRIMMBuffer) : Any? {
        if (image is STRIMMPixelBuffer) {
            when (image.imageData) {
                is ByteArray -> {
                    val imageArray = image.pix as ByteArray
                    val slicedImageArray = ByteArray(w * h)
                    var pixelIndex = 0
                    val startRowIdx = if(y>0) y-1 else 0
                    val endRowIdx = startRowIdx + h-1

                    for (row in startRowIdx until endRowIdx) {
                        val wholeSliceStartIdx = (image.w)*row
                        val wholeSliceEndIdx = wholeSliceStartIdx + (image.w-1)
                        //This wll get a whole row
                        println("startRowIdx=$startRowIdx, endRowIdx=$endRowIdx")
                        println("imageArray.size=${imageArray.size}, wholeSliceStartIdx=$wholeSliceStartIdx, wholeSliceEndIdx=$wholeSliceEndIdx")
                        val wholeRow = imageArray.slice(IntRange(wholeSliceStartIdx, wholeSliceEndIdx))

                        val subSliceStartIdx = x
                        val subSliceEndIdx = subSliceStartIdx + (w-1)
                        val rowSubset = wholeRow.slice(IntRange(subSliceStartIdx, subSliceEndIdx))

                        for(i in 0 until rowSubset.size){
                            slicedImageArray[pixelIndex] = rowSubset[i]
                            pixelIndex++
                        }
                    }
                    return slicedImageArray
            }
            is ShortArray -> {
                print("Short")
            }
            else -> {
                print("Float")
            }
        }
    }
        return 0
    }

    override fun preStart() {
    }

    override fun postStop() {
    }
}