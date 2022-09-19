package uk.co.strimm.experiment

import org.scijava.plugin.Plugin
import uk.co.strimm.ExperimentConstants
import uk.co.strimm.plugins.AbstractExperimentCommandPlugin
import uk.co.strimm.plugins.ExperimentCommandPlugin

@Plugin(type = ExperimentCommandPlugin::class)
class TriggerCommandPlugin : AbstractExperimentCommandPlugin() {
    override var displayName = ExperimentConstants.Commands.TRIGGER_COMMAND_DISPLAY_NAME
    override var title = ExperimentConstants.Commands.TRIGGER_COMMAND_TITLE
}

class TriggerCommand(){

}