package com.example.aichatbot.provider;

import com.example.aichatbot.exception.ApiBadRequestException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiCompletionServiceTest {

    private AiProvider claude;
    private AiProvider gemini;
    private AiProvider openai;

    @BeforeEach
    void setUp() {
        claude = mock(AiProvider.class);
        when(claude.id()).thenReturn("claude");
        when(claude.isAvailable()).thenReturn(true);

        gemini = mock(AiProvider.class);
        when(gemini.id()).thenReturn("gemini");
        when(gemini.isAvailable()).thenReturn(true);

        openai = mock(AiProvider.class);
        when(openai.id()).thenReturn("openai");
        when(openai.isAvailable()).thenReturn(true);
    }

    private AiCompletionService service(String defaultProvider, String fallbackProvider) {
        return new AiCompletionService(List.of(claude, gemini, openai), defaultProvider, fallbackProvider);
    }

    @Test
    void usesRequestedProviderWhenGiven() {
        when(gemini.chat(anyString(), any())).thenReturn(new AiResult("hi", "gemini-test", 1, 1));

        CompletionOutcome outcome = service("claude", "").complete("gemini", "sys", List.of());

        assertThat(outcome.providerId()).isEqualTo("gemini");
        assertThat(outcome.fallbackUsed()).isFalse();
        verify(claude, times(0)).chat(anyString(), any());
    }

    @Test
    void fallsBackToDefaultWhenNoneRequested() {
        when(claude.chat(anyString(), any())).thenReturn(new AiResult("hi", "claude-test", 1, 1));

        CompletionOutcome outcome = service("claude", "").complete(null, "sys", List.of());

        assertThat(outcome.providerId()).isEqualTo("claude");
    }

    @Test
    void rejectsUnknownProviderWith400() {
        assertThatThrownBy(() -> service("claude", "").complete("mistral", "sys", List.of()))
                .isInstanceOf(ApiBadRequestException.class);
    }

    @Test
    void rejectsUnconfiguredProviderWith400() {
        when(gemini.isAvailable()).thenReturn(false);

        assertThatThrownBy(() -> service("claude", "").complete("gemini", "sys", List.of()))
                .isInstanceOf(ApiBadRequestException.class)
                .hasMessageContaining("not configured");
    }

    @Test
    void rejectsUnknownDefaultProviderAtConstruction() {
        assertThatThrownBy(() -> service("mistral", ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DEFAULT_PROVIDER");
    }

    @Test
    void retriesOnceOnRateLimitThenSucceeds() {
        when(claude.chat(anyString(), any()))
                .thenThrow(new AiProviderException(AiProviderException.Kind.RATE_LIMITED, "slow down"))
                .thenReturn(new AiResult("hi", "claude-test", 1, 1));

        CompletionOutcome outcome = service("claude", "").complete("claude", "sys", List.of());

        assertThat(outcome.result().text()).isEqualTo("hi");
        verify(claude, times(2)).chat(anyString(), any());
    }

    @Test
    void doesNotRetryOnAuthenticationFailure() {
        when(claude.chat(anyString(), any()))
                .thenThrow(new AiProviderException(AiProviderException.Kind.AUTHENTICATION, "bad key"));

        assertThatThrownBy(() -> service("claude", "").complete("claude", "sys", List.of()))
                .isInstanceOf(AiProviderException.class);
        verify(claude, times(1)).chat(anyString(), any());
    }

    @Test
    void doesNotRetryOnBadRequest() {
        when(claude.chat(anyString(), any()))
                .thenThrow(new AiProviderException(AiProviderException.Kind.BAD_REQUEST, "bad request"));

        assertThatThrownBy(() -> service("claude", "").complete("claude", "sys", List.of()))
                .isInstanceOf(AiProviderException.class);
        verify(claude, times(1)).chat(anyString(), any());
    }

    @Test
    void doesNotRetryOnEmptyResponse() {
        // Mirrors what ClaudeProvider/GeminiProvider/OpenAiProvider throw when the SDK call
        // succeeds but returns blank text: UNKNOWN kind, not retried, surfaces as 502.
        when(claude.chat(anyString(), any()))
                .thenThrow(new AiProviderException(AiProviderException.Kind.UNKNOWN, "Claude returned an empty response"));

        assertThatThrownBy(() -> service("claude", "").complete("claude", "sys", List.of()))
                .isInstanceOf(AiProviderException.class)
                .hasMessageContaining("empty response");
        verify(claude, times(1)).chat(anyString(), any());
    }

    @Test
    void fallsBackToConfiguredProviderWhenPrimaryFailsTwice() {
        when(claude.chat(anyString(), any()))
                .thenThrow(new AiProviderException(AiProviderException.Kind.TEMPORARY, "down"));
        when(gemini.chat(anyString(), any()))
                .thenReturn(new AiResult("fallback reply", "gemini-test", 1, 1));

        CompletionOutcome outcome = service("claude", "gemini").complete("claude", "sys", List.of());

        assertThat(outcome.fallbackUsed()).isTrue();
        assertThat(outcome.providerId()).isEqualTo("gemini");
        assertThat(outcome.result().text()).isEqualTo("fallback reply");
        verify(claude, times(2)).chat(anyString(), any());
    }

    @Test
    void rethrowsOriginalFailureWhenNoFallbackConfigured() {
        when(claude.chat(anyString(), any()))
                .thenThrow(new AiProviderException(AiProviderException.Kind.AUTHENTICATION, "bad key"));

        assertThatThrownBy(() -> service("claude", "").complete("claude", "sys", List.of()))
                .isInstanceOf(AiProviderException.class)
                .hasMessageContaining("bad key");
    }

    @Test
    void rethrowsOriginalFailureWhenFallbackUnavailable() {
        when(claude.chat(anyString(), any()))
                .thenThrow(new AiProviderException(AiProviderException.Kind.AUTHENTICATION, "bad key"));
        when(gemini.isAvailable()).thenReturn(false);

        assertThatThrownBy(() -> service("claude", "gemini").complete("claude", "sys", List.of()))
                .isInstanceOf(AiProviderException.class)
                .hasMessageContaining("bad key");
        verify(gemini, times(0)).chat(anyString(), any());
    }

    @Test
    void doesNotFallBackToSameProviderAsPrimary() {
        when(claude.chat(anyString(), any()))
                .thenThrow(new AiProviderException(AiProviderException.Kind.AUTHENTICATION, "bad key"));

        assertThatThrownBy(() -> service("claude", "claude").complete("claude", "sys", List.of()))
                .isInstanceOf(AiProviderException.class);
        verify(claude, times(1)).chat(anyString(), any());
    }
}
