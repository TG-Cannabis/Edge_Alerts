package com.tgcannabis.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SensorData {
    private SensorInformation sensorName;
    private double value;
    private long timestamp;
}
