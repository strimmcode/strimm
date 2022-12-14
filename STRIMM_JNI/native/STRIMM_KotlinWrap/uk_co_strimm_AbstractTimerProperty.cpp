#include "uk_co_strimm_AbstractTimerProperty.h"
#include "util.h"
#include <iostream>


/*
 * Class:     uk_co_strimm_AbstractTimerProperty
 * Method:    getValue
 * Signature: ()Ljava/lang/Object;
 */
JNIEXPORT jobject JNICALL Java_uk_co_strimm_AbstractTimerProperty_getValue(JNIEnv *env, jobject thisObj)
{
	auto propPtr = getInstancePointer<STRIMM::CRuntime::Property>(env, thisObj, "uk/co/strimm/TimerProperty");

	jobject valueObject;
	GET_PROP_VALUE(env, propPtr, valueObject, getValue, nullptr)

	return valueObject;
}

/*
 * Class:     uk_co_strimm_AbstractTimerProperty
 * Method:    setValue
 * Signature: (Ljava/lang/Object;)V
 */
JNIEXPORT void JNICALL Java_uk_co_strimm_AbstractTimerProperty_setValue(JNIEnv *env, jobject thisObj, jobject value)
{
	auto propPtr = getInstancePointer<STRIMM::CRuntime::Property>(env, thisObj, "uk/co/strimm/TimerProperty");

	SET_PROP_VALUE(env, propPtr, value, setValue);
}

