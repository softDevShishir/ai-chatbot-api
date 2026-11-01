package com.example.aichatbot.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Checks login credentials against ADMIN_USERNAME/ADMIN_PASSWORD. There is a
 * single admin account; if either is unset, login always fails rather than
 * falling back to any default.
 */
@Service
public class AdminCredentialsService {

    private final String username;
    private final String password;

    public AdminCredentialsService(@Value("${admin.username}") String username,
                                    @Value("${admin.password}") String password) {
        this.username = username;
        this.password = password;
    }

    public boolean matches(String candidateUsername, String candidatePassword) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return false;
        }
        if (candidateUsername == null || candidatePassword == null) {
            return false;
        }
        return constantTimeEquals(username, candidateUsername) && constantTimeEquals(password, candidatePassword);
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Never prints the configured username/password, in case this bean ever
     * ends up in a log statement.
     */
    @Override
    public String toString() {
        return "AdminCredentialsService{username=***, password=***}";
    }
}
