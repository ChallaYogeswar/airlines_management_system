package com.meridianair.ams.security;

import com.meridianair.ams.domain.User;
import com.meridianair.ams.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Component
public class CurrentUserProvider {

    private final UserRepository userRepository;

    public CurrentUserProvider(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User require() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UUID userId)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Not authenticated");
        }
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User no longer exists"));
    }

    /** The session ID the current request's access token was minted for -
     * null if the token predates session-tagging or something odd is
     * going on. Used to mark "this is the session you're on right now"
     * in the sessions list, not for anything security-critical. */
    public UUID currentSessionId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return null;
        Object details = auth.getDetails();
        if (!(details instanceof String sid) || sid.isBlank()) return null;
        try {
            return UUID.fromString(sid);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
