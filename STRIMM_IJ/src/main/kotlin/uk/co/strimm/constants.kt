package uk.co.strimm

import java.awt.Color

val CAMERA_FEED_BACKGROUND_COLOUR = Color.decode("#93B8C9") //Pastel blue
val EPHYS_FEED_BACKGROUND_COLOR = Color.decode("#17ec72") //Pastel green
val DEFAULT_TRACE_RENDER_TYPE = TraceRenderType.RESIZE_AS_NEEDED //TODO this should be overwritten by the settings

/************
 * Use this file to specify any values that will be constant. For example permanent widths and heights, component
 * texts etc...
 *************/

class Program{
    companion object {
        const val PROGRAM_DEFAULT_HEIGHT = 1280
        const val PROGRAM_DEFAULT_WIDTH = 900
    }
}

class Paths{
    companion object {
        const val SETTINGS_FILE_PATH = "./settings.json"
        const val LOG_FILE_DIRECTORY = "./log"
        const val EXPERIMENT_CONFIG_FOLDER = "./ExperimentConfigurations"
        const val EXPERIMENT_OUTPUT_FOLDER = "./ExperimentOutput"
        const val CAMERA_METADATA_PREFIX = "camerametadata"
        const val TRACE_DATA_PREFIX = "traceoutput"
        const val LUTS_FOLDER = "./luts"
        const val DEVICE_ADAPTERS_FOLDER = "./DeviceAdapters"
        const val PROTOCOLS_FOLDER = ".\\Protocols\\" //Slashes have to be backward to work with NIDAQ
    }

    class Icons{
        companion object {
            const val STRIMM_LOGO_ICON = "/icons/main_strimm_logo_bolder.png"
            const val TOOLTIP_ICON = "/icons/tooltip.png"
            const val SHIFT_X_AXIS_ICON = "/icons/shift_xAxis_Icon.png"
            const val SHIFT_Y_AXIS_ICON = "/icons/shift_yAxis_Icon.png"
            const val LOAD_ICON = "/icons/load.png"
            const val ROI_SPEC_COMPLETE_ICON = "/icons/roiComplete.png"
            const val START_ICON = "/icons/start.png"
            const val STOP_ICON = "/icons/stop.png"
            const val RESIZE_ROI_ICON = "/icons/resize_roi.png"
            const val FULL_VIEW_ICON = "/icons/full_view.png"
            const val LOAD_PREV_ICON = "/icons/load_prev_experiment.png"
            const val SETTINGS_WINDOW_ICON = "/icons/settings-icon-png-10-transparent.png"
            const val EXPERIMENT_BUILDER_WINDOW_ICON = "/icons/build_tools.png"
            const val SAVE_JSON_ICON = "/icons/save_json.png"

        }
    }

    class Files{
        companion object {
            const val ACQUISITION_FILE_PREFIX = "strimm_exp"
        }
    }
}

class SettingKeys{
    class GeneralSettings{
        companion object {
            const val GROUP_NAME = "General"
            const val THEME = "dark"
        }
    }
    class TraceSettings{
        companion object {
            const val GROUP_NAME = "Trace"
            const val DEFAULT_X_AXIS_LOWERBOUND = "defaultXAxisLowerBound"
            const val DEFAULT_X_AXIS_UPPERBOUND = "defaultXAxisUpperBound"
            const val DEFAULT_Y_AXIS_LOWERBOUND = "defaultYAxisLowerBound"
            const val DEFAULT_Y_AXIS_UPPERBOUND = "defaultYAxisUpperBound"
            const val DEFAULT_X_AXIS_NUM_POINTS = "defaultXAxisNumPoints"
            const val DEFAULT_RENDER_MODE = "defaultRenderMode"
        }
    }
    class CameraSettings{
        companion object {
            const val GROUP_NAME = "Camera"
        }
    }
}

class ComponentTexts{
    class ExperimentBuilder{
        companion object {
            const val EXPERIMENT_BUILDER_WINDOW_TITLE = "Experiment Builder"
            const val INPUT_TREE_TITLE = "Input feeds"
            const val CONDITION_TREE_TITLE = "Conditions"
            const val COMMAND_TREE_TITLE = "Commands"
        }
    }
    class CameraWindow{
        companion object {
            const val PLUGIN_TITLE_SUFFIX = "CameraFeed"
        }
    }
    class TraceWindow{
        companion object {
            const val RENDER_MODE_MENU = "Render mode"
            const val RENDER_AND_CLEAR = "Render and clear"
            const val RENDER_AND_OVERWRITE = "Render and overwrite"
            const val RENDER_AND_SCROLL = "Render and scroll"
            const val RESIZE_AS_NEEDED = "Resize as needed"
            const val SCROLLBAR_FOLLOW_TOGGLE = "Toggle follow"
            const val SAVE_BUTTON_TEXT = "Save"
            const val PLUGIN_TITLE_SUFFIX = "TraceFeed"
        }
    }
    class SettingsWindow{
        companion object {
            const val SETTINGS_WINDOW_TITLE = "STRIMM settings"
            const val SAVE_BUTTON = "Save"
            const val SAVE_AND_EXIT_BUTTON = "Save and Exit"
            const val EXIT_BUTTON = "Exit"
        }
    }
    class MetaDataWindow{
        companion object {
            const val PLUGIN_TITLE_SUFFIX = "MetaData"
        }
    }
    class TraceExportDialog{
        companion object {
            const val EXPORT_DIALOG_TITLE = "Trace Export Options"
            const val EXPORT_LABEL = "Export (seconds)"
            const val FROM_LABEL = "from:"
            const val TO_LABEL = "to:"
            const val DELIMITER_TYPE_LABEL = "Delimiter:"
            const val TIME_EXPORT_RANGE_ERROR = "Time values must be numbers and within the data's timeAcquired range"
            const val OK_BUTTON = "OK"
            const val CANCEL_BUTTON = "Cancel"
        }
    }

    class AcquisitionButtons{
        companion object {
            const val LOAD_BUTTON_TOOLTIP = "Load experiment configuration"
            const val ROICOMPLETE_BUTTON_TOOLTIP = "Done specifying ROIs"
            const val START_BUTTON_TOOLTIP = "Start acquisition"
            const val STOP_BUTTON_TOOLTIP = "Stop acquisition"
            const val RESIZE_ROI_TOOLTIP = "Resize to ROI"
            const val FULL_VIEW_TOOLTIP = "Expand to full view"
            const val LOAD_PREV_EXPERIMENT_TOOLTIP = "Load a previously acquired experiment"
            const val SCALE_IMAGE_TOOLTIP = "Scale image"
        }
    }
    class AcquisitionDialogs{
        companion object {
            const val ERROR_CREATING_GRAPH = "Error creating stream graph. See logs for details"
            const val STREAM_CREATE_SUCCESS = "Configuration loaded successfully!...Now loading components"
            const val ERROR_LOADING_EXPCONFIG = "Error loading experiment configuration. See logs for details"
            const val ERROR_FINDING_CONFIGURATIONS = "Couldn't find any experiment configurations"
            const val SPECIFY_ROI_PROMPT = "Specify any trace from ROIs, then click the \"complete\" button"
            const val RESIZE_MUST_BE_RECT = "Resize must be from a rectangle overlay"
            const val COULD_NOT_FIND_ROI_FOR_RESIZE = "Could not find overlay to use for resizing"
        }
    }
}

class CssClasses {
    companion object {
        const val VERTICAL_CURSOR = "vertical-cursor"
        const val EXPERIMENT_TREE_BOX = "experiment-tree-box"
        const val EXPERIMENT_TREE_BOX_LABEL = "tree-box-label"
        const val EXPERIMENT_SUMMARY = "experiment-summary"
    }
}

class FileExtensions{
    companion object {
        const val TEXT_FILE = "txt"
        const val CSV_FILE = "csv"
        const val H5_FILE = ".h5"
        const val LOG_FILE_EXT = ".log"
        val COMMA_DELIMITER = Pair("Comma",",")
        val TAB_DELIMITER = Pair("Tab","\t")
        val SEMICOLON_DELIMITER = Pair("Semicolon",";")
        val DELIMITERS = hashMapOf(COMMA_DELIMITER,TAB_DELIMITER,SEMICOLON_DELIMITER)
    }
}

class ActorConstants{
    companion object {
        const val MAIN_ACTOR_NAME = "StrimmActor"
        const val FILE_WRITE_ACTOR_NAME = "FileWriterActor"
    }
}

class ExperimentConstants{
    class Acquisition{
        companion object {
            const val AVERAGE_ROI_METHOD_NAME = "averageROI"
            const val SNAP_IMAGE_METHOD_NAME = "snapImage"
            const val GENERATE_IMAGE_METHOD_NAME = "generateImage"
            const val GET_TRACE_DATA_METHOD_NAME = "getTraceData"
            const val CONFIGURED_CAMERA_METHOD_NAME = "ConfiguredCamera"
            const val TIMES_DATASET_SUFFIX = "_Times"
            const val TRACE_DATA_NIDAQ_METHOD_NAME = "Trace Data Method NIDAQ"
            const val TRACE_DATA_KEYBOARD_METHOD_NAME = "Trace Data Method Keyboard"
            const val A_KEYBOARD_METHOD_NAME = "KeyboardA"
            const val RANDOM_TRACE_SOURCE_METHOD_NAME = "RandomTraceSource"
            const val CONSTANT_TRACE_SOURCE_METHOD_NAME = "ConstantTraceSource"
            const val CONSTANT_VECTOR_SOURCE_METHOD_NAME = "ConstantVectorSource"
            const val SINE_WAVE_SOURCE_METHOD_NAME = "SineWaveSource"
            const val SQUARE_WAVE_SOURCE_METHOD_NAME = "SquareWaveSource"
            const val TIME_TERMCOND = "time"
            const val DATA_TERMCOND = "data"
            const val KEYBOARD_TERMCOND = "keyboard"
        }
    }
    class Commands{
        companion object {
            const val MOVE_COMMAND_TITLE = "moveStage"
            const val MOVE_COMMAND_DISPLAY_NAME = "Move stage"
            const val TRIGGER_COMMAND_TITLE = "triggerStimulus"
            const val TRIGGER_COMMAND_DISPLAY_NAME = "Trigger stimulus"
        }
    }

    class ConfigurationProperties{
        companion object {
            const val IMAGE_INPUT_TYPE = "Image"
            const val TRACE_INPUT_TYPE = "Trace"
            const val IMAGE_OUTPUT_TYPE = "Image"
            const val TRACE_OUTPUT_TYPE = "Trace"
            const val DISPLAY = "Display"
            const val STORE = "Store"
            const val ROI_RECTANGLE_TYPE = "Rectangle"
            const val ROI_ELLIPSE_TYPE = "Ellipse"
            const val ROI_POLYGON_TYPE = "Polygon"
            const val NEW_WINDOW = "New trace window"

            const val DEFAULT_NUM_TRACE_POINTS = 500

            //TODO - link these up to a new settings group. However these should be advanced settings that should only
            //TODO - be changed when someone know's what they're doing
            const val IMAGE_BUFFER_SIZE = 1
            const val DEFAULT_IMAGE_THROTTLE_FPS = 5 //fps
            const val IMAGE_SOURCE_INTERVAL_MS = 0.toLong()
            const val IMAGE_THROTTLE_DURATION_SEC = 1.toLong()
            const val TRACE_GROUPING_AMOUNT = 1
            const val TRACE_GROUPING_DURATION_MS = 1.toLong()
            const val TRACE_SOURCE_INTERVAL_MS = 0.toLong()

            const val ANALOGUE_IN_TYPE = 1
            const val ANALOGUE_OUT_TYPE = 2
            const val DIGITAL_IN_TYPE = 3
            const val DIGITAL_OUT_TYPE = 4
        }
    }
}