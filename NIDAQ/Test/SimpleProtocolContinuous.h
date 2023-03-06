#pragma once
#include "nidaqmx.h"
#include <string>
#include <sstream>
#include <iostream>
#include <fstream>
#include <vector>
#include <Windows.h>
using namespace std;


class SimpleProtocolContinuous
{
public:
	SimpleProtocolContinuous();
	~SimpleProtocolContinuous();


	bool	Init(int deviceID, char* szCsv, bool bStartTrigger, bool bRisingEdge, uInt32 pFIx, double timeoutSec, double minV, double maxV);
	bool	Run(double* pTimeSec, double* DataAO, double* DataAI, uInt32* DataDO, uInt32* DataDI);
	bool	Shutdown();



	static int32 CVICALLBACK EveryNCallback(TaskHandle taskHandle, int32 everyNsamplesEventType, uInt32 nSamples, void* callbackData);
	int		GetNumAOChannels();
	int		GetNumDOChannels();
	int		GetNumAIChannels();
	int		GetNumDIChannels();
	int		GetNumSamples();
	double  GetSampleFreq();
	int		GetContinuousChannelFromIndex(int type, int index);
	int		GetContinuousDOPort();
	int		GetContinuousDIPort();


	void	con(int in, int x, int y);
	void	con(string in, int x, int y);
	void ccout(string sz) {
		if (bDebug) cout << deviceID << " " << sz << endl;

	}
private:
	static bool bDebug;

	bool bShutdown;
	bool bReadyToShutdown;
	bool bReadyToShutodwn1;
	bool bError, bStartTrigger, bFirst;
	int triggerType;
	uInt32 pFIx;
	bool bInRunMethod;
	int deviceID;
	double minV, maxV;
	double sampleFreq;
	vector <int> AIChannels;
	vector <int> AOChannels;
	vector <int> DIChannels;
	vector <int> DOChannels;
	int DIPort, DOPort;


	double* pDataAI, * pDataAO;
	uInt32* pDataDI, * pDataDO;
	bool bGotData;
	double curTotalTime;
	double timeoutSec;
	int numAIChannels, numAOChannels, numDIChannels, numDOChannels;
	TaskHandle AItaskHandle, AOtaskHandle, DOtaskHandle, DItaskHandle;
	int numSamples;
	bool bRunning;
	bool bUsingDO, bUsingDI, bUsingAO, bUsingAI;

	bool bStop;
	bool bStopped;









};

