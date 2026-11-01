package com.example.aichatbot.provider;

import com.example.aichatbot.domain.MessageRole;

/**
 * One turn of conversation history sent to an {@link AiProvider}. The system
 * prompt is passed separately, not as a turn.
 */
public record ChatTurn(MessageRole role, String content) {
}
