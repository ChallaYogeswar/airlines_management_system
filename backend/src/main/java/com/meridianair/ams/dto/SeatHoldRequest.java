package com.meridianair.ams.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record SeatHoldRequest(@NotEmpty @Size(max = 9) List<UUID> seatIds) {}
