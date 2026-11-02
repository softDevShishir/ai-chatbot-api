package com.example.aichatbot;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.aichatbot.domain.HandoffRequest;
import com.example.aichatbot.notification.TelegramNotifier;
import com.example.aichatbot.provider.AiCompletionService;
import com.example.aichatbot.provider.AiResult;
import com.example.aichatbot.provider.CompletionOutcome;
import com.example.aichatbot.repository.ConversationRepository;
import com.example.aichatbot.repository.HandoffRepository;
import com.example.aichatbot.retrieval.KnowledgeSearch;
import com.example.aichatbot.security.JwtService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Handoff flow over HTTP against the real H2 database. Only the AI call, knowledge retrieval and
 * the Telegram notifier are mocked, so no network, Telegram or AI key is ever needed.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "telegram.bot-token=999999:INTEGRATION-SECRET-TOKEN",
        "telegram.chat-id=42"
})
class HandoffFlowIntegrationTest {

    private static final String OWNER = "test-admin";
    private static final String TOKEN = "999999:INTEGRATION-SECRET-TOKEN";

    private final Logger rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private ConversationRepository conversationRepository;
    @Autowired
    private HandoffRepository handoffRepository;

    @MockBean
    private KnowledgeSearch knowledgeSearch;
    @MockBean
    private AiCompletionService aiCompletionService;
    @MockBean
    private TelegramNotifier telegramNotifier;

    private String auth;

    @BeforeEach
    void setUp() {
        logs.start();
        rootLogger.addAppender(logs);
        auth = "Bearer " + jwtService.generateToken(OWNER);
        reset(telegramNotifier);
        when(telegramNotifier.isEnabled()).thenReturn(true);
        when(telegramNotifier.sendHandoffAlert(any(), any(), any(), anyString())).thenReturn(true);
        when(knowledgeSearch.search(anyString())).thenReturn(List.of());
        aiReplies("Let me get a human for you. [[HANDOFF]]");
    }

    @AfterEach
    void tearDown() {
        rootLogger.detachAppender(logs);
    }

    private void aiReplies(String text) {
        when(aiCompletionService.complete(any(), anyString(), any())).thenReturn(
                new CompletionOutcome(new AiResult(text, "claude-test", 10, 5), "claude", false));
    }

    private UUID newConversation() {
        return conversationRepository.create(OWNER, "handoff test").id();
    }

    private String send(UUID conversationId, String message) throws Exception {
        return mockMvc.perform(post("/api/chats/" + conversationId + "/messages")
                        .header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\": \"" + message + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private JsonNode handoffsFor(UUID conversationId, String statusParam) throws Exception {
        String body = mockMvc.perform(get("/api/handoffs" + (statusParam == null ? "" : "?status=" + statusParam))
                        .header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<JsonNode> matches = StreamSupport.stream(objectMapper.readTree(body).spliterator(), false)
                .filter(n -> n.get("conversationId").asText().equals(conversationId.toString()))
                .toList();
        return objectMapper.valueToTree(matches);
    }

    @Test
    void handoffCreatesOneOpenRowAndSendsOneAlertThenMarksNotified() throws Exception {
        UUID conversationId = newConversation();

        String response = send(conversationId, "I want to talk to a person");

        assertThat(objectMapper.readTree(response).get("handoff").asBoolean()).isTrue();
        JsonNode rows = handoffsFor(conversationId, null); // default status = OPEN
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("status").asText()).isEqualTo("OPEN");
        assertThat(rows.get(0).get("lastCustomerMessage").asText()).isEqualTo("I want to talk to a person");
        verify(telegramNotifier, timeout(3000).times(1)).sendHandoffAlert(any(), any(), any(),
                org.mockito.ArgumentMatchers.eq("I want to talk to a person"));
        await().atMost(Duration.ofSeconds(3)).untilAsserted(
                () -> assertThat(handoffsFor(conversationId, null).get(0).get("notified").asBoolean()).isTrue());
    }

    @Test
    void secondHandoffInTheSameConversationCreatesNoSecondRowAndNoSecondAlert() throws Exception {
        UUID conversationId = newConversation();
        send(conversationId, "first request");
        verify(telegramNotifier, timeout(3000).times(1)).sendHandoffAlert(any(), any(), any(), anyString());

        send(conversationId, "second request");

        assertThat(handoffsFor(conversationId, "OPEN")).hasSize(1);
        assertThat(handoffsFor(conversationId, "OPEN").get(0).get("lastCustomerMessage").asText())
                .isEqualTo("first request");
        verify(telegramNotifier, after(400).times(1)).sendHandoffAlert(any(), any(), any(), anyString());
    }

    @Test
    void afterResolvingANewHandoffInTheSameConversationIsAllowedAndAlerted() throws Exception {
        UUID conversationId = newConversation();
        send(conversationId, "first request");
        String id = handoffsFor(conversationId, "OPEN").get(0).get("id").asText();
        mockMvc.perform(post("/api/handoffs/" + id + "/resolve").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk());

        send(conversationId, "I need help again");

        assertThat(handoffsFor(conversationId, "OPEN")).hasSize(1);
        assertThat(handoffsFor(conversationId, "RESOLVED")).hasSize(1);
        verify(telegramNotifier, timeout(3000).times(2)).sendHandoffAlert(any(), any(), any(), anyString());
    }

    @Test
    void noHandoffMarkerMeansNoRowAndNoAlert() throws Exception {
        aiReplies("We have running shoes in stock.");
        UUID conversationId = newConversation();

        send(conversationId, "Do you have running shoes?");

        assertThat(handoffsFor(conversationId, null)).isEmpty();
        verify(telegramNotifier, never()).sendHandoffAlert(any(), any(), any(), anyString());
    }

    @Test
    void telegramFailureDoesNotBreakTheReplyAndLeavesNotifiedFalse() throws Exception {
        when(telegramNotifier.sendHandoffAlert(any(), any(), any(), anyString()))
                .thenThrow(new IllegalStateException("simulated Telegram crash"));
        UUID conversationId = newConversation();

        String response = send(conversationId, "need a human please");

        JsonNode reply = objectMapper.readTree(response);
        assertThat(reply.get("reply").asText()).isEqualTo("Let me get a human for you.");
        assertThat(reply.get("handoff").asBoolean()).isTrue();
        verify(telegramNotifier, timeout(3000)).sendHandoffAlert(any(), any(), any(), anyString());
        JsonNode row = handoffsFor(conversationId, "OPEN").get(0);
        assertThat(row.get("notified").asBoolean()).isFalse();
    }

    @Test
    void telegramNotConfiguredStillSavesTheHandoffAndReplies() throws Exception {
        when(telegramNotifier.isEnabled()).thenReturn(false);
        UUID conversationId = newConversation();

        send(conversationId, "need a human please");

        assertThat(handoffsFor(conversationId, "OPEN")).hasSize(1);
        assertThat(handoffsFor(conversationId, "OPEN").get(0).get("notified").asBoolean()).isFalse();
        verify(telegramNotifier, never()).sendHandoffAlert(any(), any(), any(), anyString());
    }

    @Test
    void aSlowTelegramDoesNotDelayTheReply() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        when(telegramNotifier.sendHandoffAlert(any(), any(), any(), anyString())).thenAnswer(invocation -> {
            release.await(10, TimeUnit.SECONDS); // Telegram "hangs" until the test is done
            return true;
        });
        UUID conversationId = newConversation();
        try {
            // If the reply waited for Telegram, this request would block for ~10s instead of returning now.
            long start = System.nanoTime();
            send(conversationId, "need a human please");
            long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

            assertThat(elapsedMs).isLessThan(5000);
            verify(telegramNotifier, timeout(3000)).sendHandoffAlert(any(), any(), any(), anyString());
            assertThat(handoffsFor(conversationId, "OPEN").get(0).get("notified").asBoolean()).isFalse();
        } finally {
            release.countDown();
        }
    }

    @Test
    void listIsNewestFirstAndResolveMovesARowBetweenStatuses() throws Exception {
        UUID older = newConversation();
        UUID newer = newConversation();
        HandoffRequest olderRow = handoffRepository.insertIfNoneOpen(older, OWNER, "older").orElseThrow();
        Thread.sleep(20);
        HandoffRequest newerRow = handoffRepository.insertIfNoneOpen(newer, OWNER, "newer").orElseThrow();

        String body = mockMvc.perform(get("/api/handoffs?status=OPEN").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        List<String> ids = StreamSupport.stream(objectMapper.readTree(body).spliterator(), false)
                .map(n -> n.get("id").asText()).collect(Collectors.toList());
        assertThat(ids).contains(olderRow.id().toString(), newerRow.id().toString());
        assertThat(ids.indexOf(newerRow.id().toString())).isLessThan(ids.indexOf(olderRow.id().toString()));

        mockMvc.perform(post("/api/handoffs/" + newerRow.id() + "/resolve").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(newerRow.id().toString()))
                .andExpect(jsonPath("$.status").value("RESOLVED"))
                .andExpect(jsonPath("$.resolvedAt").isNotEmpty());

        assertThat(handoffsFor(newer, "OPEN")).isEmpty();
        assertThat(handoffsFor(newer, "RESOLVED")).hasSize(1);
        assertThat(handoffsFor(older, "OPEN")).hasSize(1);

        // Resolving again is harmless and returns the already-resolved row.
        mockMvc.perform(post("/api/handoffs/" + newerRow.id() + "/resolve").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));
    }

    @Test
    void resolveUnknownIdIsNotFoundAndBadInputIsBadRequest() throws Exception {
        mockMvc.perform(post("/api/handoffs/" + UUID.randomUUID() + "/resolve").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Handoff not found"));
        mockMvc.perform(get("/api/handoffs?status=BOGUS").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/handoffs/not-a-uuid/resolve").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isBadRequest());
    }

    @Test
    void anotherOwnersHandoffLooksNonexistent() throws Exception {
        UUID conversationId = conversationRepository.create("someone-else", "t").id();
        HandoffRequest row = handoffRepository.insertIfNoneOpen(conversationId, "someone-else", "x").orElseThrow();

        mockMvc.perform(post("/api/handoffs/" + row.id() + "/resolve").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isNotFound());
        assertThat(handoffsFor(conversationId, "OPEN")).isEmpty();
    }

    @Test
    void bothEndpointsRequireAToken() throws Exception {
        mockMvc.perform(get("/api/handoffs")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/handoffs?status=RESOLVED")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/handoffs/" + UUID.randomUUID() + "/resolve")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/handoffs").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void botTokenNeverAppearsInResponsesOrLogs() throws Exception {
        UUID conversationId = newConversation();
        StringBuilder responses = new StringBuilder();
        responses.append(send(conversationId, "need a human please"));
        verify(telegramNotifier, timeout(3000).times(1)).sendHandoffAlert(any(), any(), any(), anyString());
        responses.append(mockMvc.perform(get("/api/handoffs").header(HttpHeaders.AUTHORIZATION, auth))
                .andReturn().getResponse().getContentAsString());
        responses.append(mockMvc.perform(get("/api/handoffs?status=RESOLVED").header(HttpHeaders.AUTHORIZATION, auth))
                .andReturn().getResponse().getContentAsString());
        responses.append(mockMvc.perform(post("/api/handoffs/" + UUID.randomUUID() + "/resolve")
                .header(HttpHeaders.AUTHORIZATION, auth)).andReturn().getResponse().getContentAsString());
        responses.append(mockMvc.perform(get("/api/handoffs")).andReturn().getResponse().getContentAsString());

        String allLogs = logs.list.stream().map(ILoggingEvent::getFormattedMessage).collect(Collectors.joining("\n"));
        assertThat(responses.toString()).doesNotContain("INTEGRATION-SECRET-TOKEN").doesNotContain(TOKEN);
        assertThat(allLogs).doesNotContain("INTEGRATION-SECRET-TOKEN").doesNotContain(TOKEN);
    }
}
