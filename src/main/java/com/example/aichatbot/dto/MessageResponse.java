package com.example.aichatbot.dto;

import java.time.Instant;
import java.util.UUID;

public record MessageResponse(
        UUID id,
        String role,
        String content,
        String provider,
        String model,
        Integer inputTokens,
        Integer outputTokens,
        Instant createdAt
) {
}
