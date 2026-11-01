package com.example.aichatbot.provider;

/**
 * Provider-neutral result of a single chat call: the reply text plus the
 * model that produced it and the token usage reported by the provider.
 */
public record AiResult(String text, String model, long inputTokens, long outputTokens) {
}
