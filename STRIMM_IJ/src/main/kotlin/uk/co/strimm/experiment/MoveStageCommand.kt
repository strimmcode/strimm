package uk.co.strimm.experiment

import org.scijava.plugin.Plugin
import uk.co.strimm.ExperimentConstants
import uk.co.strimm.plugins.AbstractExperimentCommandPlugin
import uk.co.strimm.plugins.ExperimentCommandPlugin

@Plugin(type = ExperimentCommandPlugin::class)
class MoveStageCommandPlugin : AbstractExperimentCommandPlugin() {
    override var displayName = ExperimentConstants.Commands.MOVE_COMMAND_DISPLAY_NAME
    override var title = ExperimentConstants.Commands.MOVE_COMMAND_TITLE
}

class MoveStageCommand(){

}