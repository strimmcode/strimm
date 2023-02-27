package uk.co.strimm.sourceMethods

import com.opencsv.CSVReader
import uk.co.strimm.STRIMMBuffer
import uk.co.strimm.STRIMMBufferFileLoader
import uk.co.strimm.STRIMMPixelBuffer
import uk.co.strimm.experiment.Source
import uk.co.strimm.gui.GUIMain
import java.io.FileReader

//HF5SourceMethod
// take an h5 experimental file and recreate the samples and timings
//
//TODO needs further expansion and development for multiple cameras and trace sources
//
//
class HF5SourceMethod() : SourceBaseMethod() {
    override lateinit var properties : HashMap<String, String>
    var serialiser = STRIMMBufferFileLoader()
    lateinit var buffer : List<STRIMMBuffer>
    var cnt = 0
    var timeDelay = 30.0

    override fun init(source: Source) {
        this.source = source
        loadCfg()
        val HF5sz = properties["HF5sz"]!!
        val HF5desc = properties["HF5desc"]!!
        val HF5dataset = properties["HF5dataset"]!!
        timeDelay = properties["FrameIntervalMs"]!!.toDouble()
      //  buffer = serialiser.LoadDatasetFromHDF5(HF5sz, HF5desc, HF5dataset) as List<STRIMMBuffer>
    }
    override fun run(): STRIMMBuffer? {
        Thread.sleep(timeDelay.toLong())//simulate blocking delay
        val ccc = cnt
        (buffer[ccc] as STRIMMPixelBuffer).timeAcquired = timeDelay*cnt
        cnt++
        if (cnt == buffer.size) cnt = 0
        return buffer[ccc]
    }
    override fun postStop() {
    }
    override fun preStart() {
    }
}