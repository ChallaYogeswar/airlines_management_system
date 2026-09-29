package com.meridianair.ams.security;

import com.meridianair.ams.domain.Role;
import com.meridianair.ams.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Access tokens carry roles so the JWT filter can populate Spring
 * Security's authorities without a database round trip on every request.
 * Refresh tokens intentionally carry nothing but the subject and a
 * "type" claim - their only job is to prove identity long enough to mint
 * a new access token, and SessionService independently verifies them
 * against a stored hash before that's allowed to happen.
 */
@Component
public class JwtService {

    private static final String CLAIM_TYPE = "type";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";
    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_ROLES = "roles";
    private static final String CLAIM_SESSION_ID = "sid";

    private final SecretKey key;
    private final SecurityProperties properties;

    public JwtService(SecurityProperties properties) {
        this.properties = properties;
        byte[] secretBytes = properties.jwtSecret().getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException(
                    "ams.security.jwt-secret must be at least 32 bytes (256 bits) for HS256");
        }
        this.key = Keys.hmacShaKeyFor(secretBytes);
    }

    public String generateAccessToken(User user, java.util.UUID sessionId) {
        List<String> roleNames = user.getRoles().stream().map(Role::getName).collect(Collectors.toList());
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim(CLAIM_EMAIL, user.getEmail())
                .claim(CLAIM_ROLES, roleNames)
                .claim(CLAIM_TYPE, TYPE_ACCESS)
                .claim(CLAIM_SESSION_ID, sessionId.toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.accessTokenTtlMinutes(), ChronoUnit.MINUTES)))
                .signWith(key)
                .compact();
    }

    public String generateRefreshToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim(CLAIM_TYPE, TYPE_REFRESH)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.refreshTokenTtlDays(), ChronoUnit.DAYS)))
                .signWith(key)
                .compact();
    }

    public Instant refreshTokenExpiry() {
        return Instant.now().plus(properties.refreshTokenTtlDays(), ChronoUnit.DAYS);
    }

    /** Throws JwtException (expired, malformed, bad signature) rather than
     * returning null - callers are expected to catch and translate to a
     * 401, never to treat a missing return value as "not logged in". */
    public Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public boolean isAccessToken(Claims claims) {
        return TYPE_ACCESS.equals(claims.get(CLAIM_TYPE, String.class));
    }

    public boolean isRefreshToken(Claims claims) {
        return TYPE_REFRESH.equals(claims.get(CLAIM_TYPE, String.class));
    }

    @SuppressWarnings("unchecked")
    public List<String> rolesFrom(Claims claims) {
        Object roles = claims.get(CLAIM_ROLES);
        return roles instanceof List ? (List<String>) roles : List.of();
    }

    public String sessionIdFrom(Claims claims) {
        return claims.get(CLAIM_SESSION_ID, String.class);
    }
}
