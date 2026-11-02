package com.example.aichatbot.repository;

import com.example.aichatbot.domain.HandoffRequest;
import com.example.aichatbot.domain.HandoffStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Runs the real V6 migration on H2 (the test profile's database). */
@SpringBootTest
@ActiveProfiles("test")
class HandoffRepositoryTest {

    private static final String OWNER = "repo-test-owner";

    @Autowired
    private HandoffRepository handoffRepository;

    @Autowired
    private ConversationRepository conversationRepository;

    private UUID newConversation() {
        return conversationRepository.create(OWNER, "t").id();
    }

    @Test
    void insertCreatesOneOpenUnnotifiedRow() {
        UUID conversationId = newConversation();

        Optional<HandoffRequest> created = handoffRepository.insertIfNoneOpen(conversationId, OWNER, "need a human");

        assertThat(created).isPresent();
        HandoffRequest stored = handoffRepository.findByIdAndOwner(created.get().id(), OWNER).orElseThrow();
        assertThat(stored.status()).isEqualTo(HandoffStatus.OPEN);
        assertThat(stored.notified()).isFalse();
        assertThat(stored.resolvedAt()).isNull();
        assertThat(stored.lastCustomerMessage()).isEqualTo("need a human");
    }

    @Test
    void secondInsertWhileOpenIsRejectedByTheDatabase() {
        UUID conversationId = newConversation();
        handoffRepository.insertIfNoneOpen(conversationId, OWNER, "first");

        Optional<HandoffRequest> second = handoffRepository.insertIfNoneOpen(conversationId, OWNER, "second");

        assertThat(second).isEmpty();
        assertThat(handoffRepository.findByOwnerAndStatus(OWNER, HandoffStatus.OPEN))
                .filteredOn(h -> h.conversationId().equals(conversationId)).hasSize(1);
    }

    @Test
    void afterResolveANewHandoffCanBeOpened() {
        UUID conversationId = newConversation();
        HandoffRequest first = handoffRepository.insertIfNoneOpen(conversationId, OWNER, "first").orElseThrow();

        assertThat(handoffRepository.resolve(first.id(), OWNER)).isTrue();
        assertThat(handoffRepository.resolve(first.id(), OWNER)).isFalse(); // already resolved
        Optional<HandoffRequest> again = handoffRepository.insertIfNoneOpen(conversationId, OWNER, "again");

        assertThat(again).isPresent();
        HandoffRequest resolved = handoffRepository.findByIdAndOwner(first.id(), OWNER).orElseThrow();
        assertThat(resolved.status()).isEqualTo(HandoffStatus.RESOLVED);
        assertThat(resolved.resolvedAt()).isNotNull();
    }

    @Test
    void markNotifiedAndOwnerScoping() {
        UUID conversationId = newConversation();
        HandoffRequest h = handoffRepository.insertIfNoneOpen(conversationId, OWNER, "x").orElseThrow();

        handoffRepository.markNotified(h.id());

        assertThat(handoffRepository.findByIdAndOwner(h.id(), OWNER).orElseThrow().notified()).isTrue();
        assertThat(handoffRepository.findByIdAndOwner(h.id(), "someone-else")).isEmpty();
        assertThat(handoffRepository.resolve(h.id(), "someone-else")).isFalse();
    }
}
