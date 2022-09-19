/**
*	@file 
*		dynamic_functions.h
*	@brief 
*		Contains the declarations of all the functions exported from the dynamic libraries
*		as well as the corresponding type aliases and the definition of the @ref TimerLibFunctions
*		class that is used in the STRIMM C++ Runtime
*	@author	
*		Elliot Steele
*	@version 
*		1.0.0
*	@date 
*		2018
*	@copyright 
*
*	@verbatim
	MIT License

	Copyright (c) 2018 Elliot Steele

	Permission is hereby granted, free of charge, to any person obtaining a copy
	of this software and associated documentation files (the "Software"), to deal
	in the Software without restriction, including without limitation the rights
	to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
	copies of the Software, and to permit persons to whom the Software is
	furnished to do so, subject to the following conditions:

	The above copyright notice and this permission notice shall be included in all
	copies or substantial portions of the Software.

	THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
	IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
	FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
	AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
	LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
	OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
	SOFTWARE.
	@endverbatim
*/
#ifndef STRIMM_DYNAMIC_FUNCS_H
#define STRIMM_DYNAMIC_FUNCS_H

/** @brief The current version of the STRIMM Timing Interface API */
#define STRIMM_C_API_VERSION 2

#ifdef _WIN32
/** @brief __declspec(dllexport) required for MSVC compilers */
#define STRIMM_API __declspec(dllexport)
#elif __linux__
/** @brief __declspec(dllexport) required for MSVC compilers */
	#define STRIMM_API
#else
/** @brief __declspec(dllexport) required for MSVC compilers */
	#define STRIMM_API
#endif //_WIN32

#ifdef _WIN32

#define WIN32_LEAN_AND_MEAN
#define NOMINMAX
#include <windows.h>
/** @brief Microsoft's 2017 C++ compilers implement std::filesystem but consider them experimental */
#define fs std::experimental::filesystem

/** @brief Dynamic library extension */
#define DYNAMIC_LIB_EXT ".dll"

/** @brief function to open a dynamic library */
#define openLib(name) LoadLibrary((name))

/** @brief function to close a dynamic library */
#define closeLib(mod) FreeLibrary((mod))

/** @brief function to retrieve a function */
#define getFuncPointer(lib, name) GetProcAddress((lib), (name))

/** @brief the type returned by the dynamic library opening function */
#define LibType HINSTANCE

#else //UNTESTED!!!!!

#include <dlfcn.h>

/** @brief Microsoft's 2017 C++ compilers implement std::filesystem but consider them experimental */
#define fs std::filesystem

/** @brief Dynamic library extension */
#define DYNAMIC_LIB_EXT ".so"

/** @brief function to open a dynamic library */
#define openLib(name) dlopen((name), RTLD_LAZY)

/** @brief function to close a dynamic library */
#define closeLib(mod) dlclose((mod))

/** @brief function to retrieve a function */
#define getFuncPointer(lib, name) dlsym((lib), (name))

/** @brief the type returned by the dynamic library opening function */
#define LibType void*

#endif // _WIN32

	// TIMER FUNCTIONS

#include "STRIMM_Timing.h"



extern "C"
{

	/** 
	*	@brief 
	*		Gets the name of the timer
	*	@note 
	*		@b Must be implemented by timer implementations
	*	@return
	*		The name of the timer implemented by the dynamic library
	*/
	STRIMM_API const char*								GetTimerName			();

	/** 
	*	@brief
	*		Creates an instance of STRIMM::AbstractTimer and returns a pointer to it
	*	@note
	*		@b Must be implemented by timer implementations
	*	@return 
	*		A pointer to the timer instance
	*/
	STRIMM_API STRIMM::AbstractTimer*					CreateTimer				();

	/**
	*	@brief
	*		Deletes a timer instance previously created via @ref CreateTimer
	*	@note 
	*		@b Must be implemented by timer implementations
	*	@param timer
	*		The timer to be deleted
	*/
	STRIMM_API void										DeleteTimer				(STRIMM::AbstractTimer* timer);


	/**
	*	@brief
	*		Returns the current API version (i.e., the current value of @ref STRIMM_C_API_VERSION)
	*/
	STRIMM_API int										GetAPIVersion			();

	/** 
	*	@fn STRIMM::Result Initialise(STRIMM::AbstractTimer* timer)
	*	@brief C style wrapper for STRIMM::AbstractTimer's @ref STRIMM::AbstractTimer::initialise "initialise" method 
	*/
	/** 
	*	@fn int GetNAvailableChannels(STRIMM::AbstractTimer* timer, STRIMM::AbstractTimer::ChannelType type)
	*	@brief C style wrapper for STRIMM::AbstractTimer's @ref STRIMM::AbstractTimer::getNAvailableChannels "getNAvailableChannels" method 
	*/
	/** 
	*	@fn const char* GetAvailableChannelName(STRIMM::AbstractTimer* timer, STRIMM::AbstractTimer::ChannelType type, int i)
	*	@brief C style wrapper for STRIMM::AbstractTimer's @ref STRIMM::AbstractTimer::getAvailableChannelName "getAvailableChannelName" method 
	*/
	/** 
	*	@fn int GetNChannels(STRIMM::AbstractTimer* timer, STRIMM::AbstractTimer::ChannelType type)
	*	@brief C style wrapper for STRIMM::AbstractTimer's @ref STRIMM::AbstractTimer::getNChannels "getNChannels" method 
	*/
	/** 
	*	@fn const char * GetChannelName(STRIMM::AbstractTimer* timer, STRIMM::AbstractTimer::ChannelType type, int i)
	*	@brief C style wrapper for STRIMM::AbstractTimer's @ref STRIMM::AbstractTimer::getChannelName "getChannelName" method 
	*/
	/**
	*	@fn STRIMM_API int GetNChannelsInUse(STRIMM::AbstractTimer* timer, STRIMM::AbstractTimer::ChannelType type)
	*	@breif C style wrapper for STRIMM::AbstractTimer's @ref STRIMM::AbstractTimer::getNChannelsInUse "getNChannelsInUse" method
	*/
	/** 
	*	@fn STRIMM_API const char *	GetChannelInUseName(STRIMM::AbstractTimer* timer, STRIMM::AbstractTimer::ChannelType type, int i)
	*	@breif C style wrapper for STRIMM::AbstractTimer's @ref STRIMM::AbstractTimer::getChannelInUseName "getChannelInUseName" method
	*/
	/** 
	*	@fn STRIMM::Result AddAnalogueInput(STRIMM::AbstractTimer* timer, const char* channelName, STRIMM_AIDesc desc)
	*	@brief C style wrapper for STRIMM::AbstractTimer's @ref STRIMM::AbstractTimer::addAnalogueInput "addAnalogueInput" method 
	*/
	/** 
	*	@fn STRIMM::Result AddDigitalInput(STRIMM::AbstractTimer* timer, const char* channelName, STRIMM_DIDesc desc)
	*	@brief C style wrapper for STRIMM::AbstractTimer's @ref STRIMM::AbstractTimer::addDigitalInput "addDigitalInput" method 
	*/
	/** 
	*	@fn STRIMM::Result AddAnalogueOutput(STRIMM::AbstractTimer* timer, const char* channelName, STRIMM_AODesc desc)
	*	@brief C style wrapper for STRIMM::AbstractTimer's @ref STRIMM::AbstractTimer::addAnalogueOutput "addAnalogueOutput" method 
	*/
	/** 
	*	@fn STRIMM::Result AddDigitalOutput(STRIMM::AbstractTimer* timer, const char* channelName, STRIMM_DODesc desc)
	*	@brief C style wrapper for STRIMM::AbstractTimer's @ref STRIMM::AbstractTimer::addDigitalOutput "addDigitalOutput" method 
	*/
	/**
	*	@fn STRIMM_API STRIMM::Result RemoveAnalogueInput(STRIMM::AbstractTimer* timer, const char* channelName)
	*	@brief C style wrapper for STRIMM::AbstractTimer's @ref STRIMM::AbstractTimer::removeAnalogueInput "removeAnalogueInput" method
	*/
	/**
	*	@fn STRIMM_API STRIMM::Result RemoveDigitalInput(STRIMM::AbstractTimer* timer, const char* channelName)
	*	@brief C style wrapper for STRIMM::AbstractTimer's @ref STRIMM::AbstractTimer::removeDigitalInput "removeDigitalInput" method
	*/
	/**
	*	@fn STRIMM_API STRIMM::Result RemoveAnalogueOutput(STRIMM::AbstractTimer* timer, const char* channelName)
	*	@brief C style wrapper for STRIMM::AbstractTimer's @ref STRIMM::AbstractTimer::removeAnalogueOutput "removeAnalogueOutput" method
	*/
	/** 
	*	@fn STRIMM_API STRIMM::Result RemoveDigitalOutput(STRIMM::AbstractTimer* timer, const char* channelName)
	*	@breif C style wrapper for STRIMM::AbstractTimer's @ref STRIMM::AbstractTimer::removeDigitalOutput "removeDigitalOutput" method
	*/
	/** 
	*	@fn void RegisterStopCallback(STRIMM::AbstractTimer* timer, STRIMM_StopCallback callback, void* callbackInfo)
	*	@brief C style wrapper for STRIMM::AbstractTimer's @ref STRIMM::AbstractTimer::registerStopCallback "registerStopCallback" method 
	*/
	/** 
	*	@fn void RegisterErrorCallback(STRIMM::AbstractTimer* timer, STRIMM_ErrorCallback callback, void* callbackInfo)
	*	@brief C style wrapper for STRIMM::AbstractTimer's @ref STRIMM::AbstractTimer::registerErrorCallback "registerErrorCallback" method 
	*/
	/** 
	*	@fn STRIMM_API STRIMM::Result Prime(STRIMM::AbstractTimer* timer)
	*	@brief C style wrapper for STRIMM::AbstractTimer's @ref STRIMM::AbstractTimer::prime "prime" method 
	*/
	/** 
	*	@fn STRIMM_API STRIMM::Result Start(STRIMM::AbstractTimer* timer)
	*	@brief C style wrapper for STRIMM::AbstractTimer's @ref STRIMM::AbstractTimer::start "start" method 
	*/
	/** 
	*	@fn STRIMM_API STRIMM::Result Stop(STRIMM::AbstractTimer* timer)
	*	@brief C style wrapper for STRIMM::AbstractTimer's @ref STRIMM::AbstractTimer::stop "stop" method 
	*/
	/** 
	*	@fn STRIMM_API const char* GetTimerError(STRIMM::AbstractTimer* timer)
	*	@brief C style wrapper for STRIMM::AbstractTimer's @ref STRIMM::AbstractTimer::getTimerError "getTimerError" method 
	*/
	/** 
	*	@fn STRIMM_API STRIMM::PropertyCollection::Property* GetProperty(STRIMM::AbstractTimer* timer, const char* propName)
	*	@brief C style wrapper for STRIMM::AbstractTimer's @ref STRIMM::AbstractTimer::getProperty "getProperty" method 
	*/
	/** 
	*	@fn STRIMM_API STRIMM::PropertyCollection::PropertyType GetPropertyType(STRIMM::PropertyCollection::Property* prop)
	*	@brief C style wrapper for STRIMM::PropertyCollection::Property's @ref STRIMM::PropertyCollection::Property::getPropertyType "getPropertyType" method 
	*/
	/** 
	*	@fn STRIMM_API int GetNProperties(STRIMM::AbstractTimer* timer)
	*	@brief C style wrapper for STRIMM::AbstractTimer's @ref STRIMM::AbstractTimer::getNProperties "getNProperties" method 
	*/
	/** 
	*	@fn STRIMM_API const char* GetPropertyName(STRIMM::AbstractTimer* timer, int idx)
	*	@brief C style wrapper for STRIMM::AbstractTimer's @ref STRIMM::AbstractTimer::getPropertyName "getPropertyName" method 
	*/
	/** 
	*	@fn STRIMM_API bool IsPreInit(STRIMM::PropertyCollection::Property* prop)
	*	@brief C style wrapper for STRIMM::PropertyCollection::Property's @ref STRIMM::PropertyCollection::Property::isPreInit "isPreInit" method 
	*/
	/** 
	*	@fn const char* GetValueS(STRIMM::PropertyCollection::StringProperty* prop)
	*	@brief C style wrapper for STRIMM::PropertyCollection::StringProperty's @ref STRIMM::PropertyCollection::StringProperty::getValueS "getValueS" method 
	*/
	/** 
	*	@fn void SetValueS(STRIMM::PropertyCollection::StringProperty * prop, const char * str)
	*	@brief C style wrapper for STRIMM::PropertyCollection::StringProperty 's @ref STRIMM::PropertyCollection::StringProperty::setValueS "setValueS" method 
	*/
	/** 
	*	@fn int GetNAllowedValues(STRIMM::PropertyCollection::StringProperty * prop)
	*	@brief C style wrapper for STRIMM::PropertyCollection::StringProperty 's @ref STRIMM::PropertyCollection::StringProperty::getNAllowedValues "getNAllowedValues" method 
	*/
	/** 
	*	@fn const char* GetAllowedValue(STRIMM::PropertyCollection::StringProperty * prop, int i)
	*	@brief C style wrapper for STRIMM::PropertyCollection::StringProperty's @ref STRIMM::PropertyCollection::StringProperty::getAllowedValue "getAllowedValue" method 
	*/
	/** 
	*	@fn int GetValueI(STRIMM::PropertyCollection::IntegerProperty* prop)
	*	@brief C style wrapper for STRIMM::PropertyCollection::IntegerProperty's @ref STRIMM::PropertyCollection::IntegerProperty::getValueI "getValueI" method 
	*/
	/** 
	*	@fn void SetValueI(STRIMM::PropertyCollection::IntegerProperty* prop, int i)
	*	@brief C style wrapper for STRIMM::PropertyCollection::IntegerProperty's @ref STRIMM::PropertyCollection::IntegerProperty::setValueI "setValueI" method 
	*/
	/** 
	*	@fn int GetMaxI(STRIMM::PropertyCollection::IntegerProperty* prop)
	*	@brief C style wrapper for STRIMM::PropertyCollection::IntegerProperty's @ref STRIMM::PropertyCollection::IntegerProperty::getMaxI "getMaxI" method 
	*/
	/** 
	*	@fn int GetMinI(STRIMM::PropertyCollection::IntegerProperty* prop)
	*	@brief C style wrapper for STRIMM::PropertyCollection::IntegerProperty's @ref STRIMM::PropertyCollection::IntegerProperty::getMinI "getMinI" method 
	*/
	/** 
	*	@fn void SetMaxI(STRIMM::PropertyCollection::IntegerProperty* prop, int max)
	*	@brief C style wrapper for STRIMM::PropertyCollection::IntegerProperty's @ref STRIMM::PropertyCollection::IntegerProperty::setMaxI "setMaxI" method 
	*/
	/** 
	*	@fn void SetMinI(STRIMM::PropertyCollection::IntegerProperty* prop, int min)
	*	@brief C style wrapper for STRIMM::PropertyCollection::IntegerProperty's @ref STRIMM::PropertyCollection::IntegerProperty::setMinI "setMinI" method 
	*/
	/** 
	*	@fn float GetValueF(STRIMM::PropertyCollection::FloatProperty* prop)
	*	@brief C style wrapper for STRIMM::PropertyCollection::FloatProperty's @ref STRIMM::PropertyCollection::FloatProperty::getValueF "getValueF" method 
	*/
	/** 
	*	@fn void SetValueF(STRIMM::PropertyCollection::FloatProperty* prop, float i)
	*	@brief C style wrapper for STRIMM::PropertyCollection::FloatProperty's @ref STRIMM::PropertyCollection::FloatProperty::setValueF "setValueF" method 
	*/
	/** 
	*	@fn float GetMaxF(STRIMM::PropertyCollection::FloatProperty* prop)
	*	@brief C style wrapper for STRIMM::PropertyCollection::FloatProperty's @ref STRIMM::PropertyCollection::FloatProperty::getMaxF "getMaxF" method 
	*/
	/** 
	*	@fn float GetMinF(STRIMM::PropertyCollection::FloatProperty* prop)
	*	@brief C style wrapper for STRIMM::PropertyCollection::FloatProperty's @ref STRIMM::PropertyCollection::FloatProperty::getMinF "getMinF" method 
	*/
	/** 
	*	@fn void SetMaxF(STRIMM::PropertyCollection::FloatProperty* prop, float max)
	*	@brief C style wrapper for STRIMM::PropertyCollection::FloatProperty's @ref STRIMM::PropertyCollection::FloatProperty::setMaxF "setMaxF" method 
	*/
	/** 
	*	@fn void SetMinF(STRIMM::PropertyCollection::FloatProperty* prop, float min)
	*	@brief C style wrapper for STRIMM::PropertyCollection::FloatProperty's @ref STRIMM::PropertyCollection::FloatProperty::setMinF "setMinF" method 
	*/
	/** 
	*	@fn double GetValueD(STRIMM::PropertyCollection::DoubleProperty* prop)
	*	@brief C style wrapper for STRIMM::PropertyCollection::DoubleProperty's @ref STRIMM::PropertyCollection::DoubleProperty::getValueD "getValueD" method 
	*/
	/** 
	*	@fn void SetValueD(STRIMM::PropertyCollection::DoubleProperty* prop, double i)
	*	@brief C style wrapper for STRIMM::PropertyCollection::DoubleProperty's @ref STRIMM::PropertyCollection::DoubleProperty::setValueD "setValueD" method 
	*/
	/** 
	*	@fn double GetMaxD(STRIMM::PropertyCollection::DoubleProperty* prop)
	*	@brief C style wrapper for STRIMM::PropertyCollection::DoubleProperty's @ref STRIMM::PropertyCollection::DoubleProperty::getMaxD "getMaxD" method 
	*/
	/** 
	*	@fn double GetMinD(STRIMM::PropertyCollection::DoubleProperty* prop)
	*	@brief C style wrapper for STRIMM::PropertyCollection::DoubleProperty's @ref STRIMM::PropertyCollection::DoubleProperty::getMinD "getMinD" method 
	*/
	/** 
	*	@fn void SetMaxD(STRIMM::PropertyCollection::DoubleProperty* prop, double max)
	*	@brief C style wrapper for STRIMM::PropertyCollection::DoubleProperty's @ref STRIMM::PropertyCollection::DoubleProperty::setMaxD "setMaxD" method 
	*/
	/** 
	*	@fn void SetMinD(STRIMM::PropertyCollection::DoubleProperty* prop, double min)
	*	@brief C style wrapper for STRIMM::PropertyCollection::DoubleProperty's @ref STRIMM::PropertyCollection::DoubleProperty::setMinD "setMinD" method 
	*/
	/** 
	*	@fn bool GetValueB(STRIMM::PropertyCollection::BooleanProperty* prop)
	*	@brief C style wrapper for STRIMM::PropertyCollection::BooleanProperty's @ref STRIMM::PropertyCollection::BooleanProperty::getValueB "getValueB" method 
	*/
	/** 
	*	@fn void SetValueB(STRIMM::PropertyCollection::BooleanProperty* prop, bool value)
	*	@brief C style wrapper for STRIMM::PropertyCollection::BooleanProperty's @ref STRIMM::PropertyCollection::BooleanProperty::setValueB "setValueB" method 
	*/

	
	STRIMM_API STRIMM::Result							Initialise				(STRIMM::AbstractTimer* timer);

	STRIMM_API int										GetNAvailableChannels	(STRIMM::AbstractTimer* timer, STRIMM::AbstractTimer::ChannelType type);
	STRIMM_API const char*								GetAvailableChannelName	(STRIMM::AbstractTimer* timer, STRIMM::AbstractTimer::ChannelType type, int i);
	STRIMM_API int										GetNChannels			(STRIMM::AbstractTimer* timer, STRIMM::AbstractTimer::ChannelType type);
	STRIMM_API const char *								GetChannelName			(STRIMM::AbstractTimer* timer, STRIMM::AbstractTimer::ChannelType type, int i);
	STRIMM_API int										GetNChannelsInUse		(STRIMM::AbstractTimer* timer, STRIMM::AbstractTimer::ChannelType type);
	STRIMM_API const char *								GetChannelInUseName		(STRIMM::AbstractTimer* timer, STRIMM::AbstractTimer::ChannelType type, int i);

	STRIMM_API STRIMM::Result 							AddAnalogueInput		(STRIMM::AbstractTimer* timer, const char* channelName, STRIMM_AIDesc desc);
	STRIMM_API STRIMM::Result 							AddDigitalInput			(STRIMM::AbstractTimer* timer, const char* channelName, STRIMM_DIDesc desc);
	STRIMM_API STRIMM::Result 							AddAnalogueOutput		(STRIMM::AbstractTimer* timer, const char* channelName, STRIMM_AODesc desc);
	STRIMM_API STRIMM::Result							AddDigitalOutput		(STRIMM::AbstractTimer* timer, const char* channelName, STRIMM_DODesc desc);

	STRIMM_API STRIMM::Result 							RemoveAnalogueInput		(STRIMM::AbstractTimer* timer, const char* channelName);
	STRIMM_API STRIMM::Result 							RemoveDigitalInput		(STRIMM::AbstractTimer* timer, const char* channelName);
	STRIMM_API STRIMM::Result 							RemoveAnalogueOutput	(STRIMM::AbstractTimer* timer, const char* channelName);
	STRIMM_API STRIMM::Result							RemoveDigitalOutput		(STRIMM::AbstractTimer* timer, const char* channelName);

	STRIMM_API void										RegisterStopCallback	(STRIMM::AbstractTimer* timer, STRIMM_StopCallback callback, void* callbackInfo);
	STRIMM_API void										RegisterErrorCallback	(STRIMM::AbstractTimer* timer, STRIMM_ErrorCallback callback, void* callbackInfo);

	STRIMM_API STRIMM::Result							Prime					(STRIMM::AbstractTimer* timer);
	STRIMM_API STRIMM::Result 							Start					(STRIMM::AbstractTimer* timer);
	STRIMM_API STRIMM::Result							Stop					(STRIMM::AbstractTimer* timer);
	STRIMM_API const char*								GetTimerError			(STRIMM::AbstractTimer* timer);

	// PROPERTY FUNCTIONS
	STRIMM_API STRIMM::PropertyCollection::Property*	GetProperty				(STRIMM::AbstractTimer* timer, const char* propName);
	STRIMM_API STRIMM::PropertyCollection::PropertyType	GetPropertyType			(STRIMM::PropertyCollection::Property		* prop);
	STRIMM_API int										GetNProperties			(STRIMM::AbstractTimer* timer);
	STRIMM_API const char*								GetPropertyName			(STRIMM::AbstractTimer* timer, int idx);
	STRIMM_API bool										IsPreInit				(STRIMM::PropertyCollection::Property* prop);

	// STRING SPECIFIC FUNCTIONS
	STRIMM_API const char*								GetValueS				(STRIMM::PropertyCollection::StringProperty	* prop);
	STRIMM_API void										SetValueS				(STRIMM::PropertyCollection::StringProperty * prop, const char * str);
	STRIMM_API int										GetNAllowedValues		(STRIMM::PropertyCollection::StringProperty * prop);
	STRIMM_API const char*								GetAllowedValue			(STRIMM::PropertyCollection::StringProperty * prop, int i);
	
	// INT SPECIFIC FUNCTIONS
	STRIMM_API int										GetValueI				(STRIMM::PropertyCollection::IntegerProperty* prop);
	STRIMM_API void										SetValueI				(STRIMM::PropertyCollection::IntegerProperty* prop, int i);
	STRIMM_API int										GetMaxI					(STRIMM::PropertyCollection::IntegerProperty* prop);
	STRIMM_API int										GetMinI					(STRIMM::PropertyCollection::IntegerProperty* prop);
	STRIMM_API void										SetMaxI					(STRIMM::PropertyCollection::IntegerProperty* prop, int max);
	STRIMM_API void										SetMinI					(STRIMM::PropertyCollection::IntegerProperty* prop, int min);

	// FLOAT SPECIFIC FUNCTIONS
	STRIMM_API float									GetValueF				(STRIMM::PropertyCollection::FloatProperty* prop);
	STRIMM_API void										SetValueF				(STRIMM::PropertyCollection::FloatProperty* prop, float i);
	STRIMM_API float									GetMaxF					(STRIMM::PropertyCollection::FloatProperty* prop);
	STRIMM_API float									GetMinF					(STRIMM::PropertyCollection::FloatProperty* prop);
	STRIMM_API void										SetMaxF					(STRIMM::PropertyCollection::FloatProperty* prop, float max);
	STRIMM_API void										SetMinF					(STRIMM::PropertyCollection::FloatProperty* prop, float min);

	// DOUBLE SPECIFIC FUNCTIONS
	STRIMM_API double									GetValueD				(STRIMM::PropertyCollection::DoubleProperty* prop);
	STRIMM_API void										SetValueD				(STRIMM::PropertyCollection::DoubleProperty* prop, double i);
	STRIMM_API double									GetMaxD					(STRIMM::PropertyCollection::DoubleProperty* prop);
	STRIMM_API double									GetMinD					(STRIMM::PropertyCollection::DoubleProperty* prop);
	STRIMM_API void										SetMaxD					(STRIMM::PropertyCollection::DoubleProperty* prop, double max);
	STRIMM_API void										SetMinD					(STRIMM::PropertyCollection::DoubleProperty* prop, double min);

	// BOOLEAN SPECIFIC FUNCTIONS
	STRIMM_API bool										GetValueB				(STRIMM::PropertyCollection::BooleanProperty* prop);
	STRIMM_API void										SetValueB				(STRIMM::PropertyCollection::BooleanProperty* prop, bool value);
}

//#define _STRIMM_C_RUNTIME_BUILD
#ifdef _STRIMM_C_RUNTIME_BUILD

// TIMER FUNCTIONS
using fnInitialise					= STRIMM::Result							(*)(STRIMM::AbstractTimer* timer);
using fnGetTimerName				= const char*								(*)();
using fnCreateTimer					= STRIMM::AbstractTimer*					(*)();
using fnDeleteTimer					= void										(*)(STRIMM::AbstractTimer* timer);
using fnGetAPIVersion				= int										(*)();

using fnGetAvailableChannelName		= const char*								(*)(STRIMM::AbstractTimer* timer, STRIMM::AbstractTimer::ChannelType type, int i);
using fnGetNAvailableChannels		= int										(*)(STRIMM::AbstractTimer* timer, STRIMM::AbstractTimer::ChannelType type);
using fnGetNChannels				= int										(*)(STRIMM::AbstractTimer* timer, STRIMM::AbstractTimer::ChannelType type);
using fnGetChannelName				= const char*								(*)(STRIMM::AbstractTimer* timer, STRIMM::AbstractTimer::ChannelType type, int i);
using fnGetNChannelsInUse			= int										(*)(STRIMM::AbstractTimer* timer, STRIMM::AbstractTimer::ChannelType type);
using fnGetChannelInUseName			= const char *								(*)(STRIMM::AbstractTimer* timer, STRIMM::AbstractTimer::ChannelType type, int i);

using fnAddAnalogueInput			= STRIMM::Result							(*)(STRIMM::AbstractTimer* timer, const char* channelName, STRIMM_AIDesc desc);
using fnAddDigitalInput				= STRIMM::Result							(*)(STRIMM::AbstractTimer* timer, const char* channelName, STRIMM_DIDesc desc);
using fnAddAnalogueOutput			= STRIMM::Result							(*)(STRIMM::AbstractTimer* timer, const char* channelName, STRIMM_AODesc desc);
using fnAddDigitalOutput			= STRIMM::Result							(*)(STRIMM::AbstractTimer* timer, const char* channelName, STRIMM_DODesc desc);

using fnRemoveAnalogueInput			= STRIMM::Result							(*)(STRIMM::AbstractTimer* timer, const char* channelName);
using fnRemoveDigitalInput			= STRIMM::Result							(*)(STRIMM::AbstractTimer* timer, const char* channelName);
using fnRemoveAnalogueOutput		= STRIMM::Result							(*)(STRIMM::AbstractTimer* timer, const char* channelName);
using fnRemoveDigitalOutput			= STRIMM::Result							(*)(STRIMM::AbstractTimer* timer, const char* channelName);

using fnPrime						= STRIMM::Result							(*)(STRIMM::AbstractTimer* timer);
using fnStart						= STRIMM::Result							(*)(STRIMM::AbstractTimer* timer);
using fnStop						= STRIMM::Result							(*)(STRIMM::AbstractTimer* timer);
using fnGetTimerError				= const char*								(*)(STRIMM::AbstractTimer* timer);
using fnRegisterStopCallback		= void										(*)(STRIMM::AbstractTimer* timer, STRIMM_StopCallback callback, void* callbackInfo);
using fnRegisterErrorCallback		= void										(*)(STRIMM::AbstractTimer* timer, STRIMM_ErrorCallback callback, void* callbackInfo);

// PROPERTY FUNCTIONS
using fnGetProperty					= STRIMM::PropertyCollection::Property*		(*)(STRIMM::AbstractTimer* timer, const char* propName);
using fnGetPropertyType				= STRIMM::PropertyCollection::PropertyType	(*)(STRIMM::PropertyCollection::Property* prop);
using fnGetNProperties				= int										(*)(STRIMM::AbstractTimer* timer);
using fnGetPropertyName				= const char *								(*)(STRIMM::AbstractTimer* timer, int idx);
using fnIsPreInit					= bool										(*)(STRIMM::PropertyCollection::Property* prop);

// STRING SPECIFIC FUNCTIONS
using fnGetValueS					= const char*								(*)(STRIMM::PropertyCollection::StringProperty* prop);
using fnSetValueS					= void										(*)(STRIMM::PropertyCollection::StringProperty* prop, const char * str);
using fnGetNAllowedValues			= int										(*)(STRIMM::PropertyCollection::StringProperty* prop);
using fnGetAllowedValue				= const char*								(*)(STRIMM::PropertyCollection::StringProperty* prop, int i);

// INT SPECIFIC FUNCTIONS
using fnGetValueI					= int										(*)(STRIMM::PropertyCollection::IntegerProperty* prop);
using fnSetValueI					= void										(*)(STRIMM::PropertyCollection::IntegerProperty* prop, int i);
using fnGetMaxI						= int										(*)(STRIMM::PropertyCollection::IntegerProperty* prop);
using fnGetMinI						= int										(*)(STRIMM::PropertyCollection::IntegerProperty* prop);
using fnSetMaxI						= void										(*)(STRIMM::PropertyCollection::IntegerProperty* prop, int max);
using fnSetMinI						= void										(*)(STRIMM::PropertyCollection::IntegerProperty* prop, int min);

// FLOAT SPECIFIC FUNCTIONS
using fnGetValueF					= float										(*)(STRIMM::PropertyCollection::FloatProperty* prop);
using fnSetValueF					= void										(*)(STRIMM::PropertyCollection::FloatProperty* prop, float i);
using fnGetMaxF						= float										(*)(STRIMM::PropertyCollection::FloatProperty* prop);
using fnGetMinF						= float										(*)(STRIMM::PropertyCollection::FloatProperty* prop);
using fnSetMaxF						= void										(*)(STRIMM::PropertyCollection::FloatProperty* prop, float max);
using fnSetMinF						= void										(*)(STRIMM::PropertyCollection::FloatProperty* prop, float min);

// DOUBLE SPECIFIC FUNCTIONS
using fnGetValueD					= double									(*)(STRIMM::PropertyCollection::DoubleProperty* prop);
using fnSetValueD					= void										(*)(STRIMM::PropertyCollection::DoubleProperty* prop, double i);
using fnGetMaxD						= double									(*)(STRIMM::PropertyCollection::DoubleProperty* prop);
using fnGetMinD						= double									(*)(STRIMM::PropertyCollection::DoubleProperty* prop);
using fnSetMaxD						= void										(*)(STRIMM::PropertyCollection::DoubleProperty* prop, double max);
using fnSetMinD						= void										(*)(STRIMM::PropertyCollection::DoubleProperty* prop, double min);

// BOOLEAN SPECIFIC FUNCTIONS
using fnGetValueB					= bool										(*)(STRIMM::PropertyCollection::BooleanProperty* prop);
using fnSetValueB					= void										(*)(STRIMM::PropertyCollection::BooleanProperty* prop, bool value);


template<class c_Type>
using proptype_from_ctype =
	std::conditional_t<::std::is_same_v<c_Type, int>, STRIMM::PropertyCollection::IntegerProperty,
	std::conditional_t<::std::is_same_v<c_Type, float>, STRIMM::PropertyCollection::FloatProperty,
	std::conditional_t<::std::is_same_v<c_Type, double>, STRIMM::PropertyCollection::DoubleProperty,
	std::conditional_t<::std::is_same_v<c_Type, ::std::string>, STRIMM::PropertyCollection::StringProperty, 
	std::conditional_t<::std::is_same_v<c_Type, bool>, STRIMM::PropertyCollection::BooleanProperty, STRIMM::PropertyCollection::Property>>>>>;


struct TimerLibFunctions
{
	fnInitialise				Initialise				= nullptr;
	fnGetTimerName				GetTimerName			= nullptr;
	fnCreateTimer				CreateTimer				= nullptr;
	fnDeleteTimer				DeleteTimer				= nullptr;
	fnGetAPIVersion				GetAPIVersion			= nullptr;

	fnGetNAvailableChannels		GetNAvailableChannels	= nullptr;
	fnGetAvailableChannelName	GetAvailableChannelName = nullptr;
	fnGetNChannels				GetNChannels			= nullptr;
	fnGetChannelName			GetChannelName			= nullptr;
	fnGetNChannelsInUse			GetNChannelsInUse		= nullptr;
	fnGetChannelInUseName		GetChannelInUseName		= nullptr;

	fnAddAnalogueInput			AddAnalogueInput		= nullptr;
	fnAddDigitalInput			AddDigitalInput			= nullptr;
	fnAddAnalogueOutput			AddAnalogueOutput		= nullptr;
	fnAddDigitalOutput			AddDigitalOutput		= nullptr;

	fnRemoveAnalogueInput		RemoveAnalogueInput		= nullptr;
	fnRemoveDigitalInput		RemoveDigitalInput		= nullptr;
	fnRemoveAnalogueOutput		RemoveAnalogueOutput	= nullptr;
	fnRemoveDigitalOutput		RemoveDigitalOutput		= nullptr;

	fnRegisterStopCallback		RegisterStopCallback	= nullptr;
	fnRegisterErrorCallback		RegisterErrorCallback	= nullptr;

	fnPrime						Prime					= nullptr;
	fnStart						Start					= nullptr;
	fnStop						Stop					= nullptr;
	fnGetTimerError				GetTimerError			= nullptr;

	// PROPERTY FUNCTIONS
	fnGetProperty				GetProperty			= nullptr;
	fnGetPropertyType			GetPropertyType		= nullptr;
	fnGetNProperties			GetNProperties		= nullptr;
	fnGetPropertyName			GetPropertyName		= nullptr;
	fnIsPreInit					IsPreInit			= nullptr;

	// STRING SPECIFIC FUNCTIONS
	fnGetValueS					GetValueS			= nullptr;
	fnSetValueS					SetValueS			= nullptr;
	fnGetNAllowedValues			GetNAllowedValues	= nullptr;
	fnGetAllowedValue			GetAllowedValue		= nullptr;

	// INT SPECIFIC FUNCTIONS
	fnGetValueI					GetValueI			= nullptr;
	fnSetValueI					SetValueI			= nullptr;
	fnGetMaxI					GetMaxI				= nullptr;
	fnGetMinI					GetMinI				= nullptr;
	fnSetMaxI					SetMaxI				= nullptr;
	fnSetMinI					SetMinI				= nullptr;

	// FLOAT SPECIFIC FUNCTIONS
	fnGetValueF					GetValueF			= nullptr;
	fnSetValueF					SetValueF			= nullptr;
	fnGetMaxF					GetMaxF				= nullptr;
	fnGetMinF					GetMinF				= nullptr;
	fnSetMaxF					SetMaxF				= nullptr;
	fnSetMinF					SetMinF				= nullptr;

	// DOUBLE SPECIFIC FUNCTIONS
	fnGetValueD					GetValueD			= nullptr;
	fnSetValueD					SetValueD			= nullptr;
	fnGetMaxD					GetMaxD				= nullptr;
	fnGetMinD					GetMinD				= nullptr;
	fnSetMaxD					SetMaxD				= nullptr;
	fnSetMinD					SetMinD				= nullptr;

	// BOOLEAN SPECIFIC FUNCTIONS
	fnGetValueB					GetValueB			= nullptr;
	fnSetValueB					SetValueB			= nullptr;

	bool isValid()
	{
		return (
			Initialise				&&
			GetTimerName			&&
			CreateTimer				&&
			DeleteTimer				&&
			GetAPIVersion			&&

			GetNAvailableChannels	&&
			GetAvailableChannelName &&
			GetNChannels			&&
			GetChannelName			&&
			GetNChannelsInUse		&&
			GetChannelInUseName		&&

			AddAnalogueInput		&&
			AddDigitalInput			&&
			AddAnalogueOutput		&&
			AddDigitalOutput		&&

			RemoveAnalogueInput		&&
			RemoveDigitalInput		&&
			RemoveAnalogueOutput	&&
			RemoveDigitalOutput		&&

			RegisterStopCallback	&&
			RegisterErrorCallback	&&

			Prime					&&
			Start					&&
			Stop					&&
			GetTimerError			&&

			GetProperty				&&
			GetPropertyType			&&
			GetNProperties			&&
			GetPropertyName			&&
			IsPreInit				&&

			GetValueS				&&
			SetValueS				&&
			GetNAllowedValues		&&
			GetAllowedValue			&&

			GetValueI				&&
			SetValueI				&&
			GetMaxI					&&
			GetMinI					&&
			SetMaxI					&&
			SetMinI					&&

			GetValueF				&&
			SetValueF				&&
			GetMaxF					&&
			GetMinF					&&
			SetMaxF					&&
			SetMinF					&&

			GetValueD				&&
			SetValueD				&&

			GetMaxD					&&
			GetMinD					&&
			SetMaxD					&&
			SetMinD					&&
			GetValueB				&&
			SetValueB);
	}

	void outputMissing();

public:
	template<class c_Type>
	decltype(auto) getValFun()
	{
		if constexpr (std::is_same_v<c_Type, int>)
			return GetValueI;
		if constexpr (std::is_same_v<c_Type, float>)
			return GetValueF;
		if constexpr (std::is_same_v<c_Type, double>)
			return GetValueD;
		if constexpr (std::is_same_v<c_Type, std::string>)
			return GetValueS;
		if constexpr (std::is_same_v<c_Type, bool>)
			return GetValueB;
	}

	template<class c_Type>
	decltype(auto) setValFun()
	{
		if constexpr (std::is_same_v<c_Type, int>)
			return SetValueI;
		if constexpr (std::is_same_v<c_Type, float>)
			return SetValueF;
		if constexpr (std::is_same_v<c_Type, double>)
			return SetValueD;
		if constexpr (std::is_same_v<c_Type, std::string>)
			return SetValueS;
		if constexpr (std::is_same_v<c_Type, bool>)
			return SetValueB;
	}

	template<class c_Type>
	decltype(auto) getMaxFun()
	{
		if constexpr (std::is_same_v<c_Type, int>)
			return GetMaxI;
		if constexpr (std::is_same_v<c_Type, float>)
			return GetMaxF;
		if constexpr (std::is_same_v<c_Type, double>)
			return GetMaxD;
	}

	template<class c_Type>
	decltype(auto) setMaxFun()
	{
		if constexpr (std::is_same_v<c_Type, int>)
			return SetMaxI;
		if constexpr (std::is_same_v<c_Type, float>)
			return SetMaxF;
		if constexpr (std::is_same_v<c_Type, double>)
			return SetMaxD;
	}

	template<class c_Type>
	decltype(auto) getMinFun()
	{
		if constexpr (std::is_same_v<c_Type, int>)
			return GetMinI;
		if constexpr (std::is_same_v<c_Type, float>)
			return GetMinF;
		if constexpr (std::is_same_v<c_Type, double>)
			return GetMinD;
	}

	template<class c_Type>
	decltype(auto) setMinFun()
	{
		if constexpr (std::is_same_v<c_Type, int>)
			return SetMinI;
		if constexpr (std::is_same_v<c_Type, float>)
			return SetMinF;
		if constexpr (std::is_same_v<c_Type, double>)
			return SetMinD;
	}
};

TimerLibFunctions getLibFuncs(LibType libHandle, bool verbose);
#endif


#endif