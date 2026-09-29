package com.meridianair.ams.auth;

import com.meridianair.ams.audit.AuditService;
import com.meridianair.ams.domain.AuditLog;
import com.meridianair.ams.domain.Role;
import com.meridianair.ams.domain.Session;
import com.meridianair.ams.domain.User;
import com.meridianair.ams.dto.*;
import com.meridianair.ams.repository.RoleRepository;
import com.meridianair.ams.repository.UserRepository;
import com.meridianair.ams.security.JwtService;
import com.meridianair.ams.security.SecurityProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AuthService {

    private static final String DEFAULT_ROLE = "PASSENGER";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final SessionService sessionService;
    private final MfaService mfaService;
    private final MfaChallengeStore mfaChallengeStore;
    private final AuditService auditService;
    private final SecurityProperties securityProperties;

    public AuthService(UserRepository userRepository, RoleRepository roleRepository,
                        PasswordEncoder passwordEncoder, JwtService jwtService,
                        SessionService sessionService, MfaService mfaService,
                        MfaChallengeStore mfaChallengeStore, AuditService auditService,
                        SecurityProperties securityProperties) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.sessionService = sessionService;
        this.mfaService = mfaService;
        this.mfaChallengeStore = mfaChallengeStore;
        this.auditService = auditService;
        this.securityProperties = securityProperties;
    }

    public UserSummary register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists");
        }

        User user = new User(request.email(), request.name(), passwordEncoder.encode(request.password()));
        Role defaultRole = roleRepository.findByName(DEFAULT_ROLE)
                .orElseThrow(() -> new IllegalStateException(
                        "Default role " + DEFAULT_ROLE + " missing - check RoleSeeder ran on startup"));
        user.setRoles(Set.of(defaultRole));

        User saved;
        try {
            saved = userRepository.save(user);
        } catch (DataIntegrityViolationException ex) {
            // race: two concurrent registrations with the same email
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists");
        }

        auditService.log(AuditLog.builder()
                .userId(saved.getId())
                .eventType("user.registered").eventCategory("auth").action("create")
                .resourceType("user").resourceId(saved.getId().toString()).status("success")
                .message("Account created"));

        return toSummary(saved);
    }

    public LoginResponse login(LoginRequest request, String ipAddress, String userAgent) {
        User user = userRepository.findByEmail(request.email()).orElse(null);

        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            if (user != null) {
                recordFailedAttempt(user, ipAddress, userAgent);
            }
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }

        if (user.isLocked()) {
            auditService.log(AuditLog.builder()
                    .userId(user.getId()).eventType("auth.login.blocked").eventCategory("auth")
                    .action("login").status("failure").message("Account temporarily locked")
                    .ipAddress(ipAddress).userAgent(userAgent));
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Account temporarily locked due to repeated failed attempts - try again later");
        }

        // success path resets lockout state
        user.setFailedLoginAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(Instant.now());
        userRepository.save(user);

        if (user.isMfaEnabled()) {
            String challenge = mfaChallengeStore.issue(user.getId());
            auditService.log(AuditLog.builder()
                    .userId(user.getId()).eventType("auth.login.mfa_required").eventCategory("auth")
                    .action("login").status("pending").ipAddress(ipAddress).userAgent(userAgent));
            return LoginResponse.mfaRequired(challenge, toSummary(user));
        }

        return issueTokensAndSession(user, request.deviceName(), userAgent, ipAddress);
    }

    public LoginResponse completeMfaLogin(MfaVerifyRequest request, String deviceName,
                                           String ipAddress, String userAgent) {
        UUID userId = mfaChallengeStore.consume(request.mfaChallenge())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "MFA challenge expired or invalid - please log in again"));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid session"));

        boolean verified = mfaService.verifyTotp(user, request.code())
                || mfaService.verifyAndConsumeBackupCode(user, request.code());

        if (!verified) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid authentication code");
        }

        return issueTokensAndSession(user, deviceName, userAgent, ipAddress);
    }

    public TokenPairResponse refresh(RefreshRequest request) {
        Claims claims;
        try {
            claims = jwtService.parseClaims(request.refreshToken());
        } catch (JwtException | IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired refresh token");
        }
        if (!jwtService.isRefreshToken(claims)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not a refresh token");
        }

        UUID userId = UUID.fromString(claims.getSubject());
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid refresh token"));

        Session session = sessionService.findValidByToken(user, request.refreshToken())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Session not found, expired, or already revoked"));

        String newAccessToken = jwtService.generateAccessToken(user, session.getId());
        String newRefreshToken = jwtService.generateRefreshToken(user);
        // rotates the SAME session's stored hash - see SessionService.rotate() for why
        sessionService.rotate(session, newRefreshToken);

        return new TokenPairResponse(newAccessToken, newRefreshToken);
    }

    public void logout(User user, String refreshToken) {
        sessionService.findValidByToken(user, refreshToken)
                .ifPresent(session -> sessionService.revoke(session, "user_logout"));

        auditService.log(AuditLog.builder()
                .userId(user.getId()).eventType("auth.logout").eventCategory("auth")
                .action("logout").status("success"));
    }

    private LoginResponse issueTokensAndSession(User user, String deviceName, String userAgent, String ipAddress) {
        String refreshToken = jwtService.generateRefreshToken(user);
        Session session = sessionService.create(user, refreshToken, deviceName, userAgent, ipAddress);
        String accessToken = jwtService.generateAccessToken(user, session.getId());

        auditService.log(AuditLog.builder()
                .userId(user.getId()).eventType("auth.login.success").eventCategory("auth")
                .action("login").status("success").ipAddress(ipAddress).userAgent(userAgent));

        return LoginResponse.issued(accessToken, refreshToken, toSummary(user));
    }

    private void recordFailedAttempt(User user, String ipAddress, String userAgent) {
        int attempts = user.getFailedLoginAttempts() + 1;
        user.setFailedLoginAttempts(attempts);
        if (attempts >= securityProperties.maxFailedLoginAttempts()) {
            user.setLockedUntil(Instant.now().plus(securityProperties.lockoutMinutes(), ChronoUnit.MINUTES));
        }
        userRepository.save(user);

        auditService.log(AuditLog.builder()
                .userId(user.getId()).eventType("auth.login.failed").eventCategory("auth")
                .action("login").status("failure").message("Invalid password")
                .ipAddress(ipAddress).userAgent(userAgent));
    }

    private UserSummary toSummary(User user) {
        List<String> roles = user.getRoles().stream().map(Role::getName).collect(Collectors.toList());
        return new UserSummary(user.getId(), user.getEmail(), user.getName(), roles, user.isMfaEnabled());
    }
}
