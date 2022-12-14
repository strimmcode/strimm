#include "uk_co_strimm_TimerProperty.h"
#include "util.h"

/*
 * Class:     uk_co_strimm_TimerProperty
 * Method:    getPropertyType
 * Signature: ()Luk/co/strimm/PropertyType;
 */
JNIEXPORT jobject JNICALL Java_uk_co_strimm_TimerProperty_getPropertyType(JNIEnv *env, jobject thisObj)
{
	auto propPtr = getInstancePointer<STRIMM::CRuntime::Property>(env, thisObj, "uk/co/strimm/TimerProperty");

	auto cPropType = propPtr->getPropertyType();

	jclass jPropertyTypeClass = env->FindClass("uk/co/strimm/PropertyType");

	jfieldID jPropTypeField;
	switch (cPropType)
	{
	case STRIMM::PropertyCollection::IntPropertyType:
		jPropTypeField = env->GetStaticFieldID(jPropertyTypeClass, "IntegerPropertyType", "Luk/co/strimm/PropertyType;");
		break;
	case STRIMM::PropertyCollection::DoublePropertyType:
		jPropTypeField = env->GetStaticFieldID(jPropertyTypeClass, "DoublePropertyType", "Luk/co/strimm/PropertyType;");
		break;
	case STRIMM::PropertyCollection::FloatPropertyType:
		jPropTypeField = env->GetStaticFieldID(jPropertyTypeClass, "FloatPropertyType", "Luk/co/strimm/PropertyType;");
		break;
	case STRIMM::PropertyCollection::BooleanPropertyType:
		jPropTypeField = env->GetStaticFieldID(jPropertyTypeClass, "BooleanPropertyType", "Luk/co/strimm/PropertyType;");
		break;
	case STRIMM::PropertyCollection::StringPropertyType:
		jPropTypeField = env->GetStaticFieldID(jPropertyTypeClass, "StringPropertyType", "Luk/co/strimm/PropertyType;");
		break;
	default:
		jPropTypeField = env->GetStaticFieldID(jPropertyTypeClass, "UnknownPropertyType", "Luk/co/strimm/PropertyType;");
		break;
	}

	return env->GetStaticObjectField(jPropertyTypeClass, jPropTypeField);
}

/*
 * Class:     uk_co_strimm_TimerProperty
 * Method:    isPreInit
 * Signature: ()Z
 */
JNIEXPORT jboolean JNICALL Java_uk_co_strimm_TimerProperty_isPreInit(JNIEnv *env, jobject thisObj)
{
	auto propPtr = getInstancePointer<STRIMM::CRuntime::Property>(env, thisObj, "uk/co/strimm/TimerProperty");

	auto propType = propPtr->getPropertyType();

	switch (propType)
	{
	case STRIMM::PropertyCollection::IntPropertyType:
		return (dynamic_cast<STRIMM::CRuntime::IntegerProperty*>(propPtr))->isPreInit();
		break;
	case STRIMM::PropertyCollection::DoublePropertyType:
		return (dynamic_cast<STRIMM::CRuntime::DoubleProperty*>(propPtr))->isPreInit();
		break;
	case STRIMM::PropertyCollection::FloatPropertyType:
		return (dynamic_cast<STRIMM::CRuntime::FloatProperty*>(propPtr))->isPreInit();
		break;
	case STRIMM::PropertyCollection::BooleanPropertyType:
		return (dynamic_cast<STRIMM::CRuntime::BooleanProperty*>(propPtr))->isPreInit();
		break;
	case STRIMM::PropertyCollection::StringPropertyType:
		return (dynamic_cast<STRIMM::CRuntime::StringProperty*>(propPtr))->isPreInit();
		break;
	default:
		return false;
		break;
	}
}

