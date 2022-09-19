#include <iostream>
#include <array>
#include <cmath>
#include <queue>
#include <fstream>
#include <mutex>
#include <ratio>

#include <STRIMM_CRuntime\STRIMM_CRuntime.h>

constexpr double pi = 3.14159265358979323846;

struct PrintInfo
{
	std::ofstream* file;
	bool analogue;
	int num;
};

double*		allocateBuffer(int blockSize, void*);
uint8_t*	allocateDigitalBuffer(int blockSize, void*);
void		printToFile(void*);
void		stopCallback(void* info);
void		errorCallback(void* info);

template<int nSamps>
std::array<double, nSamps> getSinWave(double freq, double amplitude, double phase)
{
	std::array<double, nSamps> arr;
	for (int i = 0; i < arr.size(); i++)
		arr[i] = amplitude * std::sin(2*pi*i*freq + phase);
	return std::move(arr);
}

template<int nSamps, int T, class markSpaceRatio>
std::array<uint8_t, nSamps> getSquareWave()
{
	constexpr int nHigh = std::ratio_multiply<std::ratio<markSpaceRatio::num, markSpaceRatio::num + markSpaceRatio::den>, std::ratio<T, 1>>::num;

	std::array<uint8_t, nSamps> arr;
	for (int i = 0, n = 0; i < arr.size(); i++, n = i % T)
	{
		arr[i] = (n < nHigh) ? 1 : 0;
	}
	return std::move(arr);
}

bool stopped = false;

int main() 
{
	auto runtime = std::make_unique<STRIMM::CRuntime>();

	std::cout << "****** TEST LOAD DEVICES *******" << std::endl;
	runtime->addInstallDirectory("..\\x64\\Debug");
	auto devices = runtime->GetInstalledDevices();

	if (devices.empty())
	{
		std::cout << "No DLLs found" << std::endl;
		getchar();
		std::exit(EXIT_FAILURE);
	}

	for (auto dev : devices)
		std::cout << dev << std::endl;

	const std::string devLabel = "testDevice";
	const std::string propLabel = "Device Name";

	if (runtime->loadTimer(devices[0], devLabel) == STRIMM::Result::Success)
	{
		std::cout << "Load succeeded!" << std::endl;
	}
	else
	{
		std::cout << "Load failed!" << std::endl;
		std::exit(EXIT_FAILURE);
	}
	
	auto timer = runtime->getDevicePointer(devLabel);
	if (timer == nullptr)
	{
		std::cout << "Timer : " << devLabel << " not loaded" << std::endl;
		getchar();
		std::exit(EXIT_FAILURE);
	}

	std::cout << std::endl << "****** TEST GET PARAM *******" << std::endl;
	auto res = timer->getProperty<STRIMM::PropertyCollection::StringPropertyType>(propLabel);


	if (res.get() == nullptr)
	{
		std::cout << "Failed to get parameter : " << propLabel << std::endl;
		getchar();
		std::exit(EXIT_FAILURE);
	}

	std::cout << "Original Value: " << res->getValue() << std::endl;
	
	std::cout << std::endl << "****** TEST SET PARAM *******" << std::endl;
	res->setValue("Not Selected");

	std::cout << "New Value : " << res->getValue() << std::endl;


	std::cout << std::endl << "****** TEST GET ALLOWED VALUES *******" << std::endl;

	auto allowedValues = res->getAllowedValues();
	for (auto str : allowedValues)
		std::cout << str << std::endl;

	std::cout << std::endl << "****** TEST GET PROPERTY NAMES *******" << std::endl;
	auto propNames = timer->getPropertyNames();

	std::cout << std::boolalpha;
	for (auto name : propNames)
	{
		std::cout << name << " : ";

		auto prop = timer->getProperty<STRIMM::PropertyCollection::UnknownPropertyType>(name);
		
		if (prop.second == STRIMM::PropertyCollection::IntPropertyType)
			std::cout << dynamic_cast<STRIMM::CRuntime::IntegerProperty*>(prop.first.get())->getValue();
		else if (prop.second == STRIMM::PropertyCollection::FloatPropertyType)
			std::cout << dynamic_cast<STRIMM::CRuntime::FloatProperty*>(prop.first.get())->getValue();
		else if (prop.second == STRIMM::PropertyCollection::DoublePropertyType)
			std::cout << dynamic_cast<STRIMM::CRuntime::DoubleProperty*>(prop.first.get())->getValue();
		else if (prop.second == STRIMM::PropertyCollection::StringPropertyType)
			std::cout << dynamic_cast<STRIMM::CRuntime::StringProperty*>(prop.first.get())->getValue();
		else if (prop.second == STRIMM::PropertyCollection::BooleanPropertyType)
			std::cout << dynamic_cast<STRIMM::CRuntime::BooleanProperty*>(prop.first.get())->getValue();

		std::cout << std::endl;
	}


	std::cout << std::endl << "****** TEST MAX AND MIN *******" << std::endl;
	auto prop = timer->getProperty<STRIMM::PropertyCollection::IntPropertyType>("Test Int");
	prop->setMax(100);
	prop->setMin(10);

	std::cout << "MAX: " << prop->getMax() << std::endl;
	std::cout << "MIN: " << prop->getMin() << std::endl;

	int x[3] = { 101, 9, 50 };

	for (int i = 0; i < 3; i++)
	{
		prop->setValue(x[i]);
		std::cout << "SET :" << x[i] << "  \tGET : " << prop->getValue() << std::endl;
	}


	std::cout << std::endl << std::endl << "Available Devices: " << std::endl;
	for (int i = 0; i < allowedValues.size(); i++)
		std::cout << i << ". " << allowedValues[i] << std::endl;

	int indx = -1;
	while (true)
	{
		std::cout << "Select Device: ";
		std::string tmp;
		std::cin >> tmp;
		
		try { indx = std::stoi(tmp); }
		catch (const std::exception& ex) { continue; }

		if (indx >= 0 && indx < allowedValues.size())
			break;

		std::cin.clear();
		std::cin.ignore(std::numeric_limits<std::streamsize>::max(), '\n');
	}

	// Removed... replaced with GetAvailableChannelNames
	std::cout << std::endl << "****** TEST GET # CHANNELS *******" << std::endl;
	res->setValue(allowedValues[indx]);
	if (timer->initialise() != STRIMM::Result::Success)
	{
		std::cout << timer->getLastError() << std::endl;
		getchar();
		std::exit(EXIT_FAILURE);
	}
	/*
	std::cout << "# Analogue Inputs  : " << timer->getNAvailableChannels(STRIMM::AbstractTimer::AnalogueIn) << std::endl;
	for (auto name : timer->getChannelNames(STRIMM::AbstractTimer::AnalogueIn))
		std::cout << "\t" << name << std::endl;
	std::cout << "# Analogue Outputs : " << timer->getNAvailableChannels(STRIMM::AbstractTimer::AnalogueOut) << std::endl;
	for (auto name : timer->getChannelNames(STRIMM::AbstractTimer::AnalogueOut))
		std::cout << "\t" << name << std::endl;
	std::cout << "# Digital  Inputs  : " << timer->getNAvailableChannels(STRIMM::AbstractTimer::DigitalIn) << std::endl;
	for (auto name : timer->getChannelNames(STRIMM::AbstractTimer::DigitalIn))
		std::cout << "\t" << name << std::endl;
	std::cout << "# Digital  Outputs : " << timer->getNAvailableChannels(STRIMM::AbstractTimer::DigitalOut) << std::endl;
	for (auto name : timer->getChannelNames(STRIMM::AbstractTimer::DigitalOut))
		std::cout << "\t" << name << std::endl;*/


	auto analogue_data_out = getSinWave<1000>(1.0/1000.0, 3.0, 0.0);
	auto digital_data_out = getSquareWave<1000, 100, std::ratio<2, 8>>();

	auto analogue_data_out2 = getSinWave<100>(1.0 / 100.0, 3.0, pi / 2);
	auto digital_data_out2 = std::array<uint8_t, 1000>();
	for (int i = 0; i < digital_data_out.size(); i++)
		digital_data_out2[i] = digital_data_out[i] ^ 1;

	STRIMM_AIDesc AIDesc	{};			STRIMM_AODesc AODesc	{};								STRIMM_DIDesc DIDesc {};		STRIMM_DODesc DODesc	{};
	AIDesc.voltageMax		= 10;		AODesc.blockSize		= 1000;							;								DODesc.blockSize		= 1000;
	AIDesc.voltageMin		= -10;		AODesc.sampleArray		= analogue_data_out.data();		;								DODesc.sampleArray		= digital_data_out.data();
	AIDesc.clockDiv			= 1;		AODesc.clockDiv			= 1;							DIDesc.clockDiv = 1;			DODesc.clockDiv			= 1;

	STRIMM_AODesc AODesc2{};
	AODesc2.blockSize = 1000;
	AODesc2.sampleArray = analogue_data_out2.data();
	AODesc2.clockDiv = 10;

	STRIMM_AIDesc AIDesc2{};
	AIDesc2.voltageMax = 10;
	AIDesc2.voltageMin = -10;
	AIDesc2.clockDiv = 10;

	STRIMM_DODesc DODesc2{};
	DODesc2.blockSize = 1000;
	DODesc2.sampleArray = digital_data_out2.data();
	DODesc2.clockDiv = 1;

	STRIMM_DIDesc DIDesc2{};
	DIDesc2.clockDiv = 1;

	auto file = new std::ofstream("out/OUT.csv");

	(*file) << "A Out 0,";
	for (auto d : analogue_data_out)
		(*file) << d << ",";
	(*file) << std::endl;

	(*file) << "A Out 1,";
	for (auto d : analogue_data_out2)
		(*file) << d << ",";
	(*file) << std::endl;

	(*file) << "D Out 0,";
	for (auto d : digital_data_out)
		(*file) << (int)d << ",";
	(*file) << std::endl;

	(*file) << "D Out 1,";
	for (auto d : digital_data_out2)
		(*file) << (int)d << ",";
	(*file) << std::endl << std::endl;

	PrintInfo api = { file, true, 0};
	PrintInfo dpi = { file, false, 0 };
	PrintInfo api2 = { file, true, 1};
	PrintInfo dpi2 = { file, false, 1 };

	AIDesc.bufferCallback = &allocateBuffer;
	AIDesc.dataCopiedCallback = &printToFile;
	AIDesc.bufferCallbackInfo = (void*)file;
	AIDesc.dataCopiedCallbackInfo = (void*)&api;

	AIDesc2.bufferCallback = &allocateBuffer;
	AIDesc2.dataCopiedCallback = &printToFile;
	AIDesc2.bufferCallbackInfo = (void*)file;
	AIDesc2.dataCopiedCallbackInfo = (void*)&api2;

	DIDesc.bufferCallback = &allocateDigitalBuffer;
	DIDesc.dataCopiedCallback = &printToFile;
	DIDesc.bufferCallbackInfo = (void*)file;
	DIDesc.dataCopiedCallbackInfo = (void*)&dpi;

	DIDesc2.bufferCallback = &allocateDigitalBuffer;
	DIDesc2.dataCopiedCallback = &printToFile;
	DIDesc2.bufferCallbackInfo = (void*)file;
	DIDesc2.dataCopiedCallbackInfo = (void*)&dpi2;


	timer->registerStopCallback(&stopCallback, (void*)file);
	timer->registerErrorCallback(&errorCallback, (void*)timer);

	file = nullptr; //throw away our reference to the file so as not to be tempted to delete it!

	std::cout << std::endl << "****** TEST ADD AI CHANNEL (2) *******" << std::endl;
	if (timer->addAnalogueInput((allowedValues[indx] + "/ai1").c_str(), AIDesc2) == STRIMM::Result::Success)
	{
		std::cout << "No Errors!" << std::endl;
	}
	else
	{
		std::cout << timer->getLastError() << std::endl;
	PrintInfo api = { file, true, 0};
	PrintInfo dpi = { file, false, 0 };
		getchar();
		std::exit(EXIT_FAILURE);
	}	



	std::cout << std::endl << "****** TEST ADD AI CHANNEL *******" << std::endl;
	if (timer->addAnalogueInput((allowedValues[indx] + "/ai0").c_str(), AIDesc) == STRIMM::Result::Success)
	{
		std::cout << "No Errors!" << std::endl;
	}
	else
	{
		std::cout << timer->getLastError() << std::endl;
		getchar();
		std::exit(EXIT_FAILURE);
	}

	std::cout << std::endl << "****** TEST ADD AO CHANNEL *******" << std::endl;
	if (timer->addAnalogueOutput((allowedValues[indx] + "/ao0").c_str(), AODesc) == STRIMM::Result::Success)
	{
		std::cout << "No Errors!" << std::endl;
	}
	else
	{
		std::cout << timer->getLastError() << std::endl;
		getchar();
		std::exit(EXIT_FAILURE);
	}

	std::cout << std::endl << "****** TEST ADD AO (2) CHANNEL *******" << std::endl;
	if (timer->addAnalogueOutput((allowedValues[indx] + "/ao1").c_str(), AODesc2) == STRIMM::Result::Success)
	{
		std::cout << "No Errors!" << std::endl;
	}
	else
	{
		std::cout << timer->getLastError() << std::endl;
		getchar();
		std::exit(EXIT_FAILURE);
	}

	std::cout << std::endl << "****** TEST ADD DI CHANNEL *******" << std::endl;
	if (timer->addDigitalInput((allowedValues[indx] + "/port0/line0").c_str(), DIDesc) == STRIMM::Result::Success)
	{
		std::cout << "No Errors!" << std::endl;
	}
	else
	{
		std::cout << timer->getLastError() << std::endl;
		getchar();
		std::exit(EXIT_FAILURE);
	}

	std::cout << std::endl << "****** TEST ADD DI CHANNEL (2) *******" << std::endl;
	if (timer->addDigitalInput((allowedValues[indx] + "/port0/line2").c_str(), DIDesc2) == STRIMM::Result::Success)
	{
		std::cout << "No Errors!" << std::endl;
	}
	else
	{
		std::cout << timer->getLastError() << std::endl;
		getchar();
		std::exit(EXIT_FAILURE);
	}

	std::cout << std::endl << "****** TEST ADD DO CHANNEL *******" << std::endl;
	if (timer->addDigitalOutput((allowedValues[indx] + "/port0/line7").c_str(), DODesc) == STRIMM::Result::Success)
	{
		std::cout << "No Errors!" << std::endl;
	}
	else
	{
		std::cout << timer->getLastError() << std::endl;
		getchar();
		std::exit(EXIT_FAILURE);
	}

	std::cout << std::endl << "****** TEST ADD DO CHANNEL (2) *******" << std::endl;
	if (timer->addDigitalOutput((allowedValues[indx] + "/port0/line3").c_str(), DODesc2) == STRIMM::Result::Success)
	{
		std::cout << "No Errors!" << std::endl;
	}
	else
	{
		std::cout << timer->getLastError() << std::endl;
		getchar();
		std::exit(EXIT_FAILURE);
	}

	std::cout << std::endl << "****** TEST PRIME CHANNELS *******" << std::endl;
	if (timer->prime() != STRIMM::Result::Success)
	{
		std::cout << timer->getLastError() << std::endl;
		getchar();
		std::exit(EXIT_FAILURE);
	}
	else std::cout << "No Errors!" << std::endl;

	std::cout << std::endl << "****** TEST RUN ACQUISITION *******" << std::endl;
	if (timer->start() != STRIMM::Result::Success)
	{
		std::cout << timer->getLastError() << std::endl;
		getchar();
		std::exit(EXIT_FAILURE);
	}
	else std::cout << "Starting acquisition! Press enter to stop" << std::endl;

	getchar();

	if (timer->stop() != STRIMM::Result::Success)
	{
		std::cout << timer->getLastError() << std::endl;
		getchar();
		std::exit(EXIT_FAILURE);
	}

	getchar();
	return 0;
}

std::queue<std::vector<double>> analogueBufferQueue;
std::queue<std::vector<uint8_t>> digitalBufferQueue;

double * allocateBuffer(int blockSize, void*)
{
	auto buffer = std::vector<double>(blockSize);
	analogueBufferQueue.push(std::move(buffer));
	return analogueBufferQueue.back().data();
}

uint8_t * allocateDigitalBuffer(int blockSize, void *)
{
	auto buffer = std::vector<uint8_t>(blockSize);
	digitalBufferQueue.push(std::move(buffer));
	return digitalBufferQueue.back().data();
}

std::mutex g_file;

void  printToFile(void* callbackInfo)
{
	std::lock_guard<std::mutex> lock(g_file);

	auto info = (PrintInfo*)callbackInfo;

	if (info->analogue)
	{
		auto data = std::move(analogueBufferQueue.front());
		analogueBufferQueue.pop();

		(*info->file) << "A In " << info->num << ",";
		for (auto d : data)
			(*info->file) << d << ",";
		(*info->file) << std::endl;
	}
	else
	{
		auto data = std::move(digitalBufferQueue.front());
		digitalBufferQueue.pop();

		(*info->file) << "D In " << info->num << ",";
		for (auto d : data)
			(*info->file) << (int)d << ",";
		(*info->file) << std::endl;
	}

}

void stopCallback(void* info)
{
	std::lock_guard<std::mutex> lock(g_file);

	auto file = (std::ofstream*)info;
	file->close();
	delete file;
	file = nullptr;
}

void errorCallback(void * info)
{
	auto timer = (STRIMM::CRuntime::TimerDevice*)info;
	std::cout << timer->getLastError() << std::endl << "Aborting acquisition!" << std::endl;
}
