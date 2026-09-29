package com.meridianair.ams.dto;

import java.time.Instant;
import java.util.UUID;

public record SessionSummary(
        UUID id,
        String deviceName,
        String ipAddress,
        Instant createdAt,
        Instant lastActivityAt,
        boolean current
) {}
