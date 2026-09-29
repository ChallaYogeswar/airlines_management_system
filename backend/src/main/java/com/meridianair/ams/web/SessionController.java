package com.meridianair.ams.web;

import com.meridianair.ams.auth.SessionService;
import com.meridianair.ams.domain.Session;
import com.meridianair.ams.domain.User;
import com.meridianair.ams.dto.SessionSummary;
import com.meridianair.ams.security.CurrentUserProvider;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final SessionService sessionService;
    private final CurrentUserProvider currentUserProvider;

    public SessionController(SessionService sessionService, CurrentUserProvider currentUserProvider) {
        this.sessionService = sessionService;
        this.currentUserProvider = currentUserProvider;
    }

    @GetMapping
    public List<SessionSummary> listMine() {
        User user = currentUserProvider.require();
        UUID currentId = currentUserProvider.currentSessionId();
        return sessionService.listActive(user).stream()
                .map(s -> toSummary(s, currentId))
                .collect(Collectors.toList());
    }

    @DeleteMapping("/{id}")
    public void revoke(@PathVariable UUID id) {
        User user = currentUserProvider.require();
        Session target = sessionService.listActive(user).stream()
                .filter(s -> s.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found"));
        sessionService.revoke(target, "user_revoked");
    }

    /** "Log out everywhere else" - keeps the session the request itself
     * is authenticated with. */
    @DeleteMapping
    public void revokeAllOthers() {
        User user = currentUserProvider.require();
        UUID currentId = currentUserProvider.currentSessionId();
        sessionService.revokeAllExcept(user, currentId, "user_revoked_all");
    }

    private SessionSummary toSummary(Session s, UUID currentId) {
        return new SessionSummary(
                s.getId(), s.getDeviceName(), s.getIpAddress(),
                s.getCreatedAt(), s.getLastActivityAt(), s.getId().equals(currentId)
        );
    }
}
