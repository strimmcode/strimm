package uk.ac.shef

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract


/**
 * Specifies the [File] creation DSL
 *
 * @param[name] The name of the file to be created
 *
 * @see File
 *
 * @author Elliot Steele
 */
class FileCreator(val name : String) {
    /**
     * Reference to the file description object used to initialise the file. If not specified the DSL will initialise
     * the file with all optional metadata set to null. This should be specified before creating datasets unless the
     * default value is desired.
     *
     * @see FileDescription
     *
     * @author Elliot Steele
     */
    var metadata : FileDescription? = null

    /**
     * The created UHDF5 file. Calls build on first access
     *
     * @see File
     *
     * @author Elliot Steele
     */
    val file by lazy { build() }

    private fun build() : File {
        val name = name
        val metadata = metadata ?: FileDescription.new(null, null, null, null)
        return File.create(name, metadata)
    }

    /**
     * Specifies the metadata property using the [FileDescCreator] DSL
     *
     * @param[init] The metadata DSL
     *
     * @see FileDescCreator
     * @see FileDescription
     *
     * @author Elliot Steele
     */
    inline fun metadata(init : FileDescCreator.() -> Unit) {
        if (metadata != null) throw U5Exception("Metadata already specified (or dataset specified before metadata)")
        FileDescCreator().apply(init).build().apply { metadata = this }
    }

    /**
     * Specifies the [FileDescription] DSL
     *
     * @see FileDescription
     *
     * @author Elliot Steele
     */
    class FileDescCreator {
        /**
         * The author of the file
         *
         * @author Elliot Steele
         */
        var author : String? = null

        /**
         * The affiliation of the author of the file
         *
         * @author Elliot Steele
         */
        var affiliation : String? = null

        /**
         * A description of the contents of the file
         *
         * @author Elliot Steele
         */
        var description : String? = null

        /**
         * The name of the software used to create the file
         *
         * @author Elliot Steele
         */
        var software : String? = null

        /**
         * Constructs the [FileDescription] object from the specified DSL
         *
         * @return The [FileDescription] specified by the [FileDescCreator] properties
         *
         * @see FileDescription
         *
         * @author Elliot Steele
         */
        fun build() : FileDescription = FileDescription.new(author, affiliation, description, software)
    }
}

/**
 * Creates a [FileCreator] object, allowing definition of the contents of a [File] via the UHDF5 File Creation DSL
 *
 * @see File
 * @see FileCreator
 *
 * @param[name] The name of the file to be created
 * @param[init] The UHDF5 File Creation DSL
 *
 * @return The created file
 *
 * @author Elliot Steele
 */
inline fun buildFile(name : String, init : FileCreator.() -> Unit) : File = FileCreator(name).apply(init).file

/**
 * Same as [buildFile] but utilises the Experimental "Contracts" feature to inform the compiler that [init] is only
 * executed once. This allows vals that are external to the DSL to be initialised from within the DSL, avoiding the
 * need for lateinit vars, e.g.,
 *
 * ```
 * // This is required with buildFile:
 * latinit var dataset : PlanarDataset
 * buildfile("test.h5") {
 *      dataset = planar("Some Data") { ... }
 * }
 *
 * // With contracts:
 * val dataset : PlanarDataset
 * buildfile("test.h5") {
 *      dataset = planar("Some Data") { ... }
 * }
 * ```
 *
 * @param[name] The name of the file to be created
 * @param[init] The UHDF5 File Creation DSL
 *
 * @see buildFile
 *
 * @author Elliot Steele
 */
@ExperimentalContracts
inline fun buildFileExperimentalContracts(name : String, init : FileCreator.() -> Unit) : File {
    contract {
        callsInPlace(init, InvocationKind.EXACTLY_ONCE)
    }
    return FileCreator(name).apply(init).file
}


