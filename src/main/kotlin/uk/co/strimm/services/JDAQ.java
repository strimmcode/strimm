package uk.co.strimm.services;


import com.typesafe.config.ConfigException;

import java.io.File;
import java.io.*;
import java.net.*;
import java.net.Socket;
import java.util.concurrent.*;
import java.util.function.Function;

public class JDAQ {

   // public int deviceID;


    public native int Test();
    public native int TestStart();
    public native int PreTestStart();
    public native int TestStop();

    public native boolean GetKeyState(int VK);
    public native int WinBeep(int freq, int dur);
    public native int GDIPrintText
            (int data_w, int data_h, short[] data,
             String szText, double xPos, double yPos, int fontSize);
    public native int GDIWriteNumbersOntoArray(int[] nums);
    public native int GDICreateFLFMImageFile(int id, int width, int height, short[] pix, String fileSz, int count);
    public native int GDIFLFMInit();
    public native int GDICreateFLFMMontage(int id, int data_w, int data_h, short[] data, int data_wt, int data_ht, short[] data1);
    public native int GDICreateFLFMImage(int id, int data_w, int data_h, short[] data);
    public native int GDITestWriteArray(int data_w, int data_h, short[] data, int numBins, int numSeries, String seriesName, double xMin, double xMax, double[] yValues);
    public native int WinInitSpeechEngine();
    public native int WinShutdownSpeechEngine();
    public native int WinSpeak(String outSz1, boolean bSychronous);
    public native int WinAlert();



    // NIDAQ  Continuous Source
    public native int NIDAQContinuousSourceInit
            (int deviceID, String szCsv, boolean bStartTrigger, boolean bRisingEdge, int pFIx, double timeoutSec, double minV, double maxV);

    public native int NIDAQContinuousSourceRun
            (int deviceID, double[] pTimes, double[] pAOData, double[] pAIData, int[] pDOData, int[] pDIData);

    public native int NIDAQContinuousSourceShutdown
            (int deviceID);

    public native int NIDAQContinuousSourceGetNumSamples
            (int deviceID);

    public native double NIDAQContinuousSourceGetSampleFreq
            (int deviceID);

    public native int NIDAQContinuousSourceGetNumAOChannels
            (int deviceID);

    public native int NIDAQContinuousSourceGetNumAIChannels
            (int deviceID);

    public native int NIDAQContinuousSourceGetNumDOChannels
            (int deviceID);

    public native int NIDAQContinuousSourceGetNumDIChannels
            (int deviceID);

    public native int NIDAQContinuousSourceGetChannelFromIndex
            (int deviceID, int type, int index);

    public native int NIDAQContinuousSourceGetDOPort
            (int deviceID);

    public native int NIDAQContinuousSourceGetDIPort
            (int deviceID);


    // NIDAQ Source

    public native int  NIDAQSourceInit
            (String szCsv, boolean bCompound, boolean bRepeat , int deviceID, String deviceName, double minV, double maxV);

    public native int  NIDAQSourceSetStartTrigger
            (int deviceID, boolean bStartTrigger, int pFIx, boolean bRisingEdge, double timeoutSec);

    public native int  NIDAQSourceSetTimingMethod
            (int deviceID, int timingMethod);

    public native int  NIDAQSourceGetNumAOChannels
            (int deviceID);

    public native int  NIDAQSourceGetNumAIChannels
            (int deviceID);

    public native int  NIDAQSourceGetNumDOChannels
            (int deviceID);

    public native int  NIDAQSourceGetNumDIChannels
            (int deviceID);

    public native int  NIDAQSourceGetNumSamples
            (int deviceID);

    public native double  NIDAQSourceGetSampleFreq
            (int deviceID);

    public native int  NIDAQSourceGetChannelFromIndex
            (int deviceID, int type, int index);

    public native int  NIDAQSourceGetDOPort
            (int deviceID);

    public native int  NIDAQSourceGetDIPort
            (int deviceID);

    public native int  NIDAQSourceRun
            (int deviceID, double[] pTimes, double[] pAOData, double[] pAIData, int[] pDOData, int[] pDIData);

    public native int  NIDAQSourceShutdown
            (int deviceID);



    //NIDAQ DataSink
    public native int NIDAQDataSinkInit
            (int deviceID, double minV, double maxV);

    public native int NIDAQDataSinkRun
            (int deviceID, double[] pAOData, int[] pDOData, int numSamples, double sampleFreq, int numAOChannels, int[] AOChannels, int numDOChannels, int[] DOChannels, int DOport);

    public native int NIDAQDataSinkShutdown(int deviceID);


}