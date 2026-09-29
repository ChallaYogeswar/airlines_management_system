package com.meridianair.ams.dto;

import java.time.Instant;
import java.util.UUID;

public record AuditLogSummary(
        UUID id,
        UUID userId,
        String eventType,
        String eventCategory,
        String action,
        String status,
        String message,
        String ipAddress,
        Instant createdAt
) {}
