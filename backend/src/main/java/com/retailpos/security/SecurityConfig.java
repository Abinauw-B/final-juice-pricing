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

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

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
                .requestMatchers(HttpMethod.GET, "/api/pos/products", "/api/products").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/pos/products/**", "/api/products/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/pos/checkout", "/api/pos/orders", "/api/checkout", "/api/orders").permitAll()

                // Live pricing read endpoints (needed by POS and LED display)
                .requestMatchers(HttpMethod.GET, "/api/pricing/market", "/api/pricing/status", "/api/pricing/live", "/api/pricing/products").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/pricing/market-crash/status", "/api/pricing/crash/status").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/pricing/history/**", "/api/pricing/history").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/pricing/timing", "/api/pricing/config").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/pricing/products/**").permitAll()

                // Price quote / lock endpoint (used by POS checkout flow)
                .requestMatchers(HttpMethod.POST, "/api/pricing/quote", "/api/pricing/lock").permitAll()

                // WebSocket endpoints (authentication handled at STOMP layer)
                .requestMatchers("/ws/**").permitAll()

                // Health check and actuator
                .requestMatchers("/actuator/**").permitAll()
                .requestMatchers("/api/health").permitAll()

                // H2 console (dev only)
                .requestMatchers("/h2-console/**").permitAll()

                // OpenAPI / Swagger UI (Phase 36)
                .requestMatchers("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**", "/v3/api-docs").permitAll()

                // Reports summary endpoint (used by admin dashboard on initial load)
                .requestMatchers(HttpMethod.GET, "/api/reports/**", "/api/dashboard").permitAll()

                // Notifications read endpoint
                .requestMatchers(HttpMethod.GET, "/api/notifications/**").permitAll()

                // --- All other endpoints require authentication ---
                .anyRequest().authenticated()
            )
            .headers(headers -> headers.frameOptions(frame -> frame.disable()))
            // Wire the JWT filter before Spring Security's default auth filter
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private List<String> parseAllowedOrigins() {
        if (allowedOrigins == null || allowedOrigins.trim().isEmpty()) {
            return List.of("https://final-juice-pricing-admin.vercel.app", "https://final-juice-pricing.vercel.app");
        }
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty() && !s.equals("*"))
            .collect(Collectors.toList());
        if (origins.isEmpty()) {
            return List.of("https://final-juice-pricing-admin.vercel.app", "https://final-juice-pricing.vercel.app");
        }
        return origins;
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
