package com.example.aichatbot.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ChatDetailResponse(
        UUID id,
        String title,
        Instant createdAt,
        Instant updatedAt,
        List<MessageResponse> messages
) {
}
