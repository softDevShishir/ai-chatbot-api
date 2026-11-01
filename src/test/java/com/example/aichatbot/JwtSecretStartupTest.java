package com.example.aichatbot;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Confirms the app refuses to start outright when JWT_SECRET is too weak —
 * not just that {@code JwtService} itself rejects it (see {@code JwtServiceTest}).
 *
 * <p>System properties are used (not {@code SpringApplicationBuilder.properties(...)},
 * which become low-precedence default properties) so this override actually
 * beats the valid secret set in application-test.properties for the "test" profile.
 */
class JwtSecretStartupTest {

    @AfterEach
    void clearSystemProperties() {
        System.clearProperty("jwt.secret");
        System.clearProperty("admin.username");
        System.clearProperty("admin.password");
        System.clearProperty("server.port");
    }

    @Test
    void applicationFailsToStartWithJwtSecretShorterThan32Bytes() {
        System.setProperty("jwt.secret", "too-short-secret");
        System.setProperty("admin.username", "test-admin");
        System.setProperty("admin.password", "test-admin-password");
        System.setProperty("server.port", "0");

        assertThatThrownBy(() -> new SpringApplicationBuilder(AiChatbotApiApplication.class)
                .profiles("test")
                .run())
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .hasStackTraceContaining("JWT_SECRET");
    }
}
