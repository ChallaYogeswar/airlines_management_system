package com.meridianair.ams.web;

import com.meridianair.ams.auth.AdminUserService;
import com.meridianair.ams.dto.AdminUserSummary;
import com.meridianair.ams.dto.UpdateUserRolesRequest;
import com.meridianair.ams.security.CurrentUserProvider;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AdminUserService adminUserService;
    private final CurrentUserProvider currentUserProvider;

    public AdminUserController(AdminUserService adminUserService, CurrentUserProvider currentUserProvider) {
        this.adminUserService = adminUserService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping
    public Page<AdminUserSummary> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return adminUserService.listAll(PageRequest.of(page, Math.min(size, 200)));
    }

    @PutMapping("/{id}/roles")
    public AdminUserSummary updateRoles(@PathVariable UUID id, @Valid @RequestBody UpdateUserRolesRequest request) {
        UUID actingAdminId = currentUserProvider.require().getId();
        return adminUserService.updateRoles(actingAdminId, id, request.roles());
    }
}
