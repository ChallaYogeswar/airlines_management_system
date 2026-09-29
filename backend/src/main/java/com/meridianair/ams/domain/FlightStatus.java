package com.meridianair.ams.domain;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Mirrors the Angular FlightStatus union type exactly:
 * 'on-time' | 'delayed' | 'boarding' | 'on-runway' | 'departed'
 * so the frontend doesn't need any mapping layer between what the
 * WebSocket sends and what the templates already expect.
 */
public enum FlightStatus {
    ON_TIME("on-time"),
    DELAYED("delayed"),
    BOARDING("boarding"),
    ON_RUNWAY("on-runway"),
    DEPARTED("departed");

    private final String wireValue;

    FlightStatus(String wireValue) {
        this.wireValue = wireValue;
    }

    @JsonValue
    public String wireValue() {
        return wireValue;
    }
}
