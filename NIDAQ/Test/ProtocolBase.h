#pragma once
#include "nidaqmx.h"
#include <string>
#include <sstream>
#include <iostream>
#include <fstream>
#include <vector>
#include <Windows.h>
using namespace std;

class ProtocolBase
{
public:
	ProtocolBase();
	~ProtocolBase();
	int		GetNumAOChannels() {
		return numAOChannels;
	}
	int		GetNumDOChannels() {
		return numDOChannels;
	}
	int		GetNumAIChannels() {
		return numAIChannels;
	}
	int		GetNumDIChannels() {
		return numDIChannels;
	}
	int		GetDOPort() {
		return DOPort;
	}
	int		GetDIPort() {
		return DIPort;
	}
	int		GetNumSamples() {
		return numSamples;

	}
	double GetSampleFreq() {
		return sampleFreq;
	}
	vector<int> GetAOChannels() {
		return AOChannels;
	}
	vector<int> GetDOChannels() {
		return DOChannels;
	}
	vector<int> GetAIChannels() {
		return AIChannels;
	}
	vector<int> GetDIChannels() {
		return DIChannels;
	}
	vector<int> GetDOData() {
		return v_DO;
	}
	vector<double> GetAOData() {
		return v_AO;
	}

	bool	ReadProtocol(string szProt);
protected:
	int numAIChannels, numAOChannels, numDIChannels, numDOChannels;
	int numSamples;
	double sampleFreq;
	int DIPort, DOPort;
	vector <int> AIChannels;
	vector <int> AOChannels;
	vector <int> DIChannels;
	vector <int> DOChannels;

	vector <int> v_DO;
	vector <double> v_AO;

};

