package com.tgcannabis.config;

import com.google.gson.Gson;
import com.tgcannabis.model.SensorThreshold;
import com.google.gson.reflect.TypeToken;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.Objects;

@Getter
public class AlertConfigLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(AlertConfigLoader.class.getName());
    private static final Gson gson = new Gson();
    private static final String CONFIG_FILE = "/alerts-config.json";

    private final Map<String, SensorThreshold> thresholdsMap;

    public AlertConfigLoader() {
        this.thresholdsMap = loadConfig();
    }

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

    public SensorThreshold getThreshold(String sensorType) {
        return thresholdsMap.get(sensorType.toLowerCase());
    }
}
