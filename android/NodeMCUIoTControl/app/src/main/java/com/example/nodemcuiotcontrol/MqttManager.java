package com.example.nodemcuiotcontrol;

import android.util.Log;

import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class MqttManager {

    private static final String TAG = "MqttManager";

    // EMQX Cloud configuration
    private static final String BROKER_URL =
            "ssl://j6fffe6b.ala.asia-southeast1.emqxsl.com:8883";

    private static final String USERNAME = "esp32_device01";

    private static final String LED_DESIRED_STATE_TOPIC = "abhishek/nodemcu-01/desired/builtinLed";

    private static final String DEVICE_STATUS_TOPIC = "abhishek/nodemcu-01/status";

    private static final String LED_STATUS_TOPIC = "abhishek/nodemcu-01/status/builtinLed";

    private static final int CONNECTION_TIMEOUT = 15;
    private static final int KEEP_ALIVE_INTERVAL = 30;
    private static final int QOS = 1;

    private MqttAsyncClient mqttClient;

    private boolean connectionInProgress = false;
    private boolean disconnectInProgress = false;

    private final ConnectionListener listener;

    // Listener interface for communicating with MainActivity
    public interface ConnectionListener {

        void onConnected();

        void onDisconnected();

        void onError(String message);

        void onMessageReceived(String topic, String message);

        void onPublishComplete(String topic);
    }

    // Constructor
    public MqttManager(ConnectionListener listener) {
        this.listener = listener;
    }

    // Establish MQTT connection
    public synchronized void connect(String password) {

        if (connectionInProgress) {
            Log.d(TAG, "Connection already in progress");
            return;
        }

        if (mqttClient != null && mqttClient.isConnected()) {
            Log.d(TAG, "Already connected");
            return;
        }

        if (disconnectInProgress) {
            notifyError("Please wait for disconnection to complete");
            return;
        }

        connectionInProgress = true;

        try {

            if (mqttClient == null) {

                String clientId = "Android_IoT_" +
                        UUID.randomUUID().toString().replace("-", "");

                mqttClient = new MqttAsyncClient(
                        BROKER_URL,
                        clientId,
                        new MemoryPersistence()
                );

                setMqttCallback();
            }

            MqttConnectOptions options = new MqttConnectOptions();

            options.setUserName(USERNAME);
            options.setPassword(password.toCharArray());

            options.setCleanSession(true);
            options.setAutomaticReconnect(true);

            options.setConnectionTimeout(CONNECTION_TIMEOUT);
            options.setKeepAliveInterval(KEEP_ALIVE_INTERVAL);

            mqttClient.connect(options, null, new IMqttActionListener() {

                @Override
                public void onSuccess(IMqttToken asyncActionToken) {

                    synchronized (MqttManager.this) {
                        connectionInProgress = false;
                    }

                    Log.d(TAG, "MQTT connection successful");

                    // Subscribe to NodeMCU device status
                    subscribe(DEVICE_STATUS_TOPIC);
                    Log.d(TAG, "Attempting to subscribe to device status...");
                    // Subscribe to LED status feedback
                    subscribe(LED_STATUS_TOPIC);
                    Log.d(TAG, "Attempting to subscribe to LED status...");
                    // Subscribe to LED desired state
                    subscribe(LED_DESIRED_STATE_TOPIC);
                    Log.d(TAG, "Attempting to subscribe to LED desired state...");

                    if (listener != null) {
                        listener.onConnected();
                    }
                }

                @Override
                public void onFailure(
                        IMqttToken asyncActionToken,
                        Throwable exception) {

                    synchronized (MqttManager.this) {
                        connectionInProgress = false;
                    }

                    Log.e(TAG, "MQTT connection failed", exception);

                    notifyError(getErrorMessage(exception));
                }
            });

        } catch (MqttException e) {

            connectionInProgress = false;

            Log.e(TAG, "MQTT connection exception", e);

            notifyError(getErrorMessage(e));
        }
    }

    // Configure MQTT callbacks
    private void setMqttCallback() {

        mqttClient.setCallback(new MqttCallback() {

            @Override
            public void connectionLost(Throwable cause) {

                Log.e(TAG, "MQTT connection lost", cause);

                if (listener != null) {
                    listener.onDisconnected();
                }
            }

            @Override
            public void messageArrived(
                    String topic,
                    MqttMessage message) {

                String receivedMessage =
                        new String(message.getPayload(), StandardCharsets.UTF_8);

                Log.d(TAG, "MESSAGE RECEIVED!");
                Log.d(TAG, "Topic: " + topic);
                Log.d(TAG, "Payload: " + receivedMessage);

                if (listener != null) {
                    listener.onMessageReceived(topic, receivedMessage);
                }
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {

                Log.d(TAG, "Message delivery completed");
            }
        });
    }

    // Disconnect MQTT connection
    public synchronized void disconnect() {

        if (mqttClient == null || !mqttClient.isConnected()) {
            Log.d(TAG, "MQTT client is already disconnected");
            return;
        }

        if (disconnectInProgress) {
            Log.d(TAG, "Disconnection already in progress");
            return;
        }

        disconnectInProgress = true;

        try {

            mqttClient.disconnect(null, new IMqttActionListener() {

                @Override
                public void onSuccess(IMqttToken asyncActionToken) {

                    synchronized (MqttManager.this) {
                        disconnectInProgress = false;
                    }

                    Log.d(TAG, "MQTT disconnected successfully");

                    if (listener != null) {
                        listener.onDisconnected();
                    }
                }

                @Override
                public void onFailure(
                        IMqttToken asyncActionToken,
                        Throwable exception) {

                    synchronized (MqttManager.this) {
                        disconnectInProgress = false;
                    }

                    Log.e(TAG, "MQTT disconnection failed", exception);

                    notifyError(getErrorMessage(exception));
                }
            });

        } catch (MqttException e) {

            disconnectInProgress = false;

            Log.e(TAG, "Disconnect exception", e);

            notifyError(getErrorMessage(e));
        }
    }

    // Publish LED command
    public void publishLedCommand(String command) {

        if (!"ON".equals(command) && !"OFF".equals(command)) {
            notifyError("Invalid LED command");
            return;
        }

      //  publishMessage(LED_DESIRED_STATE_TOPIC, command);
        publishRetainedMessage(LED_DESIRED_STATE_TOPIC, command);
    }

    // Generic MQTT publish method
    public void publishMessage(String topic, String payload) {

        if (mqttClient == null || !mqttClient.isConnected()) {
            notifyError("MQTT client is not connected");
            return;
        }

        try {

            MqttMessage message = new MqttMessage(
                    payload.getBytes(StandardCharsets.UTF_8)
            );

            message.setQos(QOS);

            mqttClient.publish(topic, message, null, new IMqttActionListener() {

                @Override
                public void onSuccess(IMqttToken asyncActionToken) {

                    Log.d(TAG, "Published successfully: " + payload);

                    if (listener != null) {
                        listener.onPublishComplete(topic);
                    }
                }

                @Override
                public void onFailure(
                        IMqttToken asyncActionToken,
                        Throwable exception) {

                    Log.e(TAG, "Publish failed", exception);

                    notifyError(getErrorMessage(exception));
                }
            });

        } catch (MqttException e) {

            Log.e(TAG, "Publish exception", e);

            notifyError(getErrorMessage(e));
        }
    }

    // Publish retained MQTT message
    public void publishRetainedMessage(String topic, String payload) {

        if (mqttClient == null || !mqttClient.isConnected()) {
            notifyError("MQTT client is not connected");
            return;
        }

        try {

            MqttMessage message = new MqttMessage(
                    payload.getBytes(StandardCharsets.UTF_8)
            );

            message.setQos(QOS);
            message.setRetained(true);

            mqttClient.publish(topic, message, null, new IMqttActionListener() {

                @Override
                public void onSuccess(IMqttToken asyncActionToken) {

                    Log.d(TAG, "Retained message published successfully: "
                            + topic + " -> " + payload);

                    if (listener != null) {
                        listener.onPublishComplete(topic);
                    }
                }

                @Override
                public void onFailure(
                        IMqttToken asyncActionToken,
                        Throwable exception) {

                    Log.e(TAG, "Retained publish failed", exception);

                    notifyError(getErrorMessage(exception));
                }
            });

        } catch (MqttException e) {

            Log.e(TAG, "Retained publish exception", e);

            notifyError(getErrorMessage(e));
        }
    }


    // Subscribe to MQTT topic
    public void subscribe(String topic) {

        Log.d(TAG, "Subscribe method called for: " + topic);

        if (mqttClient == null || !mqttClient.isConnected()) {
            Log.e(TAG, "Cannot subscribe: MQTT client is not connected");
            notifyError("Cannot subscribe: MQTT client is disconnected");
            return;
        }

        try {

            mqttClient.subscribe(topic, QOS, null, new IMqttActionListener() {

                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    Log.d(TAG, "Subscribed successfully: " + topic);
                }

                @Override
                public void onFailure(
                        IMqttToken asyncActionToken,
                        Throwable exception) {

                        Log.e(TAG, "Subscription failed for: " + topic, exception);

                        notifyError("Subscription failed: " + getErrorMessage(exception));
                }
            });

        } catch (MqttException e) {

            Log.e(TAG, "Subscribe exception for: " + topic, e);

            notifyError("Subscription error: " + getErrorMessage(e));
        }
    }

    // Check MQTT connection state
    public boolean isConnected() {

        return mqttClient != null && mqttClient.isConnected();
    }

    // Return readable error message
    private String getErrorMessage(Throwable exception) {

        if (exception == null) {
            return "Unknown MQTT error";
        }

        String message = exception.getMessage();

        return message != null ? message : exception.toString();
    }

    // Notify error through listener
    private void notifyError(String message) {

        if (listener != null) {
            listener.onError(message);
        }
    }

}
