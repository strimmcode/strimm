/*
@file Test.cpp
@author Terry Wright
@email tw567@cam.ac.uk

Test.cpp contains the implementation of the functions exported by the DLL.  These functions can be grouped into:
1)  Functions which implement the file-map to allow for a GDI+ based multicamera sink for STRIMM which overcomes the problem
    of the very slow frame rate due to the slow rendering of ImageJ
2)  Functions which implement the CompoundProtocol to be used by the NIDAQ source in STRIMM.  The CompoundProtocol can also
    be used for a SimpleProtocol
3)  Some general WinAPI functions which are used by the Keyboard source and Speech Engine which can be useful for occasional warnings.

*/

#pragma once
#include "framework.h"
#include "JDAQ.h"
#include <NIDAQmx.h>
#include "Test.h"
#include <Windows.h>
#include <gdiplus.h>
#include "SimpleProtocol.h"
#include "CompoundProtocol.h"

#include "SimpleProtocolContinuous.h"
#include <string>
#include <sstream>

#include "SimpleDataProtocol.h"
#include <map>

#pragma warning(disable : 4996) //has to be added to each file that has deprecated functions in it

using namespace Gdiplus;







int imFileMapStatus = -1;

#define PI	3.1415926535
uInt8		data_DO[2000]; //DO
uInt8		data_DI[2000]; //DI
float64     data_AO[2000]; //AO
float64     data_AI[2000]; //AI
int32       read;

TaskHandle  taskHandleDO = 0;
TaskHandle  taskHandleDI = 0;
TaskHandle  taskHandleAO = 0;
TaskHandle  taskHandleAI = 0;

int32       error = 0;
char        errBuff[2048] = { '\0' };

bool bGotData = false;
int n = 0;













//
//Functions for easily reading csv protocol
//


//
// Functions for NIDAQ Data Sink
//


map<int, SimpleDataProtocol> map_protocols_data;

JNIEXPORT jint JNICALL Java_uk_co_strimm_services_JDAQ_NIDAQDataSinkInit
(JNIEnv* env, jobject, jint deviceID,jdouble minV, jdouble maxV) {
    SimpleDataProtocol SDP;
    int ret = SDP.Init(deviceID,  minV,  maxV);
    map_protocols_data[deviceID] = SDP;

    return ret;
}
JNIEXPORT jint JNICALL Java_uk_co_strimm_services_JDAQ_NIDAQDataSinkRun
(JNIEnv* env, jobject, jint deviceID, jdoubleArray pAOData, jintArray pDOData, jint numSamples, jdouble sampleFreq, jint numAOChannels, jintArray AOChannels, jint numDOChannels, jintArray DOChannels, jint DOport) {


    jboolean bIsCopy3 = 0;
    double* pDataDoubleAO = env->GetDoubleArrayElements(pAOData, &bIsCopy3);

    jboolean bIsCopy4 = 0;
    jint* pDataIntDO = env->GetIntArrayElements(pDOData, &bIsCopy4);

    jboolean bIsCopy5 = 0;
    jint* pDataIntAOChannels = env->GetIntArrayElements(AOChannels, &bIsCopy5);

    jboolean bIsCopy6 = 0;
    jint* pDataIntDOChannels = env->GetIntArrayElements(AOChannels, &bIsCopy6);

    int ret = map_protocols_data[deviceID].Run(pDataDoubleAO, (uInt32*)pDataIntDO, numSamples, sampleFreq, numAOChannels, (int*)pDataIntAOChannels, numDOChannels, (int*)pDataIntDOChannels, DOport);





    env->ReleaseIntArrayElements(pDOData, pDataIntDO, 0); //copy the contents back into the array
    env->ReleaseDoubleArrayElements(pAOData, pDataDoubleAO, 0); //copy the contents back into the array
    env->ReleaseIntArrayElements(AOChannels, pDataIntAOChannels, 0);
    env->ReleaseIntArrayElements(DOChannels, pDataIntDOChannels, 0);
    return ret;
}
JNIEXPORT jint JNICALL Java_uk_co_strimm_services_JDAQ_NIDAQDataSinkShutdown
(JNIEnv* env, jobject, jint deviceID) {

    int ret = map_protocols_data[deviceID].Shutdown();
    return ret;

}



//Functions for NIDAQ Continuous Source

map<int, SimpleProtocolContinuous> map_protocols_cont;

JNIEXPORT jint JNICALL Java_uk_co_strimm_services_JDAQ_NIDAQContinuousSourceInit
(JNIEnv* env, jobject, jint deviceID, jstring szCsv, jboolean bStartTrigger, jboolean bRisingEdge, jint pFIx, jdouble timeoutSec, jdouble minV, jdouble maxV) {
    jboolean bIsCopy = 0;
    char* szProt = (char*)env->GetStringUTFChars(szCsv, &bIsCopy);

    SimpleProtocolContinuous spc;
    int ret = spc.Init(deviceID, szProt, bStartTrigger, bRisingEdge, pFIx, timeoutSec, minV, maxV);
    map_protocols_cont[deviceID] = spc;

    env->ReleaseStringUTFChars(szCsv, szProt);
    return ret;
}

JNIEXPORT jint JNICALL Java_uk_co_strimm_services_JDAQ_NIDAQContinuousSourceRun
(JNIEnv* env, jobject, jint deviceID, jdoubleArray pTimes, jdoubleArray pAOData, jdoubleArray pAIData, jintArray pDOData, jintArray pDIData) {

    jboolean bIsCopy0 = 0;
    jsize lenDouble0 = env->GetArrayLength(pTimes);
    jdouble* pDataDouble0 = env->GetDoubleArrayElements(pTimes, &bIsCopy0);


    jboolean bIsCopy1 = 0;
    jsize lenDouble = env->GetArrayLength(pAIData);
    jdouble* pDataDouble = env->GetDoubleArrayElements(pAIData, &bIsCopy1);

    jboolean bIsCopy2 = 0;
    jsize lenInt = env->GetArrayLength(pDIData);
    jint* pDataInt = env->GetIntArrayElements(pDIData, &bIsCopy2);

    jboolean bIsCopy3 = 0;
    jsize lenDouble1 = env->GetArrayLength(pAOData);
    jdouble* pDataDouble1 = env->GetDoubleArrayElements(pAOData, &bIsCopy3);

    jboolean bIsCopy4 = 0;
    jsize lenInt1 = env->GetArrayLength(pDOData);
    jint* pDataInt1 = env->GetIntArrayElements(pDOData, &bIsCopy4);

    
    int ret = map_protocols_cont[deviceID].Run(pDataDouble0, pDataDouble1, pDataDouble, (uInt32*)pDataInt1, (uInt32*)pDataInt);
    cout << "from c++ end of Test.cpp : RunSimpleContinuous" << endl;


    env->ReleaseDoubleArrayElements(pTimes, pDataDouble0, 0);
    env->ReleaseIntArrayElements(pDIData, pDataInt, 0); //copy the contents back into the array
    env->ReleaseDoubleArrayElements(pAIData, pDataDouble, 0); //copy the contents back into the array
    env->ReleaseIntArrayElements(pDOData, pDataInt1, 0); //copy the contents back into the array
    env->ReleaseDoubleArrayElements(pAOData, pDataDouble1, 0); //copy the contents back into the array

    return ret;

}

JNIEXPORT jint JNICALL Java_uk_co_strimm_services_JDAQ_NIDAQContinuousSourceShutdown
(JNIEnv* env, jobject, jint deviceID) {
    int ret = map_protocols_cont[deviceID].Shutdown();
    map_protocols_cont.erase(deviceID);
    return ret;
}

JNIEXPORT jint JNICALL Java_uk_co_strimm_services_JDAQ_NIDAQContinuousSourceGetNumSamples
(JNIEnv* env, jobject, jint deviceID) {
    return map_protocols_cont[deviceID].GetNumSamples();
}

JNIEXPORT jdouble JNICALL Java_uk_co_strimm_services_JDAQ_NIDAQContinuousSourceGetSampleFreq
(JNIEnv* env, jobject, jint deviceID) {
    return map_protocols_cont[deviceID].GetSampleFreq();
}

JNIEXPORT jint JNICALL Java_uk_co_strimm_services_JDAQ_NIDAQContinuousSourceGetNumAOChannels
(JNIEnv* env, jobject, jint deviceID) {
    return map_protocols_cont[deviceID].GetNumAOChannels();
}

JNIEXPORT jint JNICALL Java_uk_co_strimm_services_JDAQ_NIDAQContinuousSourceGetNumDOChannels
(JNIEnv* env, jobject, jint deviceID) {
    return map_protocols_cont[deviceID].GetNumDOChannels();
}

JNIEXPORT jint JNICALL Java_uk_co_strimm_services_JDAQ_NIDAQContinuousSourceGetNumAIChannels
(JNIEnv* env, jobject, jint deviceID) {
    return map_protocols_cont[deviceID].GetNumAIChannels();
}

JNIEXPORT jint JNICALL Java_uk_co_strimm_services_JDAQ_NIDAQContinuousSourceGetNumDIChannels
(JNIEnv* env, jobject, jint deviceID) {
    return map_protocols_cont[deviceID].GetNumDIChannels();
}

JNIEXPORT jint JNICALL Java_uk_co_strimm_services_JDAQ_NIDAQContinuousSourceGetChannelFromIndex
(JNIEnv* env, jobject, jint deviceID, jint type, jint index) {
    return map_protocols_cont[deviceID].GetContinuousChannelFromIndex(type, index);
}

JNIEXPORT jint JNICALL Java_uk_co_strimm_services_JDAQ_NIDAQContinuousSourceGetDOPort
(JNIEnv* env, jobject, jint deviceID) {
    return map_protocols_cont[deviceID].GetContinuousDOPort();
}

JNIEXPORT jint JNICALL Java_uk_co_strimm_services_JDAQ_NIDAQContinuousSourceGetDIPort
(JNIEnv* env, jobject, jint deviceID) {
    return map_protocols_cont[deviceID].GetContinuousDIPort();
}







//
//functions for NIDAQ Source
//

//TODO new timing method where everything is software timed but no trigger
//so could have consecutive rows in the simpleprotocols played consecutively
//and could have software timing in the compound file to say when each software protocol should run
//timing mode 4 = immediate consecutive software timing
//timing mode 5 = read the times in the compound file for software timing.
//also make sure that all of STRIMM uses software timing from this dll
//this would then allow any NIDAQ no matter how complicated to be able to be used by STRIMM in this mode
//it would not allow mutilple NIDAQS

map<int, CompoundProtocol> map_protocols;
JNIEXPORT jint JNICALL Java_uk_co_strimm_services_JDAQ_NIDAQSourceInit
(JNIEnv* env, jobject, jstring szCsv, jboolean bCompound, jboolean bRepeat, jint deviceID, jstring deviceName, jdouble minV, jdouble maxV) {
   // MessageBox(NULL, L"InitProtocol", L"", MB_OK);
    jboolean bIsCopy = 0;
    char* szProt = (char*)env->GetStringUTFChars(szCsv, &bIsCopy);

    CompoundProtocol CP;
    cout << "Initialising protocol..." << endl;
    int ret = CP.InitProtocol(szProt, bCompound, bRepeat, deviceID, minV, maxV);
    map_protocols[deviceID] = CP;
    
    env->ReleaseStringUTFChars(szCsv, szProt);
    return ret;
}

JNIEXPORT jint JNICALL Java_uk_co_strimm_services_JDAQ_NIDAQSourceSetStartTrigger
(JNIEnv*, jobject, jint deviceID, jboolean bStartTrigger, jint pFIx, jboolean bRisingEdge, jdouble timeoutSec) {
   // MessageBox(NULL, L"SetStartTrigger", L"", MB_OK);
    
    return map_protocols[deviceID].SetStartTrigger(bStartTrigger, pFIx, bRisingEdge, timeoutSec);
}

JNIEXPORT jint JNICALL Java_uk_co_strimm_services_JDAQ_NIDAQSourceSetTimingMethod
(JNIEnv*, jobject, jint deviceID,  jint timingMethod) {
  //  MessageBox(NULL, L"SetTimingMethod", L"", MB_OK);


    return map_protocols[deviceID].SetTimingMethod(timingMethod);
}

JNIEXPORT jint JNICALL Java_uk_co_strimm_services_JDAQ_NIDAQSourceGetNumAOChannels
(JNIEnv* env, jobject , jint deviceID ) {

    return map_protocols[deviceID].GetNumAOChannels();
}

JNIEXPORT jint JNICALL Java_uk_co_strimm_services_JDAQ_NIDAQSourceGetNumDOChannels
(JNIEnv* env,   jobject, jint deviceID ) {

    return map_protocols[deviceID].GetNumDOChannels();
}

JNIEXPORT jint JNICALL Java_uk_co_strimm_services_JDAQ_NIDAQSourceGetNumAIChannels
(JNIEnv* env, jobject, jint deviceID ) {

    return map_protocols[deviceID].GetNumAIChannels();
}

JNIEXPORT jint JNICALL Java_uk_co_strimm_services_JDAQ_NIDAQSourceGetNumDIChannels
(JNIEnv* env, jobject, jint deviceID ) {

    return map_protocols[deviceID].GetNumDIChannels();
}

JNIEXPORT jint JNICALL Java_uk_co_strimm_services_JDAQ_NIDAQSourceGetNumSamples
(JNIEnv* env, jobject, jint deviceID) {

    return map_protocols[deviceID].GetNumSamples();
}

JNIEXPORT jdouble JNICALL Java_uk_co_strimm_services_JDAQ_NIDAQSourceGetSampleFreq
(JNIEnv* env, jobject, jint deviceID) {

    return map_protocols[deviceID].GetSampleFreq();
}

JNIEXPORT jint JNICALL Java_uk_co_strimm_services_JDAQ_NIDAQSourceGetChannelFromIndex
(JNIEnv* env, jobject, jint deviceID, jint type, jint index) {

    return map_protocols[deviceID].GetChannelFromIndex(type, index);
}

JNIEXPORT jint JNICALL Java_uk_co_strimm_services_JDAQ_NIDAQSourceGetDIPort
(JNIEnv* env, jobject, jint deviceID) {
    return map_protocols[deviceID].GetPort(true);
}

JNIEXPORT jint JNICALL Java_uk_co_strimm_services_JDAQ_NIDAQSourceGetDOPort
(JNIEnv* env, jobject, jint deviceID) {
    return map_protocols[deviceID].GetPort(false);
}

JNIEXPORT jint JNICALL Java_uk_co_strimm_services_JDAQ_NIDAQSourceRun
(JNIEnv* env, jobject, jint deviceID,  jdoubleArray pTimes, jdoubleArray pAOData, jdoubleArray pAIData, jintArray pDOData, jintArray pDIData) {
   //MessageBox(NULL, L"RunNext1", L"", MB_OK);

   jdouble *pDataDouble = NULL, *pDataDouble0 = NULL, *pDataDouble1 = NULL;
   jint* pDataInt = NULL, * pDataInt1 = NULL;

   cout << "In NIDAQSourceRun" << endl;

   if (pTimes) {
       jboolean bIsCopy0 = 0;
       jsize lenDouble0 = env->GetArrayLength(pTimes);
       pDataDouble0 = env->GetDoubleArrayElements(pTimes, &bIsCopy0);
   }

    if (pAIData) {
        jboolean bIsCopy1 = 0;
        jsize lenDouble = env->GetArrayLength(pAIData);
        pDataDouble = env->GetDoubleArrayElements(pAIData, &bIsCopy1);
    }
    if (pDIData) {
        jboolean bIsCopy2 = 0;
        jsize lenInt = env->GetArrayLength(pDIData);
        pDataInt = env->GetIntArrayElements(pDIData, &bIsCopy2);
    }
    if (pAOData) {
        jboolean bIsCopy3 = 0;
        jsize lenDouble1 = env->GetArrayLength(pAOData);
        pDataDouble1 = env->GetDoubleArrayElements(pAOData, &bIsCopy3);
    }
    if (pDOData) {
        jboolean bIsCopy4 = 0;
        jsize lenInt1 = env->GetArrayLength(pDOData);
        pDataInt1 = env->GetIntArrayElements(pDOData, &bIsCopy4);
    }

    bool bSuccess = false;
   // software timing should be part of CP no code change
    int ret = map_protocols[deviceID].RunNext(pDataDouble0, pDataDouble1, pDataDouble, (uInt32*)pDataInt1, (uInt32*)pDataInt, &bSuccess);

    if (pTimes) {
        env->ReleaseDoubleArrayElements(pTimes, pDataDouble0, 0);
    }
 
    if (pAIData) {
        env->ReleaseDoubleArrayElements(pAIData, pDataDouble, 0); //copy the contents back into the array
    }

    if (pDIData) {
        env->ReleaseIntArrayElements(pDIData, pDataInt, 0); //copy the contents back into the array
    }

    if (pAOData) {
        env->ReleaseDoubleArrayElements(pAOData, pDataDouble1, 0); //copy the contents back into the array
    }

    if (pDOData) {
        env->ReleaseIntArrayElements(pDOData, pDataInt1, 0); //copy the contents back into the array
    }

    return ret;
}

JNIEXPORT jint JNICALL Java_uk_co_strimm_services_JDAQ_NIDAQSourceShutdown
(JNIEnv*, jobject, jint deviceID) {
    // MessageBox(NULL, L"ShutdownProtocol", L"", MB_OK);
    bool bRet = map_protocols[deviceID].ShutdownProtocol();
    map_protocols.erase(deviceID);
    return bRet;
}

JNIEXPORT jint JNICALL Java_uk_co_strimm_services_JDAQ_NIDAQSourceTerminate
(JNIEnv*, jobject, jint deviceID) {
    //// MessageBox(NULL, L"ShutdownProtocol", L"", MB_OK);
    //return CP.ShutdownProtocol();
    return 0;
}


























//Useful windows api calls
 

JNIEXPORT jboolean JNICALL Java_uk_co_strimm_services_JDAQ_GetKeyState(JNIEnv*, jobject, int VK) {
    bool bRet = GetKeyState(VK) & 0x8000;
    return bRet;
}

JNIEXPORT jint JNICALL Java_uk_co_strimm_services_JDAQ_GetKeyboardState(JNIEnv* env, jobject, jintArray virtual_keyboard) {

    jboolean bIsCopy0 = 0;
    jint* pDataInt0 = env->GetIntArrayElements(virtual_keyboard, &bIsCopy0);
    for (int f = 0; f < 166; f++) {
        bool bState = GetKeyState(f) & 0x8000;
        pDataInt0[f] = bState ? 1 : 0;
    }






    env->ReleaseIntArrayElements(virtual_keyboard, pDataInt0, 0); //copy the contents back into the array

    return 0;
}

JNIEXPORT jdouble JNICALL Java_uk_co_strimm_services_JDAQ_GetCurrentSystemTime(JNIEnv*, jobject) {
    return 0.0;
}
//
//
//
//
//
//

extern "C" TEST_API bool SimpleProtocolContinuousTest() {

    /*
    SIMPLE TEST PROTOCOLS
    */




    //Continuous NIDAQ source requires AO, AI, DO, DI


    string sz_AOAIDODI = "C:\\Users\\twrig\\Desktop\\Code\\FullGraph25\\STRIMM.app\\TBIO_LDI_Trig.csv"; //test1 ok 15/8/21

    SimpleProtocolContinuous PPP;

    //int deviceID, char* szCsv, bool bStartTrigger, bool bRisingEdge, uInt32 pFIx, double timeoutSec, double minV, double maxV);
    PPP.Init(4, (char*)sz_AOAIDODI.c_str(), false, true, 0, -1, -10.0, 10.0);

   
    int numSamples = PPP.GetNumSamples();

    int numAOChannels = PPP.GetNumAOChannels();
    int numAIChannels = PPP.GetNumAIChannels();
    int numDOChannels = PPP.GetNumDOChannels();
    int numDIChannels = PPP.GetNumDIChannels();

    double* pTime = new double[numSamples];
    double* pDataAI = new double[numSamples * numAIChannels];
    double* pDataAO = new double[numSamples * numAOChannels];
    uInt32* pDataDI = new uInt32[numSamples * numDIChannels];
    uInt32* pDataDO = new uInt32[numSamples * numDOChannels];


    while (PPP.Run(pTime, pDataAO, pDataAI, pDataDO, pDataDI)) {
        cout << "new data" << endl; // pDataAI[0] << " " << pDataAO[0] << " " << pDataDI[0] << " " << pDataDO[0] << endl;
        if (numAOChannels > 0) {
            for (int ff = 0; ff < numSamples; ff++) {
                cout << "AO ";
                for (int fff = 0; fff < numAOChannels; fff++) {
                    cout << pDataAO[ff * numAOChannels + fff] << " ";
                }
                cout << endl;
            }
        }
        if (numDOChannels > 0) {
            for (int ff = 0; ff < numSamples; ff++) {
                cout << "DO ";
                for (int fff = 0; fff < numDOChannels; fff++) {
                    cout << pDataDO[ff * numDOChannels + fff] << " ";
                }
                cout << endl;
            }
        }
        if (numAIChannels > 0) {
            for (int ff = 0; ff < numSamples; ff++) {
                cout << "AI ";
                for (int fff = 0; fff < numAIChannels; fff++) {
                    cout << pDataAI[ff * numAIChannels + fff] << " ";
                }
                cout << endl;
            }
        }
        if (numDIChannels > 0) {
            for (int ff = 0; ff < numSamples; ff++) {
                cout << "DI ";
                for (int fff = 0; fff < numDIChannels; fff++) {
                    cout << pDataDI[ff * numDIChannels + fff] << " ";
                }
                cout << endl;
            }
        }
    }


    delete[] pTime;
    delete[] pDataAO;
    delete[] pDataAI;
    delete[] pDataDO;
    delete[] pDataDI;




    return false;
}
extern "C" TEST_API bool SimpleProtocolTest() {

    /*
    SIMPLE TEST PROTOCOLS
    */

    string sz_AI = "C:\\Users\\twrig\\source\\repos\\CreateCSV\\CreateCSV\\1_AI.csv"; // test1 ok 15/8/21   test2 ok 15/8/21
    string sz_AO = "C:\\Users\\twrig\\source\\repos\\CreateCSV\\CreateCSV\\1_AO.csv"; // test1 ok 15/8/21   test2 ok 15/8/21
    string sz_DI = "C:\\Users\\twrig\\source\\repos\\CreateCSV\\CreateCSV\\1_DI.csv"; // test1 ok 15/8/21   test2 ok 15/8/21
    string sz_DO = "C:\\Users\\twrig\\source\\repos\\CreateCSV\\CreateCSV\\1_DO.csv"; // test1 ok 15/8/21   test2 ok 15/8/21


    string sz_AOAI = "C:\\Users\\twrig\\source\\repos\\CreateCSV\\CreateCSV\\1_AOAI.csv"; // test1 ok 15/8/21
    string sz_AODO = "C:\\Users\\twrig\\source\\repos\\CreateCSV\\CreateCSV\\1_AODO.csv"; // test1 ok 15/8/21
    string sz_AODI = "C:\\Users\\twrig\\source\\repos\\CreateCSV\\CreateCSV\\1_AODI.csv"; // test1 ok 15/8/21
    string sz_AIDO = "C:\\Users\\twrig\\source\\repos\\CreateCSV\\CreateCSV\\1_AIDO.csv"; // test1 ok 15/8/21
    string sz_AIDI = "C:\\Users\\twrig\\source\\repos\\CreateCSV\\CreateCSV\\1_AIDI.csv"; // test1 ok 15/8/21
    string sz_DODI = "C:\\Users\\twrig\\source\\repos\\CreateCSV\\CreateCSV\\1_DODI.csv"; // test1 ok 15/8/21



    string sz_AOAIDO = "C:\\Users\\twrig\\source\\repos\\CreateCSV\\CreateCSV\\1_AOAIDO.csv"; // test1 ok 15/8/21
    string sz_AOAIDI = "C:\\Users\\twrig\\source\\repos\\CreateCSV\\CreateCSV\\1_AOAIDI.csv"; // test1 ok 15/8/21
    string sz_AODODI = "C:\\Users\\twrig\\source\\repos\\CreateCSV\\CreateCSV\\1_AODODI.csv"; // test1 ok 15/8/21
    string sz_AIDODI = "C:\\Users\\twrig\\source\\repos\\CreateCSV\\CreateCSV\\1_AIDODI.csv"; // test1 ok 15/8/21


    string sz_AOAIDODI = "C:\\Users\\twrig\\source\\repos\\CreateCSV\\CreateCSV\\1_AOAIDODI.csv"; //test1 ok 15/8/21

    string sz_Test = "C:\\Users\\twrig\\Desktop\\Code\\FullGraph25\\STRIMM.app\\TBIO_LDI_Trig.csv"; //test1 ok 15/8/21

    SimpleProtocolContinuous PPP;
    
    //int deviceID, char* szCsv, bool bStartTrigger, bool bRisingEdge, uInt32 pFIx, double timeoutSec, double minV, double maxV);
 
    ////////////multiple daq test//////////////////////////////////////////////////////////////////////////
    SimpleProtocol PP1(4, -10.0, 10.0);
    PP1.Init((char*)sz_Test.c_str());
    int numSamples = PP1.GetNumSamples();
    int numAOChannels = PP1.GetNumAOChannels();
    int numAIChannels = PP1.GetNumAIChannels();
    int numDOChannels = PP1.GetNumDOChannels();
    int numDIChannels = PP1.GetNumDIChannels();

    double* pDataAO = new double[numSamples * numAOChannels];
    ZeroMemory(pDataAO, sizeof(pDataAO));

    double* pDataAI = new double[numSamples * numAIChannels];
  
    uInt32* pDataDO = new uInt32 [numSamples * numDOChannels];
    ZeroMemory(pDataDO, sizeof(pDataDO));

    uInt32* pDataDI = new uInt32[numSamples * numDIChannels];

    double* pTime = new double[numSamples];

    PP1.InitDAQ();

    for (int f = 0; f < 10000; f++) {
        PP1.RunProtocolDAQ(pTime, pDataAO, pDataAI, pDataDO, pDataDI, false, true, 0, -1);
        cout << "done" << endl;
    }

    PP1.ReleaseDAQ();




    delete[] pDataAO;
    delete[] pDataAI;
    delete[] pDataDO;
    delete[] pDataDI;

    delete[] pTime;


    ////////////////////////////////////////////////////////////////////////////////////////////////////////////
    /*
    * TEST1      All of the simple test protocols confirmed to operate correctly using an oscilloscope
    *           not using a start trigger.
    */
    //cout << "TEST1" << "******************************" << endl;
    //SimpleProtocol PP(4, -10.0, 10.0);
    //PP.Init((char*)sz_DO.c_str());

    //int numAOChannels = PP.GetNumChannels(0);
    //int numDOChannels = PP.GetNumChannels(2);
    //int numAIChannels = PP.GetNumChannels(1);
    //int numDIChannels = PP.GetNumChannels(3);

    //int numSamples = PP.GetNumSamples();
    //double* pTime = NULL, * pDataAO = NULL, * pDataAI = NULL;
    //uInt32* pDataDI = NULL, * pDataDO = NULL;
    //pTime = new double[numSamples];
    //if (numAIChannels > 0) pDataAI = new double[numSamples * numAIChannels];
    //if (numDIChannels > 0) pDataDI = new uInt32[numSamples * numDIChannels];

    //if (numAOChannels > 0) pDataAO = new double[numSamples * numAOChannels];
    //if (numDOChannels > 0) pDataDO = new uInt32[numSamples * numDOChannels];

  
    //bool bRepeat = true;
    //bool bDoNext = true;

    //if (PP.InitDAQ()) {
    //    while (bDoNext) {
    //        PP.RunProtocolDAQ(pTime, pDataAO, pDataAI, pDataDO, pDataDI, false, true, 0, -1);
    //        if (numAOChannels > 0) {
    //            for (int ff = 0; ff < numSamples; ff++) {
    //                cout << "AO ";
    //                for (int fff = 0; fff < numAOChannels; fff++) {
    //                    cout << pDataAO[ff * numAOChannels + fff] << " ";
    //                }
    //                cout << endl;
    //            }
    //        }
    //        if (numDOChannels > 0) {
    //            for (int ff = 0; ff < numSamples; ff++) {
    //                cout << "DO ";
    //                for (int fff = 0; fff < numDOChannels; fff++) {
    //                    cout << pDataDO[ff * numDOChannels + fff] << " ";
    //                }
    //                cout << endl;
    //            }
    //        }
    //        if (numAIChannels > 0) {
    //            for (int ff = 0; ff < numSamples; ff++) {
    //                cout << "AI ";
    //                for (int fff = 0; fff < numAIChannels; fff++) {
    //                    cout << pDataAI[ff * numAIChannels + fff] << " ";
    //                }
    //                cout << endl;
    //            }
    //        }
    //        if (numDIChannels > 0) {
    //            for (int ff = 0; ff < numSamples; ff++) {
    //                cout << "DI ";
    //                for (int fff = 0; fff < numDIChannels; fff++) {
    //                    cout << pDataDI[ff * numDIChannels + fff] << " ";
    //                }
    //                cout << endl;
    //            }
    //        }
    //        if (!bRepeat) bDoNext = false;
    //    }

    //}
    //PP.ReleaseDAQ();
    //
    //if (!pTime) delete[] pTime;
    //if (!pDataAO) delete[] pDataAO;
    //if (!pDataAI) delete[] pDataAI;
    //if (!pDataDO) delete[] pDataDO;
    //if (!pDataDI) delete[] pDataDI;
    /////////////////////////////////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////

   
    ///*
    //* TEST2     All of the simple test protocols confirmed to operate correctly using an oscilloscope
    //*           however using a start trigger.
    //*           Confirm that the start trigger and the protocol starts at the same time where it can be observed
    //*/
    //cout << "TEST2" << "******************************" << endl;
    //SimpleProtocol PP1(1, -10.0, 10.0);
    //PP1.Init((char*)sz_DO.c_str());

    //numAOChannels = PP1.GetNumChannels(0);
    //numDOChannels = PP1.GetNumChannels(2);
    //numAIChannels = PP1.GetNumChannels(1);
    //numDIChannels = PP1.GetNumChannels(3);

    //numSamples = PP1.GetNumSamples();
    //pTime = NULL;
    //pDataAO = NULL;
    //pDataAI = NULL;
    //pDataDO = NULL;
    //pDataDI = NULL;
    //pTime = new double[numSamples];
    //if (numAIChannels > 0) pDataAI = new double[numSamples * numAIChannels];
    //if (numDIChannels > 0) pDataDI = new uInt32[numSamples * numDIChannels];

    //if (numAOChannels > 0) pDataAO = new double[numSamples * numAOChannels];
    //if (numDOChannels > 0) pDataDO = new uInt32[numSamples * numDOChannels];


    //if (PP1.InitDAQ()) {
    //    for (int f = 0; f < 1; f++) {
    //        //use a start trigger (provided by sig gen)
    //        PP1.RunProtocolDAQ(pTime, pDataAO, pDataAI, pDataDO, pDataDI, true, true, 0, -1);
    //        if (numAOChannels > 0) {
    //            for (int ff = 0; ff < numSamples; ff++) {
    //                cout << "AO ";
    //                for (int fff = 0; fff < numAOChannels; fff++) {
    //                    cout << pDataAO[ff * numAOChannels + fff] << " ";
    //                }
    //                cout << endl;
    //            }
    //        }
    //        if (numDOChannels > 0) {
    //            for (int ff = 0; ff < numSamples; ff++) {
    //                cout << "DO ";
    //                for (int fff = 0; fff < numDOChannels; fff++) {
    //                    cout << pDataDO[ff * numDOChannels + fff] << " ";
    //                }
    //                cout << endl;
    //            }
    //        }
    //        if (numAIChannels > 0) {
    //            for (int ff = 0; ff < numSamples; ff++) {
    //                cout << "AI ";
    //                for (int fff = 0; fff < numAIChannels; fff++) {
    //                    cout << pDataAI[ff * numAIChannels + fff] << " ";
    //                }
    //                cout << endl;
    //            }
    //        }
    //        if (numDIChannels > 0) {
    //            for (int ff = 0; ff < numSamples; ff++) {
    //                cout << "DI ";
    //                for (int fff = 0; fff < numDIChannels; fff++) {
    //                    cout << pDataDI[ff * numDIChannels + fff] << " ";
    //                }
    //                cout << endl;
    //            }
    //        }

    //    }

    //}
    //PP1.ReleaseDAQ();
    //if (!pTime) delete[] pTime;
    //if (!pDataAO) delete[] pDataAO;
    //if (!pDataAI) delete[] pDataAI;
    //if (!pDataDO) delete[] pDataDO;
    //if (!pDataDI) delete[] pDataDI;
    ///////////////////////////////////////////////////////////////////////////////////////////////////////



    


    return false;
    }
extern "C" TEST_API bool CompoundProtocolTest() {

    string szFolder = "C:\\Users\\twrig\\source\\repos\\CreateCSV\\CreateCSV\\";

    string szCsv = szFolder + "simpleTest1.csv";
    string szCsv3 = szFolder + "simpleTest_eg3.csv";
    string szCsv_Dev2 = szFolder + "simpleTest_Dev2.csv";
    string szCsv1 = szFolder + "compoundTest1.csv";
    string szCsv2 = szFolder + "compoundTest2.csv";

    string szTest1 = "C:\\Users\\twrig\\Desktop\\Code\\FullGraph25\\STRIMM.app\\TBIO_LDI_Trig.csv";

    int testNum = 11;


    if (testNum == 0) {
        //timingMethod = 0, no startTrigger

        CompoundProtocol PM3;
        PM3.InitProtocol(szCsv1, true, false, 4, -10.0, 10.0);
        PM3.SetStartTrigger(false, 0, true, -1);
        PM3.SetTimingMethod(0);

        int numAOChannels = PM3.GetNumChannels(0);
        int numDOChannels = PM3.GetNumChannels(2);
        int numAIChannels = PM3.GetNumChannels(1);
        int numDIChannels = PM3.GetNumChannels(3);

        int numSamples = PM3.GetNextNumSamples();
        double* pDataAI = new double[numSamples * numAIChannels];
        uInt32* pDataDI = new uInt32[numSamples * numDIChannels];

        double* pDataAO = new double[numSamples * numAOChannels];
        uInt32* pDataDO = new uInt32[numSamples * numDOChannels]; 

        double* pTime = new double[numSamples];
        //

             //it is up to you to make sure all of the buffers are the right size
        bool bError = false;
        int ret = 1;
        while (ret > 0) {
            cout << "run next" << endl;
            ret = PM3.RunNext(pTime, pDataAO, pDataAI, pDataDO, pDataDI, &bError);

            if (numAOChannels > 0) {
                for (int ff = 0; ff < numSamples; ff++) {
                    cout << "AO ";
                    for (int fff = 0; fff < numAOChannels; fff++) {
                        cout << pDataAO[ff * numAOChannels + fff] << " ";
                    }
                    cout << endl;
                }
            }
            if (numDOChannels > 0) {
                for (int ff = 0; ff < numSamples; ff++) {
                    cout << "DO ";
                    for (int fff = 0; fff < numDOChannels; fff++) {
                        cout << pDataDO[ff * numDOChannels + fff] << " ";
                    }
                    cout << endl;
                }
            }
            if (numAIChannels > 0) {
                for (int ff = 0; ff < numSamples; ff++) {
                    cout << "AI ";
                    for (int fff = 0; fff < numAIChannels; fff++) {
                        cout << pDataAI[ff * numAIChannels + fff] << " ";
                    }
                    cout << endl;
                }
            }
            if (numDIChannels > 0) {
                for (int ff = 0; ff < numSamples; ff++) {
                    cout << "DI ";
                    for (int fff = 0; fff < numDIChannels; fff++) {
                        cout << pDataDI[ff * numDIChannels + fff] << " ";
                    }
                    cout << endl;
                }
            }

        }

        PM3.ShutdownProtocol();

    }
    else if (testNum == 1) {
        //timingMethod = 0, startTrigger


        CompoundProtocol PM3;
        PM3.InitProtocol(szCsv1, true, false, 1, -10.0, 10.0);
        PM3.SetStartTrigger(true, 0, true, -1);
        PM3.SetTimingMethod(0);

        int numAOChannels = PM3.GetNumChannels(0);
        int numDOChannels = PM3.GetNumChannels(2);
        int numAIChannels = PM3.GetNumChannels(1);
        int numDIChannels = PM3.GetNumChannels(3);

        int numSamples = PM3.GetNextNumSamples();
        double* pDataAI = new double[numSamples * numAIChannels];
        uInt32* pDataDI = new uInt32[numSamples * numDIChannels];

        double* pDataAO = new double[numSamples * numAOChannels];
        uInt32* pDataDO = new uInt32[numSamples * numDOChannels];

        double* pTime = new double[numSamples];
        //

             //it is up to you to make sure all of the buffers are the right size
        bool bError = false;
        cout << "run next" << endl;
        int ret = PM3.RunNext(pTime, pDataAO, pDataAI, pDataDO, pDataDI, &bError);
        while (ret > 0) {
            ret = PM3.RunNext(pTime, pDataAO, pDataAI, pDataDO, pDataDI, &bError);
            cout << "run next" << endl;
            //if (numAOChannels > 0) {
            //    for (int ff = 0; ff < numSamples; ff++) {
            //        cout << "AO ";
            //        for (int fff = 0; fff < numAOChannels; fff++) {
            //            cout << pDataAO[ff * numAOChannels + fff] << " ";
            //        }
            //        cout << endl;
            //    }
            //}
            //if (numDOChannels > 0) {
            //    for (int ff = 0; ff < numSamples; ff++) {
            //        cout << "DO ";
            //        for (int fff = 0; fff < numDOChannels; fff++) {
            //            cout << pDataDO[ff * numDOChannels + fff] << " ";
            //        }
            //        cout << endl;
            //    }
            //}
            //if (numAIChannels > 0) {
            //    for (int ff = 0; ff < numSamples; ff++) {
            //        cout << "AI ";
            //        for (int fff = 0; fff < numAIChannels; fff++) {
            //            cout << pDataAI[ff * numAIChannels + fff] << " ";
            //        }
            //        cout << endl;
            //    }
            //}
            //if (numDIChannels > 0) {
            //    for (int ff = 0; ff < numSamples; ff++) {
            //        cout << "DI ";
            //        for (int fff = 0; fff < numDIChannels; fff++) {
            //            cout << pDataDI[ff * numDIChannels + fff] << " ";
            //        }
            //        cout << endl;
            //    }
            //}

        }

        PM3.ShutdownProtocol();
    }
    else if (testNum == 2) {
        //timingMethod = 0, no startTrigger, repeat protocol

        CompoundProtocol PM3;
        PM3.InitProtocol(szCsv1, true, true, 1, -10.0, 10.0);
        PM3.SetStartTrigger(false, 0, true, -1);
        PM3.SetTimingMethod(0);

        int numAOChannels = PM3.GetNumChannels(0);
        int numDOChannels = PM3.GetNumChannels(2);
        int numAIChannels = PM3.GetNumChannels(1);
        int numDIChannels = PM3.GetNumChannels(3);

        int numSamples = PM3.GetNextNumSamples();
        double* pDataAI = new double[numSamples * numAIChannels];
        uInt32* pDataDI = new uInt32[numSamples * numDIChannels];

        double* pDataAO = new double[numSamples * numAOChannels];
        uInt32* pDataDO = new uInt32[numSamples * numDOChannels];

        double* pTime = new double[numSamples];
        //

             //it is up to you to make sure all of the buffers are the right size
        bool bError = false;
        cout << "run next" << endl;
        int ret = PM3.RunNext(pTime, pDataAO, pDataAI, pDataDO, pDataDI, &bError);
        while (ret > 0) {
            ret = PM3.RunNext(pTime, pDataAO, pDataAI, pDataDO, pDataDI, &bError);
            cout << "run next" << endl;
            //if (numAOChannels > 0) {
            //    for (int ff = 0; ff < numSamples; ff++) {
            //        cout << "AO ";
            //        for (int fff = 0; fff < numAOChannels; fff++) {
            //            cout << pDataAO[ff * numAOChannels + fff] << " ";
            //        }
            //        cout << endl;
            //    }
            //}
            //if (numDOChannels > 0) {
            //    for (int ff = 0; ff < numSamples; ff++) {
            //        cout << "DO ";
            //        for (int fff = 0; fff < numDOChannels; fff++) {
            //            cout << pDataDO[ff * numDOChannels + fff] << " ";
            //        }
            //        cout << endl;
            //    }
            //}
            //if (numAIChannels > 0) {
            //    for (int ff = 0; ff < numSamples; ff++) {
            //        cout << "AI ";
            //        for (int fff = 0; fff < numAIChannels; fff++) {
            //            cout << pDataAI[ff * numAIChannels + fff] << " ";
            //        }
            //        cout << endl;
            //    }
            //}
            //if (numDIChannels > 0) {
            //    for (int ff = 0; ff < numSamples; ff++) {
            //        cout << "DI ";
            //        for (int fff = 0; fff < numDIChannels; fff++) {
            //            cout << pDataDI[ff * numDIChannels + fff] << " ";
            //        }
            //        cout << endl;
            //    }
            //}

        }

        PM3.ShutdownProtocol();
    }
    else if (testNum == 3) {
        //timingMethod = 0, startTrigger, repeat protocol

        CompoundProtocol PM3;
        PM3.InitProtocol(szCsv1, true, true, 1, -10.0, 10.0);
        PM3.SetStartTrigger(true, 0, true, -1);
        PM3.SetTimingMethod(0);

        int numAOChannels = PM3.GetNumChannels(0);
        int numDOChannels = PM3.GetNumChannels(2);
        int numAIChannels = PM3.GetNumChannels(1);
        int numDIChannels = PM3.GetNumChannels(3);

        int numSamples = PM3.GetNextNumSamples();
        double* pDataAI = new double[numSamples * numAIChannels];
        uInt32* pDataDI = new uInt32[numSamples * numDIChannels];

        double* pDataAO = new double[numSamples * numAOChannels];
        uInt32* pDataDO = new uInt32[numSamples * numDOChannels];

        double* pTime = new double[numSamples];
        //

             //it is up to you to make sure all of the buffers are the right size
        bool bError = false;
        cout << "run next" << endl;
        int ret = PM3.RunNext(pTime, pDataAO, pDataAI, pDataDO, pDataDI, &bError);
        while (ret > 0) {
            ret = PM3.RunNext(pTime, pDataAO, pDataAI, pDataDO, pDataDI, &bError);
            cout << "run next" << endl;
            //if (numAOChannels > 0) {
            //    for (int ff = 0; ff < numSamples; ff++) {
            //        cout << "AO ";
            //        for (int fff = 0; fff < numAOChannels; fff++) {
            //            cout << pDataAO[ff * numAOChannels + fff] << " ";
            //        }
            //        cout << endl;
            //    }
            //}
            //if (numDOChannels > 0) {
            //    for (int ff = 0; ff < numSamples; ff++) {
            //        cout << "DO ";
            //        for (int fff = 0; fff < numDOChannels; fff++) {
            //            cout << pDataDO[ff * numDOChannels + fff] << " ";
            //        }
            //        cout << endl;
            //    }
            //}
            //if (numAIChannels > 0) {
            //    for (int ff = 0; ff < numSamples; ff++) {
            //        cout << "AI ";
            //        for (int fff = 0; fff < numAIChannels; fff++) {
            //            cout << pDataAI[ff * numAIChannels + fff] << " ";
            //        }
            //        cout << endl;
            //    }
            //}
            //if (numDIChannels > 0) {
            //    for (int ff = 0; ff < numSamples; ff++) {
            //        cout << "DI ";
            //        for (int fff = 0; fff < numDIChannels; fff++) {
            //            cout << pDataDI[ff * numDIChannels + fff] << " ";
            //        }
            //        cout << endl;
            //    }
            //}

        }

        PM3.ShutdownProtocol();
    }
    else if (testNum == 4) {
        //timingMethod = 0, no trigger, repeat                  //at the moment the change is applied to all repeats TO DO
        //50% chance to change AO0 on the next run

        CompoundProtocol PM3;
        PM3.InitProtocol(szCsv1,  true, true, 1, -10.0, 10.0);
        PM3.SetStartTrigger(false, 0, true, -1);
        PM3.SetTimingMethod(0);

        int numAOChannels = PM3.GetNumChannels(0);
        int numDOChannels = PM3.GetNumChannels(2);
        int numAIChannels = PM3.GetNumChannels(1);
        int numDIChannels = PM3.GetNumChannels(3);

        int numSamples = PM3.GetNextNumSamples();
        double* pDataAI = new double[numSamples * numAIChannels];
        uInt32* pDataDI = new uInt32[numSamples * numDIChannels];

        double* pDataAO = new double[numSamples * numAOChannels];
        uInt32* pDataDO = new uInt32[numSamples * numDOChannels];

        double* pTime = new double[numSamples];
        //

             //it is up to you to make sure all of the buffers are the right size
        bool bError = false;
        cout << "run next" << endl;

        double* pChange = new double[numSamples];
        int ret = PM3.RunNext(pTime, pDataAO, pDataAI, pDataDO, pDataDI, &bError);
    

        PM3.ShutdownProtocol();
    }
    else if (testNum == 5) {
        //timingMethod == 0, no trigger, repeat
        //50% chance to change DO0 on the next run     TO DO
    }
    else if (testNum == 6) {
        //timingMethod == 0, no trigger, repeat, have to reallocate after each run due to then protocols having varying numSamples
           //timingMethod = 0, startTrigger, repeat protocol

           CompoundProtocol PM3;
           PM3.InitProtocol(szCsv2, true, true, 1, -10.0, 10.0);
           PM3.SetStartTrigger(true, 0, true, -1);
           PM3.SetTimingMethod(0);

           int numAOChannels = 0;
           int numDOChannels = 0;
           int numAIChannels = 0;
           int numDIChannels = 0;

           int numSamples = PM3.GetNextNumSamples();
           PM3.GetBufferDimensions(&numSamples, &numAOChannels, &numAIChannels, &numDOChannels, &numDIChannels);

           double* pDataAI = new double[numSamples * numAIChannels];
           uInt32* pDataDI = new uInt32[numSamples * numDIChannels];

           double* pDataAO = new double[numSamples * numAOChannels];
           uInt32* pDataDO = new uInt32[numSamples * numDOChannels];

           double* pTime = new double[numSamples];
           //

                //it is up to you to make sure all of the buffers are the right size
           bool bError = false;
           cout << "run next" << endl;
           int ret = PM3.RunNext(pTime, pDataAO, pDataAI, pDataDO, pDataDI, &bError);
           while (ret > 0) {
               ret = PM3.RunNext(pTime, pDataAO, pDataAI, pDataDO, pDataDI, &bError);

               delete[] pDataAI;
               delete[] pDataDI;
               delete[] pDataAO;
               delete[] pDataDO;
               delete[] pTime;


               PM3.GetBufferDimensions(&numSamples, &numAOChannels, &numAIChannels, &numDOChannels, &numDIChannels);

               pDataAI = new double[numSamples * numAIChannels];
               pDataDI = new uInt32[numSamples * numDIChannels];

               pDataAO = new double[numSamples * numAOChannels];
               pDataDO = new uInt32[numSamples * numDOChannels];

               pTime = new double[numSamples];













               cout << "run next" << endl;
               //if (numAOChannels > 0) {
               //    for (int ff = 0; ff < numSamples; ff++) {
               //        cout << "AO ";
               //        for (int fff = 0; fff < numAOChannels; fff++) {
               //            cout << pDataAO[ff * numAOChannels + fff] << " ";
               //        }
               //        cout << endl;
               //    }
               //}
               //if (numDOChannels > 0) {
               //    for (int ff = 0; ff < numSamples; ff++) {
               //        cout << "DO ";
               //        for (int fff = 0; fff < numDOChannels; fff++) {
               //            cout << pDataDO[ff * numDOChannels + fff] << " ";
               //        }
               //        cout << endl;
               //    }
               //}
               //if (numAIChannels > 0) {
               //    for (int ff = 0; ff < numSamples; ff++) {
               //        cout << "AI ";
               //        for (int fff = 0; fff < numAIChannels; fff++) {
               //            cout << pDataAI[ff * numAIChannels + fff] << " ";
               //        }
               //        cout << endl;
               //    }
               //}
               //if (numDIChannels > 0) {
               //    for (int ff = 0; ff < numSamples; ff++) {
               //        cout << "DI ";
               //        for (int fff = 0; fff < numDIChannels; fff++) {
               //            cout << pDataDI[ff * numDIChannels + fff] << " ";
               //        }
               //        cout << endl;
               //    }
               //}

           }

           PM3.ShutdownProtocol();
    }
    else if (testNum == 7) {
        //timingMethod == 1, no repeat



        CompoundProtocol PM3;
        PM3.InitProtocol(szCsv1,  true, false, 1, -10.0, 10.0);
        PM3.SetStartTrigger(true, 0, true, -1);
        PM3.SetTimingMethod(1);

        int numAOChannels = PM3.GetNumChannels(0);
        int numDOChannels = PM3.GetNumChannels(2);
        int numAIChannels = PM3.GetNumChannels(1);
        int numDIChannels = PM3.GetNumChannels(3);

        int numSamples = PM3.GetNextNumSamples();
        double* pDataAI = new double[numSamples * numAIChannels];
        uInt32* pDataDI = new uInt32[numSamples * numDIChannels];

        double* pDataAO = new double[numSamples * numAOChannels];
        uInt32* pDataDO = new uInt32[numSamples * numDOChannels];

        double* pTime = new double[numSamples];
        //

             //it is up to you to make sure all of the buffers are the right size
        bool bError = false;
        cout << "run next" << endl;
        int ret = PM3.RunNext(pTime, pDataAO, pDataAI, pDataDO, pDataDI, &bError);
        while (ret > 0) {
            ret = PM3.RunNext(pTime, pDataAO, pDataAI, pDataDO, pDataDI, &bError);
            cout << "run next" << endl;
            //if (numAOChannels > 0) {
            //    for (int ff = 0; ff < numSamples; ff++) {
            //        cout << "AO ";
            //        for (int fff = 0; fff < numAOChannels; fff++) {
            //            cout << pDataAO[ff * numAOChannels + fff] << " ";
            //        }
            //        cout << endl;
            //    }
            //}
            //if (numDOChannels > 0) {
            //    for (int ff = 0; ff < numSamples; ff++) {
            //        cout << "DO ";
            //        for (int fff = 0; fff < numDOChannels; fff++) {
            //            cout << pDataDO[ff * numDOChannels + fff] << " ";
            //        }
            //        cout << endl;
            //    }
            //}
            //if (numAIChannels > 0) {
            //    for (int ff = 0; ff < numSamples; ff++) {
            //        cout << "AI ";
            //        for (int fff = 0; fff < numAIChannels; fff++) {
            //            cout << pDataAI[ff * numAIChannels + fff] << " ";
            //        }
            //        cout << endl;
            //    }
            //}
            //if (numDIChannels > 0) {
            //    for (int ff = 0; ff < numSamples; ff++) {
            //        cout << "DI ";
            //        for (int fff = 0; fff < numDIChannels; fff++) {
            //            cout << pDataDI[ff * numDIChannels + fff] << " ";
            //        }
            //        cout << endl;
            //    }
            //}

        }

        PM3.ShutdownProtocol();



    }
    else if (testNum == 8) {
        //timingMethod == 1, repeat


        CompoundProtocol PM3;
        PM3.InitProtocol(szCsv1, true, true, 1, -10.0, 10.0);
        PM3.SetStartTrigger(true, 0, true, -1);
        PM3.SetTimingMethod(0);

        int numAOChannels = PM3.GetNumChannels(0);
        int numDOChannels = PM3.GetNumChannels(2);
        int numAIChannels = PM3.GetNumChannels(1);
        int numDIChannels = PM3.GetNumChannels(3);

        int numSamples = PM3.GetNextNumSamples();
        double* pDataAI = new double[numSamples * numAIChannels];
        uInt32* pDataDI = new uInt32[numSamples * numDIChannels];

        double* pDataAO = new double[numSamples * numAOChannels];
        uInt32* pDataDO = new uInt32[numSamples * numDOChannels];

        double* pTime = new double[numSamples];
        //

             //it is up to you to make sure all of the buffers are the right size
        bool bError = false;
        cout << "run next" << endl;
        int ret = PM3.RunNext(pTime, pDataAO, pDataAI, pDataDO, pDataDI, &bError);
        while (ret > 0) {
            ret = PM3.RunNext(pTime, pDataAO, pDataAI, pDataDO, pDataDI, &bError);
            cout << "run next" << endl;
            //if (numAOChannels > 0) {
            //    for (int ff = 0; ff < numSamples; ff++) {
            //        cout << "AO ";
            //        for (int fff = 0; fff < numAOChannels; fff++) {
            //            cout << pDataAO[ff * numAOChannels + fff] << " ";
            //        }
            //        cout << endl;
            //    }
            //}
            //if (numDOChannels > 0) {
            //    for (int ff = 0; ff < numSamples; ff++) {
            //        cout << "DO ";
            //        for (int fff = 0; fff < numDOChannels; fff++) {
            //            cout << pDataDO[ff * numDOChannels + fff] << " ";
            //        }
            //        cout << endl;
            //    }
            //}
            //if (numAIChannels > 0) {
            //    for (int ff = 0; ff < numSamples; ff++) {
            //        cout << "AI ";
            //        for (int fff = 0; fff < numAIChannels; fff++) {
            //            cout << pDataAI[ff * numAIChannels + fff] << " ";
            //        }
            //        cout << endl;
            //    }
            //}
            //if (numDIChannels > 0) {
            //    for (int ff = 0; ff < numSamples; ff++) {
            //        cout << "DI ";
            //        for (int fff = 0; fff < numDIChannels; fff++) {
            //            cout << pDataDI[ff * numDIChannels + fff] << " ";
            //        }
            //        cout << endl;
            //    }
            //}

        }

        PM3.ShutdownProtocol();
    }
    else if (testNum == 9) {
        //timingMethod == 2, no repeat


        CompoundProtocol PM3;
        PM3.InitProtocol(szCsv1,  true, false, 1, -10.0, 10.0);
        PM3.SetStartTrigger(false, 0, true, -1);
        PM3.SetTimingMethod(2);

        int numAOChannels = PM3.GetNumChannels(0);
        int numDOChannels = PM3.GetNumChannels(2);
        int numAIChannels = PM3.GetNumChannels(1);
        int numDIChannels = PM3.GetNumChannels(3);

        int numSamples = PM3.GetNextNumSamples();
        double* pDataAI = new double[numSamples * numAIChannels];
        uInt32* pDataDI = new uInt32[numSamples * numDIChannels];

        double* pDataAO = new double[numSamples * numAOChannels];
        uInt32* pDataDO = new uInt32[numSamples * numDOChannels];

        double* pTime = new double[numSamples];
        //

             //it is up to you to make sure all of the buffers are the right size
        bool bError = false;
        cout << "run next" << endl;
        int ret = PM3.RunNext(pTime, pDataAO, pDataAI, pDataDO, pDataDI, &bError);
        while (ret > 0) {
            ret = PM3.RunNext(pTime, pDataAO, pDataAI, pDataDO, pDataDI, &bError);
            cout << "run next" << endl;
            //if (numAOChannels > 0) {
            //    for (int ff = 0; ff < numSamples; ff++) {
            //        cout << "AO ";
            //        for (int fff = 0; fff < numAOChannels; fff++) {
            //            cout << pDataAO[ff * numAOChannels + fff] << " ";
            //        }
            //        cout << endl;
            //    }
            //}
            //if (numDOChannels > 0) {
            //    for (int ff = 0; ff < numSamples; ff++) {
            //        cout << "DO ";
            //        for (int fff = 0; fff < numDOChannels; fff++) {
            //            cout << pDataDO[ff * numDOChannels + fff] << " ";
            //        }
            //        cout << endl;
            //    }
            //}
            //if (numAIChannels > 0) {
            //    for (int ff = 0; ff < numSamples; ff++) {
            //        cout << "AI ";
            //        for (int fff = 0; fff < numAIChannels; fff++) {
            //            cout << pDataAI[ff * numAIChannels + fff] << " ";
            //        }
            //        cout << endl;
            //    }
            //}
            //if (numDIChannels > 0) {
            //    for (int ff = 0; ff < numSamples; ff++) {
            //        cout << "DI ";
            //        for (int fff = 0; fff < numDIChannels; fff++) {
            //            cout << pDataDI[ff * numDIChannels + fff] << " ";
            //        }
            //        cout << endl;
            //    }
            //}

        }

        PM3.ShutdownProtocol();
    }
    else if (testNum == 10) {
        //timingMethod == 2, repeat
           //timingMethod == 2, no repeat


           CompoundProtocol PM3;
           PM3.InitProtocol(szCsv1,  true, true, 1, -10.0, 10.0);
           PM3.SetStartTrigger(false, 0, true, -1);
           PM3.SetTimingMethod(2);

           int numAOChannels = PM3.GetNumChannels(0);
           int numDOChannels = PM3.GetNumChannels(2);
           int numAIChannels = PM3.GetNumChannels(1);
           int numDIChannels = PM3.GetNumChannels(3);

           int numSamples = PM3.GetNextNumSamples();
           double* pDataAI = new double[numSamples * numAIChannels];
           uInt32* pDataDI = new uInt32[numSamples * numDIChannels];

           double* pDataAO = new double[numSamples * numAOChannels];
           uInt32* pDataDO = new uInt32[numSamples * numDOChannels];

           double* pTime = new double[numSamples];
           //

                //it is up to you to make sure all of the buffers are the right size
           bool bError = false;
           cout << "run next" << endl;
           int ret = PM3.RunNext(pTime, pDataAO, pDataAI, pDataDO, pDataDI, &bError);
           while (ret > 0) {
               ret = PM3.RunNext(pTime, pDataAO, pDataAI, pDataDO, pDataDI, &bError);
               cout << "run next" << endl;
               //if (numAOChannels > 0) {
               //    for (int ff = 0; ff < numSamples; ff++) {
               //        cout << "AO ";
               //        for (int fff = 0; fff < numAOChannels; fff++) {
               //            cout << pDataAO[ff * numAOChannels + fff] << " ";
               //        }
               //        cout << endl;
               //    }
               //}
               //if (numDOChannels > 0) {
               //    for (int ff = 0; ff < numSamples; ff++) {
               //        cout << "DO ";
               //        for (int fff = 0; fff < numDOChannels; fff++) {
               //            cout << pDataDO[ff * numDOChannels + fff] << " ";
               //        }
               //        cout << endl;
               //    }
               //}
               //if (numAIChannels > 0) {
               //    for (int ff = 0; ff < numSamples; ff++) {
               //        cout << "AI ";
               //        for (int fff = 0; fff < numAIChannels; fff++) {
               //            cout << pDataAI[ff * numAIChannels + fff] << " ";
               //        }
               //        cout << endl;
               //    }
               //}
               //if (numDIChannels > 0) {
               //    for (int ff = 0; ff < numSamples; ff++) {
               //        cout << "DI ";
               //        for (int fff = 0; fff < numDIChannels; fff++) {
               //            cout << pDataDI[ff * numDIChannels + fff] << " ";
               //        }
               //        cout << endl;
               //    }
               //}

           }

           PM3.ShutdownProtocol();
    }
    else     if (testNum == 11) {
    //timingMethod = 0, no startTrigger

    CompoundProtocol PM3;
    PM3.InitProtocol(szTest1, false, true, 4, -10.0, 10.0);
    PM3.SetStartTrigger(false, 0, true, -1);
    PM3.SetTimingMethod(0);

    int numAOChannels = PM3.GetNumChannels(0);
    int numDOChannels = PM3.GetNumChannels(2);
    int numAIChannels = PM3.GetNumChannels(1);
    int numDIChannels = PM3.GetNumChannels(3);

    int numSamples = PM3.GetNextNumSamples();
    double* pDataAI = new double[numSamples * numAIChannels];
    uInt32* pDataDI = new uInt32[numSamples * numDIChannels];

    double* pDataAO = new double[numSamples * numAOChannels];
    uInt32* pDataDO = new uInt32[numSamples * numDOChannels];

    double* pTime = new double[numSamples];
    //

         //it is up to you to make sure all of the buffers are the right size
    bool bError = false;
    int ret = 1;
    while (ret > 0) {
        cout << "run next" << endl;
        ret = PM3.RunNext(pTime, pDataAO, pDataAI, pDataDO, pDataDI, &bError);

        if (numAOChannels > 0) {
            for (int ff = 0; ff < numSamples; ff++) {
                cout << "AO ";
                for (int fff = 0; fff < numAOChannels; fff++) {
                    cout << pDataAO[ff * numAOChannels + fff] << " ";
                }
                cout << endl;
            }
        }
        if (numDOChannels > 0) {
            for (int ff = 0; ff < numSamples; ff++) {
                cout << "DO ";
                for (int fff = 0; fff < numDOChannels; fff++) {
                    cout << pDataDO[ff * numDOChannels + fff] << " ";
                }
                cout << endl;
            }
        }
        if (numAIChannels > 0) {
            for (int ff = 0; ff < numSamples; ff++) {
                cout << "AI ";
                for (int fff = 0; fff < numAIChannels; fff++) {
                    cout << pDataAI[ff * numAIChannels + fff] << " ";
                }
                cout << endl;
            }
        }
        if (numDIChannels > 0) {
            for (int ff = 0; ff < numSamples; ff++) {
                cout << "DI ";
                for (int fff = 0; fff < numDIChannels; fff++) {
                    cout << pDataDI[ff * numDIChannels + fff] << " ";
                }
                cout << endl;
            }
        }

    }

    PM3.ShutdownProtocol();

    }
    return false;
}
extern "C" TEST_API bool SimpleDataProtocolTest() {

    SimpleDataProtocol DP;
    DP.Init(3, -10.0, 10.0);
    double pAOData[] = { 1.0,2.0,3.0,4.0 };
    uInt32 pDOData[] = { 1,0,1,0 };
    int AOChannels[] = { 0 };
    int DOChannels[] = { 0 };

    DP.Run(pAOData, pDOData, 4, 1.0, 0, AOChannels, 1, DOChannels, 1);
    Sleep(3000);
    DP.Run(pAOData, pDOData, 4, 1.0, 1, AOChannels, 1, DOChannels, 0);


    DP.Shutdown();
    

    return false;
}
extern "C" TEST_API int Test(void)
{
    //cout << "Simple protocol test " << endl;
   // SimpleProtocolContinuousTest();
    //SimpleProtocolTest();
    
    cout << "Compound protocol test " << endl;
    CompoundProtocolTest();
    //cout << "test complete" << endl;
    return 1000;
}