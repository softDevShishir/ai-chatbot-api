package com.example.aichatbot.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

/**
 * Sends one plain-text Telegram message per handoff via the Bot API {@code sendMessage} method.
 *
 * <p>Never throws: when Telegram is not configured, or the call fails, it logs a warning and
 * returns {@code false}. The bot token is part of the request URL, so anything derived from an
 * exception is passed through {@link #redact} before it is logged, and exceptions themselves are
 * never logged (their stack traces would carry the unredacted message).
 */
public class TelegramNotifier {

    private static final Logger log = LoggerFactory.getLogger(TelegramNotifier.class);

    static final int MAX_CUSTOMER_MESSAGE_CHARS = 300;
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z");

    private final TelegramConfig config;
    private final RestClient restClient;
    private final String storeName;
    private final ZoneId zone;

    public TelegramNotifier(TelegramConfig config, RestClient restClient, String storeName, ZoneId zone) {
        this.config = config;
        this.restClient = restClient;
        this.storeName = storeName;
        this.zone = zone;
    }

    /** A RestClient with short connect/read timeouts, so a slow Telegram can't hang a worker thread. */
    public static RestClient newRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(TIMEOUT);
        factory.setReadTimeout(TIMEOUT);
        return RestClient.builder().requestFactory(factory).build();
    }

    public boolean isEnabled() {
        return config.isConfigured();
    }

    /**
     * @return true only if Telegram accepted the message
     */
    public boolean sendHandoffAlert(UUID handoffId, UUID conversationId, Instant createdAt, String lastCustomerMessage) {
        if (!config.isConfigured()) {
            log.warn("Telegram is not configured (TELEGRAM_BOT_TOKEN/TELEGRAM_CHAT_ID); "
                    + "handoff {} alert not sent", handoffId);
            return false;
        }
        try {
            String text = buildMessage(storeName, conversationId, createdAt, zone, lastCustomerMessage);
            // No parse_mode: Telegram treats the text as plain text, so customer-supplied
            // characters can't inject formatting or links-with-labels.
            restClient.post()
                    // Built as a URI (not a template) so the ':' in the token is left as-is.
                    .uri(URI.create(config.apiBaseUrl() + "/bot" + config.botToken() + "/sendMessage"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("chat_id", config.chatId(), "text", text))
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (RuntimeException e) {
            log.warn("Telegram alert for handoff {} failed: {}", handoffId,
                    redact(e.getClass().getSimpleName() + ": " + e.getMessage()));
            return false;
        }
    }

    static String buildMessage(String storeName, UUID conversationId, Instant createdAt, ZoneId zone,
                               String lastCustomerMessage) {
        return storeName + ": customer needs help\n"
                + "Conversation: " + conversationId + "\n"
                + "Time: " + TIME_FORMAT.format(createdAt.atZone(zone)) + "\n"
                + "Last customer message: " + truncate(lastCustomerMessage, MAX_CUSTOMER_MESSAGE_CHARS);
    }

    /** Cuts to at most {@code max} characters in total (a trailing "…" marks a cut), never splitting a surrogate pair. */
    static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        if (text.length() <= max) {
            return text;
        }
        int end = max - 1;
        if (Character.isHighSurrogate(text.charAt(end - 1))) {
            end--;
        }
        return text.substring(0, end) + "…";
    }

    /** Removes the bot token (raw, percent-encoded, or as a /bot…/ URL segment) from text about to be logged. */
    String redact(String text) {
        if (text == null) {
            return null;
        }
        String token = config.botToken();
        String out = text;
        if (!token.isEmpty()) {
            out = out.replace(token, "***").replace(token.replace(":", "%3A"), "***");
        }
        // Belt and braces: even if the exact token is not recognised, never leave a /bot<secret>/ segment.
        return out.replaceAll("/bot[^/\\s\"']+/", "/bot***/");
    }
}
