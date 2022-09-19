package uk.co.strimm


enum class PropertyType(val i : Int)
{
    IntegerPropertyType(0),
    DoublePropertyType(1),
    FloatPropertyType(2),
    BooleanPropertyType(3),
    StringPropertyType(4),
    UnknownPropertyType(5)
}

sealed class TimerProperty(val instance: Long)
{
    external fun getPropertyType() : PropertyType
    external fun isPreInit() : Boolean
}

sealed class AbstractTimerProperty<T>(instance: Long) : TimerProperty(instance)
{
    var value : T
        external set
        external get
}

sealed class NumericTimerProperty<T>(instance: Long) : AbstractTimerProperty<T>(instance)
{
    var max : T
        external set
        external get

    var min : T
        external set
        external get
}

class IntegerTimerProperty(instance: Long) : NumericTimerProperty<Int>(instance)
class DoubleTimerProperty(instance: Long) : NumericTimerProperty<Double>(instance)
class FloatTimerProperty(instance: Long) : NumericTimerProperty<Float>(instance)
class BooleanTimerProperty(instance: Long) : AbstractTimerProperty<Boolean>(instance)
class StringTimerProperty(instance: Long) : AbstractTimerProperty<String>(instance)
{
    external fun getAllowedValues() : Array<String>
}
