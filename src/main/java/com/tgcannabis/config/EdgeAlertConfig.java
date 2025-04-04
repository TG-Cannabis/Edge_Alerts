package com.tgcannabis.config;

import io.github.cdimascio.dotenv.Dotenv;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Getter
public class EdgeAlertConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(EdgeAlertConfig.class);

    private final String mqttBroker;
    private final String mqttClientId;
    private final String mqttTopic;

    /**
     * Initializes the key connection configuration value and keys by reading the env file
     */
    public EdgeAlertConfig() {
        Dotenv dotenv = Dotenv.configure()
                .ignoreIfMissing()
                .load();
        mqttBroker = getEnv(dotenv, "MQTT_BROKER", "tcp://localhost:1883");
        mqttClientId = getEnv(dotenv, "MQTT_CLIENT_ID", "batch-processor-" + System.currentTimeMillis());
        mqttTopic = getEnv(dotenv, "MQTT_TOPIC_FILTER", "sensors/#");

        logConfiguration();
    }

    /**
     * Gets a value from Dotenv or system environment, returning a default if not found.
     *
     * @param dotenv       Dotenv instance
     * @param varName      Environment variable name
     * @param defaultValue Default value if not found
     * @return The value found or the default value
     */
    private String getEnv(Dotenv dotenv, String varName, String defaultValue) {
        String value = dotenv.get(varName);
        if (value == null || value.trim().isEmpty()) {
            LOGGER.warn("Environment variable '{}' not found or empty, using default: '{}'", varName, defaultValue);
            return defaultValue;
        }
        return value;
    }

    /**
     * Logs the loaded configuration (except sensitive tokens).
     */
    private void logConfiguration() {
        LOGGER.info("Batch Processor Configuration Loaded:");
        LOGGER.info("  MQTT Broker: {}", mqttBroker);
        LOGGER.info("  MQTT Client ID: {}", mqttClientId);
        LOGGER.info("  MQTT Topic Filter: {}", mqttTopic);
    }
}
