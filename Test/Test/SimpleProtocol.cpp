#include "SimpleProtocol.h"

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
* is over
* Stop each task.
* Clear each task to reset the daqmx.



*/
extern bool bDebug;
extern ofstream* pOfs;

SimpleProtocol::SimpleProtocol(int deviceID, double minV, double maxV, string szDeviceName) {
	if (bDebug) {
		(*pOfs) << "SimpleProtocol::SimpleProtocol" << endl;
	}
	this->deviceID = deviceID;
	this->minV = minV;
	this->maxV = maxV;
	this->szDeviceName = szDeviceName;

	bStartTrigger = false;
	numAIChannels = 0;
	numAOChannels = 0;
	numDIChannels = 0;
	numDOChannels = 0;
	pFIx = 0;


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
	bTerminate = false;
	pThreadData = new ThreadData();
	bReturn = false;
}
SimpleProtocol::~SimpleProtocol() {
	if (bDebug) {
		(*pOfs) << "SimpleProtocol::~SimpleProtocol" << endl;
	}
	//release the non daqmx resources
	if (!dataAO) delete[] dataAO;
	if (!dataDO) delete[] dataDO;
	if (!dataAO_back) delete[] dataAO_back;
	if (!dataDO_back) delete[] dataDO_back;
	if (pThreadData) delete pThreadData;
}
bool	SimpleProtocol::Init(char* szCsv) {
	if (bDebug) {
		(*pOfs) << "SimpleProtocol::Init" << endl;
	}
	//load the simple protocol from the text file szCsv
	ifstream pIfs(szCsv);
	vector<string> v_lines;
	string szLine;
	while (getline(pIfs, szLine)) {
		v_lines.push_back(szLine);
	}
	pIfs.close();
	//cout << "INIT FILE: " << szCsv << endl;
	//load the header from the csv
	for (int f = 0; f < 12; f++) {
		stringstream sstream(v_lines[f]);
		string member;
		sstream >> member;
		if (member == "numSamples") {
			sstream >> numSamples;
			cout << "Number of samples: " << numSamples << endl;
		}
		else if (member == "sampFreq") {
			sstream >> sampleFreq;
			cout << "Sample Frequency: " << sampleFreq << endl;
		}
		else if (member == "numAIChannels") {
			sstream >> numAIChannels;
			cout << "Number of AI Channels: " << numAIChannels << endl;
		}
		else if (member == "AIChannels") {
			if (numAIChannels > 0) {
				int channel = 0;
				string word2;
				for (int ff = 0; ff < numAIChannels; ff++) {
					getline(sstream, word2, ',');
					stringstream sss(word2);
					channel = word2[(ff == 0) ? 3 : 2] - '0';
					AIChannels.push_back(channel);
				}
				cout << "AI Channels: ";
				for (int ff = 0; ff < AIChannels.size(); ff++) {
					cout << AIChannels[ff] << " ";
				}
				cout << endl;
			}
		}
		else if (member == "numDIChannels") {
			sstream >> numDIChannels;
			cout << "Number of DI Channels: " << numDIChannels << endl;
		}
		else if (member == "DIPort") {
			sstream >> DIPort;
			cout << "DI Port: " << DIPort << endl;
		}
		else if (member == "DIChannels") {
			if (numDIChannels > 0) {
				int channel = 0;
				string word2;
				for (int ff = 0; ff < numDIChannels; ff++) {
					getline(sstream, word2, ',');
					stringstream sss(word2);
					channel = word2[(ff == 0) ? 5 : 4] - '0';
					DIChannels.push_back(channel);
				}
				cout << "DI Channels: ";
				for (int ff = 0; ff < DIChannels.size(); ff++) {
					cout << DIChannels[ff] << " ";
				}
				cout << endl;
			}
		}
		else if (member == "numAOChannels") {
			sstream >> numAOChannels;
			cout << "Number of AO Channels: " << numAOChannels << endl;
		}
		else if (member == "AOChannels") {
			if (numAOChannels > 0) {
				int channel = 0;
				string word2;
				for (int ff = 0; ff < numAOChannels; ff++) {
					getline(sstream, word2, ',');
					stringstream sss(word2);
					channel = word2[(ff == 0) ? 3 : 2] - '0';
					AOChannels.push_back(channel);
				}
				cout << "AO Channels: ";
				for (int ff = 0; ff < AOChannels.size(); ff++) {
					cout << AOChannels[ff] << " ";
				}
				cout << endl;
			}
		}
		else if (member == "numDOChannels") {
			sstream >> numDOChannels;
			cout << "Number of DO Channels: " << numDOChannels << endl;
		}
		else if (member == "DOPort") {
			sstream >> DOPort;
			cout << "DO Port: " << DOPort << endl;
		}
		else if (member == "DOChannels") {
			if (numDOChannels > 0) {
				int channel = 0;
				string word2;
				for (int ff = 0; ff < numDOChannels; ff++) {
					getline(sstream, word2, ',');
					stringstream sss(word2);
					channel = word2[(ff == 0) ? 5 : 4] - '0';
					DOChannels.push_back(channel);
				}
				cout << "DO Channels: ";
				for (int ff = 0; ff < DOChannels.size(); ff++) {
					cout << DOChannels[ff] << " ";
				}
				cout << endl;
			}
		}
		else {
			cout << "protocol  member not recognised: " << member << endl;
			MessageBoxA(NULL, "Protocol member not recognised", "NIDAQ error", MB_OK);

		}
	}
	if (numDOChannels > 0 && numAOChannels > 0) {
		stringstream sstream(v_lines[12]);
		string member;
		sstream >> member;
		if (member == "AODATA") {
			cout << "AODATA" << endl;
			dataAO = new double[numSamples * numAOChannels];
			dataAO_back = new double[numSamples * numAOChannels];
			int fcnt = 0;
			for (int f = 13; f < 13 + numSamples; f++) {
				stringstream ss(v_lines[f]);
				string word2;
				while (getline(ss, word2, ',')) {
					stringstream sss(word2);
					sss >> dataAO[fcnt];
					fcnt++;
				}
			}
			memcpy(dataAO_back, dataAO, numSamples * numAOChannels * sizeof(double));

			//int ccc = 0;
			//for (int f = 0; f < numSamples; f++) {
			//	for (int ff = 0; ff < numAOChannels; ff++) {
			//		cout << dataAO[ccc] << " ";
			//		ccc++;
			//	}
			//	cout << endl;
			//}
		}
		stringstream sstream1(v_lines[13 + numSamples]);
		sstream1 >> member;
		if (member == "DODATA") {
			cout << "DODATA" << endl;
			dataDO = new uInt32[numSamples * numDOChannels];
			dataDO_back = new uInt32[numSamples * numDOChannels];
			int fcnt = 0;
			for (int f = 14 + numSamples; f < 14 + 2 * numSamples; f++) {
				stringstream ss(v_lines[f]);
				string word2;
				int cnt = 0;
				while (getline(ss, word2, ',')) {
					stringstream sss(word2);
					sss >> dataDO[fcnt];
					//Each binary value and needs to be changed to a power of 2
					dataDO[fcnt] *= (uInt32)pow(2, DOChannels[cnt]);
					fcnt++;
					cnt++;
				}
			}
			memcpy(dataDO_back, dataDO, numSamples * numDOChannels * sizeof(uInt32));

			//int ccc = 0;
			//for (int f = 0; f < numSamples; f++) {
			//	for (int ff = 0; ff < numDOChannels; ff++) {
			//		cout << dataDO[ccc] << " ";
			//		ccc++;
			//	}
			//	cout << endl;
			//}

		}
	}
	else if (numDOChannels > 0) {
		stringstream sstream(v_lines[12]);
		string member;
		sstream >> member;
		if (member == "DODATA") {
			dataDO = new uInt32[numSamples * numDOChannels];
			dataDO_back = new uInt32[numSamples * numDOChannels];
			int fcnt = 0;
			for (int f = 13; f < 13 + numSamples; f++) {
				stringstream ss(v_lines[f]);
				string word2;
				int cnt = 0;
				while (getline(ss, word2, ',')) {
					stringstream sss(word2);
					sss >> dataDO[fcnt];
					//Each binary value and needs to be changed to a power of 2
					dataDO[fcnt] *= (uInt32)pow(2, DOChannels[cnt]);
					fcnt++;
					cnt++;
				}
			}


			memcpy(dataDO_back, dataDO, numSamples * numDOChannels * sizeof(uInt32));
		}
	}
	else if (numAOChannels > 0) {
		stringstream sstream(v_lines[12]);
		string member;
		sstream >> member;
		if (member == "AODATA") {
			dataAO = new double[numSamples * numAOChannels];
			dataAO_back = new double[numSamples * numAOChannels];
			int fcnt = 0;
			for (int f = 13; f < 13 + numSamples; f++) {
				stringstream ss(v_lines[f]);
				string word2;
				while (getline(ss, word2, ',')) {
					stringstream sss(word2);
					sss >> dataAO[fcnt];
					fcnt++;
				}
			}


			memcpy(dataAO_back, dataAO, numSamples * numAOChannels * sizeof(double));
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
	* 
	* at this point the NIDAQ is ready to go and start issuing and reading samples.
	* 
	
	
	
	
	*/

	if (bDebug) {
		(*pOfs) << "SimpleProtocol::InitDAQ" << endl;
	}
	char buffer[2000] = { 0 };
	int errorCode = 0;

	stringstream ssError;
	ssError << szDeviceName << deviceID << " NIDAQ error";


	bool bUsingAO = numAOChannels > 0;
	bool bUsingAI = numAIChannels > 0;
	bool bUsingDO = numDOChannels > 0;
	bool bUsingDI = numDIChannels > 0;


	if (bUsingDO) {
		//cout << "create DO task" << endl;
		errorCode = DAQmxCreateTask("", &DOtaskHandle);
		if (errorCode < 0) {
			DAQmxGetErrorString(errorCode, buffer, 2000);
			//MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
			return false;
		}
	}
	if (bUsingDI) {
		//cout << "create DI task" << endl;
		errorCode = DAQmxCreateTask("", &DItaskHandle);
		if (errorCode < 0) {
			DAQmxGetErrorString(errorCode, buffer, 2000);
			//MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
			return false;
		}
	}
	if (bUsingAO) {
		errorCode = DAQmxCreateTask("", &AOtaskHandle);
		if (errorCode < 0) {
			DAQmxGetErrorString(errorCode, buffer, 2000);
			//MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
			return false;
		}

	}
	if (bUsingAI) {
		errorCode = DAQmxCreateTask("", &AItaskHandle);
		if (errorCode < 0) {
			DAQmxGetErrorString(errorCode, buffer, 2000);
			//MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
			return false;
		}
	}


	////load channels
	for (int f = 0; f < numAIChannels; f++) {
		std::stringstream ss;
		ss << szDeviceName << deviceID << "/ai" << AIChannels[f];
		errorCode = DAQmxCreateAIVoltageChan(AItaskHandle, ss.str().c_str(), "", DAQmx_Val_RSE, minV, maxV, DAQmx_Val_Volts, NULL);
		if (errorCode < 0) {
			DAQmxGetErrorString(errorCode, buffer, 2000);
			//MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
			return false;
		}
	}

	for (int f = 0; f < numAOChannels; f++) {
		std::stringstream ss;
		ss << szDeviceName << deviceID << "/ao" << AOChannels[f];
		errorCode = DAQmxCreateAOVoltageChan(AOtaskHandle, ss.str().c_str(), "", minV, maxV, DAQmx_Val_Volts, NULL);
		if (errorCode < 0) {
			DAQmxGetErrorString(errorCode, buffer, 2000);
			//MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
			return false;
		}
	}

	for (int f = 0; f < numDOChannels; f++) {
		std::stringstream ssd;
		//cout << "create DO channel" << endl;
		ssd << szDeviceName << deviceID << "/port" << DOPort << "/line" << DOChannels[f];
		errorCode = DAQmxCreateDOChan(DOtaskHandle, ssd.str().c_str(), "", DAQmx_Val_ChanPerLine);
		if (errorCode < 0) {
			DAQmxGetErrorString(errorCode, buffer, 2000);
			//MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
			return false;
		}
	}

	for (int f = 0; f < numDIChannels; f++) {
		std::stringstream ssd;
		//cout << "create DI channel" << endl;
		ssd << szDeviceName << deviceID << "/port" << DIPort << "/line" << DIChannels[f];
		errorCode = DAQmxCreateDIChan(DItaskHandle, ssd.str().c_str(), "", DAQmx_Val_ChanPerLine);
		if (errorCode < 0) {
			DAQmxGetErrorString(errorCode, buffer, 2000);
			//MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
			return false;
		}
	}

	////cfg clocks
	if (bUsingAI) {
		errorCode = DAQmxCfgSampClkTiming(AItaskHandle, "", sampleFreq, DAQmx_Val_Rising, DAQmx_Val_FiniteSamps, numSamples);
		if (errorCode < 0) {
			DAQmxGetErrorString(errorCode, buffer, 2000);
			//MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
			return false;
		}
	}
	if (bUsingAO) {
		errorCode = DAQmxCfgSampClkTiming(AOtaskHandle, "", sampleFreq, DAQmx_Val_Rising, DAQmx_Val_FiniteSamps, numSamples);
		if (errorCode < 0) {
			DAQmxGetErrorString(errorCode, buffer, 2000);
			//MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
			return false;
		}
	}
	if (bUsingDO) {
		//cout << "DO sample clk" << endl;
		errorCode = DAQmxCfgSampClkTiming(DOtaskHandle, "", sampleFreq, DAQmx_Val_Rising, DAQmx_Val_FiniteSamps, numSamples);
		if (errorCode < 0) {
			DAQmxGetErrorString(errorCode, buffer, 2000);
			//MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
			return false;
		}
	}

	if (bUsingDI) {
		//cout << "DI sample clock" << endl;
		errorCode = DAQmxCfgSampClkTiming(DItaskHandle, "", sampleFreq, DAQmx_Val_Rising, DAQmx_Val_FiniteSamps, numSamples);
		if (errorCode < 0) {
			DAQmxGetErrorString(errorCode, buffer, 2000);
			//MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
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
			//MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
			return false;
		}
		errorCode = DAQmxCfgDigEdgeStartTrig(DOtaskHandle, ss4.str().c_str(), DAQmx_Val_Rising);
		if (errorCode < 0) {
			DAQmxGetErrorString(errorCode, buffer, 2000);
			//MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
			return false;
		}
		errorCode = DAQmxCfgDigEdgeStartTrig(AItaskHandle, ss4.str().c_str(), DAQmx_Val_Rising);
		if (errorCode < 0) {
			DAQmxGetErrorString(errorCode, buffer, 2000);
			//MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
			return false;
		}
	}
	else if (bUsingAO && bUsingAI && bUsingDO && !bUsingDI) {
		//ao/StartTrigger is the master trigger
		ss4 << "/Dev" << deviceID << "/ao/StartTrigger";
		errorCode = DAQmxCfgDigEdgeStartTrig(DOtaskHandle, ss4.str().c_str(), DAQmx_Val_Rising);
		if (errorCode < 0) {
			DAQmxGetErrorString(errorCode, buffer, 2000);
			//MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
			return false;
		}
		errorCode = DAQmxCfgDigEdgeStartTrig(AItaskHandle, ss4.str().c_str(), DAQmx_Val_Rising);
		if (errorCode < 0) {
			DAQmxGetErrorString(errorCode, buffer, 2000);
			//MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
			return false;
		}
	}
	else if (bUsingAO && bUsingAI && !bUsingDO && bUsingDI) {
		//using ao/StartTrigger
		ss4 << "/Dev" << deviceID << "/ao/StartTrigger";
		errorCode = DAQmxCfgDigEdgeStartTrig(DItaskHandle, ss4.str().c_str(), DAQmx_Val_Rising);
		if (errorCode < 0) {
			DAQmxGetErrorString(errorCode, buffer, 2000);
			//MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
			return false;
		}
		errorCode = DAQmxCfgDigEdgeStartTrig(AItaskHandle, ss4.str().c_str(), DAQmx_Val_Rising);
		if (errorCode < 0) {
			DAQmxGetErrorString(errorCode, buffer, 2000);
			//MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
			return false;
		}
	}
	else if (bUsingAO && bUsingAI && !bUsingDO && !bUsingDI) {
		//using ao/StartTrigger
		ss4 << "/Dev" << deviceID << "/ao/StartTrigger";
		errorCode = DAQmxCfgDigEdgeStartTrig(AItaskHandle, ss4.str().c_str(), DAQmx_Val_Rising);
		if (errorCode < 0) {
			DAQmxGetErrorString(errorCode, buffer, 2000);
			//MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
			return false;
		}
	}
	else if (bUsingAO && !bUsingAI && bUsingDO && bUsingDI) {
		//using ao/StartTrigger
		ss4 << "/Dev" << deviceID << "/ao/StartTrigger";
		errorCode = DAQmxCfgDigEdgeStartTrig(DItaskHandle, ss4.str().c_str(), DAQmx_Val_Rising);
		if (errorCode < 0) {
			DAQmxGetErrorString(errorCode, buffer, 2000);
			//MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
			return false;
		}
		errorCode = DAQmxCfgDigEdgeStartTrig(DOtaskHandle, ss4.str().c_str(), DAQmx_Val_Rising);
		if (errorCode < 0) {
			DAQmxGetErrorString(errorCode, buffer, 2000);
			//MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
			return false;
		}
	}
	else if (bUsingAO && !bUsingAI && bUsingDO && !bUsingDI) {
		//using ao/StartTrigger
		ss4 << "/Dev" << deviceID << "/ao/StartTrigger";
		errorCode = DAQmxCfgDigEdgeStartTrig(DOtaskHandle, ss4.str().c_str(), DAQmx_Val_Rising);
		if (errorCode < 0) {
			DAQmxGetErrorString(errorCode, buffer, 2000);
			//MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
			return false;
		}
	}
	else if (!bUsingAO && bUsingAI && bUsingDO && bUsingDI) {
		//using do/StartTrigger
		ss4 << "/Dev" << deviceID << "/do/StartTrigger";
		errorCode = DAQmxCfgDigEdgeStartTrig(DItaskHandle, ss4.str().c_str(), DAQmx_Val_Rising);
		if (errorCode < 0) {
			DAQmxGetErrorString(errorCode, buffer, 2000);
			//MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
			return false;
		}
		errorCode = DAQmxCfgDigEdgeStartTrig(AItaskHandle, ss4.str().c_str(), DAQmx_Val_Rising);
		if (errorCode < 0) {
			DAQmxGetErrorString(errorCode, buffer, 2000);
			//MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
			return false;
		}
	}
	else if (!bUsingAO && bUsingAI && bUsingDO && !bUsingDI) {
		//using do/StartTrigger
		ss4 << "/Dev" << deviceID << "/do/StartTrigger";
		errorCode = DAQmxCfgDigEdgeStartTrig(AItaskHandle, ss4.str().c_str(), DAQmx_Val_Rising);
		if (errorCode < 0) {
			DAQmxGetErrorString(errorCode, buffer, 2000);
			//MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
			return false;
		}
	}
	else if (bUsingAO && !bUsingAI && !bUsingDO && bUsingDI) {
		//using ao/StartTrigger
		ss4 << "/Dev" << deviceID << "/ao/StartTrigger";
		errorCode = DAQmxCfgDigEdgeStartTrig(DItaskHandle, ss4.str().c_str(), DAQmx_Val_Rising);
		if (errorCode < 0) {
			DAQmxGetErrorString(errorCode, buffer, 2000);
			//MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
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
			//MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
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
			//MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
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
				//MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
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
				//MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
				return false;
			}
		}

	}
	if (bUsingDO) {
		if (bDirtyDO) {
			errorCode = DAQmxWriteDigitalU32(DOtaskHandle, numSamples, 0, DAQmx_Val_WaitInfinitely, DAQmx_Val_GroupByScanNumber, dataDO_back, NULL, NULL);
			if (errorCode < 0) {
				DAQmxGetErrorString(errorCode, buffer, 2000);
				//MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
				return false;
			}
		}
		else
		{
			//cout << "DO write" << endl;
			errorCode = DAQmxWriteDigitalU32(DOtaskHandle, numSamples, 0, DAQmx_Val_WaitInfinitely, DAQmx_Val_GroupByScanNumber, dataDO, NULL, NULL);
			if (errorCode < 0) {
				DAQmxGetErrorString(errorCode, buffer, 2000);
				//MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
				return false;
			}
		}
	}
	return true;
}

DWORD WINAPI MyThreadFunction(LPVOID lpParam)
{
	ThreadData* pTd = (ThreadData*)lpParam;
	pTd->pSimple->bReturn = pTd->pSimple->DAQRunThread(pTd->pTimeSec, pTd->pDataAO, pTd->pDataAI, pTd->pDataDO, pTd->pDataDI, pTd->bStartTrigger, pTd->bRisingEdge, pTd->pFIx, pTd->timeOutSec);

	return 0;
}

bool SimpleProtocol::DAQRunThread(double* pTimeSec, double* pDataAO, double* pDataAI, uInt32* pDataDO, uInt32* pDataDI, bool bStartTrigger, bool bRisingEdge, uInt32 pFIx, double timeoutSec) {


	//cout<<"DAQRunThread"<<endl;
	// 
	// 
	// 
	// 
	// 
	// 
	// 
	// 
	// 
	// 
	// 
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



	this->bStartTrigger = bStartTrigger;
	int32 triggerType = (bRisingEdge) ? DAQmx_Val_Rising : DAQmx_Val_Falling;

	this->pFIx = pFIx;
	std::stringstream ssTrig;
	ssTrig << "/" << szDeviceName << deviceID << "/PFI" << pFIx;

	stringstream ssError;
	ssError << szDeviceName << deviceID << " NIDAQ error";

	////Start protocol ///////////////////////////////////////////
	if (bUsingAO && bUsingAI && bUsingDO && bUsingDI) {
		//start everything before AO
		errorCode = DAQmxStartTask(DItaskHandle);
		if (errorCode < 0) {
			DAQmxGetErrorString(errorCode, buffer, 2000);
			//MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
			return false;
		}
		errorCode = DAQmxStartTask(AItaskHandle);
		if (errorCode < 0) {
			DAQmxGetErrorString(errorCode, buffer, 2000);
			//MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
			return false;
		}
		errorCode = DAQmxStartTask(DOtaskHandle);
		if (errorCode < 0) {
			DAQmxGetErrorString(errorCode, buffer, 2000);
			//MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
			return false;
		}
		if (bStartTrigger) {
			errorCode = DAQmxCfgDigEdgeStartTrig(AOtaskHandle, ssTrig.str().c_str(), triggerType);
			if (errorCode < 0) {
				DAQmxGetErrorString(errorCode, buffer, 2000);
				//MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
				return false;
			}
		}
		errorCode = DAQmxStartTask(AOtaskHandle);
		if (errorCode < 0) {
			DAQmxGetErrorString(errorCode, buffer, 2000);
			//MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
			return false;
		}
	}
	else if (bUsingAO && bUsingAI && bUsingDO && !bUsingDI) {
		//start everything before AO
		errorCode = DAQmxStartTask(AItaskHandle);
		if (errorCode < 0) {
			DAQmxGetErrorString(errorCode, buffer, 2000);
			//MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
			return false;
		}
		errorCode = DAQmxStartTask(DOtaskHandle);
		if (errorCode < 0) {
			DAQmxGetErrorString(errorCode, buffer, 2000);
			//MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
			return false;
		}
		if (bStartTrigger) {
			errorCode = DAQmxCfgDigEdgeStartTrig(AOtaskHandle, ssTrig.str().c_str(), triggerType);
			if (errorCode < 0) {
				DAQmxGetErrorString(errorCode, buffer, 2000);
				//MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
				return false;
			}
		}
		errorCode = DAQmxStartTask(AOtaskHandle);
		if (errorCode < 0) {
			DAQmxGetErrorString(errorCode, buffer, 2000);
			//MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
			return false;
		}
	}
	else if (bUsingAO && bUsingAI && !bUsingDO && bUsingDI) {
		errorCode = DAQmxStartTask(DItaskHandle);
		if (errorCode < 0) {
			DAQmxGetErrorString(errorCode, buffer, 2000);
			//MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
			return false;
		}
		errorCode = DAQmxStartTask(AItaskHandle);
		if (errorCode < 0) {
			DAQmxGetErrorString(errorCode, buffer, 2000);
			//MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
			return false;
		}
		if (bStartTrigger) {
			errorCode = DAQmxCfgDigEdgeStartTrig(AOtaskHandle, ssTrig.str().c_str(), triggerType);
			if (errorCode < 0) {
				DAQmxGetErrorString(errorCode, buffer, 2000);
				//MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
				return false;
			}
		}
		errorCode = DAQmxStartTask(AOtaskHandle);
		if (errorCode < 0) {
			DAQmxGetErrorString(errorCode, buffer, 2000);
			//MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
			return false;
		}
	}
	else if (bUsingAO && bUsingAI && !bUsingDO && !bUsingDI) {
		errorCode = DAQmxStartTask(AItaskHandle);
		if (errorCode < 0) {
			DAQmxGetErrorString(errorCode, buffer, 2000);
			//MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
			return false;
		}
		if (bStartTrigger) {
			errorCode = DAQmxCfgDigEdgeStartTrig(AOtaskHandle, ssTrig.str().c_str(), triggerType);
			if (errorCode < 0) {
				DAQmxGetErrorString(errorCode, buffer, 2000);
				//MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
				return false;
			}
		}
		errorCode = DAQmxStartTask(AOtaskHandle);
		if (errorCode < 0) {
			DAQmxGetErrorString(errorCode, buffer, 2000);
			//MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
			return false;
		}
	}
	else if (bUsingAO && !bUsingAI && bUsingDO && bUsingDI) {
		errorCode = DAQmxStartTask(DItaskHandle);
		if (errorCode < 0) {
			DAQmxGetErrorString(errorCode, buffer, 2000);
			//MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
			return false;
		}
		errorCode = DAQmxStartTask(DOtaskHandle);
		if (errorCode < 0) {
			DAQmxGetErrorString(errorCode, buffer, 2000);
			//MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
			return false;
		}
		if (bStartTrigger) {
			errorCode = DAQmxCfgDigEdgeStartTrig(AOtaskHandle, ssTrig.str().c_str(), triggerType);
			if (errorCode < 0) {
				DAQmxGetErrorString(errorCode, buffer, 2000);
				//MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
				return false;
			}
		}
		errorCode = DAQmxStartTask(AOtaskHandle);
		if (errorCode < 0) {
			DAQmxGetErrorString(errorCode, buffer, 2000);
			//MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
			return false;
		}
	}
	else if (!bUsingAO && bUsingAI && bUsingDO && !bUsingDI) {
		errorCode = DAQmxStartTask(AItaskHandle);
		if (errorCode < 0) {
			DAQmxGetErrorString(errorCode, buffer, 2000);
			//MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
			return false;
		}
		if (bStartTrigger) {
			errorCode = DAQmxCfgDigEdgeStartTrig(DOtaskHandle, ssTrig.str().c_str(), triggerType);
			if (errorCode < 0) {
				DAQmxGetErrorString(errorCode, buffer, 2000);
				//MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
				return false;
			}
		}
		errorCode = DAQmxStartTask(DOtaskHandle);
		if (errorCode < 0) {
			DAQmxGetErrorString(errorCode, buffer, 2000);
			//MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
			return false;
		}
	}
	else if (bUsingAO && !bUsingAI && !bUsingDO && bUsingDI) {
		errorCode = DAQmxStartTask(DItaskHandle);
		if (errorCode < 0) {
			DAQmxGetErrorString(errorCode, buffer, 2000);
			//MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
			return false;
		}
		if (bStartTrigger) {
			errorCode = DAQmxCfgDigEdgeStartTrig(AOtaskHandle, ssTrig.str().c_str(), triggerType);
			if (errorCode < 0) {
				DAQmxGetErrorString(errorCode, buffer, 2000);
				//MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
				return false;
			}
		}
		errorCode = DAQmxStartTask(AOtaskHandle);
		if (errorCode < 0) {
			DAQmxGetErrorString(errorCode, buffer, 2000);
			//MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
			return false;
		}
	}
	else if (bUsingAO && !bUsingAI && !bUsingDO && !bUsingDI) {
		if (bStartTrigger) {
			errorCode = DAQmxCfgDigEdgeStartTrig(AOtaskHandle, ssTrig.str().c_str(), triggerType);
			if (errorCode < 0) {
				DAQmxGetErrorString(errorCode, buffer, 2000);
				//MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
				return false;
			}
		}
		errorCode = DAQmxStartTask(AOtaskHandle);
		if (errorCode < 0) {
			DAQmxGetErrorString(errorCode, buffer, 2000);
			//MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
			return false;
		}
		errorCode = DAQmxWaitUntilTaskDone(AOtaskHandle, DAQmx_Val_WaitInfinitely);
		if (errorCode < 0) {
			DAQmxGetErrorString(errorCode, buffer, 2000);
			//MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
			return false;
		}
	}
	else if (!bUsingAO && bUsingAI && !bUsingDO && bUsingDI) {
		errorCode = DAQmxStartTask(DItaskHandle);
		if (errorCode < 0) {
			DAQmxGetErrorString(errorCode, buffer, 2000);
			//MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
			return false;
		}
		if (bStartTrigger) {
			errorCode = DAQmxCfgDigEdgeStartTrig(AItaskHandle, ssTrig.str().c_str(), triggerType);
			if (errorCode < 0) {
				DAQmxGetErrorString(errorCode, buffer, 2000);
				//MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
				return false;
			}
		}
		errorCode = DAQmxStartTask(AItaskHandle);
		if (errorCode < 0) {
			DAQmxGetErrorString(errorCode, buffer, 2000);
			//MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
			return false;
		}
	}
	else if (!bUsingAO && bUsingAI && !bUsingDO && !bUsingDI) {
		//no trigger
		if (bStartTrigger) {
			errorCode = DAQmxCfgDigEdgeStartTrig(AItaskHandle, ssTrig.str().c_str(), triggerType);
			if (errorCode < 0) {
				DAQmxGetErrorString(errorCode, buffer, 2000);
				//MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
				return false;
			}
		}
		errorCode = DAQmxStartTask(AItaskHandle);
		if (errorCode < 0) {
			DAQmxGetErrorString(errorCode, buffer, 2000);
			//MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
			return false;
		}
	}
	else if (!bUsingAO && !bUsingAI && bUsingDO && bUsingDI) {
		//no trigger
		//cout << "DI start task" << endl;////////////////////////////////////////////////IS THIS CORRECT?
		errorCode = DAQmxStartTask(DItaskHandle);
		if (errorCode < 0) {
			DAQmxGetErrorString(errorCode, buffer, 2000);
			//MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
			return false;  
		}
		if (bStartTrigger) {
			errorCode = DAQmxCfgDigEdgeStartTrig(DOtaskHandle, ssTrig.str().c_str(), triggerType);
			if (errorCode < 0) {
				DAQmxGetErrorString(errorCode, buffer, 2000);
				//MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
				return false;
			}
		}
		errorCode = DAQmxStartTask(DOtaskHandle);
		if (errorCode < 0) {
			DAQmxGetErrorString(errorCode, buffer, 2000);
			//MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
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
		//cout << "**1" << endl;
		errorCode = DAQmxStartTask(DOtaskHandle);
		if (errorCode < 0) {
			DAQmxGetErrorString(errorCode, buffer, 2000);
			//MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
			return false;
		}
		//cout << "**2" << endl;
		errorCode = DAQmxWaitUntilTaskDone(DOtaskHandle, DAQmx_Val_WaitInfinitely);
		if (errorCode < 0) {
			DAQmxGetErrorString(errorCode, buffer, 2000);
			//MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
			return false;
		}
	}
	else if (!bUsingAO && !bUsingAI && !bUsingDO && bUsingDI) {
		//no trigger
		if (bStartTrigger) {
			errorCode = DAQmxCfgDigEdgeStartTrig(DItaskHandle, ssTrig.str().c_str(), triggerType);
			if (errorCode < 0) {
				DAQmxGetErrorString(errorCode, buffer, 2000);
				//MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
				return false;
			}
		}
		errorCode = DAQmxStartTask(DItaskHandle);
		if (errorCode < 0) {
			DAQmxGetErrorString(errorCode, buffer, 2000);
			//MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
			return false;
		}
	}
	else if (bUsingAO && !bUsingAI && bUsingDO && !bUsingDI) {
	

		errorCode = DAQmxStartTask(DOtaskHandle);
		if (errorCode < 0) {
			DAQmxGetErrorString(errorCode, buffer, 2000);
			//MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
			return false;
		}

		if (bStartTrigger) {
			errorCode = DAQmxCfgDigEdgeStartTrig(AOtaskHandle, ssTrig.str().c_str(), triggerType);
			if (errorCode < 0) {
				DAQmxGetErrorString(errorCode, buffer, 2000);
				//MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
				return false;
			}
		}
		errorCode = DAQmxStartTask(AOtaskHandle);
		if (errorCode < 0) {
			DAQmxGetErrorString(errorCode, buffer, 2000);
			//MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
			return false;
		}
		errorCode = DAQmxWaitUntilTaskDone(AOtaskHandle, DAQmx_Val_WaitInfinitely);
		if (errorCode < 0) {
			DAQmxGetErrorString(errorCode, buffer, 2000);
			//MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
			return false;
		}



	}
	else if (!bUsingAO && bUsingAI && bUsingDO && bUsingDI) {
		
		//no trigger
		errorCode = DAQmxStartTask(DItaskHandle);
		if (errorCode < 0) {
			DAQmxGetErrorString(errorCode, buffer, 2000);
			//MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
			return false;
		}
		errorCode = DAQmxStartTask(AItaskHandle);
		if (errorCode < 0) {
			DAQmxGetErrorString(errorCode, buffer, 2000);
			//MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
			return false;
		}
		if (bStartTrigger) {
			errorCode = DAQmxCfgDigEdgeStartTrig(DOtaskHandle, ssTrig.str().c_str(), triggerType);
			if (errorCode < 0) {
				DAQmxGetErrorString(errorCode, buffer, 2000);
				//MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
				return false;
			}
		}
		errorCode = DAQmxStartTask(DOtaskHandle);
		if (errorCode < 0) {
			DAQmxGetErrorString(errorCode, buffer, 2000);
			//MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
			return false;
		}
	}
	else {
		cout << "ERROR - can be !AO, !AI and !DO" << endl;
	}



	if (bUsingAI) {
		int32 read = 0;
		errorCode = DAQmxReadAnalogF64(AItaskHandle, DAQmx_Val_Auto, timeoutSec, DAQmx_Val_GroupByScanNumber, pDataAI, numSamples * numAIChannels, (int32*)&read, NULL);
		if (errorCode < 0) {
			DAQmxGetErrorString(errorCode, buffer, 2000);
			//MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
			return false;
		}
	}

	if (bUsingDI) {
		int32 read = 0;
		errorCode = DAQmxReadDigitalU32(DItaskHandle, DAQmx_Val_Auto, timeoutSec, DAQmx_Val_GroupByScanNumber, pDataDI, numSamples * numDIChannels, (int32*)&read, NULL);
		if (errorCode < 0) {
			DAQmxGetErrorString(errorCode, buffer, 2000);
			//MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
			return false;
		}
	}

	LARGE_INTEGER li;
	QueryPerformanceCounter(&li);
	double endTime = double(li.QuadPart) / PCFreq / 1000.0; // start time in Sec

	if (bUsingAI) {
		errorCode = DAQmxStopTask(AItaskHandle);
		if (errorCode < 0) {
			DAQmxGetErrorString(errorCode, buffer, 2000);
			//MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
			return false;
		}
	}
	if (bUsingDO) {
		//cout << "**3" << endl;
		errorCode = DAQmxStopTask(DOtaskHandle);
		if (errorCode < 0) {
			//MessageBoxA(NULL, "", "", MB_OK);
			DAQmxGetErrorString(errorCode, buffer, 2000);
			//MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
			return false;
		}
	}
	if (bUsingAO) {
		errorCode = DAQmxStopTask(AOtaskHandle);
		if (errorCode < 0) {
			DAQmxGetErrorString(errorCode, buffer, 2000);
			//MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
			return false;
		}
	}
	if (bUsingDI) {
		errorCode = DAQmxStopTask(DItaskHandle);
		if (errorCode < 0) {
			DAQmxGetErrorString(errorCode, buffer, 2000);
			//MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
			return false;
		}
	}



	for (int f = 0; f < numSamples; f++) {
		pTimeSec[f] = endTime - (numSamples - f)/ sampleFreq;
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

	if (bDebug) {
		(*pOfs) << "SimpleProtocol::RunProtocolDAQ" << endl;
	}



	//run in a thread and store the handle statically
	//
	DWORD dwID = 0;

	pThreadData->pSimple = this;
	pThreadData->bRisingEdge = bRisingEdge;
	pThreadData->bStartTrigger = bStartTrigger;
	pThreadData->pDataAI = pDataAI;
	pThreadData->pDataAO = pDataAO;
	pThreadData->pDataDI = pDataDI;
	pThreadData->pDataDO = pDataDO;
	pThreadData->pFIx = pFIx;
	pThreadData->pTimeSec = pTimeSec;
	pThreadData->timeOutSec = timeoutSec;

	if (!bTerminate) {
		threadHandle = CreateThread(NULL, 0, MyThreadFunction, pThreadData, 0, &dwID);
		WaitForSingleObject(threadHandle, INFINITE);
		//cout << "done with thread proc" << endl;
	}
	else {
		Sleep(200);

	}



	return bReturn;
}
bool	SimpleProtocol::ReleaseDAQ() {
/*
* Clear all of the daqmx tasks - which destroys the resources held by the NIDAQ



*/

	if (bDebug) {
		(*pOfs) << "SimpleProtocol::ReleaseDAQ" << endl;
	}

	if (!bTerminate) {
		char buffer[2000] = { 0 };
		int errorCode = 0;
		stringstream ssError;
		ssError << szDeviceName << deviceID << " NIDAQ error";

		if (numAIChannels > 0) {
			errorCode = DAQmxClearTask(AItaskHandle);
			if (errorCode < 0) {
				DAQmxGetErrorString(errorCode, buffer, 2000);
				//MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
				return false;
			}
		}
		if (numDOChannels > 0) {
			errorCode = DAQmxClearTask(DOtaskHandle);
			if (errorCode < 0) {
				DAQmxGetErrorString(errorCode, buffer, 2000);
				//MessageBoxA(NULL, buffer, "NIDAQ error", MB_OK);
				return false;
			}
		}
		if (numAOChannels > 0) {
			errorCode = DAQmxClearTask(AOtaskHandle);
			if (errorCode < 0) {
				DAQmxGetErrorString(errorCode, buffer, 2000);
				//MessageBoxA(NULL, buffer, "NIDAQ error", MB_OK);
				return false;
			}
		}
		if (numDIChannels > 0) {
			errorCode = DAQmxClearTask(DItaskHandle);
			if (errorCode < 0) {
				DAQmxGetErrorString(errorCode, buffer, 2000);
				//MessageBoxA(NULL, buffer, "NIDAQ error", MB_OK);
				return false;
			}
		}
	}
		return true;
}
bool	SimpleProtocol::TerminateProtocol() {
	if (bDebug) {
		(*pOfs) << "SimpleProtocol::TerminateProtocol" << endl;
	}

	bool bUsingAO = numAOChannels > 0;
	bool bUsingAI = numAIChannels > 0;
	bool bUsingDO = numDOChannels > 0;
	bool bUsingDI = numDIChannels > 0;

	bTerminate = true;
	//TerminateThread(threadHandle, 0);
	cout << "about to DAQStopTask" << endl;
	//TerminateThread(threadHandle, 0);
	 //DAQmxStopTask(DOtaskHandle);
	if (bUsingDO) DAQmxTaskControl(DOtaskHandle, DAQmx_Val_Task_Abort);
	if (bUsingDI) DAQmxTaskControl(DItaskHandle, DAQmx_Val_Task_Abort);
	if (bUsingAO) DAQmxTaskControl(AOtaskHandle, DAQmx_Val_Task_Abort);
	if (bUsingAI) DAQmxTaskControl(AItaskHandle, DAQmx_Val_Task_Abort);
	 cout << "after DAQStopTask" << endl;
	 
	

	return true;
}

double	SimpleProtocol::GetCurrentRunSampleTime() {

	if (bDebug) {
		(*pOfs) << "SimpleProtocol::GetCurrentRunSampleTime" << endl;
	}
	return 1000.0 / sampleFreq; // in ms
}
int		SimpleProtocol::GetNumSamples() {
	if (bDebug) {
		(*pOfs) << "SimpleProtocol::GetNumSamples" << endl;
	}
	return numSamples;
}
double	SimpleProtocol::GetSampleFreq() {
	if (bDebug) {
		(*pOfs) << "SimpleProtocol::GetSampleFreq" << endl;
	}
	return sampleFreq;
}


int		SimpleProtocol::GetNumChannels(int type) {
	if (bDebug) {
		(*pOfs) << "SimpleProtocol::GetNumChannels" << endl;
	}
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
	if (bDebug) {
		(*pOfs) << "SimpleProtocol::GetIndexFromChannel" << endl;
	}
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
	if (bDebug) {
		(*pOfs) << "SimpleProtocol::GetChannelFromIndex" << endl;
	}
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
	if (bDebug) {
		(*pOfs) << "SimpleProtocol::GetPort" << endl;
	}
	if (bIn) {
		return DIPort;
	}
	else {
		return DOPort;
	}
}
int		SimpleProtocol::GetDeviceID() {
	if (bDebug) {
		(*pOfs) << "SimpleProtocol::GetDeviceID()" << endl;
	}
	return deviceID;
}


