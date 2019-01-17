package org.mobicents.servers.diameter.gmlc.queue;

import com.google.gson.Gson;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.mobicents.servers.diameter.gmlc.queue.data.LocationInformation;

public class GmlcConnector implements MqttCallback {

    String clientId  = "gmlc-diameter-service";
    String topic     = "gmlc.beconnect.us";
    String broker    = "tcp://localhost:1883";

    Boolean mqttClientConnected = false;
    MqttClient mqttClient = null;
    Boolean isServer = false;

    IGmlcSubscriber callback = null;

    private void connectMqtt() {
        if (!mqttClientConnected) {
            try {
                MemoryPersistence persistence = new MemoryPersistence();
                mqttClient = new MqttClient(broker, clientId, persistence);
                MqttConnectOptions connOpts = new MqttConnectOptions();
                connOpts.setCleanSession(true);
                mqttClient.connect(connOpts);
                mqttClient.setCallback(this);
                mqttClient.subscribe((isServer ? "in." : "out.") + topic, 2);
                this.mqttClientConnected = mqttClient.isConnected();
            } catch (MqttException e) {
            }
        }
    }

    public GmlcConnector(Boolean isServer, IGmlcSubscriber callback) {
        super();
        this.isServer = isServer;
        this.callback = callback;
        connectMqtt();
    }

    public void Destroy() {
        try {
            mqttClient.disconnect();
        } catch (MqttException me) {
        }
    }

    public synchronized void send(LocationInformation locationInformation) {
        try {
            connectMqtt();
            String locationRequestContent = new Gson().toJson(locationInformation);
            MqttMessage mqttMessage = new MqttMessage(locationRequestContent.getBytes());
            mqttMessage.setQos(2);
            mqttClient.publish((isServer ? "out." : "in." ) + topic, mqttMessage);
        } catch(MqttException me) {
        }
    }

    /**
     * callback interface methods
     *
     */
    public void connectionLost(Throwable throwable) {
        this.mqttClientConnected = false;
    }

    public void messageArrived(String s, MqttMessage mqttMessage) throws Exception {
        String jsonMessage = new String(mqttMessage.getPayload());

        LocationInformation locationInformation = new Gson().fromJson(jsonMessage, LocationInformation.class);
        callback.notificationReceived(locationInformation);
    }

    public void deliveryComplete(IMqttDeliveryToken iMqttDeliveryToken) {
    }

}
