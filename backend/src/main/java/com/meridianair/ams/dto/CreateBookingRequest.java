package com.meridianair.ams.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CreateBookingRequest(
        @NotNull UUID flightOfferId,
        @NotEmpty @Size(max = 9) @Valid List<PassengerDetailRequest> passengers
) {}
