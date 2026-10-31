package com.example.nodemcuiotcontrol;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MainActivity";

    // UI components
    private TextView txtBrokerStatus;
    private TextView txtDeviceStatus;
    private TextView txtLedStatus;

    private EditText edtMqttPassword;

    private Button btnConnect;
    private Button btnDisconnect;
    private Button btnLedOn;
    private Button btnLedOff;

    // MQTT manager
    private MqttManager mqttManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        initializeViews();

        initializeMqttManager();

        setupButtonListeners();

        updateBrokerStatus(false);
        updateDeviceStatus(false);
        txtLedStatus.setText("Current Status: Unknown");
    }

    // Initialize UI components
    private void initializeViews() {

        txtBrokerStatus = findViewById(R.id.txtBrokerStatus);
        txtDeviceStatus = findViewById(R.id.txtDeviceStatus);
        txtLedStatus = findViewById(R.id.txtLedStatus);

        edtMqttPassword = findViewById(R.id.edtMqttPassword);

        btnConnect = findViewById(R.id.btnConnect);
        btnDisconnect = findViewById(R.id.btnDisconnect);

        btnLedOn = findViewById(R.id.btnLedOn);
        btnLedOff = findViewById(R.id.btnLedOff);
    }

    // Initialize MQTT manager
    private void initializeMqttManager() {

        mqttManager = new MqttManager(
                new MqttManager.ConnectionListener() {

                    @Override
                    public void onConnected() {

                        runOnUiThread(() -> {

                            Log.d(TAG, "MQTT Connected");

                            updateBrokerStatus(true);

                            //showToast("Connected to EMQX");
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

                            Log.d(TAG, "Received topic: " + topic);
                            Log.d(TAG, "Received message: " + message);

                            // NodeMCU connection status
                            if (topic.equals("abhishek/nodemcu-01/status")) {

                                if (message.equalsIgnoreCase("online")) {
                                    updateDeviceStatus(true);
                                }
                                else if (message.equalsIgnoreCase("offline")) {
                                    updateDeviceStatus(false);
                                }
                            }
                            // LED status feedback
                            else if (topic.equals("abhishek/nodemcu-01/status/builtinLed")) {

                                if (message.equalsIgnoreCase("ON")) {
                                    updateLedStatus(true);
                                }
                                else if (message.equalsIgnoreCase("OFF")) {
                                    updateLedStatus(false);
                                }
                            }
                        });
                    }

                    @Override
                    public void onPublishComplete(String topic) {

                        runOnUiThread(() -> {

                            Log.d(TAG, "Publish completed: " + topic);

                        });
                    }
                }
        );
    }

    // Configure button click listeners
    private void setupButtonListeners() {

        btnConnect.setOnClickListener(view -> {

            String password =
                    edtMqttPassword.getText().toString();

            if (password.isEmpty()) {

                edtMqttPassword.setError("Enter MQTT password");
                return;
            }

            txtBrokerStatus.setText("Connecting...");
            btnConnect.setEnabled(false);

            mqttManager.connect(password);
        });

        btnDisconnect.setOnClickListener(view -> {

            mqttManager.disconnect();

        });

        btnLedOn.setOnClickListener(view -> {

            mqttManager.publishLedCommand("ON");

        });

        btnLedOff.setOnClickListener(view -> {

            mqttManager.publishLedCommand("OFF");

        });
    }

    // Update connection-related UI
    // Update MQTT broker connection status
    private void updateBrokerStatus(boolean connected) {

        if (connected) {

            txtBrokerStatus.setText("● MQTT Broker: Connected");

            txtBrokerStatus.setTextColor(
                    getColor(android.R.color.holo_green_dark)
            );

        } else {

            txtBrokerStatus.setText("● MQTT Broker: Disconnected");

            txtBrokerStatus.setTextColor(
                    getColor(android.R.color.holo_red_dark)
            );
        }

        btnConnect.setEnabled(!connected);
        btnDisconnect.setEnabled(connected);

        btnLedOn.setEnabled(connected);
        btnLedOff.setEnabled(connected);
    }


    // Update NodeMCU device status
    private void updateDeviceStatus(boolean online) {

        if (online) {

            txtDeviceStatus.setText("● NodeMCU: Online");

            txtDeviceStatus.setTextColor(
                    getColor(android.R.color.holo_green_dark)
            );

        } else {

            txtDeviceStatus.setText("● NodeMCU: Offline");

            txtDeviceStatus.setTextColor(
                    getColor(android.R.color.holo_red_dark)
            );
        }
    }

    // Update LED status based on NodeMCU feedback
    private void updateLedStatus(boolean isOn) {

        if (isOn) {

            txtLedStatus.setText("Current Status: ON");

            txtLedStatus.setTextColor(
                    getColor(android.R.color.holo_green_dark)
            );

        } else {

            txtLedStatus.setText("Current Status: OFF");

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
    protected void onDestroy() {

        if (mqttManager != null) {
            mqttManager.disconnect();
        }

        super.onDestroy();
    }
}
