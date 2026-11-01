package com.example.aichatbot.controller;

import com.example.aichatbot.dto.LoginRequest;
import com.example.aichatbot.dto.LoginResponse;
import com.example.aichatbot.exception.AuthenticationFailedException;
import com.example.aichatbot.security.AdminCredentialsService;
import com.example.aichatbot.security.JwtService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Issues a JWT for the single admin account configured via
 * ADMIN_USERNAME/ADMIN_PASSWORD. This is the only endpoint in the API that
 * does not require a Bearer token, and it never issues one without valid
 * credentials.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AdminCredentialsService adminCredentialsService;
    private final JwtService jwtService;

    public AuthController(AdminCredentialsService adminCredentialsService, JwtService jwtService) {
        this.adminCredentialsService = adminCredentialsService;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        if (!adminCredentialsService.matches(request.username(), request.password())) {
            throw new AuthenticationFailedException("Invalid username or password");
        }
        String token = jwtService.generateToken(request.username());
        return ResponseEntity.ok(new LoginResponse(token));
    }
}
