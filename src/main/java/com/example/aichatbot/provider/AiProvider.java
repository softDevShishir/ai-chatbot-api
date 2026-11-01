package com.example.aichatbot.provider;

import java.util.List;

/**
 * A single AI backend (Claude, Gemini or OpenAI) that can turn a system
 * prompt plus conversation history into a reply.
 */
public interface AiProvider {

    /**
     * Stable lowercase id used in requests/config: {@code claude}, {@code gemini} or {@code openai}.
     */
    String id();

    /**
     * Whether this provider has an API key configured. A provider without a
     * key only fails (clearly) when actually selected — the app still starts.
     */
    boolean isAvailable();

    /**
     * Sends {@code systemPrompt} and the conversation {@code turns} (oldest
     * first) to the provider and returns its reply.
     *
     * @throws AiProviderException if the call fails for any reason, or the
     *                              provider returns an empty response
     */
    AiResult chat(String systemPrompt, List<ChatTurn> turns);
}
