package com.tgcannabis.edge_alerts.mqtt;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.function.BiConsumer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.*;

class MqttCustomCallbackTest {
    private MqttClient mockClient;
    private BiConsumer<String, String> mockHandler;
    private MqttCustomCallback callback;

    @BeforeEach
    void setUp() {
        mockClient = mock(MqttClient.class);
        mockHandler = mock(BiConsumer.class);
        callback = new MqttCustomCallback(mockClient, "test/topic", mockHandler);
    }

    @Test
    void testConnectComplete_shouldSubscribeIfConnected() throws MqttException {
        when(mockClient.isConnected()).thenReturn(true);

        callback.connectComplete(false, "tcp://broker");

        verify(mockClient).subscribe("test/topic", 1);
    }

    @Test
    void testConnectComplete_shouldNotSubscribeIfNotConnected() throws MqttException {
        when(mockClient.isConnected()).thenReturn(false);

        callback.connectComplete(true, "tcp://broker");

        verify(mockClient, never()).subscribe(anyString(), anyInt());
    }

    @Test
    void testMessageArrived_shouldCallHandler() throws Exception {
        MqttMessage message = new MqttMessage("hello".getBytes());

        assertDoesNotThrow(() -> callback.messageArrived("test/topic", message));

        verify(mockHandler, times(1)).accept("test/topic", "hello");
    }


    @Test
    void testMessageArrived_shouldNotFailIfHandlerNull() throws Exception {
        callback = new MqttCustomCallback(mockClient, "test/topic", null);
        MqttMessage message = new MqttMessage("payload".getBytes());

        assertDoesNotThrow(() -> callback.messageArrived("test/topic", message));
    }

    @Test
    void testMessageArrived_shouldLogErrorIfHandlerThrows() throws Exception {
        doThrow(new RuntimeException("Handler error"))
                .when(mockHandler).accept(anyString(), anyString());

        MqttMessage message = new MqttMessage("bad".getBytes());

        assertDoesNotThrow(() -> callback.messageArrived("test/topic", message));

        verify(mockHandler, times(1)).accept("test/topic", "bad");
    }

    @Test
    void testDeliveryComplete_shouldDoNothing() {
        IMqttDeliveryToken token = mock(IMqttDeliveryToken.class);
        assertDoesNotThrow(() -> callback.deliveryComplete(token));
    }
}
