package uk.ac.shef.planar

import uk.ac.shef.DataType
import uk.ac.shef.File
import uk.ac.shef.MinMax
import uk.ac.shef.UnopenedDataset
import java.time.LocalDateTime

/**
 * Represents the coordinate of a plane in a planar dataset
 *
 * @see [Dataset.write]
 * @see [Dataset.readAs]
 *
 * @param[vals] The coordinate elements
 */
class Coordinate(vararg vals : Long) {
    val arr = longArrayOf(*vals)

    /** Automatically generated. Allows [Coordinate] to behave as expected */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Coordinate

        if (!arr.contentEquals(other.arr)) return false

        return true
    }

    /** Automatically generated. Allows [Coordinate] to behave as expected */
    override fun hashCode(): Int {
        return arr.contentHashCode()
    }
}


/**
 * Description of a dataset's dimensions
 *
 * This class is a JVM wrapper around a native object. Upon [creation][DimensionDescription.create], the parameters are
 * converted into their native equivalents, a native [DimensionDescription] object is created and passed via reference
 * to the JVM for lifetime management. Accessing properties is performed by retrieving the native reference and
 * constructing the JVM object from the data contained in the native property. As a result it should be taken into
 * account that objects must be created every time the property is accessed, which may have performance implications
 * under some circumstances. Native memory is cleaned up in the [finalizer][java.lang.Object.finalize] method.
 *
 * @author Elliot Steele
 */
class DimensionDescription private constructor() {
    private var nativeRef : Long = 0

    private external fun getNAxesExt() : Byte
    private external fun getNamesExt() : Array<String>
    private external fun getDescriptionsExt() : Array<String>?
    private external fun getUnitsExt() : Array<String>?
    private external fun getUnitsPerSampleExt() : DoubleArray?
    private external fun getNamedAxisValuesExt() : Map<String, Array<String>>?

    /** The number of axes */
    val nAxes get() = getNAxesExt()

    /** The names of the axes */
    val names get() = getNamesExt()

    /** The (optional) descriptions of the axes */
    val descriptions get() = getDescriptionsExt()

    /** The (optional) units of the axes */
    val units get() = getUnitsExt()

    /** The (optional) units per sample of the axes */
    val unitsPerSample get() = getUnitsPerSampleExt()

    /** The (optional) names of axes with units marked as "NAMED" */
    val namedAxisValues get() = getNamedAxisValuesExt()

    /**
     * Cleans up the memory associated with the [DimensionDescription] object
     *
     * @author Elliot Steele
     */
    protected external fun finalize()

    /**
     * Prints a textual representation of the [DimensionDescription] instance for debugging purposes
     *
     * @param[pre] A string to be prepended to the beginning of each line of output (useful for formatting)
     *
     * @author Elliot Steele
     */
    fun print(pre : String = "") {
        println("${pre}N_AXES\t\t\t\t:\t${nAxes}")
        println("${pre}NAMES\t\t\t\t:\t${names.contentToString()}")
        println("${pre}DESCRIPTIONS\t\t:\t${descriptions?.contentToString()}")
        println("${pre}UNITS\t\t\t\t:\t${units?.contentToString()}")
        println("${pre}UNITS PER SAMPLE\t:\t${unitsPerSample?.contentToString()}")
        println("${pre}NAMED AXIS VALS\t:\t${if (namedAxisValues == null) "null" else "{"}")
        namedAxisValues?.entries?.forEach { nav ->
            println("${pre}\t${nav.key} : ${nav.value.contentToString()}")
        }?.let { println("${pre}}") }
    }

    companion object {
        /**
         * Creates a Dimension Description object
         *
         * @param[n_axes] The number of axes
         * @param[names] The names of the axes
         * @param[descriptions] The (optional) descriptions of the axes
         * @param[units] The (optional) units of the axes
         * @param[units_per_sample] The (optional) units per sample of the axes
         * @param[named_axis_values] The (optional) names of axes with units marked as "NAMED"
         *
         * @return The created DimensionDescription object
         *
         * @throws uk.ac.shef.U5Exception if the the parameters are invalid (e.g., if the length of provided arrays is
         * inconsistent with n_axes parameter)
         *
         * @author Elliot Steele
         */
        @JvmStatic external fun create(
            n_axes : Byte,
            names : Array<String>,
            descriptions : Array<String>?,
            units : Array<String>?,
            units_per_sample : DoubleArray?,
            named_axis_values : HashMap<String, Array<String>>?
        ) : DimensionDescription
    }
}

/**
 * Description of a planar Dataset
 *
 * This class is a JVM wrapper around a native object. Upon [creation][Description.create], the parameters are
 * converted into their native equivalents, a native [Description] object is created and passed via reference
 * to the JVM for lifetime management. Accessing properties is performed by retrieving the native reference and
 * constructing the JVM object from the data contained in the native property. As a result it should be taken into
 * account that objects must be created every time the property is accessed, which may have performance implications
 * under some circumstances. Native memory is cleaned up in the [finalizer][java.lang.Object.finalize] method.
 *
 * @author Elliot Steele
 */
class Description private constructor() {
    private var nativeRef : Long = 0

    private external fun getNameExt() : String
    private external fun getCreatedExt() : LocalDateTime
    private external fun getDescriptionExt() : String?
    private external fun getAxisDescExt() : DimensionDescription
    private external fun getPlaneDescriptionExt() : DimensionDescription

    /** The name of the dataset */
    val name get() = getNameExt()

    /** The date/time of dataset creation */
    val created get() = getCreatedExt()

    /** The (optional) description of the dataset */
    val description get() = getDescriptionExt()

    /** The (optional) description of the axes of the dataset */
    val axisDescription get() = getAxisDescExt()

    /** The (optional) description of the plane axes */
    val planeDescription get() = getPlaneDescriptionExt()

    /**
     * Cleans up the memory associated with the [Description] object
     *
     * @author Elliot Steele
     */
    protected external fun finalize()

    /**
     * Prints a textual representation of the [Description] instance for debugging purposes
     *
     * @param[pre] A string to be prepended to the beginning of each line of output (useful for formatting)
     *
     * @author Elliot Steele
     */
    fun print(pre : String = "") {
        println("${pre}Name\t\t:\t$name")
        println("${pre}Created\t\t:\t$created")
        println("${pre}description\t:\t$description")
        println("${pre}Axis Description {")
        axisDescription.print("${pre}\t")
        println("${pre}}")
        println("${pre}Plane Description {")
        planeDescription.print("${pre}\t")
        println("${pre}}")
    }

    companion object {
        /**
         * Creates a new [Description] object
         *
         * @param[name] The name of the dataset
         * @param[description] The (optional) description of the dataset
         * @param[axis_description] The description of the axes of the dataset
         * @param[plane_description] The description of the plane axes
         *
         * @return The new [Description] object
         *
         * @author Elliot Steele
         */
        @JvmStatic external fun create(
            name : String,
            description : String?,
            axis_description : DimensionDescription,
            plane_description : DimensionDescription
        ) : Description
    }
}

/**
 * Represents a planar dataset
 *
 * A Dataset instance can be used to read from or write to a planar dataset within a file. Contains a reference to the
 * file that contains it so should not be used after the file has been closed
 *
 * Implements AutoClosable so can (and should) be used with [use] expressions
 *
 * This class is a JVM wrapper around a native object. Upon [creation][Dataset.create] or [parsing][Dataset.parse],
 * the parameters are converted into their native equivalents, a native [Dataset] object is created and passed
 * via reference to the JVM for lifetime management. Accessing properties is performed by retrieving the native
 * reference and constructing the JVM object from the data contained in the native property. As a result it should be
 * taken into account that objects must be created every time the property is accessed, which may have performance
 * implications under some circumstances. Native memory is cleaned up in the [Dataset.close] method.
 * method.
 *
 * @author Elliot Steele
 */
class Dataset private constructor() : AutoCloseable {
    private var nativeRef : Long = 0

    private external fun getDescExt() : Description
    private external fun write(c : Coordinate, data: DataType.RustType)

    /** @suppress */
    external fun readAs(c : Coordinate, type : Byte) : DataType


    /** The dataset description read from the file or used to create the dataset */
    val description get() = getDescExt()

    /**
     * Write data to the dataset
     *
     * @param[c] The coordinate of the plane to write to
     * @param[data] The data to write to the dataset
     *
     * @throws uk.ac.shef.U5Exception if data already exists at the given coordinate
     *
     * @author Elliot Steele
     */
    fun write(c: Coordinate, data : DataType) {
        data.toRustType().use { write(c, it) }
    }

    /**
     * Read data from the dataset
     *
     * Example Use:
     *
     * ```
     * val data = dataset.readAs<DataType.LongType>(coord)
     * // or...
     * val data : DataType.LongType = dataset.readAs(coord)
     * ```
     *
     * @param[T] The type of data to be read (Note: UHDF5 will automatically convert to the requested data type)
     * @param[c] The coordinate to read from
     *
     * @return The data read from the file
     *
     * @author Elliot Steele
     */
    inline fun <reified T : DataType> readAs(c : Coordinate) : T {
        return readAs(c, DataType.toTypeByte<T>()) as T
    }

    /**
     * Read data from the dataset without needing to specify the type to load as. The return type is inferred
     * from the data format on disk
     *
     * @param[c] The coordinate to read from
     *
     * @return The data read from the file
     *
     * @author Elliot Steele
     */
    external fun read(c : Coordinate) : DataType

    external fun getMinMaxCoords() : Array<MinMax>
    external fun getMinCoords() : LongArray
    external fun getMaxCoords() : LongArray

    /**
     * Closes the dataset and frees any resources
     *
     * @author Elliot Steele
     */
    external override fun close()

    companion object {
        /**
         * Parses an [UnopenedDataset], assuming it to be a planar dataset
         *
         * @param[ds] The dataset to parse
         *
         * @return The parsed dataset
         *
         * @throws uk.ac.shef.U5Exception if the parse fails (e.g., if the dataset is malformed or is not a planar dataset)
         *
         * @see UnopenedDataset
         *
         * @author Elliot Steele
         */
        @JvmStatic private external fun parseExt(ds : UnopenedDataset) : Dataset
        @JvmStatic fun parse(ds : UnopenedDataset) : Dataset = ds.use { parseExt(ds) }

        /**
         * Creates a new planar dataset
         *
         * @param[file] The file to create the dataset in
         * @param[description] The description of the dataset
         *
         * @return The newly created dataset
         *
         * @throws uk.ac.shef.U5Exception if the dataset already exists or if the dataset couldn't be created
         *
         * @author Elliot Steele
         */
        @JvmStatic external fun create(file : File, description: Description) : Dataset
    }
}

