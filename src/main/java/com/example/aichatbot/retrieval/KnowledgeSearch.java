package com.example.aichatbot.retrieval;

import java.util.List;

/**
 * Finds store knowledge (products and policies) relevant to a customer
 * question, used to ground the AI's answer in the store's own data.
 */
public interface KnowledgeSearch {

    /**
     * Returns the most relevant products/policies for {@code query}, best
     * match first, or an empty list if nothing matches.
     */
    List<KnowledgeItem> search(String query);
}
