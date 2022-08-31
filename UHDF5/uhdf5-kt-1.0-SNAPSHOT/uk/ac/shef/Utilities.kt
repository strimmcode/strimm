package uk.ac.shef

/**
 * Construct the absolute path of the uhdf5_jni library (including the system dynamic library extension) from a relative
 * path
 *
 * ### Example Usage
 * ```
 * relativeLibraryPath("libraries/uhdf5") // possible output "/home/user/Documents/MyProject/libraries/uhdf5/uhdf5_jni.so"
 * ```
 *
 * @param[relPath] The relative path
 *
 * @return The absolute path to the uhdf5_jni dynamic library
 *
 * @see System.getProperty
 * @see System.mapLibraryName
 * @see initialiseLibrary
 *
 * @author Elliot Steele
 */
fun relativeLibraryPath(relPath : String = ".")
        = "${System.getProperty("user.dir")}/$relPath/${System.mapLibraryName("uhdf5_jni")}"

/**
 * Initialises the UHDF5 library. This function is idempotent (i.e., it is safe to call it multiple times)
 *
 * **THIS FUNCTION MUST BE CALLED BEFORE USING ANY OF THE NATIVE FUNCTIONS CONTAINED IN THE LIBRARY**
 *
 * ### Example Usage
 * ```kotlin
 * // attempts to load uhdf5_jni dynamic library from the current directory
 * initialiseLibrary()
 *
 * // attempts to load from the "../libraries" path
 * initialiseLibrary(relativeLibraryPath("../libraries"))
 * ```
 *
 * @param[name] The absolute path to the UHDF5 JNI dynamic library, including file extension. To generate a relative
 * path the [relativeLibraryPath] utility function is provided. Defaults to $pwd/uhdf5_jni.<extension>.
 *
 * @exception UnsatisfiedLinkError if the file is not found
 * @exception SecurityException if a [SecurityManager] exists and its [SecurityManager.checkLink] method doesn't allow
 * loading of the specified dynamic library
 *
 * @see System.mapLibraryName
 * @see System.getProperty
 * @see relativeLibraryPath
 *
 * @author Elliot Steele
 */
fun initialiseLibrary(name : String = relativeLibraryPath()) {
    System.load(name)

    val required = requiredLibVersion()
    val actual = JNILibVersion()
    if (required.major != actual.major || required.minor != actual.minor)
        throw U5Exception("DLL version $actual is incompatible with required DLL version $required")
}

data class MinMax(val min : Long, val max : Long)