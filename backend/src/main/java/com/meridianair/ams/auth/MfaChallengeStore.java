package com.meridianair.ams.auth;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bridges the two steps of an MFA login: password verified but code not
 * yet entered. The reference implementation this was ported from left
 * this as a TODO ("Store MFA session in Redis (simplified for now)") -
 * this fills that gap with a real, working single-instance store.
 *
 * Single JVM only - fine for now, but note for later: back this with
 * Redis (or any shared cache) before running more than one backend
 * instance, or a challenge issued by instance A won't be visible to
 * instance B when the verify request lands there.
 */
@Component
public class MfaChallengeStore {

    private static final long TTL_SECONDS = 300; // 5 minutes to enter the code

    private record Entry(UUID userId, Instant expiresAt) {}

    private final Map<String, Entry> challenges = new ConcurrentHashMap<>();
    private final SecureRandom secureRandom = new SecureRandom();

    public String issue(UUID userId) {
        String token = randomToken();
        challenges.put(token, new Entry(userId, Instant.now().plusSeconds(TTL_SECONDS)));
        return token;
    }

    /** Consumes the challenge on read - a token is single-use whether the
     * code that follows is right or wrong, so a leaked/observed challenge
     * token can't be replayed against a fresh login attempt. */
    public Optional<UUID> consume(String token) {
        Entry entry = challenges.remove(token);
        if (entry == null || entry.expiresAt().isBefore(Instant.now())) {
            return Optional.empty();
        }
        return Optional.of(entry.userId());
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
