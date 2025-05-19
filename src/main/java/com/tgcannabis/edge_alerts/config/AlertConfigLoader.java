package com.tgcannabis.edge_alerts.config;

import com.google.gson.Gson;
import com.tgcannabis.edge_alerts.model.SensorThreshold;
import com.google.gson.reflect.TypeToken;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.Objects;

/**
 * Loads sensor threshold configurations from a JSON file.
 * This class reads a configuration file containing sensor threshold settings
 * and provides access to them via a map.
 */
@Getter
public class AlertConfigLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(AlertConfigLoader.class.getName());
    private static final Gson gson = new Gson();
    private static final String CONFIG_FILE = "/alerts-config.json";

    private final Map<String, SensorThreshold> thresholdsMap;

    /**
     * Initializes the alert configuration loader by reading the configuration file.
     * The configuration is stored as a map where sensor types are keys, and the values
     * are {@link SensorThreshold} objects.
     */
    public AlertConfigLoader() {
        this.thresholdsMap = loadConfig();
    }

    /**
     * Loads the sensor threshold configuration from a JSON file.
     *
     * @return A map containing sensor types as keys and their respective thresholds as values.
     * @throws RuntimeException if the configuration file cannot be loaded.
     */
    private Map<String, SensorThreshold> loadConfig() {
        try (InputStreamReader reader = new InputStreamReader(
                Objects.requireNonNull(getClass().getResourceAsStream(CONFIG_FILE))
        )) {
            Type type = new TypeToken<Map<String, SensorThreshold>>() {
            }.getType();
            return gson.fromJson(reader, type);
        } catch (Exception e) {
            LOGGER.error("Error loading alert configuration: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to load alert configuration", e);
        }
    }

    /**
     * Retrieves the threshold configuration for a specific sensor type.
     *
     * @param sensorType The type of sensor (e.g., temperature, humidity, CO2).
     * @return The corresponding {@link SensorThreshold} object, or null if not found.
     */
    public SensorThreshold getThreshold(String sensorType) {
        return thresholdsMap.get(sensorType.toLowerCase());
    }
}
