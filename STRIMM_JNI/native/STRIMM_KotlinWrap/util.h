#ifndef _STRIMM_KOTLIN_WRAP_UTIL_H
#define _STRIMM_KOTLIN_WRAP_UTIL_H

#include <jni.h>
#include <STRIMM_CRuntime/STRIMM_CRuntime.h>
#include <vector>
#include <string>
#include <queue>
#include <unordered_map>

#include <iostream>
struct STRIMM_JNI_Timer;

struct jCallbackInfo
{
	JavaVM *jvm;
	jobject callbackObject;
	jclass callbackClass;
	jmethodID callbackMethod;

	jCallbackInfo() : jvm(nullptr), callbackObject(nullptr), callbackMethod(nullptr), callbackClass(nullptr) {}
	jCallbackInfo(const jCallbackInfo&) = delete;
	jCallbackInfo(jCallbackInfo&& that)
	{
		this->jvm = that.jvm;
		this->callbackObject = that.callbackObject;
		this->callbackMethod = that.callbackMethod;
		this->callbackClass = that.callbackClass;

		that.jvm = nullptr;
		that.callbackObject = nullptr;
		that.callbackMethod = nullptr;
		that.callbackClass = nullptr;
	}
	~jCallbackInfo()
	{
		if (jvm && callbackObject && callbackClass)
		{
			JNIEnv* env;
			int res = jvm->GetEnv((void**)&env, JNI_VERSION_1_8);
			if (res == JNI_EDETACHED)
			{
				if (jvm->AttachCurrentThread((void**)env, nullptr) != 0) return;

				env->DeleteGlobalRef(callbackObject);
				env->DeleteGlobalRef(callbackClass);

				jvm->DetachCurrentThread();
			}
			else if (res == JNI_OK)
			{
				env->DeleteGlobalRef(callbackObject);
				env->DeleteGlobalRef(callbackClass);
			}
		}
	}

	jCallbackInfo& operator=(jCallbackInfo&& that)
	{
		this->jvm = that.jvm;
		this->callbackObject = that.callbackObject;
		this->callbackMethod = that.callbackMethod;
		this->callbackClass = that.callbackClass;

		that.jvm = nullptr;
		that.callbackObject = nullptr;
		that.callbackMethod = nullptr;	
		that.callbackClass = nullptr;

		return *this;
	}
};

struct jCopiedDataCallbackInfo
{
	STRIMM_JNI_Timer *timer;
	jCallbackInfo info;

	jCopiedDataCallbackInfo() : timer(nullptr) {}
	jCopiedDataCallbackInfo(const jCopiedDataCallbackInfo&) = delete;
	jCopiedDataCallbackInfo(jCopiedDataCallbackInfo&& that) : timer(that.timer), info(std::move(that.info)) {}
};

struct STRIMM_JNI_Timer
{
	STRIMM_JNI_Timer(STRIMM::CRuntime::TimerDevice* tmr, std::string lbl) : timer(tmr), label(lbl) {}
	STRIMM::CRuntime::TimerDevice* timer;
	std::string label;

	std::queue<std::vector<double>> analogueBufferQueue;
	std::queue<std::vector<uint8_t>> digitalBufferQueue;
	std::list<std::unique_ptr<STRIMM::CRuntime::Property>> propList;
	std::unordered_map<std::string, jCopiedDataCallbackInfo> callbackData;
	jCallbackInfo stopCallbackInfo;
	jCallbackInfo errorCallbackInfo;
};

#define GET_VAL_FROM_PROP_START(prop) \
switch ((prop)->getPropertyType()) \
{ 

#define GET_VAL_FROM_PROP_INT(env, prop, valName, func) \
case STRIMM::PropertyCollection::IntPropertyType: \
	{ \
		int val = (dynamic_cast<STRIMM::CRuntime::IntegerProperty*>((prop)))->func(); \
		jclass jTypeClass = (env)->FindClass("java/lang/Integer"); \
		jmethodID jInit = (env)->GetMethodID(jTypeClass, "<init>", "(I)V"); \
		valName = (env)->NewObject(jTypeClass, jInit, val); \
	} \
	break;

#define GET_VAL_FROM_PROP_DOUBLE(env, prop, valName, func) \
case STRIMM::PropertyCollection::DoublePropertyType: \
		{ \
			double val = (dynamic_cast<STRIMM::CRuntime::DoubleProperty*>(propPtr))->func(); \
			jclass jTypeClass = (env)->FindClass("java/lang/Double"); \
			jmethodID jInit = (env)->GetMethodID(jTypeClass, "<init>", "(D)V"); \
			valName = (env)->NewObject(jTypeClass, jInit, val); \
		} \
		break;

#define GET_VAL_FROM_PROP_FLOAT(env, prop, valName, func) \
case STRIMM::PropertyCollection::FloatPropertyType: \
	{ \
		float val = (dynamic_cast<STRIMM::CRuntime::FloatProperty*>(propPtr))->func(); \
		jclass jTypeClass = (env)->FindClass("java/lang/Float"); \
		jmethodID jInit = (env)->GetMethodID(jTypeClass, "<init>", "(F)V"); \
		valName = (env)->NewObject(jTypeClass, jInit, val); \
	} \
	break;

#define GET_VAL_FROM_PROP_BOOLEAN(env, prop, valName, func) \
case STRIMM::PropertyCollection::BooleanPropertyType: \
	{ \
		bool val = (dynamic_cast<STRIMM::CRuntime::BooleanProperty*>(propPtr))->func(); \
		jclass jTypeClass = (env)->FindClass("java/lang/Boolean"); \
		jmethodID jInit = (env)->GetMethodID(jTypeClass, "<init>", "(Z)V"); \
		valName = (env)->NewObject(jTypeClass, jInit, val); \
	} \
	break;

#define GET_VAL_FROM_PROP_STRING(env, prop, valName, func) \
case STRIMM::PropertyCollection::StringPropertyType: \
	{ \
		std::string val = (dynamic_cast<STRIMM::CRuntime::StringProperty*>(propPtr))->func(); \
		valName = (env)->NewStringUTF(val.c_str()); \
	} \
	break; 

#define GET_VAL_FROM_PROP_DEFAULT(valName, dflt) \
	default: \
		valName = dflt; \
		break; 

#define GET_VAL_FROM_PROP_END() }

#define GET_PROP_VALUE(env, prop, valName, func, dflt) \
GET_VAL_FROM_PROP_START(prop) \
	GET_VAL_FROM_PROP_INT(env, prop, valName, func) \
	GET_VAL_FROM_PROP_DOUBLE(env, prop, valName, func) \
	GET_VAL_FROM_PROP_FLOAT(env, prop, valName, func) \
	GET_VAL_FROM_PROP_BOOLEAN(env, prop, valName, func) \
	GET_VAL_FROM_PROP_STRING(env, prop, valName, func) \
	GET_VAL_FROM_PROP_DEFAULT(valName, dflt) \
GET_VAL_FROM_PROP_END() 

#define GET_NUMERIC_PROP_VALUE(env, prop, valName, func, dflt) \
GET_VAL_FROM_PROP_START(prop) \
	GET_VAL_FROM_PROP_INT(env, prop, valName, func) \
	GET_VAL_FROM_PROP_DOUBLE(env, prop, valName, func) \
	GET_VAL_FROM_PROP_FLOAT(env, prop, valName, func) \
	GET_VAL_FROM_PROP_DEFAULT(valName, dflt) \
GET_VAL_FROM_PROP_END() 


#define SET_PROP_VAL_START(prop) \
switch (propPtr->getPropertyType()) \
{

#define SET_PROP_VAL_INT(env, prop, jvalue, func) \
case STRIMM::PropertyCollection::IntPropertyType: \
	{ \
		jclass jTypeClass = (env)->FindClass("java/lang/Integer"); \
		jmethodID getVal = (env)->GetMethodID(jTypeClass, "intValue", "()I"); \
		int val = (env)->CallIntMethod((jvalue), getVal); \
		(dynamic_cast<STRIMM::CRuntime::IntegerProperty*>(prop))->func(val); \
	} \
	break; 

#define SET_PROP_VAL_DOUBLE(env, prop, jvalue, func) \
case STRIMM::PropertyCollection::DoublePropertyType: \
	{ \
		jclass jTypeClass = (env)->FindClass("java/lang/Double"); \
		jmethodID getVal = (env)->GetMethodID(jTypeClass, "doubleValue", "()D"); \
		double val = (env)->CallDoubleMethod((jvalue), getVal); \
		(dynamic_cast<STRIMM::CRuntime::DoubleProperty*>(prop))->func(val); \
	} \
	break; 

#define SET_PROP_VAL_FLOAT(env, prop, jvalue, func) \
case STRIMM::PropertyCollection::FloatPropertyType: \
	{ \
		jclass jTypeClass = (env)->FindClass("java/lang/Float"); \
		jmethodID getVal = (env)->GetMethodID(jTypeClass, "floatValue", "()F"); \
		float val = (env)->CallFloatMethod((jvalue), getVal);\
		(dynamic_cast<STRIMM::CRuntime::FloatProperty*>(prop))->func(val); \
	} \
	break; 


#define SET_PROP_VAL_BOOLEAN(env, prop, jvalue, func) \
case STRIMM::PropertyCollection::BooleanPropertyType: \
	{ \
		jclass jTypeClass = (env)->FindClass("java/lang/Boolean"); \
		jmethodID getVal = (env)->GetMethodID(jTypeClass, "booleanValue", "()Z"); \
		bool val = (env)->CallBooleanMethod((jvalue), getVal);\
		(dynamic_cast<STRIMM::CRuntime::BooleanProperty*>(prop))->func(val); \
	} \
	break; 

#define SET_PROP_VAL_STRING(env, prop, jvalue, func) \
case STRIMM::PropertyCollection::StringPropertyType: \
	{ \
		const char* tmpStr = (env)->GetStringUTFChars((jstring)(jvalue), nullptr); \
		std::string val(tmpStr); \
		(env)->ReleaseStringUTFChars((jstring)(jvalue), tmpStr); \
		(dynamic_cast<STRIMM::CRuntime::StringProperty*>(prop))->func(val); \
	} \
	break; 

#define SET_PROP_VAL_DEFAULT() \
default: \
	break; \

#define SET_PROP_VAL_END() }


#define SET_PROP_VALUE(env, prop, jvalue, func) \
SET_PROP_VAL_START(prop) \
	SET_PROP_VAL_INT(env, prop, jvalue, func) \
	SET_PROP_VAL_DOUBLE(env, prop, jvalue, func) \
	SET_PROP_VAL_FLOAT(env, prop, jvalue, func) \
	SET_PROP_VAL_BOOLEAN(env, prop, jvalue, func) \
	SET_PROP_VAL_STRING(env, prop, jvalue, func) \
	SET_PROP_VAL_DEFAULT() \
SET_PROP_VAL_END()

#define SET_NUMERIC_PROP_VALUE(env, prop, jvalue, func) \
SET_PROP_VAL_START(prop) \
	SET_PROP_VAL_INT(env, prop, jvalue, func) \
	SET_PROP_VAL_DOUBLE(env, prop, jvalue, func) \
	SET_PROP_VAL_FLOAT(env, prop, jvalue, func) \
	SET_PROP_VAL_DEFAULT() \
SET_PROP_VAL_END()

jobject resultToJResult(JNIEnv* env, STRIMM::Result res);

STRIMM::Result valueToResult(int i);
STRIMM::AbstractTimer::ChannelType valueToChannelType(int i);
STRIMM::PropertyCollection::PropertyType valueToPropertyType(int i);


template<class T>
T* getInstancePointer(JNIEnv* env, jobject jObj, const std::string& jClassName)
{
	jclass jTimerClass = env->FindClass(jClassName.c_str());
	if (!jTimerClass)
		return nullptr;
	
	jfieldID instanceFID = env->GetFieldID(jTimerClass, "instance", "J");
	if (!instanceFID)
		return nullptr;

	jlong res = env->GetLongField(jObj, instanceFID);

	return (T*)res;
}

jobjectArray strVecToJArray(JNIEnv* env, const std::vector<std::string>& vec);
std::string getUTFString(JNIEnv* env, jstring j_string);

#endif 