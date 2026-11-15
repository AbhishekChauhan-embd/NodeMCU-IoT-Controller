package com.example.nodemcuiotcontrol;

import android.os.Bundle;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.Button;
import android.widget.Toast;
import android.text.InputType;

import androidx.appcompat.app.AppCompatActivity;

public class BrokerConnectionActivity extends AppCompatActivity {

    private MqttManager mqttManager;

    // UI Components
    private TextView txtBrokerConnectionStatus;
    private TextView txtBrokerHost;
    private TextView txtBrokerUsername;
    private EditText edtMqttPassword;

    private Switch switchShowPassword;
    private Switch switchRememberPassword;

    private Button btnBrokerConnect;
    private Button btnBrokerDisconnect;
    private TextView btnBack;

    // Password entered for the current connection attempt
    private String pendingPassword;

    private final MqttManager.ConnectionListener connectionListener =
            new MqttManager.ConnectionListener() {

                @Override
                public void onConnected() {

                    runOnUiThread(() -> {

                        updateBrokerStatus();

                        // Save password only after successful connection
                        if (switchRememberPassword.isChecked()
                                && pendingPassword != null
                                && !pendingPassword.isEmpty()) {

                            boolean saved = MqttCredentialStorage.savePassword(
                                    BrokerConnectionActivity.this,
                                    pendingPassword
                            );

                            if (saved) {
                                Toast.makeText(
                                        BrokerConnectionActivity.this,
                                        "MQTT password saved securely",
                                        Toast.LENGTH_SHORT
                                ).show();
                            } else {
                                Toast.makeText(
                                        BrokerConnectionActivity.this,
                                        "Unable to save MQTT password",
                                        Toast.LENGTH_LONG
                                ).show();
                            }

                        } else {
                            MqttCredentialStorage.clearPassword(
                                    BrokerConnectionActivity.this
                            );
                        }

                        pendingPassword = null;
                    });
                }

                @Override
                public void onDisconnected() {
                    runOnUiThread(() -> updateBrokerStatus());
                }

                @Override
                public void onError(String message) {

                    runOnUiThread(() -> {

                        updateBrokerStatus();

                        pendingPassword = null;

                        Toast.makeText(
                                BrokerConnectionActivity.this,
                                "MQTT connection failed: " + message,
                                Toast.LENGTH_LONG
                        ).show();
                    });
                }

                @Override
                public void onMessageReceived(String topic, String message) {
                    // Not required for connection status.
                }

                @Override
                public void onPublishComplete(String topic) {
                    // Not required for connection status.
                }
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_broker_connection);

        NodeMCUIoTApplication app =
                (NodeMCUIoTApplication) getApplication();

        mqttManager = app.getMqttManager();

        // Initialize UI Components
        txtBrokerConnectionStatus =
                findViewById(R.id.txtBrokerConnectionStatus);

        txtBrokerHost = findViewById(R.id.txtBrokerHost);
        txtBrokerUsername = findViewById(R.id.txtBrokerUsername);
        edtMqttPassword = findViewById(R.id.edtMqttPassword);

        switchShowPassword = findViewById(R.id.switchShowPassword);
        switchRememberPassword = findViewById(R.id.switchRememberPassword);

        btnBrokerConnect = findViewById(R.id.btnBrokerConnect);
        btnBrokerDisconnect = findViewById(R.id.btnBrokerDisconnect);
        btnBack = findViewById(R.id.btnBack);

        // Load previously saved password
        loadSavedCredentials();

        mqttManager.setConnectionListener(connectionListener);

        updateBrokerStatus();

        btnBack.setOnClickListener(v -> finish());

        // CONNECT BUTTON
        btnBrokerConnect.setOnClickListener(v -> {

            String password = edtMqttPassword.getText().toString().trim();

            if (password.isEmpty()) {
                edtMqttPassword.setError("Please enter MQTT password");
                edtMqttPassword.requestFocus();
                return;
            }

            if (mqttManager.isConnected()) {

                Toast.makeText(
                        BrokerConnectionActivity.this,
                        "Already connected to MQTT broker",
                        Toast.LENGTH_SHORT
                ).show();

                return;
            }

            pendingPassword = password;

            mqttManager.connect(password);
        });

        // DISCONNECT BUTTON
        btnBrokerDisconnect.setOnClickListener(v -> {

            if (!mqttManager.isConnected()) {

                Toast.makeText(
                        BrokerConnectionActivity.this,
                        "MQTT is already disconnected",
                        Toast.LENGTH_SHORT
                ).show();

                return;
            }

            mqttManager.disconnect();
        });

        // SHOW / HIDE PASSWORD
        switchShowPassword.setOnCheckedChangeListener(
                (buttonView, isChecked) -> {

                    if (isChecked) {

                        edtMqttPassword.setInputType(
                                InputType.TYPE_CLASS_TEXT |
                                        InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                        );

                    } else {

                        edtMqttPassword.setInputType(
                                InputType.TYPE_CLASS_TEXT |
                                        InputType.TYPE_TEXT_VARIATION_PASSWORD
                        );
                    }

                    edtMqttPassword.setSelection(
                            edtMqttPassword.getText().length()
                    );
                }
        );

        // REMEMBER PASSWORD
        switchRememberPassword.setOnCheckedChangeListener(
                (buttonView, isChecked) -> {

                    if (!isChecked) {

                        // Immediately clear saved credentials
                        MqttCredentialStorage.clearPassword(
                                BrokerConnectionActivity.this
                        );

                        Toast.makeText(
                                BrokerConnectionActivity.this,
                                "Saved password removed",
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                }
        );
    }

    private void loadSavedCredentials() {

        String savedPassword = MqttCredentialStorage.getPassword(this);

        if (savedPassword != null && !savedPassword.isEmpty()) {

            edtMqttPassword.setText(savedPassword);

            // Enable Remember Password without triggering its listener
            switchRememberPassword.setChecked(true);

        } else {

            switchRememberPassword.setChecked(false);
        }
    }

    private void updateBrokerStatus() {

        if (mqttManager.isConnected()) {

            txtBrokerConnectionStatus.setText("● CONNECTED");

            txtBrokerConnectionStatus.setTextColor(
                    getColor(android.R.color.holo_green_dark)
            );

        } else {

            txtBrokerConnectionStatus.setText("● DISCONNECTED");

            txtBrokerConnectionStatus.setTextColor(
                    getColor(android.R.color.holo_red_dark)
            );
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}