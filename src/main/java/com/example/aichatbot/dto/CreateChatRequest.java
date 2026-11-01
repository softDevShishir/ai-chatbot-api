package com.example.aichatbot.dto;

import jakarta.validation.constraints.Size;

public record CreateChatRequest(
        @Size(max = 200, message = "title must not exceed 200 characters")
        String title
) {
}
