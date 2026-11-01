package com.example.aichatbot.exception;

/**
 * Thrown when a per-user rate limit or the daily AI-call cap is exceeded.
 * Mapped to 429.
 */
public class TooManyRequestsException extends RuntimeException {
    public TooManyRequestsException(String message) {
        super(message);
    }
}
