package com.meridianair.ams.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;

public record UpdateFlightOfferRequest(
        @NotNull @DecimalMin("0.0") BigDecimal price,
        @Min(1) int totalSeats,
        @NotNull Instant departureTime,
        @NotNull Instant arrivalTime
) {}
