package com.example.aichatbot.config;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Builds the {@link OpenAIClient} bean used to call OpenAI. Always built,
 * even without a key, so the app starts; {@code OpenAiProvider.isAvailable()}
 * is what gates actual use.
 */
@Configuration
public class OpenAiConfig {

    private static final Logger log = LoggerFactory.getLogger(OpenAiConfig.class);

    @Bean(destroyMethod = "close")
    public OpenAIClient openAiClient(@Value("${openai.api-key:}") String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("OPENAI_API_KEY is not set. Requests using provider \"openai\" will fail until it is configured.");
        }

        return OpenAIOkHttpClient.builder()
                .apiKey(apiKey == null || apiKey.isBlank() ? "missing-openai-api-key" : apiKey)
                .build();
    }
}
