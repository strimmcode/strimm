import com.fazecast.jSerialComm.SerialPort;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.Integer;

//===need it for parameters parsing
import java.util.ArrayList;
import java.util.List;


public class ArduinoCommunicator {

    static final int responceTimeout=50;
    //static final int responceTimeout=2000; //debug stage

    public static final int ANALOG_PIN_NOT_USED=0xFF;
    public static final int ANALOG_INPUTS_NUMBER = 6;
    static final String STR_OK= "<OK>";
    static final String STR_READY= "<READY>";
    static final String STR_COMMAND_MODE ="<CMD_MOD>\r\n";
    static final String STR_ERROR ="<ERROR>";
    static final String STR_DIGITAL_DATA ="<D-DATA>";
    static final String STR_ANALOG_DATA ="<A-DATA>";
    static final String STR_ECHO_ON ="<ECHO_ON>";
    static final String STR_ECHO_OFF ="<ECHO_OFF>";

    static final String STR_STATUS="<STATUS>";
    static final String STR_DEV_RESTARTED ="<DEVICE RESTARTED>";

    static final String STR_OUTPUTS_SET ="<OUTP SET>";
    static final String STR_ANALOG_IPUTS_SET ="<A_INP SET>";
    static final String STR_DIGITAL_IPUTS_SET ="<D_INP SET>";
    static final String STR_DO_MEASUREMENT_CONFIRM ="<DO_MEAS>";

    static final String STR_MEASUREMENT_DATA_READY ="<DAT_READY>";

    int logLevel=1; //0-no log, 1-errors only, 5 - maximal log
    public static List<String> splitTokens(String text, String delim) {
        List<String> tokens = new ArrayList<String>();
        String[] tokenArr = text.split(delim);
        for (String token : tokenArr) {
            tokens.add(token.trim());
        }
        return tokens;
    }

    private SerialPort myComPort;
    private boolean debugPrintingActive;
    private InputStream inStream;
    private OutputStream outStream;
    public void setLogLevel(int parLogLevel){
        logLevel=parLogLevel;
    }
    //======================================
    //======================================
    //======================================
    // waits for parExpectedString,
    //returns true if string received during a specified time, otherwise - false
    //======================================
    public boolean waitForString(long parReadingTimeoutMillis, String parExpectedString ){
        log(4,"\n>>Waiting for string '"+parExpectedString+"'', timeout "+parReadingTimeoutMillis+" msec...");
        long startingMillis=java.lang.System.currentTimeMillis();
        String receivedString="";


        //myComPort.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, 5, 0);
        //InputStream inStream = myComPort.getInputStream();


        while((java.lang.System.currentTimeMillis() - startingMillis) < parReadingTimeoutMillis){
//System.out.println(java.lang.System.currentTimeMillis() - startingMillis);
            //=== HERE WE ARE READING THE INCOMING CHARACTERS AND PRINT THEM
            try {

                if (inStream.available() > 0){
                    char incomingChar=(char)inStream.read();
                    receivedString=receivedString+incomingChar;
                    if (receivedString.contains(parExpectedString)) {
                        log(4,"\texpected substring received");
                        log(5,"\t,what was received:["+receivedString+"]");
                        log(5,"\t, waiting time was "+(java.lang.System.currentTimeMillis()-startingMillis)+" msec.");
                        //inStream.close();
                        return true;
                    }//\if (incomingChar==parLineEndingCharacter)...
                }
            } catch (com.fazecast.jSerialComm.SerialPortTimeoutException e) {
                //do nothing
            } catch (Exception e) {
                log(1,"\tERROR: in waiting for string an unknown serial exception:"+e);
                return false;
            }
        } //\while((java.lang.System.currentTimeMillis() - startingMillis) < parReadingTimeoutMillis)....
        log(4,"time is over, what we have received:["+receivedString+"]");
        return false;
        //inStream.close();
    }

    public ArduinoCommunicator (SerialPort parComPort, int parBaudrate) {
        myComPort=parComPort;
        myComPort.setBaudRate(parBaudrate) ;
        myComPort.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING|SerialPort.TIMEOUT_WRITE_BLOCKING, 5, 5);
        //myComPort.addDataListener(this);

    } //\public ArduinoCommunicator...

    public boolean connect(){

        try {
            if (myComPort.isOpen()==false) {
                log(2,"Opening port...");

                if (myComPort.openPort()) {
                    log(2,"connected");

                } else {
                    log(1,"ERROR:Failed to open port") ;
                    return false;
                }
            } else {
                log(1, "ERROR:Port is already open!");
                return false;
            } //\if (!myComPort.isOpen) ...
        } catch (Exception e) {
            log(1,"Exception error:"+e);
            return false;
        }
        try {
            //===handle board restart
            inStream = myComPort.getInputStream();
            outStream=myComPort.getOutputStream();
            clearIncomingBuffer();

            log(3,"========   Wait until arduino is ready");
            //===when com-port is connected it may take some time before device is ready
            if (!waitForString(10000, STR_DEV_RESTARTED)){
                log(1,"Arduino is not ready!\nMay be wrong firmware or need reconnect port/restart a board?");
                return false;
            }
            //ardu.clearIncomingBuffer();
            /*
            if (!waitForString(100, STR_READY)){
                log("Arduino is not ready!\nMay be wrong firmware or need reconnect port/restart a board?");
                return false;
            }
            */
            clearIncomingBuffer();
        } catch (Exception e) {
            log(1,"Stream exception error:"+e);
            return false;
        }


        log(2,"Board CONNECTED");
        return true;
    }
    public void closePort(){
        try {
            outStream.flush();
            outStream.close();
            inStream.close();
            myComPort.closePort();

        } catch (Exception e) {
            log(1, "Port closing exception error:"+e);
        }

    }
    //======================================
    public boolean waitForReady(long parReadingTimeoutMillis){
        boolean received = waitForString(parReadingTimeoutMillis, "<READY>\r\n");
        //readStringUntilChar(10, '\n');
        return received;
    }
    //======================================
    public boolean waitForOK(long parReadingTimeoutMillis){
        boolean received = waitForString(parReadingTimeoutMillis, "<OK>\r\n");
        //readStringUntilChar(10, '\n');
        return received;
    }
    //======================================
    public boolean waitForCommandMode(long parReadingTimeoutMillis){
        log(4,"Waiting for command mode...");
        boolean received = waitForString(parReadingTimeoutMillis, STR_COMMAND_MODE);
        //readStringUntilChar(10, '\n');
        return received;
    }

    //======================================
    //======================================
    public boolean sendCommand(String parCommandString, String parConfirmationString){
        log(3,"Send "+parCommandString+", wait for "+parConfirmationString);
        clearIncomingBuffer();
        writeString(parCommandString+"\r\n");
        if (parConfirmationString != null) {
            if (!waitForString(responceTimeout, parConfirmationString)) {
                log(3,"confirmation string not received");
                return false;
            }
        }
        return waitForOK(2000);
    }
    //======================================
    //======================================
    public String readStringUntilChar(long parReadingTimeoutMillis, char parLineEndingCharacter){
        log(5,"\n>>Reading for "+parReadingTimeoutMillis+" msec or until line ended...");
        long startingMillis=java.lang.System.currentTimeMillis();


        //myComPort.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, 10, 0);
        String resultString="";


        while((java.lang.System.currentTimeMillis() - startingMillis) < parReadingTimeoutMillis){
            //=== HERE WE ARE READING THE INCOMING CHARACTERS AND PRINT THEM
            try {
                char incomingChar=(char)inStream.read();
                resultString=resultString+incomingChar;
                //System.out.print(incomingChar);
                if (incomingChar==parLineEndingCharacter) {
                    log(5,"\tline ending character received, the whole string was:["+resultString+"]");
                    return resultString;
                }//\if (incomingChar==parLineEndingCharacter)...
            } catch (com.fazecast.jSerialComm.SerialPortTimeoutException e) {
                //do nothing
            } catch (Exception e) {
                log(1,"\treadStringUntilChar::unknown serial exception:"+e);
                return resultString;
            }
        } //\while((java.lang.System.currentTimeMillis() - startingMillis) < parReadingTimeoutMillis)....
        log(2,"readStringUntilChar::time is over");
        return resultString;
        //inStream.close();
    }
    //======================================
    //function to read any data from incoming buffer to ensure no garbage is waiting in the port
    //======================================
    public void clearIncomingBuffer(){
        log(5,"Clear incoming buffer");

        long startingMillis=java.lang.System.currentTimeMillis();

        //myComPort.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, 0, 0);
        //myComPort.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, 5, 0);

        try {
            if(inStream.available()>0)
                while(inStream.available()>0){
                    char incomingChar=(char)inStream.read();
                    //===here we just ignore all readed bytes.
                    if (debugPrintingActive) {
                        System.out.print("{"+incomingChar+"[0x"+Integer.toString(incomingChar, 16)+"/0b"+Integer.toString(incomingChar, 2)+"]}"); //printing of HEX value for non-printable may be usefull here
                    }
                    //if(inStream.available()<1){
                    //    blockingDelay(1); //wait in case more bytes are onthe way
                    //}
                } //\while(inStream.available()>0)...
            //inStream.close();
        } catch (Exception e) {
            log(1,"\tserial exception when cleaning buffer:"+e);
            return;
        }


    }


    public void writeString(String parString){
        log(5,"\t\tSend string:["+parString+"]");
        myComPort.writeBytes(parString.getBytes(), parString.length());
    }

    public void switchToEchoMode(){
        clearIncomingBuffer();
        sendCommand("~e", STR_ECHO_ON);

        waitForReady(1000);
        clearIncomingBuffer();
        log(2,"Now in echo mode");
    }
    public void switchToCommandMode(){
        log(2,"Switch in command mode");
        clearIncomingBuffer();
        writeString("+++\r\n");

        waitForReady(1000);
        clearIncomingBuffer();
        log(2,"Now in command mode"); //better to check by status command!!!
    }

    //======================================
    //in case direct manipulation or native functions for SerialPort class are necessary
    //======================================
    public SerialPort getComPort(){
        return myComPort;
    }

    //======================================
    //======================================
    public void debugOn(){debugPrintingActive=true;}
    //======================================
    public void debugOff(){debugPrintingActive=false;}
    //======================================
    public void log(int parLogLevel, String parString){
        if (logLevel>=parLogLevel) {
            if (debugPrintingActive) {
                System.out.println("[ardu]>>"+parString);
            }
        }
    }//\public void log...
    //======================================
    //======================================
    public void blockingDelay(long parDelayMillis){
        try {
            log(3,"Pause (delay) for "+parDelayMillis+" msec...\n");
            Thread.sleep(parDelayMillis);
        } catch (InterruptedException ie) {
            log(1,"Interrupt exception occurs");
            Thread.currentThread().interrupt();
        }
    }
    //======================================
    //======================================

    public boolean setOutputPins(int par_0, int par_1, int par_2, int par_3, int par_4, int par_5){
        String commandStr="~o"
                +" "+par_0
                +" "+par_1
                +" "+par_2
                +" "+par_3
                +" "+par_4
                +" "+par_5
                ;
        return sendCommand(commandStr, STR_OUTPUTS_SET);
    }
    //======================================
    //======================================
    public boolean setDigitalInputPins(int par_0, int par_1, int par_2, int par_3, int par_4, int par_5){
        String commandStr="~i"
                +" "+par_0
                +" "+par_1
                +" "+par_2
                +" "+par_3
                +" "+par_4
                +" "+par_5
                ;
        return sendCommand(commandStr, STR_DIGITAL_IPUTS_SET);
    }
    //======================================
    //======================================
    public boolean setAnalogInputPins(int par_0, int par_1, int par_2, int par_3, int par_4, int par_5){
        String commandStr="~a"
                +" "+par_0
                +" "+par_1
                +" "+par_2
                +" "+par_3
                +" "+par_4
                +" "+par_5
                ;
        return sendCommand(commandStr, STR_ANALOG_IPUTS_SET);
    }
    //======================================
    //======================================
    public void sniffBuffer(boolean parDetailedChar){
        try {
            int avail=inStream.available();
            if(avail>0){
                System.out.print("\r\n\tprtsniff:[");

                while( avail>0){

                    int readTmpByte=inStream.read();
                    char inChar=(char) readTmpByte;
                    if (parDetailedChar) {
                        System.out.println("\r\n\t\t\tavl:"+inStream.available());
                        System.out.println("t\t\tint:"+readTmpByte+" char:"+inChar);
                    }else{
                        System.out.print(inChar);
                    }
                    avail=inStream.available();
                    //clearIncomingBuffer();
                }
                System.out.print("\tprtsniff:[");
            }
        } catch (Exception e) {
            log(1,"\tsniff exception:"+e);
        }
    }

    //======================================
    //======================================
    public boolean getIntArrayFromSerial_1D(int [] parResultArray, int parBytesPerValue, int parNumberOfElements, long parTimeoutMillis){

        try {
            for (int i = 0; i < parNumberOfElements; i++) {
                int multiplyier=1;
                int receivedValue=0;
                for (int j = 0; j < parBytesPerValue; j++) {
                    while (true){
                        if(inStream.available()>0){
                            int readTmpByte=inStream.read();
                            receivedValue+= readTmpByte*multiplyier;
                            //System.out.println("\t\t\tbyte"+readTmpByte+" mult="+multiplyier+" val="+receivedValue);
                            multiplyier*= 0xFF; //shift

                            break;
                        }
                    }
                }
                parResultArray[i]=receivedValue;
                //System.out.println("\ti="+i+" val="+receivedValue);

            }
            return true;
            //clearIncomingBuffer();
        } catch (Exception e) {
            log(1,"\tsniff exception:"+e);
            return false;
        }
    }

    //======================================
    //======================================
    public boolean getIntArrayFromSerial_2D(int [][] parResultArray, int parBytesPerValue, int parNumberOfRecords, int parNumberOfElements, long parTimeoutMillis){

        try {
            for (int recordNo = 0;  recordNo< parNumberOfRecords; recordNo++) {
                for (int elementNo = 0; elementNo < parNumberOfElements; elementNo++) {
                    int multiplyier=1;
                    int receivedValue=0;
                    for (int j = 0; j < parBytesPerValue; j++) {
                        while (true){
                            if(inStream.available()>0){
                                int readTmpByte=Byte.toUnsignedInt((byte)inStream.read());
                                //byte is always signed value in Java
                                //if (readTmpByte<0) {
                                //    readTmpByte=256-readTmpByte;
                                //}
                                receivedValue+= readTmpByte*multiplyier;
                                //System.out.println("\t\t\tbyte_"+j+"="+readTmpByte+" mult="+multiplyier+" val="+receivedValue);
                                multiplyier*= 0x100; //shift
                                break;
                            }//\if(inStream.available()>0)...
                        }//\while...
                    }//\for (int j = 0; j < parBytesPerValue; j++)...
                    //System.out.println("\t\tresult["+recordNo+"]["+elementNo+"]="+receivedValue);
                    parResultArray[recordNo][elementNo]=receivedValue;
                }//\for (int elementNo = 0; elementNo < 10; elementNo++)...
            }//\for (int recordNo = 0; i < parNumberOfRecords; i++)...
            //System.out.println("\ti="+i+" val="+receivedValue);


            return true;
            //clearIncomingBuffer();
        } catch (Exception e) {
            log(1,"\tsniff exception:"+e);
            return false;
        }
    }
    //======================================
    //======================================
    public boolean doMeasurement(byte [] parSequence, long parIntervalMicros, int[]parResultArray_digital, int[][]parResultArray_analog, int[][]parResultArray_timings){
        int stepsNumber=parSequence.length;

        long dur=(long)1.1*parIntervalMicros*stepsNumber/1000+1000;


        log(2,"New sequence, lsteps No="+stepsNumber+", step micros="+parIntervalMicros);
        clearIncomingBuffer();
        String commandStr="~m"
                +" "+stepsNumber
                +" "+parIntervalMicros

                ;
        if(!sendCommand(commandStr, STR_DO_MEASUREMENT_CONFIRM)){
            log(1,"No responce to measurement command");
            return false;
        }

        if(! waitForString(responceTimeout, ">")){
            log(2,"Measurement not started");
            return false;
        }

        try {
            for (int i = 0; i < stepsNumber; i++) {
                log(3,"Send:"+parSequence[i]);
                //myComPort.writeBytes(parSequence, 1, i);

                byte tmpByte[] = new byte[1];
                tmpByte[0]=parSequence[i];
                log(4,"Send: 0b"+Integer.toBinaryString(tmpByte[0])+"("+(char)tmpByte[0]+" / "+tmpByte[0]+")");
                outStream.write(tmpByte,0,1);
                outStream.flush();

                sniffBuffer(false);
            }
        } catch (Exception e) {
            log(1,"\texception when sending sequence "+e);
            return false;
        }

        if(!waitForOK(20)){
            log(1,"ERROR: no confirmation of sequence (OK) received");
            return false;
        }
        log(2,"Waiting for measurement (BLOCKING)...");
        if(!waitForString(dur, STR_MEASUREMENT_DATA_READY)){
            log(1,"ERROR: no measurement data received");
//blockingDelay(1000);
//sniffBuffer(false);

            return false;
        }
        log(2,"\t...measurement completed, get data...");

        log(3,"Waiting for digital data...");
        if(!waitForString(20, STR_DIGITAL_DATA)){
            log(1,"ERROR: no digital data received");
            return false;
        }
        log(3,"\t...digital data received");


        log(1, "digit OK");
        if(!getIntArrayFromSerial_1D(parResultArray_digital, 1, stepsNumber, 50)){
            log(1,"ERROR: no digital array received");
            return false;
        }

        log(3,"Waiting for analog data...");
        if(!waitForString(20, STR_ANALOG_DATA)){
            log(1,"ERROR: no analog data received");
            return false;
        }
        log(3,"\t...analog data received");
        log(1, "analog OK");
        if(!getIntArrayFromSerial_2D(parResultArray_timings, 4, stepsNumber, ANALOG_INPUTS_NUMBER,50)){
            log(1,"ERROR: no analog timings array received");
            return false;
        }

        if(!getIntArrayFromSerial_2D(parResultArray_analog, 2, stepsNumber, ANALOG_INPUTS_NUMBER, 50)){
            log(1,"ERROR: no analog values array received");
            return false;
        }

        if (! waitForCommandMode(10)) {
            return false;
        }
        clearIncomingBuffer();
        log(2,"=====MEASUREMENT COMPLETED ======");
        return true;
    }

    //======================================
    public boolean isInCommandMode(){
        log(3,"\t\tCheck if in COMMAND mode...");
        if(!sendCommand("~s", STR_STATUS)){
            return false;
        }
        if(!waitForString(50, STR_COMMAND_MODE)){
            clearIncomingBuffer();
            return false;
        }
        log(3,"\t\tcommand mode is active");
        waitForOK(5);
        return true;
    }
}//\public class ArduinoCommunicator ...
