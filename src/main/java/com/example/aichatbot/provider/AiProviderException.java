package com.example.aichatbot.provider;

/**
 * Wraps any failure from a {@link AiProvider} SDK call. {@code kind} drives
 * retry/fallback decisions in the chat flow and is also logged with the error.
 */
public class AiProviderException extends RuntimeException {

    public enum Kind {
        RATE_LIMITED,
        TEMPORARY,
        AUTHENTICATION,
        BAD_REQUEST,
        UNKNOWN
    }

    private final Kind kind;

    public AiProviderException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public AiProviderException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }

    public boolean isRetryable() {
        return kind == Kind.RATE_LIMITED || kind == Kind.TEMPORARY;
    }
}
