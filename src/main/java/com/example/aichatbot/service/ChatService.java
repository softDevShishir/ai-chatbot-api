package com.example.aichatbot.service;

import com.example.aichatbot.domain.Conversation;
import com.example.aichatbot.domain.Message;
import com.example.aichatbot.domain.MessageRole;
import com.example.aichatbot.exception.ApiBadRequestException;
import com.example.aichatbot.exception.ApiConflictException;
import com.example.aichatbot.exception.ApiNotFoundException;
import com.example.aichatbot.exception.TooManyRequestsException;
import com.example.aichatbot.provider.AiCompletionService;
import com.example.aichatbot.provider.ChatTurn;
import com.example.aichatbot.provider.CompletionOutcome;
import com.example.aichatbot.repository.ConversationRepository;
import com.example.aichatbot.repository.MessageRepository;
import com.example.aichatbot.retrieval.KnowledgeItem;
import com.example.aichatbot.retrieval.KnowledgeSearch;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Core chat flow: save the customer message, retrieve relevant store
 * knowledge, build the system prompt and bounded history, call the AI, strip
 * the handoff marker, save the reply, and enforce the cost/abuse guardrails
 * along the way.
 */
@Service
public class ChatService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final KnowledgeSearch knowledgeSearch;
    private final SystemPromptBuilder systemPromptBuilder;
    private final ChatHistoryBuilder chatHistoryBuilder;
    private final AiCompletionService aiCompletionService;
    private final RateLimiter rateLimiter;
    private final HandoffService handoffService;
    private final int maxMessageChars;
    private final int maxMessagesPerConversation;
    private final int maxAiCallsPerDay;
    private final ZoneId appZone;

    public ChatService(ConversationRepository conversationRepository,
                        MessageRepository messageRepository,
                        KnowledgeSearch knowledgeSearch,
                        SystemPromptBuilder systemPromptBuilder,
                        ChatHistoryBuilder chatHistoryBuilder,
                        AiCompletionService aiCompletionService,
                        RateLimiter rateLimiter,
                        HandoffService handoffService,
                        @Value("${chat.max-message-chars}") int maxMessageChars,
                        @Value("${chat.max-messages-per-conversation}") int maxMessagesPerConversation,
                        @Value("${chat.max-ai-calls-per-day}") int maxAiCallsPerDay,
                        @Value("${app.timezone}") String timezone) {
        this.conversationRepository = conversationRepository;
        this.messageRepository = messageRepository;
        this.knowledgeSearch = knowledgeSearch;
        this.systemPromptBuilder = systemPromptBuilder;
        this.chatHistoryBuilder = chatHistoryBuilder;
        this.aiCompletionService = aiCompletionService;
        this.rateLimiter = rateLimiter;
        this.handoffService = handoffService;
        this.maxMessageChars = maxMessageChars;
        this.maxMessagesPerConversation = maxMessagesPerConversation;
        this.maxAiCallsPerDay = maxAiCallsPerDay;
        this.appZone = ZoneId.of(timezone);
    }

    public Conversation createConversation(String owner, String title) {
        return conversationRepository.create(owner, blankToNull(title));
    }

    public List<Conversation> listConversations(String owner) {
        return conversationRepository.findAllByOwner(owner);
    }

    public ConversationWithMessages getConversation(String owner, UUID id) {
        Conversation conversation = requireOwnedConversation(owner, id);
        List<Message> messages = messageRepository.findByConversationId(id);
        return new ConversationWithMessages(conversation, messages);
    }

    public void deleteConversation(String owner, UUID id) {
        if (!conversationRepository.deleteByIdAndOwner(id, owner)) {
            throw new ApiNotFoundException("Conversation not found");
        }
    }

    public SendMessageResult sendMessage(String owner, UUID conversationId, String messageText, String requestedProvider) {
        requireOwnedConversation(owner, conversationId);

        if (messageText == null || messageText.isBlank()) {
            throw new ApiBadRequestException("message must not be blank");
        }
        if (messageText.length() > maxMessageChars) {
            throw new ApiBadRequestException("message must not exceed " + maxMessageChars + " characters");
        }
        if (messageRepository.countByConversationId(conversationId) >= maxMessagesPerConversation) {
            throw new ApiConflictException(
                    "This conversation has reached the maximum of " + maxMessagesPerConversation + " messages");
        }

        rateLimiter.checkAndRecord(owner);

        if (messageRepository.countAssistantMessagesBetween(startOfToday(), startOfTomorrow()) >= maxAiCallsPerDay) {
            throw new TooManyRequestsException(
                    "Daily AI call limit reached (" + maxAiCallsPerDay + "); please try again tomorrow");
        }

        messageRepository.insert(conversationId, MessageRole.USER, messageText, null, null, null, null);
        List<Message> history = messageRepository.findByConversationId(conversationId);

        List<KnowledgeItem> sources = knowledgeSearch.search(buildRetrievalQuery(history));
        String systemPrompt = systemPromptBuilder.build(sources);
        List<ChatTurn> turns = chatHistoryBuilder.build(history);

        CompletionOutcome outcome = aiCompletionService.complete(requestedProvider, systemPrompt, turns);

        String rawText = outcome.result().text().trim();
        boolean handoff = rawText.endsWith(SystemPromptBuilder.HANDOFF_MARKER);
        String replyText = handoff
                ? rawText.substring(0, rawText.length() - SystemPromptBuilder.HANDOFF_MARKER.length()).trim()
                : rawText;

        Message assistantMessage = messageRepository.insert(conversationId, MessageRole.ASSISTANT, replyText,
                outcome.providerId(), outcome.result().model(),
                (int) outcome.result().inputTokens(), (int) outcome.result().outputTokens());

        conversationRepository.touchUpdatedAt(conversationId);

        if (handoff) {
            // Never throws and never waits on Telegram (see HandoffService), so the reply is unaffected.
            handoffService.onHandoff(conversationId, owner, messageText);
        }

        return new SendMessageResult(replyText, assistantMessage.id(), outcome.providerId(), outcome.result().model(),
                outcome.result().inputTokens(), outcome.result().outputTokens(), sources, handoff, outcome.fallbackUsed());
    }

    private Conversation requireOwnedConversation(String owner, UUID id) {
        return conversationRepository.findByIdAndOwner(id, owner)
                .orElseThrow(() -> new ApiNotFoundException("Conversation not found"));
    }

    /**
     * Builds the retrieval query from the last two USER messages (the new one
     * plus the previous one, if any) so follow-ups like "what about the blue
     * one?" still retrieve relevant knowledge.
     */
    private static String buildRetrievalQuery(List<Message> historyOldestFirst) {
        List<String> userMessages = historyOldestFirst.stream()
                .filter(m -> m.role() == MessageRole.USER)
                .map(Message::content)
                .collect(Collectors.toCollection(ArrayList::new));

        int size = userMessages.size();
        List<String> lastTwo = size <= 2 ? userMessages : userMessages.subList(size - 2, size);
        return String.join(" ", lastTwo);
    }

    private Instant startOfToday() {
        return LocalDate.now(appZone).atStartOfDay(appZone).toInstant();
    }

    private Instant startOfTomorrow() {
        return LocalDate.now(appZone).plusDays(1).atStartOfDay(appZone).toInstant();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
