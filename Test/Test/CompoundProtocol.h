/*
@file	CompoundProtocol.h
@author Terry Wright
@email	tw567@cam.ac.uk

CompoundProtocol runs a sequence of SimpleProtocols.  There is no need to interact with daqmx directly. The CompoundProtocol object
can run simple as well as compound protocols.  The compound protocol can be initiated with a start trigger (which can be
a rising edge or a falling edge), the timeout for the trigger can also be specified.  The CompoundProtocol object will
report an error if it goes past its timeout. Additionally the compound protocol can be arranged to repeat - however there
is a slight delay between each simple protocol which makes up the compound protocol. 

There are 3 timing methods,  Timing method 0 plays the simple protocols consecutively and can have a start trigger.  If the sequence
repeats then it will also require anothe start trigger.  Timing method 1 required triggers for each simple protocols including
the repeats. Timing method 2 uses PC timing in order to trigger each group of repeats - although the separate simple protocols
are played consecutively.

InitProtocol(szCompound, szFolder, bCompound, bRepeat, deviceID, minV, maxV)  Initialises the compound protocol - the reference in the szCompound and joined with szFolder.
The bCompound can be used to play a compound or a simple protocol. When used for a simple protocol then there is very little need
to work with the daqmx (even less than using the SimpleProtocol object). The variable bRepeat allows the protocol to be repeatedly
executed over and over again and maxV and minV gives the max and min voltages of the signal and ideally these should be close to the
actual max and min of the input signal.  Initially this function will Shutdown the previous, and so the object can be reused with different
compound protocols.

SetStartTrigger(bStartTrigger, pFIx, bRisingEdge, timeoutSec)		This function allows a start trigger to be set for the compund protocol
along with whether to use a rising/lowering edge and also a timeout.

SetTimingMethod(timingMethod)	This function allows the selection of the timeing method for the compound protocol.  TimingMethod == 0 means
that the constituent simple protocols are played consecutively and sychronously.  TimingMethod == 1 means that each simple protocol including 
repeats is triggered.  This means that each simple protocol can be triggered electronically at precise times.  TimingMethod == 2 means that 
PC timing is used.  The Performance Timer determines when a a simple protocol starts - the repeats of a particular protocol are consecutive.

int RunNext(pTimes, dataAO, dataAI, dataDO, dataDI, pbSuccess)	This function triggers the next simple protocol in the compound protocol.
Arrays pTimes, dataAO, dataAI, dataDO, dataDI are supplied to the function (or NULL if those variables are not used), and will retrieve
the AO,AI,DO,DI values for that simple protocol.  The bSuccess provides an indicator of any errors in the simple protocol.  The return value
indicates whether the comppound protocol has finished (-1 indicates it has finished, 1 indicates that there are still further simple protocols).

It is important that the arrays pTimes, dataAO etc be of the correct size, one approach is to ensure that all of the simple protocols are of the same
size - however this is not necessary.  The following getter function can retrieve all of the dimensions:

GetBufferDimensions(int* numSamples, int* numAOChannels, int* numAIChannels, int* numDOChannels, int* numDIChannels)

ShutdownProtocol()  Releases all of the daqmx resources associated with each Simple Protocol

Additionally the AO and DO values for the next simple protocol (which will be used by RunNext) can be altered by:

UpdateDOChannel and UpdateAOChannel

The way in which the this object is used is:

InitProtocol(...)
SetStartTrigger(...)
SetTimingMethod(...)
GetBufferDimensions(...)
//allocate buffers
while (RunNext(...) > 0){
	RunNext(...)
	}
Shutdown()
//deallocate buffers

*/



#pragma once
#include "framework.h"
#include <Windows.h>
#include <string>
#include <vector>
#include "SimpleProtocol.h"
using namespace std;
extern bool bDebug;
extern ofstream* pOfs;

class CompoundProtocol
{
public:
	CompoundProtocol();
	~CompoundProtocol();

	int		InitProtocol(string csvProt, string szFolder, bool bCompound, bool bRepeat, int deviceID, double minV, double maxV, string szDeviceName);
	int		SetStartTrigger(bool bStartTrigger, uInt32 pFIx, bool bRisingEdge, double timeoutSec);
	int		SetTimingMethod(int timingMethod);
	//0 - consecutive, 1 - softwareTiming, 2 - external trigger, 3 - arduino trigger
	int		RunNext(double* pTimes, double* dataAO, double* dataAI, uInt32* dataDO, uInt32* dataDI, bool* pError);
	int		UpdateDOChannel(uInt32* pData, int line);
	int		UpdateAOChannel(double* pData, int channel);
	int		ShutdownProtocol();

	int		TerminateProtocol();
	


	//get information from the current protocol
	void	GetBufferDimensions(int* numSamples, int* numAOChannels, int* numAIChannels, int* numDOChannels, int* numDIChannels);
	int		GetNextNumSamples();
	int		GetNumberOfDataPoints();
	double	GetCurrentRunSampleTime();
	long	GetNumberOfStages();
	int		GetNumChannels(int type);
	int		GetChannelFromIndex(int type, int ix);
	int		GetPort(bool bIn);
	int		GetDeviceID();
	double	GetCurrentSystemTime();

private:
	int		curProtocol;
	int		curRepeat;
	uInt32* pDOInject;
	double* pAOInject;
	int		channel_inject;
	int		line_inject;
	bool	bAOInject;
	bool	bDOInject;

	int		deviceID;
	string szDeviceName;
	double	minV, maxV;

	bool	bRepeat;
	bool	bCompound;
	bool	bDirty;
	bool	bStartTrigger;
	bool	bRisingEdge;
	double	timeoutSec;
	uInt32	pFIx;
	int		timingMethod;

	vector<int> vID;
	vector<int> vRepeats;
	vector<int> vTiming;
	//
	vector<SimpleProtocol* > vProtocols;

	double startTime = 0.0;
	string szProtocolFolder;
	double PCFreq = 0.0;
	__int64 CounterStart = 0;


};

