/**
*	@file 
*		STRIMM_Timing.h
*	@brief 
*		Contains the channel description structures and required alias declarations as well as the STRIMM::AbstractTimer class declaration
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


#define _CRT_SECURE_NO_WARNINGS
#include <cstring>
#include <vector>
#include <exception>
#include <algorithm>
#include <climits>

#include <unordered_map>
#include <memory>

#include "STRIMMString.h"
#include "STRIMM_PropertyCollection.h"

#ifndef STRIMM_TIMING_H
#define STRIMM_TIMING_H

namespace STRIMM { class AbstractTimer; }

extern "C"
{
	/**
	*	@brief 
	*		Type alias representing a pointer to a callback function which produces a buffer of doubles
	*	@param blockSize 
	*		The number of elements the buffer should contain
	*	@param info
	*		A pointer to provided data required for the callback to operate
	*	@return 
	*		A pointer to the beginning of the created buffer of doubles
	*/
	using STRIMM_DoubleBufferCallback	= double*	(*)(int blockSize, void* info);

	/**
	*	@brief 
	*		Type alias representing a pointer to a callback function which produces a buffer of uint8_t
	*	@param blockSize
	*		The number of elements the buffer should contain
	*	@param info
	*		A pointer to provided data required for the callback to operate
	*	@return 
	*		A pointer to the beginning of the created buffer of doubles
	*/
	using STRIMM_uInt8BufferCallback	= uint8_t*	(*)(int blockSize, void* info);

	/**
	*	@brief 
	*		Type alias representing a pointer to a callback function which should run after data has 
	*		been copied to the buffer provided by either a STRIMM_DoubleBufferCallback or a STRIMM_uInt8BufferCallback
	*	@param info
	*		A pointer to provided data required for the callback to operate
	*/
	using STRIMM_DataCopiedCallback		= void		(*)(void* info);

	/**
	*	@brief 
	*		Type alias representing a pointer to a callback function which should run after acquisition has terminated
	*	@param info
	*		A pointer to provided data required for the callback to operate
	*/
	using STRIMM_StopCallback			= void		(*)(void* info);

	/**
	*	@brief 
	*		Type alias representing a pointer to a callback function which should run after an error in acquisition
	*	@param info
	*		A pointer to provided data required for the callback to operate
	*/
	using STRIMM_ErrorCallback			= void		(*)(void* info);

	/**
	*	@brief 
			Description of an analogue input channel used by implementations of STRIMM::AbstractTimer
	*/
	struct STRIMM_AIDesc
	{
		/** @brief The clock division factor specifying the rate of the analogue input relative to the analogue input clock */
		unsigned int clockDiv; 

		/** @brief The minimum voltage expected to be read from the timer */
		double voltageMin; 
		/** @brief The maximum voltage expected to be read from the timer */
		double voltageMax; 

		/** 
		*	@brief The callback used to produce the write buffer
		*	@see STRIMM_DoubleBufferCallback
		*/
		STRIMM_DoubleBufferCallback bufferCallback; 

		/**
		*	@brief The callback run when data has been successfully copied to the buffer
		*	@see STRIMM_DataCopiedCallback
		*/
		STRIMM_DataCopiedCallback dataCopiedCallback;

		/**
		*	@brief A pointer to the data to be passed to bufferCallback when a buffer is requested
		*	@see STRIMM_DoubleBufferCallback
		*/
		void* bufferCallbackInfo;

		/**
		*	@brief A pointer to the data to be passed to dataCopiedCallback when copying has completed
		*	@see STRIMM_DataCopiedCallback
		*/
		void* dataCopiedCallbackInfo;

		/**
		*	@brief Should always be nullptr
		*	@details An implementation of STRIMM::AbstractTimer may choose to assign a value to this member
		*		to include extra information when passing or storing the channel description. This avoids
		*		the need to create wrapper structs in the timer implementations.
		*/
		void* reserved;
	};

	/**
	*	@brief Description of an analogue output channel used by implementations of STRIMM::AbstractTimer
	*/
	struct STRIMM_AODesc
	{
		/** @brief The clock division factor specifying the rate of the analogue output relative to the analogue output clock */
		unsigned int clockDiv;

		/** @brief Pointer to the beginning of an array containing the samples to be written to the analogue output channel*/
		double *sampleArray;
		/** @brief The number of elements in the sampleArray*/
		int blockSize;

		/**
		*	@brief Should always be nullptr
		*	@details An implementation of STRIMM::AbstractTimer may choose to assign a value to this member
		*		to include extra information when passing or storing the channel description. This avoids
		*		the need to create wrapper structs in the timer implementations.
		*/
		void* reserved;
	};

	/**
	*	@brief Description of an digital input channel used by implementations of STRIMM::AbstractTimer
	*/
	struct STRIMM_DIDesc
	{
		/** @brief The clock division factor specifying the rate of the digital input relative to the digital input clock */
		unsigned int clockDiv;

		/** 
		*	@brief 
		*		The callback used to produce the buffer to write to
		*	@see 
		*		STRIMM_uInt8BufferCallback
		*/
		STRIMM_uInt8BufferCallback bufferCallback;

		/**
		*	@brief 
		*		The callback run when data has been successfully copied to the buffer
		*	@see 
		*		STRIMM_DataCopiedCallback
		*/
		STRIMM_DataCopiedCallback dataCopiedCallback;

		/**
		*	@brief 
		*		A pointer to the data to be passed to bufferCallback when a buffer is requested
		*	@see 
		*		STRIMM_DoubleBufferCallback
		*/
		void* bufferCallbackInfo;

		/**
		*	@brief 
		*		A pointer to the data to be passed to dataCopiedCallback when copying has completed
		*	@see 
		*		STRIMM_DataCopiedCallback
		*/
		void* dataCopiedCallbackInfo;

		/**
		*	@brief 
		*		Should always be nullptr
		*	@details 
		*		An implementation of STRIMM::AbstractTimer may choose to assign a value to this member
		*		to include extra information when passing or storing the channel description. This avoids
		*		the need to create wrapper structs in the timer implementations.
		*/
		void* reserved;
	};


	/**
	*	@brief Description of an digital output channel used by implementations of STRIMM::AbstractTimer
	*/
	struct STRIMM_DODesc
	{
		/** @brief The clock division factor specifying the rate of the digital output relative to the digital output clock */
		unsigned int clockDiv;

		/** @brief Pointer to the begining of an array containing the samples to be written to the digital output channel*/
		uint8_t *sampleArray;

		/** @brief The number of elements in the sampleArray*/
		int blockSize;

		/**
		*	@brief 
		*		Should always be nullptr
		*	@details 
		*		An implementation of STRIMM::AbstractTimer may choose to assign a value to this member
		*		to include extra information when passing or storing the the channel description. This avoids
		*		the need to create wrapper structs in the timer implementations.
		*/		
		void* reserved;
	};
}

namespace STRIMM
{
	/**
	*	@brief Enumeration representing the result of a STRIMM::AbstractTimer operation
	*/
	enum class Result
	{
		Success = 1,	/*!< @brief Operation completed successfully */
		Error = 0,		/*!< @brief Operation failed to complete */
		Warning = -1	/*!< @brief Operation completed but with warnings */
	};

	/**
	*	@brief 
	*		The interface that must be implemented to allow STRIMM to comunicate with the timer
	*	@details 
	*		The STRIMM::Abstract timer class provides an interface which allows the STRIMM C++ Runtime to communicate with the timer hardware.
	*		The STRIMM C++ Runtime requests that an implementing DLL provides a pointer to the STRIMM::AbstractTimer. It can then communicate
	*		with the hardware via the functions defined in dynamic_functions.h and dynamic_functions.cpp which wrap calls to the functions defined
	*		in STRIMM::AbstractTimer into a C-style interface which can be used across DLL boundaries. The class extends the 
	*		STRIMM::PropertyCollection class to provide common functionality for managing of properties in a similarly to uManager's device adapters.
	*	@see
	*		dynamic_functions.h \n
	*		dynamic_functions.cpp \n
	*		STRIMM::PropertyCollection
	*/
	class AbstractTimer : public PropertyCollection
	{
	public:

		/**
		*	@brief Enumeration representing the four different channel types
		*/
		enum class ChannelType
		{
			AnalogueOut = 0,	/*!< @brief Analogue output channel */
			AnalogueIn = 1,		/*!< @brief Analogue input channel */
			DigitalOut = 2,		/*!< @brief Digtial output channel */
			DigitalIn = 3		/*!< @brief Digital input channel */
		};

		/**
		*	@brief 
		*		Initialises the timer device
		*	@details 
		*		Initialisation may consist of, for example, requesting a pointer to the device from the system drivers. The
		*		implementation may rely on properties marked as pre-init.
		*	@note 
		*		Not to be confused with construction of the timing device where initial
		*		communication with the device drivers is performed (to determine which devices
		*		are connected for example) and the pre-init properties are defined. 
		*	@returns 
		*		@ref STRIMM::Result "STRIMM::Result::Success" if initialisation was completed successfully.\n
		*		@ref STRIMM::Result "STRIMM::Result::Failure" if initialisation failed.\n
		*		@ref STRIMM::Result "STRIMM::Result::Warning" if initialisation succeeded with warnings.
		*	@pre 
		*		All pre-init properties should be set to valid values.
		*/
		virtual Result initialise() = 0;

		/**
		*	@brief 
		*		Gets the number of channels available for a given channel type
		*	@details 
		*		Available channels are defined as the channels which are not in use and can be assigned to perform a task.
		*	@note 
		*		This should not be confused with getNChannels which returns the total number of channels
		*		present on the device (i.e., including both available and unavailable channels)
		*	@param type
		*		The channel type to recieve information about
		*	@return
		*		The number of available channels for the given channel type
		*	@pre
		*		The device should be initialised
		*	@see
		*		getNChannels
		*/
		virtual int getNAvailableChannels(ChannelType type) = 0;

		/**
		*	@brief
		*		Gets the name corresponding to the available channel at index i.
		*	@details
		*		Gets the name corresponding to the available channel for a given chnanel type. Available
		*		channels are defined as the channels which are not in use and can be assigned to perform
		*		a task. Ideally the implementation should store the names in such a way that the order of
		*		the available channels remains the same even when channels become unavailable.
		*	@note
		*		This should not be confused with getChannelName which returns the name of the channel at
		*		index i in the collection containing all available and unavailable channels.
		*	@param type
		*		The type of channel
		*	@param i
		*		The index of the channel in the backing collection (0 <= i < getNAvailableChannels(type))
		*	@return
		*		The name of the channel at index i
		*	@pre
		*		The device should be initialised
		*	@see
		*		getNAvailableChannels \n
		*		getChannelName
		*/
		virtual const char* getAvailableChannelName(ChannelType type, int i) = 0;

		/**
		*	@brief 
		*		Gets the number of channels present on the device
		*	@details
		*		Returns the total number of channels present on the device, irrespective of whether they
		*		are available or not
		*	@note
		*		This should not be confused with getNAvailableChannels which returns the number of channels
		*		that are available to be assigned to a task.
		*	@param type
		*		The channel type to receive information about
		*	@return
		*		The total number of channels present on the device
		*	@pre
		*		The device should be initialised
		*	@see
		*		getNAvailableChannels
		*/
		virtual int getNChannels(ChannelType type) = 0;

		/**
		*	@brief 
		*		Gets the name corresponding to the channel at index i
		*	@details
		*		The collection of names from which the name is retrieved contains all channels irrespective
		*		of whether they're available or not
		*	@note
		*		This should not be confused with getAvailableChannelName for which the backing collection
		*		does not contain channels that are unavailable
		*	@param type
		*		The channel type
		*	@param i
		*		The index of the channel in the backing collection (0 <= i < GetNChannels(type))
		*	@return
		*		The name of the channel
		*	@pre
		*		The device should be initialised
		*	@see
		*		getAvailableChannelName \n
		*		getNChannels
		*/
		virtual const char* getChannelName(ChannelType type, int i) = 0;

		/**
		*	@brief 
		*		Gets the number of channels currently in use on the device
		*	@details
		*		Returns the total number of channels currently in use on the device
		*	@note
		*		This should not be confused with getNAvailableChannels or getNChannels, which return the number of channels
		*		that are available to be assigned to a task and the total number of channels respectively.
		*	@param type
		*		The channel type to receive information about
		*	@return
		*		The total number of channels currently in use
		*	@pre
		*		The device should be initialised
		*	@see
		*		getNAvailableChannels\n
		*		getNChannels
		*/
		virtual int getNChannelsInUse(ChannelType type) = 0;

		/**
		*	@brief 
		*		Gets the name corresponding to the in use channel at index i
		*	@details
		*		The collection of names from which the name is retrieved contains all channels 
		*		which are currently in use
		*	@note
		*		This should not be confused with getAvailableChannelName or getChannelName
		*	@param type
		*		The channel type
		*	@param i
		*		The index of the channel in the backing collection (0 <= i < GetNChannels(type))
		*	@return
		*		The name of the channel
		*	@pre
		*		The device should be initialised
		*	@see
		*		getAvailableChannelName \n
		*		getChannelName \n
		*		getNChannelsInUse
		*/
		virtual const char* getChannelInUseName(ChannelType type, int i) = 0;
		
		/**
		*	@brief
		*		Sets up an analogue input channel on the timer device
		*	@details
		*		Once the function returns the analogue input channel will be set up to run when prime/start are
		*		executed.
		*	@note
		*		While the STRIMM_AIDesc structure is copied by value, the copy performed is not a deep copy and
		*		as a result the caller of addAnalogueInput is expected to keep the data pointed to by bufferCallbackInfo,
		*		and dataCopiedCallbackInfo valid for the lifetime of the channel.
		*	@param channelName
		*		The name of the physical channel to be used, returned from getChannelName or getAvailableChannelName
		*	@param desc
		*		The description of the analogue input channel
		*	@returns
		*		@ref STRIMM::Result "STRIMM::Result::Success" if the channel was added successfully.\n
		*		@ref STRIMM::Result "STRIMM::Result::Failure" if adding the channel failed.\n
		*		@ref STRIMM::Result "STRIMM::Result::Warning" if the channel was added successfully but with warnings.
		*	@pre
		*		The device should be initiialised
		*	@see
		*		getAvailableChannelName \n
		*		getChannelName \n
		*		STRIMM_AIDesc
		*/
		virtual Result addAnalogueInput		(const char* channelName, STRIMM_AIDesc desc) = 0;

		/**
		*	@brief
		*		Sets up a digital input channel on the timer device
		*	@details
		*		Once the function returns the digital input channel will be set up to run when prime/start are
		*		executed.
		*	@note
		*		While the STRIMM_DIDesc structure is copied by value, the copy performed is not a deep copy and
		*		as a result the caller of addDigitalInput is expected to keep the data pointed to by bufferCallbackInfo
		*		and dataCopiedCallbackInfo valid for the lifetime of the channel.
		*	@param channelName
		*		The name of the physical channel to be used, returned from getChannelName or getAvailableChannelName
		*	@param desc
		*		The description of the digital input channel
		*	@returns
		*		@ref STRIMM::Result "STRIMM::Result::Success" if the channel was added successfully.\n
		*		@ref STRIMM::Result "STRIMM::Result::Failure" if adding the channel failed.\n
		*		@ref STRIMM::Result "STRIMM::Result::Warning" if the channel was added successfully but with warnings.
		*	@pre
		*		The device should be initiialised
		*	@see
		*		getAvailableChannelName \n
		*		getChannelName \n
		*		STRIMM_DIDesc		
		*/
		virtual Result addDigitalInput		(const char* channelName, STRIMM_DIDesc desc) = 0;

		/**
		*	@brief 
		*		sets up a digital input channel on the timer device
		*	@details
		*		Once the function returns the analogue output channel will be set up to run when prime/start
		*		are executed.
		*	@note
		*		Implementations of addAnalogueOuptut are expected to perform a copy of the sampleArray values
		*		so callers of the addAnalogueOuput function are not required to keep the pointer valid after
		*		addAnalogueOuptut returns.
		*	@param channelName
		*		The name of the physical channel to be used, returned from getChannelName or getAvailableChannelName
		*	@param desc
		*		The description of the analogue output channel
		*	@returns
		*		@ref STRIMM::Result "STRIMM::Result::Success" if the channel was added successfully.\n
		*		@ref STRIMM::Result "STRIMM::Result::Failure" if adding the channel failed.\n
		*		@ref STRIMM::Result "STRIMM::Result::Warning" if the channel was added successfully but with warnings.
		*	@pre
		*		The device should be initialised
		*	@see
		*		getAvailableChannelName \n
		*		getChannelName \n
		*		STRIMM_AODesc
		*/
		virtual Result addAnalogueOutput	(const char* channelName, STRIMM_AODesc desc) = 0;

		/**
		*	@brief 
		*		Sets up a digital output channel on the timer device
		*	@details
		*		Once the function returns the digital output channel will be set up to run when prime/start
		*		are executed.
		*	@note
		*		Implementations of addDigitalInput are expected to perform a copy of the sampleArray values
		*		so callers of the addDigitalOuput function are not required to keep this pointer valid after
		*		addDigitalOuput returns.
		*	@param channelName
		*		The name of the physical channel to be used, returned from getChannelName or getAvailableChannelName
		*	@param desc
		*		The description of the analogue output channel
		*	@returns
		*		@ref STRIMM::Result "STRIMM::Result::Success" if the channel was added successfully.\n
		*		@ref STRIMM::Result "STRIMM::Result::Failure" if adding the channel failed.\n
		*		@ref STRIMM::Result "STRIMM::Result::Warning" if the channel was added successfully but with warnings.
		*	@pre
		*		The device should be initialised
		*	@see
		*		getAvailableChannelName \n
		*		getChannelName \n		
		*		STRIMM_DODesc
		*/
		virtual Result addDigitalOutput		(const char* channelName, STRIMM_DODesc desc) = 0;

		/**
		*	@brief
		*		Removes a channel previously set up by addAnalogueInput
		*	@param channelName
		*		The name of the in use channel to be removed
		*	@returns
		*		@ref STRIMM::Result "STRIMM::Result::Success" if the channel was removed successfully.\n
		*		@ref STRIMM::Result "STRIMM::Result::Failure" if the channel could not be removed.\n
		*	@pre
		*		The device should be initialised
		*	@see
		*		getChannelInUseName()
		*/
		virtual Result removeAnalogueInput	(const char* channelName) = 0;

		/**
		*	@brief
		*		Removes a channel previously set up by addAnalogueOutput
		*	@param channelName
		*		The name of the in use channel to be removed
		*	@returns
		*		@ref STRIMM::Result "STRIMM::Result::Success" if the channel was removed successfully.\n
		*		@ref STRIMM::Result "STRIMM::Result::Failure" if the channel could not be removed.\n
		*	@pre
		*		The device should be initialised
		*	@see
		*		getChannelInUseName()
		*/
		virtual Result removeAnalogueOutput	(const char* channelName) = 0;

		/**
		*	@brief
		*		Removes a channel previously set up by addDigitalInput
		*	@param channelName
		*		The name of the in use channel to be removed
		*	@returns
		*		@ref STRIMM::Result "STRIMM::Result::Success" if the channel was removed successfully.\n
		*		@ref STRIMM::Result "STRIMM::Result::Failure" if the channel could not be removed.\n
		*	@pre
		*		The device should be initialised
		*	@see
		*		getChannelInUseName()
		*/
		virtual Result removeDigitalInput	(const char* channelName) = 0;

		/**
		*	@brief
		*		Removes a channel previously set up by addDigitalOutput
		*	@param channelName
		*		The name of the in use channel to be removed
		*	@returns
		*		@ref STRIMM::Result "STRIMM::Result::Success" if the channel was removed successfully.\n
		*		@ref STRIMM::Result "STRIMM::Result::Failure" if the channel could not be removed.\n
		*	@pre
		*		The device should be initialised
		*	@see
		*		getChannelInUseName()
		*/
		virtual Result removeDigitalOutput	(const char* channelName) = 0;

		/**
		*	@brief 
		*		Registers the callback to be run when the acquisition stops
		*	@details
		*		The registered function will be executed once upon termination of the acquisition, by normal
		*		means or if the acquisition fails.
		*	@note
		*		Callers are required to keep the callbackInfo pointer valid during any acquisitions where
		*		the callback may be called
		*	@param callback
		*		A pointer to the funtion to be called upon termination of the acquisition
		*	@param callbackInfo
		*		A pointer to data that will be passed to the callback function
		*	@pre
		*		The device should be initialised
		*	@see
		*		STRIMM_StopCallback
		*/
		virtual void registerStopCallback	(STRIMM_StopCallback callback, void* callbackInfo) = 0;

		/**
		*	@brief
		*		Registers the clalback to be run when the acquisition stops
		*	@details
		*		The registered function will be executed once upon termination of the acquisition due to an error.
		*		The error callback is called @b before the stop callback is called
		*		Callers are required to keep the callbackInfo pointer valid for the lifetime of the acquisition
		*	@note
		*		Callers are required to keep the callbackInfo pointer valid during any acquisitions where
		*		the callback may be called
		*	@param callback
		*		A pointer to the funtion to be called upon termination of the acquisition
		*	@param callbackInfo
		*		A pointer to data that will be passed to the callback function
		*	@pre
		*		The device should be initialised	
		*	@see
		*		registerStopCallback \n
		*		STRIMM_ErrorCallback
		*/
		virtual void registerErrorCallback	(STRIMM_ErrorCallback callback, void* callbackInfo) = 0;

		/**
		*	@brief 
		*		Performs any required pre-start tasks such that the acquisition can begin as soon as possible
		*		when start is called
		*	@pre
		*		The device should be initialised. Additionally, the desired channels must be configured and
		*		the stop and error callbacks should be registered (if required).
		*	@returns
		*		@ref STRIMM::Result "STRIMM::Result::Success" if priming was successful for all channels.\n
		*		@ref STRIMM::Result "STRIMM::Result::Failure" if priming failed for any channels.\n
		*		@ref STRIMM::Result "STRIMM::Result::Warning" if priming succeeded for all channels but some produced warnings.
		*	@see
		*		addAnalogueInput \n
		*		addAnalogueOutput \n
		*		addDigitalInput \n
		*		addDigitalOutput \n
		*		registerStopCallback \n
		*		registerErrorCallback \n
		*		start 
		*/
		virtual Result prime() = 0;

		/**
		*	@brief
		*		Starts the acquisition
		*	@details
		*		Once started the acquisition will run in its own thread(s). When data is available from an
		*		output channel the timer will request a buffer via the STRIMM_DoubleBufferCallback or the
		*		STRIMM_uInt8BufferCallback. Once it receives the buffer it will attempt to copy the received
		*		data to it. Once the data is copied the timer will call the STRIMM_DataCopiedCallback function
		*		associated with that channel. If at any point during the acquisition there is an error that
		*		prevents the acquisition from continuing the timer will automatically abort the acquisition,
		*		calling the error and then stop callbacks. The acquisition may be stopped gracefully using
		*		the stop method.
		*	@returns
		*		@ref STRIMM::Result "STRIMM::Result::Success" if the acquisition started successfully.\n
		*		@ref STRIMM::Result "STRIMM::Result::Failure" if the acquisition failed to start.\n
		*		@ref STRIMM::Result "STRIMM::Result::Warning" if the acquisition started with warnings.
		*	@pre
		*		@ref prime should have been called successfully
		*	@see
		*		prime \n
		*		stop \n
		*		registerStopCallback \n
		*		registerErrorCallback
		*/
		virtual Result start() = 0;

		/**
		*	@brief
		*		Terminates the acquisition
		*	@details
		*		Once the acquisition has been stopped the registered stop callback is called.
		*	@returns
		*		@ref STRIMM::Result "STRIMM::Result::Success" if the acquisition stopped successfully.\n
		*		@ref STRIMM::Result "STRIMM::Result::Failure" if the acquisition failed to stop.\n
		*		@ref STRIMM::Result "STRIMM::Result::Warning" if the acquisition stopped with warnings.
		*	@pre
		*		An acquisition should be running (i.e., @ref start should have been called with a successful
		*		result)
		*	@see
		*		start \n
		*		registerStopCallback
		*/
		virtual Result stop() = 0;

		/**
		*	@brief
		*		Returns the current STRIMM Timing Interface API version
		*	@details
		*		Used by the STRIMM C++ Runtime to determine the API version used to compile the timer
		*		to ensure the timer is compatible
		*	@return
		*		The value of @ref STRIMM_C_API_VERSION (defined in dynamic_functions.h)
		*	@see
		*		STRIMM_C_API_VERSION \n
		*		dynamic_functions.h 
		*/
		static int getAPIVersion();

		/**
		*	@brief
		*		Returns the last error message (or the empty string if there have been no errors)
		*	@details
		*		The error message can be set by the timer via the @ref setErrorMessage method. When
		*		a new error occurs the timer will overwrite the previous error message so the
		*		last error message should be checked immediately upon a failure or warning result
		*	@return
		*		The last error message
		*	@see
		*		setErrorMessage
		*/
		const STRIMM::String& getErrorMessage();

	protected:

		/**
		*	@brief
		*		Used to set the latest error message
		*	@details
		*		Overwrites the previous error message
		*	@param errorMessage
		*		The new error message
		*	@see
		*		getErrorMessage
		*/
		void setErrorMessage(const STRIMM::String& errorMessage);
	private:

		/** @brief The last error message */
		STRIMM::String _lastError = "";
	};
}


#endif // !STRIMM_TIMING_H

