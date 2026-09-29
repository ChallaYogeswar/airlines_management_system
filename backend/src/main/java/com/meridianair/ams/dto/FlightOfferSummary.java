package com.meridianair.ams.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record FlightOfferSummary(
        UUID id,
        String flightNumber,
        String origin,
        String destination,
        Instant departureTime,
        Instant arrivalTime,
        BigDecimal price,
        int seatsAvailable
) {}
