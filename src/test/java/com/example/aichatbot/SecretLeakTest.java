package com.example.aichatbot;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.aichatbot.security.AdminCredentialsService;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Proves that configured secrets (API keys, JWT signing secret, admin
 * password) never leak into logs or HTTP responses, however they are
 * triggered. The Logback appender is attached in a static initializer so it
 * is in place before the Spring context — and its startup logging — exists.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "claude.api-key=super-secret-claude-key-123",
        "gemini.api-key=super-secret-gemini-key-456",
        "openai.api-key=super-secret-openai-key-789",
        "jwt.secret=super-secret-jwt-signing-key-should-not-leak-anywhere-ok",
        "admin.password=super-secret-admin-password-xyz"
})
class SecretLeakTest {

    private static final List<String> SECRETS = List.of(
            "super-secret-claude-key-123",
            "super-secret-gemini-key-456",
            "super-secret-openai-key-789",
            "super-secret-jwt-signing-key-should-not-leak-anywhere-ok",
            "super-secret-admin-password-xyz");

    private static final Logger ROOT_LOGGER = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    private static final ListAppender<ILoggingEvent> LOG_APPENDER = new ListAppender<>();

    static {
        LOG_APPENDER.start();
        ROOT_LOGGER.addAppender(LOG_APPENDER);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AdminCredentialsService adminCredentialsService;

    @Test
    void secretsNeverAppearInStartupOrRequestLogs() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\": \"test-admin\", \"password\": \"wrong-password\"}"));
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\": \"test-admin\", \"password\": \"super-secret-admin-password-xyz\"}"));
        mockMvc.perform(post("/api/chats")
                .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-real-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"));

        String allLogs = LOG_APPENDER.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .collect(Collectors.joining("\n"));

        for (String secret : SECRETS) {
            assertThat(allLogs).doesNotContain(secret);
        }
    }

    @Test
    void secretsNeverAppearInHttpResponses() throws Exception {
        var wrongPassword = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"test-admin\", \"password\": \"wrong-password\"}"))
                .andReturn().getResponse().getContentAsString();

        var correctLogin = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"test-admin\", \"password\": \"super-secret-admin-password-xyz\"}"))
                .andReturn().getResponse().getContentAsString();

        for (String secret : SECRETS) {
            assertThat(wrongPassword).doesNotContain(secret);
            assertThat(correctLogin).doesNotContain(secret);
        }
    }

    @Test
    void adminCredentialsServiceMasksSecretsInToString() {
        String text = adminCredentialsService.toString();

        assertThat(text).doesNotContain("super-secret-admin-password-xyz");
    }
}
