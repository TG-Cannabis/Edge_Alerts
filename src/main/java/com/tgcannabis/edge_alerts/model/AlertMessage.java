package com.tgcannabis.edge_alerts.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AlertMessage {
    private String sensorType;
    private double currentValue;
    private String alertType;
    private long durationSeconds;
    private String message;
}
