#include "util.h"

#include <iostream>

jobject resultToJResult(JNIEnv* env, STRIMM::Result res)
{
	jclass jresultClass = env->FindClass("uk/co/strimm/TimerResult");

	if (jresultClass == nullptr)
		std::cout << "First Error" << std::endl;

	jfieldID jresField;
	switch (res)
	{
	case STRIMM::Result::Success:
		jresField = env->GetStaticFieldID(jresultClass, "Success", "Luk/co/strimm/TimerResult;");
		break;
	case STRIMM::Result::Error:
		jresField = env->GetStaticFieldID(jresultClass, "Error", "Luk/co/strimm/TimerResult;");
		break;
	case STRIMM::Result::Warning:
		jresField = env->GetStaticFieldID(jresultClass, "Warning", "Luk/co/strimm/TimerResult;");
		break;
	default:
		jresField = nullptr;
		break;
	}

	return env->GetStaticObjectField(jresultClass, jresField);
}

STRIMM::Result valueToResult(int i)
{
	switch (i)
	{
	case -1:
		return STRIMM::Result::Warning;
		break;
	case 1:
		return STRIMM::Result::Success;
		break;
	case 0:
		return STRIMM::Result::Error;
		break;
	}
}

STRIMM::AbstractTimer::ChannelType valueToChannelType(int i)
{
	switch (i)
	{
	case 0:
		return STRIMM::AbstractTimer::ChannelType::AnalogueIn;
		break;
	case 1:
		return STRIMM::AbstractTimer::ChannelType::AnalogueOut;
		break;
	case 2:
		return STRIMM::AbstractTimer::ChannelType::DigitalIn;
		break;
	case 3:
		return STRIMM::AbstractTimer::ChannelType::DigitalOut;
		break;
	}
}

STRIMM::PropertyCollection::PropertyType valueToPropertyType(int i)
{
	switch (i)
	{
	case 0:
		return STRIMM::PropertyCollection::IntPropertyType;
		break;
	case 1:
		return STRIMM::PropertyCollection::DoublePropertyType;
		break;
	case 2:
		return STRIMM::PropertyCollection::FloatPropertyType;
		break;
	case 3:
		return STRIMM::PropertyCollection::BooleanPropertyType;
		break;
	case 4:
		return STRIMM::PropertyCollection::StringPropertyType;
		break;
	default:
		return STRIMM::PropertyCollection::UnknownPropertyType;
		break;
	}
}

jobjectArray strVecToJArray(JNIEnv* env, const std::vector<std::string>& vec)
{
	jclass jStringClass = env->FindClass("java/lang/String");
	jobjectArray out = env->NewObjectArray(vec.size(), jStringClass, nullptr);

	for (int i = 0; i < vec.size(); i++)
	{
		jstring string = env->NewStringUTF(vec[i].c_str());
		env->SetObjectArrayElement(out, i, string);
	}

	return out;
}

std::string getUTFString(JNIEnv * env, jstring j_string)
{
	const char* tmpString = env->GetStringUTFChars(j_string, nullptr);
	std::string str(tmpString);
	env->ReleaseStringUTFChars(j_string, tmpString);
	return str;
}

