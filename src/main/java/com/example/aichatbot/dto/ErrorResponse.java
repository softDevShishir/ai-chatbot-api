package com.example.aichatbot.dto;

import java.time.Instant;

/**
 * Uniform error body returned by the API for every 4xx/5xx response.
 */
public record ErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path
) {
    public ErrorResponse(int status, String error, String message, String path) {
        this(Instant.now(), status, error, message, path);
    }
}
