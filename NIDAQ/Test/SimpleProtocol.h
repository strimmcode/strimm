/*
@file SimpleProtocol.h
@author Terry Wright  
@email tw567@cam.ac.uk

SimpleProtocol runs a protocol in a single shot manner on a NIDAQ6343 (lower spec NIDAQ eg 6001 will only 
be able to use a subset of the functionality).  The protocol is specified in a text file - where the behaviour
of the NIDAQ is specified in a csv format. The format of these files is self documented and they can be found
in ./ExampleProtocols/ . Protocols which only have AO,DO  or   AI,DI    or    AI,DO,DI   are currently not
supported however all other options are supported (eg AO, AI, DO, DI). 
The SimpleProtocol object requires the user to have some awareness that daqmx is running and being managed by this object
although very little is needed.  The CompoundProtocol does not require any awareness of daqmx (unless an error message pops up).

It is possible to upload very large protocols (1000000's of samples) however this results in a very long upload
time, 10s of minutes.  For short protocols < 1000 samples the upload time is less than 1 sec.

SimpleProtocol(int deviceID, double minV, double maxV)   - several NIDAQs can be attached to the system each running their own simple protocol. 
choose minV and maxV to be close to the min and max of the signal for a better fidelity representation of the signal

~SimpleProtocol  - destroys non-NIDAQ resources

Init(char* csv)  loads all of the non-NIDAQ resources.  It parses the csv files and initialised the SimpleProtocol object.
One should choose a sampling frequency which is at least twice the maximum frequency in the sample.

InitDAQ()  initialised all of the NIDAQ components and interacts with daqmx.  This needs to be called every time a new protocol
needs to be loaded into the NIDAQ
ReleaseDAQ()  similarly releases the NIDAQ resources, and needs to be used with each InitDAQ().

RunProtocolDAQ(...)  this function runs the protocol. It is a sychronous function and will not return until after the protocol.
It can be repeatedly called without needing to reload the protocol, so to repeat a protocol eg:

InitDAQ();
for (int f=0; f<100; f++)  RunProtocolDAQ();
ReleaseDAQ();

There is a slight delay (10-100ms) between each time the protocol is run - and the size of this depends on the size of the protocol
for time critical applications this is best measured with a scope.  To avoid it the all of the repetitions should be rolled into a large 
single protocol and uploaded to the NIDAQ.

If part of the protocol needs to change then this can be achieved with UpdateDOChannel and UpdateAOChannel - the changes will be in place
for the next time RunProtocolDAQ is called.  Behind the scenes this will ReleaseDAQ, change the internal representation of the protocol
and then InitDAQ - so it is comparatively expensive and there can be a time delay.


InitDAQ()
RunProtocolDAQ(...)
UpdateDOChannel(...)
RunProtocolDAQ(...)
UpdateAOChannel(...)
RunProtocolDAQ(...)
ReleaseDAQ()

For the arguments of  RunProtocolDAQ(pTimeSec, pDataAO, pDataAI, pDataDO, pDataDI,...
, you supply arrays of the right size and each time the function is run it will fill in the times of each sample (estimated by the PC time)
the AO,AI, DO, DI values used/obtained in the simple protocol.  If the protocol does not use a particular subsystem eg AO then send
a NULL pointer. 

The remaining arguments  RunProtocolDAQ(......  bool bStartTrigger, bool bRisingEdge, uInt32 pFIx, double timeoutSec). You can specify a start trigger
and also if it is rising or falling edge.  The pFIx gives the location of the start trigger for the NIDAQ 6343 it needs to be PFI 0/0 0/1 1/0.
Lastly the timeoutSec in sec, gives the timeout to wait for a trigger. If a trigger does not arrive then it is assumed to be an error
and it will alert the user of the class.  To have an infinite timeout pass the value -1.  With inifinite time-outs the object will essentially
wait forever - and this could cause problems for other objects that are waiting for it to complete.

In STRIMM the timeout used by simple protocols can be specified by the JSON







*/

#pragma once
#include "nidaqmx.h"
#include <string>
#include <sstream>
#include <iostream>
#include <fstream>
#include <vector>
#include <Windows.h>
using namespace std;

class SimpleProtocol
{
public:
	/*
	* cstr	choose the deviceID will internally be of the form Dev0, Dev1 etc 
			also the range of the signal needs to be between minV and maxV or assumed 
			to be an error and the user is alerted
	*/
	SimpleProtocol(int deviceID, double minV, double maxV);
	/*
	* dstr	releases the non NIDAQ resources
	*/
	~SimpleProtocol();

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





