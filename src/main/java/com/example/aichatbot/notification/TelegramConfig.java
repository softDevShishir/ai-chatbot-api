package com.example.aichatbot.notification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Telegram settings, read only from the environment (TELEGRAM_BOT_TOKEN, TELEGRAM_CHAT_ID,
 * TELEGRAM_API_BASE_URL). The bot token is a secret: it is never exposed by {@link #toString()}.
 */
@Component
public class TelegramConfig {

    public static final String DEFAULT_API_BASE_URL = "https://api.telegram.org";

    private final String botToken;
    private final String chatId;
    private final String apiBaseUrl;

    public TelegramConfig(@Value("${telegram.bot-token:}") String botToken,
                          @Value("${telegram.chat-id:}") String chatId,
                          @Value("${telegram.api-base-url:" + DEFAULT_API_BASE_URL + "}") String apiBaseUrl) {
        this.botToken = botToken == null ? "" : botToken.trim();
        this.chatId = chatId == null ? "" : chatId.trim();
        String base = apiBaseUrl == null || apiBaseUrl.isBlank() ? DEFAULT_API_BASE_URL : apiBaseUrl.trim();
        this.apiBaseUrl = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    }

    /** Alerts are sent only when both the token and the chat id are non-blank. */
    public boolean isConfigured() {
        return !botToken.isEmpty() && !chatId.isEmpty();
    }

    String botToken() {
        return botToken;
    }

    String chatId() {
        return chatId;
    }

    String apiBaseUrl() {
        return apiBaseUrl;
    }

    @Override
    public String toString() {
        return "TelegramConfig[botToken=***, chatId=" + chatId + ", apiBaseUrl=" + apiBaseUrl + "]";
    }
}
