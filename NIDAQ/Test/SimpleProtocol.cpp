#include "SimpleProtocol.h"
#include "ProtocolBase.h"

/*
* DAQMX summary:
* For each subsystem AO, AI etc initially make tasks
* Next add channels
* Next add clocks to each subsystem. In order to synchronise these systems - the clocks are linked to a master subsystem
* Write the data to each channel.
* Attach a trigger if required to each master subsystem and then run the master subsystem.
* Write samples to the AO and DO output subsystems
* Read samples from the AI and DI subsystems.
* The Write function is asynchronous and the Read is asynchronous, for write only operations tell daqmx to wait until the operation 
* so ??? DONT I HAVE TO WAIT FOR THE WRITE TO COMPLETE AND ISNT THE READ SYNCHRONOUS - SO WE ARE WAITING FOR THE DATA
* THERE IS AN ASYNCH READ IN CONTSAMPS 
* is over
* Stop each task.
* Clear each task to reset the daqmx.



*/


void SimpleProtocol::con(int in, int x, int y)
{
	HDC dc = GetDC(NULL);
	char buff[100] = { 0 };
	_ltoa_s(in, buff, 10);
	TextOutA(dc, x, y, "            ", 7);
	TextOutA(dc, x, y, buff, (int)strlen(buff));
	ReleaseDC(NULL, dc);
}
void SimpleProtocol::con(string in, int x, int y)
{
	HDC dc = GetDC(NULL);
	TextOutA(dc, x, y, "            ", 7);
	TextOutA(dc, x, y, in.c_str(), (int)in.length());
	ReleaseDC(NULL, dc);
}
SimpleProtocol::SimpleProtocol(int deviceID, double minV, double maxV) {
	bContinuous = false;


	bRepeat = false;
	bFirst = true;

	this->deviceID = deviceID;
	this->minV = minV;
	this->maxV = maxV;

	AItaskHandle = AOtaskHandle = DOtaskHandle = DItaskHandle = NULL;
	dataAO = dataAO_back = NULL;
	dataDO = dataDO_back = NULL;
	sampleFreq = 1000.0;
	bError = false;
	numSamples = 0;
	bDirtyAO = false;
	bDirtyDO = false;
	DIPort = DOPort = 0;
	//windows high performance timer to estimate the time of each sample
	LARGE_INTEGER li;
	QueryPerformanceFrequency(&li);
	PCFreq = double(li.QuadPart) / 1000.0;
}
SimpleProtocol::~SimpleProtocol() {
	//release the non daqmx resources
	if (!dataAO) delete[] dataAO;
	if (!dataDO) delete[] dataDO;
	if (!dataAO_back) delete[] dataAO_back;
	if (!dataDO_back) delete[] dataDO_back;
}
bool	SimpleProtocol::Init(char* szCsv) {
	cout << "Initialising simple protocol" << endl;
	ProtocolBase pb;
	cout << "Reading protocol CSV" << endl;
	pb.ReadProtocol(szCsv);

	numAOChannels = pb.GetNumAOChannels();
	numAIChannels = pb.GetNumAIChannels();
	numDOChannels = pb.GetNumDOChannels();
	numDIChannels = pb.GetNumDIChannels();
	numSamples = pb.GetNumSamples();
	sampleFreq = pb.GetSampleFreq();
	DOPort = pb.GetDOPort();
	DIPort = pb.GetDIPort();
	AOChannels = pb.GetAOChannels();
	AIChannels = pb.GetAIChannels();
	DOChannels = pb.GetDOChannels();
	DIChannels = pb.GetDIChannels();

	cout << "Getting output data" << endl;
	vector<int> v_DO = pb.GetDOData();
	vector<double> v_AO = pb.GetAOData();
	
	//
	//to avoid AO and DO add a AI line
	if (numAOChannels > 0) {
		dataAO = new double[numSamples * numAOChannels];
		for (int f = 0; f < v_AO.size(); f++) {
			dataAO[f] = v_AO[f];
		}
	}

	if (numDOChannels > 0) {
		dataDO = new uInt32[numSamples * numDOChannels];
		for (int f = 0; f < v_DO.size(); f++) {
			dataDO[f] = v_DO[f];
		}
	}
	return true;
}
bool	SimpleProtocol::UpdateAOChannel(double* pData, int channel) {
	if (dataAO == NULL) return false;
	int channel_ix = 0;
	for (int f = 0; f < numAOChannels; f++) {
		if (AOChannels[f] == channel) {
			channel_ix = f;
			break;
		}
	}
	for (int f = 0; f < numSamples; f++) {
		dataAO_back[channel_ix + f * numAOChannels] = pData[f];
	}
	bDirtyAO = true;
	return true;
}
bool	SimpleProtocol::UpdateDOChannel(uInt32* pData, int line) {
	if (dataDO == NULL) return false;
	int line_ix = 0;
	for (int f = 0; f < numDOChannels; f++) {
		if (DOChannels[f] == line) {
			line_ix = f;
			break;
		}
	}
	//do I need to use 2^x etc
	for (int f = 0; f < numSamples; f++) {
		dataDO_back[line_ix + f * numDOChannels] = pData[f];
	}
	bDirtyDO = true;
	return true;
}

bool	SimpleProtocol::InitDAQ() {
	/*
	* Create tasks for each subsystem eg AO AI DO DI
	* Load channels for each task
	* Specify the clock for each subsystem
	* Lock subsystem clocks together  eg if using AO, AI, DO then lock AI, AO to the clock of AO, which means that the different systems are synchronised (not some earlier NIDAQs cannot do this).
	* Write the data to each channel eg AO or DO
	* ????DO WE NEED TO WAIT HERE - OR IS WRITE SYNCHRONOUSE
	* 
	* at this point the NIDAQ is ready to go and start issuing and reading samples.
	* 
	
	
	
	
	*/
	cout << "Simple protocol initialising DAQ" << endl;
	char buffer[2000] = { 0 };
	int errorCode = 0;

	stringstream ssError;
	ssError << "Dev" << deviceID << " NIDAQ error";


	bool bUsingAO = numAOChannels > 0;
	bool bUsingAI = numAIChannels > 0;
	bool bUsingDO = numDOChannels > 0;
	bool bUsingDI = numDIChannels > 0;


	if (bUsingDO) {
		//cout << "create DO task" << endl;
		errorCode = DAQmxCreateTask("", &DOtaskHandle);
		if (errorCode < 0) {
			DAQmxGetErrorString(errorCode, buffer, 2000);
			MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
			return false;
		}
	}
	if (bUsingDI) {
		//cout << "create DI task" << endl;
		errorCode = DAQmxCreateTask("", &DItaskHandle);
		if (errorCode < 0) {
			DAQmxGetErrorString(errorCode, buffer, 2000);
			MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
			return false;
		}
	}
	if (bUsingAO) {
		//MessageBoxA(NULL, "Create AO task", "", MB_OK);
		//con("deviceID:", 20, 20);
		//con(deviceID, 20, 40);
	
		errorCode = DAQmxCreateTask("", &AOtaskHandle);
		if (errorCode < 0) {
			DAQmxGetErrorString(errorCode, buffer, 2000);
			MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
			return false;
		}

	}
	if (bUsingAI) {
		errorCode = DAQmxCreateTask("", &AItaskHandle);
		if (errorCode < 0) {
			DAQmxGetErrorString(errorCode, buffer, 2000);
			MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
			return false;
		}
	}

	cout << "Simple protocol loading channels" << endl;
	////load channels
	for (int f = 0; f < numAIChannels; f++) {
		std::stringstream ss;
		ss << "Dev" << deviceID << "/ai" << AIChannels[f];
		errorCode = DAQmxCreateAIVoltageChan(AItaskHandle, ss.str().c_str(), "", DAQmx_Val_RSE, minV, maxV, DAQmx_Val_Volts, NULL);
		if (errorCode < 0) {
			DAQmxGetErrorString(errorCode, buffer, 2000);
			MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
			return false;
		}
	}

	for (int f = 0; f < numAOChannels; f++) {
		std::stringstream ss;
		ss << "Dev" << deviceID << "/ao" << AOChannels[f];
		//MessageBoxA(NULL, "add AO Channel", ss.str().c_str(), MB_OK);
		errorCode = DAQmxCreateAOVoltageChan(AOtaskHandle, ss.str().c_str(), "", minV, maxV, DAQmx_Val_Volts, NULL);
		if (errorCode < 0) {
			DAQmxGetErrorString(errorCode, buffer, 2000);
			MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
			return false;
		}
	}

	for (int f = 0; f < numDOChannels; f++) {
		std::stringstream ssd;
		//cout << "create DO channel" << endl;
		ssd << "Dev" << deviceID << "/port" << DOPort << "/line" << DOChannels[f];
		errorCode = DAQmxCreateDOChan(DOtaskHandle, ssd.str().c_str(), "", DAQmx_Val_ChanPerLine);
		if (errorCode < 0) {
			DAQmxGetErrorString(errorCode, buffer, 2000);
			MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
			return false;
		}
	}

	for (int f = 0; f < numDIChannels; f++) {
		std::stringstream ssd;
		//cout << "create DI channel" << endl;
		ssd << "Dev" << deviceID << "/port" << DIPort << "/line" << DIChannels[f];
		errorCode = DAQmxCreateDIChan(DItaskHandle, ssd.str().c_str(), "", DAQmx_Val_ChanPerLine);
		if (errorCode < 0) {
			DAQmxGetErrorString(errorCode, buffer, 2000);
			MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
			return false;
		}
	}

	////cfg clocks
	if (bUsingAI) {
		errorCode = DAQmxCfgSampClkTiming(AItaskHandle, "", sampleFreq, DAQmx_Val_Rising, (bRepeat? DAQmx_Val_ContSamps : DAQmx_Val_FiniteSamps), numSamples);
		if (errorCode < 0) {
			DAQmxGetErrorString(errorCode, buffer, 2000);
			MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
			return false;
		}
	}
	if (bUsingAO) {
		//MessageBoxA(NULL, "Add sample timing", "", MB_OK);
		errorCode = DAQmxCfgSampClkTiming(AOtaskHandle, "", sampleFreq, DAQmx_Val_Rising, (bRepeat ? DAQmx_Val_ContSamps : DAQmx_Val_FiniteSamps), numSamples);
		if (errorCode < 0) {
			DAQmxGetErrorString(errorCode, buffer, 2000);
			MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
			return false;
		}
	}
	if (bUsingDO) {
		//cout << "DO sample clk" << endl;
		errorCode = DAQmxCfgSampClkTiming(DOtaskHandle, "", sampleFreq, DAQmx_Val_Rising, (bRepeat ? DAQmx_Val_ContSamps : DAQmx_Val_FiniteSamps), numSamples);
		if (errorCode < 0) {
			DAQmxGetErrorString(errorCode, buffer, 2000);
			MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
			return false;
		}
	}
	if (bUsingDI) {
		//cout << "DI sample clock" << endl;
		errorCode = DAQmxCfgSampClkTiming(DItaskHandle, "", sampleFreq, DAQmx_Val_Rising, (bRepeat ? DAQmx_Val_ContSamps : DAQmx_Val_FiniteSamps), numSamples);
		if (errorCode < 0) {
			DAQmxGetErrorString(errorCode, buffer, 2000);
			MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
			return false;
		}
	}


	////cfg triggers depending on AO, AI, DO, DI
	std::stringstream ss4;


	if (bUsingAO && bUsingAI && bUsingDO && bUsingDI) {
		//ao/StartTrigger is the master trigger
		ss4 << "/Dev" << deviceID << "/ao/StartTrigger";
		errorCode = DAQmxCfgDigEdgeStartTrig(DItaskHandle, ss4.str().c_str(), DAQmx_Val_Rising);
		if (errorCode < 0) {
			DAQmxGetErrorString(errorCode, buffer, 2000);
			MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
			return false;
		}
		errorCode = DAQmxCfgDigEdgeStartTrig(DOtaskHandle, ss4.str().c_str(), DAQmx_Val_Rising);
		if (errorCode < 0) {
			DAQmxGetErrorString(errorCode, buffer, 2000);
			MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
			return false;
		}
		errorCode = DAQmxCfgDigEdgeStartTrig(AItaskHandle, ss4.str().c_str(), DAQmx_Val_Rising);
		if (errorCode < 0) {
			DAQmxGetErrorString(errorCode, buffer, 2000);
			MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
			return false;
		}
	}
	else if (bUsingAO && bUsingAI && bUsingDO && !bUsingDI) {
		//ao/StartTrigger is the master trigger
		ss4 << "/Dev" << deviceID << "/ao/StartTrigger";
		errorCode = DAQmxCfgDigEdgeStartTrig(DOtaskHandle, ss4.str().c_str(), DAQmx_Val_Rising);
		if (errorCode < 0) {
			DAQmxGetErrorString(errorCode, buffer, 2000);
			MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
			return false;
		}
		errorCode = DAQmxCfgDigEdgeStartTrig(AItaskHandle, ss4.str().c_str(), DAQmx_Val_Rising);
		if (errorCode < 0) {
			DAQmxGetErrorString(errorCode, buffer, 2000);
			MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
			return false;
		}
	}
	else if (bUsingAO && bUsingAI && !bUsingDO && bUsingDI) {
		//using ao/StartTrigger
		ss4 << "/Dev" << deviceID << "/ao/StartTrigger";
		errorCode = DAQmxCfgDigEdgeStartTrig(DItaskHandle, ss4.str().c_str(), DAQmx_Val_Rising);
		if (errorCode < 0) {
			DAQmxGetErrorString(errorCode, buffer, 2000);
			MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
			return false;
		}
		errorCode = DAQmxCfgDigEdgeStartTrig(AItaskHandle, ss4.str().c_str(), DAQmx_Val_Rising);
		if (errorCode < 0) {
			DAQmxGetErrorString(errorCode, buffer, 2000);
			MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
			return false;
		}
	}
	else if (bUsingAO && bUsingAI && !bUsingDO && !bUsingDI) {
		//using ao/StartTrigger
		ss4 << "/Dev" << deviceID << "/ao/StartTrigger";
		errorCode = DAQmxCfgDigEdgeStartTrig(AItaskHandle, ss4.str().c_str(), DAQmx_Val_Rising);
		if (errorCode < 0) {
			DAQmxGetErrorString(errorCode, buffer, 2000);
			MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
			return false;
		}
	}
	else if (bUsingAO && !bUsingAI && bUsingDO && bUsingDI) {
		//using ao/StartTrigger
		ss4 << "/Dev" << deviceID << "/ao/StartTrigger";
		errorCode = DAQmxCfgDigEdgeStartTrig(DItaskHandle, ss4.str().c_str(), DAQmx_Val_Rising);
		if (errorCode < 0) {
			DAQmxGetErrorString(errorCode, buffer, 2000);
			MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
			return false;
		}
		errorCode = DAQmxCfgDigEdgeStartTrig(DOtaskHandle, ss4.str().c_str(), DAQmx_Val_Rising);
		if (errorCode < 0) {
			DAQmxGetErrorString(errorCode, buffer, 2000);
			MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
			return false;
		}
	}
	else if (bUsingAO && !bUsingAI && bUsingDO && !bUsingDI) {
		//using ao/StartTrigger
		ss4 << "/Dev" << deviceID << "/ao/StartTrigger";
		errorCode = DAQmxCfgDigEdgeStartTrig(DOtaskHandle, ss4.str().c_str(), DAQmx_Val_Rising);
		if (errorCode < 0) {
			DAQmxGetErrorString(errorCode, buffer, 2000);
			MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
			return false;
		}
	}
	else if (!bUsingAO && bUsingAI && bUsingDO && bUsingDI) {
		//using do/StartTrigger
		ss4 << "/Dev" << deviceID << "/do/StartTrigger";
		errorCode = DAQmxCfgDigEdgeStartTrig(DItaskHandle, ss4.str().c_str(), DAQmx_Val_Rising);
		if (errorCode < 0) {
			DAQmxGetErrorString(errorCode, buffer, 2000);
			MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
			return false;
		}
		errorCode = DAQmxCfgDigEdgeStartTrig(AItaskHandle, ss4.str().c_str(), DAQmx_Val_Rising);
		if (errorCode < 0) {
			DAQmxGetErrorString(errorCode, buffer, 2000);
			MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
			return false;
		}
	}
	else if (!bUsingAO && bUsingAI && bUsingDO && !bUsingDI) {
		//using do/StartTrigger
		ss4 << "/Dev" << deviceID << "/do/StartTrigger";
		errorCode = DAQmxCfgDigEdgeStartTrig(AItaskHandle, ss4.str().c_str(), DAQmx_Val_Rising);
		if (errorCode < 0) {
			DAQmxGetErrorString(errorCode, buffer, 2000);
			MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
			return false;
		}
	}
	else if (bUsingAO && !bUsingAI && !bUsingDO && bUsingDI) {
		//using ao/StartTrigger
		ss4 << "/Dev" << deviceID << "/ao/StartTrigger";
		errorCode = DAQmxCfgDigEdgeStartTrig(DItaskHandle, ss4.str().c_str(), DAQmx_Val_Rising);
		if (errorCode < 0) {
			DAQmxGetErrorString(errorCode, buffer, 2000);
			MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
			return false;
		}
	}
	else if (bUsingAO && !bUsingAI && !bUsingDO && !bUsingDI) {
		// dont need to lock timers
	}
	else if (!bUsingAO && bUsingAI && !bUsingDO && bUsingDI) {
		//use AI trigger
		ss4 << "/Dev" << deviceID << "/ai/StartTrigger";
		errorCode = DAQmxCfgDigEdgeStartTrig(DItaskHandle, ss4.str().c_str(), DAQmx_Val_Rising);
		if (errorCode < 0) {
			DAQmxGetErrorString(errorCode, buffer, 2000);
			MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
			return false;
		}
	}
	else if (!bUsingAO && bUsingAI && !bUsingDO && !bUsingDI) {
		//no trigger
	}
	else if (!bUsingAO && !bUsingAI && bUsingDO && bUsingDI) {
		//use DO trigger
		//cout << "DI set start trigger" << endl;
		ss4 << "/Dev" << deviceID << "/do/StartTrigger";
		errorCode = DAQmxCfgDigEdgeStartTrig(DItaskHandle, ss4.str().c_str(), DAQmx_Val_Rising);
		if (errorCode < 0) {
			DAQmxGetErrorString(errorCode, buffer, 2000);
			MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
			return false;
		}
	}
	else if (!bUsingAO && !bUsingAI && bUsingDO && !bUsingDI) {
		//no trigger
	}
	else if (!bUsingAO && !bUsingAI && !bUsingDO && bUsingDI) {
		//no trigger
	}
	else if (bUsingAO && !bUsingAI && !bUsingDO && !bUsingDI) {
		//no trigger
	}
	else if (!bUsingAO && !bUsingAI && bUsingDO && !bUsingDI) {
		//no trigger
	}
	else {
		cout << "ERROR - can be !AO, !AI !DO and !DI, so NO PROTOCOL!" << endl;
	}

	////get buffers ready to send and in correct order
	if (bUsingAO) {
		if (bDirtyAO) {
			errorCode = DAQmxWriteAnalogF64(AOtaskHandle, numSamples, 0, DAQmx_Val_WaitInfinitely, DAQmx_Val_GroupByScanNumber, dataAO_back, NULL, NULL);
			if (errorCode < 0) {
				DAQmxGetErrorString(errorCode, buffer, 2000);
				MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
				return false;
			}
		}
		else {
			errorCode = DAQmxWriteAnalogF64(AOtaskHandle, numSamples, 0, DAQmx_Val_WaitInfinitely, DAQmx_Val_GroupByScanNumber, dataAO, NULL, NULL);
			if (errorCode < 0) {
				cout << "EXCEPTION" << endl;
				for (int f = 0; f < numSamples * numAOChannels; f++) {
					cout << dataAO[f] << endl;
				}
				DAQmxGetErrorString(errorCode, buffer, 2000);
				MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
				return false;
			}
		}

	}
	if (bUsingDO) {
		if (bDirtyDO) {
			errorCode = DAQmxWriteDigitalU32(DOtaskHandle, numSamples, 0, DAQmx_Val_WaitInfinitely, DAQmx_Val_GroupByScanNumber, dataDO_back, NULL, NULL);
			if (errorCode < 0) {
				DAQmxGetErrorString(errorCode, buffer, 2000);
				MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
				return false;
			}
		}
		else
		{
			//cout << "DO write" << endl;
			errorCode = DAQmxWriteDigitalU32(DOtaskHandle, numSamples, 0, DAQmx_Val_WaitInfinitely, DAQmx_Val_GroupByScanNumber, dataDO, NULL, NULL);
			if (errorCode < 0) {
				DAQmxGetErrorString(errorCode, buffer, 2000);
				MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
				return false;
			}
		}
	}

	return true;
}
bool	SimpleProtocol::RunProtocolDAQ(double* pTimeSec, double* pDataAO, double* pDataAI, uInt32* pDataDO, uInt32* pDataDI, bool bStartTrigger, bool bRisingEdge, uInt32 pFIx, double timeoutSec) {
	/*
	* Start all of the tasks which have a clock dependent on another subsystem - so that they will start when the master subsystem starts
	* configure a start trigger if needed in the master subsystem
	* Start the master sub-system
	* This will now block until the samples have been read. (Reading is synchronous and Writing is asynchronous)
	* For tasks which are only output eg AO, DO then daqmx waits until the task is over.
	* The tasks are stopped.
	*


	*/
	

	if (bContinuous) {
		// and if bFirst
		//     start continuous sampling
		//	   then wait for the callback to signal that the data has arrived
		//	   then copy and return with it
		//		return 1 which should mean that if it is called again then the next data drop will be returned to java
		//
		// the ShutdownDAQ should stop the process
		//
		//
		//  can I pass a member function as the callback function - in which case each protocol will be correctly called 

		char buffer[2000] = { 0 };
		int errorCode = 0;

		bool bUsingAO = numAOChannels > 0;
		bool bUsingAI = numAIChannels > 0;
		bool bUsingDO = numDOChannels > 0;
		bool bUsingDI = numDIChannels > 0;
		if (bUsingAO) {
			if (bDirtyAO) {
				memcpy(pDataAO, dataAO_back, numSamples * numAOChannels * sizeof(double));
				bDirtyAO = false;
			}
			else {
				memcpy(pDataAO, dataAO, numSamples * numAOChannels * sizeof(double));
			}

		}
		if (bUsingDO) {
			if (bDirtyDO) {
				memcpy(pDataDO, dataDO_back, numSamples * numDOChannels * sizeof(uInt32));
				bDirtyDO = false;
			}
			else {
				memcpy(pDataDO, dataDO, numSamples * numDOChannels * sizeof(uInt32));



			}
		}




		return false;
	}
	else {

		char buffer[2000] = { 0 };
		int errorCode = 0;

		bool bUsingAO = numAOChannels > 0;
		bool bUsingAI = numAIChannels > 0;
		bool bUsingDO = numDOChannels > 0;
		bool bUsingDI = numDIChannels > 0;
		if (bUsingAO) {
			if (bDirtyAO) {
				memcpy(pDataAO, dataAO_back, numSamples * numAOChannels * sizeof(double));
				bDirtyAO = false;
			}
			else {
				memcpy(pDataAO, dataAO, numSamples * numAOChannels * sizeof(double));
			}

		}
		if (bUsingDO) {
			if (bDirtyDO) {
				memcpy(pDataDO, dataDO_back, numSamples * numDOChannels * sizeof(uInt32));
				bDirtyDO = false;
			}
			else {
				memcpy(pDataDO, dataDO, numSamples * numDOChannels * sizeof(uInt32));



			}

			//todo
		}

		stringstream ssError;
		if ((!bRepeat && bFirst) || (!bRepeat && !bFirst) || (bRepeat && bFirst)) {
			if (bRepeat && !bFirst) bFirst = false;
			//if !bRepeat then do this each time
			//if bRepeat then do only 1
			this->bStartTrigger = bStartTrigger;
			int32 triggerType = (bRisingEdge) ? DAQmx_Val_Rising : DAQmx_Val_Falling;

			this->pFIx = pFIx;
			std::stringstream ssTrig;
			cout << "Checking PFIx" << endl;
			ssTrig << "/Dev" << deviceID << "/PFI" << pFIx;


			ssError << "Dev" << deviceID << " NIDAQ error";

			////Start protocol ///////////////////////////////////////////
			if (bUsingAO && bUsingAI && bUsingDO && bUsingDI) {
				//start everything before AO
				errorCode = DAQmxStartTask(DItaskHandle);
				if (errorCode < 0) {
					DAQmxGetErrorString(errorCode, buffer, 2000);
					MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
					return false;
				}
				errorCode = DAQmxStartTask(AItaskHandle);
				if (errorCode < 0) {
					DAQmxGetErrorString(errorCode, buffer, 2000);
					MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
					return false;
				}
				errorCode = DAQmxStartTask(DOtaskHandle);
				if (errorCode < 0) {
					DAQmxGetErrorString(errorCode, buffer, 2000);
					MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
					return false;
				}
				if (bStartTrigger) {
					errorCode = DAQmxCfgDigEdgeStartTrig(AOtaskHandle, ssTrig.str().c_str(), triggerType);
					if (errorCode < 0) {
						DAQmxGetErrorString(errorCode, buffer, 2000);
						MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
						return false;
					}
				}
				errorCode = DAQmxStartTask(AOtaskHandle);
				if (errorCode < 0) {
					DAQmxGetErrorString(errorCode, buffer, 2000);
					MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
					return false;
				}
			}
			else if (bUsingAO && bUsingAI && bUsingDO && !bUsingDI) {
				//start everything before AO
				errorCode = DAQmxStartTask(AItaskHandle);
				if (errorCode < 0) {
					DAQmxGetErrorString(errorCode, buffer, 2000);
					MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
					return false;
				}
				errorCode = DAQmxStartTask(DOtaskHandle);
				if (errorCode < 0) {
					DAQmxGetErrorString(errorCode, buffer, 2000);
					MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
					return false;
				}
				if (bStartTrigger) {
					errorCode = DAQmxCfgDigEdgeStartTrig(AOtaskHandle, ssTrig.str().c_str(), triggerType);
					if (errorCode < 0) {
						DAQmxGetErrorString(errorCode, buffer, 2000);
						MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
						return false;
					}
				}
				errorCode = DAQmxStartTask(AOtaskHandle);
				if (errorCode < 0) {
					DAQmxGetErrorString(errorCode, buffer, 2000);
					MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
					return false;
				}
			}
			else if (bUsingAO && bUsingAI && !bUsingDO && bUsingDI) {
				errorCode = DAQmxStartTask(DItaskHandle);
				if (errorCode < 0) {
					DAQmxGetErrorString(errorCode, buffer, 2000);
					MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
					return false;
				}
				errorCode = DAQmxStartTask(AItaskHandle);
				if (errorCode < 0) {
					DAQmxGetErrorString(errorCode, buffer, 2000);
					MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
					return false;
				}
				if (bStartTrigger) {
					errorCode = DAQmxCfgDigEdgeStartTrig(AOtaskHandle, ssTrig.str().c_str(), triggerType);
					if (errorCode < 0) {
						DAQmxGetErrorString(errorCode, buffer, 2000);
						MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
						return false;
					}
				}
				errorCode = DAQmxStartTask(AOtaskHandle);
				if (errorCode < 0) {
					DAQmxGetErrorString(errorCode, buffer, 2000);
					MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
					return false;
				}
			}
			else if (bUsingAO && bUsingAI && !bUsingDO && !bUsingDI) {
				errorCode = DAQmxStartTask(AItaskHandle);
				if (errorCode < 0) {
					DAQmxGetErrorString(errorCode, buffer, 2000);
					MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
					return false;
				}
				if (bStartTrigger) {
					errorCode = DAQmxCfgDigEdgeStartTrig(AOtaskHandle, ssTrig.str().c_str(), triggerType);
					if (errorCode < 0) {
						DAQmxGetErrorString(errorCode, buffer, 2000);
						MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
						return false;
					}
				}
				errorCode = DAQmxStartTask(AOtaskHandle);
				if (errorCode < 0) {
					DAQmxGetErrorString(errorCode, buffer, 2000);
					MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
					return false;
				}
			}
			else if (bUsingAO && !bUsingAI && bUsingDO && bUsingDI) {
				errorCode = DAQmxStartTask(DItaskHandle);
				if (errorCode < 0) {
					DAQmxGetErrorString(errorCode, buffer, 2000);
					MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
					return false;
				}
				errorCode = DAQmxStartTask(DOtaskHandle);
				if (errorCode < 0) {
					DAQmxGetErrorString(errorCode, buffer, 2000);
					MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
					return false;
				}
				if (bStartTrigger) {
					errorCode = DAQmxCfgDigEdgeStartTrig(AOtaskHandle, ssTrig.str().c_str(), triggerType);
					if (errorCode < 0) {
						DAQmxGetErrorString(errorCode, buffer, 2000);
						MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
						return false;
					}
				}
				errorCode = DAQmxStartTask(AOtaskHandle);
				if (errorCode < 0) {
					DAQmxGetErrorString(errorCode, buffer, 2000);
					MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
					return false;
				}
			}
			else if (!bUsingAO && bUsingAI && bUsingDO && !bUsingDI) {
				errorCode = DAQmxStartTask(AItaskHandle);
				if (errorCode < 0) {
					DAQmxGetErrorString(errorCode, buffer, 2000);
					MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
					return false;
				}
				if (bStartTrigger) {
					errorCode = DAQmxCfgDigEdgeStartTrig(DOtaskHandle, ssTrig.str().c_str(), triggerType);
					if (errorCode < 0) {
						DAQmxGetErrorString(errorCode, buffer, 2000);
						MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
						return false;
					}
				}
				errorCode = DAQmxStartTask(DOtaskHandle);
				if (errorCode < 0) {
					DAQmxGetErrorString(errorCode, buffer, 2000);
					MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
					return false;
				}
			}
			else if (bUsingAO && !bUsingAI && !bUsingDO && bUsingDI) {
				errorCode = DAQmxStartTask(DItaskHandle);
				if (errorCode < 0) {
					DAQmxGetErrorString(errorCode, buffer, 2000);
					MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
					return false;
				}
				if (bStartTrigger) {
					errorCode = DAQmxCfgDigEdgeStartTrig(AOtaskHandle, ssTrig.str().c_str(), triggerType);
					if (errorCode < 0) {
						DAQmxGetErrorString(errorCode, buffer, 2000);
						MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
						return false;
					}
				}
				errorCode = DAQmxStartTask(AOtaskHandle);
				if (errorCode < 0) {
					DAQmxGetErrorString(errorCode, buffer, 2000);
					MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
					return false;
				}
			}
			else if (bUsingAO && !bUsingAI && !bUsingDO && !bUsingDI) {
				if (bStartTrigger) {
					errorCode = DAQmxCfgDigEdgeStartTrig(AOtaskHandle, ssTrig.str().c_str(), triggerType);
					if (errorCode < 0) {
						DAQmxGetErrorString(errorCode, buffer, 2000);
						MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
						return false;
					}
				}
				errorCode = DAQmxStartTask(AOtaskHandle);
				if (errorCode < 0) {
					DAQmxGetErrorString(errorCode, buffer, 2000);
					MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
					return false;
				}
				if (!bRepeat) errorCode = DAQmxWaitUntilTaskDone(AOtaskHandle, DAQmx_Val_WaitInfinitely);
				if (errorCode < 0) {
					DAQmxGetErrorString(errorCode, buffer, 2000);
					MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
					return false;
				}
			}
			else if (!bUsingAO && bUsingAI && !bUsingDO && bUsingDI) {
				errorCode = DAQmxStartTask(DItaskHandle);
				if (errorCode < 0) {
					DAQmxGetErrorString(errorCode, buffer, 2000);
					MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
					return false;
				}
				if (bStartTrigger) {
					errorCode = DAQmxCfgDigEdgeStartTrig(AItaskHandle, ssTrig.str().c_str(), triggerType);
					if (errorCode < 0) {
						DAQmxGetErrorString(errorCode, buffer, 2000);
						MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
						return false;
					}
				}
				errorCode = DAQmxStartTask(AItaskHandle);
				if (errorCode < 0) {
					DAQmxGetErrorString(errorCode, buffer, 2000);
					MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
					return false;
				}
			}
			else if (!bUsingAO && bUsingAI && !bUsingDO && !bUsingDI) {
				//no trigger
				if (bStartTrigger) {
					errorCode = DAQmxCfgDigEdgeStartTrig(AItaskHandle, ssTrig.str().c_str(), triggerType);
					if (errorCode < 0) {
						DAQmxGetErrorString(errorCode, buffer, 2000);
						MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
						return false;
					}
				}
				errorCode = DAQmxStartTask(AItaskHandle);
				if (errorCode < 0) {
					DAQmxGetErrorString(errorCode, buffer, 2000);
					MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
					return false;
				}
			}
			else if (!bUsingAO && !bUsingAI && bUsingDO && bUsingDI) {
				//no trigger
				//cout << "DI start task" << endl;////////////////////////////////////////////////IS THIS CORRECT?
				errorCode = DAQmxStartTask(DItaskHandle);
				if (errorCode < 0) {
					DAQmxGetErrorString(errorCode, buffer, 2000);
					MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
					return false;
				}
				if (bStartTrigger) {
					errorCode = DAQmxCfgDigEdgeStartTrig(DOtaskHandle, ssTrig.str().c_str(), triggerType);
					if (errorCode < 0) {
						DAQmxGetErrorString(errorCode, buffer, 2000);
						MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
						return false;
					}
				}
				errorCode = DAQmxStartTask(DOtaskHandle);
				if (errorCode < 0) {
					DAQmxGetErrorString(errorCode, buffer, 2000);
					MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
					return false;
				}
			}
			else if (!bUsingAO && !bUsingAI && bUsingDO && !bUsingDI) {
				//no trigger
				if (bStartTrigger) {
					errorCode = DAQmxCfgDigEdgeStartTrig(DOtaskHandle, ssTrig.str().c_str(), triggerType);
					if (errorCode < 0) {
						DAQmxGetErrorString(errorCode, buffer, 2000);
						MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
						return false;
					}
				}
				errorCode = DAQmxStartTask(DOtaskHandle);
				if (errorCode < 0) {
					DAQmxGetErrorString(errorCode, buffer, 2000);
					MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
					return false;
				}
				errorCode = DAQmxWaitUntilTaskDone(DOtaskHandle, DAQmx_Val_WaitInfinitely);
				if (errorCode < 0) {
					DAQmxGetErrorString(errorCode, buffer, 2000);
					MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
					return false;
				}
			}
			else if (!bUsingAO && !bUsingAI && !bUsingDO && bUsingDI) {
				//no trigger
				if (bStartTrigger) {
					errorCode = DAQmxCfgDigEdgeStartTrig(DItaskHandle, ssTrig.str().c_str(), triggerType);
					if (errorCode < 0) {
						DAQmxGetErrorString(errorCode, buffer, 2000);
						MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
						return false;
					}
				}
				errorCode = DAQmxStartTask(DItaskHandle);
				if (errorCode < 0) {
					DAQmxGetErrorString(errorCode, buffer, 2000);
					MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
					return false;
				}
			}
			else if (bUsingAO && !bUsingAI && bUsingDO && !bUsingDI) {


				errorCode = DAQmxStartTask(DOtaskHandle);
				if (errorCode < 0) {
					DAQmxGetErrorString(errorCode, buffer, 2000);
					MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
					return false;
				}

				if (bStartTrigger) {
					errorCode = DAQmxCfgDigEdgeStartTrig(AOtaskHandle, ssTrig.str().c_str(), triggerType);
					if (errorCode < 0) {
						DAQmxGetErrorString(errorCode, buffer, 2000);
						MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
						return false;
					}
				}
				errorCode = DAQmxStartTask(AOtaskHandle);
				if (errorCode < 0) {
					DAQmxGetErrorString(errorCode, buffer, 2000);
					MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
					return false;
				}
				errorCode = DAQmxWaitUntilTaskDone(AOtaskHandle, DAQmx_Val_WaitInfinitely);
				if (errorCode < 0) {
					DAQmxGetErrorString(errorCode, buffer, 2000);
					MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
					return false;
				}



			}
			else if (!bUsingAO && bUsingAI && bUsingDO && bUsingDI) {

				//no trigger
				errorCode = DAQmxStartTask(DItaskHandle);
				if (errorCode < 0) {
					DAQmxGetErrorString(errorCode, buffer, 2000);
					MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
					return false;
				}
				errorCode = DAQmxStartTask(AItaskHandle);
				if (errorCode < 0) {
					DAQmxGetErrorString(errorCode, buffer, 2000);
					MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
					return false;
				}
				if (bStartTrigger) {
					errorCode = DAQmxCfgDigEdgeStartTrig(DOtaskHandle, ssTrig.str().c_str(), triggerType);
					if (errorCode < 0) {
						DAQmxGetErrorString(errorCode, buffer, 2000);
						MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
						return false;
					}
				}
				errorCode = DAQmxStartTask(DOtaskHandle);
				if (errorCode < 0) {
					DAQmxGetErrorString(errorCode, buffer, 2000);
					MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
					return false;
				}
			}
			else {
				cout << "ERROR - can be !AO, !AI and !DO" << endl;
			}

		}

		if (bUsingAI) {
			int32 read = 0;
			errorCode = DAQmxReadAnalogF64(AItaskHandle, DAQmx_Val_Auto, timeoutSec, DAQmx_Val_GroupByScanNumber, pDataAI, numSamples * numAIChannels, (int32*)&read, NULL);
			if (errorCode < 0) {
				DAQmxGetErrorString(errorCode, buffer, 2000);
				MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
				return false;
			}
		}

		if (bUsingDI) {
			int32 read = 0;
			errorCode = DAQmxReadDigitalU32(DItaskHandle, DAQmx_Val_Auto, timeoutSec, DAQmx_Val_GroupByScanNumber, pDataDI, numSamples * numDIChannels, (int32*)&read, NULL);
			if (errorCode < 0) {
				DAQmxGetErrorString(errorCode, buffer, 2000);
				MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
				return false;
			}
		}

		//what about if bUsingAO etc dont we also have to wait for these?

		LARGE_INTEGER li;
		QueryPerformanceCounter(&li);
		double endTime = double(li.QuadPart) / PCFreq / 1000.0; // start time in Sec

		if (!bRepeat) {
			if (bUsingAI) {
				errorCode = DAQmxStopTask(AItaskHandle);
				if (errorCode < 0) {
					DAQmxGetErrorString(errorCode, buffer, 2000);
					MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
					return false;
				}
			}
			if (bUsingDO) {
				errorCode = DAQmxStopTask(DOtaskHandle);
				if (errorCode < 0) {
					DAQmxGetErrorString(errorCode, buffer, 2000);
					MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
					return false;
				}
			}
			if (bUsingAO) {
				errorCode = DAQmxStopTask(AOtaskHandle);
				if (errorCode < 0) {
					DAQmxGetErrorString(errorCode, buffer, 2000);
					MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
					return false;
				}
			}
			if (bUsingDI) {
				errorCode = DAQmxStopTask(DItaskHandle);
				if (errorCode < 0) {
					DAQmxGetErrorString(errorCode, buffer, 2000);
					MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
					return false;
				}
			}
		}



		for (int f = 0; f < numSamples; f++) {
			pTimeSec[f] = endTime - (numSamples - f) / sampleFreq;
		}


		return true;  // return value no meaning
	}
}
bool	SimpleProtocol::ReleaseDAQ() {
	
		char buffer[2000] = { 0 };
		stringstream ssError;
		int errorCode = 0;
		bool bUsingAO = numAOChannels > 0;
		bool bUsingAI = numAIChannels > 0;
		bool bUsingDO = numDOChannels > 0;
		bool bUsingDI = numDIChannels > 0;

		if (bRepeat) {
			bFirst = true;
			if (bUsingAI) {
				errorCode = DAQmxStopTask(AItaskHandle);
				if (errorCode < 0) {
					DAQmxGetErrorString(errorCode, buffer, 2000);
					MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
					return false;
				}
			}
			if (bUsingDO) {
				errorCode = DAQmxStopTask(DOtaskHandle);
				if (errorCode < 0) {
					DAQmxGetErrorString(errorCode, buffer, 2000);
					MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
					return false;
				}
			}
			if (bUsingAO) {
				errorCode = DAQmxStopTask(AOtaskHandle);
				if (errorCode < 0) {
					DAQmxGetErrorString(errorCode, buffer, 2000);
					MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
					return false;
				}
			}
			if (bUsingDI) {
				errorCode = DAQmxStopTask(DItaskHandle);
				if (errorCode < 0) {
					DAQmxGetErrorString(errorCode, buffer, 2000);
					MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
					return false;
				}
			}
			}
/*
* Clear all of the daqmx tasks - which destroys the resources held by the NIDAQ



*/

		ssError << "Dev" << deviceID << " NIDAQ error";

		if (numAIChannels > 0) {
			errorCode = DAQmxClearTask(AItaskHandle);
			if (errorCode < 0) {
				DAQmxGetErrorString(errorCode, buffer, 2000);
				MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
				return false;
			}
		}
		if (numDOChannels > 0) {
			errorCode = DAQmxClearTask(DOtaskHandle);
			if (errorCode < 0) {
				DAQmxGetErrorString(errorCode, buffer, 2000);
				MessageBoxA(NULL, buffer, "NIDAQ error", MB_OK);
				return false;
			}
		}
		if (numAOChannels > 0) {
			errorCode = DAQmxClearTask(AOtaskHandle);
			if (errorCode < 0) {
				DAQmxGetErrorString(errorCode, buffer, 2000);
				MessageBoxA(NULL, buffer, "NIDAQ error", MB_OK);
				return false;
			}
		}
		if (numDIChannels > 0) {
			errorCode = DAQmxClearTask(DItaskHandle);
			if (errorCode < 0) {
				DAQmxGetErrorString(errorCode, buffer, 2000);
				MessageBoxA(NULL, buffer, "NIDAQ error", MB_OK);
				return false;
			}
		}
		return true;
}

double	SimpleProtocol::GetCurrentRunSampleTime() {


	return 1000.0 / sampleFreq; // in ms
}

double	SimpleProtocol::GetSampleFreq() {
	return sampleFreq;
}


int		SimpleProtocol::GetNumChannels(int type) {
	if (type == 0) {
		//ao
		return numAOChannels;
	}
	else if (type == 1) {
		//ai
		return numAIChannels;
	}
	else if (type == 2) {
		//do
		return numDOChannels;
	}
	else {
		//di
		return numDIChannels;
	}

}
int		SimpleProtocol::GetIndexFromChannel(int type, int ch) {
	if (type == 0) {
		for (int f = 0; f < AOChannels.size(); f++) {
			if (ch == AOChannels[f]) {
				return f;
			}
		}
		return -1;
	}
	else if (type == 1) {
		for (int f = 0; f < AIChannels.size(); f++) {
			if (ch == AIChannels[f]) {
				return f;
			}
		}
		return -1;

	}
	else if (type == 2) {
		for (int f = 0; f < DOChannels.size(); f++) {
			if (ch == DOChannels[f]) {
				return f;
			}
		}
		return -1;
	}
	else {
		for (int f = 0; f < DIChannels.size(); f++) {
			if (ch == DIChannels[f]) {
				return f;
			}
		}
		return -1;
	}
}
int		SimpleProtocol::GetChannelFromIndex(int type, int ix) {

	if (type == 0) {
		return AOChannels[ix];
	}
	else if (type == 1) {
		return AIChannels[ix];
	}
	else if (type == 2) {
		return DOChannels[ix];
	}
	else {
		return DIChannels[ix];
	}

}
int		SimpleProtocol::GetPort(bool bIn) {

	if (bIn) {
		return DIPort;
	}
	else {
		return DOPort;
	}
}
int		SimpleProtocol::GetDeviceID() {
	return deviceID;
}

int		SimpleProtocol::GetNumSamples() {
	return numSamples;
}
int		SimpleProtocol::GetNumAOChannels() {
	return numAOChannels;
}
int		SimpleProtocol::GetNumAIChannels() {
	return numAIChannels;
}
int		SimpleProtocol::GetNumDOChannels() {
	return numDOChannels;
}
int		SimpleProtocol::GetNumDIChannels() {
	return numDIChannels;
}


