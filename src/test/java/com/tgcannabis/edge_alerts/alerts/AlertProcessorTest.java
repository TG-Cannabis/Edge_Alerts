package com.tgcannabis.edge_alerts.alerts;

import com.tgcannabis.edge_alerts.config.AlertConfigLoader;
import com.tgcannabis.edge_alerts.model.SensorData;
import com.tgcannabis.edge_alerts.model.SensorThreshold;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

public class AlertProcessorTest {
    private AlertConfigLoader configLoader;
    private AlertProcessor alertProcessor;

    @BeforeEach
    void setup() {
        configLoader = mock(AlertConfigLoader.class);
        alertProcessor = new AlertProcessor(configLoader);
    }

    @Test
    void constructor_nullConfigLoader_throwsException() {
        NullPointerException ex = assertThrows(NullPointerException.class, () -> new AlertProcessor(null));
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
}
