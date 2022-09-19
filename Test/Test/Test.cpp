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
#include "CameraInfo.h"
#include "CameraMapHeader.h"
#include "ImageMemMap.h"
#include "SimpleProtocol.h"
#include "CompoundProtocol.h"

#include <string>
#include <sstream>

#include "..\Include\sapi.h"
#include "..\Include\sphelper.h"

extern bool bDebug;
extern ofstream* pOfs;
extern CompoundProtocol* CP;

#pragma warning(disable : 4996) //has to be added to each file that has deprecated functions in it

using namespace Gdiplus;

ISpVoice* pVoice = NULL;
bool EngineStarted = false;
vector<CameraInfo> v_cameras;
CameraMapHeader* pHead = NULL;
ImageMemMap* pImm = NULL;
vector<int> v_width;
vector<int> v_height;
vector<int> v_bytesPerPixel;
int* pRois_x = NULL, * pRois_y = NULL, * pRois_w = NULL, * pRois_h = NULL;

int imFileMapStatus = -1;


//Image File Map for GDI+ image sink

JNIEXPORT jint JNICALL Java_uk_co_strimm_services_JDAQ_GetCameraMapStatus
(JNIEnv * env, jobject) {
    if (bDebug) {
        (*pOfs) << "Java_uk_co_strimm_services_JDAQ_GetCameraMapStatus" << endl;
    }
    //return -1 for not created
    //return 0 for created by not init - so cant use
    //return 1 for good to go
    return imFileMapStatus;
}

JNIEXPORT jint JNICALL Java_uk_co_strimm_services_JDAQ_RegisterCameraForCameraMap
(JNIEnv * env, jobject, jstring cameraSz, jint w, jint h, jint bitDepth, jint binning, 
    jint numRect, jintArray rois_x, jintArray rois_y, jintArray rois_w, jintArray rois_h){
    if (bDebug) {
        (*pOfs) << "Java_uk_co_strimm_services_JDAQ_RegisterCameraForCameraMap" << endl;
    }
  //  jboolean bSzCopy = 0;
  //  char* szCamera = (char*)env->GetStringUTFChars(cameraSz, &bSzCopy);
  //// MessageBoxA(NULL, szCamera, "RegisterCameraForCameraMap", MB_OK);

  //  jboolean bIsCopy1 = 0;
  //  jsize lenShort1 = env->GetArrayLength(rois_x);
  //  jint* pDatax = env->GetIntArrayElements(rois_x, &bIsCopy1);

  //  jboolean bIsCopy2 = 0;
  //  jsize lenShort2 = env->GetArrayLength(rois_y);
  //  jint* pDatay = env->GetIntArrayElements(rois_y, &bIsCopy2);

  //  jboolean bIsCopy3= 0;
  //  jsize lenShort3 = env->GetArrayLength(rois_w);
  //  jint* pDataw = env->GetIntArrayElements(rois_w, &bIsCopy3);

  //  jboolean bIsCopy4 = 0;
  //  jsize lenShort4 = env->GetArrayLength(rois_h);
  //  jint* pDatah = env->GetIntArrayElements(rois_h, &bIsCopy4);

  //  pRois_x = new int[numRect];
  //  pRois_y = new int[numRect];
  //  pRois_w = new int[numRect];
  //  pRois_h = new int[numRect];

  //  memcpy(pRois_x, pDatax, numRect * sizeof(int));
  //  memcpy(pRois_y, pDatay, numRect * sizeof(int));
  //  memcpy(pRois_w, pDataw, numRect * sizeof(int));
  //  memcpy(pRois_h, pDatah, numRect * sizeof(int));

  //  v_cameras.push_back(CameraInfo((char*)szCamera, v_cameras.size(), w, h, bitDepth, binning, numRect, pRois_x, pRois_y, pRois_w, pRois_h));
  //  v_width.push_back(w);
  //  v_height.push_back(h);
  //  v_bytesPerPixel.push_back(bitDepth);

  //  env->ReleaseIntArrayElements(rois_x, pDatax, 0); //copy the contents back into the array
  //  env->ReleaseIntArrayElements(rois_y, pDatay, 0); //copy the contents back into the array
  //  env->ReleaseIntArrayElements(rois_w, pDataw, 0); //copy the contents back into the array
  //  env->ReleaseIntArrayElements(rois_h, pDatah, 0); //copy the contents back into the array

  //  env->ReleaseStringUTFChars(cameraSz, szCamera);

  //  imFileMapStatus = -1;  //if you are adding cameras to the vector then the filemap can not be operational
    return 0;
}

JNIEXPORT jint JNICALL Java_uk_co_strimm_services_JDAQ_StartCameraMap
(JNIEnv * env, jobject) {
    if (bDebug) {
        (*pOfs) << "Java_uk_co_strimm_services_JDAQ_StartCameraMap" << endl;
    }
    //pHead = new CameraMapHeader(v_cameras);
    //pImm = new ImageMemMap(pHead);
    //pImm->CreateMemoryMap();
    //imFileMapStatus = 1;  //map ready to go
    return 0;
}


JNIEXPORT jint JNICALL Java_uk_co_strimm_services_JDAQ_ShutdownCameraMap
(JNIEnv * env, jobject) {
    if (bDebug) {
        (*pOfs) << "Java_uk_co_strimm_services_JDAQ_ShutdownCameraMap" << endl;
    }
    //imFileMapStatus = -1;  //the image filemap is no longer safe to use
    //
    //delete pHead;
    //pHead = NULL;
    //delete pImm;
    //pImm = NULL;

    //v_width.clear();
    //v_height.clear();
    //v_bytesPerPixel.clear();

    //if (pRois_x) delete[] pRois_x;
    //if (pRois_y) delete[] pRois_y;
    //if (pRois_w) delete[] pRois_w;
    //if (pRois_h) delete[] pRois_h;
    
    return 0;
}


JNIEXPORT jint JNICALL Java_uk_co_strimm_services_JDAQ_Add16BitImageDataCameraMap
(JNIEnv * env, jobject, jstring cameraSz, jdouble fps, jdouble interval, jint w, jint h, jshortArray pix, jboolean bSave) {
 
    if (bDebug) {
        (*pOfs) << "Java_uk_co_strimm_services_JDAQ_Add16BitImageDataCameraMap" << endl;
    }
   
    //jboolean bSzCopy = 0;
    //char* szCamera = (char*)env->GetStringUTFChars(cameraSz, &bSzCopy);

    //jboolean bIsCopy1 = 0;
    //jsize lenShort = env->GetArrayLength(pix);
    //jshort* pDataShort = env->GetShortArrayElements(pix, &bIsCopy1);
    ////MessageBoxA(NULL, szCamera, "Add16BitImageDataCameraMap", MB_OK);
    //pImm->AddImageData(szCamera, fps, interval, w, h, (BYTE*)pDataShort); 

    //env->ReleaseStringUTFChars(cameraSz, szCamera);
    //env->ReleaseShortArrayElements(pix, pDataShort, 0); //copy the contents back into the array

    return 0;
}


JNIEXPORT jint JNICALL Java_uk_co_strimm_services_JDAQ_Add8BitImageDataCameraMap
(JNIEnv * env, jobject, jstring cameraSz, jdouble fps, jdouble interval, jint w, jint h, jbyteArray pix, jboolean bSave) {
    if (bDebug) {
        (*pOfs) << "Java_uk_co_strimm_services_JDAQ_Add8BitImageDataCameraMap" << endl;
    }

    //jboolean bSzCopy = 0;
    //char* szCamera = (char*)env->GetStringUTFChars(cameraSz, &bSzCopy);

    //jboolean bIsCopy1 = 0;
    //jsize lenByte = env->GetArrayLength(pix);
    //jbyte* pDataByte = env->GetByteArrayElements(pix, &bIsCopy1);
    //pImm->AddImageData(szCamera, fps, interval, w, h, (BYTE*)pDataByte);
    //env->ReleaseStringUTFChars(cameraSz, szCamera);
    //env->ReleaseByteArrayElements(pix, pDataByte, 0); 

    return 0;
}


JNIEXPORT jint JNICALL Java_uk_co_strimm_services_JDAQ_AddARGBBitImageDataCameraMap
(JNIEnv * env, jobject, jstring cameraSz, jdouble fps, jdouble interval, jint w, jint h, jbyteArray pix, jboolean bSave) {
    if (bDebug) {
        (*pOfs) << "Java_uk_co_strimm_services_JDAQ_AddARGBBitImageDataCameraMap" << endl;
    }
    //jboolean bSzCopy = 0;
    //char* szCamera = (char*)env->GetStringUTFChars(cameraSz, &bSzCopy);


    //jboolean bIsCopy1 = 0;
    //jsize lenInt = env->GetArrayLength(pix);
    //jbyte* pDataInt = env->GetByteArrayElements(pix, &bIsCopy1);
    //
    //pImm->AddImageData(szCamera, fps, interval, w, h, (BYTE*)pDataInt);

    //env->ReleaseStringUTFChars(cameraSz, szCamera);
    //env->ReleaseByteArrayElements(pix, pDataInt, 0); //copy the contents back into the array

    return 0;
}

JNIEXPORT jint JNICALL Java_uk_co_strimm_services_JDAQ_WinBeep(JNIEnv * env, jobject, jint freq, jint dur) {
    if (bDebug) {
        (*pOfs) << "Java_uk_co_strimm_services_JDAQ_WinBeep" << endl;
    }
   /* Beep(freq, dur);*/
    return 0;
}


//NIDAQ Source

JNIEXPORT jint JNICALL Java_uk_co_strimm_services_JDAQ_InitProtocol
(JNIEnv* env, jobject, jstring csvProt, jstring szFolder, jboolean bCompound, jboolean bRepeat, jint deviceID, jdouble minV, jdouble maxV, jstring szDeviceName) {
   // MessageBox(NULL, L"InitProtocol", L"", MB_OK);
    
    if (bDebug) { 
        (*pOfs) << "Java_uk_co_strimm_services_JDAQ_InitProtocol : CP.InitProtocol" << endl;
    }
    jboolean bIsCopy = 0;
    char* szProt = (char*)env->GetStringUTFChars(csvProt, &bIsCopy);
    char* szFold = (char*)env->GetStringUTFChars(szFolder, &bIsCopy);
    char* szDevice = (char*)env->GetStringUTFChars(szDeviceName, &bIsCopy);

    int ret = CP->InitProtocol(szProt, szFold, bCompound, bRepeat, deviceID, minV, maxV, szDevice);


    
    env->ReleaseStringUTFChars(csvProt, szProt);
    env->ReleaseStringUTFChars(szFolder, szFold);
    env->ReleaseStringUTFChars(szFolder, szDevice);
    return ret;
}

JNIEXPORT jint JNICALL Java_uk_co_strimm_services_JDAQ_SetStartTrigger
(JNIEnv*, jobject, jboolean bStartTrigger, jint pFIx, jboolean bRisingEdge, jdouble timeoutSec) {
   // MessageBox(NULL, L"SetStartTrigger", L"", MB_OK);
    if (bDebug) {
        (*pOfs) << "Java_uk_co_strimm_services_JDAQ_SetStartTrigger : CP.SetStartTrigger" << endl;
    }
    return CP->SetStartTrigger(bStartTrigger, pFIx, bRisingEdge, timeoutSec);
}

JNIEXPORT jint JNICALL Java_uk_co_strimm_services_JDAQ_SetTimingMethod
(JNIEnv*, jobject, jint timingMethod) {
  //  MessageBox(NULL, L"SetTimingMethod", L"", MB_OK);
    if (bDebug) {
        (*pOfs) << "Java_uk_co_strimm_services_JDAQ_SetTimingMethod : CP.SetTimingMethod" << endl;
    }
    return CP->SetTimingMethod(timingMethod);
}

JNIEXPORT jint JNICALL Java_uk_co_strimm_services_JDAQ_RunNext
(JNIEnv* env, jobject, jdoubleArray pTimes, jdoubleArray pAOData, jdoubleArray pAIData, jintArray pDOData, jintArray pDIData) {
  //  MessageBox(NULL, L"RunNext1", L"", MB_OK);
    if (bDebug) {
        (*pOfs) << "Java_uk_co_strimm_services_JDAQ_RunNext : CP.RunNext" << endl;
    }

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

    bool bSuccess = false;
    int ret = CP->RunNext(pDataDouble0, pDataDouble1, pDataDouble, (uInt32*)pDataInt1, (uInt32*)pDataInt, &bSuccess);


    env->ReleaseDoubleArrayElements(pTimes, pDataDouble0, 0);
    env->ReleaseIntArrayElements(pDIData, pDataInt, 0); //copy the contents back into the array
    env->ReleaseDoubleArrayElements(pAIData, pDataDouble, 0); //copy the contents back into the array
    env->ReleaseIntArrayElements(pDOData, pDataInt1, 0); //copy the contents back into the array
    env->ReleaseDoubleArrayElements(pAOData, pDataDouble1, 0); //copy the contents back into the array
    return ret;
}

JNIEXPORT jint JNICALL Java_uk_co_strimm_services_JDAQ_GetBufferDimensions(JNIEnv* env, jobject, jintArray bufSizes) {
    
    if (bDebug) {
        (*pOfs) << "Java_uk_co_strimm_services_JDAQ_GetBufferDimensions : CP.GetBufferDimensions" << endl;
    }
    jboolean bIsCopy2 = 0;
    jsize lenInt = env->GetArrayLength(bufSizes);
    jint* pDataInt = env->GetIntArrayElements(bufSizes, &bIsCopy2);
    int numSamples = 0;
    int numAOChannels = 0;
    int numAIChannels = 0;
    int numDOChannels = 0;
    int numDIChannels = 0;

    CP->GetBufferDimensions(&numSamples, &numAOChannels, &numAIChannels, &numDOChannels, &numDIChannels);
    pDataInt[0] = numSamples;
    pDataInt[1] = numAOChannels;
    pDataInt[2] = numAIChannels;
    pDataInt[3] = numDOChannels;
    pDataInt[4] = numDIChannels;


    env->ReleaseIntArrayElements(bufSizes, pDataInt, 0);


    return 0;
}

JNIEXPORT jint JNICALL Java_uk_co_strimm_services_JDAQ_UpdateDOChannel
(JNIEnv* env, jobject, jintArray pDOData, jint line) {
    
    if (bDebug) {
        (*pOfs) << "Java_uk_co_strimm_services_JDAQ_UpdateDOChannel" << endl;
    }
    jboolean bIsCopy2 = 0;
    jsize lenInt = env->GetArrayLength(pDOData);
    jint* pDataInt = env->GetIntArrayElements(pDOData, &bIsCopy2);

    int ret = CP->UpdateDOChannel((uInt32*)pDataInt, line);

    env->ReleaseIntArrayElements(pDOData, pDataInt, 0); //copy the contents back into the array

    return ret;
}

JNIEXPORT jint JNICALL Java_uk_co_strimm_services_JDAQ_UpdateAOChannel
(JNIEnv* env, jobject, jdoubleArray pAOData, jint channel) {

    if (bDebug) {
        (*pOfs) << "Java_uk_co_strimm_services_JDAQ_UpdateAOChannel" << endl;
    }
    jboolean bIsCopy1 = 0;
    jsize lenDouble = env->GetArrayLength(pAOData);
    jdouble* pDataDouble = env->GetDoubleArrayElements(pAOData, &bIsCopy1);

    int ret = CP->UpdateAOChannel(pDataDouble, channel);

    env->ReleaseDoubleArrayElements(pAOData, pDataDouble, 0); //copy the contents back into the array

    return ret;
}

JNIEXPORT jint JNICALL Java_uk_co_strimm_services_JDAQ_ShutdownProtocol
(JNIEnv*, jobject) {
   // MessageBox(NULL, L"ShutdownProtocol", L"", MB_OK);
    
    if (bDebug) {
        (*pOfs) << "Java_uk_co_strimm_services_JDAQ_ShutdownProtocol : CP.ShutdownProtocol" << endl;
    }
    return CP->ShutdownProtocol();
}

JNIEXPORT jint JNICALL Java_uk_co_strimm_services_JDAQ_TerminateProtocol
(JNIEnv*, jobject) {
    // MessageBox(NULL, L"TerminateProtocol", L"", MB_OK);

    if (bDebug) {
        (*pOfs) << "Java_uk_co_strimm_services_JDAQ_TerminateProtocol : CP.TerminateProtocol" << endl;
    }
    return CP->TerminateProtocol();
}

JNIEXPORT jint JNICALL Java_uk_co_strimm_services_JDAQ_GetNextNumSamples(JNIEnv* env, jobject) {
   // MessageBox(NULL, L"GetNextNumSamples", L"", MB_OK);
    
    if (bDebug) {
        (*pOfs) << "Java_uk_co_strimm_services_JDAQ_GetNextNumSamples : CP.GetNextNumSamples" << endl;
    }
    return CP->GetNextNumSamples();
}

JNIEXPORT jint JNICALL Java_uk_co_strimm_services_JDAQ_GetNumberOfDataPoints(JNIEnv* env, jobject) {
   // MessageBox(NULL, L"GetNumberOfDataPoints", L"", MB_OK);
    if (bDebug) {
        (*pOfs) << "Java_uk_co_strimm_services_JDAQ_GetNumberOfDataPoints : CP.GetNumberOfDataPoints" << endl;
    }
    return CP->GetNumberOfDataPoints();
}

JNIEXPORT jdouble JNICALL Java_uk_co_strimm_services_JDAQ_GetCurrentRunSampleTime(JNIEnv* env, jobject) {
   // MessageBox(NULL, L"GetCurrentRunSampleTime", L"", MB_OK);
    if (bDebug) {
        (*pOfs) << "Java_uk_co_strimm_services_JDAQ_GetCurrentRunSampleTime : CP.GetCurrentRunSampleTime" << endl;
    }
    return CP->GetCurrentRunSampleTime();
}

JNIEXPORT jlong JNICALL Java_uk_co_strimm_services_JDAQ_GetNumberOfStages(JNIEnv* env, jobject) {
   // MessageBox(NULL, L"GetNumberOfStages", L"", MB_OK);
    if (bDebug) {
        (*pOfs) << "Java_uk_co_strimm_services_JDAQ_GetNumberOfStages : CP.GetNumberOfStages" << endl;
    }
    return CP->GetNumberOfStages();
}

JNIEXPORT jint JNICALL Java_uk_co_strimm_services_JDAQ_GetNumChannels(JNIEnv* env, jobject, jint type) {
   // MessageBox(NULL, L"GetNumChannels", L"", MB_OK);
    if (bDebug) {
        (*pOfs) << "Java_uk_co_strimm_services_JDAQ_GetNumChannels : CP.GetNumChannels" << endl;
    }
    return CP->GetNumChannels(type);
}

JNIEXPORT jint JNICALL Java_uk_co_strimm_services_JDAQ_GetChannelFromIndex(JNIEnv* env, jobject, jint type, jint ix) {
   // MessageBox(NULL, L"GetChannelFromIndex", L"", MB_OK);
    if (bDebug) {
        (*pOfs) << "Java_uk_co_strimm_services_JDAQ_GetChannelFromIndex : CP.GetChannelFromIndex" << endl;
    }
    return CP->GetChannelFromIndex(type, ix);
}

JNIEXPORT jint JNICALL Java_uk_co_strimm_services_JDAQ_GetPort(JNIEnv* env, jobject, jboolean bIn) {
   // MessageBox(NULL, L"GetPort", L"", MB_OK);
    if (bDebug) {
        (*pOfs) << "Java_uk_co_strimm_services_JDAQ_GetPort : CP.GetPort" << endl;
    }
    return CP->GetPort(bIn);
}

JNIEXPORT jint JNICALL Java_uk_co_strimm_services_JDAQ_GetDeviceID(JNIEnv* env, jobject) {
    //MessageBox(NULL, L"GetDeviceID", L"", MB_OK);
    if (bDebug) {
        (*pOfs) << "Java_uk_co_strimm_services_JDAQ_GetDeviceID : CP.GetDeviceID" << endl;
    }
    return CP->GetDeviceID();
}


//Useful windows api calls
 
JNIEXPORT jint JNICALL Java_uk_co_strimm_services_JDAQ_WinInitSpeechEngine(JNIEnv * env, jobject)													//init TTS engine
{
    if (bDebug) {
        (*pOfs) << "Java_uk_co_strimm_services_JDAQ_WinInitSpeechEngine" << endl;
    }
    //if (EngineStarted)
    //    return 1;

    //if (FAILED(::CoInitialize(NULL)))
    //    return -1;

    //HRESULT hr = CoCreateInstance(CLSID_SpVoice, NULL, CLSCTX_ALL, IID_ISpVoice, (void**)&pVoice);
    //if (!SUCCEEDED(hr))
    //{
    //    ::CoUninitialize();
    //    return -1;
    //}

    //return 1;

    return 0;
}

JNIEXPORT jint JNICALL Java_uk_co_strimm_services_JDAQ_WinShutdownSpeechEngine(JNIEnv * env, jobject)													//deinit TTS engine
{
    if (bDebug) {
        (*pOfs) << "Java_uk_co_strimm_services_JDAQ_WinShutdownSpeechEngine" << endl;
    }
    //if (!EngineStarted)
    //    -1;

    //pVoice->Release();
    //pVoice = NULL;

    //::CoUninitialize();
    //return 1;

    return 0;
}

JNIEXPORT jint JNICALL Java_uk_co_strimm_services_JDAQ_WinSpeak(JNIEnv * env, jobject, jstring outSz1, jboolean bSychronous)							//text to speech
{
    if (bDebug) {
        (*pOfs) << "Java_uk_co_strimm_services_JDAQ_WinSpeak" << endl;
    }
    //jboolean bIsCopy = 0;
    //char* outSz = (char*)env->GetStringUTFChars(outSz1, &bIsCopy);


    //const WCHAR* pwcsName;
    //// required size
    //int nChars = MultiByteToWideChar(CP_ACP, 0, outSz, -1, NULL, 0);
    //// allocate it
    //pwcsName = new WCHAR[nChars];
    //MultiByteToWideChar(CP_ACP, 0, outSz, -1, (LPWSTR)pwcsName, nChars);
    //HRESULT hr;

    //if (!bSychronous)
    //    hr = pVoice->Speak(pwcsName, SPF_ASYNC, NULL);
    //else
    //    hr = pVoice->Speak(pwcsName, SPF_DEFAULT, NULL);

    //delete[] pwcsName;

    //env->ReleaseStringUTFChars(outSz1, outSz);

    //if (SUCCEEDED(hr))
    //    return 1;
    //else
    //    return -1;

    return 0;
}

JNIEXPORT jboolean JNICALL Java_uk_co_strimm_services_JDAQ_GetKeyState(JNIEnv*, jobject, int VK) {
    if (bDebug) {
        (*pOfs) << "Java_uk_co_strimm_services_JDAQ_GetKeyState" << endl;
    }
    bool bRet = GetKeyState(VK) & 0x8000;
    return bRet;

    return 0;
}

JNIEXPORT jdouble JNICALL Java_uk_co_strimm_services_JDAQ_GetCurrentSystemTime(JNIEnv*, jobject) {
    if (bDebug) {
        (*pOfs) << "Java_uk_co_strimm_services_JDAQ_GetCurrentSystemTime" << endl;
    }
    return CP->GetCurrentSystemTime();


}

//test idea
JNIEXPORT jint JNICALL Java_uk_co_strimm_services_JDAQ_GDITestWriteArray
(JNIEnv* env, jobject, jint data_w, jint data_h, jshortArray data, 
    jint numBins, jint numSeries, jstring seriesNames, jdouble xMin, jdouble xMax, jdoubleArray yValues) {

    jboolean bIsCopy0 = 0;
    jsize numData = env->GetArrayLength(data);
    jshort* pix = env->GetShortArrayElements(data, &bIsCopy0);
    BYTE* pix1 = new BYTE[data_w * data_h * 3];
    for (int f = 0; f < data_w * data_h - 1; f++) {
        pix1[3 * f + 1] = pix[f] >> 8; 
    }
    Bitmap bits(data_w, data_h, 3 * 1000, PixelFormat24bppRGB, pix1);

    jboolean bIsCopy1 = 0;
    jsize numData1 = env->GetArrayLength(yValues);
    jdouble* values = env->GetDoubleArrayElements(yValues, &bIsCopy1);


    jboolean bIsCopy2 = 0;
    char* seriesDataSz = (char*)env->GetStringUTFChars(seriesNames, &bIsCopy2);
    wstringstream wss1;
    wss1 << seriesDataSz;

  



    //we are using the GDIplus green channel
    Graphics    graphics(&bits);
    graphics.SetTextRenderingHint(TextRenderingHintAntiAlias);

    //axes line path and join
    Pen penJoin(Color(255, 0, 255, 0), 8);
    penJoin.SetEndCap(LineCapArrowAnchor);
    penJoin.SetStartCap(LineCapArrowAnchor);
    GraphicsPath path;
    path.StartFigure();
    path.AddLine(Point(100, 80), Point(100, data_h-100));
    path.AddLine(Point(100, data_h-100), Point(data_w-60, data_h-100));
    penJoin.SetLineJoin(LineJoinRound);
    graphics.DrawPath(&penJoin, &path);


    //construct histogram
    double binWidth = (double)(xMax - xMin) / numBins;
    double origin[] = { 100, data_h-(int)100 };
    SolidBrush  brush(Color(255, 0, 255, 0));
    FontFamily  fontFamily(L"Times New Roman");
    Font        font(&fontFamily, 20, FontStyleRegular, UnitPixel);

    //draw x tick labels
    for (int f = 0; f < numBins + 1; f++) {
        PointF      pointF(origin[0] + (double)(data_w - 200) / numBins * f, origin[1]+20.0);
        wstringstream wss;
        wss << xMin + f * binWidth;
        graphics.DrawString(wss.str().c_str(), -1, &font, pointF, &brush);
    }

    //draw bars
    double yMax = 0;
    for (int f = 0; f < numBins*numSeries; f++) {
        yMax = max(yMax, values[f]);
    }
    SolidBrush solidBrush(Color(100, 0, 255, 0));
    for (int ff = 0; ff < numSeries; ff++)
    {

        for (int f = 0; f < numBins; f++) {

            graphics.FillRectangle(&solidBrush, (int)(origin[0] + (double)(data_w - 200) / numBins * f), (int)(origin[1] - (double)(data_h - 200) / yMax * values[f + ff*numBins]), (int)((double)(data_w - 200) / numBins), (int)((double)(data_h - 200) / yMax * values[f + ff*numBins]));
        }
    }

    //draw x-axis label
    PointF      pointF((double)(data_w/2 - 75), (double)(data_h - 50));
    graphics.DrawString(L"Image pixel value", -1, &font, pointF, &brush);

    PointF      pointF1(100.0,20.0);
    Font        font2(&fontFamily, 40, FontStyleRegular, UnitPixel);
    graphics.DrawString(wss1.str().c_str(), -1, &font2, pointF1, &brush);

  //  cout << "copy back" << endl;
    for (int f = 0; f < 1000*1000; f++) {
        pix[f] = pix1[3 * f + 1] << 8; // return the data
    }
   // cout << pix[0] << endl;
    delete[] pix1;


    env->ReleaseShortArrayElements(data, pix, 0);
    env->ReleaseDoubleArrayElements(yValues, values, 0);
    env->ReleaseStringUTFChars(seriesNames, seriesDataSz);
 

    return 1;
}

JNIEXPORT jint JNICALL Java_uk_co_strimm_services_JDAQ_GDIWriteNumbersOntoArray
(JNIEnv* env, jobject,  jintArray nums){

    jboolean bIsCopy0 = 0;
    jsize numsNum = env->GetArrayLength(nums);
    jint* pNumbers = env->GetIntArrayElements(nums, &bIsCopy0);
    wstringstream wss;
    for (int f = 0; f < numsNum; f++) {
        wss << pNumbers[f] << "   ";
    }

 
    HDC hdc = GetDC(NULL);
    Graphics    graphics(hdc);
    SolidBrush  brush(Color(255, 0, 0, 255));
    FontFamily  fontFamily(L"Times New Roman");
    Font        font(&fontFamily, 96, FontStyleRegular, UnitPixel);
    PointF      pointF(1500.0f, 30.0f);

    

    graphics.DrawString(wss.str().c_str(), -1, &font, pointF, &brush);
    ReleaseDC(NULL, hdc);

    env->ReleaseIntArrayElements(nums, pNumbers, 0);

    return 1;
}
//
//
//
//
//
//
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





    ////////////////////////////////////////////////////////////////////////////////////////////////////////////
    /*
    * TEST1      All of the simple test protocols confirmed to operate correctly using an oscilloscope
    *           not using a start trigger.
    */
    cout << "TEST1" << "******************************" << endl;
    SimpleProtocol PP(1, -10.0, 10.0, "Dev");
    PP.Init((char*)sz_AIDODI.c_str());

    int numAOChannels = PP.GetNumChannels(0);
    int numDOChannels = PP.GetNumChannels(2);
    int numAIChannels = PP.GetNumChannels(1);
    int numDIChannels = PP.GetNumChannels(3);

    int numSamples = PP.GetNumSamples();
    double* pTime = NULL, * pDataAO = NULL, * pDataAI = NULL;
    uInt32* pDataDI = NULL, * pDataDO = NULL;
    pTime = new double[numSamples];
    if (numAIChannels > 0) pDataAI = new double[numSamples * numAIChannels];
    if (numDIChannels > 0) pDataDI = new uInt32[numSamples * numDIChannels];

    if (numAOChannels > 0) pDataAO = new double[numSamples * numAOChannels];
    if (numDOChannels > 0) pDataDO = new uInt32[numSamples * numDOChannels];

    /*
    if (PP.InitDAQ()) {
        for (int f = 0; f < 1; f++) {
            PP.RunProtocolDAQ(pTime, pDataAO, pDataAI, pDataDO, pDataDI, false, true, 0, -1);
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

    }
    PP.ReleaseDAQ();
    */
    if (!pTime) delete[] pTime;
    if (!pDataAO) delete[] pDataAO;
    if (!pDataAI) delete[] pDataAI;
    if (!pDataDO) delete[] pDataDO;
    if (!pDataDI) delete[] pDataDI;
    /////////////////////////////////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////
    /*
    * TEST2     All of the simple test protocols confirmed to operate correctly using an oscilloscope
    *           however using a start trigger.
    *           Confirm that the start trigger and the protocol starts at the same time where it can be observed
    */
    cout << "TEST2" << "******************************" << endl;
    SimpleProtocol PP1(4, -10.0, 10.0, "Dev");
    PP1.Init((char*)sz_DO.c_str());

    numAOChannels = PP1.GetNumChannels(0);
    numDOChannels = PP1.GetNumChannels(2);
    numAIChannels = PP1.GetNumChannels(1);
    numDIChannels = PP1.GetNumChannels(3);

    numSamples = PP1.GetNumSamples();
    pTime = NULL;
    pDataAO = NULL;
    pDataAI = NULL;
    pDataDO = NULL;
    pDataDI = NULL;
    pTime = new double[numSamples];
    if (numAIChannels > 0) pDataAI = new double[numSamples * numAIChannels];
    if (numDIChannels > 0) pDataDI = new uInt32[numSamples * numDIChannels];

    if (numAOChannels > 0) pDataAO = new double[numSamples * numAOChannels];
    if (numDOChannels > 0) pDataDO = new uInt32[numSamples * numDOChannels];


    if (PP1.InitDAQ()) {
        for (int f = 0; f < 1; f++) {
            //use a start trigger (provided by sig gen)
            PP1.RunProtocolDAQ(pTime, pDataAO, pDataAI, pDataDO, pDataDI, false, true, 0, -1);
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

    }
    PP1.ReleaseDAQ();
    if (!pTime) delete[] pTime;
    if (!pDataAO) delete[] pDataAO;
    if (!pDataAI) delete[] pDataAI;
    if (!pDataDO) delete[] pDataDO;
    if (!pDataDI) delete[] pDataDI;
    /////////////////////////////////////////////////////////////////////////////////////////////////////






    return false;
    }
extern "C" TEST_API bool CompoundProtocolTest() {



    string szCsv = "simpleTest1.csv";
    string szCsv3 = "simpleTest_eg3.csv";
    string szCsv_Dev2 = "simpleTest_Dev2.csv";
    string szCsv1 = "compoundTest1.csv";
    string szCsv2 = "compoundTest2.csv";

    string szFolder = "C:\\Users\\twrig\\source\\repos\\CreateCSV\\CreateCSV\\";

    int testNum = 10;
    if (testNum == 0) {
        //timingMethod = 0, no startTrigger

        CompoundProtocol PM3;
        PM3.InitProtocol(szCsv1, szFolder, true, false, 1, -10.0, 10.0, "Dev");
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
    else if (testNum == 1) {
        //timingMethod = 0, startTrigger


        CompoundProtocol PM3;
        PM3.InitProtocol(szCsv1, szFolder, true, false, 1, -10.0, 10.0, "Dev");
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
        PM3.InitProtocol(szCsv1, szFolder, true, true, 1, -10.0, 10.0, "Dev");
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
        PM3.InitProtocol(szCsv1, szFolder, true, true, 1, -10.0, 10.0, "Dev");
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
        PM3.InitProtocol(szCsv1, szFolder, true, true, 1, -10.0, 10.0, "Dev");
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
        while (ret > 0) {

            for (int f = 0; f < numSamples; f++) {
                pChange[f] = rand() % 10 - 5;
            }
            if (rand() % 7 == 0) {
                PM3.UpdateAOChannel(pChange, 0);
            }
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
    else if (testNum == 5) {
        //timingMethod == 0, no trigger, repeat
        //50% chance to change DO0 on the next run     TO DO
    }
    else if (testNum == 6) {
        //timingMethod == 0, no trigger, repeat, have to reallocate after each run due to then protocols having varying numSamples
           //timingMethod = 0, startTrigger, repeat protocol

           CompoundProtocol PM3;
           PM3.InitProtocol(szCsv2, szFolder, true, true, 1, -10.0, 10.0, "Dev");
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
        PM3.InitProtocol(szCsv1, szFolder, true, false, 1, -10.0, 10.0, "Dev");
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
        PM3.InitProtocol(szCsv1, szFolder, true, true, 1, -10.0, 10.0, "Dev");
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
        PM3.InitProtocol(szCsv1, szFolder, true, false, 1, -10.0, 10.0, "Dev");
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
           PM3.InitProtocol(szCsv1, szFolder, true, true, 1, -10.0, 10.0, "Dev");
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

    return false;
}

extern "C" TEST_API int Test(void)
{
    if (bDebug) {
        (*pOfs) << "Test" << endl;
    }
    cout << "Test load DLL " << endl;
    int x = 0;
    cin >> x;
    SimpleProtocolTest();
    //cout << "Compound protocol test " << endl;
    //CompoundProtocolTest();
    //cout << "test complete" << endl;
    return 0;
}