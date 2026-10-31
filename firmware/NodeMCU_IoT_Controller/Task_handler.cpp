#include "wl_definitions.h"
#include "WiFiClient.h"
#include "HardwareSerial.h"


#include"config.h"



/*--------------------Project globals-------------------*/
unsigned long currentMillis = 0;
unsigned long previousMillis1ms = 0;
unsigned long previousMillis10ms = 0;
unsigned long previousMillis100ms = 0;
unsigned long previousMillis500ms = 0;
unsigned long previousMillis1000ms = 0;
/*--------------------------end--------------------------*/

/*----------------------Externs--------------------------*/

/*--------------------------end--------------------------*/

bool flgDeviceReadyLedStatus = 0;

void IOT_AP_ini()
{
  // Start Serial monitor
  Serial.begin(115200);
  delay(1000);

  
  Serial.println("");
  Serial.println("===================================================");
  Serial.print  ("=             NodeMCU EMQX Cloud MQTT             =");
  Serial.println("===================================================");

  pinMode(LED_DEVICE_READY, OUTPUT);
  pinMode(LED_BUILTIN, OUTPUT);
  Serial.println("I/O PIN configured.");

  digitalWrite(LED_DEVICE_READY, OFF);
  connectWiFi();
  configureMQTT();

}

void IOT_AP_1ms(){
   
}

void IOT_AP_10ms()
{

   // MQTT processing
    mqttClient.loop();
    
  //Serial.println("10ms tick: System is running smoothly!");
  
  // if(WiFi.status() != WL_CONNECTED)
  // {
  //     connectWiFi();
  // }

  // if(mqttClient.connected())
  // {
  //   Serial.println("MQTT client connected.");
  // }else(
  //   configureMQTT();
  // )
  
}


void IOT_AP_500ms()
{
  //Serial.println("500ms tick: System is running smoothly!");
  //toggleLed(LED_DEVICE_READY);
}

void IOT_AP_1000ms()
{
    if(mqttClient.connected())
    {
      Serial.println("MQTT client connected.");
    }else{
      configureMQTT();
    }
}

/*---------------------------------------------------------------
API : connectWiFi
Functoin: This API is responsible to connect NodeMCU to the WiFi
---------------------------------------------------------------*/
void connectWiFi()
{
  Serial.println();
  Serial.print("Connecting to ");
  Serial.println();

  // Wait until connection is established
  WiFi.begin(WIFI_SSID,WIFI_PASSWORD);

  // Wait until connection is established
  while (WiFi.status() != WL_CONNECTED) {
    digitalWrite(LED_BUILTIN, ON);
    delay(200);
    digitalWrite(LED_BUILTIN, OFF);
    delay(200);
    Serial.print(".");
  }

  if(WiFi.status() == WL_CONNECTED)
  {
    // Once connected, print success message and local IP address
    Serial.println("");
    Serial.println("WiFi connected successfully!");
    digitalWrite(LED_BUILTIN, ON);
    delay(100);
    Serial.print("NodeMCU IP address: ");
    Serial.println(WiFi.localIP()); // This is your NodeMCU's local IP on your network
    Serial.print("Signal Strength : ");
    Serial.print(WiFi.RSSI());
    Serial.println(" dBm");
  }

}

/*----------------------------------------------------------------------
API : configureMQTT
Functoin: This API is responsible to configure MQTT broker
----------------------------------------------------------------------*/
void configureMQTT()
{

  Serial.println("MQTT Configuiration started...");

  secureClient.setInsecure();

  mqttClient.setServer(MQTT_BROKER,MQTT_PORT);

  mqttClient.setCallback(mqttCallback);

  mqttClient.setKeepAlive(60);
  mqttClient.setBufferSize(512);

  connectMQTT();
}

/*----------------------------------------------------------------------
API : connectMQTT
Functoin: This API is responsible to connect NodeMCU to the MQTT broker
----------------------------------------------------------------------*/
void connectMQTT()
{
  while(!mqttClient.connected())
  {
    //Serial.println();
    Serial.print("Connecting to EMQX...");

    String clientID = "NodeMCU-" + String(ESP.getChipId(),HEX);

    Serial.print("Client ID : ");
    Serial.println(clientID);

    bool connected = 
      mqttClient.connect(clientID.c_str(), 
                                      MQTT_USER, 
                                      MQTT_PASWD,
                                      //last will Topic
                                      TOPIC_STATUS,
                                      //QoS
                                      1,
                                      //Retain
                                      true,
                                      //Last will Message
                                      "offline");

    if(connected)
    {
      Serial.println("Connected to EMQX successfully!");

      //Publish device status.
      mqttClient.publish(TOPIC_STATUS, "online",true);

      //Subscribe to LED command.
      mqttClient.subscribe(TOPIC_BUILTIN_LED_COMMAND,1);

      Serial.print("Subscribed to: ");
      Serial.println(TOPIC_BUILTIN_LED_COMMAND);
    }
    else{
      Serial.print("MQTT connection failed. State = ");
      Serial.println(mqttClient.state());

      Serial.println("Retrying...");
      delay(5000);
    }
  }
}

/*----------------------MQTT message callback------------------------*/

void mqttCallback(char* topic, byte* payload, unsigned int length)
{
  Serial.println();
  Serial.print("MQTT Message received");
  Serial.println();

  Serial.print("Topic : ");
  Serial.println(topic);
  Serial.print("Message : ");

  String message = "";

  for(unsigned int i = 0; i<length;i++)
  {
    message += (char)payload[i];
  }

  Serial.println(message);

  message.trim();
  message.toUpperCase();

  if(String(topic) == TOPIC_BUILTIN_LED_COMMAND)
  {
    builtinLedHendler(message);
  }
}

/*----------------------BUILTIN LED Handler------------------------*/
void builtinLedHendler(String message)
{
  bool validCommand = false;
  const char* ledStatus = nullptr;
  
  if(message == "ON")
  {
    digitalWrite(LED_DEVICE_READY, ON);
    Serial.print("LED STATUS : ");
    Serial.println(message);

    ledStatus = "ON";
    validCommand = true;
  }
  else if(message == "OFF")
  {
    digitalWrite(LED_DEVICE_READY, OFF);
    Serial.print("LED STATUS: ");
    Serial.println(message);

    ledStatus = "OFF";
    validCommand = true;
  }

  if(validCommand)
  {
    Serial.print("LED STATUS : ");
    Serial.println(ledStatus);

    if(mqttClient.connected())
    {
      bool published = mqttClient.publish(
        TOPIC_BUILTIN_LED_STATUS,
        ledStatus,
        true
      );

      if(published)
      {
        Serial.println("LED status feedback published successfully.");
      }
      else
      {
        Serial.println("LED status feedback publishing failed.");
      }
    }
  }
  else
  {
    Serial.println("Invalid LED command received.");
  }
  
}


void toggleLed(int led)
{
  //ledStatus?digitalWrite(led, OFF):digitalWrite(led, ON);
  if(led == LED_DEVICE_READY)
  {
    
    if(flgDeviceReadyLedStatus == 1){
      flgDeviceReadyLedStatus = 0;
      digitalWrite(led, OFF);
      Serial.println("LED_DEVICE_READY : OFF");
    }else{
      flgDeviceReadyLedStatus = 1;
      digitalWrite(led, ON);
      Serial.println("LED_DEVICE_READY : ON");
    }
  }
}
