package com.tgcannabis.edge_alerts.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SensorData {
    private String sensorType;
    private String location;
    private String sensorId;
    private double value;
    private long timestamp;
}
