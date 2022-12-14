#include "uk_co_strimm_STRIMM_JNI.h"
#include "util.h"

#include <STRIMM_CRuntime/STRIMM_CRuntime.h>

#include <iostream>
#include <string>
#include <mutex>
#include <vector>
#include <queue>
#include <unordered_map>
#include <set>
#include <sstream>

bool initialised = false;

std::mutex g_runtime;
STRIMM::CRuntime *runtime = nullptr;

std::string errorMsg = "";

std::unordered_map<std::string, int> nInstances;
std::unordered_map<std::string, std::unique_ptr<STRIMM_JNI_Timer>> timers;

#define WITH_DEBUG_OUTPUT

#ifdef WITH_DEBUG_OUTPUT
#define DEBUG_OUTPUT(v) std::cout << v << std::endl
#else
#define DEBUG_OUTPUT(v);
#endif

/*
 * Class:     uk_co_strimm_STRIMM_JNI
 * Method:    initialise
 * Signature: ()Luk/co/strimm/Result;
 */
JNIEXPORT jobject JNICALL Java_uk_co_strimm_STRIMM_1JNI_initialise(JNIEnv *env, jobject thisObj)
{
	DEBUG_OUTPUT("[JNI CALL]: INITIALISE");
	if (initialised)
	{
		errorMsg = "STRIMM C++ runtime already initialised!";
		return resultToJResult(env, STRIMM::Result::Error);
	}

	{
		std::lock_guard<std::mutex> lock(g_runtime);


		initialised = runtime = new STRIMM::CRuntime();

		if (!initialised)
			errorMsg = "Failed to create new STRIMM C++ runtime object!";
	}


	return resultToJResult(env, initialised ? STRIMM::Result::Success : STRIMM::Result::Error);
}

/*
 * Class:     uk_co_strimm_STRIMM_JNI
 * Method:    deinitialise
 * Signature: ()Luk/co/strimm/Result;
 */
JNIEXPORT jobject JNICALL Java_uk_co_strimm_STRIMM_1JNI_deinitialise(JNIEnv *env, jobject thisObj)
{
	DEBUG_OUTPUT("[JNI CALL]: DEINITIALISE");
	if (!initialised)
	{
		errorMsg = "STRIMM C++ runtime not initialised!"; \
		return resultToJResult((env), STRIMM::Result::Error); \
	}

	{
		std::lock_guard<std::mutex> lock(g_runtime);

		if (runtime)
		{
			delete runtime;
			runtime = nullptr;
		}

		timers.erase(timers.begin(), timers.end());

		initialised = false;
	}

	return resultToJResult(env, STRIMM::Result::Success);
}


/*
 * Class:     uk_co_strimm_STRIMM_JNI
 * Method:    getInstalledDevicesEXT
 * Signature: ()[Ljava/lang/String;
 */
JNIEXPORT jobjectArray JNICALL Java_uk_co_strimm_STRIMM_1JNI_getInstalledDevicesEXT(JNIEnv *env, jobject thisObj)
{
	DEBUG_OUTPUT("[JNI CALL]: GetInstalledDevicesEXT");
	if (!initialised)
	{
		errorMsg = "STRIMM C++ runtime not initialised";
		return nullptr;
	}

	std::vector<std::string> installedDevices;
	{
		std::lock_guard<std::mutex> lock(g_runtime);
		installedDevices = runtime->GetInstalledDevices();
	}

	jclass jstringClass = env->FindClass("java/lang/String");
	jobjectArray out = env->NewObjectArray(installedDevices.size(), jstringClass, nullptr);

	for (int i = 0; i < installedDevices.size(); i++)
	{
		jstring jstr = env->NewStringUTF(installedDevices[i].c_str());
		env->SetObjectArrayElement(out, i, jstr);
	}

	return out;
}

/*
 * Class:     uk_co_strimm_STRIMM_JNI
 * Method:    addInstallDirectory
 * Signature: (Ljava/lang/String;)V
 */
JNIEXPORT void JNICALL Java_uk_co_strimm_STRIMM_1JNI_addInstallDirectory(JNIEnv *env, jobject thisObj, jstring directory)
{
	DEBUG_OUTPUT("[JNI CALL]: addInstallDirectory");
	if (!initialised) return;

	{
		std::lock_guard<std::mutex> lock(g_runtime);

		auto tmp = getUTFString(env, directory);
		std::cout << tmp << std::endl;
		runtime->addInstallDirectory(tmp);
	}
}


///*
// * Class:     uk_co_strimm_STRIMM_JNI
// * Method:    loadTimer
// * Signature: (Ljava/lang/String;Ljava/lang/String;)Luk/co/strimm/Result;
// */
//JNIEXPORT jobject JNICALL Java_uk_co_strimm_STRIMM_1JNI_loadTimer(JNIEnv *env, jobject thisObj, jstring deviceName, jstring deviceLabel)
//{
//	if (!initialised)
//	{
//		errorMsg = "STRIMM C++ runtim not initialised";
//		return resultToJResult(env, STRIMM::Result::Error);
//	}
//
//	auto cDeviceName = getUTFString(env, deviceName);
//	auto cDeviceLabel = getUTFString(env, deviceLabel);
//
//	STRIMM::Result res;
//
//	{
//		std::lock_guard<std::mutex> lock(g_runtime);
//		res = runtime->loadTimer(cDeviceName, cDeviceLabel);
//	}
//
//	return resultToJResult(env, res);
//}
//
///*
// * Class:     uk_co_strimm_STRIMM_JNI
// * Method:    getDevicePointer
// * Signature: (Ljava/lang/String;)Luk/co/strimm/Timer;
// */
//JNIEXPORT jobject JNICALL Java_uk_co_strimm_STRIMM_1JNI_getDevicePointer(JNIEnv *env, jobject thisObj, jstring deviceLabel)
//{
//	if (!initialised)
//	{
//		errorMsg = "STRIMM C++ runtime not initialised!";
//		return nullptr;
//	}
//
//	jclass jTimerClass = env->FindClass("uk/co/strimm/Timer");
//	if (!jTimerClass)
//	{
//		errorMsg = "Could not find Timer class!";
//		return nullptr;
//	}
//
//	jmethodID jTimerInit = env->GetMethodID(jTimerClass, "<init>", "(J)V");
//	if (!jTimerInit)
//	{
//		errorMsg = "Could not find Timer class init method!";
//		return nullptr;
//	}
//
//	auto cDeviceLabel = getUTFString(env, deviceLabel);
//
//
//	STRIMM::CRuntime::TimerDevice* timer_ptr = nullptr;
//
//	{
//		std::lock_guard<std::mutex> lock(g_runtime);
//		timer_ptr = runtime->getDevicePointer(cDeviceLabel);
//	}
//
//	if (!timer_ptr)
//	{
//		errorMsg = "Failed to get pointer to timer device!";
//		return nullptr;
//	}
//
//	return env->NewObject(jTimerClass, jTimerInit, timer_ptr);
//}

/*
 * Class:     uk_co_strimm_STRIMM_JNI
 * Method:    createTimer
 * Signature: (Ljava/lang/String;)Luk/co/strimm/Timer;
 */
JNIEXPORT jobject JNICALL Java_uk_co_strimm_STRIMM_1JNI_createTimer(JNIEnv *env, jobject thisObj, jstring deviceName)
{
	DEBUG_OUTPUT("[JNI CALL]: createTimer");
	if (!initialised)
	{
		errorMsg = "STRIMM C++ Runtime not initialised!";
		return nullptr;
	}

	auto cDeviceName = getUTFString(env, deviceName);
	std::stringstream ss;
	ss << cDeviceName << nInstances[cDeviceName]++;

	auto cDeviceLabel = ss.str();

	STRIMM::Result res;

	{
		std::lock_guard<std::mutex> lock(g_runtime);
		res = runtime->loadTimer(cDeviceName, cDeviceLabel);
	}

	if (res == STRIMM::Result::Error)
	{
		std::cout << "Failed to create STRIMM Timer device : " << deviceName;
		return nullptr;
	}

	jclass jTimerClass = env->FindClass("uk/co/strimm/Timer");
	if (!jTimerClass)
	{
		errorMsg = "Could not find Timer class!";
		return nullptr;
	}

	jmethodID jTimerInit = env->GetMethodID(jTimerClass, "<init>", "(J)V");
	if (!jTimerInit)
	{
		errorMsg = "Could not find Timer class init method!";
		return nullptr;
	}

	STRIMM::CRuntime::TimerDevice* timer_ptr = nullptr;

	{
		std::lock_guard<std::mutex> lock(g_runtime);
		timer_ptr = runtime->getDevicePointer(cDeviceLabel);
	}

	if (!timer_ptr)
	{
		errorMsg = "Failed to get pointer to timer device!";
		return nullptr;
	}

	{
		std::lock_guard<std::mutex> lock(g_runtime);
		timers.insert(std::make_pair(cDeviceLabel, std::make_unique<STRIMM_JNI_Timer>(timer_ptr, cDeviceLabel)));
	}

	return env->NewObject(jTimerClass, jTimerInit, timers[cDeviceLabel].get());
}

/*
 * Class:     uk_co_strimm_STRIMM_JNI
 * Method:    deleteTimer
 * Signature: (Luk/co/strimm/Timer;)V
 */
JNIEXPORT void JNICALL Java_uk_co_strimm_STRIMM_1JNI_deleteTimer(JNIEnv *env, jobject thisObj, jobject timer)
{
	DEBUG_OUTPUT("[JNI CALL]: deleteTimer");
	auto instance = getInstancePointer<STRIMM_JNI_Timer>(env, timer, "uk/co/strimm/Timer");

	timers.erase(instance->label);
}


/*
 * Class:     uk_co_strimm_STRIMM_JNI
 * Method:    getLastError
 * Signature: ()Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL Java_uk_co_strimm_STRIMM_1JNI_getLastError(JNIEnv *env, jobject thisObj)
{
	DEBUG_OUTPUT("[JNI CALL]: getLastError");
	return env->NewStringUTF(errorMsg.c_str());
}