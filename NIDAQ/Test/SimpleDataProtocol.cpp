#include "SimpleDataProtocol.h"




SimpleDataProtocol::SimpleDataProtocol() {
	bError =  false;
	deviceID = 0;
	minV = -10.0;
	maxV = 10.0;
	
}

SimpleDataProtocol::~SimpleDataProtocol() {


}

bool	SimpleDataProtocol::Init(int deviceID, double minV, double maxV) {
	cout << "from c++ start init" << endl;
	this->deviceID = deviceID;
	this->minV = minV;
	this->maxV = maxV;
	bRunning = false;
	AOtaskHandle = DOtaskHandle =  NULL;	
	bError = false;

	return false;
}
bool	SimpleDataProtocol::Run(double* pDataAO, uInt32* pDataDO,  int numSamples, double sampleFreq, int numAOChannels, int* AOChannels, int numDOChannels, int* DOChannels, int DOPort) {// hyp: do is single, di is array
	//cout << "from c++ start run" << endl;
	this->numAOChannels = numAOChannels;
	this->numDOChannels = numDOChannels;
	
	bInRunMethod = true;
	int errorCode = 0;
	char buffer[2000] = { 0 };
	stringstream ssError;

	if (numDOChannels == 0 && numAOChannels == 0)  return false;

	ssError << "Dev" << deviceID << " NIDAQ error";

	if (numDOChannels > 0) errorCode = DAQmxCreateTask("", &DOtaskHandle);
	if (numAOChannels > 0) errorCode = DAQmxCreateTask("", &AOtaskHandle);

	////load channels

	for (int f = 0; f < numAOChannels; f++) {
		std::stringstream ss;
		ss << "Dev" << deviceID << "/ao" << AOChannels[f];
		errorCode = DAQmxCreateAOVoltageChan(AOtaskHandle, ss.str().c_str(), "", minV, maxV, DAQmx_Val_Volts, NULL);

	}
	for (int f = 0; f < numDOChannels; f++) {
		std::stringstream ssd;
		//cout << "create DO channel" << endl;
		ssd << "Dev" << deviceID << "/port" << DOPort << "/line" << DOChannels[f];
		errorCode = DAQmxCreateDOChan(DOtaskHandle, ssd.str().c_str(), "", DAQmx_Val_ChanPerLine);

	}


	////cfg clocks
	if (numAOChannels > 0) errorCode = DAQmxCfgSampClkTiming(AOtaskHandle, "", sampleFreq, DAQmx_Val_Rising, DAQmx_Val_FiniteSamps, numSamples);
	if (numDOChannels > 0) errorCode = DAQmxCfgSampClkTiming(DOtaskHandle, "", sampleFreq, DAQmx_Val_Rising, DAQmx_Val_FiniteSamps, numSamples);



	std::stringstream ss4;

	if (numAOChannels > 0 && numDOChannels > 0) {
		//ao/StartTrigger is the master trigger
		ss4 << "/Dev" << deviceID << "/ao/StartTrigger";
		errorCode = DAQmxCfgDigEdgeStartTrig(DOtaskHandle, ss4.str().c_str(), DAQmx_Val_Rising);
		errorCode = DAQmxWriteAnalogF64(AOtaskHandle, numSamples, 0, DAQmx_Val_WaitInfinitely, DAQmx_Val_GroupByScanNumber, pDataAO, NULL, NULL);
		errorCode = DAQmxWriteDigitalU32(DOtaskHandle, numSamples, 0, DAQmx_Val_WaitInfinitely, DAQmx_Val_GroupByScanNumber, pDataDO, NULL, NULL);
		errorCode = DAQmxStartTask(DOtaskHandle);
		errorCode = DAQmxStartTask(AOtaskHandle);
		DAQmxWaitUntilTaskDone(AOtaskHandle, DAQmx_Val_WaitInfinitely);
	}
	else if (numAOChannels > 0) {
		ss4 << "/Dev" << deviceID << "/ao/StartTrigger";
		errorCode = DAQmxWriteAnalogF64(AOtaskHandle, numSamples, 0, DAQmx_Val_WaitInfinitely, DAQmx_Val_GroupByScanNumber, pDataAO, NULL, NULL);
		errorCode = DAQmxStartTask(AOtaskHandle);
		DAQmxWaitUntilTaskDone(AOtaskHandle, DAQmx_Val_WaitInfinitely);
	}
	else {
		errorCode = DAQmxWriteDigitalU32(DOtaskHandle, numSamples, 0, DAQmx_Val_WaitInfinitely, DAQmx_Val_GroupByScanNumber, pDataDO, NULL, NULL);
		errorCode = DAQmxStartTask(DOtaskHandle);
		DAQmxWaitUntilTaskDone(DOtaskHandle, DAQmx_Val_WaitInfinitely);

	}

	bRunning = true;

	if (numAOChannels > 0) DAQmxStopTask(AOtaskHandle);
	if (numDOChannels > 0) DAQmxStopTask(DOtaskHandle);

	if (numDOChannels > 0) DAQmxClearTask(DOtaskHandle);
	if (numAOChannels > 0) DAQmxClearTask(AOtaskHandle);
	

	bInRunMethod = false;
	return true;
}
bool	SimpleDataProtocol::Shutdown() {

	bRunning = false;
	while (bInRunMethod) {
		Sleep(100);
	}



	return false;
}


