package com.example.nodemcuiotcontrol;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.content.Intent;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    // UI components
    private TextView txtBrokerStatus;
    private TextView txtDeviceStatus;
    private TextView txtLedStatus;

    private SwitchCompat switchLed;
    private boolean isUpdatingLedSwitch = false;
    private Button btnBrokerSettings;

    // MQTT manager
    private MqttManager mqttManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        btnBrokerSettings = findViewById(R.id.btnBrokerSettings);

        initializeViews();

        initializeMqttManager();

        setupButtonListeners();

        updateBrokerStatus(mqttManager.isConnected());
        updateDeviceStatus(false);
        txtLedStatus.setText("Current Status: Unknown");
    }

    // Initialize UI components
    private void initializeViews() {

        txtBrokerStatus = findViewById(R.id.txtBrokerStatus);
        txtDeviceStatus = findViewById(R.id.txtDeviceStatus);
        txtLedStatus = findViewById(R.id.txtLedStatus);

        switchLed = findViewById(R.id.switchLed);
    }

    // Initialize MQTT manager
    private void initializeMqttManager() {

        NodeMCUIoTApplication app =
                (NodeMCUIoTApplication) getApplication();

        mqttManager = app.getMqttManager();

        mqttManager.setConnectionListener(
                new MqttManager.ConnectionListener() {

                    @Override
                    public void onConnected() {
                        runOnUiThread(() -> {
                            Log.d(TAG, "MQTT Connected");
                            updateBrokerStatus(true);
                        });
                    }

                    @Override
                    public void onDisconnected() {
                        runOnUiThread(() -> {
                            Log.d(TAG, "MQTT Disconnected");
                            updateBrokerStatus(false);
                            showToast("MQTT Disconnected");
                        });
                    }

                    @Override
                    public void onError(String message) {
                        runOnUiThread(() -> {
                            Log.e(TAG, "MQTT Error: " + message);
                            showToast("MQTT Error: " + message);
                            updateBrokerStatus(mqttManager.isConnected());
                        });
                    }

                    @Override
                    public void onMessageReceived(String topic, String message) {
                        runOnUiThread(() -> {

                            Log.d(TAG, "MainActivity received message");
                            Log.d(TAG, "Topic: " + topic);
                            Log.d(TAG, "Message: " + message);

                            if (topic.equals("abhishek/nodemcu-01/status")) {

                                if (message.equalsIgnoreCase("online")) {
                                    updateDeviceStatus(true);
                                    Log.d(TAG, "NodeMCU UI updated: ONLINE");
                                } else if (message.equalsIgnoreCase("offline")) {
                                    updateDeviceStatus(false);
                                    Log.d(TAG, "NodeMCU UI updated: OFFLINE");
                                }

                            } else if (topic.equals(
                                    "abhishek/nodemcu-01/status/builtinLed")) {

                                if (message.equalsIgnoreCase("ON")) {
                                    updateLedStatus(true);
                                } else if (message.equalsIgnoreCase("OFF")) {
                                    updateLedStatus(false);
                                }
                            }
                        });
                    }

                    @Override
                    public void onPublishComplete(String topic) {
                        runOnUiThread(() ->
                                Log.d(TAG, "Publish completed: " + topic));
                    }
                }
        );
    }
    // Configure button click listeners
    private void setupButtonListeners() {

        switchLed.setOnCheckedChangeListener((buttonView, isChecked) -> {

            if (isUpdatingLedSwitch) {
                return;
            }

            if (mqttManager != null && mqttManager.isConnected()) {

                mqttManager.publishLedCommand(
                        isChecked ? "ON" : "OFF"
                );

            }
        });

        btnBrokerSettings.setOnClickListener(v -> {

            Intent intent = new Intent(
                    MainActivity.this,
                    BrokerConnectionActivity.class
            );

            startActivity(intent);
        });
    }

    // Update connection-related UI
    // Update MQTT broker connection status
    private void updateBrokerStatus(boolean connected) {

        if (connected) {

            txtBrokerStatus.setText("● CONNECTED");

            txtBrokerStatus.setTextColor(
                    getColor(android.R.color.holo_green_dark)
            );

        } else {

            txtBrokerStatus.setText("● DISCONNECTED");

            txtBrokerStatus.setTextColor(
                    getColor(android.R.color.holo_red_dark)
            );
        }
        switchLed.setEnabled(connected);
    }


    // Update NodeMCU device status
    private void updateDeviceStatus(boolean online) {

        if (online) {

            txtDeviceStatus.setText("● ONLINE");

            txtDeviceStatus.setTextColor(
                    getColor(android.R.color.holo_green_dark)
            );

        } else {

            txtDeviceStatus.setText("● OFFLINE");

            txtDeviceStatus.setTextColor(
                    getColor(android.R.color.holo_red_dark)
            );
        }
    }

    // Update LED status based on NodeMCU feedback
    private void updateLedStatus(boolean isOn) {

        isUpdatingLedSwitch = true;

        switchLed.setChecked(isOn);

        isUpdatingLedSwitch = false;

        if (isOn) {

            txtLedStatus.setText("Status: ON");

            txtLedStatus.setTextColor(
                    getColor(android.R.color.holo_green_dark)
            );

        } else {

            txtLedStatus.setText("Status: OFF");

            txtLedStatus.setTextColor(
                    getColor(android.R.color.holo_red_dark)
            );
        }
    }

    // Display short messages
    private void showToast(String message) {

        Toast.makeText(
                MainActivity.this,
                message,
                Toast.LENGTH_SHORT
        ).show();
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mqttManager != null) {

            initializeMqttManager();

            // Refresh broker connection status
            updateBrokerStatus(mqttManager.isConnected());

            // Refresh latest NodeMCU status
            updateDeviceStatus(mqttManager.isDeviceOnline());

            if (mqttManager.isLedStatusKnown()) {
                updateLedStatus(mqttManager.isLedOn());
            }
        }
    }
    @Override
    protected void onDestroy() {

        if (mqttManager != null) {
            mqttManager.removeConnectionListener();
        }

        super.onDestroy();
    }
}
