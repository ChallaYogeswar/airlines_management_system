package com.meridianair.ams.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Reads "Authorization: Bearer <token>", verifies it's a well-formed,
 * unexpired *access* token (never a refresh token - see isAccessToken),
 * and populates the SecurityContext with a principal carrying the
 * user's id and role-derived authorities.
 *
 * A bad or missing token is not an error at this layer - it just leaves
 * the request unauthenticated, and Spring Security's authorization rules
 * (see SecurityConfig) decide whether that's allowed for the endpoint
 * being hit.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            try {
                Claims claims = jwtService.parseClaims(token);
                if (jwtService.isAccessToken(claims)) {
                    UUID userId = UUID.fromString(claims.getSubject());
                    List<GrantedAuthority> authorities = jwtService.rolesFrom(claims).stream()
                            .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                            .collect(Collectors.toList());

                    UsernamePasswordAuthenticationToken auth =
                            new UsernamePasswordAuthenticationToken(userId, null, authorities);
                    auth.setDetails(jwtService.sessionIdFrom(claims));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            } catch (JwtException | IllegalArgumentException ex) {
                // Invalid/expired/tampered token, or a non-UUID subject -
                // leave the context unauthenticated rather than failing
                // the request here; a protected endpoint will 401/403 on
                // its own via the security rules.
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }
}
