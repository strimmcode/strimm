package uk.co.strimm.MicroManager

import uk.co.strimm.services.MMCoreService

sealed class MMProperty<T>(val devLabel : String, val name : String) {
    abstract fun getValue() : T
    abstract fun setValue(value : T)

    fun isPreinit() = MMCoreService.core.isPropertyPreInit(devLabel, name)
}

class MMFloatProperty(devLabel : String, name : String) : MMProperty<Float>(devLabel, name) {
    override fun getValue(): Float =
            MMCoreService.core.getProperty(devLabel, name).toFloat()

    override fun setValue(value: Float) =
            MMCoreService.core.setProperty(devLabel, name, value)

    fun getUpperLimit() =
            if (MMCoreService.core.hasPropertyLimits(devLabel, name))
                MMCoreService.core.getPropertyUpperLimit(devLabel, name).toFloat()
            else
                Float.MAX_VALUE

    fun getLowerLimit() =
            if (MMCoreService.core.hasPropertyLimits(devLabel, name))
                MMCoreService.core.getPropertyLowerLimit(devLabel, name).toFloat()
            else
                Float.MIN_VALUE
}

class MMIntProperty(devLabel : String, name : String) : MMProperty<Int>(devLabel, name) {
    override fun getValue(): Int =
            MMCoreService.core.getProperty(devLabel, name).toInt()

    override fun setValue(value: Int)  =
            MMCoreService.core.setProperty(devLabel, name, value)

    fun getUpperLimit() =
            if (MMCoreService.core.hasPropertyLimits(devLabel, name))
                MMCoreService.core.getPropertyUpperLimit(devLabel, name).toInt()
            else
                Int.MAX_VALUE

    fun getLowerLimit() =
            if (MMCoreService.core.hasPropertyLimits(devLabel, name))
                MMCoreService.core.getPropertyLowerLimit(devLabel, name).toInt()
            else
                Int.MIN_VALUE
}

class MMStringProperty(devLabel : String, name : String) : MMProperty<String>(devLabel, name) {
    override fun getValue(): String =
            MMCoreService.core.getProperty(devLabel, name)

    override fun setValue(value: String) =
            MMCoreService.core.setProperty(devLabel, name, value)

    fun getAllowedValues() : List<String>? =
            MMCoreService.core.getAllowedPropertyValues(devLabel, name)
                    .run { if (size() == 0L) null else toList() }
}

class MMUnknownProperty(devLabel: String, name: String) : MMProperty<String>(devLabel, name) {
    override fun getValue(): String =
            MMCoreService.core.getProperty(devLabel, name)

    override fun setValue(value: String) =
            MMCoreService.core.setProperty(devLabel, name, value)
}