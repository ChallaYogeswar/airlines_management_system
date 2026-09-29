package com.meridianair.ams.auth;

import com.meridianair.ams.domain.Session;
import com.meridianair.ams.domain.User;
import com.meridianair.ams.repository.SessionRepository;
import com.meridianair.ams.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Owns the Session table: one row per issued refresh token, hashed at
 * rest (Argon2 - the token itself is a bearer credential, same security
 * class as a password). Access tokens are never persisted anywhere -
 * they're stateless and self-expiring, so there's nothing to revoke
 * mid-flight beyond waiting out their short TTL.
 */
@Service
public class SessionService {

    private final SessionRepository sessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public SessionService(SessionRepository sessionRepository, PasswordEncoder passwordEncoder,
                           JwtService jwtService) {
        this.sessionRepository = sessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public Session create(User user, String refreshToken, String deviceName, String userAgent, String ipAddress) {
        Session session = new Session(
                user,
                passwordEncoder.encode(refreshToken),
                deviceName,
                userAgent,
                ipAddress,
                jwtService.refreshTokenExpiry()
        );
        return sessionRepository.save(session);
    }

    /** Finds the session matching this raw refresh token for this user.
     * Argon2 hashes can't be looked up by equality in SQL, so this scans
     * the user's active sessions and checks each hash - fine at the
     * per-user session counts a real app has, and avoids ever storing
     * (or needing to store) the raw token anywhere queryable. */
    public Optional<Session> findValidByToken(User user, String rawRefreshToken) {
        return sessionRepository.findByUserAndRevokedFalse(user).stream()
                .filter(Session::isValid)
                .filter(s -> passwordEncoder.matches(rawRefreshToken, s.getRefreshTokenHash()))
                .findFirst();
    }

    /** Rotates the given session onto a new refresh token instead of
     * minting a fresh session row - same session identity, same device
     * record, just a new credential. This is the fix for the reference
     * implementation's gap: it issued a new refresh token on /refresh but
     * never updated the stored hash, so the old session became unusable
     * on the very next refresh. */
    public void rotate(Session session, String newRefreshToken) {
        session.setRefreshTokenHash(passwordEncoder.encode(newRefreshToken));
        session.setExpiresAt(jwtService.refreshTokenExpiry());
        session.touchActivity();
        sessionRepository.save(session);
    }

    public void revoke(Session session, String reason) {
        session.revoke(reason);
        sessionRepository.save(session);
    }

    public List<Session> listActive(User user) {
        return sessionRepository.findByUserAndRevokedFalse(user);
    }

    public void revokeAllExcept(User user, UUID keepSessionId, String reason) {
        for (Session s : sessionRepository.findByUserAndRevokedFalse(user)) {
            if (!s.getId().equals(keepSessionId)) {
                s.revoke(reason);
                sessionRepository.save(s);
            }
        }
    }
}
