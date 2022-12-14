#include "uk_co_strimm_NumericTimerProperty.h"
#include "util.h"

/*
 * Class:     uk_co_strimm_NumericTimerProperty
 * Method:    getMax
 * Signature: ()Ljava/lang/Object;
 */
JNIEXPORT jobject JNICALL Java_uk_co_strimm_NumericTimerProperty_getMax(JNIEnv *env, jobject thisObj)
{
	auto propPtr = getInstancePointer<STRIMM::CRuntime::Property>(env, thisObj, "uk/co/strimm/TimerProperty");

	jobject valueObject;
	GET_NUMERIC_PROP_VALUE(env, propPtr, valueObject, getMax, nullptr);

	return valueObject;
}

/*
 * Class:     uk_co_strimm_NumericTimerProperty
 * Method:    setMax
 * Signature: (Ljava/lang/Object;)V
 */
JNIEXPORT void JNICALL Java_uk_co_strimm_NumericTimerProperty_setMax(JNIEnv *env, jobject thisObj, jobject maxObj)
{
	auto propPtr = getInstancePointer<STRIMM::CRuntime::Property>(env, thisObj, "uk/co/strimm/TimerProperty");

	SET_NUMERIC_PROP_VALUE(env, propPtr, maxObj, setMax);
	//SET_PROP_VAL(env, propPtr, value, setMax);
}

/*
 * Class:     uk_co_strimm_NumericTimerProperty
 * Method:    getMin
 * Signature: ()Ljava/lang/Object;
 */
JNIEXPORT jobject JNICALL Java_uk_co_strimm_NumericTimerProperty_getMin  (JNIEnv *env, jobject thisObj)
{
	auto propPtr = getInstancePointer<STRIMM::CRuntime::Property>(env, thisObj, "uk/co/strimm/TimerProperty");

	jobject valueObject;
	GET_NUMERIC_PROP_VALUE(env, propPtr, valueObject, getMin, nullptr);

	return valueObject;
}

/*
 * Class:     uk_co_strimm_NumericTimerProperty
 * Method:    setMin
 * Signature: (Ljava/lang/Object;)V
 */
JNIEXPORT void JNICALL Java_uk_co_strimm_NumericTimerProperty_setMin  (JNIEnv *env, jobject thisObj, jobject minObj)
{
	auto propPtr = getInstancePointer<STRIMM::CRuntime::Property>(env, thisObj, "uk/co/strimm/TimerProperty");

	SET_NUMERIC_PROP_VALUE(env, propPtr, minObj, setMin);
}

