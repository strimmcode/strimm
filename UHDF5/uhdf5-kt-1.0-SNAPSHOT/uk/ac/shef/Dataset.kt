package uk.ac.shef

import kotlin.reflect.KClass

/**
 * Abstract representation of data that can be saved to a UHDF5 Dataset
 *
 * Each concrete implementation of DataType provides a constructor taking a 1D primitive array and an implementation of
 * the [toRustType] function (used in the write function to convert the java array to a rust Vec backed type)
 *
 * @param[shape] The shape of the data. Data is stored in 1 dimensional primitive arrays, this informs UHDF5 of the
 * dimensionality of the array
 *
 * @author Elliot Steele
 */
sealed class DataType(val shape : LongArray) {
    abstract val dat : Any

    /** @suppress */
    abstract fun toRustType() : RustType

    class LongType(override val dat : LongArray, shape : LongArray) : DataType(shape) {
        external override fun toRustType() : RustType
    }

    class IntType(override val dat : IntArray, shape : LongArray) : DataType(shape) {
        external override fun toRustType() : RustType
    }

    class ShortType(override val dat : ShortArray, shape : LongArray) : DataType(shape) {
        external override fun toRustType() : RustType
    }

    class ByteType(override val dat : ByteArray, shape : LongArray) : DataType(shape) {
        external override fun toRustType() : RustType
    }

    class ULongType(override val dat : LongArray, shape : LongArray) : DataType(shape) {
        external override fun toRustType() : RustType
    }

    class UIntType(override val dat : IntArray, shape : LongArray) : DataType(shape) {
        external override fun toRustType() : RustType
    }

    class UShortType(override val dat : ShortArray, shape : LongArray) : DataType(shape) {
        external override fun toRustType() : RustType
    }

    class UByteType(override val dat : ByteArray, shape : LongArray) : DataType(shape) {
        external override fun toRustType() : RustType
    }

    class FloatType(override val dat : FloatArray, shape : LongArray) : DataType(shape) {
        external override fun toRustType() : RustType
    }

    class DoubleType(override val dat : DoubleArray, shape : LongArray) : DataType(shape) {
        external override fun toRustType() : RustType
    }

    class BoolType(override val dat : BooleanArray, shape : LongArray) : DataType(shape) {
        external override fun toRustType() : RustType
    }

    /** @suppress */
    class RustType private constructor() : AutoCloseable {
        private var nativeRef : Long = 0
        external override fun close()
    }

    companion object {
        /**
         * Creates a Long (64-bit) DataType object
         *
         * @param[dat] The data
         * @param[shape] The data dimensions
         *
         * @return DataType object representing the data
         *
         * @author Elliot Steele
         */
        fun from(dat : LongArray, shape : LongArray) = LongType(dat, shape)

        /**
         * Creates an Int (32-bit) DataType object
         *
         * @param[dat] The data
         * @param[shape] The data dimensions
         *
         * @return DataType object representing the data
         *
         * @author Elliot Steele
         */
        fun from(dat : IntArray, shape : LongArray) = IntType(dat, shape)

        /**
         * Creates a Short (16-bit) DataType object
         *
         * @param[dat] The data
         * @param[shape] The data dimensions
         *
         * @return DataType object representing the data
         *
         * @author Elliot Steele
         */
        fun from(dat : ShortArray, shape : LongArray) = ShortType(dat, shape)

        /**
         * Creates a Byte (8-bit) DataType object
         *
         * @param[dat] The data
         * @param[shape] The data dimensions
         *
         * @return DataType object representing the data
         *
         * @author Elliot Steele
         */
        fun from(dat : ByteArray, shape : LongArray) = ByteType(dat, shape)

        /**
         * Creates a Float (32-bit floating point) DataType object
         *
         * @param[dat] The data
         * @param[shape] The data dimensions
         *
         * @return DataType object representing the data
         *
         * @author Elliot Steele
         */
        fun from(dat : FloatArray, shape : LongArray) = FloatType(dat, shape)

        /**
         * Creates a Double (64-bit floating point) DataType object
         *
         * @param[dat] The data
         * @param[shape] The data dimensions
         *
         * @return DataType object representing the data
         *
         * @author Elliot Steele
         */
        fun from(dat : DoubleArray, shape : LongArray) = DoubleType(dat, shape)

        /**
         * Creates a Boolean DataType object
         *
         * @param[dat] The data
         * @param[shape] The data dimensions
         *
         * @return DataType object representing the data
         *
         * @author Elliot Steele
         */
        fun from(dat : BooleanArray, shape : LongArray) = BoolType(dat, shape)

        /**
         * Creates a ULong (64-bit, Unsigned) DataType object
         *
         * @param[dat] The data
         * @param[shape] The data dimensions
         *
         * @return DataType object representing the data
         *
         * @author Elliot Steele
         */
        @ExperimentalUnsignedTypes
        fun from(dat : ULongArray, shape : LongArray) = ULongType(dat.asLongArray(), shape)

        /**
         * Creates an UInt (32-bit, Unsigned) DataType object
         *
         * @param[dat] The data
         * @param[shape] The data dimensions
         *
         * @return DataType object representing the data
         *
         * @author Elliot Steele
         */
        @ExperimentalUnsignedTypes
        fun from(dat : UIntArray, shape : LongArray) = UIntType(dat.asIntArray(), shape)

        /**
         * Creates a UShort (16-bit, Unsigned) DataType object
         *
         * @param[dat] The data
         * @param[shape] The data dimensions
         *
         * @return DataType object representing the data
         *
         * @author Elliot Steele
         */
        @ExperimentalUnsignedTypes
        fun from(dat : UShortArray, shape : LongArray) = UShortType(dat.asShortArray(), shape)

        /**
         * Creates a UByte (8-bit, Unsigned) DataType object
         *
         * @param[dat] The data
         * @param[shape] The data dimensions
         *
         * @return DataType object representing the data
         *
         * @author Elliot Steele
         */
        @ExperimentalUnsignedTypes
        fun from(dat : UByteArray, shape : LongArray) = UByteType(dat.asByteArray(), shape)

        /**
         * Creates a ULong (64-bit, Unsigned) DataType object
         *
         * @param[dat] The data
         * @param[shape] The data dimensions
         *
         * @return DataType object representing the data
         *
         * @author Elliot Steele
         */
        fun fromUnsigned(dat : LongArray,   shape : LongArray) = ULongType(dat, shape)

        /**
         * Creates an UInt (32-bit, Unsigned) DataType object
         *
         * @param[dat] The data
         * @param[shape] The data dimensions
         *
         * @return DataType object representing the data
         *
         * @author Elliot Steele
         */
        fun fromUnsigned(dat : IntArray,    shape : LongArray) = UIntType(dat, shape)

        /**
         * Creates a UShort (16-bit, Unsigned) DataType object
         *
         * @param[dat] The data
         * @param[shape] The data dimensions
         *
         * @return DataType object representing the data
         *
         * @author Elliot Steele
         */
        fun fromUnsigned(dat : ShortArray,  shape : LongArray) = UShortType(dat, shape)

        /**
         * Creates a UByte (8-bit, Unsigned) DataType object
         *
         * @param[dat] The data
         * @param[shape] The data dimensions
         *
         * @return DataType object representing the data
         *
         * @author Elliot Steele
         */
        fun fromUnsigned(dat : ByteArray,   shape : LongArray) = UByteType(dat, shape)


        /** @suppress */
        fun <T : DataType> toTypeByte(clazz : KClass<T>) = when (clazz)
        {
            LongType::class -> 0
            IntType::class -> 1
            ShortType::class -> 2
            ByteType::class -> 3

            DoubleType::class -> 4
            FloatType::class -> 5
            BoolType::class -> 6

            ULongType::class -> 7
            UIntType::class -> 8
            UShortType::class -> 9
            UByteType::class -> 10

            DataType::class -> -1

            else -> throw RuntimeException("Unreachable")
        }.toByte()

        /** @suppress */
        inline fun <reified T : DataType> toTypeByte() = toTypeByte(T::class)
    }
}

/**
 * Represents a dataset contained within a UHDF5 file that has not yet been parsed. Allows access to all metadata that
 * is common to all dataset types.
 *
 * This class is a JVM wrapper around a native object. Upon creation, a native [UnopenedDataset] object is created and
 * passed via reference to the JVM for lifetime management. Accessing properties is performed by retrieving the native
 * reference and constructing the JVM object from the data contained in the native property. As a result it should be
 * taken into account that objects must be created every time the property is accessed which may have performance
 * implications under some circumstances. Native memory is cleaned up in the [close][UnopenedDataset.close]
 * method.
 *
 * NB: The native implementation of this class holds references to parts of the HDF5 file so failing to close them
 * will result in the file handle being kept alive, which will prevent operations such as deletion on some operating
 * systems (most notably Windows). Parsers are expected to call close on the [UnopenedDataset]
 *
 * @author Elliot Steele
 */
class UnopenedDataset private constructor() : AutoCloseable {
    private var nativeRef : Long = 0

    private external fun getNameExt() : String
    private external fun getDatasetTypeExt() : String
    private external fun getVersionExt() : Version

    /** The name of the dataset */
    val name : String
        get() = getNameExt()

    /** The name of the dataset type */
    val datasetType : String
        get() = getDatasetTypeExt()

    /** The version of the dataset type */
    val version : Version
        get() = getVersionExt()


    /**
     * Prints a textual representation of the [UnopenedDataset] instance for debugging purposes
     *
     * @param[pre] A string to be prepended to the beginning of each line of output (useful for formatting)
     *
     * @author Elliot Steele
     */
    fun print(pre : String = "") {
        println("${pre}${name}:")
        println("${pre}\t${datasetType}")
        println("${pre}\t${version}")
    }

    /**
     * Releases the native memory associated with the [UnopenedDataset] object
     *
     * @author Elliot Steele
     */
    external override fun close()
}

