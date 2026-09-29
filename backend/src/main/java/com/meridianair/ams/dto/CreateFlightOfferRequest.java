package com.meridianair.ams.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;

public record CreateFlightOfferRequest(
        @NotBlank String flightNumber,
        @NotBlank @Size(min = 3, max = 3) String origin,
        @NotBlank @Size(min = 3, max = 3) String destination,
        @NotNull Instant departureTime,
        @NotNull Instant arrivalTime,
        @NotNull @DecimalMin("0.0") BigDecimal price,
        @Min(1) int totalSeats
) {}
