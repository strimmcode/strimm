#pragma once
#include "nidaqmx.h"
#include <string>
#include <sstream>
#include <iostream>
#include <fstream>
#include <vector>
#include <Windows.h>
using namespace std;

class SimpleDataProtocol
{
public:
	SimpleDataProtocol();
	~SimpleDataProtocol();


	bool	Init(int deviceID, double minV, double maxV);
	bool	Run(double* pDataAO, uInt32* pDataDO, int numSamples, double sampleFreq, int numAOChannels, int* AOChannels, int numDOChannels, int* DOChannels, int DOport);
	bool	Shutdown();


private:
	bool bError;
	bool bInRunMethod;
	int deviceID;
	double minV, maxV;
	TaskHandle AOtaskHandle, DOtaskHandle;
	bool bRunning;
	int numAOChannels;
	int numDOChannels;
};

