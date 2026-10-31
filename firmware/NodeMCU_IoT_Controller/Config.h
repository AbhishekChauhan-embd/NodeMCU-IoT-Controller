
#ifndef CONFIG_H
#define CONFIG_H

#include<ESP8266WiFi.h>
#include <Arduino.h>
#include <PubSubClient.h>
#include <WiFiClientSecureBearSSL.h>

#include "credentials.h"
//Comment out the above include if no credentials.h in project and give the credentials manually in the below.
   
/*-------------------Wi-Fi configuration-----------------*/
/*
WiFi credentials are included from the credentials.h and it is not present in GIT for security resions.
used below code for credentials
*/

//const char *WIFI_SSID = "Your WiFi SSID";
//const char *WIFI_PASSWORD = "Your WiFi password";

/*--------------------------end--------------------------*/

/*--------------EMQX Serverless configuration-------------*/

const char* MQTT_BROKER =   "j6fffe6b.ala.asia-southeast1.emqxsl.com";
const int MQTT_PORT     =   8883;


//EMQX Serverless credentials are included from the credentials.h
//crendtials.h is not present in GIT for security resions

//const char* MQTT_USER   =   "MQTT_USER";
//const char* MQTT_PASWD  =   "MQTT_PASSWORD";

/*--------------------------end--------------------------*/

/*----------------------MQTT Topics----------------------*/
const char* TOPIC_BUILTIN_LED_COMMAND = "abhishek/nodemcu-01/command/builtinLed";
const char* TOPIC_STATUS  = "abhishek/nodemcu-01/status";
const char* TOPIC_BUILTIN_LED_STATUS  = "abhishek/nodemcu-01/status/builtinLed";
/*--------------------------end--------------------------*/

#define PUBLISH_INTERVAL_MS = 10000

BearSSL::WiFiClientSecure secureClient;
PubSubClient mqttClient(secureClient);

unsigned long previousPublishTime = 0;



/*---------------TASK Function Declaration---------------*/

void IOT_AP_ini();
void IOT_AP_500ms();

/*--------------------------end--------------------------*/

/*------------------Function Declaration-----------------*/
void connectWiFi();
void configureMQTT();
void connectMQTT();
void mqttCallback(char* topic, byte* payload, unsigned int length);
void builtinLedHendler(String message);
void toggleLed(int led);
/*--------------------------end--------------------------*/

#define LED_DEVICE_READY D0
#define ON 0x0
#define OFF 0x1

#endif