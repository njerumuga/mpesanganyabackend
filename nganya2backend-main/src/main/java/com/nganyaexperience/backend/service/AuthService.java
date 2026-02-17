package com.nganyaexperience.backend.service;

import com.nganyaexperience.backend.dto.AuthResponse;
import com.nganyaexperience.backend.dto.LoginRequest;
import com.nganyaexperience.backend.dto.RegisterRequest;
import com.nganyaexperience.backend.entity.AppUser;
import com.nganyaexperience.backend.repository.AppUserRepository;
import com.nganyaexperience.backend.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AppUserRepository appUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthResponse register(RegisterRequest req) {
        String email = req.getEmail().trim().toLowerCase();

        if (appUserRepository.existsByEmailIgnoreCase(email)) {
            throw new IllegalArgumentException("Email already registered");
        }

        AppUser u = AppUser.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(req.getPassword()))
                .build();

        appUserRepository.save(u);

        String token = jwtService.generateToken(email);
        return new AuthResponse(token, email);
    }

    public AuthResponse login(LoginRequest req) {
        String email = req.getEmail().trim().toLowerCase();

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, req.getPassword())
        );

        String token = jwtService.generateToken(email);
        return new AuthResponse(token, email);
    }
}
