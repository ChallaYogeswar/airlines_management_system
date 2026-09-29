package com.meridianair.ams.audit;

import com.meridianair.ams.domain.AuditLog;
import com.meridianair.ams.dto.AuditLogSummary;
import com.meridianair.ams.repository.AuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void log(AuditLog.Builder entry) {
        auditLogRepository.save(entry.build());
    }

    public Page<AuditLogSummary> findByUser(UUID userId, Pageable pageable) {
        return auditLogRepository.findByUserId(userId, pageable).map(this::toSummary);
    }

    public Page<AuditLogSummary> findByCategory(String category, Pageable pageable) {
        return auditLogRepository.findByEventCategory(category, pageable).map(this::toSummary);
    }

    public Page<AuditLogSummary> findAll(Pageable pageable) {
        return auditLogRepository.findAll(pageable).map(this::toSummary);
    }

    private AuditLogSummary toSummary(AuditLog log) {
        return new AuditLogSummary(
                log.getId(), log.getUserId(), log.getEventType(), log.getEventCategory(),
                log.getAction(), log.getStatus(), log.getMessage(), log.getIpAddress(), log.getCreatedAt()
        );
    }
}
