package com.meridianair.ams.dto;

import java.time.Instant;
import java.util.List;

public record SeatHoldResponse(List<SeatSummary> heldSeats, Instant holdExpiresAt) {}
