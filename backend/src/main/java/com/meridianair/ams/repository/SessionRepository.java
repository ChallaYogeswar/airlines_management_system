package com.meridianair.ams.repository;

import com.meridianair.ams.domain.Session;
import com.meridianair.ams.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface SessionRepository extends JpaRepository<Session, UUID> {
    List<Session> findByUserAndRevokedFalse(User user);
    List<Session> findByUser(User user);
    List<Session> findByExpiresAtBeforeAndRevokedFalse(Instant cutoff);
}
