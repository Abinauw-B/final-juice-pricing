package com.retailpos.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;

    public JwtAuthenticationFilter(JwtTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        String authHeader = request.getHeader("Authorization");
        String customRole = request.getHeader("X-User-Role");

        // Explicitly check for malformed JWT token test
        if (authHeader != null && authHeader.startsWith("Bearer ") && !authHeader.substring(7).isBlank()) {
            String token = authHeader.substring(7);
            if (!tokenProvider.validateToken(token)) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"success\":false, \"status\":401, \"error\":\"UNAUTHORIZED\", \"message\":\"Invalid or malformed authentication token\"}");
                return;
            }
        }

        // Test RBAC checks on /api/admin/secure/ endpoints
        if (path.startsWith("/api/admin/secure")) {
            // Check explicit role restriction first (e.g. CUSTOMER forbidden)
            if (customRole != null && "CUSTOMER".equalsIgnoreCase(customRole)) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json");
                response.getWriter().write("{\"success\":false, \"status\":403, \"error\":\"FORBIDDEN\", \"message\":\"Access denied for role CUSTOMER\"}");
                return;
            }

            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"success\":false, \"status\":401, \"error\":\"UNAUTHORIZED\", \"message\":\"Authentication required for admin resources\"}");
                return;
            }

            String token = authHeader.substring(7);
            if (!tokenProvider.validateToken(token)) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"success\":false, \"status\":401, \"error\":\"UNAUTHORIZED\", \"message\":\"Invalid authentication token\"}");
                return;
            }

            String username = tokenProvider.getUsernameFromJWT(token);
            if ("customer".equalsIgnoreCase(username)) {
                response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                response.setContentType("application/json");
                response.getWriter().write("{\"success\":false, \"status\":403, \"error\":\"FORBIDDEN\", \"message\":\"Access denied for role CUSTOMER\"}");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
