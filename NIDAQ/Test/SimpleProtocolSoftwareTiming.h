
#pragma once
#include "nidaqmx.h"
#include <string>
#include <sstream>
#include <iostream>
#include <fstream>
#include <vector>
#include <Windows.h>
using namespace std;


class SimpleProtocolSoftwareTiming
{
public:
	/*
	* cstr	choose the deviceID will internally be of the form Dev0, Dev1 etc
			also the range of the signal needs to be between minV and maxV or assumed
			to be an error and the user is alerted
	*/
	SimpleProtocolSoftwareTiming(int deviceID, double minV, double maxV);
	/*
	* dstr	releases the non NIDAQ resources
	*/
	~SimpleProtocolSoftwareTiming();

	void SetRepeat(bool bRep) {
		bRepeat = bRep;
	}
	/*
	* Init	reads the simple protocol from a text file and initialised its internal structures
	*/
	bool	Init(char* sz);
	/*
	* UpdateDOChannel	Can change a line of DO for the simple protocol - which is ready for the next time the protocol is run
	* uInt32*	pData		samples to update a line of the simple protocol  - it is 1 x numSamples
	* int		line		the line to be updated - the port is defined by the text file of the simple protocol


	*/
	bool	UpdateDOChannel(uInt32* pData, int line);
	/*
	* UpdateAOChannel	Can change a channel of AO - ready for the next time the protocol is run.
	* double*	pData		samples to update a single channel in the simple protocol - 1 x numSamples
	* int		channel		the channel to be updated on the next run of the protocol


	*/
	bool	UpdateAOChannel(double* pData, int channel);
	/*
	* InitDAQ			Initialises the NIDAQ structures by using daqmx calls. Will load the protocol onto the daq.

	*/
	bool	InitDAQ();
	/*
	* ReleaseDAQ		Releases the NIDAQ resources - every InitDAQ must be released with ReleaseDAQ

	*/
	bool	ReleaseDAQ();
	/*
	* RunProtocolDAQ				'Plays' the simple protocol on the NIDAQ according to the specifications of the defining text file, it is sychronous and will block until completed
	* double		*pTimeSec		Pass an array to fill up with time estimates for each sample (sec) using PC time
	  double		*pDataAO		For m AO channels and n samples, pass a m*n array of doubles to be filled with AO data.
	  double		*pDataAI		For m AI channels and n samples, pass a m*n array of doubles to be filled with AI data.
	  uInt32		*pDataDO		For m DO lines and n samples, pass a m*n array of uInt32 to be filled
	  uInt32		*pDataDI		For m DI lines and n samples, pass a m*n array of uInt32 to be filled
	  bool			bStartTrigger	Use a start trigger
	  bool			bRisingEdge		True for a rising edge trigger and false for a falling edge trigger
	  uInt32		pFIx			The PFI input for the start trigger
	  double		timeoutSec		Timeout in sec (-1) is an infinite timeout


	*/
	bool	RunProtocolDAQ(double* pTimeSec, double* pDataAO, double* pDataAI, uInt32* pDataDO, uInt32* dataDI,
		bool bStartTrigger, bool bRisingEdge, uInt32 pFIx, double timeoutSec);
	/*
	* Getters

	*/


	/*
	GetSampleFreq				frequency in Hz
	*/
	double	GetSampleFreq();
	int		GetNumChannels(int type);
	int		GetChannelFromIndex(int type, int ix);
	int		GetPort(bool bIn);
	int		GetDeviceID();
	/*
	GetCurrentRunSampleTime		time for 1 sample (sec)
	*/
	double	GetCurrentRunSampleTime();
	int		GetIndexFromChannel(int type, int ch);

	int		GetNumSamples();
	int		GetNumAOChannels();
	int		GetNumAIChannels();
	int		GetNumDOChannels();
	int		GetNumDIChannels();

	void	con(int in, int x, int y);
	void	con(string in, int x, int y);

private:
	bool bContinuous;
	int numSamples;
	double sampleFreq;
	int numAIChannels, numAOChannels, numDIChannels, numDOChannels;
	TaskHandle AItaskHandle, AOtaskHandle, DOtaskHandle, DItaskHandle;
	vector <int> AIChannels;
	vector <int> AOChannels;
	vector <int> DIChannels;
	vector <int> DOChannels;
	int DIPort, DOPort;
	double* dataAO, * dataAO_back;
	uInt32* dataDO, * dataDO_back;
	bool bDirtyAO, bDirtyDO, bError, bStartTrigger, bRepeat;
	bool bFirst;
	int deviceID;
	double minV, maxV;
	uInt32 pFIx;
	//windows high performance timer
	double PCFreq = 0.0;
	__int64 CounterStart = 0;
};

