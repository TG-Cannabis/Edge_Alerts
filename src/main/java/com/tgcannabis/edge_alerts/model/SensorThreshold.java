package com.tgcannabis.edge_alerts.model;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class SensorThreshold {
    private double min;
    private double max;
    private int timeThreshold;

    // Ignoring for default value since it can be loaded from JSON config file in AlertConfigLoader
    @SuppressWarnings("FieldMayBeFinal")
    private int percentageThreshold = 100;
}
