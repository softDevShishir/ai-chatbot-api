package com.example.aichatbot.provider;

/**
 * Maps an HTTP status code from a provider SDK's service exception to an
 * {@link AiProviderException.Kind}, shared by all three providers.
 */
final class StatusCodeClassifier {

    private StatusCodeClassifier() {
    }

    static AiProviderException.Kind classify(int statusCode) {
        if (statusCode == 429) {
            return AiProviderException.Kind.RATE_LIMITED;
        }
        if (statusCode == 401 || statusCode == 403) {
            return AiProviderException.Kind.AUTHENTICATION;
        }
        if (statusCode >= 500) {
            return AiProviderException.Kind.TEMPORARY;
        }
        if (statusCode >= 400) {
            return AiProviderException.Kind.BAD_REQUEST;
        }
        return AiProviderException.Kind.UNKNOWN;
    }
}
