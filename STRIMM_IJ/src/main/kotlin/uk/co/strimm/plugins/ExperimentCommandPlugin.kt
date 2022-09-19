package uk.co.strimm.plugins

import net.imagej.ImageJPlugin

abstract class AbstractExperimentCommandPlugin : ExperimentCommandPlugin {

}

interface ExperimentCommandPlugin : ImageJPlugin{
    var title : String
    var displayName : String
    fun initialise() = Unit
}