package com.example.aichatbot.dto;

import java.time.Instant;
import java.util.UUID;

public record HandoffResponse(
        UUID id,
        UUID conversationId,
        String status,
        String lastCustomerMessage,
        boolean notified,
        Instant createdAt,
        Instant resolvedAt
) {
}
