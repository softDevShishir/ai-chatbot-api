package com.example.aichatbot.controller;

import com.example.aichatbot.dto.ChatDetailResponse;
import com.example.aichatbot.dto.ChatSummaryResponse;
import com.example.aichatbot.dto.CreateChatRequest;
import com.example.aichatbot.dto.MessageResponse;
import com.example.aichatbot.dto.SendMessageRequest;
import com.example.aichatbot.dto.SendMessageResponse;
import com.example.aichatbot.dto.SourceResponse;
import com.example.aichatbot.domain.Conversation;
import com.example.aichatbot.domain.Message;
import com.example.aichatbot.service.ChatService;
import com.example.aichatbot.service.ConversationWithMessages;
import com.example.aichatbot.service.SendMessageResult;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Conversations and messages for the logged-in admin. Every endpoint here
 * requires a Bearer token (see {@code SecurityConfig}); a conversation id
 * that doesn't belong to the caller behaves as if it doesn't exist (404).
 */
@RestController
@RequestMapping("/api/chats")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping
    public ResponseEntity<ChatSummaryResponse> createChat(
            @Valid @RequestBody(required = false) CreateChatRequest request, Authentication auth) {
        String title = request == null ? null : request.title();
        Conversation conversation = chatService.createConversation(auth.getName(), title);
        return ResponseEntity.status(HttpStatus.CREATED).body(toSummary(conversation));
    }

    @GetMapping
    public List<ChatSummaryResponse> listChats(Authentication auth) {
        return chatService.listConversations(auth.getName()).stream().map(ChatController::toSummary).toList();
    }

    @GetMapping("/{id}")
    public ChatDetailResponse getChat(@PathVariable UUID id, Authentication auth) {
        ConversationWithMessages cwm = chatService.getConversation(auth.getName(), id);
        return toDetail(cwm);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteChat(@PathVariable UUID id, Authentication auth) {
        chatService.deleteConversation(auth.getName(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/messages")
    public SendMessageResponse sendMessage(@PathVariable UUID id, @Valid @RequestBody SendMessageRequest request,
                                            Authentication auth) {
        SendMessageResult result = chatService.sendMessage(auth.getName(), id, request.message(), request.provider());
        return toResponse(result);
    }

    private static ChatSummaryResponse toSummary(Conversation conversation) {
        return new ChatSummaryResponse(conversation.id(), conversation.title(),
                conversation.createdAt(), conversation.updatedAt());
    }

    private static ChatDetailResponse toDetail(ConversationWithMessages cwm) {
        List<MessageResponse> messages = cwm.messages().stream().map(ChatController::toMessage).toList();
        Conversation conversation = cwm.conversation();
        return new ChatDetailResponse(conversation.id(), conversation.title(),
                conversation.createdAt(), conversation.updatedAt(), messages);
    }

    private static MessageResponse toMessage(Message message) {
        return new MessageResponse(message.id(), message.role().name(), message.content(),
                message.provider(), message.model(), message.inputTokens(), message.outputTokens(),
                message.createdAt());
    }

    private static SendMessageResponse toResponse(SendMessageResult result) {
        List<SourceResponse> sources = result.sources().stream()
                .map(item -> new SourceResponse(item.sourceType(), item.id(), item.title()))
                .toList();
        return new SendMessageResponse(result.reply(), result.messageId(), result.provider(), result.model(),
                result.inputTokens(), result.outputTokens(), sources, result.handoff(), result.fallbackUsed());
    }
}
