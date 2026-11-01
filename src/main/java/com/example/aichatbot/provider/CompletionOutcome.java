package com.example.aichatbot.provider;

/**
 * Result of {@link AiCompletionService#complete}: the reply plus which
 * provider actually produced it and whether the configured fallback had to
 * be used.
 */
public record CompletionOutcome(AiResult result, String providerId, boolean fallbackUsed) {
}
