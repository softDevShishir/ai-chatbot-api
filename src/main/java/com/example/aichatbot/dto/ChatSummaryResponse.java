package com.example.aichatbot.dto;

import java.time.Instant;
import java.util.UUID;

public record ChatSummaryResponse(UUID id, String title, Instant createdAt, Instant updatedAt) {
}
