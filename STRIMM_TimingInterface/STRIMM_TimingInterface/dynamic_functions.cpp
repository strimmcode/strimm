#define _STRIMM_C_RUNTIME_BUILD
#include "dynamic_functions.h"

#define TIMER_GET(name, type) \
STRIMM_API type Get ## name (STRIMM::AbstractTimer * timer) { \
	return timer->get ## name(); \
}

#define PROPERTY_GET(name, typeLabel, type, propType, extra) \
STRIMM_API type Get ## name ## typeLabel (STRIMM::PropertyCollection:: ## propType ## Property* prop) {\
	return prop->get ## name() ## extra; \
}

#define PROPERTY_SET(name, typeLabel, type, propType) \
STRIMM_API void Set ## name ## typeLabel (STRIMM::PropertyCollection:: ## propType ## Property* prop, type value) {\
	prop->set ## name(value);\
}

extern "C" {
	STRIMM_API int GetAPIVersion() { return STRIMM_C_API_VERSION; }

	STRIMM_API int GetNAvailableChannels(STRIMM::AbstractTimer * timer, STRIMM::AbstractTimer::ChannelType type) { return timer->getNAvailableChannels(type); }
	STRIMM_API const char* GetAvailableChannelName(STRIMM::AbstractTimer* timer, STRIMM::AbstractTimer::ChannelType type, int i) { return timer->getAvailableChannelName(type, i); }
	STRIMM_API int GetNChannels(STRIMM::AbstractTimer * timer, STRIMM::AbstractTimer::ChannelType type) { return timer->getNChannels(type); }
	STRIMM_API const char * GetChannelName(STRIMM::AbstractTimer * timer, STRIMM::AbstractTimer::ChannelType type, int i) { return timer->getChannelName(type, i); }
	STRIMM_API int GetNChannelsInUse(STRIMM::AbstractTimer* timer, STRIMM::AbstractTimer::ChannelType type) { return timer->getNChannelsInUse(type); }
	STRIMM_API const char * GetChannelInUseName(STRIMM::AbstractTimer* timer, STRIMM::AbstractTimer::ChannelType type, int i) { return timer->getChannelInUseName(type, i); }

	STRIMM_API STRIMM::Result Initialise(STRIMM::AbstractTimer* timer) { return timer->initialise(); }

	STRIMM_API STRIMM::Result AddAnalogueInput(STRIMM::AbstractTimer * timer, const char* channelName, STRIMM_AIDesc desc) { return timer->addAnalogueInput(channelName, desc); }
	STRIMM_API STRIMM::Result AddDigitalInput(STRIMM::AbstractTimer * timer, const char* channelName, STRIMM_DIDesc desc) { return timer->addDigitalInput(channelName, desc); }
	STRIMM_API STRIMM::Result AddAnalogueOutput(STRIMM::AbstractTimer * timer, const char* channelName, STRIMM_AODesc desc) { return timer->addAnalogueOutput(channelName, desc); }
	STRIMM_API STRIMM::Result AddDigitalOutput(STRIMM::AbstractTimer * timer, const char* channelName, STRIMM_DODesc desc) { return timer->addDigitalOutput(channelName, desc); }

	STRIMM_API STRIMM::Result RemoveAnalogueInput(STRIMM::AbstractTimer* timer, const char* channelName) { return timer->removeAnalogueInput(channelName); }
	STRIMM_API STRIMM::Result RemoveDigitalInput(STRIMM::AbstractTimer* timer, const char* channelName) { return timer->removeDigitalInput(channelName); }
	STRIMM_API STRIMM::Result RemoveAnalogueOutput(STRIMM::AbstractTimer* timer, const char* channelName) { return timer->removeAnalogueOutput(channelName); }
	STRIMM_API STRIMM::Result RemoveDigitalOutput(STRIMM::AbstractTimer* timer, const char* channelName) { return timer->removeDigitalOutput(channelName); }

	STRIMM_API void RegisterStopCallback(STRIMM::AbstractTimer * timer, STRIMM_StopCallback callback, void* callbackInfo) { return timer->registerStopCallback(callback, callbackInfo); }
	STRIMM_API void	RegisterErrorCallback(STRIMM::AbstractTimer* timer, STRIMM_ErrorCallback callback, void* callbackInfo) { return timer->registerErrorCallback(callback, callbackInfo); }

	STRIMM_API STRIMM::Result Prime(STRIMM::AbstractTimer * timer) { return timer->prime(); }
	STRIMM_API STRIMM::Result Start(STRIMM::AbstractTimer * timer) { return timer->start(); }
	STRIMM_API STRIMM::Result Stop(STRIMM::AbstractTimer * timer) { return timer->stop(); }
	STRIMM_API const char * GetTimerError(STRIMM::AbstractTimer * timer) { return timer->getErrorMessage().getValue(); }

	STRIMM_API STRIMM::PropertyCollection::Property* GetProperty(STRIMM::AbstractTimer* timer, const char* propName) { return timer->getProperty(propName); }
	STRIMM_API STRIMM::PropertyCollection::PropertyType GetPropertyType(STRIMM::PropertyCollection::Property * prop) { return prop->getPropertyType(); }
	STRIMM_API int GetNProperties(STRIMM::AbstractTimer* timer) { return timer->getNProperties(); }
	STRIMM_API const char*GetPropertyName(STRIMM::AbstractTimer* timer, int idx) { return timer->getPropertyName(idx).getValue(); }
	STRIMM_API bool IsPreInit(STRIMM::PropertyCollection::Property * prop) { return prop->isPreInit; }

	PROPERTY_GET(Value, I, int, Integer,)
	PROPERTY_GET(Max, I, int, Integer,)
	PROPERTY_GET(Min, I, int, Integer,)

	PROPERTY_SET(Value, I, int, Integer)
	PROPERTY_SET(Max, I, int, Integer)
	PROPERTY_SET(Min, I, int, Integer)


	PROPERTY_GET(Value, F, float, Float,)
	PROPERTY_GET(Max, F, float, Float,)
	PROPERTY_GET(Min, F, float, Float,)

	PROPERTY_SET(Value, F, float, Float)
	PROPERTY_SET(Max, F, float, Float)
	PROPERTY_SET(Min, F, float, Float)


	PROPERTY_GET(Value, D, double, Double,)
	PROPERTY_GET(Max, D, double, Double,)
	PROPERTY_GET(Min, D, double, Double,)

	PROPERTY_SET(Value, D, double, Double)
	PROPERTY_SET(Max, D, double, Double)
	PROPERTY_SET(Min, D, double, Double)


	PROPERTY_GET(Value, S, const char *, String, .getValue())
	PROPERTY_GET(NAllowedValues, , int, String, )
	PROPERTY_SET(Value, S, const char *, String)
	STRIMM_API const char * GetAllowedValue(STRIMM::PropertyCollection::StringProperty * prop, int i) { return prop->getAllowedValue(i).getValue(); }

	PROPERTY_GET(Value, B, bool, Boolean,)
	PROPERTY_SET(Value, B, bool, Boolean)
}

#include <iostream>

TimerLibFunctions getLibFuncs(LibType libHandle, bool verbose)
{
	TimerLibFunctions funcs = {};

	funcs.Initialise				= (fnInitialise)			getFuncPointer(libHandle, "Initialise");
	funcs.GetTimerName				= (fnGetTimerName)			getFuncPointer(libHandle, "GetTimerName");
	funcs.CreateTimer				= (fnCreateTimer)			getFuncPointer(libHandle, "CreateTimer");
	funcs.DeleteTimer				= (fnDeleteTimer)			getFuncPointer(libHandle, "DeleteTimer");
	funcs.GetAPIVersion				= (fnGetAPIVersion)			getFuncPointer(libHandle, "GetAPIVersion");

	funcs.GetNAvailableChannels		= (fnGetNAvailableChannels)	getFuncPointer(libHandle, "GetNAvailableChannels");
	funcs.GetAvailableChannelName	= (fnGetAvailableChannelName)getFuncPointer(libHandle, "GetAvailableChannelName");
	funcs.GetNChannels				= (fnGetNChannels)			getFuncPointer(libHandle, "GetNChannels");
	funcs.GetChannelName			= (fnGetChannelName)		getFuncPointer(libHandle, "GetChannelName");
	funcs.GetNChannelsInUse			= (fnGetNChannelsInUse)		getFuncPointer(libHandle, "GetNChannelsInUse");
	funcs.GetChannelInUseName		= (fnGetChannelInUseName)	getFuncPointer(libHandle, "GetChannelInUseName");

	funcs.AddAnalogueInput			= (fnAddAnalogueInput)		getFuncPointer(libHandle, "AddAnalogueInput");
	funcs.AddDigitalInput			= (fnAddDigitalInput)		getFuncPointer(libHandle, "AddDigitalInput");
	funcs.AddAnalogueOutput 		= (fnAddAnalogueOutput)		getFuncPointer(libHandle, "AddAnalogueOutput");
	funcs.AddDigitalOutput			= (fnAddDigitalOutput)		getFuncPointer(libHandle, "AddDigitalOutput");

	funcs.RemoveAnalogueInput		= (fnRemoveAnalogueInput)	getFuncPointer(libHandle, "RemoveAnalogueInput");
	funcs.RemoveDigitalInput		= (fnRemoveDigitalInput)	getFuncPointer(libHandle, "RemoveDigitalInput");
	funcs.RemoveAnalogueOutput 		= (fnRemoveAnalogueOutput)	getFuncPointer(libHandle, "RemoveAnalogueOutput");
	funcs.RemoveDigitalOutput		= (fnRemoveDigitalOutput)	getFuncPointer(libHandle, "RemoveDigitalOutput");

	funcs.RegisterStopCallback		= (fnRegisterStopCallback)	getFuncPointer(libHandle, "RegisterStopCallback");
	funcs.RegisterErrorCallback 	= (fnRegisterErrorCallback)	getFuncPointer(libHandle, "RegisterErrorCallback");

	funcs.Prime						= (fnPrime)					getFuncPointer(libHandle, "Prime");
	funcs.Start						= (fnStart)					getFuncPointer(libHandle, "Start");
	funcs.Stop						= (fnStop)					getFuncPointer(libHandle, "Stop");
	funcs.GetTimerError				= (fnGetTimerError)			getFuncPointer(libHandle, "GetTimerError");
	
	// PROPERTY FUNCTIONS
	funcs.GetProperty				= (fnGetProperty)			getFuncPointer(libHandle, "GetProperty");
	funcs.GetPropertyType			= (fnGetPropertyType)		getFuncPointer(libHandle, "GetPropertyType");
	funcs.GetNProperties			= (fnGetNProperties)		getFuncPointer(libHandle, "GetNProperties");
	funcs.GetPropertyName			= (fnGetPropertyName)		getFuncPointer(libHandle, "GetPropertyName");

	// STRING SPECIFIC FUNCTIONS
	funcs.GetValueS					= (fnGetValueS)				getFuncPointer(libHandle, "GetValueS");
	funcs.SetValueS					= (fnSetValueS)				getFuncPointer(libHandle, "SetValueS");
	funcs.GetNAllowedValues 		= (fnGetNAllowedValues)		getFuncPointer(libHandle, "GetNAllowedValues");
	funcs.GetAllowedValue			= (fnGetAllowedValue)		getFuncPointer(libHandle, "GetAllowedValue");
	funcs.IsPreInit					= (fnIsPreInit)				getFuncPointer(libHandle, "IsPreInit");

	// INT SPECIFIC FUNCTIONS
	funcs.GetValueI					= (fnGetValueI)				getFuncPointer(libHandle, "GetValueI");
	funcs.SetValueI					= (fnSetValueI)				getFuncPointer(libHandle, "SetValueI");
	funcs.GetMaxI					= (fnGetMaxI)				getFuncPointer(libHandle, "GetMaxI");
	funcs.GetMinI					= (fnGetMinI)				getFuncPointer(libHandle, "GetMinI");
	funcs.SetMaxI					= (fnSetMaxI)				getFuncPointer(libHandle, "SetMaxI");
	funcs.SetMinI					= (fnSetMinI)				getFuncPointer(libHandle, "SetMinI");

	// FLOAT SPECIFIC FUNCTIONS
	funcs.GetValueF					= (fnGetValueF)				getFuncPointer(libHandle, "GetValueF");
	funcs.SetValueF					= (fnSetValueF)				getFuncPointer(libHandle, "SetValueF");
	funcs.GetMaxF					= (fnGetMaxF)				getFuncPointer(libHandle, "GetMaxF");
	funcs.GetMinF					= (fnGetMinF)				getFuncPointer(libHandle, "GetMinF");
	funcs.SetMaxF					= (fnSetMaxF)				getFuncPointer(libHandle, "SetMaxF");
	funcs.SetMinF					= (fnSetMinF)				getFuncPointer(libHandle, "SetMinF");

	// DOUBLE SPECIFIC FUNCTIONS
	funcs.GetValueD					= (fnGetValueD)				getFuncPointer(libHandle, "GetValueD");
	funcs.SetValueD					= (fnSetValueD)				getFuncPointer(libHandle, "SetValueD");
	funcs.GetMaxD					= (fnGetMaxD)				getFuncPointer(libHandle, "GetMaxD");
	funcs.GetMinD					= (fnGetMinD)				getFuncPointer(libHandle, "GetMinD");
	funcs.SetMaxD					= (fnSetMaxD)				getFuncPointer(libHandle, "SetMaxD");
	funcs.SetMinD					= (fnSetMinD)				getFuncPointer(libHandle, "SetMinD");

	// BOOLEAN SPECIFIC FUNCTIONS
	funcs.GetValueB					= (fnGetValueB)				getFuncPointer(libHandle, "GetValueB");
	funcs.SetValueB					= (fnSetValueB)				getFuncPointer(libHandle, "SetValueB");

	if (verbose)
		funcs.outputMissing();

	return funcs;
}

inline void missing(bool missing, const char* name)
{
	if (missing)
		std::cout << "Function " << name << " not found!" << std::endl;
}

#define STRIMM_DYNAMIC_MISSING_CHECK(func) missing(!func, #func)

void TimerLibFunctions::outputMissing()
{
	STRIMM_DYNAMIC_MISSING_CHECK(Initialise);
	STRIMM_DYNAMIC_MISSING_CHECK(GetTimerName);
	STRIMM_DYNAMIC_MISSING_CHECK(CreateTimer);
	STRIMM_DYNAMIC_MISSING_CHECK(DeleteTimer);
	STRIMM_DYNAMIC_MISSING_CHECK(GetAPIVersion);

	STRIMM_DYNAMIC_MISSING_CHECK(GetNAvailableChannels);
	STRIMM_DYNAMIC_MISSING_CHECK(GetAvailableChannelName);
	STRIMM_DYNAMIC_MISSING_CHECK(GetNChannels);
	STRIMM_DYNAMIC_MISSING_CHECK(GetChannelName);
	STRIMM_DYNAMIC_MISSING_CHECK(GetNChannelsInUse);
	STRIMM_DYNAMIC_MISSING_CHECK(GetChannelInUseName);

	STRIMM_DYNAMIC_MISSING_CHECK(AddAnalogueInput);
	STRIMM_DYNAMIC_MISSING_CHECK(AddDigitalInput);
	STRIMM_DYNAMIC_MISSING_CHECK(AddAnalogueOutput);
	STRIMM_DYNAMIC_MISSING_CHECK(AddDigitalOutput);

	STRIMM_DYNAMIC_MISSING_CHECK(RemoveAnalogueInput);
	STRIMM_DYNAMIC_MISSING_CHECK(RemoveDigitalInput);
	STRIMM_DYNAMIC_MISSING_CHECK(RemoveAnalogueOutput);
	STRIMM_DYNAMIC_MISSING_CHECK(RemoveDigitalOutput);

	STRIMM_DYNAMIC_MISSING_CHECK(RegisterStopCallback);
	STRIMM_DYNAMIC_MISSING_CHECK(RegisterErrorCallback);

	STRIMM_DYNAMIC_MISSING_CHECK(Prime);
	STRIMM_DYNAMIC_MISSING_CHECK(Start);
	STRIMM_DYNAMIC_MISSING_CHECK(Stop);
	STRIMM_DYNAMIC_MISSING_CHECK(GetTimerError);

	STRIMM_DYNAMIC_MISSING_CHECK(GetProperty);
	STRIMM_DYNAMIC_MISSING_CHECK(GetPropertyType);
	STRIMM_DYNAMIC_MISSING_CHECK(GetNProperties);
	STRIMM_DYNAMIC_MISSING_CHECK(GetPropertyName);
	STRIMM_DYNAMIC_MISSING_CHECK(IsPreInit);

	STRIMM_DYNAMIC_MISSING_CHECK(GetValueS);
	STRIMM_DYNAMIC_MISSING_CHECK(SetValueS);
	STRIMM_DYNAMIC_MISSING_CHECK(GetNAllowedValues);
	STRIMM_DYNAMIC_MISSING_CHECK(GetAllowedValue);

	STRIMM_DYNAMIC_MISSING_CHECK(GetValueI);
	STRIMM_DYNAMIC_MISSING_CHECK(SetValueI);
	STRIMM_DYNAMIC_MISSING_CHECK(GetMaxI);
	STRIMM_DYNAMIC_MISSING_CHECK(GetMinI);
	STRIMM_DYNAMIC_MISSING_CHECK(SetMaxI);
	STRIMM_DYNAMIC_MISSING_CHECK(SetMinI);

	STRIMM_DYNAMIC_MISSING_CHECK(GetValueF);
	STRIMM_DYNAMIC_MISSING_CHECK(SetValueF);
	STRIMM_DYNAMIC_MISSING_CHECK(GetMaxF);
	STRIMM_DYNAMIC_MISSING_CHECK(GetMinF);
	STRIMM_DYNAMIC_MISSING_CHECK(SetMaxF);
	STRIMM_DYNAMIC_MISSING_CHECK(SetMinF);

	STRIMM_DYNAMIC_MISSING_CHECK(GetValueD);
	STRIMM_DYNAMIC_MISSING_CHECK(SetValueD);
	STRIMM_DYNAMIC_MISSING_CHECK(GetMaxD);
	STRIMM_DYNAMIC_MISSING_CHECK(GetMinD);
	STRIMM_DYNAMIC_MISSING_CHECK(SetMaxD);
	STRIMM_DYNAMIC_MISSING_CHECK(SetMinD);

	STRIMM_DYNAMIC_MISSING_CHECK(GetValueB);
	STRIMM_DYNAMIC_MISSING_CHECK(SetValueB);
}