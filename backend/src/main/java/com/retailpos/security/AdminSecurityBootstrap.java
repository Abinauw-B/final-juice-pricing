package com.retailpos.security;

import com.retailpos.domain.User;
import com.retailpos.domain.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Enterprise Production Admin Bootstrap Component.
 *
 * Ensures administrator credentials can be securely injected via environment variable
 * without committing credentials to Git or modifying historical Flyway migrations.
 */
@Component
public class AdminSecurityBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSecurityBootstrap.class);

    // Known default seed hash from V11 migration ($2a$10$e0MYzXyjpJS7Pd0RVvHwHe16n/G36m./o0WcT8x98e87vO3hL5V/O for 'admin123')
    private static final String DEFAULT_SEED_HASH = "$2a$10$e0MYzXyjpJS7Pd0RVvHwHe16n/G36m./o0WcT8x98e87vO3hL5V/O";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${ADMIN_PASSWORD:${INITIAL_ADMIN_PASSWORD:}}")
    private String adminPasswordEnv;

    @Value("${spring.profiles.active:default}")
    private String activeProfile;

    public AdminSecurityBootstrap(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        boolean isProd = activeProfile != null && activeProfile.toLowerCase().contains("prod");

        if (adminPasswordEnv != null && !adminPasswordEnv.isBlank()) {
            updateAdminPassword("admin", adminPasswordEnv.trim());
            updateAdminPassword("superadmin", adminPasswordEnv.trim());
            log.info("[SECURITY BOOTSTRAP] Admin user credentials synchronized with ADMIN_PASSWORD environment variable.");
        } else if (isProd) {
            checkDefaultCredentialsInProd("admin");
            checkDefaultCredentialsInProd("superadmin");
        }
    }

    private void updateAdminPassword(String username, String rawPassword) {
        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            if (!passwordEncoder.matches(rawPassword, user.getPassword())) {
                user.setPassword(passwordEncoder.encode(rawPassword));
                userRepository.save(user);
                log.info("[SECURITY BOOTSTRAP] Administrator '{}' password hash updated securely from environment configuration.", username);
            }
        }
    }

    private void checkDefaultCredentialsInProd(String username) {
        Optional<User> userOpt = userRepository.findByUsername(username);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            if (DEFAULT_SEED_HASH.equals(user.getPassword()) || passwordEncoder.matches("admin123", user.getPassword())) {
                log.error("================================================================================");
                log.error("[CRITICAL SECURITY WARNING] User '{}' has default development credentials ('admin123') in PRODUCTION profile!", username);
                log.error("SUPPLY 'ADMIN_PASSWORD' ENVIRONMENT VARIABLE OR ROTATE VIA /api/auth/change-password IMMEDIATELY.");
                log.error("================================================================================");
            }
        }
    }
}
