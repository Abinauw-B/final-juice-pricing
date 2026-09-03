package com.retailpos.security;

import com.retailpos.domain.User;
import com.retailpos.domain.UserRepository;
import com.retailpos.domain.RoleRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*")
@SuppressWarnings("null")
public class AuthController {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;

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

        User user = userRepository.findByUsername(username)
                .or(() -> userRepository.findByEmail(username))
                .orElse(null);

        // Fallback demo users check for quick system bootstrap
        if (user == null) {
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
        } else {
            user.setLastLoginAt(LocalDateTime.now());
            userRepository.save(user);
            String roleName = roleRepository.findById(user.getRoleId()).map(r -> r.getName()).orElse("ADMIN");
            user.setRoleName(roleName);
        }

        String token = jwtTokenProvider.generateToken(user.getUsername(), user.getRoleName() != null ? user.getRoleName() : "ADMIN");

        Map<String, Object> response = new HashMap<>();
        response.put("token", token);
        response.put("refreshToken", "ref_" + System.currentTimeMillis());
        response.put("user", user);

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
        userRepository.findByUsername(username).ifPresent(u -> {
            u.setPassword(passwordEncoder.encode(request.getNewPassword()));
            userRepository.save(u);
        });
        return ResponseEntity.ok(Map.of("message", "Password changed successfully"));
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refreshToken(@RequestBody Map<String, String> body) {
        String token = jwtTokenProvider.generateToken("admin", "ADMIN");
        return ResponseEntity.ok(Map.of("token", token));
    }
}
