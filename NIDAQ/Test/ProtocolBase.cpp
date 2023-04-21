#include "ProtocolBase.h"

ProtocolBase::ProtocolBase() {


	sampleFreq = 0.0;
	DIPort = 0;
	DOPort = 0;



	numAIChannels = 0;
	numAOChannels = 0;
	numDIChannels = 0;
	numDOChannels = 0;
	numSamples = 0;

}
ProtocolBase::~ProtocolBase() {



	AIChannels.clear();
	AOChannels.clear();
	DIChannels.clear();
	DOChannels.clear();

	v_AO.clear();
	v_DO.clear();

	numAIChannels = numAOChannels = numDIChannels = numDOChannels = 0;

}
bool	ProtocolBase::ReadProtocol(string szProt) {

	ifstream pIfs(szProt);
	string szLine;
	getline(pIfs, szLine);

	//make a vector of the cells in the 1st row
	stringstream szz;
	szz << szLine;
	string szWord;
	vector<string> v_line0;
	while (getline(szz, szWord, ',')) {
		v_line0.push_back(szWord);
	}


	//make a vector of the cells in the 2nd row
	string szLine1;
	getline(pIfs, szLine1);
	stringstream szz1;
	szz1 << szLine1;

	vector<string> v_line1;
	while (getline(szz1, szWord, ',')) {
		v_line1.push_back(szWord);

	}



	vector<int> v_AO_locs;
	vector<int> v_AI_locs;
	vector<int> v_DO_locs;
	vector<int> v_DI_locs;



	for (int f = 0; f < v_line0.size(); f++) {
		if (v_line0[f] == "numSamples") {
			stringstream szz;
			szz << v_line1[f];
			szz >> numSamples;

		}
		else if (v_line0[f] == "sampFreq") {
			stringstream szz;
			szz << v_line1[f];
			szz >> sampleFreq;
		}
		else if (v_line0[f] == "DOPort") {
			stringstream szz;
			szz << v_line1[f];
			szz >> DOPort;

		}
		else if (v_line0[f] == "DIPort") {
			stringstream szz;
			szz << v_line1[f];
			szz >> DIPort;
		}
		else if (v_line0[f].substr(0, 2) == "AO") {
			stringstream szz2;
			szz2 << v_line0[f].substr(2, 1);
			int channelVal = 0;
			szz2 >> channelVal;
			szz2.clear();
			AOChannels.push_back(channelVal);
			v_AO_locs.push_back(f);
			szz2 << v_line1[f];
			double val = 0.0;
			szz2 >> val;
			szz2.clear();
			v_AO.push_back(val);

		}
		else if (v_line0[f].substr(0, 2) == "AI") {
			stringstream szz2;
			szz2 << v_line0[f].substr(2, 1);
			int channelVal = 0;
			szz2 >> channelVal;
			szz2.clear();
			AIChannels.push_back(channelVal);
			v_AI_locs.push_back(f);

		}
		else if (v_line0[f].substr(0, 2) == "DO") {
			stringstream szz2;
			szz2 << v_line0[f].substr(2, 1);
			int channelVal = 0;
			szz2 >> channelVal;
			szz2.clear();
			DOChannels.push_back(channelVal);
			v_DO_locs.push_back(f);

			szz2 << v_line1[f];
			int val = 0;
			szz2 >> val;
			szz2.clear();
			//v_DO.push_back((int)(val * pow(2, channelVal)));
			v_DO.push_back((int)(val));
		}
		else if (v_line0[f].substr(0, 2) == "DI") {
			stringstream szz2;
			szz2 << v_line0[f].substr(2, 1);
			int channelVal = 0;
			szz2 >> channelVal;
			szz2.clear();
			DIChannels.push_back(channelVal);
			v_DI_locs.push_back(f);
		}
	}

	while (getline(pIfs, szLine)) {
		stringstream szz;
		szz << szLine;
		string szWord;
		vector<string> v_line00;
		while (getline(szz, szWord, ',')) {
			v_line00.push_back(szWord);
		}
		for (int f = 0; f < v_AO_locs.size(); f++) {
			stringstream szz2;
			szz2 << v_line00[v_AO_locs[f]];
			double val = 0.0;
			szz2 >> val;
			v_AO.push_back(val);
		}
		for (int f = 0; f < v_DO_locs.size(); f++) {
			stringstream szz2;
			szz2 << v_line00[v_DO_locs[f]];
			int val = 0;
			szz2 >> val;
			//v_DO.push_back((int)(val * pow(2, DOChannels[f])));
			v_DO.push_back((int)(val));
		}
	}

	pIfs.close();

	numAOChannels = (int)AOChannels.size();
	numAIChannels = (int)AIChannels.size();
	numDOChannels = (int)DOChannels.size();
	numDIChannels = (int)DIChannels.size();


	return true;
}
