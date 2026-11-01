package com.example.aichatbot.exception;

/**
 * Thrown by {@code POST /api/auth/login} when the submitted credentials don't
 * match ADMIN_USERNAME/ADMIN_PASSWORD. Mapped to 401, same shape as a missing
 * or invalid JWT.
 */
public class AuthenticationFailedException extends RuntimeException {
    public AuthenticationFailedException(String message) {
        super(message);
    }
}
