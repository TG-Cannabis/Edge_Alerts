package com.tgcannabis.mqtt;

import org.eclipse.paho.client.mqttv3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.BiConsumer;

/**
 * Custom MQTT callback handler for managing connection events, message reception,
 * and automatic re-subscription to a specified topic.
 */
public class MqttCustomCallback implements MqttCallbackExtended {

    private static final Logger LOGGER = LoggerFactory.getLogger(MqttCustomCallback.class);

    private final MqttClient mqttClient;
    private final String topicFilter;
    private final BiConsumer<String, String> messageHandler; // Handles incoming messages

    /**
     * Constructs an MQTT callback instance.
     *
     * @param mqttClient The MQTT client instance.
     * @param topicFilter The topic filter to subscribe to.
     * @param messageHandler A function to process received messages, accepting topic and payload.
     */
    public MqttCustomCallback(MqttClient mqttClient, String topicFilter, BiConsumer<String, String> messageHandler) {
        this.mqttClient = mqttClient;
        this.topicFilter = topicFilter;
        this.messageHandler = messageHandler;
    }

    /**
     * Called when the MQTT connection is established or reconnected.
     *
     * @param reconnect Indicates whether the connection was re-established after a loss.
     * @param serverURI The URI of the connected MQTT broker.
     */
    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
        LOGGER.info("MQTT Connection {} complete to {}", (reconnect ? "re" : ""), serverURI);
        subscribe();
    }

    /**
     * Called when the MQTT connection is lost.
     *
     * @param cause The reason for the connection loss.
     */
    @Override
    public void connectionLost(Throwable cause) {
        LOGGER.warn("MQTT Connection lost!", cause);
    }

    /**
     * Called when a message is received on a subscribed topic.
     *
     * @param topic The topic on which the message was received.
     * @param message The received MQTT message.
     * @throws Exception If any error occurs during message processing.
     */
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

    /**
     * Called when message delivery is complete. Not used for subscribers.
     *
     * @param token The delivery token associated with the message.
     */
    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // Not needed for subscriber
    }

    /**
     * Subscribes to the configured MQTT topic filter.
     */
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
