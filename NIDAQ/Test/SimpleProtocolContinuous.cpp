#include "SimpleProtocolContinuous.h"
#include "ProtocolBase.h"

void SimpleProtocolContinuous::con(int in, int x, int y)
{
	HDC dc = GetDC(NULL);
	char buff[100] = { 0 };
	_ltoa_s(in, buff, 10);
	TextOutA(dc, x, y, "            ", 7);
	TextOutA(dc, x, y, buff, (int)strlen(buff));
	ReleaseDC(NULL, dc);
}
void SimpleProtocolContinuous::con(string in, int x, int y)
{
	HDC dc = GetDC(NULL);
	TextOutA(dc, x, y, "            ", 7);
	TextOutA(dc, x, y, in.c_str(), (int)in.length());
	ReleaseDC(NULL, dc);
}
bool SimpleProtocolContinuous::bDebug = true;

SimpleProtocolContinuous::SimpleProtocolContinuous() {
	bShutdown = false;
	bReadyToShutdown = false;
	bDebug = true;
	bError = bStartTrigger = bFirst = false;
	deviceID = 0;
	minV = -10.0;
	maxV = 10.0;
	sampleFreq = 1000.0;
	DIPort = DOPort = 0;
	pFIx = 0;
	pDataAI = NULL;
	pDataAO = NULL;
	pDataDI = NULL;
	pDataDO = NULL;
	bGotData = false;
	curTotalTime = 0.0;
	timeoutSec = 0.0;
	numAIChannels = numAOChannels = numDIChannels = numDOChannels = 0;
	AItaskHandle = AOtaskHandle = DOtaskHandle = DItaskHandle = 0;
	numSamples = 0;
}

SimpleProtocolContinuous::~SimpleProtocolContinuous() {

}

bool	SimpleProtocolContinuous::Init(int deviceID, char* szCsv, bool bStartTrigger, bool bRisingEdge, uInt32 pFIx, double timeoutSec, double minV, double maxV) {
	ccout("Init");
	ccout("set variables");
	bShutdown = false;
	bReadyToShutdown = true;

	this->timeoutSec = timeoutSec;
	this->deviceID = deviceID;
	this->minV = minV;
	this->maxV = maxV;
	this->bStartTrigger = bStartTrigger;
	this->pFIx = pFIx;
	triggerType = (bRisingEdge) ? DAQmx_Val_Rising : DAQmx_Val_Falling;

	bGotData = false;
	pDataAI = NULL;
	pDataAO = NULL;
	pDataDI = NULL;
	pDataDO = NULL;

	bStop = false;
	bStopped = false;

	bFirst = true;
	bInRunMethod = false;
	bRunning = false;
	AItaskHandle = AOtaskHandle = DOtaskHandle = DItaskHandle = NULL;
	sampleFreq = 1000.0;
	bError = false;
	numSamples = 0;
	DIPort = DOPort = 0;

	ccout("begin read protocol txt");
	//ifstream pIfs(szCsv);
	//vector<string> v_lines;
	//string szLine;
	//while (getline(pIfs, szLine)) {
	//	v_lines.push_back(szLine);
	//}
	//pIfs.close();
	////cout << "INIT FILE: " << szCsv << endl;
	////load the header from the csv
	//for (int f = 0; f < 12; f++) {
	//	stringstream sstream(v_lines[f]);
	//	string member;
	//	sstream >> member;
	//	if (member == "numSamples") {
	//		sstream >> numSamples;
	//		cout << "Number of samples: " << numSamples << endl;
	//	}
	//	else if (member == "sampFreq") {
	//		sstream >> sampleFreq;
	//		cout << "Sample Frequency: " << sampleFreq << endl;
	//	}
	//	else if (member == "numAIChannels") {
	//		sstream >> numAIChannels;
	//		cout << "Number of AI Channels: " << numAIChannels << endl;
	//	}
	//	else if (member == "AIChannels") {
	//		if (numAIChannels > 0) {
	//			int channel = 0;
	//			string word2;
	//			for (int ff = 0; ff < numAIChannels; ff++) {
	//				getline(sstream, word2, ',');
	//				stringstream sss(word2);
	//				channel = word2[(ff == 0) ? 3 : 2] - '0';
	//				AIChannels.push_back(channel);
	//			}
	//			cout << "AI Channels: ";

	//			for (int ff = 0; ff < AIChannels.size(); ff++) {
	//				cout << AIChannels[ff] << " ";
	//			}
	//			cout << endl;

	//		}
	//	}
	//	else if (member == "numDIChannels") {
	//		sstream >> numDIChannels;
	//		cout << "Number of DI Channels: " << numDIChannels << endl;

	//	}
	//	else if (member == "DIPort") {
	//		sstream >> DIPort;
	//		cout << "DI Port: " << DIPort << endl;

	//	}
	//	else if (member == "DIChannels") {
	//		if (numDIChannels > 0) {
	//			int channel = 0;
	//			string word2;
	//			for (int ff = 0; ff < numDIChannels; ff++) {
	//				getline(sstream, word2, ',');
	//				stringstream sss(word2);
	//				channel = word2[(ff == 0) ? 5 : 4] - '0';
	//				DIChannels.push_back(channel);
	//			}


	//			for (int ff = 0; ff < DIChannels.size(); ff++) {
	//				cout << DIChannels[ff] << " ";
	//			}
	//			cout << endl;

	//		}
	//	}
	//	else if (member == "numAOChannels") {
	//		sstream >> numAOChannels;
	//		cout << "Number of AO Channels: " << numAOChannels << endl;

	//	}
	//	else if (member == "AOChannels") {
	//		if (numAOChannels > 0) {
	//			int channel = 0;
	//			string word2;
	//			for (int ff = 0; ff < numAOChannels; ff++) {
	//				getline(sstream, word2, ',');
	//				stringstream sss(word2);
	//				channel = word2[(ff == 0) ? 3 : 2] - '0';
	//				AOChannels.push_back(channel);
	//			}

	//			for (int ff = 0; ff < AOChannels.size(); ff++) {
	//				cout << AOChannels[ff] << " ";
	//			}
	//			cout << endl;


	//		}
	//	}
	//	else if (member == "numDOChannels") {
	//		sstream >> numDOChannels;
	//		cout << "Number of DO Channels: " << numDOChannels << endl;

	//	}
	//	else if (member == "DOPort") {
	//		sstream >> DOPort;
	//		cout << "DO Port: " << DOPort << endl;


	//	}
	//	else if (member == "DOChannels") {
	//		if (numDOChannels > 0) {
	//			int channel = 0;
	//			string word2;
	//			for (int ff = 0; ff < numDOChannels; ff++) {
	//				getline(sstream, word2, ',');
	//				stringstream sss(word2);
	//				channel = word2[(ff == 0) ? 5 : 4] - '0';
	//				DOChannels.push_back(channel);
	//			}
	//			cout << "DO Channels: ";

	//			for (int ff = 0; ff < DOChannels.size(); ff++) {
	//				cout << DOChannels[ff] << " ";
	//			}
	//			cout << endl;
	//		}
	//	}
	//	else {
	//		cout << "protocol  member not recognised: " << member << endl;
	//		MessageBoxA(NULL, "Protocol member not recognised", "NIDAQ error", MB_OK);

	//	}
	//}
	//if (numDOChannels > 0 && numAOChannels > 0) {
	//	stringstream sstream(v_lines[12]);
	//	string member;
	//	sstream >> member;
	//	if (member == "AODATA") {
	//		cout << "AODATA" << endl;
	//		pDataAO = new double[numSamples * numAOChannels];
	//		int fcnt = 0;
	//		for (int f = 13; f < 13 + numSamples; f++) {
	//			stringstream ss(v_lines[f]);
	//			string word2;
	//			cout << v_lines[f] << endl;
	//			while (getline(ss, word2, ',')) {
	//				stringstream sss(word2);
	//				sss >> pDataAO[fcnt];
	//				fcnt++;
	//			}
	//		}
	//	}
	//	stringstream sstream1(v_lines[13 + numSamples]);
	//	sstream1 >> member;
	//	if (member == "DODATA") {
	//		cout << "DODATA" << endl;
	//		pDataDO = new uInt32[numSamples * numDOChannels];
	//		int fcnt = 0;
	//		for (int f = 14 + numSamples; f < 14 + 2 * numSamples; f++) {
	//			stringstream ss(v_lines[f]);
	//			cout << v_lines[f] << endl;
	//			string word2;
	//			int cnt = 0;
	//			while (getline(ss, word2, ',')) {
	//				stringstream sss(word2);
	//				sss >> pDataDO[fcnt];
	//				//Each binary value and needs to be changed to a power of 2
	//				pDataDO[fcnt] *= (uInt32)pow(2, cnt);
	//				fcnt++;
	//				cnt++;
	//			}
	//		}
	//	}
	//}
	//else if (numDOChannels > 0) {
	//	stringstream sstream(v_lines[12]);
	//	string member;
	//	sstream >> member;
	//	if (member == "DODATA") {
	//		pDataDO = new uInt32[numSamples * numDOChannels];
	//		int fcnt = 0;
	//		for (int f = 13; f < 13 + numSamples; f++) {
	//			stringstream ss(v_lines[f]);
	//			string word2;
	//			int cnt = 0;
	//			while (getline(ss, word2, ',')) {
	//				stringstream sss(word2);
	//				sss >> pDataDO[fcnt];
	//				//Each binary value and needs to be changed to a power of 2
	//				pDataDO[fcnt] *= (uInt32)pow(2, cnt);
	//				fcnt++;
	//				cnt++;
	//			}
	//		}


	//	}
	//}
	//else if (numAOChannels > 0) {
	//	stringstream sstream(v_lines[12]);
	//	string member;
	//	sstream >> member;
	//	if (member == "AODATA") {
	//		pDataAO = new double[numSamples * numAOChannels];
	//		int fcnt = 0;
	//		for (int f = 13; f < 13 + numSamples; f++) {
	//			stringstream ss(v_lines[f]);
	//			string word2;
	//			while (getline(ss, word2, ',')) {
	//				stringstream sss(word2);
	//				sss >> pDataAO[fcnt];
	//				fcnt++;
	//			}
	//		}
	//	}
	//}

	//cout << "numSamples:" << numSamples << endl;
	//cout << "numAOChannels:" << numAOChannels << endl;
	//cout << "numDOChannels:" << numDOChannels << endl;
	//cout << "numAIChannels:" << numAIChannels << endl;
	//cout << "numDIChannels:" << numDIChannels << endl;

	ProtocolBase pb;
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
	vector<int> v_DO = pb.GetDOData();
	vector<double> v_AO = pb.GetAOData();
	//
	//to avoid AO and DO add a AI line
	if (numAOChannels > 0) {
		pDataAO = new double[numSamples * numAOChannels];
		for (int f = 0; f < v_AO.size(); f++) {
			pDataAO[f] = v_AO[f];
		}
	}


	if (numDOChannels > 0) {
		pDataDO = new uInt32[numSamples * numDOChannels];
		for (int f = 0; f < v_DO.size(); f++) {
			pDataDO[f] = v_DO[f];
		}
	}



	cout << "*******************************" << endl;
	cout << "numSamples " << numSamples << endl;
	cout << "sampleFreq " << sampleFreq << endl;
	cout << "numAOChannels " << numAOChannels << endl;
	cout << "numDOChannels " << numDOChannels << endl;
	cout << "numAIChannels " << numAIChannels << endl;
	cout << "numDIChannels " << numDIChannels << endl;
	cout << "DOPort " << DOPort << endl;
	cout << "DIPort " << DIPort << endl;
	cout << "AOChannels" << endl;
	for (int f = 0; f < numAOChannels; f++) {
		cout << AOChannels[f] << endl;
	}
	cout << "AIChannels" << endl;
	for (int f = 0; f < numAIChannels; f++) {
		cout << AIChannels[f] << endl;
	}
	cout << "DOChannels" << endl;
	for (int f = 0; f < numDOChannels; f++) {
		cout << DOChannels[f] << endl;
	}
	cout << "DIChannels" << endl;
	for (int f = 0; f < numDIChannels; f++) {
		cout << DIChannels[f] << endl;
	}

	cout << "AOData" << endl;
	for (int f = 0; f < v_AO.size(); f++) {
		cout << v_AO[f] << endl;
	}

	cout << "DOData" << endl;
	for (int f = 0; f < v_DO.size(); f++) {
		cout << v_DO[f] << endl;
	}
	cout << "**************************************" << endl;


	ccout("completed read protocol txt");
	///////////DAQMX////////////////////

	char buffer[2000] = { 0 };
	int errorCode = 0;

	stringstream ssError;
	ssError << "Dev" << deviceID << " NIDAQ error";
	bUsingAO = numAOChannels > 0;
	bUsingAI = numAIChannels > 0;
	bUsingDO = numDOChannels > 0;
	bUsingDI = numDIChannels > 0;

	ccout("create handles");

	if (bUsingDO) errorCode = DAQmxCreateTask("", &DOtaskHandle);
	if (bUsingDI) errorCode = DAQmxCreateTask("", &DItaskHandle);
	if (bUsingAO) errorCode = DAQmxCreateTask("", &AOtaskHandle);
	if (bUsingAI) errorCode = DAQmxCreateTask("", &AItaskHandle);

	ccout("create channels");
	for (int f = 0; f < numAIChannels; f++) {
		std::stringstream ss;
		ss << "Dev" << deviceID << "/ai" << AIChannels[f];
		errorCode = DAQmxCreateAIVoltageChan(AItaskHandle, ss.str().c_str(), "", DAQmx_Val_RSE, minV, maxV, DAQmx_Val_Volts, NULL);

	}
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
	for (int f = 0; f < numDIChannels; f++) {
		std::stringstream ssd;
		//cout << "create DI channel" << endl;
		ssd << "Dev" << deviceID << "/port" << DIPort << "/line" << DIChannels[f];
		errorCode = DAQmxCreateDIChan(DItaskHandle, ssd.str().c_str(), "", DAQmx_Val_ChanPerLine);
	
	}


	////cfg clocks
	ccout("set sample clocks");
	if (bUsingAI) errorCode = DAQmxCfgSampClkTiming(AItaskHandle, "", sampleFreq, DAQmx_Val_Rising, DAQmx_Val_ContSamps, numSamples);
	if (bUsingAO) errorCode = DAQmxCfgSampClkTiming(AOtaskHandle, "", sampleFreq, DAQmx_Val_Rising, DAQmx_Val_ContSamps, numSamples);
	if (bUsingDO) errorCode = DAQmxCfgSampClkTiming(DOtaskHandle, "", sampleFreq, DAQmx_Val_Rising, DAQmx_Val_ContSamps, numSamples);
	if (bUsingDI) errorCode = DAQmxCfgSampClkTiming(DItaskHandle, "", sampleFreq, DAQmx_Val_Rising, DAQmx_Val_ContSamps, numSamples);

	//
	//
	// different combos of triggers

	////cfg triggers depending on AO, AI, DO, DI
	std::stringstream ss4;


	//order to triggers ao->do->ai->di

	ccout("set triggers");

	if (!bUsingAO && !bUsingAI && !bUsingDO && !bUsingDI) {
		MessageBoxA(NULL, ssError.str().c_str(), "No channels selected", MB_OK);
	}
	else if (!bUsingAO && !bUsingAI && !bUsingDO && bUsingDI) {
		//no trigger
	}
	else if (!bUsingAO && !bUsingAI && bUsingDO && !bUsingDI) {
		//no trigger
	}
	else if (!bUsingAO && !bUsingAI && bUsingDO && bUsingDI) {
		//do trigger
		ss4 << "/Dev" << deviceID << "/do/StartTrigger";
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
	else if (!bUsingAO && bUsingAI && !bUsingDO && bUsingDI) {
		//ai trigger
		ss4 << "/Dev" << deviceID << "/ai/StartTrigger";
		errorCode = DAQmxCfgDigEdgeStartTrig(DItaskHandle, ss4.str().c_str(), DAQmx_Val_Rising);
		if (errorCode < 0) {
			DAQmxGetErrorString(errorCode, buffer, 2000);
			MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
			return false;
		}
	}
	else if (!bUsingAO && bUsingAI && bUsingDO && !bUsingDI) {
		//do trigger
		ss4 << "/Dev" << deviceID << "/do/StartTrigger";

		errorCode = DAQmxCfgDigEdgeStartTrig(AItaskHandle, ss4.str().c_str(), DAQmx_Val_Rising);
		if (errorCode < 0) {
			DAQmxGetErrorString(errorCode, buffer, 2000);
			MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
			return false;
		}
	}
	else if (!bUsingAO && bUsingAI && bUsingDO && bUsingDI) {
		//do trigger
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
	else if (bUsingAO && !bUsingAI && !bUsingDO && !bUsingDI) {
		//no trigger
	}
	else if (bUsingAO && !bUsingAI && !bUsingDO && bUsingDI) {
		//ao trigger
		ss4 << "/Dev" << deviceID << "/ao/StartTrigger";
		errorCode = DAQmxCfgDigEdgeStartTrig(DItaskHandle, ss4.str().c_str(), DAQmx_Val_Rising);
		if (errorCode < 0) {
			DAQmxGetErrorString(errorCode, buffer, 2000);
			MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
			return false;
		}

	}
	else if (bUsingAO && !bUsingAI && bUsingDO && !bUsingDI) {
		//ao trigger
		ss4 << "/Dev" << deviceID << "/ao/StartTrigger";

		errorCode = DAQmxCfgDigEdgeStartTrig(DOtaskHandle, ss4.str().c_str(), DAQmx_Val_Rising);
		if (errorCode < 0) {
			DAQmxGetErrorString(errorCode, buffer, 2000);
			MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
			return false;
		}

	}
	else if (bUsingAO && !bUsingAI && bUsingDO && bUsingDI) {
		//ao trigger
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
	else if (bUsingAO && bUsingAI && !bUsingDO && !bUsingDI) {
		//ao trigger
		ss4 << "/Dev" << deviceID << "/ao/StartTrigger";
		errorCode = DAQmxCfgDigEdgeStartTrig(AItaskHandle, ss4.str().c_str(), DAQmx_Val_Rising);
		if (errorCode < 0) {
			DAQmxGetErrorString(errorCode, buffer, 2000);
			MessageBoxA(NULL, buffer, ssError.str().c_str(), MB_OK);
			return false;
		}
	}
	else if (bUsingAO && bUsingAI && !bUsingDO && bUsingDI) {
		//ao trigger
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
	else if (bUsingAO && bUsingAI && bUsingDO && !bUsingDI) {
		//ao trigger
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
	else if (bUsingAO && bUsingAI && bUsingDO && bUsingDI) {
		//ao trigger
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
	else {

	}


	ccout("create arrays for pDataAI and pDataDI");

	if (bUsingAI) {
		pDataAI = new double[numSamples * numAIChannels];
		for (int f = 0; f < numSamples * numAIChannels; f++) {
			pDataAI[f] = 0.0;
		}
	}

	if (bUsingDI) {
		pDataDI = new uInt32[numSamples * numDIChannels];
		for (int f = 0; f < numSamples; f++) {
			pDataDI[f] = 0;
		}
	}

	curTotalTime = 0.0;
	bInRunMethod = false;

	

	ccout("end init()");
	return false;
}
int		SimpleProtocolContinuous::GetNumAOChannels() {
	return numAOChannels;
}
int		SimpleProtocolContinuous::GetNumDOChannels() {
	return numDOChannels;
}
int		SimpleProtocolContinuous::GetNumAIChannels() {
	return numAIChannels;
}
int		SimpleProtocolContinuous::GetNumDIChannels() {
	return numDIChannels;
}
int		SimpleProtocolContinuous::GetNumSamples() {
	return numSamples;
}
double	SimpleProtocolContinuous::GetSampleFreq() {
	return sampleFreq;
}
int		SimpleProtocolContinuous::GetContinuousChannelFromIndex(int type, int ix) {
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
int		SimpleProtocolContinuous::GetContinuousDOPort() {

	return DOPort;
}
int		SimpleProtocolContinuous::GetContinuousDIPort() {
	return DIPort;
}

bool	SimpleProtocolContinuous::Run(double* pTimeSec, double* DataAO, double* DataAI, uInt32* DataDO, uInt32* DataDI) {// hyp: do is single, di is array
	//cout << "from c++ start run" << endl;

	

	ccout("run()");
	//create and start a parallel thread of callbacks
	bInRunMethod = true;
	bReadyToShutdown = false;
	int errorCode = 0;
	stringstream szz;
	szz << "Device " << deviceID;
	char buffer[2000] = { 0 };

	if (bFirst) {
		//load the data
		ccout("first run");

		ccout("write to output AO DO channels");
		if (bUsingAO) {
			memcpy(DataAO, pDataAO, numSamples * numAOChannels * sizeof(double));
			errorCode = DAQmxWriteAnalogF64(AOtaskHandle, numSamples, 0, DAQmx_Val_WaitInfinitely, DAQmx_Val_GroupByScanNumber, pDataAO, NULL, NULL);
			if (errorCode < 0) {
				DAQmxGetErrorString(errorCode, buffer, 2000);
				MessageBoxA(NULL, buffer, szz.str().c_str(), MB_OK);
			}
		}
		if (bUsingDO) {
			memcpy(DataDO, pDataDO, numSamples * numDOChannels * sizeof(uInt32));
			errorCode = DAQmxWriteDigitalU32(DOtaskHandle, numSamples, 0, DAQmx_Val_WaitInfinitely, DAQmx_Val_GroupByScanNumber, pDataDO, NULL, NULL);
			if (errorCode < 0) {
				DAQmxGetErrorString(errorCode, buffer, 2000);
				MessageBoxA(NULL, buffer, szz.str().c_str(), MB_OK);
			}
		}





		//
		// 
		//use AI then DI for the callbacks (since even 6001 supports AIAO but not buffered DI
		//then use AO DO if nec (as suspect called at the beginning rather than the end, callback called when data is transferred to the device), AO before DO


		if (!bUsingAO && !bUsingAI && !bUsingDO && !bUsingDI) {
			//no data
		}
		else if (!bUsingAO && !bUsingAI && !bUsingDO && bUsingDI) {
			errorCode = DAQmxRegisterEveryNSamplesEvent(DItaskHandle, DAQmx_Val_Acquired_Into_Buffer, numSamples, 0, SimpleProtocolContinuous::EveryNCallback, this);
			if (errorCode < 0) {
				DAQmxGetErrorString(errorCode, buffer, 2000);
				MessageBoxA(NULL, buffer, szz.str().c_str(), MB_OK);
			}
		}
		else if (!bUsingAO && !bUsingAI && bUsingDO && !bUsingDI) {
			errorCode = DAQmxRegisterEveryNSamplesEvent(DOtaskHandle, DAQmx_Val_Transferred_From_Buffer, numSamples, 0, SimpleProtocolContinuous::EveryNCallback, this);
			if (errorCode < 0) {
				DAQmxGetErrorString(errorCode, buffer, 2000);
				MessageBoxA(NULL, buffer, szz.str().c_str(), MB_OK);
			}
		}
		else if (!bUsingAO && !bUsingAI && bUsingDO && bUsingDI) {
			errorCode = DAQmxRegisterEveryNSamplesEvent(DItaskHandle, DAQmx_Val_Acquired_Into_Buffer, numSamples, 0, SimpleProtocolContinuous::EveryNCallback, this);
			if (errorCode < 0) {
				DAQmxGetErrorString(errorCode, buffer, 2000);
				MessageBoxA(NULL, buffer, szz.str().c_str(), MB_OK);
			}
		}
		else if (!bUsingAO && bUsingAI && !bUsingDO && !bUsingDI) {
			errorCode = DAQmxRegisterEveryNSamplesEvent(AItaskHandle, DAQmx_Val_Acquired_Into_Buffer, numSamples, 0, SimpleProtocolContinuous::EveryNCallback, this);
			if (errorCode < 0) {
				DAQmxGetErrorString(errorCode, buffer, 2000);
				MessageBoxA(NULL, buffer, szz.str().c_str(), MB_OK);
			}
		}
		else if (!bUsingAO && bUsingAI && !bUsingDO && bUsingDI) {
			errorCode = DAQmxRegisterEveryNSamplesEvent(AItaskHandle, DAQmx_Val_Acquired_Into_Buffer, numSamples, 0, SimpleProtocolContinuous::EveryNCallback, this);
			if (errorCode < 0) {
				DAQmxGetErrorString(errorCode, buffer, 2000);
				MessageBoxA(NULL, buffer, szz.str().c_str(), MB_OK);
			}
		}
		else if (!bUsingAO && bUsingAI && bUsingDO && !bUsingDI) {
			errorCode = DAQmxRegisterEveryNSamplesEvent(AItaskHandle, DAQmx_Val_Acquired_Into_Buffer, numSamples, 0, SimpleProtocolContinuous::EveryNCallback, this);
			if (errorCode < 0) {
				DAQmxGetErrorString(errorCode, buffer, 2000);
				MessageBoxA(NULL, buffer, szz.str().c_str(), MB_OK);
			}
		}
		else if (!bUsingAO && bUsingAI && bUsingDO && bUsingDI) {
			errorCode = DAQmxRegisterEveryNSamplesEvent(AItaskHandle, DAQmx_Val_Acquired_Into_Buffer, numSamples, 0, SimpleProtocolContinuous::EveryNCallback, this);
			if (errorCode < 0) {
				DAQmxGetErrorString(errorCode, buffer, 2000);
				MessageBoxA(NULL, buffer, szz.str().c_str(), MB_OK);
			}
		}
		else if (bUsingAO && !bUsingAI && !bUsingDO && !bUsingDI) {
			errorCode = DAQmxRegisterEveryNSamplesEvent(AOtaskHandle, DAQmx_Val_Transferred_From_Buffer, numSamples, 0, SimpleProtocolContinuous::EveryNCallback, this);
			if (errorCode < 0) {
				DAQmxGetErrorString(errorCode, buffer, 2000);
				MessageBoxA(NULL, buffer, szz.str().c_str(), MB_OK);
			}
		}
		else if (bUsingAO && !bUsingAI && !bUsingDO && bUsingDI) {
			errorCode = DAQmxRegisterEveryNSamplesEvent(DItaskHandle, DAQmx_Val_Acquired_Into_Buffer, numSamples, 0, SimpleProtocolContinuous::EveryNCallback, this);
			if (errorCode < 0) {
				DAQmxGetErrorString(errorCode, buffer, 2000);
				MessageBoxA(NULL, buffer, szz.str().c_str(), MB_OK);
			}
		}
		else if (bUsingAO && !bUsingAI && bUsingDO && !bUsingDI) {
			errorCode = DAQmxRegisterEveryNSamplesEvent(AOtaskHandle, DAQmx_Val_Transferred_From_Buffer, numSamples, 0, SimpleProtocolContinuous::EveryNCallback, this);
			if (errorCode < 0) {
				DAQmxGetErrorString(errorCode, buffer, 2000);
				MessageBoxA(NULL, buffer, szz.str().c_str(), MB_OK);
			}
		}
		else if (bUsingAO && !bUsingAI && bUsingDO && bUsingDI) {
			errorCode = DAQmxRegisterEveryNSamplesEvent(DItaskHandle, DAQmx_Val_Acquired_Into_Buffer, numSamples, 0, SimpleProtocolContinuous::EveryNCallback, this);
			if (errorCode < 0) {
				DAQmxGetErrorString(errorCode, buffer, 2000);
				MessageBoxA(NULL, buffer, szz.str().c_str(), MB_OK);
			}
		}
		else if (bUsingAO && bUsingAI && !bUsingDO && !bUsingDI) {
			errorCode = DAQmxRegisterEveryNSamplesEvent(AItaskHandle, DAQmx_Val_Acquired_Into_Buffer, numSamples, 0, SimpleProtocolContinuous::EveryNCallback, this);
			if (errorCode < 0) {
				DAQmxGetErrorString(errorCode, buffer, 2000);
				MessageBoxA(NULL, buffer, szz.str().c_str(), MB_OK);
			}
		}
		else if (bUsingAO && bUsingAI && !bUsingDO && bUsingDI) {
			errorCode = DAQmxRegisterEveryNSamplesEvent(AItaskHandle, DAQmx_Val_Acquired_Into_Buffer, numSamples, 0, SimpleProtocolContinuous::EveryNCallback, this);
			if (errorCode < 0) {
				DAQmxGetErrorString(errorCode, buffer, 2000);
				MessageBoxA(NULL, buffer, szz.str().c_str(), MB_OK);
			}
		}
		else if (bUsingAO && bUsingAI && bUsingDO && !bUsingDI) {
			errorCode = DAQmxRegisterEveryNSamplesEvent(AItaskHandle, DAQmx_Val_Acquired_Into_Buffer, numSamples, 0, SimpleProtocolContinuous::EveryNCallback, this);
			if (errorCode < 0) {
				DAQmxGetErrorString(errorCode, buffer, 2000);
				MessageBoxA(NULL, buffer, szz.str().c_str(), MB_OK);
			}
		}
		else if (bUsingAO && bUsingAI && bUsingDO && bUsingDI) {
			errorCode = DAQmxRegisterEveryNSamplesEvent(AItaskHandle, DAQmx_Val_Acquired_Into_Buffer, numSamples, 0, SimpleProtocolContinuous::EveryNCallback, this);
			if (errorCode < 0) {
	
				DAQmxGetErrorString(errorCode, buffer, 2000);
				MessageBoxA(NULL, buffer, szz.str().c_str(), MB_OK);
			}


		}
		else {

		}

		ccout("start task or set external trigger");
		if (!bUsingAO && !bUsingAI && !bUsingDO && !bUsingDI) {
			//no data
		}
		else if (!bUsingAO && !bUsingAI && !bUsingDO && bUsingDI) {

			if (bStartTrigger) {
				std::stringstream ssTrig;
				ssTrig << "/Dev" << deviceID << "/PFI" << pFIx;
				errorCode = DAQmxCfgDigEdgeStartTrig(DItaskHandle, ssTrig.str().c_str(), triggerType);
				if (errorCode < 0) {
					DAQmxGetErrorString(errorCode, buffer, 2000);
					MessageBoxA(NULL, buffer, szz.str().c_str(), MB_OK);
				}
			}
			errorCode = DAQmxStartTask(DItaskHandle);
		}
		else if (!bUsingAO && !bUsingAI && bUsingDO && !bUsingDI) {
			if (bStartTrigger) {
				std::stringstream ssTrig;
				ssTrig << "/Dev" << deviceID << "/PFI" << pFIx;
				errorCode = DAQmxCfgDigEdgeStartTrig(DOtaskHandle, ssTrig.str().c_str(), triggerType);
				if (errorCode < 0) {
					DAQmxGetErrorString(errorCode, buffer, 2000);
					MessageBoxA(NULL, buffer, szz.str().c_str(), MB_OK);
				}
			}
			errorCode = DAQmxStartTask(DOtaskHandle);
		}
		else if (!bUsingAO && !bUsingAI && bUsingDO && bUsingDI) {
			errorCode = DAQmxStartTask(DItaskHandle);
			if (bStartTrigger) {
				std::stringstream ssTrig;
				ssTrig << "/Dev" << deviceID << "/PFI" << pFIx;
				errorCode = DAQmxCfgDigEdgeStartTrig(DOtaskHandle, ssTrig.str().c_str(), triggerType);
				if (errorCode < 0) {
					DAQmxGetErrorString(errorCode, buffer, 2000);
					MessageBoxA(NULL, buffer, szz.str().c_str(), MB_OK);
				}
			}
			errorCode = DAQmxStartTask(DOtaskHandle);
			if (errorCode < 0) {
				DAQmxGetErrorString(errorCode, buffer, 2000);
				MessageBoxA(NULL, buffer, szz.str().c_str(), MB_OK);
			}
		}
		else if (!bUsingAO && bUsingAI && !bUsingDO && !bUsingDI) {

			if (bStartTrigger) {
				std::stringstream ssTrig;
				ssTrig << "/Dev" << deviceID << "/PFI" << pFIx;
				errorCode = DAQmxCfgDigEdgeStartTrig(AItaskHandle, ssTrig.str().c_str(), triggerType);
				if (errorCode < 0) {
					DAQmxGetErrorString(errorCode, buffer, 2000);
					MessageBoxA(NULL, buffer, szz.str().c_str(), MB_OK);
				}
			}
			errorCode = DAQmxStartTask(AItaskHandle);
			if (errorCode < 0) {
				DAQmxGetErrorString(errorCode, buffer, 2000);
				MessageBoxA(NULL, buffer, szz.str().c_str(), MB_OK);
			}
		}
		else if (!bUsingAO && bUsingAI && !bUsingDO && bUsingDI) {

			errorCode = DAQmxStartTask(DItaskHandle);
			if (errorCode < 0) {
				DAQmxGetErrorString(errorCode, buffer, 2000);
				MessageBoxA(NULL, buffer, szz.str().c_str(), MB_OK);
			}

			if (bStartTrigger) {
				std::stringstream ssTrig;
				ssTrig << "/Dev" << deviceID << "/PFI" << pFIx;
				errorCode = DAQmxCfgDigEdgeStartTrig(AItaskHandle, ssTrig.str().c_str(), triggerType);
				if (errorCode < 0) {
					DAQmxGetErrorString(errorCode, buffer, 2000);
					MessageBoxA(NULL, buffer, szz.str().c_str(), MB_OK);
				}
			}
			errorCode = DAQmxStartTask(AItaskHandle);
			if (errorCode < 0) {
				DAQmxGetErrorString(errorCode, buffer, 2000);
				MessageBoxA(NULL, buffer, szz.str().c_str(), MB_OK);
			}
		}
		else if (!bUsingAO && bUsingAI && bUsingDO && !bUsingDI) {

			errorCode = DAQmxStartTask(AItaskHandle);
			if (errorCode < 0) {
				DAQmxGetErrorString(errorCode, buffer, 2000);
				MessageBoxA(NULL, buffer, szz.str().c_str(), MB_OK);
			}

			if (bStartTrigger) {
				std::stringstream ssTrig;
				ssTrig << "/Dev" << deviceID << "/PFI" << pFIx;
				errorCode = DAQmxCfgDigEdgeStartTrig(DOtaskHandle, ssTrig.str().c_str(), triggerType);
				if (errorCode < 0) {
					DAQmxGetErrorString(errorCode, buffer, 2000);
					MessageBoxA(NULL, buffer, szz.str().c_str(), MB_OK);
				}
			}
			errorCode = DAQmxStartTask(DOtaskHandle);
			if (errorCode < 0) {
				DAQmxGetErrorString(errorCode, buffer, 2000);
				MessageBoxA(NULL, buffer, szz.str().c_str(), MB_OK);
			}
		}
		else if (!bUsingAO && bUsingAI && bUsingDO && bUsingDI) {

			errorCode = DAQmxStartTask(DItaskHandle);
			if (errorCode < 0) {
				DAQmxGetErrorString(errorCode, buffer, 2000);
				MessageBoxA(NULL, buffer, szz.str().c_str(), MB_OK);
			}
			errorCode = DAQmxStartTask(AItaskHandle);
			if (errorCode < 0) {
				DAQmxGetErrorString(errorCode, buffer, 2000);
				MessageBoxA(NULL, buffer, szz.str().c_str(), MB_OK);
			}

			if (bStartTrigger) {
				std::stringstream ssTrig;
				ssTrig << "/Dev" << deviceID << "/PFI" << pFIx;
				errorCode = DAQmxCfgDigEdgeStartTrig(DOtaskHandle, ssTrig.str().c_str(), triggerType);
				if (errorCode < 0) {
					DAQmxGetErrorString(errorCode, buffer, 2000);
					MessageBoxA(NULL, buffer, szz.str().c_str(), MB_OK);
				}
			}
			errorCode = DAQmxStartTask(DOtaskHandle);
			if (errorCode < 0) {
				DAQmxGetErrorString(errorCode, buffer, 2000);
				MessageBoxA(NULL, buffer, szz.str().c_str(), MB_OK);
			}
		}
		else if (bUsingAO && !bUsingAI && !bUsingDO && !bUsingDI) {

			if (bStartTrigger) {
				std::stringstream ssTrig;
				ssTrig << "/Dev" << deviceID << "/PFI" << pFIx;
				errorCode = DAQmxCfgDigEdgeStartTrig(AOtaskHandle, ssTrig.str().c_str(), triggerType);
				if (errorCode < 0) {
					DAQmxGetErrorString(errorCode, buffer, 2000);
					MessageBoxA(NULL, buffer, szz.str().c_str(), MB_OK);
				}
			}
			errorCode = DAQmxStartTask(AOtaskHandle);
			if (errorCode < 0) {
				DAQmxGetErrorString(errorCode, buffer, 2000);
				MessageBoxA(NULL, buffer, szz.str().c_str(), MB_OK);
			}
		}
		else if (bUsingAO && !bUsingAI && !bUsingDO && bUsingDI) {

			errorCode = DAQmxStartTask(DItaskHandle);
			if (errorCode < 0) {
				DAQmxGetErrorString(errorCode, buffer, 2000);
				MessageBoxA(NULL, buffer, szz.str().c_str(), MB_OK);
			}

			if (bStartTrigger) {
				std::stringstream ssTrig;
				ssTrig << "/Dev" << deviceID << "/PFI" << pFIx;
				errorCode = DAQmxCfgDigEdgeStartTrig(AOtaskHandle, ssTrig.str().c_str(), triggerType);
				if (errorCode < 0) {
					DAQmxGetErrorString(errorCode, buffer, 2000);
					MessageBoxA(NULL, buffer, szz.str().c_str(), MB_OK);
				}
			}
			errorCode = DAQmxStartTask(AOtaskHandle);
			if (errorCode < 0) {
				DAQmxGetErrorString(errorCode, buffer, 2000);
				MessageBoxA(NULL, buffer, szz.str().c_str(), MB_OK);
			}
		}
		else if (bUsingAO && !bUsingAI && bUsingDO && !bUsingDI) {

			errorCode = DAQmxStartTask(DOtaskHandle);
			if (errorCode < 0) {
				DAQmxGetErrorString(errorCode, buffer, 2000);
				MessageBoxA(NULL, buffer, szz.str().c_str(), MB_OK);
			}

			if (bStartTrigger) {
				std::stringstream ssTrig;
				ssTrig << "/Dev" << deviceID << "/PFI" << pFIx;
				errorCode = DAQmxCfgDigEdgeStartTrig(AOtaskHandle, ssTrig.str().c_str(), triggerType);
				if (errorCode < 0) {
					DAQmxGetErrorString(errorCode, buffer, 2000);
					MessageBoxA(NULL, buffer, szz.str().c_str(), MB_OK);
				}
			}
			errorCode = DAQmxStartTask(AOtaskHandle);
			if (errorCode < 0) {
				DAQmxGetErrorString(errorCode, buffer, 2000);
				MessageBoxA(NULL, buffer, szz.str().c_str(), MB_OK);
			}
		}
		else if (bUsingAO && !bUsingAI && bUsingDO && bUsingDI) {

			errorCode = DAQmxStartTask(DItaskHandle);
			if (errorCode < 0) {
				DAQmxGetErrorString(errorCode, buffer, 2000);
				MessageBoxA(NULL, buffer, szz.str().c_str(), MB_OK);
			}
			errorCode = DAQmxStartTask(DOtaskHandle);
			if (errorCode < 0) {
				DAQmxGetErrorString(errorCode, buffer, 2000);
				MessageBoxA(NULL, buffer, szz.str().c_str(), MB_OK);
			}

			if (bStartTrigger) {
				std::stringstream ssTrig;
				ssTrig << "/Dev" << deviceID << "/PFI" << pFIx;
				errorCode = DAQmxCfgDigEdgeStartTrig(AOtaskHandle, ssTrig.str().c_str(), triggerType);
				if (errorCode < 0) {
					DAQmxGetErrorString(errorCode, buffer, 2000);
					MessageBoxA(NULL, buffer, szz.str().c_str(), MB_OK);
				}
			}
			errorCode = DAQmxStartTask(AOtaskHandle);
			if (errorCode < 0) {
				DAQmxGetErrorString(errorCode, buffer, 2000);
				MessageBoxA(NULL, buffer, szz.str().c_str(), MB_OK);
			}
		}
		else if (bUsingAO && bUsingAI && !bUsingDO && !bUsingDI) {

			errorCode = DAQmxStartTask(AItaskHandle);
			if (errorCode < 0) {
				DAQmxGetErrorString(errorCode, buffer, 2000);
				stringstream szz;
				szz << "NIDAQ cannot do continuous samples with current numSamples. Change to 10,20,50,100,200,500,1000,2000,5000 etc.   " << buffer;
				MessageBoxA(NULL, szz.str().c_str(), szz.str().c_str(), MB_OK);
			}


			if (bStartTrigger) {
				std::stringstream ssTrig;
				ssTrig << "/Dev" << deviceID << "/PFI" << pFIx;
				errorCode = DAQmxCfgDigEdgeStartTrig(AOtaskHandle, ssTrig.str().c_str(), triggerType);
				if (errorCode < 0) {
					DAQmxGetErrorString(errorCode, buffer, 2000);
					MessageBoxA(NULL, buffer, szz.str().c_str(), MB_OK);
				}
			}
			errorCode = DAQmxStartTask(AOtaskHandle);
			if (errorCode < 0) {
				DAQmxGetErrorString(errorCode, buffer, 2000);
				MessageBoxA(NULL, buffer, szz.str().c_str(), MB_OK);
			}
		}
		else if (bUsingAO && bUsingAI && !bUsingDO && bUsingDI) {

			errorCode = DAQmxStartTask(DItaskHandle);
			if (errorCode < 0) {
				DAQmxGetErrorString(errorCode, buffer, 2000);
				MessageBoxA(NULL, buffer, szz.str().c_str(), MB_OK);
			}
			errorCode = DAQmxStartTask(AItaskHandle);
			if (errorCode < 0) {
				DAQmxGetErrorString(errorCode, buffer, 2000);
				MessageBoxA(NULL, buffer, szz.str().c_str(), MB_OK);
			}


			if (bStartTrigger) {
				std::stringstream ssTrig;
				ssTrig << "/Dev" << deviceID << "/PFI" << pFIx;
				errorCode = DAQmxCfgDigEdgeStartTrig(AOtaskHandle, ssTrig.str().c_str(), triggerType);
				if (errorCode < 0) {
					DAQmxGetErrorString(errorCode, buffer, 2000);
					MessageBoxA(NULL, buffer, szz.str().c_str(), MB_OK);
				}
			}
			errorCode = DAQmxStartTask(AOtaskHandle);
			if (errorCode < 0) {
				DAQmxGetErrorString(errorCode, buffer, 2000);
				MessageBoxA(NULL, buffer, szz.str().c_str(), MB_OK);
			}
		}
		else if (bUsingAO && bUsingAI && bUsingDO && !bUsingDI) {

			errorCode = DAQmxStartTask(AItaskHandle);
			if (errorCode < 0) {
				DAQmxGetErrorString(errorCode, buffer, 2000);
				MessageBoxA(NULL, buffer, szz.str().c_str(), MB_OK);
			}
			errorCode = DAQmxStartTask(DOtaskHandle);
			if (errorCode < 0) {
				DAQmxGetErrorString(errorCode, buffer, 2000);
				MessageBoxA(NULL, buffer, szz.str().c_str(), MB_OK);
			}

			if (bStartTrigger) {
				std::stringstream ssTrig;
				ssTrig << "/Dev" << deviceID << "/PFI" << pFIx;
				errorCode = DAQmxCfgDigEdgeStartTrig(AOtaskHandle, ssTrig.str().c_str(), triggerType);
				if (errorCode < 0) {
					DAQmxGetErrorString(errorCode, buffer, 2000);
					MessageBoxA(NULL, buffer, szz.str().c_str(), MB_OK);
				}
			}
			errorCode = DAQmxStartTask(AOtaskHandle);
			if (errorCode < 0) {
				DAQmxGetErrorString(errorCode, buffer, 2000);
				MessageBoxA(NULL, buffer, szz.str().c_str(), MB_OK);
			}
		}
		else if (bUsingAO && bUsingAI && bUsingDO && bUsingDI) {

		cout << "error here  1" << endl;
			errorCode = DAQmxStartTask(DItaskHandle);
			if (errorCode < 0) {
				DAQmxGetErrorString(errorCode, buffer, 2000);
				MessageBoxA(NULL, buffer, szz.str().c_str(), MB_OK);
			}
			errorCode = DAQmxStartTask(AItaskHandle);
			if (errorCode < 0) {
				DAQmxGetErrorString(errorCode, buffer, 2000);
				MessageBoxA(NULL, buffer, szz.str().c_str(), MB_OK);
			}
			errorCode = DAQmxStartTask(DOtaskHandle);
			if (errorCode < 0) {
				DAQmxGetErrorString(errorCode, buffer, 2000);
				MessageBoxA(NULL, buffer, szz.str().c_str(), MB_OK);
			}
			cout << "error here 2" << endl;

			if (bStartTrigger) {
				std::stringstream ssTrig;
				ssTrig << "/Dev" << deviceID << "/PFI" << pFIx;
				errorCode = DAQmxCfgDigEdgeStartTrig(AOtaskHandle, ssTrig.str().c_str(), triggerType);
				if (errorCode < 0) {
					DAQmxGetErrorString(errorCode, buffer, 2000);
					MessageBoxA(NULL, buffer, szz.str().c_str(), MB_OK);
				}
			}
			else {
				errorCode = DAQmxStartTask(AOtaskHandle);
				if (errorCode < 0) {
					DAQmxGetErrorString(errorCode, buffer, 2000);
					MessageBoxA(NULL, buffer, szz.str().c_str(), MB_OK);
				}
			}
			cout << "error here 3" << endl;
		}
		else {

		}

		//cout << errorCode << endl;
		bFirst = false;
		bGotData = false;
		ccout("end of first section of run()");
	}
	//bRunning = true;
	ccout("Wait for callback data");
	while (!bGotData) {

		Sleep((int)(0.1 * numSamples / sampleFreq * 1000)); //return data within 1/10 of the time to collect each batch of data 
	}
	ccout("Got callback data");
	bGotData = false;
	ccout("copy AI DI data buffers");
	//after shutdown this ends up being called with null buffers etc
	// 
	if (!bShutdown) {
		if (bUsingDI) memcpy(DataDI, pDataDI, numSamples * numDIChannels * sizeof(uInt32));
		if (bUsingAI) memcpy(DataAI, pDataAI, numSamples * numAIChannels * sizeof(double));
	}

	for (int f = 0; f < numSamples; f++) {
		pTimeSec[f] = curTotalTime + f / sampleFreq;
	}

	curTotalTime += numSamples / sampleFreq;
	bInRunMethod = false;

	ccout("end of run()");
	return true;
}
bool	SimpleProtocolContinuous::Shutdown() {
	//cout << "shutdown" << endl;
	//this will be called in a separate thread to run, so it might be that run is still spinning waiting for data
	//so the flag bInRunMethod lets us know when the run function has returned.
	// 
	//used as a stop  stop handles and clear handles
	//cout << "from c++ shutdown start" <<  endl;

	ccout("shutdown()");
	//while (bRunning) {
	//	Sleep(100);
	//}

	bShutdown = true;
	ccout("waiting for ready to shutdown handles signal");
	while (!bReadyToShutdown) {
		Sleep(100);
	}

	ccout("stop tasks");
	if (bUsingAO) DAQmxStopTask(AOtaskHandle);
	if (bUsingAI) DAQmxStopTask(AItaskHandle);
	if (bUsingDO) DAQmxStopTask(DOtaskHandle);
	if (bUsingDI) DAQmxStopTask(DItaskHandle);

	ccout("clear tasks");
	if (bUsingDO) DAQmxClearTask(DOtaskHandle);
	if (bUsingDI) DAQmxClearTask(DItaskHandle);
	if (bUsingAO) DAQmxClearTask(AOtaskHandle);
	if (bUsingAI) DAQmxClearTask(AItaskHandle);

	//bGotData = true;
	bRunning = false;

	//wait for thread to leave run
	//while (bInRunMethod) {
	//	Sleep(100);
	//}
	ccout("delete AO,AI,DO,DI buffers");
	if (bUsingAO) {
		if (!pDataAO) delete[] pDataAO;
		pDataAO = NULL;
	}
	if (bUsingDO) {
		delete[] pDataDO;
		pDataDO = NULL;
	}
	if (bUsingAI) {
		delete[] pDataAI;
		pDataAI = NULL;
	}
	if (bUsingDI) {
		delete[] pDataDI;
		pDataDI = NULL;
	}

	AIChannels.clear();
	AOChannels.clear();
	DIChannels.clear();
	DOChannels.clear();


	bGotData = false;
	numAIChannels = numAOChannels = numDIChannels = numDOChannels = 0;


	ccout("end shutdown()");
	return false;
}

int32 CVICALLBACK SimpleProtocolContinuous::EveryNCallback(TaskHandle taskHandle, int32 everyNsamplesEventType, uInt32 nSamples, void* callbackData) {
	//
	SimpleProtocolContinuous* pProtocol = (SimpleProtocolContinuous*)callbackData;
	if (!pProtocol->bShutdown) {
		//only access pProtocol is the Shutdown() has not been called which is a different thread
		//the taskHandles will only be cleared once the bReadtToShutdown flag is true.
		pProtocol->ccout("NCallback()");
		int32 read = 0;
		int errorCode = 0;
		if (pProtocol->numAIChannels > 0) errorCode = DAQmxReadAnalogF64(pProtocol->AItaskHandle, DAQmx_Val_Auto, pProtocol->timeoutSec, DAQmx_Val_GroupByScanNumber, pProtocol->pDataAI, pProtocol->numSamples * pProtocol->numAIChannels, (int32*)&read, NULL);
		if (pProtocol->numDIChannels > 0) errorCode = DAQmxReadDigitalU32(pProtocol->DItaskHandle, DAQmx_Val_Auto, pProtocol->timeoutSec, DAQmx_Val_GroupByScanNumber, pProtocol->pDataDI, pProtocol->numSamples * pProtocol->numDIChannels, (int32*)&read, NULL);
		pProtocol->bGotData = true;


		//the bShutdown flag could become true when this point is arrived
		//set the bReadyToShutdown flag which will stop and clear the tasks
		//this function will not enter again and so the reference for pProtocol will not be used again
		if (pProtocol->bShutdown) {
			pProtocol->ccout("set ready to shutdown signal");
			pProtocol->bReadyToShutdown = true; //allow the handles to be shutdown
		}
		pProtocol->ccout("end NCallback()");
	}
	else {
		//this function is called after the bShutdown message, allow bReadyToShutdown = true so that tasks can be stopped and cleared.
		pProtocol->bReadyToShutdown = true;
	}
	return 0;
}

