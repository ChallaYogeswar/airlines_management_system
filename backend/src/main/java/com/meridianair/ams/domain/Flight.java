package com.meridianair.ams.domain;

import java.time.Instant;

/**
 * Immutable by design, same as the Angular Flight model. Status changes
 * produce a new record via withStatus() rather than mutating in place -
 * makes the engine's tick() trivially thread-safe when paired with a
 * ConcurrentHashMap of id -> Flight.
 */
public record Flight(
        String id,
        String time,
        String flightNumber,
        String destination,
        String gate,
        FlightStatus status,
        Integer delayMinutes,
        Instant updatedAt
) {
    public Flight withStatus(FlightStatus newStatus, Integer newDelayMinutes) {
        return new Flight(id, time, flightNumber, destination, gate, newStatus, newDelayMinutes, Instant.now());
    }
}
