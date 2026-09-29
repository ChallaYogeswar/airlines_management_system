package com.meridianair.ams.opensky;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ams.opensky")
public record OpenSkyProperties(
        String baseUrl,
        long pollIntervalMs,
        BoundingBox boundingBox
) {
    /** lat/lon box the radar cares about - defaults to south/central India,
     * covering the Chennai-Hyderabad-Bengaluru triangle. Configurable in
     * application.yml without touching code. */
    public record BoundingBox(double lamin, double lomin, double lamax, double lomax) {}
}
