package uk.co.strimm.services;


import javax.swing.*;
import java.io.*;
import java.util.*;

public class JDAQ {
    public int deviceID;

    static {
        System.out.println("*****************here***************");
        System.loadLibrary("Test");//TODO hardcoded
        System.out.println("*****************here***************");

        //changed Path in the run environment
    }

    public static void main(String[] args) throws java.io.IOException {
//        JDAQ daq = new JDAQ();
//        daq.WinBeep(300,300);


//        boolean[] bbb = {true, true, true, true, true, true, true, true};
//        String szAICsv = "C:\\Users\\twrig\\source\\repos\\CreateCSV\\CreateCSV\\analogAI.csv";
//        String szAOCsv = "C:\\Users\\twrig\\source\\repos\\CreateCSV\\CreateCSV\\analogAO.csv";
//        String szDOCsv = "C:\\Users\\twrig\\source\\repos\\CreateCSV\\CreateCSV\\digitalDO.csv";
//        String szGENsv = "C:\\Users\\twrig\\source\\repos\\CreateCSV\\CreateCSV\\GEN.csv";
//
//        int numSamples = 0;
//        BufferedReader reader = new BufferedReader(new FileReader(szDOCsv));
//        while (reader.readLine() != null) {
//            numSamples++;
//        }
//        reader.close();
//        numSamples--;
//        JDAQ daq = new JDAQ();
//        int id1 = daq.AddProtocol(1,-10.0,10.0,100,numSamples,true,true,true,
//            szAOCsv, szDOCsv, bbb);
//        int id2 = daq.AddProtocol(1,-10.0,10.0,10000,numSamples,true,true,true,
//                szAOCsv, szDOCsv, bbb);
//        int id3 = daq.AddProtocol(1,-10.0,10.0,1000    ,numSamples,true,true,true,
//                szAOCsv, szDOCsv, bbb);
//        boolean error = daq.InitDAQ(id3);
//
//        System.out.println("numSamples " + numSamples);
//        double[] dataAI=  new double[numSamples*8];
//        for (double x : dataAI){
//            x = 0.0;
//        }
//        //long timeStart = System.currentTimeMillis();
//        ///////////////////////////////////////////////////////////////////////
//        daq.RunProtocol(dataAI);
//        daq.ShutdownDAQ(); //remember to use Shutdown

//        error = daq.InitDAQ(id2);
//        daq.RunProtocol(dataAI);
//        long timeStart = System.currentTimeMillis();
//        daq.ShutdownDAQ();
        //      long timeFinish = System.currentTimeMillis();
        //////////////////////////////////////////////////////////////////////
        //long timeFinish = System.currentTimeMillis();
        //       System.out.println("Time: " + (double)(timeFinish - timeStart)/1000.0);
//write AI csv



//
//
//
//        int numSamples = 20;
//
//
//        double[] pInjectAO = new double[numSamples];
//        for (int f = 0; f < numSamples; f++) {
//            if (f % 2 == 0) {
//                pInjectAO[f] = 5.0;
//            } else {
//                pInjectAO[f] = -5.0;
//            }
//        }
//
//        int[] pInjectDO = new int[numSamples];
//        for (int f = 0; f < numSamples; f++) {
//            Random random = new Random();
//            int randomInt = random.nextInt(10);
//            if (randomInt > 5) {
//                pInjectDO[f] = 1;
//            } else {
//                pInjectDO[f] = 0;
//            }
//        }
//
//
//        String szFolder = "C:\\Users\\twrig\\source\\repos\\CreateCSV\\CreateCSV\\";
//        String szCsv1 = "C:\\Users\\twrig\\source\\repos\\CreateCSV\\CreateCSV\\compoundTest1.csv";
//        String szCsv2 = "C:\\Users\\twrig\\source\\repos\\CreateCSV\\CreateCSV\\compoundTest2.csv";
//
//        String szSimp1 = "C:\\Users\\twrig\\source\\repos\\CreateCSV\\CreateCSV\\simpleTest1.csv";
//        String szSimp3 = "C:\\Users\\twrig\\source\\repos\\CreateCSV\\CreateCSV\\simpleTest3.csv";
//        String szSimp2 = "C:\\Users\\twrig\\source\\repos\\CreateCSV\\CreateCSV\\simpleTest2.csv";
//
//        JDAQ daq = new JDAQ();
//
//        //DEMO detect keys
////        for (int f = 0; f < 10000000; f++) {
////            boolean b = daq.GetKeyState('A');
////
////            System.out.println(b);
////
////
////        }
//
//
//        daq.InitProtocol(szCsv1, szFolder, true, true, 1, -10.0, 10.0);
//        daq.SetStartTrigger(false, 0);
//        //0 consecutive
//        //1 triggers
//        //2 pc timing
//        daq.SetTimingMethod(0); //consecutive
//
//        numSamples = daq.GetNextNumSamples();
//        int numAOChannels = daq.GetNumChannels(0);
//        int numAIChannels = daq.GetNumChannels(1);
//        int numDOChannels = daq.GetNumChannels(2);
//        int numDIChannels = daq.GetNumChannels(3);
//        double[] dataAI = new double[numSamples * numAIChannels];
////        for (double x : dataAI){
////            x = 0.0;
////        }
//        int[] dataDI = new int[numSamples * numDIChannels];
////        for (int x : dataDI){
////            x = 0;
////        }
//
//        double[] dataAO = new double[numSamples * numAOChannels];
//        int[] dataDO = new int[numSamples * numDOChannels];
//
////this is not working correct from VS
//        //VS sort of working
//
////        daq.UpdateAOChannel(pInjectAO, 0);
////        daq.UpdateDOChannel(pInjectDO, 0);
//        //need to use RunNext1 because of C limitions with overloading
//        //       daq.RunNext1(dataAO, dataAI, dataDO, dataDI);
//        int bRun = 1;//daq.RunNext1(dataAO, dataAI, dataDO, dataDI);
////        double expStartTime = daq.GetCurrentRunStartTime();
//
////        daq.UpdateAOChannel(pInjectAO, 0);
////        daq.UpdateDOChannel(pInjectDO, 0);
//        int cnt1 = 0;
//        while (bRun >= 0) {
//            if (cnt1 % 3 == 0) daq.UpdateAOChannel(pInjectAO, 0);
//            bRun = daq.RunNext1(dataAO, dataAI, dataDO, dataDI);
//
//            cnt1++;
//            //System.out.println(daq.GetCurrentRunStartTime());
////            System.out.println(daq.GetNumberOfStages());
////           System.out.println(daq.GetNumberOfDataPoints());
////            System.out.println(daq.GetPort(false));
//            int cnt = 0;
//            for (int f = 0; f < numSamples; f++) {
//                System.out.print(dataDO[cnt] + " ");
//                cnt++;
//                System.out.println(dataDO[cnt] + " ");
//                cnt++;
//            }
//            double startTime = daq.GetCurrentRunStartTime();
//            double endTime = daq.GetCurrentRunEndTime();
//            System.out.println(startTime + "   " + endTime);
//            System.out.println("");
//            System.out.println("");
//        }
//
//        System.out.println("stages=" + daq.GetNumberOfStages());
//        daq.ShutdownProtocol();
//

        //write the data out
//        System.out.println("Write AI file");
//        FileWriter csvWriter = new FileWriter(szAICsv);
//        ArrayList<String> row1 = new ArrayList<String>();
//        for (int j = 0; j<8; j++){
//            row1.add("AI" + Integer.toString(j));
//        }
//        csvWriter.append(String.join(",", row1));
//        csvWriter.append("\n");
//        for (int i = 0; i<numSamples; i ++){
//            ArrayList<String> row = new ArrayList<String>();
//            for (int j=0; j<8; j++){
//                row.add(Double.toString(dataAI[8*i+j]));
//            }
//            csvWriter.append(String.join(",",row));
//            csvWriter.append("\n");
//        }
//        csvWriter.flush();
//        csvWriter.close();
//// general file
//        System.out.println("Write General file");
//        BufferedReader AO = new BufferedReader(new FileReader(szAOCsv));
//        BufferedReader DO = new BufferedReader(new FileReader(szDOCsv));
//        BufferedReader AI = new BufferedReader(new FileReader(szAICsv));
//
//        FileWriter GEN = new FileWriter(szGENsv);
//
//        for (int f = 0; f<numSamples; f++){
//
//            String szAO = AO.readLine();
//            String szDO = DO.readLine();
//            String szAI = AI.readLine();
//            ArrayList<String> row = new ArrayList<String>();
//            row.add(szAO);
//            if (f==0){
//                row.add(szDO);
//            } else {
//                row.add(Integer.toBinaryString(Integer.parseInt(szDO)));
//            }
//            row.add(szAI);
//            GEN.append(String.join(",", row));
//            GEN.append("\n");
//
//        }
//
//
//
//        GEN.flush();
//        GEN.close();
//
//        AO.close();
//        DO.close();
//        AI.close();
//
//        daq.RemoveProtocol(id3);


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