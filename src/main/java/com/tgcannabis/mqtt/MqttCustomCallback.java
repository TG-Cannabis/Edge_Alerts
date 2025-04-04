package com.tgcannabis.mqtt;

import org.eclipse.paho.client.mqttv3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.BiConsumer;

public class MqttCustomCallback implements MqttCallbackExtended {

    private static final Logger LOGGER = LoggerFactory.getLogger(MqttCustomCallback.class);

    private final MqttClient mqttClient;
    private final String topicFilter;
    private final BiConsumer<String, String> messageHandler; // Handles incoming messages

    public MqttCustomCallback(MqttClient mqttClient, String topicFilter, BiConsumer<String, String> messageHandler) {
        this.mqttClient = mqttClient;
        this.topicFilter = topicFilter;
        this.messageHandler = messageHandler;
    }

    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
        LOGGER.info("MQTT Connection {} complete to {}", (reconnect ? "re" : ""), serverURI);
        subscribe();
    }

    @Override
    public void connectionLost(Throwable cause) {
        LOGGER.warn("MQTT Connection lost!", cause);
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        try {
            String payload = new String(message.getPayload());
            LOGGER.debug("MQTT Message received - Topic: [{}], Payload: [{}]", topic, payload);

            if (messageHandler != null) {
                messageHandler.accept(topic, payload);
            } else {
                LOGGER.warn("No message handler set for received message on topic {}", topic);
            }
        } catch (Exception e) {
            LOGGER.error("Error processing message from topic {}: {}", topic, e.getMessage(), e);
        }
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // Not needed for subscriber
    }

    private void subscribe() {
        if (mqttClient != null && mqttClient.isConnected()) {
            try {
                LOGGER.info("Subscribing to MQTT topic filter: {}", topicFilter);
                mqttClient.subscribe(topicFilter, 1); // QoS 1: At least once
            } catch (MqttException e) {
                LOGGER.error("Error subscribing to MQTT topic filter '{}': {}", topicFilter, e.getMessage(), e);
            }
        } else {
            LOGGER.warn("Cannot subscribe, MQTT client not connected.");
        }
    }
}
