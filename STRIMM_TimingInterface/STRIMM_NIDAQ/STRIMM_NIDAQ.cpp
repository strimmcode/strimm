#include "STRIMM_NIDAQ.h"
#include <iostream>
#include <cctype>
#include <cmath>
#include <limits>
#include <array>

#ifdef _DEBUG
const char g_deviceName[] = "STRIMM_NIDAQ_Timer_dbg";
#else
const char g_deviceName[] = "STRIMM_NIDAQ_Timer";
#endif

STRIMM_API const char * GetTimerName() 
{
	return g_deviceName;
}

STRIMM_API STRIMM::AbstractTimer* CreateTimer() 
{
	return new NIDAQ_Timer();
}

STRIMM_API void DeleteTimer(STRIMM::AbstractTimer* timer) 
{
	delete timer;
}

#define NOT_IMPLEMENTED() setErrorMessage("Error! : " __FUNCTION__ " not implemented!");

// NOTE RETURNS FROM FUNCTION ON FAILURE!
#define CheckTaskResult(task) \
if (auto [success, msg] = (task); !success) \
{ \
	setErrorMessage(msg.c_str()); \
	return STRIMM::Result::Error; \
}

NIDAQ_Timer::NIDAQ_Timer()
{
	auto nameProp = createProperty<StringPropertyType>(true);

	int buffSize = DAQmxGetSystemInfoAttribute(DAQmx_Sys_DevNames, nullptr);
	char * devNames = new char[buffSize];
	DAQmxGetSystemInfoAttribute(DAQmx_Sys_DevNames, devNames, buffSize);

	rsize_t strmax = buffSize;

	if (buffSize != 0)
	{
		char * pch = strtok(devNames, ",");
		while (pch != nullptr)
		{
			nameProp->addAllowedValue(pch);
			pch = strtok(NULL, ", ");
		}
	}

	nameProp->addAllowedValue("Not Selected");
	nameProp->setValue("Not Selected");

	auto aiRateProp = createProperty<DoublePropertyType>();
	auto aoRateProp = createProperty<DoublePropertyType>();
	auto diRateProp = createProperty<DoublePropertyType>();
	auto doRateProp = createProperty<DoublePropertyType>();

	auto aiBlockSizeProp = createProperty<IntPropertyType>();
	auto aoBlockSizeProp = createProperty<IntPropertyType>();
	auto diBlockSizeProp = createProperty<IntPropertyType>();
	auto doBlockSizeProp = createProperty<IntPropertyType>();

	aiRateProp->setValue(1000.0);
	aoRateProp->setValue(1000.0);
	diRateProp->setValue(1000.0);
	doRateProp->setValue(1000.0);

	aiBlockSizeProp->setValue(1000);
	aoBlockSizeProp->setValue(1000);
	diBlockSizeProp->setValue(1000);
	doBlockSizeProp->setValue(1000);

	aiBlockSizeProp->setMin(1);
	aoBlockSizeProp->setMin(1);
	diBlockSizeProp->setMin(1);
	doBlockSizeProp->setMin(1);

	registerProperty("Analogue Input Sample Rate", std::move(aiRateProp));
	registerProperty("Analogue Output Sample Rate", std::move(aoRateProp));
	registerProperty("Digital Input Sample Rate", std::move(diRateProp));
	registerProperty("Digital Output Sample Rate", std::move(doRateProp));

	registerProperty("Analogue Input Block Size", std::move(aiBlockSizeProp));
	registerProperty("Analogue Output Block Size", std::move(aoBlockSizeProp));
	registerProperty("Digital Input Block Size", std::move(diBlockSizeProp));
	registerProperty("Digital Output Block Size", std::move(doBlockSizeProp));

#ifdef _DEBUG
	// Properties for testing regestering properties 
	registerProperty("Device Name", std::move(nameProp));

	auto testIntegerProperty = createProperty<IntPropertyType>();
	auto testFloatProperty = createProperty<FloatPropertyType>();
	auto testDoubleProperty = createProperty<DoublePropertyType>();
	auto testBooleanProperty = createProperty<BooleanPropertyType>();
	auto testRestrictedStringProperty = createProperty<StringPropertyType>();
	auto testUnrestrictedStringProperty = createProperty<StringPropertyType>();


	testIntegerProperty->setMax(200);
	testIntegerProperty->setMin(-200);
	testIntegerProperty->setValue(100);

	testFloatProperty->setValue(50.0f);
	testFloatProperty->setMin(0.0f);
	testFloatProperty->setMax(10000.0f);

	testDoubleProperty->setValue(25.0);
	testDoubleProperty->setMin(-1000.0);
	testDoubleProperty->setMax(1000.0);

	testBooleanProperty->setValue(true);

	testRestrictedStringProperty->addAllowedValue("Not Selected");
	testRestrictedStringProperty->addAllowedValue("Allowed Value 1");
	testRestrictedStringProperty->addAllowedValue("Allowed Value 2");
	testRestrictedStringProperty->addAllowedValue("Allowed Value 3");
	testRestrictedStringProperty->addAllowedValue("Allowed Value 4");
	testRestrictedStringProperty->setValue("Not Selected");
	
	testUnrestrictedStringProperty->setValue("<Enter Value>");

	registerProperty("Test Int", std::move(testIntegerProperty));
	registerProperty("Test Float", std::move(testFloatProperty));
	registerProperty("Test Double", std::move(testDoubleProperty));
	registerProperty("Test Bool", std::move(testBooleanProperty));
	registerProperty("Test Restricted String", std::move(testRestrictedStringProperty));
	registerProperty("Test Unrestricted String", std::move(testUnrestrictedStringProperty));
#endif //_DEBUG
}


STRIMM::Result NIDAQ_Timer::initialise()
{
	auto nameProp = getPropertyAs<StringPropertyType>("Device Name");
	if (nameProp->getValue() == "Not Selected")
	{
		setErrorMessage("Initialisation Failed: NIDAQ device not selected!");
		return STRIMM::Result::Error;
	}

	deviceName = nameProp->getValue().getValue();

	/*auto diPorts = std::vector<std::string>();
	auto doPorts = std::vector<std::string>();
	GetNIDAQStrings(&DAQmxGetDevDIPorts, diPorts);
	GetNIDAQStrings(&DAQmxGetDevDIPorts, doPorts);

	digitalInputRanges.resize(diPorts.size());
	digitalOutputRanges.resize(doPorts.size());*/

	//auto tmp = std::vector<std::string>();
	//GetNIDAQStrings(&DAQmxGetDevTerminals, tmp);

	GetNIDAQStrings(&DAQmxGetDevAOPhysicalChans, AOPhysicalChannels);
	GetNIDAQStrings(&DAQmxGetDevAIPhysicalChans, AIPhysicalChannels);
	GetNIDAQStrings(&DAQmxGetDevDOLines, DOPhysicalChannels);
	GetNIDAQStrings(&DAQmxGetDevDILines, DIPhysicalChannels);

	for (int i = AOPhysicalChannels.size() - 1; i >= 0; i--) AOAvailableChannelIndexes.push_front(i);
	for (int i = AIPhysicalChannels.size() - 1; i >= 0; i--) AIAvailableChannelIndexes.push_front(i);
	for (int i = DOPhysicalChannels.size() - 1; i >= 0; i--) DOAvailableChannelIndexes.push_front(i);
	for (int i = DIPhysicalChannels.size() - 1; i >= 0; i--) DIAvailableChannelIndexes.push_front(i);

	if (!CheckNIDAQError(DAQmxGetDevNumDMAChans(deviceName.c_str(), &nDMAChannels)))
		return STRIMM::Result::Error;

	return STRIMM::Result::Success;
}

int NIDAQ_Timer::getNAvailableChannels(ChannelType type)
{
	if (type == ChannelType::AnalogueOut)
		return AOAvailableChannelIndexes.size();
	if (type == ChannelType::AnalogueIn)
		return AIAvailableChannelIndexes.size();
	if (type == ChannelType::DigitalOut)
		return DOAvailableChannelIndexes.size();
	if (type == ChannelType::DigitalIn)
		return DIAvailableChannelIndexes.size();
}

const char * NIDAQ_Timer::getAvailableChannelName(ChannelType type, int i)
{
	
	if (type == ChannelType::AnalogueOut && i >= 0 && i < AOAvailableChannelIndexes.size())
		return AOPhysicalChannels[*std::next(AOAvailableChannelIndexes.begin(), i)].c_str();
	if (type == ChannelType::AnalogueIn && i >= 0 && i < AIAvailableChannelIndexes.size())
		return AIPhysicalChannels[*std::next(AIAvailableChannelIndexes.begin(), i)].c_str();
	if (type == ChannelType::DigitalOut && i >= 0 && i < DOAvailableChannelIndexes.size())
		return DOPhysicalChannels[*std::next(DOAvailableChannelIndexes.begin(), i)].c_str();
	if (type == ChannelType::DigitalIn && i >= 0 && i < DIAvailableChannelIndexes.size())
		return DIPhysicalChannels[*std::next(DIAvailableChannelIndexes.begin(), i)].c_str();	
	
	return nullptr;
}

int NIDAQ_Timer::getNChannels(ChannelType type)
{
	if (type == ChannelType::AnalogueOut)
		return AOPhysicalChannels.size(); 
	if (type == ChannelType::AnalogueIn)
		return AIPhysicalChannels.size(); 
	if (type == ChannelType::DigitalOut)
		return DOPhysicalChannels.size(); 
	if (type == ChannelType::DigitalIn)
		return DIPhysicalChannels.size(); 
}

const char * NIDAQ_Timer::getChannelName(ChannelType type, int i)
{
	if (type == ChannelType::AnalogueOut && i >= 0 && i < AOPhysicalChannels.size())
		return AOPhysicalChannels[i].c_str();
	if (type == ChannelType::AnalogueIn && i >= 0 && i < AIPhysicalChannels.size())
		return AIPhysicalChannels[i].c_str();
	if (type == ChannelType::DigitalOut && i >= 0 && i < DOPhysicalChannels.size())
		return DOPhysicalChannels[i].c_str();
	if (type == ChannelType::DigitalIn && i >= 0 && i < DIPhysicalChannels.size())
		return DIPhysicalChannels[i].c_str();

	return nullptr;
}


int NIDAQ_Timer::getNChannelsInUse(ChannelType type) 
{
	if (type == ChannelType::AnalogueOut)
		return AOTask->channelsInUse();
	if (type == ChannelType::AnalogueIn)
		return AITask->channelsInUse();
	if (type == ChannelType::DigitalOut)
		return DOTask->channelsInUse();
	if (type == ChannelType::DigitalIn)
		return DITask->channelsInUse();
}

const char* NIDAQ_Timer::getChannelInUseName(ChannelType type, int i) 
{
	if (type == ChannelType::AnalogueOut)
		return AOTask->channelInUseName(i).c_str();
	if (type == ChannelType::AnalogueIn)
		return AITask->channelInUseName(i).c_str();
	if (type == ChannelType::DigitalOut)
		return DOTask->channelInUseName(i).c_str();
	if (type == ChannelType::DigitalIn)
		return DITask->channelInUseName(i).c_str();
}

STRIMM::Result NIDAQ_Timer::addAnalogueInput(const char* channelName, STRIMM_AIDesc desc) 
{
	if (!AITask.get())
		AITask = std::make_unique<NIDAQTask<ChannelType::AnalogueIn>>(getRate<ChannelType::AnalogueIn>(),
			getBlockSize<ChannelType::AnalogueIn>(), [this](const std::string& msg) { this->abort(msg); });

	if (auto[success, error] = AITask->addChannel(channelName, desc); !success)
	{
		setErrorMessage(error.c_str());
		return STRIMM::Result::Error;
	}

	ptrdiff_t pos = std::distance(AIPhysicalChannels.begin(), std::find(AIPhysicalChannels.begin(), AIPhysicalChannels.end(), channelName));
	AIAvailableChannelIndexes.remove(pos);

	return STRIMM::Result::Success;
}

STRIMM::Result NIDAQ_Timer::addDigitalInput(const char* channelName, STRIMM_DIDesc desc) 
{
	if (!DITask.get())
		DITask = std::make_unique<NIDAQTask<ChannelType::DigitalIn>>(getRate<ChannelType::DigitalIn>(),
			getBlockSize<ChannelType::DigitalIn>(), [this](const std::string& msg) { this->abort(msg); });

	if (auto[success, error] = DITask->addChannel(channelName, desc); !success)
	{
		setErrorMessage(error.c_str());
		return STRIMM::Result::Error;
	}

	ptrdiff_t pos = std::distance(DIPhysicalChannels.begin(), std::find(DIPhysicalChannels.begin(), DIPhysicalChannels.end(), channelName));
	DIAvailableChannelIndexes.remove(pos);

	return STRIMM::Result::Success;
}

STRIMM::Result NIDAQ_Timer::addAnalogueOutput(const char* channelName, STRIMM_AODesc desc) 
{
	if (!AOTask.get())
		AOTask = std::make_unique<NIDAQTask<ChannelType::AnalogueOut>>(getRate<ChannelType::AnalogueOut>(), 
			getBlockSize<ChannelType::AnalogueOut>(), [this](const std::string& msg) { this->abort(msg); });

	if (auto[success, error] = AOTask->addChannel(channelName, desc); !success)
	{
		setErrorMessage(error.c_str());
		return STRIMM::Result::Error;
	}

	ptrdiff_t pos = std::distance(AOPhysicalChannels.begin(), std::find(AOPhysicalChannels.begin(), AOPhysicalChannels.end(), channelName));
	AOAvailableChannelIndexes.remove(pos);

	return STRIMM::Result::Success;
}

STRIMM::Result NIDAQ_Timer::addDigitalOutput(const char* channelName, STRIMM_DODesc desc) 
{
	if (!DOTask.get())
		DOTask = std::make_unique<NIDAQTask<ChannelType::DigitalOut>>(getRate<ChannelType::DigitalOut>(),
			getBlockSize<ChannelType::DigitalOut>(), [this](const std::string& msg) { this->abort(msg); });

	if (auto[success, error] = DOTask->addChannel(channelName, desc); !success)
	{
		setErrorMessage(error.c_str());
		return STRIMM::Result::Error;
	}

	ptrdiff_t pos = std::distance(DOPhysicalChannels.begin(), std::find(DOPhysicalChannels.begin(), DOPhysicalChannels.end(), channelName));
	DOAvailableChannelIndexes.remove(pos);
	
	return STRIMM::Result::Success;
}


STRIMM::Result NIDAQ_Timer::removeAnalogueInput(const char* channelName)
{
	if (!AITask)
	{
		setErrorMessage("Could not remove analogue input channel! No analogue input channels exist!");
		return STRIMM::Result::Error;
	}

	if (auto[success, error] = AITask->removeChannel(channelName); !success)
	{
		setErrorMessage(error.c_str());
		return STRIMM::Result::Error;
	}

	return STRIMM::Result::Success;
}

STRIMM::Result NIDAQ_Timer::removeDigitalInput(const char* channelName)
{
	if (!DITask)
	{
		setErrorMessage("Could not remove digital input channel! No digital input channels exist!");
		return STRIMM::Result::Error;
	}

	if (auto[success, error] = DITask->removeChannel(channelName); !success)
	{
		setErrorMessage(error.c_str());
		return STRIMM::Result::Error;
	}

	return STRIMM::Result::Success;
}

STRIMM::Result NIDAQ_Timer::removeAnalogueOutput(const char* channelName)
{
	if (!AOTask)
	{
		setErrorMessage("Could not remove analogue output channel! No analogue output channels exist!");
		return STRIMM::Result::Error;
	}

	if (auto[success, error] = AOTask->removeChannel(channelName); !success)
	{
		setErrorMessage(error.c_str());
		return STRIMM::Result::Error;
	}

	return STRIMM::Result::Success;
}

STRIMM::Result NIDAQ_Timer::removeDigitalOutput(const char* channelName)
{
	if (!DOTask)
	{
		setErrorMessage("Could not remove digital output channel! No digital output channels exist!");
		return STRIMM::Result::Error;
	}

	if (auto[success, error] = DOTask->removeChannel(channelName); !success)
	{
		setErrorMessage(error.c_str());
		return STRIMM::Result::Error;
	}

	return STRIMM::Result::Success;
}

void NIDAQ_Timer::registerStopCallback(STRIMM_StopCallback callback, void * info)
{
	stopCallback = callback;
	stopCallbackInfo = info;
}

void NIDAQ_Timer::registerErrorCallback(STRIMM_ErrorCallback callback, void * info)
{
	errorCallback = callback;
	errorCallbackInfo = info;
}


STRIMM::Result NIDAQ_Timer::prime()
{
	std::array<std::string, 4> terms =
	{
		"/" + deviceName + "/ai/StartTrigger",
		"/" + deviceName + "/do/StartTrigger",
		"/" + deviceName + "/ao/StartTrigger",
		"/" + deviceName + "/di/StartTrigger"
	};

	int idx = -1;
	if (AITask.get()) idx = 0;
	else if (DOTask.get()) idx = 1;
	else if (AOTask.get()) idx = 2;
	else if (DITask.get()) idx = 3;

	if (idx == 0) { CheckTaskResult(AITask->prime()) }

	if (idx == 1) { CheckTaskResult(DOTask->prime()) }
	else if (DOTask.get()) { CheckTaskResult(DOTask->prime(terms[idx])) }

	if (idx == 2) { CheckTaskResult(AOTask->prime()) }
	else if (AOTask.get()) { CheckTaskResult(AOTask->prime(terms[idx])) }

	if (idx == 3) { CheckTaskResult(DITask->prime()) }
	else if (DITask.get()) { CheckTaskResult(DITask->prime(terms[idx])) }

	primed = true;
	return STRIMM::Result::Success;
}

STRIMM::Result NIDAQ_Timer::start() 
{
	if (!primed)
	{
		setErrorMessage("Timer has not been primed!");
		return STRIMM::Result::Error;
	}

	if (AITask.get()) AITask->start();
	else if (DOTask.get()) DOTask->start();
	else if (AOTask.get()) AOTask->start();
	else if (DITask.get()) DITask->start();

	return STRIMM::Result::Success;
}
STRIMM::Result NIDAQ_Timer::stop() 
{
	if (!running)
		return STRIMM::Result::Success;

	if (AITask.get()) AITask->stop();
	if (DOTask.get()) DOTask->stop();
	if (AOTask.get()) AOTask->stop();
	if (DITask.get()) DITask->stop();

	if (stopCallback)
		stopCallback(stopCallbackInfo);
	
	running = false;
	primed = false;
	return STRIMM::Result::Success;
}

void NIDAQ_Timer::abort(const std::string& msg)
{
	setErrorMessage(msg.c_str());

	if (errorCallback)
		errorCallback(errorCallbackInfo);

	stop();
}


bool NIDAQ_Timer::CheckNIDAQError(int32 error, std::function<void()> cleanup)
{
	if (DAQmxFailed(error))
	{
		std::vector<char> v;
		int32 buffSize = DAQmxGetExtendedErrorInfo(nullptr, 0);
		v.resize(buffSize);
		DAQmxGetExtendedErrorInfo(v.data(), buffSize);

		setErrorMessage(v.data());
		cleanup();

		return false;
	}
	return true;
}


TaskHandle NIDAQ_Timer::setupChannel(const char* channelName, std::vector<TaskHandle>& taskHandles, double rate, int blocksize, std::function<int32(std::string, TaskHandle)> assignChannelToTask)
{
	std::string channel = channelName;
	TaskHandle handle = nullptr;

	if (std::find(usedChannels.begin(), usedChannels.end(), channel) != usedChannels.end())
	{
		setErrorMessage((channel + " is already in use!").c_str());
		return nullptr;
	}

	if (!CheckNIDAQError( DAQmxCreateTask("", &handle) ))
		return nullptr;

	if (!CheckNIDAQError(assignChannelToTask(channel, handle)))
	{
		DAQmxClearTask(handle);
		return nullptr;
	}

	if (!CheckNIDAQError(DAQmxCfgSampClkTiming(handle, "", rate, DAQmx_Val_Rising, DAQmx_Val_ContSamps, blocksize)))
	{
		DAQmxClearTask(handle);
		return nullptr;
	}

	taskHandles.push_back(handle);
	usedChannels.push_back(channel);
	return handle;
}

bool NIDAQ_Timer::GetNIDAQStrings(int32(*nameFunc)(const char *, char *, uInt32), std::vector<std::string>& out)
{
	int32 nChars;
	if (nChars = nameFunc(deviceName.c_str(), nullptr, 0); !CheckNIDAQError(nChars)) return false;
	
	auto vec = std::vector<char>(nChars);
	if (!CheckNIDAQError(nameFunc(deviceName.c_str(), vec.data(), nChars))) return false;

	char* pch = strtok(vec.data(), ",");
	while (pch)
	{
		std::string name = pch;
		name.erase(name.begin(), std::find_if(name.begin(), name.end(), [](int ch) {return !std::isspace(ch); }));

		out.push_back(name);
		pch = strtok(nullptr, ",");
	}

	return true;
}