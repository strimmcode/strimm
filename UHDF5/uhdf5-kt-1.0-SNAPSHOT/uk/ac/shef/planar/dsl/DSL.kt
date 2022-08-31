package uk.ac.shef.planar.dsl

import uk.ac.shef.FileCreator
import uk.ac.shef.U5Exception
import uk.ac.shef.planar.Dataset
import uk.ac.shef.planar.Description
import uk.ac.shef.planar.DimensionDescription

/**
 * The Planar DimensionDescription DSL
 *
 * @param[nAxes] The number of axes
 * @param[names] The names of the axes
 *
 * @see DimensionDescription
 *
 * @author Elliot Steele
 */
class PlanarDimensionDescriptionBuilder(val nAxes : Byte, val names : Array<String>) {
    /** The (optional) descriptions of the axes */
    var descriptions : Array<String>? = null
    /** The (optional) axis units */
    var units : Array<String>? = null
    /** The (optional) units per sample */
    var unitsPerSample : DoubleArray? = null
    /** The (optional) names for axes with "NAMED" units (e.g., fluorophore channel) */
    var namedAxisValues : HashMap<String, Array<String>>? = null


    /**
     * DSL Description for building named axes
     *
     * @author Elliot Steele
     */
    class NamedAxisBuilder {
        private val hashMap = HashMap<String, Array<String>>()

        /**
         * Adds a named axis
         *
         * @param[name] The name of the axis (should be the name of an axis with units of "NAMED")
         * @param[values] The the values of the named axis
         *
         * @author Elliot Steele
         */
        fun axis(name : String, vararg values : String) { hashMap[name] = arrayOf(*values) }

        /**
         * Returns the hash map created by the DSL
         *
         * @author Elliot Steele
         */
        fun build() = hashMap
    }

    /**
     * Creates a NamedAixisValueBuilder and applies the provided function
     *
     * @param[init] The NamedAxisBuilder DSL
     *
     * @author Elliot Steele
     */
    fun namedAxisValues(init : NamedAxisBuilder.() -> Unit)
            = NamedAxisBuilder().apply(init).build().also { namedAxisValues = it }


    /**
     * Builds the DimensionDescription object from the DSL
     *
     * @return The created DimensionDescription object
     *
     * @see DimensionDescription
     *
     * @author Elliot Steele
     */
    fun build() = DimensionDescription.create(
        nAxes,
        names,
        descriptions,
        units,
        unitsPerSample,
        namedAxisValues
    )
}

/**
 * The Planar DatasetDescription DSL
 *
 * @param[name] The name of the dataset
 *
 * @author Elliot Steele
 */
class PlanarDatasetDescriptionBuilder(val name : String) {
    /** The (optional) dataset description */
    var description : String? = null
    /** The description of the dataset axes */
    var axisDescription : DimensionDescription? = null
    /** The description of the plane axes */
    var planeDescription : DimensionDescription? = null

    /**
     * Creates a [PlanarDimensionDescriptionBuilder] and applies the provided init function, assigning the produced
     * [DimensionDescription] to the axisDescription property
     *
     * @param[nAxes] The number of axes
     * @param[names] The names of the axes
     * @param[init] The DSL describing the [DimensionDescription]
     *
     * @return The DimensionDescription created
     *
     * @author Elliot Steele
     */
    fun axes(nAxes: Byte, vararg names : String, init : PlanarDimensionDescriptionBuilder.() -> Unit = {}) =
        PlanarDimensionDescriptionBuilder(nAxes, arrayOf(*names)).apply(init).build().apply { axisDescription = this }

    /**
     * Creates a [PlanarDimensionDescriptionBuilder] and applies the provided init function, assigning the produced
     * [DimensionDescription] to the planeDescription property
     *
     * @param[nAxes] The number of axes
     * @param[names] The names of the axes
     * @param[init] The DSL describing the [DimensionDescription]
     *
     * @return The DimensionDescription created
     *
     * @author Elliot Steele
     */
    fun plane(nAxes: Byte, vararg names : String, init : PlanarDimensionDescriptionBuilder.() -> Unit = {}) =
        PlanarDimensionDescriptionBuilder(nAxes, arrayOf(*names)).apply(init).build().apply { planeDescription = this }

    /**
     * Builds the planar [Description] object from the DSL
     *
     * @return The created [Description] object
     *
     * @throws U5Exception if the axisDescription or planeDescription were not specified
     *
     * @author Elliot Steele
     */
    fun build() : Description {
        val axisDescription = axisDescription
            ?: throw U5Exception("Failed to create planar dataset: No axis description!")
        val planeDescription = planeDescription
            ?: throw U5Exception("Failed to create planar dataset: No plane description!")

        return Description.create(
            name,
            description,
            axisDescription,
            planeDescription
        )
    }
}

/**
 * Extension function to allow the specification of planar Datasets from within the UhDF5 File creation DSL
 *
 * @param[name] The name of the dataset
 * @param[init] The Planar Dimension Description DSL
 *
 * @receiver [FileCreator] The file creator DSL for the desired file
 *
 * @return The dataset created from the DSL
 *
 * @throws U5Exception if dataset creation fails
 *
 * @author Elliot Steele
 */
inline fun FileCreator.planar(name: String, init : PlanarDatasetDescriptionBuilder.() -> Unit) : Dataset =
    Dataset.create(
        file,
        PlanarDatasetDescriptionBuilder(name).also(init).build()
    )
