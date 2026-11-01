package com.example.aichatbot.provider;

import com.example.aichatbot.domain.MessageRole;
import com.google.genai.Client;
import com.google.genai.errors.ApiException;
import com.google.genai.errors.GenAiIOException;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.GenerateContentResponseUsageMetadata;
import com.google.genai.types.Part;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * {@link AiProvider} backed by the official Google Gen AI (Gemini) Java SDK.
 */
@Component
public class GeminiProvider implements AiProvider {

    public static final String ID = "gemini";

    private static final Logger log = LoggerFactory.getLogger(GeminiProvider.class);

    private final Client client;
    private final String model;
    private final int maxTokens;
    private final boolean configured;

    public GeminiProvider(Client client,
                           @Value("${gemini.model}") String model,
                           @Value("${gemini.max-tokens}") int maxTokens,
                           @Value("${gemini.api-key:}") String apiKey) {
        this.client = client;
        this.model = model;
        this.maxTokens = maxTokens;
        this.configured = apiKey != null && !apiKey.isBlank();
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public boolean isAvailable() {
        return configured;
    }

    @Override
    public AiResult chat(String systemPrompt, List<ChatTurn> turns) {
        if (!configured) {
            throw new AiProviderException(AiProviderException.Kind.AUTHENTICATION,
                    "Provider \"gemini\" has no GEMINI_API_KEY configured");
        }

        GenerateContentConfig.Builder configBuilder = GenerateContentConfig.builder()
                .maxOutputTokens(maxTokens);
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            configBuilder.systemInstruction(Content.fromParts(Part.fromText(systemPrompt)));
        }
        GenerateContentConfig config = configBuilder.build();

        List<Content> contents = turns.stream()
                .map(turn -> Content.builder()
                        .role(turn.role() == MessageRole.USER ? "user" : "model")
                        .parts(List.of(Part.fromText(turn.content())))
                        .build())
                .toList();

        try {
            GenerateContentResponse response = client.models.generateContent(model, contents, config);

            String text = response.text();
            if (text == null || text.isBlank()) {
                throw new AiProviderException(AiProviderException.Kind.UNKNOWN, "Gemini returned an empty response");
            }

            GenerateContentResponseUsageMetadata usage = response.usageMetadata().orElse(null);
            long inputTokens = usage == null ? 0 : usage.promptTokenCount().orElse(0);
            long outputTokens = usage == null ? 0
                    : usage.candidatesTokenCount().orElse(0) + usage.thoughtsTokenCount().orElse(0);

            return new AiResult(text, response.modelVersion().orElse(model), inputTokens, outputTokens);
        } catch (ApiException e) {
            AiProviderException.Kind kind = StatusCodeClassifier.classify(e.code());
            log.error("Gemini API returned an error status ({})", e.code(), e);
            throw new AiProviderException(kind, "Gemini API request failed: " + e.getMessage(), e);
        } catch (GenAiIOException e) {
            log.error("Failed to call the Gemini API", e);
            throw new AiProviderException(AiProviderException.Kind.TEMPORARY, "Unable to reach the Gemini API", e);
        }
    }
}
