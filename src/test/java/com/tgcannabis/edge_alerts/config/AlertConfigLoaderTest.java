package com.tgcannabis.edge_alerts.config;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import com.tgcannabis.edge_alerts.model.SensorThreshold;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AlertConfigLoaderTest {
    private Gson mockGson;

    @BeforeEach
    void setUp() {
        mockGson = mock(Gson.class);
    }

    @Test
    void shouldLoadConfigSuccessfullyFromFile() {
        // Arrange
        Map<String, SensorThreshold> expectedThresholds = new HashMap<>();
        expectedThresholds.put("temperature", new SensorThreshold(10.0, 30.0, 60, 95));
        expectedThresholds.put("humidity", new SensorThreshold(40.0, 70.0, 30, 100));
        expectedThresholds.put("co2", new SensorThreshold(300.0, 800.0, 120, 80));
        expectedThresholds.put("pressure", new SensorThreshold(900.0, 1100.0, 45, 100));

        Type expectedMapType = new TypeToken<Map<String, SensorThreshold>>() {
        }.getType();

        doAnswer(invocation -> {
            InputStreamReader reader = invocation.getArgument(0);
            Type actualType = invocation.getArgument(1);

            assertNotNull(reader, "InputStreamReader should not be null when passed to fromJson");
            assertEquals(expectedMapType, actualType, "The Type object passed to Gson.fromJson should match the expected generic map type.");

            return expectedThresholds;
        }).when(mockGson).fromJson(any(InputStreamReader.class), eq(expectedMapType));


        // Act
        AlertConfigLoader loader = new AlertConfigLoader(mockGson);

        // Assert
        assertNotNull(loader.getThresholdsMap());
        assertEquals(4, loader.getThresholdsMap().size());

        assertEquals(new SensorThreshold(10.0, 30.0, 60, 95), loader.getThresholdsMap().get("temperature"));
        assertEquals(new SensorThreshold(40.0, 70.0, 30, 100), loader.getThresholdsMap().get("humidity"));
        assertEquals(new SensorThreshold(300.0, 800.0, 120, 80), loader.getThresholdsMap().get("co2"));
        assertEquals(new SensorThreshold(900.0, 1100.0, 45, 100), loader.getThresholdsMap().get("pressure"));

        verify(mockGson).fromJson(any(InputStreamReader.class), eq(expectedMapType));
    }

    @Test
    void shouldReturnCorrectThresholdForSensorType() {
        // Arrange
        Map<String, SensorThreshold> mockThresholds = new HashMap<>();
        mockThresholds.put("temperature", new SensorThreshold(10.0, 30.0, 60, 95));
        mockThresholds.put("humidity", new SensorThreshold(40.0, 70.0, 30, 100));

        Type expectedMapType = new TypeToken<Map<String, SensorThreshold>>() {
        }.getType();

        doAnswer(invocation -> mockThresholds)
                .when(mockGson).fromJson(any(InputStreamReader.class), eq(expectedMapType));

        AlertConfigLoader loader = new AlertConfigLoader(mockGson);

        // Act & Assert
        assertEquals(new SensorThreshold(10.0, 30.0, 60, 95), loader.getThreshold("temperature"));
        assertEquals(new SensorThreshold(40.0, 70.0, 30, 100), loader.getThreshold("humidity"));
        assertNull(loader.getThreshold("nonexistent"));

        verify(mockGson).fromJson(any(InputStreamReader.class), eq(expectedMapType));
    }

    @Test
    void getThresholdShouldHandleCaseInsensitivity() {
        // Arrange
        Map<String, SensorThreshold> mockThresholds = new HashMap<>();
        mockThresholds.put("temperature", new SensorThreshold(10.0, 30.0, 60, 95));

        Type expectedMapType = new TypeToken<Map<String, SensorThreshold>>() {
        }.getType();

        doAnswer(invocation -> mockThresholds)
                .when(mockGson).fromJson(any(InputStreamReader.class), eq(expectedMapType));

        AlertConfigLoader loader = new AlertConfigLoader(mockGson);

        // Act & Assert
        assertEquals(new SensorThreshold(10.0, 30.0, 60, 95), loader.getThreshold("TEMPERATURE"));
        assertEquals(new SensorThreshold(10.0, 30.0, 60, 95), loader.getThreshold("Temperature"));
        assertEquals(new SensorThreshold(10.0, 30.0, 60, 95), loader.getThreshold("temperature"));

        verify(mockGson).fromJson(any(InputStreamReader.class), eq(expectedMapType));
    }


    @Test
    void shouldThrowRuntimeExceptionIfConfigFileIsMalformedJson() {
        // Arrange
        // We need to ensure that the getResourceAsStream() call succeeds (returns a real stream)
        // but then the `fromJson` call fails.

        // Define the exact type Gson is expecting
        Type expectedMapType = new TypeToken<Map<String, SensorThreshold>>() {
        }.getType();

        // Stub mockGson.fromJson to throw JsonSyntaxException when called with any InputStreamReader and the correct Type
        doThrow(new JsonSyntaxException("Simulated Malformed JSON"))
                .when(mockGson).fromJson(any(InputStreamReader.class), eq(expectedMapType));

        // Act & Assert
        RuntimeException ex = assertThrows(RuntimeException.class, () -> new AlertConfigLoader(mockGson));

        assertEquals("Failed to load alert configuration", ex.getMessage());
        assertInstanceOf(JsonSyntaxException.class, ex.getCause());
    }
}
