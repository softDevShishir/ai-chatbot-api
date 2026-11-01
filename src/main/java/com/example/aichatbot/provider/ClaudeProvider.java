package com.example.aichatbot.provider;

import com.anthropic.client.AnthropicClient;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Model;
import com.anthropic.models.messages.TextBlock;
import com.example.aichatbot.domain.MessageRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * {@link AiProvider} backed by the official Anthropic Java SDK.
 */
@Component
public class ClaudeProvider implements AiProvider {

    public static final String ID = "claude";

    private static final Logger log = LoggerFactory.getLogger(ClaudeProvider.class);

    private final AnthropicClient client;
    private final String model;
    private final long maxTokens;
    private final boolean configured;

    public ClaudeProvider(AnthropicClient client,
                           @Value("${claude.model}") String model,
                           @Value("${claude.max-tokens}") long maxTokens,
                           @Value("${claude.api-key:}") String apiKey) {
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
                    "Provider \"claude\" has no CLAUDE_API_KEY configured");
        }

        MessageCreateParams.Builder builder = MessageCreateParams.builder()
                .model(Model.of(model))
                .maxTokens(maxTokens);

        if (systemPrompt != null && !systemPrompt.isBlank()) {
            builder.system(systemPrompt);
        }
        for (ChatTurn turn : turns) {
            if (turn.role() == MessageRole.USER) {
                builder.addUserMessage(turn.content());
            } else {
                builder.addAssistantMessage(turn.content());
            }
        }

        MessageCreateParams params = builder.build();

        try {
            Message response = client.messages().create(params);

            String text = response.content().stream()
                    .flatMap(block -> block.text().stream())
                    .map(TextBlock::text)
                    .collect(Collectors.joining());

            if (text.isBlank()) {
                throw new AiProviderException(AiProviderException.Kind.UNKNOWN, "Claude returned an empty response");
            }

            return new AiResult(text, response.model().asString(),
                    response.usage().inputTokens(), response.usage().outputTokens());
        } catch (AnthropicServiceException e) {
            AiProviderException.Kind kind = StatusCodeClassifier.classify(e.statusCode());
            log.error("Claude API returned an error status ({})", e.statusCode(), e);
            throw new AiProviderException(kind, "Claude API request failed: " + e.getMessage(), e);
        } catch (AnthropicException e) {
            log.error("Failed to call the Claude API", e);
            throw new AiProviderException(AiProviderException.Kind.TEMPORARY, "Unable to reach the Claude API", e);
        }
    }
}
