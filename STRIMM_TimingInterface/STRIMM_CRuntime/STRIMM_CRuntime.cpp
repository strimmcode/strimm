#include "STRIMM_CRuntime.h"

#include <filesystem>
#include <iostream>

namespace STRIMM
{
	::std::vector<::std::string> CRuntime::GetInstalledDevices()
	{
		deviceLocations = ::std::unordered_map<::std::string, ::std::string>();
		auto devices = ::std::vector<::std::string>();

		for (auto &dir : installDirectories)
		{
			for (auto &p : fs::directory_iterator(dir))
			{
				auto filename = p.path().filename();
				if (filename.has_extension() && filename.extension().string() == DYNAMIC_LIB_EXT)
					loadDynamicLibrary(p.path().string(), &devices);
			}
		}

		return devices;
	}

	void CRuntime::addInstallDirectory(const ::std::string & directory)
	{
		installDirectories.push_back(directory);
	}

	Result CRuntime::loadTimer(const ::std::string & deviceName, const ::std::string& deviceLabel)
	{
		if (auto it = deviceLocations.find(deviceName); it != deviceLocations.end())
		{
			loadedDevices.emplace(::std::piecewise_construct,
				::std::forward_as_tuple(deviceLabel),
				::std::forward_as_tuple(it->second));

			if (auto it = loadedDevices.find(deviceLabel); !it->second.isInstanceValid())
			{
				loadedDevices.erase(it);
				return Result::Success;
			}

			return Result::Success;
		}
		return Result::Error;
	}

	void CRuntime::loadDynamicLibrary(const ::std::string& filename, ::std::vector<::std::string>* devices)
	{
		::std::cout << "Loading " << filename << ::std::endl;
		LibType libHandle = openLib(filename.c_str());

		if (!libHandle)
		{
			::std::cout << "Failed to load " << filename << ::std::endl;
			return;
		}

		auto funcs = getLibFuncs(libHandle, true);

		if (!funcs.isValid())
		{
			closeLib(libHandle);
			return;
		}

		if (int currentVersion = GetAPIVersion(), libraryVersion = funcs.GetAPIVersion(); currentVersion != libraryVersion)
		{
			::std::cout << filename << " linked against the wrong version of the STRIMM C Runtime! \n\tCurrent runtime version: "
				<< currentVersion << "\n\tLibrary runtime version: " << libraryVersion << ::std::endl;

			closeLib(libHandle);
			return;
		}

		::std::string timerName = ::std::string(funcs.GetTimerName());

		::std::cout << "Found timer: " << timerName << ::std::endl;

		deviceLocations[timerName] = filename;
		devices->push_back(timerName);

		closeLib(libHandle);
	}

	CRuntime::TimerDevice::TimerDevice(const ::std::string & libName)
	{
		libraryName = libName;

		libraryHandle = openLib(libName.c_str());
		if (!libraryHandle)
		{
			funcs = {};
			instance = nullptr;
		}

		funcs = getLibFuncs(libraryHandle, false);
		if (!funcs.isValid())
		{
			closeLib(libraryHandle);
			libraryHandle = nullptr;
			funcs = {};
			instance = nullptr;
		}

		instance = funcs.CreateTimer();
		if (!instance)
		{
			closeLib(libraryHandle);
			libraryHandle = nullptr;
			funcs = {};
			instance = nullptr;
		}
	}

	CRuntime::TimerDevice::~TimerDevice()
	{
		if (funcs.DeleteTimer && instance)
			funcs.DeleteTimer(instance);
		instance = nullptr;

		funcs = {};

		if (libraryHandle)
		{
			closeLib(libraryHandle);
			libraryHandle = nullptr;
		}
	}

	::std::string CRuntime::TimerDevice::getTimerName() { return ::std::string(funcs.GetTimerName()); }
	int CRuntime::TimerDevice::getAPIVersion() { return funcs.GetAPIVersion(); }
	::std::string CRuntime::TimerDevice::getLastError() { return funcs.GetTimerError(instance); }
	Result CRuntime::TimerDevice::initialise() { return funcs.Initialise(instance); }
	std::vector<std::string> CRuntime::TimerDevice::getAvailableChannelNames(AbstractTimer::ChannelType type)
	{
		auto out = std::vector<std::string>();
		for (int i = 0; i < funcs.GetNAvailableChannels(instance, type); i++)
			if (auto name = funcs.GetAvailableChannelName(instance, type, i); name)
				out.emplace_back(name);

		return out;
	}
	
	std::vector<std::string> CRuntime::TimerDevice::getChannelNames(AbstractTimer::ChannelType type) 
	{ 
		auto out = std::vector<std::string>();
		
		for (int i = 0; i < funcs.GetNChannels(instance, type); i++)
		{
			if (auto name = funcs.GetChannelName(instance, type, i); name)
				out.emplace_back(name);
		}

		return out;
	}

	std::vector<std::string> CRuntime::TimerDevice::getChannelsInUse(AbstractTimer::ChannelType type)
	{
		auto out = std::vector<std::string>();

		for (int i = 0; i < funcs.GetNChannelsInUse(instance, type); i++)
		{
			if (auto name = funcs.GetChannelInUseName(instance, type, i); name)
				out.emplace_back(name);
		}

		return out;
	}

	Result CRuntime::TimerDevice::addAnalogueInput(const std::string& channelName, const STRIMM_AIDesc& desc) { return funcs.AddAnalogueInput(instance, channelName.c_str(), desc); }
	Result CRuntime::TimerDevice::addDigitalInput(const std::string& channelName, const STRIMM_DIDesc& desc) { return funcs.AddDigitalInput(instance, channelName.c_str(), desc); }
	Result CRuntime::TimerDevice::addAnalogueOutput(const std::string& channelName, const STRIMM_AODesc& desc) { return funcs.AddAnalogueOutput(instance, channelName.c_str(), desc); }
	Result CRuntime::TimerDevice::addDigitalOutput(const std::string& channelName, const STRIMM_DODesc& desc) { return funcs.AddDigitalOutput(instance, channelName.c_str(), desc); }

	Result CRuntime::TimerDevice::removeAnalogueInput(const std::string& channelName) { return funcs.RemoveAnalogueInput(instance, channelName.c_str()); }
	Result CRuntime::TimerDevice::removeDigitalInput(const std::string& channelName) { return funcs.RemoveDigitalInput(instance, channelName.c_str()); }
	Result CRuntime::TimerDevice::removeAnalogueOutput(const std::string& channelName) { return funcs.RemoveAnalogueOutput(instance, channelName.c_str()); }
	Result CRuntime::TimerDevice::removeDigitalOutput(const std::string& channelName) { return funcs.RemoveDigitalOutput(instance, channelName.c_str()); }

	void CRuntime::TimerDevice::registerStopCallback(STRIMM_StopCallback callback, void * callbackInfo) { return funcs.RegisterStopCallback(instance, callback, callbackInfo); }
	void CRuntime::TimerDevice::registerErrorCallback(STRIMM_ErrorCallback callback, void * callbackInfo) { return funcs.RegisterErrorCallback(instance, callback, callbackInfo); }

	Result CRuntime::TimerDevice::prime() { return funcs.Prime(instance); }
	Result CRuntime::TimerDevice::start() { return funcs.Start(instance); }
	Result CRuntime::TimerDevice::stop() { return funcs.Stop(instance); }

	::std::vector<::std::string> CRuntime::TimerDevice::getPropertyNames()
	{
		int nProperties = funcs.GetNProperties(instance);
		auto out = ::std::vector<::std::string>();
		for (int i = 0; i < nProperties; i++)
		{
			if (const char * str = funcs.GetPropertyName(instance, i); str != nullptr)
				out.push_back(::std::string(str));
		}
		return out;
	}


	

	bool CRuntime::TimerDevice::isInstanceValid()
	{
		return instance;
	}

	

	CRuntime::TimerDevice * CRuntime::getDevicePointer(const::std::string & deviceLabel)
	{
		if (auto devIt = loadedDevices.find(deviceLabel); devIt != loadedDevices.end())
			return &devIt->second;
		return nullptr;
	}
}