package com.meridianair.ams.config;

import com.meridianair.ams.domain.Role;
import com.meridianair.ams.repository.RoleRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(1)
public class RoleSeeder implements CommandLineRunner {

    private final RoleRepository roleRepository;

    public RoleSeeder(RoleRepository roleRepository) {
        this.roleRepository = roleRepository;
    }

    @Override
    public void run(String... args) {
        seedIfMissing("PASSENGER", "Books flights, manages their own bookings and profile");
        seedIfMissing("STAFF", "Airline staff - flight ops, gate management, check-in");
        seedIfMissing("ADMIN", "Full administrative access, including audit logs and user management");
    }

    private void seedIfMissing(String name, String description) {
        if (roleRepository.findByName(name).isEmpty()) {
            roleRepository.save(new Role(name, description, true));
        }
    }
}
