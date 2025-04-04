package com.tgcannabis.alerts;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.tgcannabis.config.AlertConfigLoader;
import com.tgcannabis.model.SensorData;
import com.tgcannabis.model.SensorInformation;
import com.tgcannabis.model.SensorThreshold;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

public class AlertProcessor implements BiConsumer<String, String> {

    private static final Logger LOGGER = LoggerFactory.getLogger(AlertProcessor.class);
    private static final Gson gson = new Gson();

    private final Map<String, List<SensorData>> history = new ConcurrentHashMap<>();
    private final AlertConfigLoader configLoader;

    public AlertProcessor(AlertConfigLoader configLoader) {
        this.configLoader = Objects.requireNonNull(configLoader, "Alert config loader cannot be null");
    }

    @Override
    public void accept(String topic, String payload) {
        LOGGER.debug("Processing sensor data for alert detection - Topic: [{}], Payload: [{}]", topic, payload);

        try {
            SensorData sensorData = gson.fromJson(payload, SensorData.class);

            if (sensorData == null || sensorData.getSensorName() == null || sensorData.getSensorName().getId() == null) {
                LOGGER.warn("Skipping message due to incomplete data after serialization: {}", payload);
                return;
            }
            checkForAlert(sensorData);
        } catch (JsonSyntaxException e) {
            LOGGER.error("JSON Parsing Error - Topic: [{}], Payload: [{}], Error: [{}]", topic, payload, e.getMessage());
        } catch (Exception e) {
            LOGGER.error("Unexpected error processing message - Topic: [{}], Error: [{}]", topic, e.getMessage(), e);
        }
    }

    private void checkForAlert(SensorData data) {
        String sensorType = data.getSensorName().getSensorType().toLowerCase();
        SensorThreshold threshold = configLoader.getThreshold(sensorType);

        if (threshold == null) {
            LOGGER.warn("No alert configuration found for sensor type: {}", sensorType);
            return;
        }

        history.putIfAbsent(sensorType, new ArrayList<>());
        List<SensorData> dataList = history.get(sensorType);
        dataList.add(data);

        long now = System.currentTimeMillis();
        dataList.removeIf(d -> (now - d.getTimestamp()) > threshold.getTimeThreshold());

        if (dataList.isEmpty()) return;

        long outOfRangeCount = dataList.stream()
                .filter(d -> d.getValue() < threshold.getMin() || d.getValue() > threshold.getMax())
                .count();

        double percentageOut = (100.0 * outOfRangeCount) / dataList.size();

        if (percentageOut >= threshold.getPercentageThreshold()) {
            generateAlert(data, threshold, percentageOut);
        }
    }

    private void generateAlert(SensorData data, SensorThreshold threshold, double percentageOut) {
        LOGGER.warn("ALERT: {} sensor has {}% values out of range in the last {} seconds. Value: {} (Expected: {} - {})",
                data.getSensorName().getSensorType(),
                percentageOut,
                threshold.getTimeThreshold(),
                data.getValue(),
                threshold.getMin(),
                threshold.getMax());

        onAlertGenerated(data);
    }

    private void onAlertGenerated(SensorData data) {
        // TODO: Define alert generation job
    }

}
