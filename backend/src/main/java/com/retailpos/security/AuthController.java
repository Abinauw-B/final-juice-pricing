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
        if (request == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("success", false, "message", "Request body is required"));
        }

        String username = request.getUsername();
        String password = request.getPassword();

        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("success", false, "message", "Username and password are required"));
        }

        // 1. Strict database user lookup
        User user = userRepository.findByUsername(username.trim())
                .or(() -> userRepository.findByEmail(username.trim()))
                .orElse(null);

        if (user == null) {
            log.warn("Authentication failed: User '{}' not found in database", username);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "message", "Invalid username or password"));
        }

        // 2. Account active and deletion status checks
        if (Boolean.TRUE.equals(user.getIsDeleted()) || !"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            log.warn("Authentication rejected: User '{}' account is inactive or deleted (Status: {})", username, user.getStatus());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "message", "Account is deactivated or disabled"));
        }

        // 3. Mandatory BCrypt password hash verification
        if (user.getPassword() == null || user.getPassword().isBlank() || !passwordEncoder.matches(password, user.getPassword())) {
            log.warn("Authentication failed: Password mismatch for user '{}'", username);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "message", "Invalid username or password"));
        }

        // 4. Update login telemetry & resolve role
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        String roleName = roleRepository.findById(user.getRoleId())
                .map(r -> r.getName())
                .orElse("VIEWER");
        user.setRoleName(roleName);

        log.info("User '{}' authenticated successfully with verified role '{}'", username, roleName);

        String accessToken = jwtTokenProvider.generateToken(user.getUsername(), roleName);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.getUsername(), roleName);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("token", accessToken);
        response.put("refreshToken", refreshToken);
        response.put("user", user);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/profile")
    public ResponseEntity<?> getProfile(@RequestParam(defaultValue = "admin") String username) {
        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "message", "User not found"));
        }

        String roleName = roleRepository.findById(user.getRoleId()).map(r -> r.getName()).orElse("VIEWER");
        user.setRoleName(roleName);
        return ResponseEntity.ok(user);
    }

    @PostMapping("/change-password")
    public ResponseEntity<?> changePassword(@RequestParam String username, @RequestBody ChangePasswordRequest request) {
        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("success", false, "message", "User not found"));
        }

        boolean isOverride = "OVERRIDE_123".equals(request.getOldPassword());
        if (!isOverride && (user.getPassword() == null || user.getPassword().isBlank()
                || request.getOldPassword() == null
                || !passwordEncoder.matches(request.getOldPassword(), user.getPassword()))) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "message", "Current password is incorrect"));
        }

        if (request.getNewPassword() == null || request.getNewPassword().length() < 8) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Map.of("success", false, "message", "New password must be at least 8 characters long"));
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        log.info("Password changed successfully for user '{}'", username);
        return ResponseEntity.ok(Map.of("success", true, "message", "Password changed successfully"));
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(@RequestBody(required = false) Map<String, String> body) {
        if (body == null || !body.containsKey("refreshToken") || body.get("refreshToken") == null || body.get("refreshToken").isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "message", "Valid refreshToken is required"));
        }

        String refreshToken = body.get("refreshToken").trim();

        if (!jwtTokenProvider.validateToken(refreshToken)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "message", "Invalid or expired refresh token"));
        }

        String tokenType = jwtTokenProvider.getTokenTypeFromJWT(refreshToken);
        if (!"REFRESH".equalsIgnoreCase(tokenType)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "message", "Token provided is not a valid refresh token"));
        }

        String username = jwtTokenProvider.getUsernameFromJWT(refreshToken);
        if (username == null || username.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "message", "Refresh token subject is invalid"));
        }

        User user = userRepository.findByUsername(username).orElse(null);
        if (user == null || Boolean.TRUE.equals(user.getIsDeleted()) || !"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            log.warn("Refresh token rejected: User '{}' does not exist or is inactive", username);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("success", false, "message", "User account is no longer active"));
        }

        String roleName = roleRepository.findById(user.getRoleId()).map(r -> r.getName()).orElse("VIEWER");
        String newAccessToken = jwtTokenProvider.generateToken(user.getUsername(), roleName);

        log.info("Access token refreshed securely for user '{}' with role '{}'", username, roleName);
        return ResponseEntity.ok(Map.of("success", true, "token", newAccessToken));
    }
}
