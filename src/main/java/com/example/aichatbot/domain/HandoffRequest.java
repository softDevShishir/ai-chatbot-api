package com.example.aichatbot.domain;

import java.time.Instant;
import java.util.UUID;

public record HandoffRequest(
        UUID id,
        UUID conversationId,
        String owner,
        String lastCustomerMessage,
        HandoffStatus status,
        boolean notified,
        Instant createdAt,
        Instant resolvedAt
) {
}
