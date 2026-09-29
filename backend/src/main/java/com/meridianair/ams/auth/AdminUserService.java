package com.meridianair.ams.auth;

import com.meridianair.ams.audit.AuditService;
import com.meridianair.ams.domain.AuditLog;
import com.meridianair.ams.domain.Role;
import com.meridianair.ams.domain.User;
import com.meridianair.ams.dto.AdminUserSummary;
import com.meridianair.ams.repository.RoleRepository;
import com.meridianair.ams.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class AdminUserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AuditService auditService;

    public AdminUserService(UserRepository userRepository, RoleRepository roleRepository,
                             AuditService auditService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.auditService = auditService;
    }

    public Page<AdminUserSummary> listAll(Pageable pageable) {
        return userRepository.findAll(pageable).map(this::toSummary);
    }

    /**
     * Replaces a user's role set wholesale (not additive) - simpler
     * mental model for an admin form than separate add/remove endpoints.
     * Refuses to demote the last remaining ADMIN, since that's a
     * one-way door: once nobody holds ADMIN, nobody can grant it back
     * without direct database access.
     */
    @Transactional
    public AdminUserSummary updateRoles(UUID actingAdminId, UUID targetUserId, Set<String> roleNames) {
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        Set<Role> resolvedRoles = new HashSet<>();
        for (String name : roleNames) {
            Role role = roleRepository.findByName(name.toUpperCase())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown role: " + name));
            resolvedRoles.add(role);
        }

        boolean losingAdmin = hasRole(target, "ADMIN")
                && resolvedRoles.stream().noneMatch(r -> r.getName().equals("ADMIN"));
        if (losingAdmin && countOtherAdmins(target.getId()) == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Can't remove ADMIN from the last remaining admin account");
        }

        target.setRoles(resolvedRoles);
        User saved = userRepository.save(target);

        auditService.log(AuditLog.builder()
                .userId(actingAdminId).eventType("admin.user.roles_updated").eventCategory("admin")
                .action("update").resourceType("user").resourceId(target.getId().toString())
                .status("success").message("Roles set to " + roleNames));

        return toSummary(saved);
    }

    private boolean hasRole(User user, String roleName) {
        return user.getRoles().stream().anyMatch(r -> r.getName().equals(roleName));
    }

    private long countOtherAdmins(UUID excludingUserId) {
        return userRepository.findAll().stream()
                .filter(u -> !u.getId().equals(excludingUserId))
                .filter(u -> hasRole(u, "ADMIN"))
                .count();
    }

    private AdminUserSummary toSummary(User user) {
        List<String> roles = user.getRoles().stream().map(Role::getName).collect(Collectors.toList());
        return new AdminUserSummary(
                user.getId(), user.getEmail(), user.getName(), roles, user.isActive(),
                user.isMfaEnabled(), user.getCreatedAt(), user.getLastLoginAt()
        );
    }
}
