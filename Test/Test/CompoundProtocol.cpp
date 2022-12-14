#include "CompoundProtocol.h"



CompoundProtocol::CompoundProtocol() {
	//set everything to null values
	//get the HPC frequency

	//if (bDebug) {
	//	(*pOfs) << "CompoundProtocol()" << endl;
	//}
	szProtocolFolder = "";
	deviceID = 0;
	minV = -10.0;
	maxV = 10.0;

	bRepeat = false;
	bStartTrigger = false;
	timingMethod = 0;
	pFIx = 0;
	bDirty = false;
	bCompound = false;

	pDOInject = NULL;
	pAOInject = NULL;

	curRepeat = 0;
	LARGE_INTEGER li;
	QueryPerformanceFrequency(&li);
	PCFreq = double(li.QuadPart) / 1000.0;
}
CompoundProtocol::~CompoundProtocol() {
	if (bDebug) {
		(*pOfs) << "~CompoundProtocol" << endl;
	}
	ShutdownProtocol();
}
/*
InitProtocol	reads and parses the protocol file csvProt.  The file csvProt along with the references within it are contained within the
				the folder szFolder (so everything is in there).  Each line of the protocol file is parsed to produce a vector of times v_Timings,
				names and repetitions vRepeats.  The list of names is sorted to produce a list of unique names as protocols can be used more than 
				once. These are then used to create a vector of SimpleProtocols vProtocols.  Each name is then associated with the index of the
				protocol in vProtocols to give vID.  So each vID (which indexes into vProtocols) is repeaed vRepeats and should occur if software timing
				is used at vTimings.
				In summary this function initialised all of the SimpleProtocols and sets up 4 vectors vID, vRepeats, vProtocols and vTimings.

string csvProt	the name of the protocol, this can be simple or compound. In either case it must be precisely laid out otherwise it will
				not parse properly. Also it is not the full path name.

string szFolder the folder name - complete with final slash eg .\\myfolder\\  - if you miss the final slash then it wont find the file 

bool bCompound	true if it as compound protocol else false for a simple protocol

bool bRepeat	to repeat the entire protocol. This works for all timing options. In the case of PC timing - the 'clock' starts at 0 when it recycles

int deviceID	will be contanenated with Dev to give Dev1 etc

double minV	
double maxV		max and minimum voltage in the signal - match as closely as possible to effectively use the dynamical range of the NIDAQ

*/
int		CompoundProtocol::InitProtocol(string csvProt, string szFolder, bool bCompound, bool bRepeat, int deviceID, double minV, double maxV, string deviceName) {

	if (bDebug) {
		(*pOfs) << "CompoundProtocol::InitProtocol" << endl;
	}
	//removes the previous simple protocols and vectors - will trigger clear instructions in the simple protocols
	ShutdownProtocol();
	this->szDeviceName = deviceName;
	this->deviceID = deviceID;
	this->minV = minV;
	this->maxV = maxV;
	this->bCompound = bCompound;
	this->bRepeat = bRepeat;
	this->szProtocolFolder = szFolder;
	if (bCompound) {
		//	//read the lines of the compound.csv
		ifstream ifs((szProtocolFolder + csvProt).c_str());
		//MessageBoxA(NULL, "InitProtocol", csvProt.c_str(), MB_OK);
		//MessageBoxA(NULL, szProtocolFolder.c_str(), csvProt.c_str(), MB_OK);
		vector<string> v_lines;
		string szLine;
		while (getline(ifs, szLine)) {
			//MessageBoxA(NULL, "Read", szLine.c_str(), MB_OK);
			v_lines.push_back(szLine);
		}
		ifs.close();
		//MessageBoxA(NULL, "Finished Read", "", MB_OK);
		//	//
		//	//go through compound.csv
		//	// 12123 "ddd.csv" 5   //use a spaced list rather than a csv for now change on the next iteration
		long timeProt, reps;
		string csvProt;
		vector <string> vNames;
		for (int f = 0; f < v_lines.size(); f++) {
			stringstream ss(v_lines[f]);
			ss >> timeProt >> csvProt >> reps;
			vTiming.push_back(timeProt);
			vRepeats.push_back(reps);
			vNames.push_back(csvProt);
		}
		//	//
		//	// find the unique names
		vector <string> vUniqueNames;
		for (int f = 0; f < vNames.size(); f++) {
			bool bUnique = true;
			for (int ff = 0; ff < vUniqueNames.size(); ff++) {
				if (vNames[f] == vUniqueNames[ff]) {
					bUnique = false;
					break;
				}
			}
			if (bUnique) vUniqueNames.push_back(vNames[f]);
		}
		//	//
			// for each vUniqueName find a Protocol2* and store into vProtocols
		for (int f = 0; f < vUniqueNames.size(); f++) {
			SimpleProtocol* pdef = new SimpleProtocol(deviceID, -10.0, 10.0, szDeviceName);
			pdef->Init((char*)(szProtocolFolder + vUniqueNames[f]).c_str());
			vProtocols.push_back(pdef);
		}
		//
		//find vProtocols  and vID
		for (int f = 0; f < vNames.size(); f++) {
			for (int ff = 0; ff < vUniqueNames.size(); ff++) {
				if (vNames[f] == vUniqueNames[ff]) {
					vID.push_back(ff);
					break;
				}
			}
		}
		//
		//so we have a list of ids vID and each id indexes in vProtocols
		curProtocol = 0;
		curRepeat = 0;

		//stringstream ss;
		//ss << vID.size() << " " << vRepeats.size();
		//MessageBoxA(NULL, ss.str().c_str(), "results", MB_OK);
	}
	else {
		//	load a simple protocol
		SimpleProtocol* prot = new SimpleProtocol(deviceID, minV, maxV, szDeviceName);
		prot->Init((char*)(szProtocolFolder + csvProt).c_str());
		vProtocols.push_back(prot);
		vID.push_back(0);
		vRepeats.push_back(1);
		curProtocol = 0; //start at the beginning
		curRepeat = 0; // there is only 1 repeat
	}
	return 1; // has no meaning in this case  TO DO give 0 if parsing error
}

/*
SetStartTrigger		user can specify that the protocol uses a start trigger and whether the start trigger is a rising or falling edge
					also the timeout can be specified -1 means an infinite timeout - and generally makes things straight forwards. If it
					goes over a timeout then this will trigger an error alert
					For pFIx the Cairn NIDAQ 6343 can use PFI 0/0 0/1 0/5 as triggers

*/
int		CompoundProtocol::SetStartTrigger(bool bStartTrigger, uInt32 pFIx, bool bRisingEdge, double timeoutSec) {
	if (bDebug) {
		(*pOfs) << "CompoundProtocol::SetStartTrigger" << endl;
	}
	
	this->bRisingEdge = bRisingEdge;
	this->timeoutSec = timeoutSec;
	this->bStartTrigger = bStartTrigger;
	this->pFIx = pFIx;
	return 1; // no meaning
}
/*
SetTimingMethod		There are 3 timing methods:
					0	the simple protocols are played consecutively on the NIDAQ - the next being played as soon as the previous is finished
					1	the simple protocols are triggered by the previously defined trigger signal. In this way the NIDAQ can be used as a 
						slave in a more complicated setup and also benefit from accurate electronic timing,
					2	PC timing using the values in vTimings.  The program will wait (spinning) until it arrives at the stated time from the
						beginning of the program. Then all of the repetitions will play out before another delay for the next time.
						This entire process will repeat from the beginning if the repeat option has been selected.


*/
int		CompoundProtocol::SetTimingMethod(int timingMethod) {
	if (bDebug) {
		(*pOfs) << "CompoundProtocol::SetTimingMethod" << endl;
	}
	
		this->timingMethod = timingMethod;
		return 1; // no meaning
}
/*
int RunNext		This method causes the NIDAQ to run the next stage in the compound protocol - this is the stage corresponding to the
				current version of curProtocol and curRepeats - at the end of this process the value of curProcess and curRepeats might
				be different. Some getter functions use curProcess and curRepeat to find out information about the simple protocol. So it
				is important to be clear which simple protocol is being referred to.  It always refers to the next protocol about to run.
				The function will fill arrays for pTime (a PC based estimate of time), AO, AI, DO, DI - these arrays were originally passed
				to the function to be filled. This means that the calling program needs to know the size of the arrays required.

				A simple trick is to ensure that all of the SimpleProtocols have arrays of constant size.

				UpdateDO and UpdateAO are performed at the level of the simple protocol.  Which takes care of releasing and loading the 
				protocol.

double* pTimes	estimate time for each sample in sec using PC time from some arbitrary start point

double* dataAO, *dataAI
uInt32* dataDO, *dataDI			filled attays of data

bool* pSuccess		true if no problems else false and usually accompanied with an error alert

*/

int		CompoundProtocol::RunNext(double* pTimes, double* dataAO, double* dataAI, uInt32* dataDO, uInt32* dataDI, bool* pSuccess) {
	if (bDebug) {
		(*pOfs) << "CompoundProtocol::RunNext" << endl;
	}
	
		int retVal = -1;
		if (bCompound) {
			//cout << "curRepeat " << curRepeat << " curProtocol " << curProtocol << endl;

			//to simplify things the different timingMethods have been treated separately
			//
			if (timingMethod == 0) {
				//consecutive
				if (curRepeat == 0) {
					vProtocols[vID[curProtocol]]->InitDAQ();
					if (curProtocol == 0) {
						//cout << "1st run of the entire compound protocol " << endl;
						//cout << "*******apply the start trigger********" << endl;
						vProtocols[vID[curProtocol]]->RunProtocolDAQ(pTimes, dataAO, dataAI, dataDO, dataDI, bStartTrigger, bRisingEdge, pFIx, timeoutSec);
						vProtocols[vID[curProtocol]]->ReleaseDAQ();
						vProtocols[vID[curProtocol]]->InitDAQ();
						//when there has been any change to the NIDAQ once it has entered a commit state (ie it run) then that change
						//is baked in and the NIDAQ needs to be released and reinitialised. In this case the trigger status changed
						//which meant that it needed to be reset.
					}
					else {
						//cout << "1st in a repeat sequence" << endl;
						//cout << "*********initialise vProtocol[vID[" << curProtocol << "]] " << endl;
						//

						//cout << "**********run protocol  vProtocol[vID[" << curProtocol << "]] " << endl;
						//the remaing times no trigger is used
						vProtocols[vID[curProtocol]]->RunProtocolDAQ(pTimes, dataAO, dataAI, dataDO, dataDI, false, true, 0, -1);

					}
				}
				else if (curRepeat == vRepeats[curProtocol] - 1) {
					//the last repeat of thei protocol
					// 
					//cout << "last in the repeat sequence" << endl;
					//cout << "**********run protocol  vProtocol[vID[" << curProtocol << "]] " << endl;
					//cout << "***********release vProtocol[vID[" << curProtocol << "]] " << endl;
					//
					//cout << "run without trigger " << endl;

					//at the end of each sequence of repeats the protocol needs to be released
					vProtocols[vID[curProtocol]]->RunProtocolDAQ(pTimes, dataAO, dataAI, dataDO, dataDI, false, true, 0, -1);
					vProtocols[vID[curProtocol]]->ReleaseDAQ();


				}
				else {
					//cout << "**********run protocol  vProtocol[vID[" << curProtocol << "]] " << endl;
					//cout << "run without trigger " << endl;
					vProtocols[vID[curProtocol]]->RunProtocolDAQ(pTimes, dataAO, dataAI, dataDO, dataDI, false, true, 0, -1);
				}
			}
			else if (timingMethod == 1) {
				//triggered
				if (curRepeat == 0) {
					//cout << "1st in a repeat sequence" << endl;
					//cout << "*********initialise vProtocol[vID[" << curProtocol << "]] " << endl;
					//cout << "**********run protocol  vProtocol[vID[" << curProtocol << "]] " << endl;
					//
					//
					vProtocols[vID[curProtocol]]->InitDAQ();
					vProtocols[vID[curProtocol]]->RunProtocolDAQ(pTimes, dataAO, dataAI, dataDO, dataDI, bStartTrigger, bRisingEdge, pFIx, timeoutSec);
				}
				else if (curRepeat == vRepeats[curProtocol] - 1) {
					//the last repeat of thei protocol
					// 
					//cout << "last in the repeat sequence" << endl;
					//cout << "**********run protocol  vProtocol[vID[" << curProtocol << "]] " << endl;
					//cout << "***********release vProtocol[vID[" << curProtocol << "]] " << endl;
					//

					vProtocols[vID[curProtocol]]->RunProtocolDAQ(pTimes, dataAO, dataAI, dataDO, dataDI, bStartTrigger, bRisingEdge, pFIx, timeoutSec);
					vProtocols[vID[curProtocol]]->ReleaseDAQ();


				}
				else {
					//cout << "**********run protocol  vProtocol[vID[" << curProtocol << "]] " << endl;

					vProtocols[vID[curProtocol]]->RunProtocolDAQ(pTimes, dataAO, dataAI, dataDO, dataDI, bStartTrigger, bRisingEdge, pFIx, timeoutSec);
				}

			}
			else {
				//PC timed
				if (curRepeat == 0) {
					if (curProtocol == 0) {
						cout << "first entry in protocol" << endl;
						//capture the time
						LARGE_INTEGER li;
						QueryPerformanceCounter(&li);
						startTime = double(li.QuadPart) / PCFreq / 1000.0; // start time in Sec
					}
					//cout << "1st in a repeat sequence" << endl;
					//cout << "*********initialise vProtocol[vID[" << curProtocol << "]] " << endl;
					//cout << "**********run protocol  vProtocol[vID[" << curProtocol << "]] " << endl;
					//
					//
					vProtocols[vID[curProtocol]]->InitDAQ();
					LARGE_INTEGER li;
					QueryPerformanceCounter(&li);
					double currentTime = double(li.QuadPart) / PCFreq / 1000.0; // start time in Sec
					while (currentTime < startTime + vTiming[curProtocol]) {
						QueryPerformanceCounter(&li);
						currentTime = double(li.QuadPart) / PCFreq / 1000.0; //spin

					}
					//cout << "******************" << currentTime - startTime << endl;
					vProtocols[vID[curProtocol]]->RunProtocolDAQ(pTimes, dataAO, dataAI, dataDO, dataDI, false, true, 0, -1);
				}
				else if (curRepeat == vRepeats[curProtocol] - 1) {
					//the last repeat of thei protocol
					// 
					//cout << "last in the repeat sequence" << endl;
					//cout << "**********run protocol  vProtocol[vID[" << curProtocol << "]] " << endl;
					//cout << "***********release vProtocol[vID[" << curProtocol << "]] " << endl;
					//
					vProtocols[vID[curProtocol]]->RunProtocolDAQ(pTimes, dataAO, dataAI, dataDO, dataDI, false, true, 0, -1);
					vProtocols[vID[curProtocol]]->ReleaseDAQ();
				}
				else {
					//cout << "**********run protocol  vProtocol[vID[" << curProtocol << "]] " << endl;
					vProtocols[vID[curProtocol]]->RunProtocolDAQ(pTimes, dataAO, dataAI, dataDO, dataDI, false, true, 0, -1);
				}
			}
			retVal = 1;
			curRepeat++;
			if (curRepeat == vRepeats[curProtocol]) {
				curProtocol++;
				curRepeat = 0;
			}
			if (curProtocol == vID.size()) {
				//this will repeat the entire compound protocol including the initial start trigger if there is one.
				if (bRepeat) {
					curProtocol = 0;
				}
				else retVal = -1;
			}
		}
		else {
		//just run the only protocol in the vector of protocols
			vProtocols[0]->InitDAQ();
			vProtocols[0]->RunProtocolDAQ(pTimes, dataAO, dataAI, dataDO, dataDI, bStartTrigger, bRisingEdge, pFIx, timeoutSec);
			vProtocols[0]->ReleaseDAQ();
			retVal =  (bRepeat) ? 1 : -1;
		}

		return retVal;
}
/*
UpdateDOChannel	  


*/
int		CompoundProtocol::UpdateDOChannel(uInt32* pData, int line) {
	//this data pData will replace the data in dataDO on line (which must be a previously defined 
	// line) with the next calling of RunNext
	//this means that a program can change its output in response to some input
	//
	vProtocols[vID[curProtocol]]->UpdateDOChannel(pData, line);
	line_inject = line;
	bDOInject = true;
	return 1;
}
int		CompoundProtocol::UpdateAOChannel(double* pData, int channel) {

	vProtocols[vID[curProtocol]]->UpdateAOChannel(pData, channel);
	channel_inject = channel;
	bAOInject = true;
	return 1;
}
/*
ShutdownProtocol	Calls ReleaseDAQ() on all of the protocols and then clear() each - which means that all of the resources
					are released from daqmx

*/
int		CompoundProtocol::ShutdownProtocol() {
	
	if (bDebug) {
		(*pOfs) << "CompoundProtocol::ShutdownProtocol" << endl;
	}
			for (int f = 0; f < vProtocols.size(); f++) {
				if (vProtocols[f]) {
					vProtocols[f]->ReleaseDAQ();
					delete vProtocols[f];
				}
			}
			vProtocols.clear();
			vID.clear();
			vRepeats.clear();
			vTiming.clear();


			return 1;
}


int		CompoundProtocol::TerminateProtocol() {
	if (bDebug) {
		(*pOfs) << "CompoundProtocol::TerminateProtocol" << endl;
	}
	vProtocols[0]->TerminateProtocol();
	//ShutdownProtocol();

	return 0;


}

/*
GetBufferDimensions		This function returns the buffer dimensions required for the next protocol about to run.  It used functions like
						getNextNumSamples which refers to the next protocol about to run. So this function can be used to quickly get information
						to check the size of a buffer and maybe reallocate the memory if required.

*/
void	CompoundProtocol::GetBufferDimensions(int* numSamples, int* numAOChannels, int* numAIChannels, int* numDOChannels, int* numDIChannels) {
	if (bDebug) {
		(*pOfs) << "CompoundProtocol::GetBufferDimensions" << endl;
	}
	
		*numSamples = GetNextNumSamples();
		*numAOChannels = GetNumChannels(0);
		*numAIChannels = GetNumChannels(1);
		*numDOChannels = GetNumChannels(2);
		*numDIChannels = GetNumChannels(3);
}
/*
GetNextNumSamples   this returns the number of sample of the next simple protocol to run 
					this will be the protocol pointed to by curProtocol  (so at the end of a set of reps this can change).
					This is the reason for putting the word Next into this function name.



*/
int		CompoundProtocol::GetNextNumSamples() {
	
	if (bDebug) {
		(*pOfs) << "CompoundProtocol::GetNextNumSamples" << endl;
	}
	
	if (bCompound) {
		return vProtocols[vID[curProtocol]]->GetNumSamples();
	}
	else {
		return vProtocols[0]->GetNumSamples();
	}
}
/*
GetNumberOfDataPoints	This returns the total number of samples obver the entire experiment given the numerous protocols.
*/
int		CompoundProtocol::GetNumberOfDataPoints() {
	if (bDebug) {
		(*pOfs) << "CompoundProtocol::GetNumberOfDataPoints" << endl;
	}
	//this is the total number of samples in the entire experiment
	int num = 0;
	if (bCompound) {
		for (int f = 0; f < vID.size(); f++) {
			num += vRepeats[f] * vProtocols[vID[f]]->GetNumSamples();
		}
	}
	else {
		num = vProtocols[0]->GetNumSamples();
	}
	return num;
}
/*
GetCurrentRunSampleTime	 For the run about to take place next (ie curProtocol) tells you in seconds the time for 1 sample i.e. 1/samplingFrequency.
*/
double	CompoundProtocol::GetCurrentRunSampleTime() {
	
	if (bDebug) {
		(*pOfs) << "CompoundProtocol::GetCurrentRunSampleTime" << endl;
	}
	
	//time for each sample in s
	if (bCompound) {
		return 1.0 / vProtocols[vID[curProtocol]]->GetSampleFreq();

	}
	else {
		return 1.0 / vProtocols[0]->GetSampleFreq();
	}
}
/*
GetNumberOfStages	Returns the total number of stages that is simpleprotocols in this experiment (this includes repetitions).

*/
long	CompoundProtocol::GetNumberOfStages() {
	if (bDebug) {
		(*pOfs) << "CompoundProtocol::GetNumberOfStages" << endl;
	}
	
	if (bCompound) {
		int tot = 0;
		for (int f = 0; f < vID.size(); f++) {
			tot += vRepeats[f];
		}
		return tot;
	}
	else return 1; //because it is single

}
/*
GetNumChannels	Returns the number of channels in the next simple protocol to run of a particular type
				0 = AO  1 = AI   2 = DO   3 = DI

*/
int		CompoundProtocol::GetNumChannels(int type) {
	if (bDebug) {
		(*pOfs) << "CompoundProtocol::GetNumChannels" << endl;
	}
	
	return vProtocols[vID[curProtocol]]->GetNumChannels(type);
}
/*
GetChannelFromIndex		For the next protocol to run returns the channel number correspond to a particular index
						we could have selected the channels 0, 3, 7 but then would have had the index 0, 1, 2
						the type argument is the same as above.

*/
int		CompoundProtocol::GetChannelFromIndex(int type, int ix) {
	if (bDebug) {
		(*pOfs) << "CompoundProtocol::GetChannelFromIndex" << endl;
	}
	
	return vProtocols[vID[curProtocol]]->GetChannelFromIndex(type, ix);
}
/*
* GetPort		Returns the port (bIn for input vs output) for the next protocol about to run.
*/
int		CompoundProtocol::GetPort(bool bIn) {
	if (bDebug) {
		(*pOfs) << "CompoundProtocol::GetPort" << endl;
	}
	return vProtocols[vID[curProtocol]]->GetPort(bIn);
}
/*
GetDeviceID		Gets the device for the next protocol about to run  - in a compound protocol they always have the same device.

*/
int		CompoundProtocol::GetDeviceID() {
	
	if (bDebug) {
		(*pOfs) << "CompoundProtocol::GetDeviceID" << endl;
	}
	return vProtocols[vID[curProtocol]]->GetDeviceID();
}
/*
GetCurrentSystemTime	returns the current PC time using the Performance Counter from some arbitary start.

*/
double	CompoundProtocol::GetCurrentSystemTime() {
	if (bDebug) {
		(*pOfs) << "CompoundProtocol::GetCurrentSystemTime" << endl;
	}
	
		LARGE_INTEGER li;
		QueryPerformanceCounter(&li);
		return double(li.QuadPart) / PCFreq / 1000.0;  // in sec
}


