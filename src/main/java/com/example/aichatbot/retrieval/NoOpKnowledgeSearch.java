package com.example.aichatbot.retrieval;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Test-profile stand-in for {@link PostgresKnowledgeSearch} (which needs
 * real PostgreSQL). Always returns no results; tests that care about
 * retrieval behaviour replace this with a Mockito {@code @MockBean}.
 */
@Component
@Profile("test")
public class NoOpKnowledgeSearch implements KnowledgeSearch {

    @Override
    public List<KnowledgeItem> search(String query) {
        return List.of();
    }
}
