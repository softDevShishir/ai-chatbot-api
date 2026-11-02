package com.example.aichatbot.controller;

import com.example.aichatbot.domain.HandoffRequest;
import com.example.aichatbot.domain.HandoffStatus;
import com.example.aichatbot.dto.HandoffResponse;
import com.example.aichatbot.exception.ApiBadRequestException;
import com.example.aichatbot.service.HandoffService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Staff-facing view of "customer needs a human" requests. Requires a Bearer token (see
 * {@code SecurityConfig}); like conversations, handoffs are scoped to the logged-in owner, so an
 * id that isn't theirs behaves as if it doesn't exist (404).
 */
@RestController
@RequestMapping("/api/handoffs")
public class HandoffController {

    private final HandoffService handoffService;

    public HandoffController(HandoffService handoffService) {
        this.handoffService = handoffService;
    }

    @GetMapping
    public List<HandoffResponse> list(@RequestParam(defaultValue = "OPEN") String status, Authentication auth) {
        return handoffService.list(auth.getName(), parseStatus(status)).stream()
                .map(HandoffController::toResponse).toList();
    }

    @PostMapping("/{id}/resolve")
    public HandoffResponse resolve(@PathVariable UUID id, Authentication auth) {
        return toResponse(handoffService.resolve(auth.getName(), id));
    }

    private static HandoffStatus parseStatus(String value) {
        try {
            return HandoffStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ApiBadRequestException("status must be OPEN or RESOLVED");
        }
    }

    private static HandoffResponse toResponse(HandoffRequest h) {
        return new HandoffResponse(h.id(), h.conversationId(), h.status().name(), h.lastCustomerMessage(),
                h.notified(), h.createdAt(), h.resolvedAt());
    }
}
