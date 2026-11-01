package com.example.aichatbot.domain;

import java.time.Instant;
import java.util.UUID;

public record Conversation(UUID id, String owner, String title, Instant createdAt, Instant updatedAt) {
}
