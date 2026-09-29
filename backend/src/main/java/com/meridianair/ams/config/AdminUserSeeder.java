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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Registration always assigns the PASSENGER role (see AuthService) - there
 * is deliberately no "register as admin" path. So without this seeder,
 * there would be no way for anyone to ever reach an ADMIN-protected
 * endpoint (AuditController, and everything under /api/admin/**) short
 * of editing the database by hand.
 *
 * Only runs if no ADMIN-role user exists yet, so it's a one-time
 * bootstrap, not something that fights an operator who has already set
 * up real admins and changed this password.
 */
@Component
@Order(3) // after RoleSeeder, so the ADMIN role definitely exists first
public class AdminUserSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminUserSeeder.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${ams.admin.bootstrap-email:admin@meridianair.example}")
    private String bootstrapEmail;

    @Value("${ams.admin.bootstrap-password:#{null}}")
    private String bootstrapPassword;

    public AdminUserSeeder(UserRepository userRepository, RoleRepository roleRepository,
                            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        boolean adminExists = userRepository.findByEmail(bootstrapEmail).isPresent();
        if (adminExists) {
            return;
        }

        Role adminRole = roleRepository.findByName("ADMIN")
                .orElseThrow(() -> new IllegalStateException("ADMIN role missing - RoleSeeder should have run first"));

        // No AMS_ADMIN_PASSWORD set -> generate one and log it once. This
        // is a dev-friendly default, not a production-safe one - set
        // ams.admin.bootstrap-password explicitly for any real deployment.
        String password = (bootstrapPassword != null && !bootstrapPassword.isBlank())
                ? bootstrapPassword
                : randomPassword();

        User admin = new User(bootstrapEmail, "Admin", passwordEncoder.encode(password));
        admin.setEmailVerified(true);
        admin.setRoles(Set.of(adminRole));
        userRepository.save(admin);

        boolean wasGenerated = bootstrapPassword == null || bootstrapPassword.isBlank();
        if (wasGenerated) {
            log.warn("=================================================================");
            log.warn("Bootstrap admin account created: {}", bootstrapEmail);
            log.warn("Generated password (shown once, not stored anywhere else): {}", password);
            log.warn("Set ams.admin.bootstrap-password / AMS_ADMIN_PASSWORD to avoid this in future runs.");
            log.warn("=================================================================");
        } else {
            log.info("Bootstrap admin account created: {}", bootstrapEmail);
        }
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
