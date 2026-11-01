package com.example.aichatbot.provider;

import com.example.aichatbot.domain.MessageRole;
import com.openai.client.OpenAIClient;
import com.openai.errors.OpenAIException;
import com.openai.errors.OpenAIServiceException;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.completions.CompletionUsage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * {@link AiProvider} backed by the official OpenAI Java SDK (Chat Completions API).
 */
@Component
public class OpenAiProvider implements AiProvider {

    public static final String ID = "openai";

    private static final Logger log = LoggerFactory.getLogger(OpenAiProvider.class);

    private final OpenAIClient client;
    private final String model;
    private final long maxTokens;
    private final boolean configured;

    public OpenAiProvider(OpenAIClient client,
                           @Value("${openai.model}") String model,
                           @Value("${openai.max-tokens}") long maxTokens,
                           @Value("${openai.api-key:}") String apiKey) {
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
                    "Provider \"openai\" has no OPENAI_API_KEY configured");
        }

        // max_completion_tokens (not the deprecated max_tokens, which reasoning models reject)
        // caps visible output plus any hidden reasoning tokens.
        ChatCompletionCreateParams.Builder builder = ChatCompletionCreateParams.builder()
                .model(model)
                .maxCompletionTokens(maxTokens);

        if (systemPrompt != null && !systemPrompt.isBlank()) {
            builder.addSystemMessage(systemPrompt);
        }
        for (ChatTurn turn : turns) {
            if (turn.role() == MessageRole.USER) {
                builder.addUserMessage(turn.content());
            } else {
                builder.addAssistantMessage(turn.content());
            }
        }

        ChatCompletionCreateParams params = builder.build();

        try {
            ChatCompletion response = client.chat().completions().create(params);

            ChatCompletion.Choice choice = response.choices().isEmpty() ? null : response.choices().get(0);
            String text = choice == null ? null : choice.message().content().orElse(null);

            if (text == null || text.isBlank()) {
                String finishReason = choice == null ? "none" : choice.finishReason().asString();
                throw new AiProviderException(AiProviderException.Kind.UNKNOWN,
                        "OpenAI returned an empty response (finish reason: " + finishReason
                                + "). If it is \"length\", increase OPENAI_MAX_TOKENS");
            }

            CompletionUsage usage = response.usage().orElse(null);
            long inputTokens = usage == null ? 0 : usage.promptTokens();
            long outputTokens = usage == null ? 0 : usage.completionTokens();

            return new AiResult(text, response.model(), inputTokens, outputTokens);
        } catch (OpenAIServiceException e) {
            AiProviderException.Kind kind = StatusCodeClassifier.classify(e.statusCode());
            log.error("OpenAI API returned an error status ({})", e.statusCode(), e);
            throw new AiProviderException(kind, "OpenAI API request failed: " + e.getMessage(), e);
        } catch (OpenAIException e) {
            log.error("Failed to call the OpenAI API", e);
            throw new AiProviderException(AiProviderException.Kind.TEMPORARY, "Unable to reach the OpenAI API", e);
        }
    }
}
