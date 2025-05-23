package com.tgcannabis.edge_alerts.mqtt;

import com.tgcannabis.edge_alerts.config.EdgeAlertConfig;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;

import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class MqttServiceTest {
    private EdgeAlertConfig mockConfig;
    private MqttService mqttService;
    private BiConsumer<String, String> mockHandler;

    @BeforeEach
    void setUp() {
        mockConfig = mock(EdgeAlertConfig.class);
        mockHandler = mock(BiConsumer.class);
    }

    @Test
    void connect_shouldSucceed_whenValidConfigurationAndHandlerSet() throws Exception {
        when(mockConfig.getMqttBroker()).thenReturn("tcp://localhost:1883");
        when(mockConfig.getMqttClientId()).thenReturn("test-client");
        when(mockConfig.getMqttTopic()).thenReturn("test/topic");

        try (MockedConstruction<MqttClient> mockedClient = mockConstruction(MqttClient.class,
                (mock, context) -> {
                    when(mock.isConnected()).thenReturn(true);
                })) {

            mqttService = new MqttService(mockConfig);
            mqttService.setMessageHandler(mockHandler);
            mqttService.connect();

            MqttClient mockMqttClient = mockedClient.constructed().get(0);
            verify(mockMqttClient).setCallback(any(MqttCallback.class));
            verify(mockMqttClient).connect();
        }
    }

    @Test
    void connect_shouldThrowException_whenHandlerNotSet() {
        mqttService = new MqttService(mockConfig);
        assertThrows(NullPointerException.class, () -> mqttService.connect());
    }

    @Test
    void connect_shouldLogAndRethrowException_whenMqttConnectFails() {
        when(mockConfig.getMqttBroker()).thenReturn("tcp://localhost:1883");
        when(mockConfig.getMqttClientId()).thenReturn("test-client");
        when(mockConfig.getMqttTopic()).thenReturn("test/topic");

        try (MockedConstruction<MqttClient> mockedClient = mockConstruction(MqttClient.class,
                (mock, context) -> {
                    doThrow(new MqttException(1)).when(mock).connect();
                })) {

            mqttService = new MqttService(mockConfig);
            mqttService.setMessageHandler(mockHandler);

            assertThrows(MqttException.class, () -> mqttService.connect());
        }
    }

    @Test
    void close_shouldDisconnectAndClose_whenConnected() throws Exception {
        when(mockConfig.getMqttBroker()).thenReturn("tcp://localhost:1883");
        when(mockConfig.getMqttClientId()).thenReturn("test-client");
        when(mockConfig.getMqttTopic()).thenReturn("test/topic");

        try (MockedConstruction<MqttClient> mockedClient = mockConstruction(MqttClient.class,
                (mock, context) -> {
                    when(mock.isConnected()).thenReturn(true);
                })) {

            mqttService = new MqttService(mockConfig);
            mqttService.setMessageHandler(mockHandler);
            mqttService.connect();

            MqttClient mockClient = mockedClient.constructed().get(0);
            mqttService.close();

            verify(mockClient).disconnect();
            verify(mockClient).close();
        }
    }

    @Test
    void close_shouldOnlyClose_whenNotConnected() throws Exception {
        when(mockConfig.getMqttBroker()).thenReturn("tcp://localhost:1883");
        when(mockConfig.getMqttClientId()).thenReturn("test-client");
        when(mockConfig.getMqttTopic()).thenReturn("test/topic");

        try (MockedConstruction<MqttClient> mockedClient = mockConstruction(MqttClient.class,
                (mock, context) -> {
                    when(mock.isConnected()).thenReturn(false);
                })) {

            mqttService = new MqttService(mockConfig);
            mqttService.setMessageHandler(mockHandler);
            mqttService.connect();

            MqttClient mockClient = mockedClient.constructed().get(0);
            mqttService.close();

            verify(mockClient, never()).disconnect();
            verify(mockClient).close();
        }
    }
}
