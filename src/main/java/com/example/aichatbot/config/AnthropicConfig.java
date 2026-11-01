package com.example.aichatbot.config;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Builds the {@link AnthropicClient} bean used to call Claude. Always built,
 * even without a key, so the app starts; {@code ClaudeProvider.isAvailable()}
 * is what gates actual use.
 */
@Configuration
public class AnthropicConfig {

    private static final Logger log = LoggerFactory.getLogger(AnthropicConfig.class);

    @Bean
    public AnthropicClient anthropicClient(@Value("${claude.api-key:}") String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("CLAUDE_API_KEY is not set. Requests using provider \"claude\" will fail until it is configured.");
        }

        return AnthropicOkHttpClient.builder()
                .apiKey(apiKey == null || apiKey.isBlank() ? "missing-claude-api-key" : apiKey)
                .build();
    }
}
