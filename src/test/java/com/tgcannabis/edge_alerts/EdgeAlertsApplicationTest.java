package com.tgcannabis.edge_alerts;

import com.tgcannabis.edge_alerts.alerts.AlertProcessor;
import com.tgcannabis.edge_alerts.config.AlertConfigLoader;
import com.tgcannabis.edge_alerts.mqtt.MqttService;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

public class EdgeAlertsApplicationTest {
    @InjectMocks
    EdgeAlertsApplication app;

    @Mock
    MqttService mockMqttService;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void start_successfulFlow_callsConnectAndSetsHandler() {
        try (MockedConstruction<AlertConfigLoader> ignored1 = mockConstruction(AlertConfigLoader.class);
             MockedConstruction<AlertProcessor> ignored2 = mockConstruction(AlertProcessor.class);
             MockedConstruction<MqttService> mockedMqtt = mockConstruction(MqttService.class,
                     (mock, context) -> {
                         doNothing().when(mock).setMessageHandler(any());
                         doNothing().when(mock).connect();
                     })) {

            // spy the app to override addShutdownHook (avoid actually adding it)
            EdgeAlertsApplication spyApp = spy(new EdgeAlertsApplication());
            doNothing().when(spyApp).addShutdownHook();

            assertDoesNotThrow(spyApp::start);

            // verify MqttService constructor was called
            assertEquals(1, mockedMqtt.constructed().size());

            MqttService constructedMqtt = mockedMqtt.constructed().get(0);
            verify(constructedMqtt).setMessageHandler(any(AlertProcessor.class));
            verify(constructedMqtt).connect();

            verify(spyApp, times(1)).addShutdownHook();
        } catch (MqttException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void start_whenExceptionInMqttConnect_logsError() {
        try (MockedConstruction<AlertConfigLoader> ignored1 = mockConstruction(AlertConfigLoader.class);
             MockedConstruction<AlertProcessor> ignored2 = mockConstruction(AlertProcessor.class);
             MockedConstruction<MqttService> mockedMqtt = mockConstruction(MqttService.class,
                     (mock, context) -> {
                         doThrow(new RuntimeException("Connect failed")).when(mock).connect();
                     })) {

            EdgeAlertsApplication spyApp = spy(new EdgeAlertsApplication());
            doNothing().when(spyApp).addShutdownHook();

            assertDoesNotThrow(spyApp::start);

            verify(spyApp, times(1)).addShutdownHook();
        }
    }

    @Test
    void shutdown_closesMqttService() throws Exception {
        var mqttServiceField = EdgeAlertsApplication.class.getDeclaredField("mqttService");
        mqttServiceField.setAccessible(true);
        mqttServiceField.set(null, mockMqttService);

        doNothing().when(mockMqttService).close();

        assertDoesNotThrow(() -> app.shutdown());

        verify(mockMqttService, times(1)).close();

        mqttServiceField.set(null, null);
    }

    @Test
    void shutdown_handlesExceptionDuringMqttClose() throws Exception {
        var mqttServiceField = EdgeAlertsApplication.class.getDeclaredField("mqttService");
        mqttServiceField.setAccessible(true);
        mqttServiceField.set(null, mockMqttService);

        doThrow(new RuntimeException("close failure")).when(mockMqttService).close();

        assertDoesNotThrow(() -> app.shutdown());

        verify(mockMqttService, times(1)).close();

        mqttServiceField.set(null, null);
    }
}
