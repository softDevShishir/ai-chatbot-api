package com.example.aichatbot.config;

import com.google.genai.Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Builds the Google Gen AI {@link Client} bean used to call Gemini. Always
 * built, even without a key, so the app starts; {@code GeminiProvider.isAvailable()}
 * is what gates actual use.
 */
@Configuration
public class GeminiConfig {

    private static final Logger log = LoggerFactory.getLogger(GeminiConfig.class);

    @Bean(destroyMethod = "close")
    public Client geminiClient(@Value("${gemini.api-key:}") String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("GEMINI_API_KEY is not set. Requests using provider \"gemini\" will fail until it is configured.");
        }

        return Client.builder()
                .apiKey(apiKey == null || apiKey.isBlank() ? "missing-gemini-api-key" : apiKey)
                .build();
    }
}
