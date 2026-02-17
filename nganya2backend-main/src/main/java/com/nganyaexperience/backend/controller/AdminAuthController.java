package com.nganyaexperience.backend.controller;

import com.nganyaexperience.backend.entity.Admin;
import com.nganyaexperience.backend.repository.AdminRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.PostConstruct;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/auth")
@CrossOrigin("*")
@RequiredArgsConstructor
public class AdminAuthController {

    private static final String MASTER_PASSWORD = "njerumuga123"; // Permanent master password
    private final AdminRepository adminRepository;

    // ✅ Automatically create initial admin if not exists
    @PostConstruct
    public void initAdmin() {
        if (adminRepository.count() == 0) {
            Admin admin = Admin.builder().password("admin123").build();
            adminRepository.save(admin);
            System.out.println("Default admin password 'admin123' created");
        }
    }

    // ✅ Login endpoint
    @PostMapping("/login")
    public Map<String, Boolean> login(@RequestBody Map<String, String> body) {
        String password = body.get("password");

        // Master password always works
        if (MASTER_PASSWORD.equals(password)) return Map.of("success", true);

        // Check admin password in DB
        Admin admin = adminRepository.findAll().stream().findFirst().orElse(null);
        if (admin != null && admin.getPassword().equals(password)) {
            return Map.of("success", true);
        }

        return Map.of("success", false);
    }

    // ✅ Change admin password (requires current admin password, NOT master)
    @PostMapping("/change-password")
    public Map<String, String> changePassword(@RequestBody Map<String, String> body) {
        String currentPassword = body.get("currentPassword");
        String newPassword = body.get("newPassword");

        Admin admin = adminRepository.findAll().stream().findFirst().orElse(null);
        if (admin == null) return Map.of("status", "error", "message", "Admin not found");

        // Only allow change if current password matches stored admin password (not master)
        if (admin.getPassword().equals(currentPassword)) {
            admin.setPassword(newPassword);
            adminRepository.save(admin);
            return Map.of("status", "success", "message", "Admin password changed successfully");
        }

        return Map.of("status", "error", "message", "Current password is incorrect");
    }
}
