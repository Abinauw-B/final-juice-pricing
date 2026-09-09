package com.retailpos.security;

import com.retailpos.domain.User;
import com.retailpos.domain.UserRepository;
import com.retailpos.domain.RoleRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@SuppressWarnings("null")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;

    @Value("${spring.profiles.active:default}")
    private String activeProfile;

    public AuthController(UserRepository userRepository, RoleRepository roleRepository, JwtTokenProvider jwtTokenProvider, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.jwtTokenProvider = jwtTokenProvider;
        this.passwordEncoder = passwordEncoder;
    }

    public static class LoginRequest {
        private String username;
        private String password;

        public LoginRequest() {}
        public LoginRequest(String username, String password) {
            this.username = username;
            this.password = password;
        }
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }

    public static class ChangePasswordRequest {
        private String oldPassword;
        private String newPassword;

        public ChangePasswordRequest() {}
        public ChangePasswordRequest(String oldPassword, String newPassword) {
            this.oldPassword = oldPassword;
            this.newPassword = newPassword;
        }
        public String getOldPassword() { return oldPassword; }
        public void setOldPassword(String oldPassword) { this.oldPassword = oldPassword; }
        public String getNewPassword() { return newPassword; }
        public void setNewPassword(String newPassword) { this.newPassword = newPassword; }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        String username = request.getUsername();
        String password = request.getPassword();

        if (username == null || username.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("success", false, "message", "Username is required"));
        }

        // 1. Try to find the user in the database
        User user = userRepository.findByUsername(username)
                .or(() -> userRepository.findByEmail(username))
                .orElse(null);

        if (user != null) {
            // Real user found — validate BCrypt password
            if (user.getPassword() != null && !user.getPassword().isBlank()) {
                if (password == null || !passwordEncoder.matches(password, user.getPassword())) {
                    log.warn("Failed login attempt for user: {}", username);
                    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                            .body(Map.of("success", false, "message", "Invalid username or password"));
                }
            }

            user.setLastLoginAt(LocalDateTime.now());
            userRepository.save(user);
            String roleName = roleRepository.findById(user.getRoleId()).map(r -> r.getName()).orElse("ADMIN");
            user.setRoleName(roleName);

            log.info("User '{}' logged in successfully with role '{}'", username, roleName);

            String token = jwtTokenProvider.generateToken(user.getUsername(), roleName);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("token", token);
            response.put("refreshToken", "ref_" + System.currentTimeMillis());
            response.put("user", user);

            return ResponseEntity.ok(response);
        }

        // 2. Demo/dev user fallback — only allowed in non-production profiles
        boolean isDev = isDevProfile();
        if (!isDev) {
            log.warn("Login attempt for non-existent user '{}' rejected in production mode", username);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "message", "Invalid username or password"));
        }

        // Dev mode: create synthetic user for quick bootstrapping
        log.info("Dev mode: Creating synthetic user for '{}'", username);
        String roleName = switch (username.toLowerCase()) {
            case "superadmin" -> "SUPER_ADMIN";
            case "admin" -> "ADMIN";
            case "manager" -> "MANAGER";
            case "cashier" -> "CASHIER";
            case "kitchen" -> "KITCHEN_STAFF";
            case "inventory" -> "INVENTORY_MANAGER";
            default -> "VIEWER";
        };

        user = User.builder()
                .id(100L)
                .username(username)
                .email(username + "@pubexchange.com")
                .fullName(username.substring(0, 1).toUpperCase() + username.substring(1) + " User")
                .roleId(1L)
                .roleName(roleName)
                .status("ACTIVE")
                .lastLoginAt(LocalDateTime.now())
                .build();

        String token = jwtTokenProvider.generateToken(user.getUsername(), roleName);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("token", token);
        response.put("refreshToken", "ref_" + System.currentTimeMillis());
        response.put("user", user);
        response.put("devMode", true);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/profile")
    public ResponseEntity<?> getProfile(@RequestParam(defaultValue = "admin") String username) {
        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null) {
            user = User.builder()
                    .id(1L)
                    .username(username)
                    .email(username + "@pubexchange.com")
                    .fullName("Enterprise Administrator")
                    .roleName("SUPER_ADMIN")
                    .status("ACTIVE")
                    .build();
        } else {
            String roleName = roleRepository.findById(user.getRoleId()).map(r -> r.getName()).orElse("ADMIN");
            user.setRoleName(roleName);
        }
        return ResponseEntity.ok(user);
    }

    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@RequestParam String username, @RequestBody ChangePasswordRequest request) {
        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "message", "User not found"));
        }

        // Validate old password if the user has one set
        if (user.getPassword() != null && !user.getPassword().isBlank()) {
            if (request.getOldPassword() == null || !passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body(Map.of("success", false, "message", "Current password is incorrect"));
            }
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        return ResponseEntity.ok(Map.of("success", true, "message", "Password changed successfully"));
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(@RequestBody Map<String, String> body) {
        // In a full implementation, validate the refresh token
        String token = jwtTokenProvider.generateToken("admin", "ADMIN");
        return ResponseEntity.ok(Map.of("success", true, "token", token));
    }

    /**
     * Checks if the application is running in a development profile.
     * Demo user fallback is only available when dev/default profile is active.
     */
    private boolean isDevProfile() {
        if (activeProfile == null || activeProfile.isBlank()) return true;
        String lower = activeProfile.toLowerCase();
        return lower.contains("dev") || lower.contains("default") || lower.contains("local") || lower.contains("test");
    }
}
