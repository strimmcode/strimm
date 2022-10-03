package uk.co.strimm.services;

import uk.co.strimm.gui.GUIMain;
import java.util.logging.Level;

//TODO is this a service? If not can it be moved without things breaking?
public class JDAQ {
    public int deviceID;

    static {
        try {
            System.loadLibrary("Test");//TODO hardcoded
        } catch (Exception ex) {
            GUIMain.loggerService.log(Level.SEVERE, "Error loading C++ DAQ library 'Test'. Message" + ex.getMessage());
            GUIMain.loggerService.log(Level.SEVERE, ex.getStackTrace());
        }
    }

    public native int GetCameraMapStatus();

    public native int RegisterCameraForCameraMap(String cameraSz,
                                                 int w, int h, int bitDepth, int binning, int numRect,
                                                 int[] rois_x, int[] rois_y, int[] rois_w, int[] rois_h);

    public native int StartCameraMap();

    public native int ShutdownCameraMap();

    public native int Add16BitImageDataCameraMap(String cameraSz, double fps, double interval, int w, int h, short[] pix, boolean bSave);

    public native int Add8BitImageDataCameraMap(String cameraSz, double fps, double interval, int w, int h, byte[] pix, boolean bSave);

    public native int AddARGBBitImageDataCameraMap(String cameraSz, double fps, double interval, int w, int h, byte[] pix, boolean bSave);

    public native int WinBeep(int freq, int dur);

    public native int GDIWriteNumbersOntoArray(int[] nums);

    public native int GDITestWriteArray(int data_w, int data_h, short[] data, int numBins, int numSeries, String seriesName, double xMin, double xMax, double[] yValues);

    public native int InitProtocol(String szProtocol, String szFolder, boolean bCompound, boolean bRepeat, int deviceID, double minV, double maxV, String deviceName);

    public native int SetStartTrigger(boolean bStartTrigger, int pFIx, boolean bRisingEdge, double timeoutSec);

    public native int SetTimingMethod(int timingMethod);

    public native synchronized int RunNext(double[] pTimes, double[] pAOData, double[] pAIData, int[] pDOData, int[] pDIData);

    public native int GetBufferDimensions(int[] bufSizes);

    public native synchronized int UpdateDOChannel(int[] pDODate, int line);

    public native synchronized int UpdateAOChannel(double[] pAOData, int channel);

    public native int ShutdownProtocol();

    public native int TerminateProtocol();

    public native int GetNextNumSamples();

    public native int GetNumberOfDataPoints();

    public native double GetCurrentRunSampleTime();

    public native long GetNumberOfStages();

    public native int GetNumChannels(int type);

    public native int GetChannelFromIndex(int type, int ix);

    public native int GetPort(boolean bIn);

    public native int GetDeviceID();

    public native int WinInitSpeechEngine();

    public native int WinShutdownSpeechEngine();

    public native int WinSpeak(String outSz1, boolean bSychronous);

    public native boolean GetKeyState(int VK);

    public native double GetCurrentSystemTime();
}