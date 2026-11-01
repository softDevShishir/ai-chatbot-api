package com.example.aichatbot.provider;

import com.example.aichatbot.exception.ApiBadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Resolves which {@link AiProvider} answers a chat request (per-request
 * choice, else {@code DEFAULT_PROVIDER}), retries once on a retryable
 * failure, and falls back to {@code AI_FALLBACK_PROVIDER} (if configured and
 * available) when the chosen provider ultimately fails.
 */
@Service
public class AiCompletionService {

    private static final Logger log = LoggerFactory.getLogger(AiCompletionService.class);

    private final Map<String, AiProvider> providersById;
    private final String defaultProviderId;
    private final String fallbackProviderId;

    public AiCompletionService(List<AiProvider> providers,
                                @Value("${app.default-provider}") String defaultProviderId,
                                @Value("${app.fallback-provider:}") String fallbackProviderId) {
        this.providersById = providers.stream()
                .collect(Collectors.toMap(AiProvider::id, Function.identity()));

        String normalizedDefault = normalize(defaultProviderId);
        if (!providersById.containsKey(normalizedDefault)) {
            throw new IllegalArgumentException(
                    "DEFAULT_PROVIDER must be one of " + providersById.keySet() + ", but was \"" + defaultProviderId + "\"");
        }
        this.defaultProviderId = normalizedDefault;

        if (fallbackProviderId == null || fallbackProviderId.isBlank()) {
            this.fallbackProviderId = null;
        } else {
            String normalizedFallback = normalize(fallbackProviderId);
            if (!providersById.containsKey(normalizedFallback)) {
                throw new IllegalArgumentException(
                        "AI_FALLBACK_PROVIDER must be one of " + providersById.keySet() + ", but was \"" + fallbackProviderId + "\"");
            }
            this.fallbackProviderId = normalizedFallback;
        }
    }

    /**
     * Answers using {@code requestedProviderId} if given (validated below),
     * else {@code DEFAULT_PROVIDER}.
     *
     * @throws ApiBadRequestException  if the requested provider id is unknown or not configured
     * @throws AiProviderException     if the call (and any retry/fallback) ultimately fails
     */
    public CompletionOutcome complete(String requestedProviderId, String systemPrompt, List<ChatTurn> turns) {
        String providerId = resolveAndValidate(requestedProviderId);
        AiProvider provider = providersById.get(providerId);

        try {
            AiResult result = callWithRetry(provider, systemPrompt, turns);
            return new CompletionOutcome(result, providerId, false);
        } catch (AiProviderException primaryFailure) {
            AiProvider fallback = fallbackProviderId == null || fallbackProviderId.equals(providerId)
                    ? null
                    : providersById.get(fallbackProviderId);

            if (fallback == null || !fallback.isAvailable()) {
                throw primaryFailure;
            }

            log.warn("Provider \"{}\" failed ({}); retrying with fallback \"{}\"",
                    providerId, primaryFailure.kind(), fallbackProviderId);
            AiResult result = callWithRetry(fallback, systemPrompt, turns);
            return new CompletionOutcome(result, fallbackProviderId, true);
        }
    }

    private AiResult callWithRetry(AiProvider provider, String systemPrompt, List<ChatTurn> turns) {
        try {
            return provider.chat(systemPrompt, turns);
        } catch (AiProviderException ex) {
            if (!ex.isRetryable()) {
                throw ex;
            }
            log.warn("Provider \"{}\" call failed ({}); retrying once", provider.id(), ex.kind());
            return provider.chat(systemPrompt, turns);
        }
    }

    private String resolveAndValidate(String requested) {
        String providerId = requested == null || requested.isBlank() ? defaultProviderId : normalize(requested);

        AiProvider provider = providersById.get(providerId);
        if (provider == null) {
            throw new ApiBadRequestException(
                    "Unknown provider \"" + requested + "\". Valid providers: " + providersById.keySet());
        }
        if (!provider.isAvailable()) {
            throw new ApiBadRequestException("Provider \"" + providerId + "\" is not configured");
        }
        return providerId;
    }

    private static String normalize(String providerId) {
        return providerId == null ? "" : providerId.trim().toLowerCase(Locale.ROOT);
    }
}
