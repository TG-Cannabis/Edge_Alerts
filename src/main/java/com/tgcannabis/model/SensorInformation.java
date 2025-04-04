package com.tgcannabis.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SensorInformation {
    String sensorType;
    String location;
    String id;
}