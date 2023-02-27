#include "strings.h"
#include <mbDebug.h>
#include <MBCommandParser_v3.h>
#include <TimerOne.h>


#define SEQUENCES_BUFFERS_SIZE 15

#define OUTPUT_PINS_ARRAY_LENGHT 6
#define DIGITAL_INPUT_PINS_ARRAY_LENGHT 6
#define ANALOG_INPUT_PINS_ARRAY_LENGHT 6
#define PIN_NOT_USED 0xFF

#define BINARY_DATA_TIMEOUT_MILLIS 30000
#define STRING_BUFFER_SIZE 250
#define ECHO_STRING_ENDING_CHAR '\n'
#define ECHO_MODE_ENDING_CHAR '+'
#define ECHO_MODE_SEQUENCE_LENGHT 3

MBCommandParser_v3 commandParser(&Serial, &Serial);

enum device_states_t{
  st_command=0
  ,st_echo=1
  ,st_measurement=2
  ,st_dataReady=4
  ,st_infiniteSequence=5} deviceState;


uint8_t echoModeSequenceCandidateLenght=0;

uint8_t inputStringBuffer[STRING_BUFFER_SIZE];
uint8_t nextReceivedByteIndex=0;

//IDs of the pins that are used, 14-2 pins are available, pin 0 and 1 (serial TX and RX) are newer used 
//but just 8 of output pins are available for using (to store data in 8-bit value
//zero value means 0 pin is used

uint8_t outputPins[OUTPUT_PINS_ARRAY_LENGHT]; 

uint8_t inputPins_digital[DIGITAL_INPUT_PINS_ARRAY_LENGHT];
uint8_t inputPins_analog[ANALOG_INPUT_PINS_ARRAY_LENGHT];
  
struct analogReading_t{
  uint32_t readingTime;
  uint16_t value;
};
uint8_t outputSequence[SEQUENCES_BUFFERS_SIZE];
uint8_t digitalReadings[SEQUENCES_BUFFERS_SIZE];
analogReading_t analogReadings[SEQUENCES_BUFFERS_SIZE][ANALOG_INPUT_PINS_ARRAY_LENGHT];

uint16_t sequenceLenght;
uint32_t sequenceStepDuration;

int16_t sequenceStepsPassed;
uint16_t sequenceStepsSent;
/*
uint16_t analogReadingsBuffer[ANALOG_READINGS_BUFFER_SIZE];
uint8_t digitalReadingsBuffer=[DIGITAL_READINGS_BUFFER_SIZE];
uint8_t outputSequencesBuffer[OUTPUT_SEQUENCES_BUFFER_SIZE];
*/
//=========================================
//
//=========================================
void sendOK(){
  Serial.println(STR_OK);
}
//=========================================
void sendReady(){
  Serial.println(STR_READY);
}
//=========================================
void switchToCommandMode(){
  deviceState=st_command;
  Serial.println(STR_COMMAND_MODE);
  sendOK();
}
//=========================================
//
//=========================================
void dummyFillData(){
  for (size_t seqStep = 0; seqStep < sequenceLenght; seqStep++)
  {
      digitalReadings[seqStep]=30+seqStep;
      for (size_t inpNo = 0; inpNo < ANALOG_INPUT_PINS_ARRAY_LENGHT; inpNo++)
      {
        analogReadings[seqStep][inpNo].readingTime=micros();
        analogReadings[seqStep][inpNo].value=(uint16_t)100*seqStep+(uint16_t)10*inpNo;
        //analogReadings[seqStep][inpNo].value=(uint16_t)100*seqStep;
        //analogReadings[i].value=i;
      }
      
      
  }
}
//=========================================
//
//=========================================
void sendData(){
  Serial.print(STR_DIGITAL_DATA);
  for (size_t i = 0; i < sequenceLenght; i++)
  {
    Serial.write(digitalReadings[i]);
  }
  sendOK();
  Serial.print(STR_ANALOG_DATA);
  for (size_t seqStep = 0; seqStep < sequenceLenght; seqStep++)
  {
    for (size_t sensNo = 0; sensNo < ANALOG_INPUT_PINS_ARRAY_LENGHT; sensNo++)
    {
      for (size_t b = 0; b < 4; b++)
      {
        Serial.write((byte)(analogReadings[seqStep][sensNo].readingTime>>(b*8))&0xFF);  
      }
    }
  }
  for (size_t seqStep = 0; seqStep < sequenceLenght; seqStep++)
  {
    for (size_t i = 0; i < ANALOG_INPUT_PINS_ARRAY_LENGHT; i++)
    {
      Serial.write(lowByte(analogReadings[seqStep][i].value));  
      Serial.write(highByte(analogReadings[seqStep][i].value)); 
    }
  }
  sendOK();
  deviceState=st_command;
  switchToCommandMode();
  //reportStatus();
}
void setOutputs(int16_t parStep){
  //Serial.print(" STEP:");
  //Serial.println(parStep);      

  for (size_t i = 0; i < OUTPUT_PINS_ARRAY_LENGHT; i++)
  {
    if (outputPins[i]!=PIN_NOT_USED)
    {
      digitalWrite(outputPins[i], bitRead(outputSequence[parStep], i));
      
      //Serial.print(" out[");
      //Serial.print(i);      
      //Serial.print("]=");
      //Serial.print(bitRead(outputSequence[parStep], i));    
  
    }
  }
  //Serial.print("\r\n"); 
}
//=========================================
//
//=========================================
void stopSequence(){
  //===last step reached, end sequence
    Timer1.detachInterrupt();
    for (size_t i = 0; i < OUTPUT_PINS_ARRAY_LENGHT; i++)
    {
        pinMode(outputPins[i], INPUT);
    }
}
//=========================================
//
//=========================================
void measurementCallback(){
  /*
  Serial.print(F("\r\n---\t"));
  Serial.println(micros());
  Serial.print(F("\t\tseq["));
  Serial.print(sequenceStepsPassed);
  Serial.print(F("]:\t"));
  */
  if (sequenceStepsPassed==-1)
  {
    sequenceStepsPassed=0;
    setOutputs(0);  //set the first step of sequence
    
    //Serial.println(F("\r\nSKIP"));
    return;
  }

  if (deviceState!=st_infiniteSequence)
  {
    uint8_t result=0;
    for (size_t i = 0; i < DIGITAL_INPUT_PINS_ARRAY_LENGHT; i++)
    {
      //Serial.print(F(";\t["));
      if(inputPins_digital[i]!=PIN_NOT_USED){
        if(digitalRead(inputPins_digital[i])==HIGH){
          bitSet(result, i);
        }
        /*
        Serial.print(i);
        Serial.print(F("]="));
        Serial.print(bitRead(result,i));
        */
      }
    }
    //Serial.print(F("\r\n\tres="));
    //Serial.println(result, BIN);
    digitalReadings[sequenceStepsPassed]=result;

    for (size_t i = 0; i < ANALOG_INPUT_PINS_ARRAY_LENGHT; i++)
    {
      if(inputPins_analog[i]!= PIN_NOT_USED){
        analogReadings[sequenceStepsPassed][i].readingTime=micros();
        analogReadings[sequenceStepsPassed][i].value=analogRead(i);
        //Serial.print(F("an["+sequenceStepsPassed+"]["+i+"]="+analogReadings[sequenceStepsPassed][i].value));
      } 
    }
  }
  sequenceStepsPassed++;
  if (sequenceStepsPassed>=sequenceLenght )
  {
	  if (deviceState==st_measurement)
	  {
		  stopSequence();
		  //!!!!! send the rest of the sequence
		  deviceState=st_dataReady;
		  Serial.println(STR_MEASUREMENT_DATA_READY);
		  return;
	  }else if (deviceState==st_infiniteSequence)
	  {
		  sequenceStepsPassed=0;
	  }
  }
  setOutputs(sequenceStepsPassed);
}
//=========================================
//
//=========================================
void sequenceStart(){
  //Serial.println("---MEASURE---");
  
  //Serial.print(F("\r\nOUTPUTS:"));  
  for (size_t i = 0; i < OUTPUT_PINS_ARRAY_LENGHT; i++)
  {
    if (outputPins[i]!=0)
    {
      pinMode(outputPins[i], OUTPUT);

      //Serial.print(F("\t[")); 
      //Serial.print(i);  
      //Serial.print(F("]="));  
      //Serial.print(outputPins[i]);  
    }
  }
  //Serial.print(F("\r\nINPUTS::"));    
  for (size_t i = 0; i < DIGITAL_INPUT_PINS_ARRAY_LENGHT; i++)
  {
    if (inputPins_digital[i]!=0)
    {
      pinMode(inputPins_digital[i], INPUT);

      //Serial.print(F("\t[")); 
      //Serial.print(i);  
      //Serial.print(F("]="));  
      //Serial.print(inputPins_digital[i]); 
    }
  }
  
  sequenceStepsPassed=-1;

  Timer1.initialize(sequenceStepDuration);
  Timer1.attachInterrupt(measurementCallback);

}
//=========================================
//
//=========================================
void cmd_DigitalRead(){
  uint8_t digitalPin= commandParser.getParameterInt(0);
  sendOK();
  char readedValue=(digitalRead(digitalPin)==0)?'L':'H';
  
  _debug2("Digital read for pin ", digitalPin, "; value=", readedValue);
  sendReady();
  
}


//=========================================
//not used now!
//=========================================
void cmd_AnalogRead(){
  sendOK();
  uint8_t analogPin= commandParser.getParameterInt(0);
  uint16_t readedValue=analogRead(analogPin);
  _debug2("Analog read for pin ", analogPin, "value", readedValue);
  sendReady();
}

//=========================================
//
//=========================================
void cmd_EchoOn(){
  //some ugly way to skip the final carriage return, should be discussed later
  //_message("Skip <CR>...");
 /*
  while(true){
    if (Serial.available()>0)
    {
      uint8_t curByte=Serial.read();
      _debugPrintChar(curByte);
      if (curByte==0x0A)  {
        break;
      }
    }
  };
  */
  nextReceivedByteIndex=0;
  sendOK();
  _message(STR_ECHO_ON);
  
  deviceState=st_echo;
    sendReady();

}
//===============================
//===============================
void echoOff(){
  nextReceivedByteIndex=0;
  _message(STR_ECHO_OFF);
  deviceState=st_command;
  sendReady();
}

//===============================
void cmd_setUsedOutputs(){
  for (size_t i = 0; i < OUTPUT_PINS_ARRAY_LENGHT; i++)
  {
    uint8_t curOutPin=commandParser.getParameterInt(i);
    outputPins[i]=curOutPin;
    //pinMode(curOutPin, OUTPUT);   !!!!
  }
  
  Serial.println(STR_OUTPUTS_SET);
  sendOK();
}
//===============================
void cmd_setUsedDigitalInputs(){
  for (size_t i = 0; i < DIGITAL_INPUT_PINS_ARRAY_LENGHT; i++)
  {
    uint8_t curPin=commandParser.getParameterInt(i);
    inputPins_digital[i]=curPin;
    //pinMode(curOutPin, OUTPUT);   !!!!
  }
  Serial.println(STR_DIGITAL_IPUTS_SET);
  sendOK();
  
}
//===============================
//===============================
void cmd_setAnalogInputs(){

  for (size_t i = 0; i < ANALOG_INPUT_PINS_ARRAY_LENGHT; i++)
  {
    uint8_t curPin=commandParser.getParameterInt(i);
    inputPins_analog[i]=curPin;
    //pinMode(curOutPin, OUTPUT);   !!!!
  }
  Serial.println(STR_ANALOG_IPUTS_SET);
  sendOK();
}
//===============================
//===============================
void reportError(){
  Serial.println(STR_ERROR);
}
//===============================
//===============================
void reportStatus(){
  Serial.println(STR_STATUS);
  sendOK();
  Serial.print(F("Outp.:"));
  for (size_t i = 0; i < ANALOG_INPUT_PINS_ARRAY_LENGHT; i++)
  {
    Serial.write(' ');
    Serial.print(outputPins[i]);
    //pinMode(curOutPin, OUTPUT);   !!!!
  }
  Serial.print(F("\r\nDig.inp.:"));
  for (size_t i = 0; i < DIGITAL_INPUT_PINS_ARRAY_LENGHT; i++)
  {
    Serial.write(' ');
    Serial.print(inputPins_digital[i]);
    //pinMode(curOutPin, OUTPUT);   !!!!
  }
  Serial.print(F("\r\nAn.inp.:"));
  for (size_t i = 0; i < ANALOG_INPUT_PINS_ARRAY_LENGHT; i++)
  {
    Serial.write(' ');
    Serial.print(inputPins_analog[i]);
    //pinMode(curOutPin, OUTPUT);   !!!!
  }
  Serial.print(F("\r\nSTATE="));
  Serial.println(deviceState);
  switch (deviceState)
  {
    case st_command:
      Serial.println(STR_COMMAND_MODE);
    break;
    case st_dataReady:
      Serial.println(STR_DATAREADY_MODE);
    break;
    case st_infiniteSequence:
      Serial.println(STR_CONTINOUSOUT_MODE);
    break;
    case st_measurement:
      Serial.println(STR_MEASUREMENT_MODE);
    break;
    case st_echo:
      Serial.println(STR_ECHO_MODE);
    break;
  
    default:
      Serial.println(F("STATUS UNKNOWN"));
      break;
  }
  
  sendReady();

}
//===============================
//===============================
void cmd_startMeasurement(){
  sequenceLenght=commandParser.getParameterInt(0);
  sequenceStepDuration=commandParser.getParameterInt(1);
  if (sequenceLenght>SEQUENCES_BUFFERS_SIZE)
  {
    reportError();
    _message(F("\r\nERROR:TOO LONG"));
    switchToCommandMode();
    reportStatus();
    return;
  }
  
  Serial.println(STR_DO_MEASUREMENT_CONFIRM);

  sendOK();


  while (Serial.available()>0){
    Serial.read();
  }

  Serial.write('>');  

  memset(outputSequence, 0, sizeof(outputSequence));
  memset(analogReadings, 0, sizeof(analogReadings));
  memset(digitalReadings, 0, sizeof(digitalReadings));
  
  sequenceStepsSent=0;
  

  uint32_t whenStarted=millis();
  for (size_t i = 0; i < sequenceLenght; i++)
  {
  
    while (true)
    {
      
      if(millis()-whenStarted>BINARY_DATA_TIMEOUT_MILLIS){
        deviceState=st_command;
        reportError();
        _message(F("TIMEOUT"));
        switchToCommandMode();
        reportStatus();
        return;
      }
      if (Serial.available()>0)
      {
        outputSequence[i]=(uint8_t)Serial.read();
        whenStarted=millis();
        //Serial.write(outputSequence[i]);
        break;
      }else{

      }
    }
  }

  sendOK();
  sequenceStart();
  deviceState=st_measurement;

}
//=========================================
//=========================================
void cmd_startInfiniteSequence(){
  sequenceLenght=commandParser.getParameterInt(0);
  sequenceStepDuration=commandParser.getParameterInt(1);
  if (sequenceLenght>SEQUENCES_BUFFERS_SIZE)
  {
    reportError();
    _message(F("\r\nERROR:TOO LONG"));
    switchToCommandMode();
    reportStatus();
    return;
  }
  
  Serial.println(STR_INFINITE_SEQUENCE);

  sendOK();


  while (Serial.available()>0){
    Serial.read();
  }

  Serial.write('>');  

  memset(outputSequence, 0, sizeof(outputSequence));
  

  uint32_t whenStarted=millis();
  for (size_t i = 0; i < sequenceLenght; i++)
  {
  
    while (true)
    {
      
      if(millis()-whenStarted>BINARY_DATA_TIMEOUT_MILLIS){
        deviceState=st_command;
        reportError();
        _message(F("TIMEOUT"));
        switchToCommandMode();
        reportStatus();
        return;
      }
      if (Serial.available()>0)
      {
        outputSequence[i]=(uint8_t)Serial.read();
        whenStarted=millis();
        //Serial.write(outputSequence[i]);
        break;
      }else{

      }
    }
  }

  sendOK();
  sequenceStart();
  deviceState=st_infiniteSequence;

}
//=========================================
//=========================================
void cmd_stopSequence(){
  Serial.println(STR_STOP_SEQUENCE);
  stopSequence();
  sendOK();
  deviceState=st_command;
}
//=========================================
//
//=========================================
void cmd_showHelp(){
  sendOK();
  commandParser.printCommandList();
  sendReady();
}
//===============================
//
//===============================
void setup(){
  Serial.begin(115200);
  //Serial.begin(57600);
  _message(__TIME__);
  //reportFreeRAM(Serial);
    Serial.println(STR_DEV_RESTARTED);
	
	for (int i = 0; i < OUTPUT_PINS_ARRAY_LENGHT ; i++)
	{
		outputPins[i]=PIN_NOT_USED;
	}
	for (int i = 0; i < ANALOG_INPUT_PINS_ARRAY_LENGHT ; i++)
	{
		inputPins_analog[i]=PIN_NOT_USED;
	}
	for (int i = 0; i < DIGITAL_INPUT_PINS_ARRAY_LENGHT ; i++)
	{
		inputPins_digital[i]=PIN_NOT_USED;
	}
  
  commandParser.addCommand('?', F("print all commands"), cmd_showHelp, 0);
  //commandParser.addCommand('m', F("set [M]ode of the digital pin I/O"), 2);
  //commandParser.addCommand('w', F("[W]rite L/H state to the digital pin"), cmd_DigitalWrite, 2);
  commandParser.addCommand('r', F("[R]ead state of the digital pin"), cmd_DigitalRead, 1);
  //commandParser.addCommand('a', F("read value (0..1024) from analog pin"), cmd_AnalogRead, 1);
  commandParser.addCommand('e', F("switch to echo mode (until \\n received)"), cmd_EchoOn, 0);

  commandParser.addCommand('o', F("set [O]utput (6 digits)"), cmd_setUsedOutputs, OUTPUT_PINS_ARRAY_LENGHT);
  commandParser.addCommand('i', F("set [I]nput digital pins (6 digits)"), cmd_setUsedDigitalInputs, DIGITAL_INPUT_PINS_ARRAY_LENGHT);
  commandParser.addCommand('a', F("set [A]nalog input pins (6 digits)"), cmd_setAnalogInputs, ANALOG_INPUT_PINS_ARRAY_LENGHT);
  commandParser.addCommand('m', F("start [M]easurement (num, micros)"),  cmd_startMeasurement, 2);
  commandParser.addCommand('s', F("report [S]tatus"),  reportStatus, 0);
  commandParser.addCommand('f', F("start in[F]inite sequence"),  cmd_startInfiniteSequence, 2);
  commandParser.addCommand('p', F("sto[P] sequence"),  cmd_stopSequence, 0);

  
  switchToCommandMode();
  sendReady();
  
  
}

//===============================
//===============================
void processEchoReading(){
  while (Serial.available()>0)
    {
      uint8_t curByte=Serial.read();
      //_debugPrintChar(curByte);
      if (nextReceivedByteIndex>=STRING_BUFFER_SIZE){
        reportError();
        _message(F("/r/nError: buffer overflow"));
        echoOff();
        switchToCommandMode();
        return;
      }
      //_debug("index", nextReceivedByteIndex);
      inputStringBuffer[nextReceivedByteIndex]=curByte;
      if (curByte==ECHO_STRING_ENDING_CHAR)
      {
        //_message("String received:[");
        for (int i = 0; i <= nextReceivedByteIndex ; i++)
        {
          Serial.write(inputStringBuffer[i]);
        }
        //_message("]");
            memset(inputStringBuffer, 0, STRING_BUFFER_SIZE); 
            nextReceivedByteIndex=0;
            return;
      } //\if (curByte==ECHO_STRING_ENDING_CHAR)...

          if(curByte==ECHO_MODE_ENDING_CHAR){
            echoModeSequenceCandidateLenght++;
            if(echoModeSequenceCandidateLenght>=(ECHO_MODE_SEQUENCE_LENGHT-1)){
                _message(F("Termination sequence received"));
                echoOff();
                return;
            }
          }else{
            echoModeSequenceCandidateLenght=0;
          }
      nextReceivedByteIndex++;
    }
    
}

void loop(){
static uint32_t prevMillis=millis();
  switch(deviceState){
    case st_command:
      commandParser.loop();
    break;
    case st_echo:
      processEchoReading();
    break;
    case st_measurement:
		commandParser.loop();
    break;
    case st_dataReady:
    //dummyFillData();
		commandParser.loop();
		sendData();
    break;
    case st_infiniteSequence:
		commandParser.loop();
		if (millis()-prevMillis>500)
		{
			prevMillis=millis();
		}
      
    break;
    default:
      _message(F("\t\tERROR\t\tUNKNOW MODE!"));
  }
 } 
