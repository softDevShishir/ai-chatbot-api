package com.example.aichatbot.service;

import com.example.aichatbot.domain.Message;
import com.example.aichatbot.provider.ChatTurn;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Turns a conversation's full message history into the bounded list of
 * {@link ChatTurn}s sent to the AI provider: the last
 * {@code CHAT_HISTORY_MESSAGES} messages, oldest first, then trimmed from the
 * oldest end until the total character budget is met (always keeping at
 * least the most recent message).
 */
@Component
public class ChatHistoryBuilder {

    private final int maxMessages;
    private final int charBudget;

    public ChatHistoryBuilder(@Value("${chat.history-messages}") int maxMessages,
                               @Value("${chat.history-char-budget}") int charBudget) {
        this.maxMessages = maxMessages;
        this.charBudget = charBudget;
    }

    /**
     * @param conversationMessagesOldestFirst the full conversation history, oldest first
     */
    public List<ChatTurn> build(List<Message> conversationMessagesOldestFirst) {
        List<Message> limited = conversationMessagesOldestFirst.size() <= maxMessages
                ? conversationMessagesOldestFirst
                : conversationMessagesOldestFirst.subList(
                        conversationMessagesOldestFirst.size() - maxMessages,
                        conversationMessagesOldestFirst.size());

        Deque<Message> trimmed = new ArrayDeque<>(limited);
        int totalChars = trimmed.stream().mapToInt(m -> m.content().length()).sum();
        while (totalChars > charBudget && trimmed.size() > 1) {
            Message removed = trimmed.pollFirst();
            totalChars -= removed.content().length();
        }

        return trimmed.stream().map(m -> new ChatTurn(m.role(), m.content())).toList();
    }
}
