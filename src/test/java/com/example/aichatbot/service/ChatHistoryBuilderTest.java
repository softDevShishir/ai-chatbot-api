package com.example.aichatbot.service;

import com.example.aichatbot.domain.Message;
import com.example.aichatbot.domain.MessageRole;
import com.example.aichatbot.provider.ChatTurn;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ChatHistoryBuilderTest {

    private static Message message(MessageRole role, String content) {
        return new Message(UUID.randomUUID(), UUID.randomUUID(), role, content, null, null, null, null, Instant.now());
    }

    @Test
    void worksWithEmptyHistory() {
        ChatHistoryBuilder builder = new ChatHistoryBuilder(10, 6000);

        List<ChatTurn> turns = builder.build(List.of());

        assertThat(turns).isEmpty();
    }

    @Test
    void includesAllMessagesInOrderWhenUnderLimits() {
        ChatHistoryBuilder builder = new ChatHistoryBuilder(10, 6000);
        Message m1 = message(MessageRole.USER, "hi");
        Message m2 = message(MessageRole.ASSISTANT, "hello");
        Message m3 = message(MessageRole.USER, "bye");

        List<ChatTurn> turns = builder.build(List.of(m1, m2, m3));

        assertThat(turns).containsExactly(
                new ChatTurn(MessageRole.USER, "hi"),
                new ChatTurn(MessageRole.ASSISTANT, "hello"),
                new ChatTurn(MessageRole.USER, "bye"));
    }

    @Test
    void limitsToTheLastNMessages() {
        ChatHistoryBuilder builder = new ChatHistoryBuilder(2, 6000);
        List<Message> history = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            history.add(message(MessageRole.USER, "message-" + i));
        }

        List<ChatTurn> turns = builder.build(history);

        assertThat(turns).extracting(ChatTurn::content).containsExactly("message-3", "message-4");
    }

    @Test
    void trimsOldestMessagesToFitCharBudget() {
        ChatHistoryBuilder builder = new ChatHistoryBuilder(10, 12);
        Message m1 = message(MessageRole.USER, "0123456789"); // 10 chars, would push total over budget
        Message m2 = message(MessageRole.ASSISTANT, "short"); // 5 chars
        Message m3 = message(MessageRole.USER, "ok"); // 2 chars

        List<ChatTurn> turns = builder.build(List.of(m1, m2, m3));

        // m1 (10) + m2 (5) + m3 (2) = 17 > 12, so the oldest (m1) is dropped: 5 + 2 = 7 <= 12
        assertThat(turns).extracting(ChatTurn::content).containsExactly("short", "ok");
    }

    @Test
    void alwaysKeepsAtLeastTheMostRecentMessageEvenOverBudget() {
        ChatHistoryBuilder builder = new ChatHistoryBuilder(10, 1);
        Message m1 = message(MessageRole.USER, "first message");
        Message m2 = message(MessageRole.USER, "second message, also long");

        List<ChatTurn> turns = builder.build(List.of(m1, m2));

        assertThat(turns).hasSize(1);
        assertThat(turns.get(0).content()).isEqualTo("second message, also long");
    }
}
