// CreateCSV.cpp : This file contains the 'main' function. Program execution begins and ends there.
//

#include <iostream>
#include <sstream>
#include <string>
#include <fstream>
#include <vector>

using namespace std;

int numAI = 8;
int numAO = 4;
int port = 0;

string szAO = "analogAO.csv";
string szAI = "analogAI.csv";

string szDO = "digitalDO.csv";

int numSamples = 1500;
int spacing1 = 5000;

int main()
{

    //make the AO file
    ofstream ofs(szAO.c_str());
    //title
    for (int f = 0; f < numAO; f++) {
        ofs << "AO" << f;
        if (f < numAO - 1) {
            ofs << ",";
        }
    }
    ofs << endl;
    // inint dataAO
    // load up a vector with the output data
    double PI = 3.14159265359;
    double* vals1 = new double[numSamples];
    double* vals2 = new double[numSamples];
    for (int f = 0; f < numSamples; f++) vals1[f] = 4.0*sin(2*PI/60.0 * (double)f);
    for (int f = 0; f < numSamples; f++) vals2[f] = 8.0 * sin(2 * PI / 60.0 * (double)f); //4.0 * ((double)f / (double)numSamples);

    //for (unsigned int f = 0; f < numSamples; f += spacing1) {
    //    if (f > 10) {


    //        vals1[f - 1] = 0;   //trigger when LED off

    //         //vals[f - 2] = 3; //LED on and before LED off
    //         //vals[f - 1] = 0;


    //    }
    //}

    vector <double> vd;
    vector < double> vd1;
    for (int ff = 0; ff < numSamples; ff++) {
        vd.push_back(vals1[ff]);
    }
    for (int ff = 0; ff < numSamples; ff++) {
        vd1.push_back(vals2[ff]);
    }
    vector <vector<double> >  vddata;
    for (int f = 0; f < numAO; f++) {
        if (f == 0) {
            vddata.push_back(vd);
        }
        else {
            vddata.push_back(vd1);
        }
    }
    delete[] vals1;
    delete[] vals2;

    //write the data
    for (int f = 0; f < numSamples; f++) {
        for (int ff = 0; ff < numAO; ff++) {
            ofs << vddata[ff][f];
            if (ff < numAO - 1) {
                ofs << ",";
            }
        }
        ofs << endl;
    }
    ofs.close();


    //ofstream ofs1(szAI.c_str());
    ////title
    //for (int f = 0; f < numAI; f++) {
    //    ofs1 << "AI" << f;
    //    if (f < numAI - 1) {
    //        ofs1 << ",";
    //    }
    //}
    //ofs1 << endl;
    //ofs1.close();


    cout << szDO.c_str() << endl;
    cout << "numSamples " << numSamples << endl;
    cout << "spacing " << spacing1 << endl;
    ofstream ofs2(szDO.c_str());
    ofs2 << "PORT" << port << endl;
    for (int f = 0; f < 2; f++) {
        ofs2 << "LINE" << f;
        if (f < 2 - 1) ofs2 << ",";
    }
    ofs2 << endl;
    for (int f = 0; f < numSamples; f++) {
        for (int ff = 0; ff < 2; ff++) {
            ofs2 << (f >> ff) % 2;
            if (ff < 2 - 1) ofs2 << ",";
        }
        ofs2 << endl;
    }

    //for protocol1

   // unsigned int* vals = new unsigned int[numSamples];
   //for (int f = 0; f < numSamples; f++) vals[f] = f%256;

    //int cnt = 0;
    //for (int f = 0; f < numSamples; f++) vals[f] = 1;

    //for (unsigned int f = 0; f < numSamples; f+=spacing1) {
    //    if (f > 10) {


    //       vals[f - 1] = 2;   //trigger when LED off

    //        //vals[f - 2] = 3; //LED on and before LED off
    //        //vals[f - 1] = 0;
    //       if (cnt % 2 == 1) {
    //           vals[f - 1] += 4 + 8;
    //        }
    //       cnt++;
    //    }
    //}

    //for (unsigned int f = 0; f < numSamples; f++) {
    //    ofs2 << vals[f] << endl;
    //}

    //delete[] vals;


    ofs2.close();
}

