/*
This is the program to connect the NodeMCU to the Wi-Fi
*/




/*---------Project globals (Extern Declarations)---------*/
extern unsigned long currentMillis;
extern unsigned long previousMillis1ms;
extern unsigned long previousMillis10ms;
extern unsigned long previousMillis100ms;
extern unsigned long previousMillis500ms;
extern unsigned long previousMillis1000ms;
/*--------------------------end--------------------------*/
/*--------------------Extern functions-------------------*/
extern void IOT_AP_ini();
extern void IOT_AP_1ms();
extern void IOT_AP_10ms();
extern void IOT_AP_500ms();
extern void IOT_AP_1000ms();
/*--------------------------end--------------------------*/


void setup() {

  IOT_AP_ini();

}

void loop() {

    currentMillis = millis();

    if((currentMillis-previousMillis10ms) >= 1){
      previousMillis1ms = currentMillis;
      IOT_AP_1ms();
      //Serial.println("1ms tick: System is running smoothly!");
    }

    if((currentMillis-previousMillis10ms) >= 10){
      previousMillis10ms = currentMillis;
      IOT_AP_10ms();
      //Serial.println("10ms tick: System is running smoothly!");
    }

    if((currentMillis-previousMillis500ms) >= 500){
      previousMillis500ms = currentMillis;
      IOT_AP_500ms();
      //Serial.println("500ms tick: System is running smoothly!");
    }

    if (currentMillis - previousMillis1000ms >= 1000) {
     previousMillis1000ms = currentMillis;
     //Serial.println("1-second tick: System is running smoothly!");
     IOT_AP_1000ms();
    }
}
