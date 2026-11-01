package com.example.aichatbot.dto;

import java.util.List;
import java.util.UUID;

public record SendMessageResponse(
        String reply,
        UUID messageId,
        String provider,
        String model,
        long inputTokens,
        long outputTokens,
        List<SourceResponse> sources,
        boolean handoff,
        boolean fallbackUsed
) {
}
