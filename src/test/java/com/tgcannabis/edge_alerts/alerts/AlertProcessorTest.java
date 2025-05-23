package com.tgcannabis.edge_alerts.alerts;

import com.google.gson.Gson;
import com.tgcannabis.edge_alerts.config.AlertConfigLoader;
import com.tgcannabis.edge_alerts.model.SensorData;
import com.tgcannabis.edge_alerts.model.SensorThreshold;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AlertProcessorTest {
    private AlertConfigLoader configLoader;
    private AlertProcessor alertProcessor;
    private MqttClient mockedClient;

    @BeforeEach
    void setup() {
        configLoader = mock(AlertConfigLoader.class);
        mockedClient = mock(MqttClient.class);
        alertProcessor = new AlertProcessor(configLoader, mockedClient);
    }

    @Test
    void constructor_nullMqttClient_throwsException() {
        NullPointerException ex = assertThrows(NullPointerException.class, () -> new AlertProcessor(configLoader, null));
        assertEquals("MQTT client cannot be null", ex.getMessage());
    }

    @Test
    void constructor_nullConfigLoader_throwsException() {
        NullPointerException ex = assertThrows(NullPointerException.class, () -> new AlertProcessor(null, mockedClient));
        assertEquals("Alert config loader cannot be null", ex.getMessage());
    }

    @Test
    void accept_invalidJson_logsError() {
        String invalidJson = "not a json";

        assertDoesNotThrow(() -> alertProcessor.accept("some/topic", invalidJson));
    }

    @Test
    void accept_nullSensorData_doesNotProcess() {
        String payload = "{\"sensorId\":null}";

        assertDoesNotThrow(() -> alertProcessor.accept("topic", payload));

        verifyNoInteractions(configLoader);
    }

    @Test
    void accept_validSensorData_noThresholdConfig_logsWarning() {
        SensorData data = new SensorData();
        data.setSensorId("123");
        data.setSensorType("Temperature");
        data.setTimestamp(Instant.now().getEpochSecond());
        data.setValue(25.0);

        String json = new com.google.gson.Gson().toJson(data);

        when(configLoader.getThreshold("temperature")).thenReturn(null);

        assertDoesNotThrow(() -> alertProcessor.accept("topic", json));

        verify(configLoader, times(1)).getThreshold("temperature");
    }

    @Test
    void accept_validSensorData_timeThresholdNotMet_doesNotGenerateAlert() {
        long now = Instant.now().getEpochSecond();

        SensorThreshold threshold = new SensorThreshold();
        threshold.setMin(10.0);
        threshold.setMax(30.0);
        threshold.setPercentageThreshold(50);
        threshold.setTimeThreshold(60); // 60 seconds

        SensorData data = new SensorData();
        data.setSensorId("123");
        data.setSensorType("Humidity");
        data.setTimestamp(now);
        data.setValue(40.0); // out of range (above max)

        String json = new com.google.gson.Gson().toJson(data);

        when(configLoader.getThreshold("humidity")).thenReturn(threshold);

        assertDoesNotThrow(() -> alertProcessor.accept("topic", json));

        // Since time threshold is not met, firstEvaluationTime should be set but no alert triggered
        assertTrue(alertProcessor.firstEvaluationTime.containsKey("humidity"));
        assertTrue(alertProcessor.history.containsKey("humidity"));
        assertEquals(1, alertProcessor.history.get("humidity").size());
    }

    @Test
    void accept_validSensorData_timeThresholdMet_generatesAlert() {
        long now = Instant.now().getEpochSecond();

        SensorThreshold threshold = new SensorThreshold();
        threshold.setMin(10.0);
        threshold.setMax(30.0);
        threshold.setPercentageThreshold(50);
        threshold.setTimeThreshold(1); // 1 second for quick test

        SensorData inRange = new SensorData();
        inRange.setSensorId("id1");
        inRange.setSensorType("pressure");
        inRange.setTimestamp(now - 2); // older but within threshold window
        inRange.setValue(20.0); // in range

        SensorData outOfRange = new SensorData();
        outOfRange.setSensorId("id2");
        outOfRange.setSensorType("pressure");
        outOfRange.setTimestamp(now - 1);
        outOfRange.setValue(50.0); // out of range (above max)

        String jsonOutOfRange = new com.google.gson.Gson().toJson(outOfRange);
        String jsonInRange = new com.google.gson.Gson().toJson(inRange);

        when(configLoader.getThreshold("pressure")).thenReturn(threshold);

        assertDoesNotThrow(() -> alertProcessor.accept("topic", jsonInRange));

        // Manually set firstEvaluationTime in past to simulate elapsed time for alert evaluation
        alertProcessor.firstEvaluationTime.put("pressure", now - 10);

        assertDoesNotThrow(() -> alertProcessor.accept("topic", jsonOutOfRange));

        assertFalse(alertProcessor.history.get("pressure").isEmpty());

        // After generating alert, firstEvaluationTime should be updated/reset
        Long firstEvalTime = alertProcessor.firstEvaluationTime.get("pressure");
        assertNotNull(firstEvalTime);
        assertTrue(firstEvalTime >= now - 1, "First evaluation time should be recent after alert generation");
    }

    @Test
    void onAlertGenerated_validData_publishesToMqtt() throws MqttException {
        SensorThreshold threshold = new SensorThreshold();
        threshold.setMin(10);
        threshold.setMax(30);
        threshold.setTimeThreshold(60);
        threshold.setPercentageThreshold(50); // Assume alert triggers if >50% out of range

        when(configLoader.getThreshold("temperature")).thenReturn(threshold);

        long now = Instant.now().getEpochSecond();
        String sensorType = "temperature";

        // Simulate 5 historical readings
        for (int i = 0; i < 5; i++) {
            SensorData historyData = new SensorData();
            historyData.setSensorId("sensor123");
            historyData.setSensorType(sensorType);
            historyData.setValue(35.0); // out of range
            historyData.setTimestamp(now - 30 + i); // within threshold window
            alertProcessor.history.computeIfAbsent(sensorType, k -> new ArrayList<>()).add(historyData);
        }

        alertProcessor.firstEvaluationTime.put(sensorType, now - 120); // simulate threshold duration has passed

        SensorData newData = new SensorData();
        newData.setSensorId("sensor123");
        newData.setSensorType(sensorType);
        newData.setValue(35.0);
        newData.setTimestamp(now); // new reading

        alertProcessor.accept("topic", new Gson().toJson(newData));

        verify(mockedClient, atLeastOnce()).publish(eq("alerts"), any(MqttMessage.class));
    }

    @Test
    void onAlertGenerated_mqttMessageContainsCorrectJson() throws Exception {
        SensorThreshold threshold = new SensorThreshold();
        threshold.setMin(20);
        threshold.setMax(80);
        threshold.setTimeThreshold(30);
        threshold.setPercentageThreshold(50); // assume >50% triggers alert

        when(configLoader.getThreshold("humidity")).thenReturn(threshold);

        String sensorType = "humidity";
        long now = Instant.now().getEpochSecond();

        // Simulate 5 previous out-of-range readings
        for (int i = 0; i < 5; i++) {
            SensorData historicalData = new SensorData();
            historicalData.setSensorId("sensorX");
            historicalData.setSensorType(sensorType);
            historicalData.setValue(5.0); // too low
            historicalData.setTimestamp(now - 20 + i); // within 30-second window
            alertProcessor.history.computeIfAbsent(sensorType, k -> new ArrayList<>()).add(historicalData);
        }

        alertProcessor.firstEvaluationTime.put(sensorType, now - 60); // simulate that enough time has passed

        // Now send the current reading that will also be out of range
        SensorData newData = new SensorData();
        newData.setSensorId("sensorX");
        newData.setSensorType(sensorType);
        newData.setValue(5.0); // still too low
        newData.setTimestamp(now);

        alertProcessor.accept("topic", new Gson().toJson(newData));

        // Capture the MQTT message
        ArgumentCaptor<MqttMessage> captor = ArgumentCaptor.forClass(MqttMessage.class);
        verify(mockedClient).publish(eq("alerts"), captor.capture());

        String payload = new String(captor.getValue().getPayload());

        // Verify the contents of the alert JSON
        assertTrue(payload.contains("\"sensorType\":\"humidity\""));
        assertTrue(payload.contains("\"alertType\":\"too low\""));
        assertTrue(payload.contains("humidity has been too low"));
    }
}
