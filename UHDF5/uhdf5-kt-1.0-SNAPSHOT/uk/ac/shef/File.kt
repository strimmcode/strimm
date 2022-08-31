package uk.ac.shef

import java.time.LocalDateTime

/**
 * Represents the metadata associated with a U5 file
 *
 * This class is a JVM wrapper around a native object. Upon [creation][FileDescription.new], the parameters are
 * converted into their native equivalents, a native [FileDescription] object is created and passed via reference to the
 * JVM for lifetime management. Accessing properties is performed by retrieving the native reference and constructing
 * the JVM object from the data contained in the native property. As a result it should be taken into account that
 * objects must be created every time the property is accessed which may have performance implications under some
 * circumstances. Native memory is cleaned up in the [finalizer][java.lang.Object.finalize] method.
 *
 * @see File
 *
 * @author Elliot Steele
 */
class FileDescription private constructor()
{
    private var nativeRef : Long = 0

    private external fun getVersionExt() : Version
    private external fun getAuthorExt() : String?
    private external fun getAffiliationExt() : String?
    private external fun getDescriptionExt() : String?
    private external fun getSoftwareExt() : String?
    private external fun getCreatedExt() : LocalDateTime

    /** The version of the UHDF5 format used */
    val version : Version
        get() = getVersionExt()

    /** The author of the UHDF5 file */
    val author : String?
        get() = getAuthorExt()

    /** The affiliation of the author of the UHDF5 file */
    val affiliation : String?
        get() = getAffiliationExt()

    /** A description of the contents of the UHDF5 file */
    val description : String?
        get() = getDescriptionExt()

    /** The name of the software used to create the UHDF5 file */
    val software : String?
        get() = getSoftwareExt()

    /** The initial creation date of the UHDF5 file */
    val created : LocalDateTime
        get() = getCreatedExt()

    /**
     * Releases the native memory associated with the [FileDescription] object
     *
     * @author Elliot Steele
     */
    protected external fun finalize()


    /**
     * Prints a textual representation of the [FileDescription] instance for debugging purposes
     *
     * @param[pre] A string to be prepended to the beginning of each line of output (useful for formatting)
     *
     * @author Elliot Steele
     */
    fun print(pre : String = "") {
        println("${pre}VERSION\t\t:\t${version}")
        println("${pre}AUTHOR\t\t:\t${author}")
        println("${pre}AFFILIATION\t:\t${affiliation}")
        println("${pre}DESCRIPTION\t:\t${description}")
        println("${pre}SOFTWARE\t:\t${software}")
        println("${pre}CREATED\t\t:\t${created}")
    }

    companion object {
        /**
         * Creates a new [FileDescription] object. The creation date and file format version are automatically created
         * by the implementation and therefore do not need to be provided
         *
         * @param[author] (Optional) The author of the file
         * @param[author] (Optional) The affiliation of the author of the file
         * @param[description] (Optional) A description of the contents of the file
         * @param[software] (Optional) The name of the software that created the file
         *
         * @return The constructed FileDescription object
         *
         * @author Elliot Steele
         */
        @JvmStatic external fun new(author : String?,
                                    affiliation : String?,
                                    description : String?,
                                    software : String?) : FileDescription
    }
}

/**
 * Represents a UHDF5 file
 *
 * An instance of a file object can be used to query contained datasets ([listDatasets], [get]),
 * retrieve metadata ([desc], [libFileVersion]) and is also required to create new datasets.
 *
 * Implements AutoClosable so can (and should) be used with [use] expressions
 *
 * This class is a JVM wrapper around a native object. Upon creation, the parameters are converted into their native
 * equivalents, a native [File] object is created and passed via reference to the JVM for lifetime
 * management. Accessing properties is performed by retrieving the native reference and constructing the JVM object
 * from the data contained in the native property. As a result it should be taken into account that objects must be
 * created every time the property is accessed which may have performance implications under some circumstances. Native
 * memory is released in the [File.close] method.
 *
 * @see FileDescription
 *
 * @author Elliot Steele
 */
class File private constructor() : AutoCloseable {
    private var nativeRef : Long = 0

    /**
     * Closes the file and releases native resources associated with the file
     *
     * _NB: This does clean up any open datasets, these should therefore already have been closed before calling this
     *      method and should not be accessed after calling this function_
     *
     * @author Elliot Steele
     */
    external override fun close()


    /**
     * Returns an array of [UnopenedDatasets][UnopenedDataset] each representing a dataset contained in the file
     *
     * @return The [UnopenedDatasets][UnopenedDataset] contained within the file
     *
     * @see UnopenedDataset
     *
     * @author Elliot Steele
     */
    external fun listDatasets() : Array<UnopenedDataset>

    /**
     * Gets a dataset by name if it exists
     *
     * @param[name] The name of the dataset
     *
     * @return The [UnopenedDataset] representing the dataset
     *
     * @throws U5Exception if the dataset does not exist
     *
     * @author Elliot Steele
     */
    external operator fun get(name : String) : UnopenedDataset

    /**
     * Gets a dataset by index
     *
     * @param[i] The index of the dataset to retrieve from the file
     *
     * @return The [UnopenedDataset] representing the dataset
     *
     * @throws U5Exception if the index is out of range
     *
     * @author Elliot Steele
     */
    external operator fun get(i : Int) : UnopenedDataset

    /**
     * Gets the file metadata
     *
     * @return A FileDescription object representing the file metadata
     *
     * @see FileDescription
     *
     * @author Elliot Steele
     */
    val desc : FileDescription
        get() = getDescExt()

    private external fun getDescExt() : FileDescription

    companion object {
        /**
         * Gets the file format version
         *
         * @return The file format version used to write new UHDF5 files
         *
         * @see Version
         *
         * @author Elliot Steele
         */
        @JvmStatic external fun libFileVersion() : Version

        /**
         * Creates a new file
         *
         * @param[filename] The name of the file (this may be an absolute relative file path)
         * @param[desc] The [FileDescription] describing the metadata for the file
         *
         * @return A handle to the created UHDF5 file
         *
         * @throws U5Exception if the file could not be created (for example if the user doesn't have write permissions)
         *
         * @see FileDescription
         *
         * @author Elliot Steele
         */
        @JvmStatic external fun create(filename : String, desc : FileDescription) : File

        /**
         * Opens an existing UHDF5 file
         *
         * @param[filename] The name of the file (this may be an absolute relative file path)
         *
         * @return A handle to the opened UHDF5 file
         *
         * @throws U5Exception if the file could not be opened (for example if the file doesn't exist)
         *
         * @author Elliot Steele
         */
        @JvmStatic external fun open(filename : String) : File
    }
}

