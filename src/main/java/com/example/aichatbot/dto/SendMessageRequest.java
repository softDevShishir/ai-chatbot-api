package com.example.aichatbot.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * {@code provider} is optional and defaults to {@code DEFAULT_PROVIDER}. It
 * is intentionally not constrained to a fixed set here — {@code AiCompletionService}
 * validates it against the actually registered providers and returns a
 * clear 400 for anything unknown or unconfigured.
 */
public record SendMessageRequest(
        @NotBlank(message = "message must not be blank")
        String message,

        String provider
) {
}
