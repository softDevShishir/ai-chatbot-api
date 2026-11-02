package com.example.aichatbot.service;

import com.example.aichatbot.domain.Conversation;
import com.example.aichatbot.domain.Message;
import com.example.aichatbot.domain.MessageRole;
import com.example.aichatbot.exception.ApiBadRequestException;
import com.example.aichatbot.exception.ApiConflictException;
import com.example.aichatbot.exception.ApiNotFoundException;
import com.example.aichatbot.exception.TooManyRequestsException;
import com.example.aichatbot.provider.AiCompletionService;
import com.example.aichatbot.provider.AiResult;
import com.example.aichatbot.provider.CompletionOutcome;
import com.example.aichatbot.repository.ConversationRepository;
import com.example.aichatbot.repository.MessageRepository;
import com.example.aichatbot.retrieval.KnowledgeItem;
import com.example.aichatbot.retrieval.KnowledgeSearch;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ChatServiceTest {

    private static final String OWNER = "test-admin";
    private static final UUID CONVERSATION_ID = UUID.randomUUID();

    private ConversationRepository conversationRepository;
    private MessageRepository messageRepository;
    private KnowledgeSearch knowledgeSearch;
    private AiCompletionService aiCompletionService;
    private RateLimiter rateLimiter;
    private HandoffService handoffService;

    @BeforeEach
    void setUp() {
        conversationRepository = mock(ConversationRepository.class);
        messageRepository = mock(MessageRepository.class);
        knowledgeSearch = mock(KnowledgeSearch.class);
        aiCompletionService = mock(AiCompletionService.class);
        rateLimiter = mock(RateLimiter.class);
        handoffService = mock(HandoffService.class);

        Conversation conversation = new Conversation(CONVERSATION_ID, OWNER, "Help", Instant.now(), Instant.now());
        when(conversationRepository.findByIdAndOwner(CONVERSATION_ID, OWNER)).thenReturn(Optional.of(conversation));
        when(messageRepository.countByConversationId(CONVERSATION_ID)).thenReturn(0);
        when(messageRepository.countAssistantMessagesBetween(any(), any())).thenReturn(0);
        when(messageRepository.findByConversationId(CONVERSATION_ID)).thenReturn(List.of(
                userMessage("Do you have running shoes?")));
        when(knowledgeSearch.search(anyString())).thenReturn(List.of());
    }

    private static Message userMessage(String content) {
        return new Message(UUID.randomUUID(), CONVERSATION_ID, MessageRole.USER, content, null, null, null, null, Instant.now());
    }

    private ChatService service(int maxMessageChars, int maxMessagesPerConversation, int maxAiCallsPerDay) {
        return new ChatService(conversationRepository, messageRepository, knowledgeSearch,
                new SystemPromptBuilder("Test Store"), new ChatHistoryBuilder(10, 6000),
                aiCompletionService, rateLimiter, handoffService, maxMessageChars, maxMessagesPerConversation,
                maxAiCallsPerDay, "Asia/Dhaka");
    }

    @Test
    void returnsNotFoundForAnotherUsersConversation() {
        ChatService service = service(2000, 100, 200);

        assertThatThrownBy(() -> service.sendMessage("someone-else", CONVERSATION_ID, "hi", null))
                .isInstanceOf(ApiNotFoundException.class);
    }

    @Test
    void getConversationReturnsNotFoundWhenNotOwned() {
        ChatService service = service(2000, 100, 200);

        assertThatThrownBy(() -> service.getConversation("someone-else", CONVERSATION_ID))
                .isInstanceOf(ApiNotFoundException.class);
    }

    @Test
    void rejectsMessageLongerThanConfiguredLimit() {
        ChatService service = service(5, 100, 200);

        assertThatThrownBy(() -> service.sendMessage(OWNER, CONVERSATION_ID, "123456", null))
                .isInstanceOf(ApiBadRequestException.class)
                .hasMessageContaining("5 characters");
    }

    @Test
    void rejectsWhenConversationHasReachedMaxMessages() {
        when(messageRepository.countByConversationId(CONVERSATION_ID)).thenReturn(100);
        ChatService service = service(2000, 100, 200);

        assertThatThrownBy(() -> service.sendMessage(OWNER, CONVERSATION_ID, "hi", null))
                .isInstanceOf(ApiConflictException.class);
    }

    @Test
    void propagatesRateLimitExceeded() {
        org.mockito.Mockito.doThrow(new TooManyRequestsException("slow down")).when(rateLimiter).checkAndRecord(OWNER);
        ChatService service = service(2000, 100, 200);

        assertThatThrownBy(() -> service.sendMessage(OWNER, CONVERSATION_ID, "hi", null))
                .isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void rejectsWhenDailyAiCallCapReached() {
        when(messageRepository.countAssistantMessagesBetween(any(), any())).thenReturn(200);
        ChatService service = service(2000, 100, 200);

        assertThatThrownBy(() -> service.sendMessage(OWNER, CONVERSATION_ID, "hi", null))
                .isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void stripsHandoffMarkerAndSetsFlag() {
        when(aiCompletionService.complete(any(), anyString(), any())).thenReturn(
                new CompletionOutcome(new AiResult("I can't help with that. [[HANDOFF]]", "claude-test", 10, 5),
                        "claude", false));
        when(messageRepository.insert(eq(CONVERSATION_ID), eq(MessageRole.ASSISTANT), anyString(), any(), any(), any(), any()))
                .thenAnswer(invocation -> new Message(UUID.randomUUID(), CONVERSATION_ID, MessageRole.ASSISTANT,
                        invocation.getArgument(2), "claude", "claude-test", 10, 5, Instant.now()));

        ChatService service = service(2000, 100, 200);
        SendMessageResult result = service.sendMessage(OWNER, CONVERSATION_ID, "I need a human", null);

        assertThat(result.handoff()).isTrue();
        assertThat(result.reply()).isEqualTo("I can't help with that.");
        verify(handoffService).onHandoff(CONVERSATION_ID, OWNER, "I need a human");
    }

    @Test
    void doesNotSetHandoffWhenMarkerAbsent() {
        when(aiCompletionService.complete(any(), anyString(), any())).thenReturn(
                new CompletionOutcome(new AiResult("Yes, we have running shoes.", "claude-test", 10, 5), "claude", false));
        when(messageRepository.insert(eq(CONVERSATION_ID), eq(MessageRole.ASSISTANT), anyString(), any(), any(), any(), any()))
                .thenAnswer(invocation -> new Message(UUID.randomUUID(), CONVERSATION_ID, MessageRole.ASSISTANT,
                        invocation.getArgument(2), "claude", "claude-test", 10, 5, Instant.now()));

        ChatService service = service(2000, 100, 200);
        SendMessageResult result = service.sendMessage(OWNER, CONVERSATION_ID, "Do you have running shoes?", null);

        assertThat(result.handoff()).isFalse();
        assertThat(result.reply()).isEqualTo("Yes, we have running shoes.");
        verifyNoInteractions(handoffService);
    }

    @Test
    void returnsSourcesAndFallbackFlagFromCompletion() {
        KnowledgeItem item = new KnowledgeItem(KnowledgeItem.PRODUCT, "1", "Running Shoes", "Price: 3499.00 BDT");
        when(knowledgeSearch.search(anyString())).thenReturn(List.of(item));
        when(aiCompletionService.complete(any(), anyString(), any())).thenReturn(
                new CompletionOutcome(new AiResult("Yes.", "gemini-test", 10, 5), "gemini", true));
        when(messageRepository.insert(eq(CONVERSATION_ID), eq(MessageRole.ASSISTANT), anyString(), any(), any(), any(), any()))
                .thenAnswer(invocation -> new Message(UUID.randomUUID(), CONVERSATION_ID, MessageRole.ASSISTANT,
                        invocation.getArgument(2), "gemini", "gemini-test", 10, 5, Instant.now()));

        ChatService service = service(2000, 100, 200);
        SendMessageResult result = service.sendMessage(OWNER, CONVERSATION_ID, "Do you have running shoes?", null);

        assertThat(result.fallbackUsed()).isTrue();
        assertThat(result.sources()).containsExactly(item);
        assertThat(result.provider()).isEqualTo("gemini");
    }
}
