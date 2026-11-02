package com.example.aichatbot.service;

import com.example.aichatbot.retrieval.KnowledgeItem;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SystemPromptBuilderTest {

    private final SystemPromptBuilder builder = new SystemPromptBuilder("Test Store");

    @Test
    void containsHandoffInstruction() {
        String prompt = builder.build(List.of());

        assertThat(SystemPromptBuilder.HANDOFF_MARKER).isEqualTo("[[HANDOFF]]");
        assertThat(prompt)
                .contains("end with the exact marker [[HANDOFF]]")
                .contains("don't have that information")
                .contains("support person can help")
                .contains("asks to speak to a person");
    }

    @Test
    void restrictsToStoreTopicsLanguageAndLength() {
        String prompt = builder.build(List.of());

        assertThat(prompt)
                .contains("Test Store")
                .contains("Never answer questions unrelated to Test Store")
                .contains("sports, news or general knowledge")
                .contains("Keep replies short")
                .contains("(English or Bangla)");
    }

    @Test
    void includesKnowledgeOrFallback() {
        assertThat(builder.build(List.of())).contains("No matching store information was found.");
    }
}
