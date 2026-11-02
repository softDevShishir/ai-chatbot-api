package com.example.aichatbot.service;

import com.example.aichatbot.domain.HandoffRequest;
import com.example.aichatbot.domain.HandoffStatus;
import com.example.aichatbot.exception.ApiNotFoundException;
import com.example.aichatbot.notification.TelegramNotifier;
import com.example.aichatbot.repository.HandoffRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * "Customer needs a human" requests: records one per conversation while it is open, alerts staff
 * on Telegram in the background, and lets staff list and resolve them.
 */
@Service
public class HandoffService {

    private static final Logger log = LoggerFactory.getLogger(HandoffService.class);

    private final HandoffRepository handoffRepository;
    private final TelegramNotifier telegramNotifier;
    private final Executor alertExecutor;

    public HandoffService(HandoffRepository handoffRepository,
                          TelegramNotifier telegramNotifier,
                          @Qualifier("handoffAlertExecutor") Executor alertExecutor) {
        this.handoffRepository = handoffRepository;
        this.telegramNotifier = telegramNotifier;
        this.alertExecutor = alertExecutor;
    }

    /**
     * Called when a chat reply has handoff=true. Never throws and never blocks on Telegram: the
     * customer's reply must not depend on this succeeding. If the conversation already has an OPEN
     * handoff, nothing is created and no alert is sent.
     */
    public void onHandoff(UUID conversationId, String owner, String lastCustomerMessage) {
        try {
            HandoffRequest created = handoffRepository
                    .insertIfNoneOpen(conversationId, owner, lastCustomerMessage).orElse(null);
            if (created == null) {
                log.info("Conversation {} already has an open handoff; no new request or alert", conversationId);
                return;
            }
            dispatchAlert(created);
        } catch (RuntimeException e) {
            // Class name only: a DataAccessException message can quote SQL, and we never log customer text.
            log.warn("Could not record handoff for conversation {} ({}); the reply is unaffected",
                    conversationId, e.getClass().getSimpleName());
        }
    }

    private void dispatchAlert(HandoffRequest handoff) {
        if (!telegramNotifier.isEnabled()) {
            log.warn("Handoff {} saved but not alerted: Telegram is not configured", handoff.id());
            return;
        }
        try {
            alertExecutor.execute(() -> sendAlert(handoff));
        } catch (RuntimeException e) { // e.g. RejectedExecutionException when the queue is full
            log.warn("Handoff {} saved but its Telegram alert could not be queued ({})",
                    handoff.id(), e.getClass().getSimpleName());
        }
    }

    private void sendAlert(HandoffRequest handoff) {
        try {
            boolean sent = telegramNotifier.sendHandoffAlert(handoff.id(), handoff.conversationId(),
                    handoff.createdAt(), handoff.lastCustomerMessage());
            if (sent) {
                handoffRepository.markNotified(handoff.id());
            }
        } catch (RuntimeException e) {
            log.warn("Telegram alert for handoff {} failed ({})", handoff.id(), e.getClass().getSimpleName());
        }
    }

    public List<HandoffRequest> list(String owner, HandoffStatus status) {
        return handoffRepository.findByOwnerAndStatus(owner, status);
    }

    /** Resolving an already-resolved handoff is a no-op that returns it unchanged. */
    public HandoffRequest resolve(String owner, UUID id) {
        handoffRepository.resolve(id, owner);
        return handoffRepository.findByIdAndOwner(id, owner)
                .orElseThrow(() -> new ApiNotFoundException("Handoff not found"));
    }
}
