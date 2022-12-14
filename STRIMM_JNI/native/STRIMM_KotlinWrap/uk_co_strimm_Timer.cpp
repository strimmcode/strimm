#include "uk_co_strimm_Timer.h"
#include "util.h"

#include <iostream>
#include <string>
#include <queue>
#include <vector>
#include <mutex>

/*
 * Class:     uk_co_strimm_Timer
 * Method:    getName
 * Signature: ()Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_uk_co_strimm_Timer_getName(JNIEnv *env, jobject thisObj)
{
	auto inst = getInstancePointer<STRIMM_JNI_Timer>(env, thisObj, "uk/co/strimm/Timer");
	
	return env->NewStringUTF(inst->timer->getTimerName().c_str());
}

/*
 * Class:     uk_co_strimm_Timer
 * Method:    getAPIVersion
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_uk_co_strimm_Timer_getAPIVersion(JNIEnv *env, jobject thisObj)
{
	auto inst = getInstancePointer<STRIMM_JNI_Timer>(env, thisObj, "uk/co/strimm/Timer");

	return inst->timer->getAPIVersion();
}

/*
 * Class:     uk_co_strimm_Timer
 * Method:    getLastError
 * Signature: ()Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_uk_co_strimm_Timer_getLastError(JNIEnv *env, jobject thisObj)
{
	auto inst = getInstancePointer<STRIMM_JNI_Timer>(env, thisObj, "uk/co/strimm/Timer");

	return env->NewStringUTF(inst->timer->getLastError().c_str());
}

/*
 * Class:     uk_co_strimm_Timer
 * Method:    initialiseEXT
 * Signature: ()Luk/co/strimm/TimerResult;
 */
JNIEXPORT jobject JNICALL Java_uk_co_strimm_Timer_initialiseEXT(JNIEnv *env, jobject thisObj)
{
	auto inst = getInstancePointer<STRIMM_JNI_Timer>(env, thisObj, "uk/co/strimm/Timer");

	return resultToJResult(env, inst->timer->initialise());
}

/*
 * Class:     uk_co_strimm_Timer
 * Method:    getAvailableChannelsEXT
 * Signature: (I)[Ljava/lang/String;
 */
JNIEXPORT jobjectArray JNICALL Java_uk_co_strimm_Timer_getAvailableChannelsEXT(JNIEnv *env, jobject thisObj, jint channelType)
{
	auto inst = getInstancePointer<STRIMM_JNI_Timer>(env, thisObj, "uk/co/strimm/Timer");

	auto cChannelType = valueToChannelType(channelType);

	return strVecToJArray(env, inst->timer->getAvailableChannelNames(cChannelType));
}

/*
 * Class:     uk_co_strimm_Timer
 * Method:    getChannelsEXT
 * Signature: (I)[Ljava/lang/String;
 */
JNIEXPORT jobjectArray JNICALL Java_uk_co_strimm_Timer_getChannelsEXT(JNIEnv *env, jobject thisObj, jint channelType)
{
	auto inst = getInstancePointer<STRIMM_JNI_Timer>(env, thisObj, "uk/co/strimm/Timer");

	auto cChannelType = valueToChannelType(channelType);

	auto tmp = inst->timer->getChannelNames(cChannelType);
	return strVecToJArray(env, tmp);
}

double* analogueBufferCallback(int blockSize, void* data)
{
	auto timer = (STRIMM_JNI_Timer*)data;

	auto buffer = std::vector<double>(blockSize);
	timer->analogueBufferQueue.push(std::move(buffer));
	return timer->analogueBufferQueue.back().data();
}

template<class bufferType, class jDataType>
void genericDataCopiedCallback(jCopiedDataCallbackInfo *callbackData, std::queue<std::vector<bufferType>>& queue)
{
	using arrayType = typename std::conditional<std::is_same<jDataType, jdouble>::value, jdoubleArray, jbyteArray>::type;

	JNIEnv *env;
	int res = callbackData->info.jvm->GetEnv((void**)&env, JNI_VERSION_1_8);
	if (res == JNI_EDETACHED)
		if (callbackData->info.jvm->AttachCurrentThread((void**)&env, nullptr) != 0) return;
	
	auto buffer = std::move(queue.front());
	queue.pop();

	arrayType newData = nullptr;
	if constexpr (std::is_same<jDataType, jdouble>::value)
		newData = env->NewDoubleArray(buffer.size());
	else if constexpr (std::is_same<jDataType, jbyte>::value)
		newData = env->NewByteArray(buffer.size());

	if (!newData) return;

	if constexpr (std::is_same<jDataType, jdouble>::value)
		env->SetDoubleArrayRegion(newData, 0, buffer.size(), (jdouble*)buffer.data());
	else if constexpr (std::is_same<jDataType, jbyte>::value)
		env->SetByteArrayRegion(newData, 0, buffer.size(), (jbyte*)buffer.data());

	env->CallVoidMethod(callbackData->info.callbackObject, callbackData->info.callbackMethod, newData);

	callbackData->info.jvm->DetachCurrentThread();
}


void analogueDataCopiedCallback(void* data)
{
	auto callbackData = (jCopiedDataCallbackInfo*)data;
	genericDataCopiedCallback<double, jdouble>(callbackData, callbackData->timer->analogueBufferQueue);
}

uint8_t* digitalBufferCallback(int blockSize, void* data)
{
	auto timer = (STRIMM_JNI_Timer*)data;

	auto buffer = std::vector<uint8_t>(blockSize);
	timer->digitalBufferQueue.push(std::move(buffer));
	return timer->digitalBufferQueue.back().data();
}

void digitalDataCopiedCallback(void* data)
{
	auto callbackData = (jCopiedDataCallbackInfo*)data;
	genericDataCopiedCallback<uint8_t, jbyte>(callbackData, callbackData->timer->digitalBufferQueue);
}

/*
 * Class:     uk_co_strimm_Timer
 * Method:    addAnalogueInputEXT
 * Signature: (Ljava/lang/String;IDDLuk/co/strimm/STRIMM_JArrayCallback;)Luk/co/strimm/TimerResult;
 */
JNIEXPORT jobject JNICALL Java_uk_co_strimm_Timer_addAnalogueInputEXT(JNIEnv *env, jobject thisObj, jstring channelName, jint clockDiv, jdouble vMax, jdouble vMin, jobject callback)
{
	auto inst = getInstancePointer<STRIMM_JNI_Timer>(env, thisObj, "uk/co/strimm/Timer");
	
	auto cChannelName = getUTFString(env, channelName);

	jclass callbackClass = env->FindClass("uk/co/strimm/STRIMM_JArrayCallback");
	
	jCopiedDataCallbackInfo callbackInfo{};
	callbackInfo.info.callbackObject = env->NewGlobalRef(callback);
	callbackInfo.info.callbackClass = (jclass)env->NewGlobalRef(callbackClass);
	callbackInfo.info.callbackMethod = env->GetMethodID(callbackClass, "run", "(Ljava/lang/Object;)V");
	jint res = env->GetJavaVM(&callbackInfo.info.jvm);
	callbackInfo.timer = inst;

	if (res != JNI_OK)
		return resultToJResult(env, STRIMM::Result::Error);
	if (!callbackInfo.info.callbackMethod)
		return resultToJResult(env, STRIMM::Result::Error);

	inst->callbackData.insert(std::make_pair(cChannelName, std::move(callbackInfo)));

	STRIMM_AIDesc aiDesc{};
	aiDesc.voltageMax = vMax;
	aiDesc.voltageMin = vMin;
	aiDesc.clockDiv = clockDiv;
	aiDesc.bufferCallback = &analogueBufferCallback;
	aiDesc.bufferCallbackInfo = inst;
	aiDesc.dataCopiedCallback = &analogueDataCopiedCallback;
	aiDesc.dataCopiedCallbackInfo = &inst->callbackData[cChannelName];

	return resultToJResult(env, inst->timer->addAnalogueInput(cChannelName, aiDesc));
}
/*
 * Class:     uk_co_strimm_Timer
 * Method:    addAnalogueOutputEXT
 * Signature: (Ljava/lang/String;I[D)Luk/co/strimm/TimerResult;
 */
JNIEXPORT jobject JNICALL Java_uk_co_strimm_Timer_addAnalogueOutputEXT(JNIEnv *env, jobject thisObj, jstring channelName, jint clockDiv, jdoubleArray samples)
{
	auto inst = getInstancePointer<STRIMM_JNI_Timer>(env, thisObj, "uk/co/strimm/Timer");

	auto cChannelName = getUTFString(env, channelName);

	jdouble *cDoubleArray = env->GetDoubleArrayElements(samples, nullptr);
	if (!cDoubleArray)
		return resultToJResult(env, STRIMM::Result::Error);
	jsize length = env->GetArrayLength(samples);

	STRIMM_AODesc aoDesc{}; 
	aoDesc.blockSize = length;
	aoDesc.clockDiv = clockDiv;
	aoDesc.sampleArray = cDoubleArray;

	if (auto res = inst->timer->addAnalogueOutput(cChannelName, aoDesc); res != STRIMM::Result::Success)
		return resultToJResult(env, res);

	env->ReleaseDoubleArrayElements(samples, cDoubleArray, 0);

	return resultToJResult(env, STRIMM::Result::Success);
}


/*
 * Class:     uk_co_strimm_Timer
 * Method:    addDigitalInputEXT
 * Signature: (Ljava/lang/String;ILuk/co/strimm/STRIMM_JArrayCallback;)Luk/co/strimm/TimerResult;
 */
JNIEXPORT jobject JNICALL Java_uk_co_strimm_Timer_addDigitalInputEXT(JNIEnv *env, jobject thisObj, jstring channelName, jint clockDiv, jobject callback)
{
	auto inst = getInstancePointer<STRIMM_JNI_Timer>(env, thisObj, "uk/co/strimm/Timer");

	auto cChannelName = getUTFString(env, channelName);

	jclass callbackClass = env->FindClass("uk/co/strimm/STRIMM_JArrayCallback");

	jCopiedDataCallbackInfo callbackInfo{};
	callbackInfo.info.callbackObject = env->NewGlobalRef(callback);
	callbackInfo.info.callbackClass = (jclass)env->NewGlobalRef(callbackClass);
	callbackInfo.info.callbackMethod = env->GetMethodID(callbackClass, "run", "(Ljava/lang/Object;)V");
	jint res = env->GetJavaVM(&callbackInfo.info.jvm);
	callbackInfo.timer = inst;

	if (res != JNI_OK)
		return resultToJResult(env, STRIMM::Result::Error);
	if (!callbackInfo.info.callbackMethod)
		return resultToJResult(env, STRIMM::Result::Error);

	inst->callbackData.insert(std::make_pair(cChannelName, std::move(callbackInfo)));

	STRIMM_DIDesc diDesc{};
	diDesc.clockDiv = clockDiv;
	diDesc.bufferCallback = &digitalBufferCallback;
	diDesc.bufferCallbackInfo = inst;
	diDesc.dataCopiedCallback = &digitalDataCopiedCallback;
	diDesc.dataCopiedCallbackInfo = &inst->callbackData[cChannelName];

	return resultToJResult(env, inst->timer->addDigitalInput(cChannelName, diDesc));
}
/*
 * Class:     uk_co_strimm_Timer
 * Method:    addDigitalOutputEXT
 * Signature: (Ljava/lang/String;I[B)Luk/co/strimm/TimerResult;
 */
JNIEXPORT jobject JNICALL Java_uk_co_strimm_Timer_addDigitalOutputEXT(JNIEnv *env, jobject thisObj, jstring channelName, jint clockDiv, jbyteArray samples)
{
	auto inst = getInstancePointer<STRIMM_JNI_Timer>(env, thisObj, "uk/co/strimm/Timer");

	auto cChannelName = getUTFString(env, channelName);

	jbyte *cByteArray = env->GetByteArrayElements(samples, nullptr);
	if (!cByteArray)
		return resultToJResult(env, STRIMM::Result::Error);
	jsize length = env->GetArrayLength(samples);

	STRIMM_DODesc doDesc{}; 
	doDesc.blockSize = length;
	doDesc.clockDiv = clockDiv;
	doDesc.sampleArray = (uint8_t*)cByteArray;

	if (auto res = inst->timer->addDigitalOutput(cChannelName, doDesc); res != STRIMM::Result::Success)
		return resultToJResult(env, res);

	env->ReleaseByteArrayElements(samples, cByteArray, 0);

	return resultToJResult(env, STRIMM::Result::Success);
}

void StopCallback(void* data)
{
	auto info = (jCallbackInfo*)data;

	JNIEnv* env;
	int res = info->jvm->GetEnv((void**)&env, JNI_VERSION_1_8);
	if (res == JNI_EDETACHED)
		if (info->jvm->AttachCurrentThread((void**)env, nullptr) != 0) return;

	env->CallVoidMethod(info->callbackObject, info->callbackMethod);

	info->jvm->DetachCurrentThread();
}

/*
 * Class:     uk_co_strimm_Timer
 * Method:    registerStopCallback
 * Signature: (Luk/co/strimm/STRIMM_JCallback;)V
 */
JNIEXPORT void JNICALL Java_uk_co_strimm_Timer_registerStopCallback(JNIEnv *env, jobject thisObj, jobject callbackObj)
{
	auto inst = getInstancePointer<STRIMM_JNI_Timer>(env, thisObj, "uk/co/strimm/Timer");

	jclass callbackClass = env->FindClass("uk/co/strimm/STRIMM_JVoidCallback");

	auto info = jCallbackInfo{};

	info.callbackObject = env->NewGlobalRef(callbackObj);
	info.callbackMethod = env->GetMethodID(callbackClass, "run", "()V");
	info.callbackClass = (jclass)env->NewGlobalRef(callbackClass);
	jint res = env->GetJavaVM(&info.jvm);

	inst->stopCallbackInfo = std::move(info);

	if (res != JNI_OK)
		return;
	if (!info.callbackMethod)
		return;

	inst->timer->registerStopCallback(&StopCallback, &inst->stopCallbackInfo);

}

/*
 * Class:     uk_co_strimm_Timer
 * Method:    registerErrorCallback
 * Signature: (Luk/co/strimm/STRIMM_JCallback;)V
 */
JNIEXPORT void JNICALL Java_uk_co_strimm_Timer_registerErrorCallback(JNIEnv *env, jobject thisObj, jobject callbackObj)
{
	auto inst = getInstancePointer<STRIMM_JNI_Timer>(env, thisObj, "uk/co/strimm/Timer");

	jclass callbackClass = env->FindClass("uk/co/strimm/STRIMM_JVoidCallback");

	auto info = jCallbackInfo{};

	info.callbackObject = env->NewGlobalRef(callbackObj);
	info.callbackMethod = env->GetMethodID(callbackClass, "run", "()V");
	info.callbackClass = (jclass)env->NewGlobalRef(callbackClass);
	jint res = env->GetJavaVM(&info.jvm);

	inst->errorCallbackInfo = std::move(info);

	if (res != JNI_OK)
		return;
	if (!info.callbackMethod)
		return;

	inst->timer->registerStopCallback(&StopCallback, &inst->errorCallbackInfo);
}

/*
 * Class:     uk_co_strimm_Timer
 * Method:    prime
 * Signature: ()Luk/co/strimm/Result;
 */
JNIEXPORT jobject JNICALL Java_uk_co_strimm_Timer_prime(JNIEnv *env, jobject thisObj)
{
	auto inst = getInstancePointer<STRIMM_JNI_Timer>(env, thisObj, "uk/co/strimm/Timer");

	return resultToJResult(env, inst->timer->prime());
}

/*
 * Class:     uk_co_strimm_Timer
 * Method:    start
 * Signature: ()Luk/co/strimm/Result;
 */
JNIEXPORT jobject JNICALL Java_uk_co_strimm_Timer_start(JNIEnv *env, jobject thisObj)
{
	auto inst = getInstancePointer<STRIMM_JNI_Timer>(env, thisObj, "uk/co/strimm/Timer");

	return resultToJResult(env, inst->timer->start());
}

/*
 * Class:     uk_co_strimm_Timer
 * Method:    stop
 * Signature: ()Luk/co/strimm/Result;
 */
JNIEXPORT jobject JNICALL Java_uk_co_strimm_Timer_stop(JNIEnv *env, jobject thisObj)
{
	auto inst = getInstancePointer<STRIMM_JNI_Timer>(env, thisObj, "uk/co/strimm/Timer");

	return resultToJResult(env, inst->timer->stop());
}

/*
 * Class:     uk_co_strimm_Timer
 * Method:    getPropertyNames
 * Signature: ()[Ljava/lang/String;
 */
JNIEXPORT jobjectArray JNICALL Java_uk_co_strimm_Timer_getPropertyNames(JNIEnv *env, jobject thisObj)
{
	auto inst = getInstancePointer<STRIMM_JNI_Timer>(env, thisObj, "uk/co/strimm/Timer");
	
	return strVecToJArray(env, inst->timer->getPropertyNames());
}


/*
 * Class:     uk_co_strimm_Timer
 * Method:    getProperty
 * Signature: (Ljava/lang/String;)Luk/co/strimm/TimerProperty;
 */
JNIEXPORT jobject JNICALL Java_uk_co_strimm_Timer_getProperty(JNIEnv *env, jobject thisObj, jstring propertyName)
{
	using STRIMM::PropertyCollection;

	auto inst = getInstancePointer<STRIMM_JNI_Timer>(env, thisObj, "uk/co/strimm/Timer");

	auto cPropertyName = getUTFString(env, propertyName);

	auto [prop, type] = inst->timer->getProperty<STRIMM::PropertyCollection::UnknownPropertyType>(cPropertyName);
	if (!prop)
		return nullptr;

	jclass jPropertyClass;
	switch (type)
	{
	case PropertyCollection::IntPropertyType:
		jPropertyClass = env->FindClass("uk/co/strimm/IntegerTimerProperty");
		break;
	case PropertyCollection::FloatPropertyType:
		jPropertyClass = env->FindClass("uk/co/strimm/FloatTimerProperty");
		break;
	case PropertyCollection::DoublePropertyType:
		jPropertyClass = env->FindClass("uk/co/strimm/DoubleTimerProperty");
		break;
	case PropertyCollection::BooleanPropertyType:
		jPropertyClass = env->FindClass("uk/co/strimm/BooleanTimerProperty");
		break;
	case PropertyCollection::StringPropertyType:
		jPropertyClass = env->FindClass("uk/co/strimm/StringTimerProperty");
		break;
	default:
		jPropertyClass = env->FindClass("uk/co/strimm/TimerProperty");
		break;
	}

	jmethodID jPropertyInit = env->GetMethodID(jPropertyClass, "<init>", "(J)V");
	if (!jPropertyInit)
		return nullptr;

	inst->propList.push_front(std::move(prop));

	return env->NewObject(jPropertyClass, jPropertyInit, inst->propList.front().get());
}


/*
 * Class:     uk_co_strimm_Timer
 * Method:    releaseProperty
 * Signature: (Luk/co/strimm/TimerProperty;)V
 */
JNIEXPORT void JNICALL Java_uk_co_strimm_Timer_releaseProperty(JNIEnv *env, jobject thisObj, jobject prop)
{
	auto inst = getInstancePointer<STRIMM_JNI_Timer>(env, thisObj, "uk/co/strimm/Timer");
	auto propPtr = getInstancePointer<STRIMM::CRuntime::Property>(env, prop, "uk/co/strimm/TimerProperty");

	inst->propList.remove_if([propPtr](std::unique_ptr<STRIMM::CRuntime::Property>& uptr) { return uptr.get() == propPtr; });
}

