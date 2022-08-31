class AcquisitionMethods {
    class SnapImageOpenCV(override var name: String,  override var description: String) : ImageMethod(){
        companion object     {
            val openCV = OpenCV()
            var clockCounter = 0.0
        }
        override fun runMethod(vararg inputs: Any): STRIMMImage {
            return openCV.run()  //this will block until triggered
        }
    }

    class EmptyImage(override var name: String, override var description: String) : ImageMethod(){
        var numMessages = 0
        override fun runMethod(vararg inputs: Any): STRIMMImage {
            if (AkkaStream.globalFlag) {
//
                if (numMessages % 10 == 0 || numMessages == 0) {
                    println("Returning empty image $numMessages")
                }

                if (numMessages == 0){
                    println("global flag is TRUE")
                }

                numMessages++
            }
            else{
                println("FALSE")
            }
            return STRIMMImage("", byteArrayOf(1, 2, 3, 4, 5), 1, 1, 1, 5)
        }
    }
}