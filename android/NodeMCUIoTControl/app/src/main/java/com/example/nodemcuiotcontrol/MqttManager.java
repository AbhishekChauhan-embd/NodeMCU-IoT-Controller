package com.example.nodemcuiotcontrol;

import android.util.Log;

import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
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

    private static final String LED_DESIRED_STATE_TOPIC =
            "abhishek/nodemcu-01/desired/builtinLed";

    private static final String DEVICE_STATUS_TOPIC =
            "abhishek/nodemcu-01/status";

    private static final String LED_STATUS_TOPIC =
            "abhishek/nodemcu-01/status/builtinLed";

    private static final int CONNECTION_TIMEOUT = 15;
    private static final int KEEP_ALIVE_INTERVAL = 30;
    private static final int QOS = 1;

    private MqttAsyncClient mqttClient;

    private volatile boolean connected = false;
    private volatile boolean connectionInProgress = false;
    private volatile boolean disconnectInProgress = false;
    private volatile boolean manuallyDisconnected = false;
    private volatile boolean deviceOnline = false;
    private volatile boolean ledStatusKnown = false;
    private volatile boolean ledOn = false;

    // Activity listener
    private volatile ConnectionListener listener;

    // Listener interface
    public interface ConnectionListener {

        void onConnected();

        void onDisconnected();

        void onError(String message);

        void onMessageReceived(String topic, String message);

        void onPublishComplete(String topic);
    }

    // Constructor
    public MqttManager() {
    }

    // Register listener
    public synchronized void setConnectionListener(ConnectionListener listener) {
        this.listener = listener;

        Log.d(TAG, "Connection listener registered: " +
                (listener != null ? listener.getClass().getName() : "NULL"));
    }

    // Remove listener
    public synchronized void removeConnectionListener() {
        this.listener = null;
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

        manuallyDisconnected = false;
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
                }

                @Override
                public void onFailure(
                        IMqttToken asyncActionToken,
                        Throwable exception) {

                    synchronized (MqttManager.this) {
                        connectionInProgress = false;
                        connected = false;
                    }

                    Log.e(TAG, "MQTT connection failed", exception);

                    notifyError(getErrorMessage(exception));
                }
            });

        } catch (MqttException e) {

            connectionInProgress = false;
            connected = false;

            Log.e(TAG, "MQTT connection exception", e);

            notifyError(getErrorMessage(e));
        }
    }

    public boolean isLedStatusKnown() {
        return ledStatusKnown;
    }

    public boolean isLedOn() {
        return ledOn;
    }
    public boolean isDeviceOnline() {
        return deviceOnline;
    }
    // Configure MQTT callbacks
    private void setMqttCallback() {

        mqttClient.setCallback(new MqttCallbackExtended() {

            @Override
            public void connectComplete(
                    boolean reconnect,
                    String serverURI) {

                Log.d(TAG, reconnect
                        ? "MQTT reconnected successfully"
                        : "MQTT initial connection completed");

                synchronized (MqttManager.this) {

                    connectionInProgress = false;

                    // Ignore connection if user manually disconnected.
                    if (manuallyDisconnected) {

                        Log.d(TAG,
                                "Manual disconnect requested. Closing connection.");

                        disconnect();
                        return;
                    }

                    connected = true;
                }

                // Restore subscriptions
                subscribe(DEVICE_STATUS_TOPIC);
                subscribe(LED_STATUS_TOPIC);
                subscribe(LED_DESIRED_STATE_TOPIC);

                notifyConnected();
            }

            @Override
            public void connectionLost(Throwable cause) {

                Log.e(TAG, "MQTT connection lost", cause);

                connected = false;

                notifyDisconnected();
            }

            @Override
            public void messageArrived(
                    String topic,
                    MqttMessage message) {

                String receivedMessage =
                        new String(
                                message.getPayload(),
                                StandardCharsets.UTF_8
                        );
                Log.d(TAG, "MESSAGE RECEIVED!");
                Log.d(TAG, "Topic: " + topic);
                Log.d(TAG, "Payload: " + receivedMessage);

                if (topic.equals(DEVICE_STATUS_TOPIC)) {

                    if (receivedMessage.equalsIgnoreCase("online")) {
                        deviceOnline = true;
                    } else if (receivedMessage.equalsIgnoreCase("offline")) {
                        deviceOnline = false;
                    }
                }

                if (topic.equals(LED_STATUS_TOPIC)) {
                    if (receivedMessage.equalsIgnoreCase("ON")) {
                        ledOn = true;
                        ledStatusKnown = true;
                    } else if (receivedMessage.equalsIgnoreCase("OFF")) {
                        ledOn = false;
                        ledStatusKnown = true;
                    }
                }
                notifyMessageReceived(topic, receivedMessage);
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {

                Log.d(TAG, "Message delivery completed");
            }
        });
    }

    // Disconnect MQTT connection
    public synchronized void disconnect() {

        // Record manual disconnect even if connection is still pending.
        manuallyDisconnected = true;

        if (disconnectInProgress) {
            Log.d(TAG, "Disconnection already in progress");
            return;
        }

        if (mqttClient == null) {

            connected = false;
            Log.d(TAG, "MQTT client is not initialized");

            return;
        }

        // If connection is still in progress, connectComplete()
        // will handle the pending manual disconnect.
        if (!mqttClient.isConnected()) {

            connected = false;

            Log.d(TAG, "MQTT client is already disconnected");

            return;
        }

        disconnectInProgress = true;

        try {

            mqttClient.disconnect(null, new IMqttActionListener() {

                @Override
                public void onSuccess(IMqttToken asyncActionToken) {

                    synchronized (MqttManager.this) {

                        disconnectInProgress = false;
                        connected = false;
                    }

                    Log.d(TAG, "MQTT disconnected successfully");

                    notifyDisconnected();
                }

                @Override
                public void onFailure(
                        IMqttToken asyncActionToken,
                        Throwable exception) {

                    synchronized (MqttManager.this) {

                        disconnectInProgress = false;
                        connected = false;
                    }

                    Log.e(TAG, "MQTT disconnection failed", exception);

                    notifyError(getErrorMessage(exception));
                }
            });

        } catch (MqttException e) {

            disconnectInProgress = false;
            connected = false;

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

        publishRetainedMessage(LED_DESIRED_STATE_TOPIC, command);
    }

    // Generic MQTT publish method
    public void publishMessage(String topic, String payload) {

        if (!isConnected()) {

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

                    Log.d(TAG, "Published successfully: " + topic);

                    notifyPublishComplete(topic);
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

        if (!isConnected()) {

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

                    Log.d(TAG,
                            "Retained message published successfully: " + topic);

                    notifyPublishComplete(topic);
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

        if (!isConnected()) {

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

                    Log.e(TAG,
                            "Subscription failed for: " + topic,
                            exception);

                    notifyError(
                            "Subscription failed: " +
                                    getErrorMessage(exception)
                    );
                }
            });

        } catch (MqttException e) {

            Log.e(TAG, "Subscribe exception for: " + topic, e);

            notifyError(
                    "Subscription error: " + getErrorMessage(e)
            );
        }
    }

    // Check MQTT connection state
    public boolean isConnected() {

        return connected &&
                mqttClient != null &&
                mqttClient.isConnected();
    }

    // Check manual disconnect state
    public boolean isManuallyDisconnected() {

        return manuallyDisconnected;
    }

    // Return readable error message
    private String getErrorMessage(Throwable exception) {

        if (exception == null) {
            return "Unknown MQTT error";
        }

        String message = exception.getMessage();

        return message != null
                ? message
                : exception.toString();
    }

    // Listener notification methods
    private void notifyConnected() {

        ConnectionListener currentListener = listener;

        if (currentListener != null) {
            currentListener.onConnected();
        }
    }

    private void notifyDisconnected() {

        ConnectionListener currentListener = listener;

        if (currentListener != null) {
            currentListener.onDisconnected();
        }
    }

    private void notifyError(String message) {

        ConnectionListener currentListener = listener;

        if (currentListener != null) {
            currentListener.onError(message);
        }
    }

    private void notifyMessageReceived(String topic, String message) {

        ConnectionListener currentListener = listener;

        Log.d(TAG, "Forwarding message to listener");
        Log.d(TAG, "Active listener: " +
                (currentListener != null ? "AVAILABLE" : "NULL"));

        if (currentListener != null) {
            currentListener.onMessageReceived(topic, message);
        }
    }

    private void notifyPublishComplete(String topic) {

        ConnectionListener currentListener = listener;

        if (currentListener != null) {
            currentListener.onPublishComplete(topic);
        }
    }
}
