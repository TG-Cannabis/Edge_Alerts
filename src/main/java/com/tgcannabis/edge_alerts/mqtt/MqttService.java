package com.tgcannabis.edge_alerts.mqtt;

import com.tgcannabis.edge_alerts.config.EdgeAlertConfig;
import lombok.Getter;
import lombok.Setter;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * Manages MQTT connection, subscription, and message handling.
 * This service allows connecting to an MQTT broker, handling messages,
 * and disconnecting gracefully.
 */
public class MqttService implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(MqttService.class);

    @Getter
    private MqttClient mqttClient;

    private final EdgeAlertConfig config;

    /**
     * Functional interface for handling incoming MQTT messages.
     * Accepts a topic and message payload.
     */
    @Setter
    private BiConsumer<String, String> messageHandler;

    /**
     * Constructs the MQTT service with the provided configuration.
     *
     * @param config The MQTT configuration settings. Must not be null.
     * @throws NullPointerException if config is null.
     */
    public MqttService(EdgeAlertConfig config) {
        this.config = Objects.requireNonNull(config, "Configuration cannot be null");
    }

    /**
     * Establishes a connection to the MQTT broker and sets up message handling.
     *
     * @throws MqttException        If the connection to the broker fails.
     * @throws NullPointerException If messageHandler is not set before connecting.
     */
    public void connect() throws MqttException {
        Objects.requireNonNull(messageHandler, "Message handler must be set before connecting");

        mqttClient = new MqttClient(config.getMqttBroker(), config.getMqttClientId(), new MemoryPersistence());
        MqttConnectOptions connectOptions = new MqttConnectOptions();
        connectOptions.setCleanSession(true);

        mqttClient.setCallback(new MqttCustomCallback(mqttClient, config.getMqttTopic(), messageHandler));

        LOGGER.info("Connecting to MQTT broker: {}", config.getMqttBroker());

        try {
            mqttClient.connect();
        } catch (MqttException e) {
            LOGGER.error("Error connecting MQTT client: {}", e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Closes the MQTT connection gracefully.
     * Ensures disconnection before shutting down the client.
     */
    @Override
    public void close() {
        if (mqttClient != null && mqttClient.isConnected()) {
            try {
                mqttClient.disconnect();
            } catch (MqttException e) {
                LOGGER.error("Error disconnecting MQTT client: {}", e.getMessage(), e);
            } finally {
                closeClientQuietly();
            }
        } else if (mqttClient != null) {
            closeClientQuietly();
        }
    }

    /**
     * Closes the MQTT client instance quietly, suppressing any exceptions.
     */
    private void closeClientQuietly() {
        try {
            mqttClient.close();
        } catch (MqttException e) {
            LOGGER.error("Error closing MQTT client instance: {}", e.getMessage(), e);
        } finally {
            mqttClient = null;
        }
    }

}
