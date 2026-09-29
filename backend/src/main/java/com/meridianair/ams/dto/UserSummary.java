package com.meridianair.ams.dto;

import java.util.List;
import java.util.UUID;

public record UserSummary(
        UUID id,
        String email,
        String name,
        List<String> roles,
        boolean mfaEnabled
) {}
