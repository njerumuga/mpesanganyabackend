package com.nganyaexperience.backend.controller;

import com.nganyaexperience.backend.dto.AuthResponse;
import com.nganyaexperience.backend.dto.LoginRequest;
import com.nganyaexperience.backend.dto.RegisterRequest;
import com.nganyaexperience.backend.service.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin("*")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest req) {
        try {
            AuthResponse res = authService.register(req);
            return ResponseEntity.ok(res);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message", e.getMessage()));
        }
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
        AuthResponse res = authService.login(req);
        return ResponseEntity.ok(res);
    }

    // Useful for frontend: confirm token still valid + get logged-in email
    @GetMapping("/me")
    public Map<String, Object> me(Authentication authentication) {
        if (authentication == null) return Map.of("authenticated", false);
        return Map.of(
                "authenticated", true,
                "email", authentication.getName()
        );
    }
}
