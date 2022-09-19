#include <STRIMM_TimingInterface\STRIMM_Timing.h>
#include <STRIMM_TimingInterface\dynamic_functions.h>
#include <vector>
#include <array>
#include <NIDAQmx.h>
#include <functional>

#include "InclusiveRange.h"
#include "NIDAQTask.h"

#ifndef STRIMM_NIDAQ_H
#define STRIMM_NIDAQ_H

class NIDAQ_Timer;

extern "C"
{
	struct AnalogueCallbackData
	{
		NIDAQ_Timer* timer;
		STRIMM_DoubleBufferCallback bufferCallback;
		STRIMM_DataCopiedCallback dataCopiedCallback;
		void *bufferCallbackInfo;
		void *dataCopiedCallbackInfo;

		TaskHandle handle;
		int32 everyNSamplesEventType;
		uInt32 nSamples;
	};

	struct DigitalCallbackData
	{
		NIDAQ_Timer* timer;
		STRIMM_uInt8BufferCallback bufferCallback;
		STRIMM_DataCopiedCallback dataCopiedCallback;
		void *bufferCallbackInfo;
		void *dataCopiedCallbackInfo;
		int lineNum;

		TaskHandle handle;
		int32 everyNSamplesEventType;
		uInt32 nSamples;
	};

	struct DoneCallbackData
	{
		NIDAQ_Timer* timer;
		STRIMM_StopCallback stopCallback;
		void* stopCallbackInfo;

		TaskHandle handle;
		int32 status;
	};
}

class NIDAQ_Timer : public STRIMM::AbstractTimer
{
public:
	NIDAQ_Timer();
	NIDAQ_Timer(const NIDAQ_Timer&) = delete;
	NIDAQ_Timer(NIDAQ_Timer&& that) = delete;
	NIDAQ_Timer& operator=(NIDAQ_Timer that) = delete;


	STRIMM::Result initialise() override;

	int getNAvailableChannels(ChannelType type) override;
	const char* getAvailableChannelName(ChannelType type, int i) override;
	int getNChannels(ChannelType type) override;
	const char* getChannelName(ChannelType type, int i) override;
	int getNChannelsInUse(ChannelType type) override;
	const char* getChannelInUseName(ChannelType type, int i) override;

	STRIMM::Result addAnalogueInput(const char* channelName, STRIMM_AIDesc desc) override;
	STRIMM::Result addDigitalInput(const char* channelName, STRIMM_DIDesc desc) override;
	STRIMM::Result addAnalogueOutput(const char* channelName, STRIMM_AODesc desc) override;
	STRIMM::Result addDigitalOutput(const char* channelName, STRIMM_DODesc desc) override;

	STRIMM::Result removeAnalogueInput(const char* channelName) override;
	STRIMM::Result removeDigitalInput(const char* channelName) override;
	STRIMM::Result removeAnalogueOutput(const char* channelName) override;
	STRIMM::Result removeDigitalOutput(const char* channelName) override;

	void registerStopCallback(STRIMM_StopCallback callback, void* info) override;
	void registerErrorCallback(STRIMM_ErrorCallback callback, void* info) override;

	template<ChannelType channelType>
	void setRate(double rate)
	{
		auto prop = getPropertyAs<DoublePropertyType>((channelNameString<channelType>() + " Sample Rate").c_str());
		prop->setValue(rate);
	}

	template<ChannelType channelType>
	void setBlockSize(int blockSize)
	{
		auto prop = getPropertyAs<IntPropertyType>((channelNameString<channelType>() + " Block Size").c_str());
		prop->setValue(blockSize);
	}

	template<ChannelType channelType>
	double getRate()
	{
		auto prop = getPropertyAs<DoublePropertyType>((channelNameString<channelType>() + " Sample Rate").c_str());
		return prop->getValue();
	}

	template<ChannelType channelType>
	int getBlockSize()
	{
		auto prop = getPropertyAs<IntPropertyType>((channelNameString<channelType>() + " Block Size").c_str());
		return prop->getValue();
	}


	STRIMM::Result prime() override;
	STRIMM::Result start() override;
	STRIMM::Result stop() override;
	void abort(const std::string& msg);

private:
	bool primed = false;
	bool CheckNIDAQError(int32 error, std::function<void()> cleanup = []() {});
	TaskHandle setupChannel(const char* channelName, std::vector<TaskHandle>& taskHandles,
		double rate, int blocksize, std::function<int32(std::string, TaskHandle)> assignChannelToTask);
	bool GetNIDAQStrings(int32(*nameFunc)(const char*, char*, uInt32), std::vector<std::string>& out);

	template<ChannelType channelType>
	inline std::string channelNameString()
	{
		if constexpr (channelType == ChannelType::AnalogueIn)
			return "Analogue Input";
		else if (channelType == ChannelType::AnalogueOut)
			return "Analogue Output";
		else if (channelType == ChannelType::DigitalIn)
			return "Digital Input";
		else
			return "Digital Output";
	}

	std::unique_ptr<NIDAQTask<ChannelType::AnalogueIn>>		AITask;
	std::unique_ptr<NIDAQTask<ChannelType::AnalogueOut>>	AOTask;
	std::unique_ptr<NIDAQTask<ChannelType::DigitalIn>>		DITask;
	std::unique_ptr<NIDAQTask<ChannelType::DigitalOut>>		DOTask;

	std::array<std::pair<double, int>, 4> channelConfigs = std::array<std::pair<double, int>, 4>();

	std::vector<std::string> AOPhysicalChannels = std::vector<std::string>();
	std::vector<std::string> AIPhysicalChannels = std::vector<std::string>();
	std::vector<std::string> DOPhysicalChannels = std::vector<std::string>();
	std::vector<std::string> DIPhysicalChannels = std::vector<std::string>();

	std::list<int> AOAvailableChannelIndexes = std::list<int>();
	std::list<int> AIAvailableChannelIndexes = std::list<int>();
	std::list<int> DOAvailableChannelIndexes = std::list<int>();
	std::list<int> DIAvailableChannelIndexes = std::list<int>();

	std::vector<std::string> usedChannels = std::vector<std::string>();

	std::string deviceName;

	int nActiveAO = 0;
	int nActiveAI = 0;
	int nActiveDO = 0;
	int nActiveDI = 0;

	uInt32 nDMAChannels;
	TaskHandle primaryTask = nullptr;

	std::vector<AnalogueCallbackData*> analogueCallbackData;
	std::vector<DigitalCallbackData*> digitalCallbackData;

	STRIMM_StopCallback stopCallback = nullptr;
	void* stopCallbackInfo = nullptr;
	STRIMM_ErrorCallback errorCallback = nullptr;
	void* errorCallbackInfo = nullptr;

	bool running;
};


#endif // !STRIMM_NIDAQ_H

