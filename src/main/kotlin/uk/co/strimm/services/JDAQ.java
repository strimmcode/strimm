package uk.co.strimm.services;


import java.io.File;
import java.util.concurrent.*;
import java.util.function.Function;

public class JDAQ {
    public int deviceID;

    static {
        System.out.println("LIBRARY PATH :" + System.getProperty("java.library.path"));
        System.out.println("WORKINGDIRECTORY : " + System.getProperty("user.dir"));

        System.out.println("*****************here***************");
        System.loadLibrary("Test");
        System.out.println("*****************here***************");




    }

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
}