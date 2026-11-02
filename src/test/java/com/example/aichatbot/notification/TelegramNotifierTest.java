package com.example.aichatbot.notification;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** No network: the RestClient is bound to a MockRestServiceServer. */
class TelegramNotifierTest {

    private static final String TOKEN = "123456:SECRET-BOT-TOKEN";
    private static final UUID HANDOFF_ID = UUID.randomUUID();
    private static final UUID CONVERSATION_ID = UUID.randomUUID();

    private final Logger rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

    @BeforeEach
    void captureLogs() {
        logs.start();
        rootLogger.addAppender(logs);
    }

    @AfterEach
    void stopCapturingLogs() {
        rootLogger.detachAppender(logs);
    }

    private String allLogs() {
        return logs.list.stream().map(ILoggingEvent::getFormattedMessage).collect(Collectors.joining("\n"));
    }

    private static TelegramConfig config(String token, String chatId) {
        return new TelegramConfig(token, chatId, "https://telegram.test");
    }

    private static TelegramNotifier notifier(TelegramConfig config, RestClient.Builder builder) {
        return new TelegramNotifier(config, builder.build(), "Aronno Mart", ZoneId.of("Asia/Dhaka"));
    }

    private static boolean send(TelegramNotifier notifier, String text) {
        return notifier.sendHandoffAlert(HANDOFF_ID, CONVERSATION_ID, Instant.now(), text);
    }

    @Test
    void disabledWhenTokenOrChatIdIsBlank() {
        assertThat(config("", "42").isConfigured()).isFalse();
        assertThat(config("  ", "42").isConfigured()).isFalse();
        assertThat(config(TOKEN, "").isConfigured()).isFalse();
        assertThat(config(null, null).isConfigured()).isFalse();
        assertThat(config(TOKEN, "42").isConfigured()).isTrue();
    }

    @Test
    void notConfiguredMakesNoHttpCallAndLogsAWarning() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TelegramNotifier notifier = notifier(config("", ""), builder);

        assertThat(notifier.isEnabled()).isFalse();
        assertThat(send(notifier, "hi")).isFalse();

        server.verify(); // no request was expected
        assertThat(logs.list).anyMatch(e -> e.getLevel().toString().equals("WARN"));
    }

    @Test
    void defaultsToTheRealTelegramApiBaseUrl() {
        assertThat(new TelegramConfig(TOKEN, "42", "").apiBaseUrl()).isEqualTo("https://api.telegram.org");
        assertThat(new TelegramConfig(TOKEN, "42", null).apiBaseUrl()).isEqualTo("https://api.telegram.org");
        assertThat(new TelegramConfig(TOKEN, "42", "https://x.test/").apiBaseUrl()).isEqualTo("https://x.test");
    }

    @Test
    void sendsPlainTextToSendMessageWithoutParseMode() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://telegram.test/bot" + TOKEN + "/sendMessage"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.chat_id").value("42"))
                .andExpect(jsonPath("$.parse_mode").doesNotExist())
                .andExpect(jsonPath("$.text").value(org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.startsWith("Aronno Mart: customer needs help"),
                        org.hamcrest.Matchers.containsString(CONVERSATION_ID.toString()),
                        org.hamcrest.Matchers.containsString("Where is my <b>order</b>?"))))
                .andRespond(withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON));
        TelegramNotifier notifier = notifier(config(TOKEN, "42"), builder);

        assertThat(send(notifier, "Where is my <b>order</b>?")).isTrue();

        server.verify();
    }

    @Test
    void customerMessageInTheAlertIsCutTo300Characters() {
        String longMessage = "x".repeat(1000);

        String text = TelegramNotifier.buildMessage("Aronno Mart", CONVERSATION_ID, Instant.now(),
                ZoneId.of("Asia/Dhaka"), longMessage);

        String customerPart = text.substring(text.indexOf("Last customer message: ") + "Last customer message: ".length());
        assertThat(customerPart).hasSize(300);
        assertThat(customerPart).endsWith("…");
        assertThat(TelegramNotifier.truncate("x".repeat(300), 300)).hasSize(300).doesNotContain("…");
        assertThat(TelegramNotifier.truncate("short", 300)).isEqualTo("short");
    }

    @Test
    void truncateDoesNotSplitASurrogatePair() {
        String text = "a".repeat(298) + "😀" + "tail"; // the emoji straddles the cut point

        String cut = TelegramNotifier.truncate(text, 300);

        assertThat(cut.length()).isLessThanOrEqualTo(300);
        assertThat(Character.isHighSurrogate(cut.charAt(cut.length() - 2))).isFalse();
    }

    @Test
    void httpErrorNeverThrowsAndNeverLeaksTheToken() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://telegram.test/bot" + TOKEN + "/sendMessage")).andRespond(withServerError());
        TelegramNotifier notifier = notifier(config(TOKEN, "42"), builder);

        assertThatCode(() -> send(notifier, "help")).doesNotThrowAnyException();

        assertThat(allLogs()).contains("failed").doesNotContain("SECRET-BOT-TOKEN").doesNotContain(TOKEN);
    }

    @Test
    void ioErrorWhoseMessageContainsTheUrlNeverLeaksTheToken() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        // RestClient wraps this as: I/O error on POST request for "https://telegram.test/bot<TOKEN>/sendMessage"
        server.expect(requestTo("https://telegram.test/bot" + TOKEN + "/sendMessage"))
                .andRespond(request -> {
                    throw new IOException("Connection refused: " + request.getURI());
                });
        TelegramNotifier notifier = notifier(config(TOKEN, "42"), builder);

        boolean sent = send(notifier, "help");

        assertThat(sent).isFalse();
        assertThat(allLogs()).contains("failed").contains("/bot***/")
                .doesNotContain("SECRET-BOT-TOKEN").doesNotContain(TOKEN);
    }

    @Test
    void redactsRawEncodedAndUnrecognisedTokenForms() {
        TelegramNotifier notifier = notifier(config(TOKEN, "42"), RestClient.builder());

        assertThat(notifier.redact("a " + TOKEN + " b")).doesNotContain("SECRET");
        assertThat(notifier.redact("a 123456%3ASECRET-BOT-TOKEN b")).doesNotContain("SECRET");
        assertThat(notifier.redact("POST https://api.telegram.org/bot999:OTHER/sendMessage")).doesNotContain("OTHER");
    }

    @Test
    void toStringMasksTheToken() {
        assertThat(config(TOKEN, "42").toString()).doesNotContain("SECRET-BOT-TOKEN").contains("botToken=***");
    }

    @Test
    void bodyIsJsonWithChatIdAndText() {
        // Guards the wire format: Telegram's sendMessage accepts JSON with chat_id + text.
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://telegram.test/bot" + TOKEN + "/sendMessage"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andRespond(withSuccess("{\"ok\":true}", MediaType.APPLICATION_JSON));

        assertThat(send(notifier(config(TOKEN, "42"), builder), "hi")).isTrue();
        server.verify();
    }
}
