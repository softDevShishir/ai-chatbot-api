package com.example.aichatbot.service;

import com.example.aichatbot.retrieval.KnowledgeItem;

import java.util.List;
import java.util.UUID;

public record SendMessageResult(
        String reply,
        UUID messageId,
        String provider,
        String model,
        long inputTokens,
        long outputTokens,
        List<KnowledgeItem> sources,
        boolean handoff,
        boolean fallbackUsed
) {
}
