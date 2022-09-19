#ifndef _STRIMM_NIDAQ_TASK_H
#define _STRIMM_NIDAQ_TASK_H

#include <NIDAQmx.h>
#include <string>
#include <functional>
#include <vector>
#include <STRIMM_TimingInterface/STRIMM_Timing.h>


typedef STRIMM::AbstractTimer::ChannelType ChannelType;

extern "C" {
	int32 CVICALLBACK EveryNCallbackAnalogue(TaskHandle handle, int32 everyNSamplesEventType, uInt32 nSamples, void* callbackData);
	int32 CVICALLBACK EveryNCallbackDigital(TaskHandle handle, int32 everyNSamplesEventType, uInt32 nSamples, void* callbackData);
	int32 CVICALLBACK DoneCallback(TaskHandle handle, int32 status, void *callbackData);
}

class NIDAQTaskBase
{
public:
	NIDAQTaskBase(double rate, int blockSize, std::function<void(const std::string&)> abort, const std::string& name = "") 
		: m_blockSize(blockSize), m_rate(rate), abort(abort)
	{ 
		DAQmxCreateTask(name.c_str(), &handle);
	}

	NIDAQTaskBase(const NIDAQTaskBase& that) = delete; 
	NIDAQTaskBase(NIDAQTaskBase&& that) = delete;
	NIDAQTaskBase& operator=(NIDAQTaskBase that) = delete; 

	~NIDAQTaskBase() { DAQmxClearTask(handle); handle = nullptr; } 

	std::pair<bool, std::string> stop() 
	{
		if (!CheckNIDAQError(DAQmxStopTask(handle))) 
			return makeError();

		DAQmxClearTask(handle);

		return success;
	}

	std::function<void(const std::string&)> abort;

protected:
	bool CheckNIDAQError(int32 error, std::function<void()> cleanup = []() {})
	{
		if (DAQmxFailed(error))
		{
			std::vector<char> v;
			int32 buffSize = DAQmxGetExtendedErrorInfo(nullptr, 0);
			v.resize(buffSize);
			DAQmxGetExtendedErrorInfo(v.data(), buffSize);

			errorStr = v.data();
			cleanup();

			return false;
		}
		return true;
	}

	TaskHandle handle = nullptr;

	inline std::pair<bool, std::string> makeError() const { return std::make_pair(false, errorStr); }
	const std::pair<bool, std::string> success = std::make_pair(true, "");

	int m_blockSize; 
	double m_rate;

	std::string errorStr = ""; 
};



template<ChannelType channelType>
using channelDesc_type_from_channelType =
	typename std::conditional_t<channelType == ChannelType::AnalogueIn,	STRIMM_AIDesc,
	typename std::conditional_t<channelType == ChannelType::AnalogueOut,STRIMM_AODesc,
	typename std::conditional_t<channelType == ChannelType::DigitalIn,	STRIMM_DIDesc,
	typename std::conditional_t<channelType == ChannelType::DigitalOut,	STRIMM_DODesc, void>>>>;

template<ChannelType channelType>
using channelDesc_with_pair =
	typename std::conditional_t<channelType == ChannelType::AnalogueOut, std::tuple<std::string,channelDesc_type_from_channelType<channelType>, std::vector<double>>,
	typename std::conditional_t<channelType == ChannelType::DigitalOut, std::tuple<std::string,channelDesc_type_from_channelType<channelType>, std::vector<uint8_t>>,
	std::tuple<std::string,channelDesc_type_from_channelType<channelType>> >>;

template<ChannelType channelType>
using buffer_type_from_channelType =
	typename std::conditional_t<channelType == ChannelType::AnalogueIn || channelType == ChannelType::AnalogueOut, double,
	typename std::conditional_t<channelType == ChannelType::DigitalIn  || channelType == ChannelType::DigitalOut,  uint8_t, void>>;

template<ChannelType channelType>
class NIDAQTask : public NIDAQTaskBase
{
public:
	NIDAQTask(double rate, int blockSize, std::function<void(const std::string&)> abort, const std::string& name = "")
		: NIDAQTaskBase(rate, blockSize, abort, name) {};


	static constexpr ChannelType getChannelType() { return channelType; }

	int channelsInUse() { return descriptions.size(); }
	const std::string& channelInUseName(int i) { return std::get<0>(descriptions[i]); }

	std::pair<bool, std::string> addChannel(const std::string& channelName, channelDesc_type_from_channelType<channelType> desc)
	{
		desc.reserved = this;
		if constexpr (channelType == ChannelType::AnalogueOut || channelType == ChannelType::DigitalOut)
		{
			descriptions.emplace_back(channelName, desc, std::vector<buffer_type_from_channelType<channelType>>(m_blockSize));

			auto& last_desc = descriptions.back();
			auto data_ptr = std::get<2>(last_desc).data();
			if constexpr (channelType == ChannelType::AnalogueOut)
				std::memcpy(data_ptr, std::get<1>(last_desc).sampleArray, m_blockSize * sizeof(double));
			else
				std::memcpy(data_ptr, std::get<1>(last_desc).sampleArray, m_blockSize * sizeof(uint8_t));
			std::get<1>(last_desc).sampleArray = data_ptr;
		} 
		else 
		{
			descriptions.emplace_back(channelName, desc);
		}

		return success;
	}

	std::pair<bool, std::string> removeChannel(const std::string& channelName)
	{
		if (auto loc = std::find_if(descriptions.begin(), descriptions.end(), [&channelName](auto desc) { return std::get<0>(desc) == channelName; });
			loc != descriptions.end())
		{
			descriptions.erase(loc);
			return success;
		}
		else
		{
			errorStr = "Could not remove channel! Channel not in task";
			return makeError();
		}
	}

	std::pair<bool, std::string> prime(const std::string& trigger = "")
	{
		for (auto& description : descriptions) {
			auto& channelName = std::get<0>(description);
			auto& desc = std::get<1>(description);

			if constexpr (channelType == ChannelType::AnalogueIn)
			{
				if (!CheckNIDAQError(DAQmxCreateAIVoltageChan(handle, channelName.c_str(), "", DAQmx_Val_Cfg_Default, desc.voltageMin, desc.voltageMax, DAQmx_Val_Volts, nullptr)))
					return makeError();
			}
			else if constexpr (channelType == ChannelType::AnalogueOut)
			{
				double max = std::numeric_limits<double>::lowest();
				double min = std::numeric_limits<double>::max();

				for (int i = 0; i < std::ceil(((float)desc.blockSize) / ((float)desc.clockDiv)); i++)
				{
					max = std::max(desc.sampleArray[i], max);
					min = std::min(desc.sampleArray[i], min);
				}

				if (!CheckNIDAQError(DAQmxCreateAOVoltageChan(handle, channelName.c_str(), "", min, max, DAQmx_Val_Volts, nullptr)))
					return makeError();
			}
			else if constexpr (channelType == ChannelType::DigitalIn)
			{
				if (!CheckNIDAQError(DAQmxCreateDIChan(handle, channelName.c_str(), "", DAQmx_Val_ChanForAllLines)))
					return makeError();
			}
			else
			{
				if (!CheckNIDAQError(DAQmxCreateDOChan(handle, channelName.c_str(), "", DAQmx_Val_ChanForAllLines)))
					return makeError();
			}
		}

		if (!CheckNIDAQError(DAQmxCfgSampClkTiming(handle, "", m_rate, DAQmx_Val_Rising, DAQmx_Val_ContSamps, m_blockSize))) return makeError();

		if constexpr (channelType == ChannelType::AnalogueOut || channelType == ChannelType::DigitalOut)
		{
			if (trigger != "" && !writeChannelData())
				return makeError();
		}
		else if constexpr (channelType == ChannelType::AnalogueIn)
		{
			if (!CheckNIDAQError(DAQmxRegisterEveryNSamplesEvent(handle, DAQmx_Val_Acquired_Into_Buffer, m_blockSize, 0, &EveryNCallbackAnalogue, this)))
				return makeError();
		}
		else
		{
			if (!CheckNIDAQError(DAQmxRegisterEveryNSamplesEvent(handle, DAQmx_Val_Acquired_Into_Buffer, m_blockSize, 0, &EveryNCallbackDigital, this)))
				return makeError();
		}

		if (!CheckNIDAQError(DAQmxRegisterDoneEvent(handle, 0, &DoneCallback, this)))
			return makeError();

		if (trigger != "")
		{
			if (!CheckNIDAQError(DAQmxCfgDigEdgeStartTrig(handle, trigger.c_str(), DAQmx_Val_Rising)))
				return makeError();
			if (!CheckNIDAQError(DAQmxStartTask(handle))) 
				return makeError();
		}

		return success;
	}

	int32 DataAcquiredCallback()
	{
		if constexpr (channelType != ChannelType::AnalogueIn && channelType != ChannelType::DigitalIn)
			return 0;

		auto dataIn = std::vector<buffer_type_from_channelType<channelType>>(m_blockSize*descriptions.size());

		if constexpr (channelType == ChannelType::AnalogueIn)
		{
			int32 nRead;
			if (!CheckNIDAQError(DAQmxReadAnalogF64(handle, m_blockSize, -1, DAQmx_Val_GroupByChannel, dataIn.data(), dataIn.size(), &nRead, nullptr)))
			{
				abort(errorStr);
				return 0;
			}
		}
		else
		{
			int32 nRead, nBytesPerSamp;
			if (!CheckNIDAQError(DAQmxReadDigitalLines(handle, m_blockSize, -1, DAQmx_Val_GroupByChannel, dataIn.data(), dataIn.size(), &nRead, &nBytesPerSamp, nullptr)))
			{
				abort(errorStr);
				return 0;
			}
		}
	
		for (int i = 0; i < descriptions.size(); i++)
		{
			auto& desc = std::get<1>(descriptions[i]);

			if (!desc.bufferCallback)
			{
				errorStr = "Error! No buffer callback specified!";
				abort(errorStr);
				return 0;
			}

			auto buffer = desc.bufferCallback(std::ceil(((float)m_blockSize) / ((float)desc.clockDiv)) , desc.bufferCallbackInfo);
			if (!buffer)
			{
				errorStr = "Error! Failed to get buffer!";
				abort(errorStr);
			}

			if (desc.clockDiv == 1)
				std::memcpy(buffer, &dataIn[i*m_blockSize], m_blockSize * sizeof(buffer_type_from_channelType<channelType>));
			else for (int i = 0, j = 0; i < m_blockSize; j++, i += desc.clockDiv)
				buffer[j] = dataIn[i];

			if (!desc.dataCopiedCallback)
			{
				errorStr = "Error! No buffer callback specified!";
				abort(errorStr);
				return 0;
			}

			desc.dataCopiedCallback(desc.dataCopiedCallbackInfo);
		}

		return 0;
	}

	std::pair<bool, std::string> start() 
	{
		if constexpr ((channelType == ChannelType::AnalogueOut || channelType == ChannelType::DigitalOut))
			if (!writeChannelData()) return makeError();

		return CheckNIDAQError(DAQmxStartTask(handle)) ? success : makeError();
	}

private:
	bool writeChannelData()
	{
		auto channelOutput = std::vector<buffer_type_from_channelType<channelType>>(descriptions.size() * m_blockSize);
		for (int i = 0; i < channelOutput.size(); i++)
		{
			auto desc = descriptions[i % descriptions.size()];
			channelOutput[i] = std::get<2>(desc)[(i / descriptions.size()) / std::get<1>(desc).clockDiv];
		}

		if constexpr(channelType == ChannelType::AnalogueOut)
		{
			if (!CheckNIDAQError(DAQmxWriteAnalogF64(handle, m_blockSize, false, -1, DAQmx_Val_GroupByScanNumber, channelOutput.data(), nullptr, nullptr)))
				return false;
		}
		else
		{
			if (!CheckNIDAQError(DAQmxWriteDigitalLines(handle, m_blockSize, false, -1, DAQmx_Val_GroupByScanNumber, channelOutput.data(), nullptr, nullptr)))
				return false;
		}

		return true;
	}

	std::vector<channelDesc_with_pair<channelType>> descriptions = std::vector<channelDesc_with_pair<channelType>>();

};

extern "C" {
	int32 CVICALLBACK EveryNCallbackAnalogue(TaskHandle handle, int32 everyNSamplesEventType, uInt32 nSamples, void* callbackData)
	{
		NIDAQTask<ChannelType::AnalogueIn>* task = (NIDAQTask<ChannelType::AnalogueIn>*)callbackData;
		return task->DataAcquiredCallback();
	}

	int32 CVICALLBACK EveryNCallbackDigital(TaskHandle handle, int32 everyNSamplesEventType, uInt32 nSamples, void* callbackData)
	{
		NIDAQTask<ChannelType::DigitalIn>* task = (NIDAQTask<ChannelType::DigitalIn>*)callbackData;
		return task->DataAcquiredCallback();
	}

	int32 CVICALLBACK DoneCallback(TaskHandle handle, int32 status, void *callbackData)
	{
		NIDAQTaskBase* task = (NIDAQTaskBase*)callbackData;
		task->abort("");
		return 0;
	}
}

#endif