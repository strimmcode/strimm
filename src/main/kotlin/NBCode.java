//===need it for parameters parsing

import com.fazecast.jSerialComm.SerialPort;
import uk.co.strimm.ArduinoCommunicator;


public class NBCode {


    //======================================
    //======================================
    public static void main(String args[]){
        System.out.println("\nUsing Library Version v" + SerialPort.getVersion());

        //SerialPort.addShutdownHook(new Thread() { public void run() { System.out.println("\nRunning shutdown hook"); } });

        //===list available ports
        SerialPort[] availableComPorts = SerialPort.getCommPorts();
        if (availableComPorts.length == 0){
            System.out.println("*****NO COM PORTS******");
        }
        System.out.println("\nAvailable Ports:\n");
        for (int i = 0; i < availableComPorts.length; ++i){
            System.out.println("   [" + i + "] " + availableComPorts[i].getSystemPortName()
                    + " (" + availableComPorts[i].getSystemPortPath()
                    + "): "+ availableComPorts[i].getDescriptivePortName()
                    + " - " + availableComPorts[i].getPortDescription()
                    + " @ " + availableComPorts[i].getPortLocation());
        }

        //ArduinoCommunicator ardu=ArduinoCommunicator("COM" + 4, 9600); //connect using a fixed port name

        //===connect the first available port
        //!!!!check list size here, must be at least (and better the only) one port or procedure should be more complicate

        System.out.println("{{{{{{{{{{{{1}}}}}}}}}}}}");

        ArduinoCommunicator ardu=new ArduinoCommunicator(availableComPorts[0], 115200);
        //ArduinoCommunicator ardu=new ArduinoCommunicator(availableComPorts[1], 115200);
        //ArduinoCommunicator ardu=new ArduinoCommunicator(availableComPorts[3], 57600);

        ardu.debugOn(); //allow debug printing here
        ardu.setLogLevel(1); //0 - no log, 1 - errors only, 2 - workflow, 5 - detailed
        if (!ardu.connect()) {
            System.out.println("ERROR: cannot connect. Terminated");
            return;
        }
        if (! ardu.isInCommandMode()) {
            System.out.println("Board is not in command mode. Terminated");
            return;
        }
        //if (!ardu.setOutputPins(13, 12, 11, 10, 9, 8)) {
        if (!ardu.setOutputPins(8, 9, 10, 11, 12, 13)) {
            System.out.println("ERROR: cannot set pins");
            return;
        }
        if (!ardu.setDigitalInputPins(2, 3, 4, 5, 6, 7)) {
            System.out.println("ERROR: cannot set input pins");
            return;
        }

        if (!ardu.setAnalogInputPins (255,255,255,255,255,255)) {
            System.out.println("ERROR: cannot setanalog pins");
            return;
        }


        //output samples as a byte where each bit is a pins
        //
        int stepsNumber=10;

        byte seqArray[] = new byte[stepsNumber];

           /*
           //===connect output pins 13, 12...8 to A0...A5
           //===one output (pin 13)
           seqArray[0]=0b0000001;
           seqArray[1]=0b0000000;
           seqArray[2]=0b0000001;
           seqArray[3]=0b0000000;
           seqArray[4]=0b0000001;
           seqArray[5]=0b0000000;
           seqArray[6]=0b0000001;
           seqArray[7]=0b0000000;
           seqArray[8]=0b0000001;
           seqArray[9]=0b0000000;
           */

        //===binary increment value
        seqArray[0]=0b0000001;
        seqArray[1]=0b0000010;
        seqArray[2]=0b0000100; //0b0011 skipped
        seqArray[3]=0b0000101;
        seqArray[4]=0b0000111;
        seqArray[5]=0b0001000;
        seqArray[6]=0b0001001;
        seqArray[7]=0b0101010; //extra pin used
        seqArray[8]=0b0001011;
        seqArray[9]=0b0101100; //extra pin used

           /*
           //==="running one"
           seqArray[0]=0b0000001;
           seqArray[1]=0b0000010;
           seqArray[2]=0b0000100;
           seqArray[3]=0b0001000;
           seqArray[4]=0b0010000;
           seqArray[5]=0b0100000;
           seqArray[6]=0b0000001;
           seqArray[7]=0b0000010;
           seqArray[8]=0b0000100;
           seqArray[9]=0b0001000;
           */


           /*
           seqArray[0]=0;
           seqArray[1]=(byte)0xFF;
           seqArray[2]=0;
           seqArray[3]=(byte)0xFF;
           seqArray[4]=0;
           seqArray[5]=(byte)0xFF;
           seqArray[6]=0;
           seqArray[7]=(byte)0xFF;
           seqArray[8]=0;
           seqArray[9]=(byte)0xFF;
           */
           /*
           seqArray[0]=(byte) 'a';
           seqArray[1]=(byte) 'b';
           seqArray[2]=(byte) 'c';
           seqArray[3]=(byte) 'd';
           seqArray[4]=(byte) 'e';
           */

        long stepsMicros=500000;

        int resultArray_digital[]=new int[stepsNumber];
        int resultArray_analog[][]=new int[stepsNumber][ArduinoCommunicator.ANALOG_INPUTS_NUMBER];
        int tmpTimingsArray[][]=new int[stepsNumber][ArduinoCommunicator.ANALOG_INPUTS_NUMBER];


        if(!ardu.doMeasurement(seqArray, stepsMicros, resultArray_digital, resultArray_analog, tmpTimingsArray)){
            System.out.print("No data returned");
        }
        if (! ardu.isInCommandMode()) {
            System.out.println("Board is not in command mode. Terminated");
            return;
        }


        for (int i = 0; i < stepsNumber; i++) {
            System.out.println(""+i+":"+Integer.toBinaryString(resultArray_digital[i]));
        }

         for (int i = 0; i < stepsNumber; i++) {
            System.out.print(""+i+":");
            for (int inpNo = 0; inpNo < ArduinoCommunicator.ANALOG_INPUTS_NUMBER; inpNo++) {
                System.out.print(""+tmpTimingsArray[i][inpNo]+"\t"+resultArray_analog[i][inpNo]+";\t\t");
            }
            System.out.print("\r\n");
        }

        //================================

        if (!ardu.setAnalogInputPins (0,
                ArduinoCommunicator.PIN_NOT_USED,
                ArduinoCommunicator.PIN_NOT_USED,
                ArduinoCommunicator.PIN_NOT_USED,
                ArduinoCommunicator.PIN_NOT_USED,
                ArduinoCommunicator.PIN_NOT_USED
        )
        ){
            System.out.println("ERROR: cannot setanalog pins");
            return;
        }
        stepsMicros=1000;
        //===binary increment value
        seqArray[0]=0b0000001;
        seqArray[1]=0b0000000;
        seqArray[2]=0b0000101; //0b0011 skipped
        seqArray[3]=0b0000100;
        seqArray[4]=0b0000111;
        seqArray[5]=0b0001000;
        seqArray[6]=0b0001001;
        seqArray[7]=0b0101010; //extra pin used
        seqArray[8]=0b0001011;
        seqArray[9]=0b0101100; //extra pin used

        if(!ardu.doMeasurement(seqArray, stepsMicros, resultArray_digital, resultArray_analog, tmpTimingsArray)){
            System.out.print("No data returned");
        }

        if (! ardu.isInCommandMode()) {
            System.out.println("Board is not in command mode. Terminated");
            return;
        }

        for (int i = 0; i < stepsNumber; i++) {
            System.out.println(""+i+":"+Integer.toBinaryString(resultArray_digital[i]));
        }

        for (int i = 0; i < stepsNumber; i++) {
            System.out.print(""+i+":");
            for (int inpNo = 0; inpNo < ArduinoCommunicator.ANALOG_INPUTS_NUMBER; inpNo++) {
                System.out.print(""+tmpTimingsArray[i][inpNo]+"\t"+resultArray_analog[i][inpNo]+";\t\t");
            }
            System.out.print("\r\n");
        }





        System.out.println("Now let's test the continous output");
        System.out.println("Set infinite blinking on pin 13 (built-in LED).\r\n (Other pins are not used)...");
        if (!ardu.setOutputPins(13
                ,ArduinoCommunicator.PIN_NOT_USED
                ,ArduinoCommunicator.PIN_NOT_USED
                ,ArduinoCommunicator.PIN_NOT_USED
                ,ArduinoCommunicator.PIN_NOT_USED
                ,ArduinoCommunicator.PIN_NOT_USED

        )
        )
        {
            System.out.println("ERROR: cannot set pins");
            return;
        }
        //
        //
        //how does it know that infiniteSeqArray has 8
        byte infiniteSeqArray[] = new byte[8];
        infiniteSeqArray[0]=0b0001;
        infiniteSeqArray[1]=0b0000;
        infiniteSeqArray[2]=0b0001;
        infiniteSeqArray[3]=0b0000;
        infiniteSeqArray[4]=0b0001;
        infiniteSeqArray[5]=0b0001;
        infiniteSeqArray[6]=0b0000;
        infiniteSeqArray[7]=0b0000;

        if(!ardu.startContinousSequence(infiniteSeqArray, 500000)){
            System.out.print("Infinite sequence not started");
            return;
        }
        System.out.println("Just doing nothing for a while.");
        System.out.println("See how LED blinking (2 shorts, 1 long)...");
        ardu.blockingDelay(5000);

        //===Just make some delay, any other things can be done here instead
        System.out.println("Check if Ardu on command mode (should be not)");
        //===just check if device is not on command state (means in sequence state)
        if (ardu.isInCommandMode()) {
            System.out.println("PROBLEM: Board in command mode whoen should be in sequence.\r\n Terminated");
            return;
        }
        System.out.println("\t\tseems no (that's OK)");


        ardu.blockingDelay(5000);
        System.out.println("Check if in sequence  mode...");
        if (!ardu.isInContinousMode()) {
            System.out.println("PROBLEM: Board in not in sequence (continous output) mode.\r\n Terminated");
            return;
        }
        System.out.println("\t\tseems yes (OK)");

        ardu.blockingDelay(5000);
        System.out.println("Another small pause...");
        ardu.blockingDelay(5000);
        System.out.println("More pause...");
        ardu.blockingDelay(5000);
        //===Another delay, any other things can be done here instead
        System.out.println("Let's stop sequence!");
        //===just check if device is not on command state (means in sequence state)
        if (! ardu.stopSequence()) {
            System.out.println("PROBLEM: sequence seems not stopped.\r\n Terminated");
            return;
        }
        System.out.println("Check if in command mode...");
        if (! ardu.isInCommandMode()) {
            System.out.println("PROBLEM:Board is not in command mode. Terminated");
            return;
        }
        System.out.println("\t\tseems yes (OK)");

        System.out.println("\r\n\r\nTESTING ENDED, close application");

        if(!ardu.startContinousSequence(infiniteSeqArray, 500000)){
            System.out.print("Infinite sequence not started");
            return;
        }

        System.out.println("TW continuous 10 s");
        try {
            Thread.sleep(50000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if (! ardu.stopSequence()) {
            System.out.println("PROBLEM: sequence seems not stopped.\r\n Terminated");
            return;
        }

    }    //\public static void main(String args[])...

}
