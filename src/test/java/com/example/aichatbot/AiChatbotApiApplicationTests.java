package com.example.aichatbot;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class AiChatbotApiApplicationTests {

    @Test
    void contextLoads() {
        // Verifies the app starts with no AI provider keys, no Postgres, and the
        // test profile's H2 datasource/migrations.
    }
}
