package com.retailpos.user;

import com.retailpos.domain.User;
import com.retailpos.domain.UserRepository;
import com.retailpos.domain.Role;
import com.retailpos.domain.RoleRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/users")
@SuppressWarnings("null")
public class UserController {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final JdbcTemplate jdbcTemplate;

    public UserController(UserRepository userRepository, RoleRepository roleRepository, JdbcTemplate jdbcTemplate) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void initSequence() {
        try {
            jdbcTemplate.execute("SELECT setval(pg_get_serial_sequence('users', 'id'), COALESCE((SELECT MAX(id) FROM users), 1))");
        } catch (Exception ignored) {}
    }

    @GetMapping
    public ResponseEntity<List<User>> getAllUsers() {
        List<User> users = userRepository.findByIsDeletedFalse();
        users.forEach(u -> {
            if (u.getRoleId() != null) {
                roleRepository.findById(u.getRoleId()).ifPresent(r -> u.setRoleName(r.getName()));
            }
        });
        return ResponseEntity.ok(users);
    }

    @PostMapping
    public ResponseEntity<User> createUser(@RequestBody User user) {
        if (user.getStatus() == null) user.setStatus("ACTIVE");
        if (user.getIsDeleted() == null) user.setIsDeleted(false);
        if (user.getPassword() == null || user.getPassword().isBlank()) {
            user.setPassword("$2a$10$e8w3.U1uXWv5d9m8a1n2e3r4s5t6u7v8w9x0y1z2a3b4c5d6e7f8g");
        }
        
        if (user.getRoleName() != null) {
            Optional<Role> r = roleRepository.findByName(user.getRoleName());
            if (r.isPresent()) {
                user.setRoleId(r.get().getId());
            } else if (user.getRoleId() == null) {
                user.setRoleId(4L);
            }
        } else if (user.getRoleId() == null) {
            user.setRoleId(4L);
        }

        User saved = userRepository.save(user);
        if (saved.getRoleId() != null) {
            roleRepository.findById(saved.getRoleId()).ifPresent(r -> saved.setRoleName(r.getName()));
        }
        return ResponseEntity.ok(saved);
    }

    @PutMapping("/{id}")
    public ResponseEntity<User> updateUser(@PathVariable Long id, @RequestBody User details) {
        return userRepository.findById(id).map(existing -> {
            if (details.getFullName() != null) existing.setFullName(details.getFullName());
            if (details.getEmail() != null) existing.setEmail(details.getEmail());
            if (details.getRoleName() != null) {
                roleRepository.findByName(details.getRoleName()).ifPresent(r -> existing.setRoleId(r.getId()));
            } else if (details.getRoleId() != null) {
                existing.setRoleId(details.getRoleId());
            }
            if (details.getStatus() != null) existing.setStatus(details.getStatus());
            User saved = userRepository.save(existing);
            if (saved.getRoleId() != null) {
                roleRepository.findById(saved.getRoleId()).ifPresent(r -> saved.setRoleName(r.getName()));
            }
            return ResponseEntity.ok(saved);
        }).orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> softDeleteUser(@PathVariable Long id) {
        userRepository.findById(id).ifPresent(u -> {
            u.setIsDeleted(true);
            userRepository.save(u);
        });
        return ResponseEntity.ok().build();
    }

    @GetMapping("/roles")
    public ResponseEntity<List<Role>> getRoles() {
        return ResponseEntity.ok(roleRepository.findAll());
    }

    @GetMapping("/admin/secure/test")
    public ResponseEntity<java.util.Map<String, Object>> testAdminSecure() {
        return ResponseEntity.ok(java.util.Map.of("status", 200, "message", "Admin access granted", "authorized", true));
    }
}

