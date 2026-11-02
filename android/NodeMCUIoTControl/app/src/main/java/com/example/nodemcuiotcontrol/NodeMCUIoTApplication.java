package com.example.nodemcuiotcontrol;

import android.app.Application;

public class NodeMCUIoTApplication extends Application {

    private MqttManager mqttManager;

    @Override
    public void onCreate() {
        super.onCreate();

        mqttManager = new MqttManager();
    }

    public MqttManager getMqttManager() {
        return mqttManager;
    }

}
