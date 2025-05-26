package com.tgcannabis.edge_alerts.alerts;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.tgcannabis.edge_alerts.config.AlertConfigLoader;
import com.tgcannabis.edge_alerts.model.AlertMessage;
import com.tgcannabis.edge_alerts.model.SensorData;
import com.tgcannabis.edge_alerts.model.SensorThreshold;
import lombok.Setter;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * The {@code AlertProcessor} class is responsible for processing sensor data received via MQTT,
 * detecting threshold breaches based on predefined alert configurations, and triggering alerts when necessary.
 * It maintains a history of sensor readings and determines if a significant percentage of values
 * have exceeded the defined limits over a given period of time.
 */
public class AlertProcessor implements BiConsumer<String, String> {

    private static final Logger LOGGER = LoggerFactory.getLogger(AlertProcessor.class);
    private static final Gson gson = new Gson();

    private final AlertConfigLoader configLoader;

    @Setter
    private MqttClient mqttClient;

    final Map<String, List<SensorData>> history = new ConcurrentHashMap<>();
    final Map<String, Long> firstEvaluationTime = new ConcurrentHashMap<>(); // Track first sensor data time

    /**
     * Constructs an {@code AlertProcessor} with a specified configuration loader for sensor thresholds.
     *
     * @param configLoader The loader responsible for fetching alert thresholds from a configuration file.
     * @throws NullPointerException if {@code configLoader} is {@code null}.
     */
    public AlertProcessor(AlertConfigLoader configLoader, MqttClient mqttClient) {
        this.configLoader = Objects.requireNonNull(configLoader, "Alert config loader cannot be null");
        this.mqttClient = Objects.requireNonNull(mqttClient, "MQTT client cannot be null");
    }

    /**
     * Constructs an {@code AlertProcessor} with a specified configuration loader for sensor thresholds.
     *
     * @param configLoader The loader responsible for fetching alert thresholds from a configuration file.
     * @throws NullPointerException if {@code configLoader} is {@code null}.
     */
    public AlertProcessor(AlertConfigLoader configLoader) {
        this.configLoader = Objects.requireNonNull(configLoader, "Alert config loader cannot be null");
    }

    /**
     * Processes an incoming MQTT message by parsing sensor data and checking for threshold violations.
     *
     * @param topic   The MQTT topic from which the message was received.
     * @param payload The JSON payload containing the sensor data.
     */
    @Override
    public void accept(String topic, String payload) {
        LOGGER.debug("Processing sensor data for alert detection - Topic: [{}], Payload: [{}]", topic, payload);

        try {
            SensorData sensorData = gson.fromJson(payload, SensorData.class);

            if (sensorData == null || sensorData.getSensorId() == null) {
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

    /**
     * Analyzes the received sensor data to determine if an alert should be generated.
     *
     * @param data The sensor data to be evaluated.
     */
    private void checkForAlert(SensorData data) {
        // Extract the sensor type from the incoming data and its given threshold configuration
        String sensorType = data.getSensorType().toLowerCase();
        SensorThreshold threshold = configLoader.getThreshold(sensorType);

        if (threshold == null) {
            LOGGER.warn("No alert configuration found for sensor type: {}", sensorType);
            return; // Skip processing if no threshold is defined
        }

        // Maintain the history of sensor readings for given sensor type
        history.putIfAbsent(sensorType, new ArrayList<>());
        List<SensorData> dataList = history.get(sensorType);
        dataList.add(data);

        // Remove old sensor readings based on threshold time
        long now = Instant.now().getEpochSecond();
        dataList.removeIf(d -> (now - d.getTimestamp()) > threshold.getTimeThreshold());

        // Record first evaluation time for this sensor type (if not already set)
        firstEvaluationTime.putIfAbsent(sensorType, now);

        // Ensure that at least 'timeThreshold' seconds have passed since first data was received
        long firstTime = firstEvaluationTime.get(sensorType);
        if ((now - firstTime) < threshold.getTimeThreshold()) {
            LOGGER.info("Waiting for full time threshold before evaluating alerts for sensor: {}", sensorType);
            return;
        }

        // Calculate the percentage of readings that are out of range
        long outOfRangeCount = dataList.stream()
                .filter(d -> d.getValue() < threshold.getMin() || d.getValue() > threshold.getMax())
                .count();
        double percentageOut = (100.0 * outOfRangeCount) / dataList.size();

        // Trigger the alert if the percentage exceeds the configured threshold
        if (percentageOut >= threshold.getPercentageThreshold()) {
            generateAlert(data, threshold, percentageOut);
            firstEvaluationTime.put(sensorType, now); // Reset first evaluation time after generating an alert
        }
    }

    /**
     * Generates an alert when a sensor's values exceed the configured threshold.
     *
     * @param data          The sensor data that triggered the alert.
     * @param threshold     The predefined threshold configuration.
     * @param percentageOut The percentage of out-of-range values over the configured period.
     */
    private void generateAlert(SensorData data, SensorThreshold threshold, double percentageOut) {
        LOGGER.warn("ALERT: {} sensor has {}% values out of range in the last {} seconds. Value: {} (Expected: {} - {})",
                data.getSensorType(),
                percentageOut,
                threshold.getTimeThreshold(),
                data.getValue(),
                threshold.getMin(),
                threshold.getMax());

        onAlertGenerated(data);
    }

    /**
     * Placeholder method for handling alert notifications
     * This method should be implemented to define the alert handling mechanism.
     *
     * @param data The sensor data that triggered the alert.
     */
    private void onAlertGenerated(SensorData data) {
        SensorThreshold threshold = configLoader.getThreshold(data.getSensorType().toLowerCase());
        if (threshold == null) return;

        double value = data.getValue();
        String alertType = (value > threshold.getMax()) ? "TOO_HIGH" : "TOO_LOW";
        long duration = threshold.getTimeThreshold();

        String message = String.format(
                "%s has been %s for the last %d seconds",
                data.getSensorType(), alertType, duration
        );

        AlertMessage alert = new AlertMessage(
                data.getSensorType(),
                value,
                alertType,
                duration,
                message
        );

        String json = gson.toJson(alert);

        try {
            mqttClient.publish("alerts", new MqttMessage(json.getBytes()));
            LOGGER.info("Published alert to MQTT topic [alerts]: {}", json);
        } catch (MqttException e) {
            LOGGER.error("Failed to publish alert message to MQTT", e);
        }
    }

}
