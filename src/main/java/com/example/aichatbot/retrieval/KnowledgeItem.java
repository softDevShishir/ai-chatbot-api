package com.example.aichatbot.retrieval;

/**
 * One piece of store knowledge (a product or a policy) retrieved for a
 * customer question. {@code content} is the formatted block inserted into
 * the system prompt; {@code sourceType}/{@code id}/{@code title} are also
 * surfaced as "sources" in the chat API response.
 */
public record KnowledgeItem(String sourceType, String id, String title, String content) {

    public static final String PRODUCT = "product";
    public static final String POLICY = "policy";
}
