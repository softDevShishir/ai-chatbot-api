package com.example.aichatbot.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.aichatbot.domain.HandoffRequest;
import com.example.aichatbot.domain.HandoffStatus;
import com.example.aichatbot.exception.ApiNotFoundException;
import com.example.aichatbot.notification.TelegramNotifier;
import com.example.aichatbot.repository.HandoffRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Pure unit tests: mocked repository and notifier, and a same-thread executor so results are deterministic. */
class HandoffServiceTest {

    private static final String OWNER = "test-admin";
    private static final UUID CONVERSATION_ID = UUID.randomUUID();

    private final Logger rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

    private HandoffRepository repository;
    private TelegramNotifier notifier;
    private HandoffService service;

    @BeforeEach
    void setUp() {
        logs.start();
        rootLogger.addAppender(logs);
        repository = mock(HandoffRepository.class);
        notifier = mock(TelegramNotifier.class);
        when(notifier.isEnabled()).thenReturn(true);
        service = new HandoffService(repository, notifier, Runnable::run);
    }

    @AfterEach
    void tearDown() {
        rootLogger.detachAppender(logs);
    }

    private String allLogs() {
        return logs.list.stream().map(ILoggingEvent::getFormattedMessage).collect(Collectors.joining("\n"));
    }

    private static HandoffRequest created(String message) {
        return new HandoffRequest(UUID.randomUUID(), CONVERSATION_ID, OWNER, message, HandoffStatus.OPEN,
                false, Instant.now(), null);
    }

    @Test
    void newHandoffIsSavedAlertedAndMarkedNotified() {
        HandoffRequest row = created("I need a human");
        when(repository.insertIfNoneOpen(CONVERSATION_ID, OWNER, "I need a human")).thenReturn(Optional.of(row));
        when(notifier.sendHandoffAlert(row.id(), CONVERSATION_ID, row.createdAt(), "I need a human")).thenReturn(true);

        service.onHandoff(CONVERSATION_ID, OWNER, "I need a human");

        verify(repository).insertIfNoneOpen(CONVERSATION_ID, OWNER, "I need a human");
        verify(notifier).sendHandoffAlert(row.id(), CONVERSATION_ID, row.createdAt(), "I need a human");
        verify(repository).markNotified(row.id());
    }

    @Test
    void secondHandoffWhileOneIsOpenCreatesNoRowAndSendsNoAlert() {
        HandoffRequest row = created("first");
        when(repository.insertIfNoneOpen(CONVERSATION_ID, OWNER, "first")).thenReturn(Optional.of(row));
        when(repository.insertIfNoneOpen(CONVERSATION_ID, OWNER, "second")).thenReturn(Optional.empty());
        when(notifier.sendHandoffAlert(any(), any(), any(), anyString())).thenReturn(true);

        service.onHandoff(CONVERSATION_ID, OWNER, "first");
        service.onHandoff(CONVERSATION_ID, OWNER, "second");

        verify(notifier, times(1)).sendHandoffAlert(any(), any(), any(), anyString());
        verify(repository, times(1)).markNotified(any());
    }

    @Test
    void telegramFailureKeepsNotifiedFalseAndDoesNotThrow() {
        HandoffRequest row = created("help");
        when(repository.insertIfNoneOpen(CONVERSATION_ID, OWNER, "help")).thenReturn(Optional.of(row));
        when(notifier.sendHandoffAlert(any(), any(), any(), anyString())).thenReturn(false);

        assertThatCode(() -> service.onHandoff(CONVERSATION_ID, OWNER, "help")).doesNotThrowAnyException();

        verify(repository, never()).markNotified(any());
    }

    @Test
    void unexpectedNotifierExceptionDoesNotThrowAndKeepsNotifiedFalse() {
        HandoffRequest row = created("help");
        when(repository.insertIfNoneOpen(CONVERSATION_ID, OWNER, "help")).thenReturn(Optional.of(row));
        when(notifier.sendHandoffAlert(any(), any(), any(), anyString())).thenThrow(new IllegalStateException("boom"));

        assertThatCode(() -> service.onHandoff(CONVERSATION_ID, OWNER, "help")).doesNotThrowAnyException();

        verify(repository, never()).markNotified(any());
        assertThat(allLogs()).contains("failed").doesNotContain("boom");
    }

    @Test
    void notConfiguredSavesTheHandoffLogsAWarningAndSendsNothing() {
        when(notifier.isEnabled()).thenReturn(false);
        HandoffRequest row = created("help");
        when(repository.insertIfNoneOpen(CONVERSATION_ID, OWNER, "help")).thenReturn(Optional.of(row));

        service.onHandoff(CONVERSATION_ID, OWNER, "help");

        verify(repository).insertIfNoneOpen(CONVERSATION_ID, OWNER, "help");
        verify(notifier, never()).sendHandoffAlert(any(), any(), any(), anyString());
        verify(repository, never()).markNotified(any());
        assertThat(logs.list).anyMatch(e -> e.getLevel().toString().equals("WARN")
                && e.getFormattedMessage().contains("not configured"));
    }

    @Test
    void fullAlertQueueIsHandledWithoutThrowing() {
        Executor rejecting = r -> {
            throw new RejectedExecutionException("queue full");
        };
        service = new HandoffService(repository, notifier, rejecting);
        when(repository.insertIfNoneOpen(CONVERSATION_ID, OWNER, "help")).thenReturn(Optional.of(created("help")));

        assertThatCode(() -> service.onHandoff(CONVERSATION_ID, OWNER, "help")).doesNotThrowAnyException();

        verify(repository, never()).markNotified(any());
    }

    @Test
    void databaseFailureWhileRecordingDoesNotThrowAndDoesNotLogCustomerText() {
        when(repository.insertIfNoneOpen(any(), anyString(), anyString()))
                .thenThrow(new org.springframework.dao.DataAccessResourceFailureException("db down: secret customer text"));

        assertThatCode(() -> service.onHandoff(CONVERSATION_ID, OWNER, "secret customer text"))
                .doesNotThrowAnyException();

        assertThat(allLogs()).contains("Could not record handoff").doesNotContain("secret customer text");
        verify(notifier, never()).sendHandoffAlert(any(), any(), any(), anyString());
    }

    @Test
    void resolveReturnsTheRowAndMissingIdIsNotFound() {
        HandoffRequest resolved = new HandoffRequest(UUID.randomUUID(), CONVERSATION_ID, OWNER, "x",
                HandoffStatus.RESOLVED, false, Instant.now(), Instant.now());
        when(repository.findByIdAndOwner(resolved.id(), OWNER)).thenReturn(Optional.of(resolved));
        when(repository.findByIdAndOwner(any(), org.mockito.ArgumentMatchers.eq("other"))).thenReturn(Optional.empty());

        assertThat(service.resolve(OWNER, resolved.id()).status()).isEqualTo(HandoffStatus.RESOLVED);
        verify(repository).resolve(resolved.id(), OWNER);
        assertThatThrownBy(() -> service.resolve("other", UUID.randomUUID())).isInstanceOf(ApiNotFoundException.class);
    }
}
