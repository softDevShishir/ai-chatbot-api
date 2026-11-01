package com.example.aichatbot;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Confirms the Flyway migrations under db/migration apply cleanly on H2 (the
 * test profile's datasource) and seed the expected demo catalogue — the
 * Postgres-only full-text location is excluded in this profile, see
 * application-test.properties.
 */
@SpringBootTest
@ActiveProfiles("test")
class DataModelMigrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void seedsFifteenDemoProducts() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM products", Integer.class);
        assertThat(count).isEqualTo(15);
    }

    @Test
    void seedsEightStorePolicies() {
        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM store_policies", Integer.class);
        assertThat(count).isEqualTo(8);
    }

    @Test
    void conversationsAndMessagesTablesExist() {
        jdbcTemplate.execute("SELECT id, owner, title, created_at, updated_at FROM conversations WHERE 1 = 0");
        jdbcTemplate.execute("SELECT id, conversation_id, role, content, provider, model, "
                + "input_tokens, output_tokens, created_at FROM messages WHERE 1 = 0");
    }
}
