package com.meridianair.ams.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ams.security")
public record SecurityProperties(
        String jwtSecret,
        long accessTokenTtlMinutes,
        long refreshTokenTtlDays,
        int maxFailedLoginAttempts,
        long lockoutMinutes
) {
}
