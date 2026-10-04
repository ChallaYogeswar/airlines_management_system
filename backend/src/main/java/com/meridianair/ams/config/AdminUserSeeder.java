package com.meridianair.ams.config;

import com.meridianair.ams.domain.Role;
import com.meridianair.ams.domain.User;
import com.meridianair.ams.repository.RoleRepository;
import com.meridianair.ams.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Creates the first ADMIN account only when one does not already exist.
 *
 * In production, a bootstrap password must be explicitly supplied through
 * AMS_ADMIN_PASSWORD; silently generating a password and printing it to logs
 * is not acceptable for a production deployment.
 */
@Component
@Order(3)
public class AdminUserSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminUserSeeder.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment;

    @Value("${ams.admin.bootstrap-email:admin@meridianair.example}")
    private String bootstrapEmail;

    @Value("${ams.admin.bootstrap-password:}")
    private String bootstrapPassword;

    public AdminUserSeeder(UserRepository userRepository, RoleRepository roleRepository,
                           PasswordEncoder passwordEncoder, Environment environment) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.environment = environment;
    }

    @Override
    public void run(String... args) {
        boolean adminExists = userRepository.findByEmail(bootstrapEmail).isPresent();
        if (adminExists) {
            return;
        }

        if (isProduction() && (bootstrapPassword == null || bootstrapPassword.isBlank())) {
            throw new IllegalStateException(
                    "AMS_ADMIN_PASSWORD must be set before the first production ADMIN account is created");
        }

        Role adminRole = roleRepository.findByName("ADMIN")
                .orElseThrow(() -> new IllegalStateException("ADMIN role missing - RoleSeeder should have run first"));

        String password = (bootstrapPassword != null && !bootstrapPassword.isBlank())
                ? bootstrapPassword
                : randomPassword();

        User admin = new User(bootstrapEmail, "Admin", passwordEncoder.encode(password));
        admin.setEmailVerified(true);
        admin.setRoles(Set.of(adminRole));
        userRepository.save(admin);

        if (isProduction()) {
            log.info("Bootstrap admin account created: {}", bootstrapEmail);
        } else {
            log.warn("Bootstrap admin account created for development: {}", bootstrapEmail);
            if (bootstrapPassword == null || bootstrapPassword.isBlank()) {
                log.warn("Development bootstrap password was generated once for this instance.");
            }
        }
    }

    private boolean isProduction() {
        for (String profile : environment.getActiveProfiles()) {
            if ("prod".equalsIgnoreCase(profile) || "production".equalsIgnoreCase(profile)) {
                return true;
            }
        }
        return false;
    }

    private String randomPassword() {
        String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghjkmnpqrstuvwxyz23456789!@#$%";
        var random = new java.security.SecureRandom();
        StringBuilder sb = new StringBuilder(20);
        for (int i = 0; i < 20; i++) {
            sb.append(alphabet.charAt(random.nextInt(alphabet.length())));
        }
        return sb.toString();
    }
}
