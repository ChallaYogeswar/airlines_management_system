package com.meridianair.ams.auth;

import com.meridianair.ams.audit.AuditService;
import com.meridianair.ams.domain.AuditLog;
import com.meridianair.ams.domain.User;
import com.meridianair.ams.dto.LoginRequest;
import com.meridianair.ams.repository.RoleRepository;
import com.meridianair.ams.repository.UserRepository;
import com.meridianair.ams.security.JwtService;
import com.meridianair.ams.security.SecurityProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;
    @Mock private SessionService sessionService;
    @Mock private MfaService mfaService;
    @Mock private MfaChallengeStore mfaChallengeStore;
    @Mock private AuditService auditService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, roleRepository, passwordEncoder, jwtService,
                sessionService, mfaService, mfaChallengeStore, auditService,
                new SecurityProperties("test-secret", 15, 7, 5, 15));
    }

    @Test
    void invalidPasswordIncrementsFailedAttemptsAndAuditsFailure() {
        User user = new User("passenger@example.com", "Passenger", "encoded-password");
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("incorrect", user.getPasswordHash())).thenReturn(false);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> authService.login(new LoginRequest(user.getEmail(), "incorrect", "browser"),
                        "127.0.0.1", "test-agent"));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        assertEquals(1, user.getFailedLoginAttempts());
        verify(userRepository).save(user);
        verify(auditService).log(any(AuditLog.Builder.class));
    }
}