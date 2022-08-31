package uk.ac.shef

/**
 * Represents a version number. U5 uses a 3 number version of the form "<major>.<minor>.<patch>". Increments to the
 * patch number represent changes to the file format writer that do not affect contents of files (e.g., performance
 * improvements, bug fixes etc.). Increments to the minor number represent additions/removals of optional fields or
 * additions of required fields. Such changes will not prevent readers written for a previous version from loading the
 * file (although they may miss data from the newer version). Increments to the major number represent removals of
 * required fields or changes in folder structure. Such changes will prevent older readers from reading newer files.
 *
 * This class is a pure JVM class and is converted to the native equivalent when required. The conversion process only
 * reads the values and thus the object is never consumed by calling a native function.
 *
 * @constructor
 *
 * @property[major] The major version number
 * @property[minor] The minor version number
 * @property[patch] The patch number
 *
 * @author Elliot Steele
 */
data class Version(val major : Byte, val minor : Byte, val patch : Byte)

fun requiredLibVersion() = Version(0, 5, 0)
external fun JNILibVersion() : Version

