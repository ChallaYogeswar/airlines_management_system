package com.meridianair.ams.dto;

import com.meridianair.ams.domain.BookingStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record BookingSummary(
        UUID id,
        String bookingReference,
        String flightNumber,
        String origin,
        String destination,
        Instant departureTime,
        Instant arrivalTime,
        BookingStatus status,
        BigDecimal totalPrice,
        List<BookedPassenger> passengers,
        Instant createdAt
) {}
