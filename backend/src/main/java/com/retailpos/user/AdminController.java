package com.retailpos.user;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    @GetMapping("/secure/test")
    public ResponseEntity<Map<String, Object>> testAdminSecure() {
        return ResponseEntity.ok(Map.of("status", 200, "message", "Admin access granted", "authorized", true));
    }
}
