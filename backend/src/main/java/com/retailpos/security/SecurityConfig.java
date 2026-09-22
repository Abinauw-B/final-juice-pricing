package com.retailpos.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
@EnableWebSecurity
@SuppressWarnings("null")
public class SecurityConfig {

    @Value("${cors.allowed-origins:https://final-juice-pricing.vercel.app,https://final-juice-pricing-admin.vercel.app,http://localhost:8000,http://localhost:8001,http://localhost:8002}")
    private String allowedOrigins;

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // --- Public endpoints (no authentication required) ---

                // Authentication endpoints
                .requestMatchers("/api/auth/**").permitAll()

                // Customer POS endpoints (cashiers and customers don't need JWT)
                .requestMatchers(HttpMethod.GET, "/api/pos/products", "/api/products", "/api/pos/orders/**", "/api/orders/**", "/api/admin/orders/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/pos/products/**", "/api/products/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/pos/checkout", "/api/pos/orders", "/api/checkout", "/api/orders").permitAll()
                .requestMatchers(HttpMethod.PUT, "/api/pos/products/*/stock", "/api/products/*/stock").permitAll()

                // Inventory batches read endpoints (used by POS and monitoring)
                .requestMatchers(HttpMethod.GET, "/api/batches", "/api/batches/**").permitAll()

                // Live pricing read endpoints (needed by POS and LED display)
                .requestMatchers(HttpMethod.GET, "/api/pricing/market", "/api/pricing/status", "/api/pricing/live", "/api/pricing/products").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/pricing/market-crash/status", "/api/pricing/crash/status").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/pricing/market-crash/trigger", "/api/pricing/market-crash/stop").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/pricing/history/**", "/api/pricing/history").permitAll()
                .requestMatchers("/api/pricing/timing", "/api/pricing/config", "/api/admin/pricing/timing", "/api/pricing/debug/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/pricing/products/**").permitAll()

                // Price quote / lock endpoint (used by POS checkout flow)
                .requestMatchers(HttpMethod.POST, "/api/pricing/quote", "/api/pricing/lock").permitAll()

                // WebSocket endpoints (authentication handled at STOMP layer)
                .requestMatchers("/ws/**").permitAll()

                // Health check and readiness probes (public for cloud orchestration & load balancers)
                .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll()
                .requestMatchers("/api/health", "/api/health/**", "/api/readiness", "/api/liveness").permitAll()

                // OpenAPI / Swagger UI
                .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/v3/api-docs").permitAll()

                // Reports summary endpoint (used by dashboard on initial load)
                .requestMatchers(HttpMethod.GET, "/api/reports/**", "/api/dashboard").permitAll()

                // Notifications read endpoint
                .requestMatchers(HttpMethod.GET, "/api/notifications/**").permitAll()

                // Admin & Manager role enforcement (Actuator management, internal metrics, and sensitive operations)
                .requestMatchers("/actuator/**", "/api/metrics").hasAnyRole("ADMIN", "SUPER_ADMIN")

                // --- PRICING MUTATION ENDPOINTS: ADMIN-only ---
                // These endpoints can change live prices, trigger crashes, or modify product configuration.
                // They MUST be protected. A blanket permitAll() was here previously — this is the security fix.
                .requestMatchers(HttpMethod.POST, "/api/pricing/market-crash/trigger", "/api/pricing/market-crash/stop").hasAnyRole("ADMIN", "SUPER_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/pricing/crash/trigger", "/api/pricing/crash/stop").hasAnyRole("ADMIN", "SUPER_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/pricing/evaluate", "/api/pricing/force-settlement").hasAnyRole("ADMIN", "SUPER_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/pricing/pause", "/api/pricing/resume").hasAnyRole("ADMIN", "SUPER_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/pricing/reset-all", "/api/pricing/reset", "/api/admin/pricing/reset-all", "/api/admin/pricing/reset").hasAnyRole("ADMIN", "SUPER_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/pricing/simulator/**", "/api/pricing/live-bot/**").hasAnyRole("ADMIN", "SUPER_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/pricing/products/*/price").hasAnyRole("ADMIN", "SUPER_ADMIN")
                .requestMatchers(HttpMethod.PUT,  "/api/pricing/config", "/api/pricing/timing").hasAnyRole("ADMIN", "SUPER_ADMIN")
                .requestMatchers(HttpMethod.PUT,  "/api/admin/pricing/**").hasAnyRole("ADMIN", "SUPER_ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/admin/**").hasAnyRole("ADMIN", "SUPER_ADMIN", "MANAGER")
                .requestMatchers(HttpMethod.PUT,  "/api/admin/**").hasAnyRole("ADMIN", "SUPER_ADMIN", "MANAGER")
                .requestMatchers(HttpMethod.DELETE, "/api/admin/**").hasAnyRole("ADMIN", "SUPER_ADMIN")

                // POS product mutation (add/update product records) — admin only
                .requestMatchers(HttpMethod.POST, "/api/pos/products", "/api/products").hasAnyRole("ADMIN", "SUPER_ADMIN")
                .requestMatchers(HttpMethod.PUT,  "/api/pos/products/**", "/api/products/**").hasAnyRole("ADMIN", "SUPER_ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/pos/products/**", "/api/products/**").hasAnyRole("ADMIN", "SUPER_ADMIN")

                // Batch write operations — admin only
                .requestMatchers(HttpMethod.POST, "/api/batches", "/api/batches/**").hasAnyRole("ADMIN", "SUPER_ADMIN")
                .requestMatchers(HttpMethod.PUT,  "/api/batches/**").hasAnyRole("ADMIN", "SUPER_ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/batches/**").hasAnyRole("ADMIN", "SUPER_ADMIN")

                // All remaining /api/admin/** require at minimum MANAGER role
                .requestMatchers("/api/admin/**").hasAnyRole("ADMIN", "SUPER_ADMIN", "MANAGER")

                // --- All other endpoints require authentication ---
                .anyRequest().authenticated()
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setContentType("application/json");
                    response.setStatus(jakarta.servlet.http.HttpServletResponse.SC_UNAUTHORIZED);
                    response.getOutputStream().println("{\"success\":false,\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Authentication required\"}");
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setContentType("application/json");
                    response.setStatus(jakarta.servlet.http.HttpServletResponse.SC_FORBIDDEN);
                    response.getOutputStream().println("{\"success\":false,\"status\":403,\"error\":\"Forbidden\",\"message\":\"Access denied: insufficient permissions\"}");
                })
            )
            .headers(headers -> headers.frameOptions(frame -> frame.disable()))
            // Wire the JWT filter before Spring Security's default auth filter
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private List<String> parseAllowedOrigins() {
        List<String> defaultOrigins = new java.util.ArrayList<>(List.of(
            "https://final-juice-pricing.vercel.app",
            "https://final-juice-pricing-admin.vercel.app",
            "http://localhost:*",
            "http://127.0.0.1:*"
        ));
        if (allowedOrigins != null && !allowedOrigins.trim().isEmpty()) {
            for (String origin : allowedOrigins.split(",")) {
                String trimmed = origin.trim();
                if (!trimmed.isEmpty() && !trimmed.equals("*") && !defaultOrigins.contains(trimmed)) {
                    defaultOrigins.add(trimmed);
                }
            }
        }
        return defaultOrigins;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        List<String> origins = parseAllowedOrigins();
        configuration.setAllowedOriginPatterns(origins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);
        configuration.setExposedHeaders(List.of("Authorization", "Content-Type", "X-Total-Count"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public WebMvcConfigurer webMvcConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(@org.springframework.lang.NonNull CorsRegistry registry) {
                List<String> origins = parseAllowedOrigins();
                registry.addMapping("/**")
                    .allowedOriginPatterns(origins.toArray(new String[0]))
                    .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD")
                    .allowedHeaders("*")
                    .allowCredentials(true)
                    .exposedHeaders("Authorization", "Content-Type", "X-Total-Count");
            }
        };
    }
}
