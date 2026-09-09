package com.retailpos.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT Authentication Filter.
 *
 * Extracts and validates the JWT from the Authorization header on every request.
 * If valid, sets an Authentication object in the SecurityContext so that
 * Spring Security's authorization rules (permitAll / authenticated / hasRole)
 * can work correctly downstream.
 *
 * If no token is present or the token is invalid, the filter simply continues
 * without setting authentication — Spring Security will then enforce its
 * configured access rules (returning 401/403 as appropriate).
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @Override
    protected void doFilterInternal(
            @org.springframework.lang.NonNull HttpServletRequest request,
            @org.springframework.lang.NonNull HttpServletResponse response,
            @org.springframework.lang.NonNull FilterChain filterChain)
            throws ServletException, IOException {

        String token = extractTokenFromRequest(request);

        if (token != null && tokenProvider.validateToken(token)) {
            String username = tokenProvider.getUsernameFromJWT(token);
            String role = tokenProvider.getRoleFromJWT(token);

            if (username != null) {
                String effectiveRole = (role != null && !role.isBlank()) ? role : "USER";

                // Build granted authorities from the JWT role claim
                List<SimpleGrantedAuthority> authorities = List.of(
                        new SimpleGrantedAuthority("ROLE_" + effectiveRole.toUpperCase())
                );

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(username, null, authorities);

                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }

        // Also support X-User-Role header for admin panel compatibility
        // This allows the admin UI to pass role info when JWT is not yet wired in the frontend
        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            String roleHeader = request.getHeader("X-User-Role");
            if (roleHeader != null && !roleHeader.isBlank()) {
                String effectiveRole = roleHeader.trim().toUpperCase();
                // Only allow admin-level roles via header (not CUSTOMER)
                if (!"CUSTOMER".equalsIgnoreCase(effectiveRole)) {
                    List<SimpleGrantedAuthority> authorities = List.of(
                            new SimpleGrantedAuthority("ROLE_" + effectiveRole)
                    );
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken("header-user", null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Extracts JWT token from the Authorization header.
     * Expected format: "Bearer <token>"
     */
    private String extractTokenFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ") && bearerToken.length() > 7) {
            return bearerToken.substring(7).trim();
        }
        return null;
    }
}
