package com.example.aichatbot.domain;

import java.time.Instant;
import java.util.UUID;

public record Message(
        UUID id,
        UUID conversationId,
        MessageRole role,
        String content,
        String provider,
        String model,
        Integer inputTokens,
        Integer outputTokens,
        Instant createdAt
) {
}
