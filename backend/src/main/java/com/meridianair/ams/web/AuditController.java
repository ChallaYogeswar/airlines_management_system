package com.meridianair.ams.web;

import com.meridianair.ams.audit.AuditService;
import com.meridianair.ams.dto.AuditLogSummary;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@PreAuthorize("hasRole('ADMIN')")
public class AuditController {

    private final AuditService auditService;

    public AuditController(AuditService auditService) {
        this.auditService = auditService;
    }

    @GetMapping("/api/audit")
    public Page<AuditLogSummary> list(
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        PageRequest pageable = PageRequest.of(page, Math.min(size, 200));
        if (userId != null) {
            return auditService.findByUser(userId, pageable);
        }
        if (category != null) {
            return auditService.findByCategory(category, pageable);
        }
        return auditService.findAll(pageable);
    }
}
