package com.tgcannabis.edge_alerts;

import com.tgcannabis.edge_alerts.alerts.AlertProcessor;
import com.tgcannabis.edge_alerts.config.AlertConfigLoader;
import com.tgcannabis.edge_alerts.config.EdgeAlertConfig;
import com.tgcannabis.edge_alerts.mqtt.MqttService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The EdgeAlertsApplication class is the entry point for the Edge Alerts monitoring system.
 * It initializes the MQTT service, alert processing, and configuration loading to monitor
 * sensor data and trigger alerts when thresholds are exceeded.
 */
public class EdgeAlertsApplication {
    private static final Logger LOGGER = LoggerFactory.getLogger(EdgeAlertsApplication.class);
    private static MqttService mqttService;

    /**
     * Starts the Edge Alerts application.
     * - Loads the alert configuration.
     * - Initializes the alert processor.
     * - Sets up the MQTT service and connects to the broker.
     * - Registers a shutdown hook for graceful termination.
     */
    void start() {
        try (MqttService service = new MqttService(new EdgeAlertConfig())) {
            mqttService = service;
            AlertConfigLoader configLoader = new AlertConfigLoader();
            AlertProcessor alertProcessor = new AlertProcessor(configLoader, mqttService.getMqttClient());
            service.setMessageHandler(alertProcessor);
            service.connect();
            LOGGER.info("Edge Alerts Application started successfully and is now monitoring sensor data...");
        } catch (Exception e) {
            LOGGER.error("FATAL: Application failed to start", e);
        } finally {
            addShutdownHook();
        }
    }

    /**
     * Registers a JVM shutdown hook to gracefully close resources.
     */
    void addShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LOGGER.info("Shutdown hook triggered. Cleaning up resources...");
            this.shutdown();
            LOGGER.info("Cleanup finished.");
        }));
    }

    /**
     * Gracefully shuts down all services.
     */
    public void shutdown() {
        LOGGER.info("Shutting down Edge Alerts Application...");
        // Close in reverse order of dependency or where it makes sense
        if (mqttService != null) {
            try {
                mqttService.close();
            } catch (Exception e) {
                LOGGER.error("Error closing MQTT Service", e);
            }
        }
        LOGGER.info("Batch Processor Application shut down complete.");
    }

    public static void main(String[] args) {
        EdgeAlertsApplication app = new EdgeAlertsApplication();
        app.start();
    }
}
