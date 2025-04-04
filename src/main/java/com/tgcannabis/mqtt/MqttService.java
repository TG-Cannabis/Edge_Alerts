package com.tgcannabis.mqtt;

import com.tgcannabis.config.EdgeAlertConfig;
import lombok.Setter;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.function.BiConsumer;

public class MqttService implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(MqttService.class);

    private MqttClient mqttClient;

    private final EdgeAlertConfig config;

    @Setter
    private BiConsumer<String, String> messageHandler;

    public MqttService(EdgeAlertConfig config) {
        this.config = Objects.requireNonNull(config, "Configuration cannot be null");
    }

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
