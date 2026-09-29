package com.meridianair.ams.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminUserSummary(
        UUID id,
        String email,
        String name,
        List<String> roles,
        boolean active,
        boolean mfaEnabled,
        Instant createdAt,
        Instant lastLoginAt
) {}
