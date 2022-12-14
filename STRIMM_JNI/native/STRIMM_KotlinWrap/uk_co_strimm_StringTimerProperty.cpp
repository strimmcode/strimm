#include "uk_co_strimm_StringTimerProperty.h"
#include "util.h"

/*
 * Class:     uk_co_strimm_StringTimerProperty
 * Method:    getAllowedValues
 * Signature: ()[Ljava/lang/String;
 */
JNIEXPORT jobjectArray JNICALL Java_uk_co_strimm_StringTimerProperty_getAllowedValues(JNIEnv *env, jobject thisObj)
{
	auto propPtr = getInstancePointer<STRIMM::CRuntime::StringProperty>(env, thisObj, "uk/co/strimm/TimerProperty");

	return strVecToJArray(env, propPtr->getAllowedValues());
}

