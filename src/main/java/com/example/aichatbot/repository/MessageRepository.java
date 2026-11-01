package com.example.aichatbot.repository;

import com.example.aichatbot.domain.Message;
import com.example.aichatbot.domain.MessageRole;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class MessageRepository {

    private static final RowMapper<Message> MAPPER = (rs, rowNum) -> new Message(
            UUID.fromString(rs.getString("id")),
            UUID.fromString(rs.getString("conversation_id")),
            MessageRole.valueOf(rs.getString("role")),
            rs.getString("content"),
            rs.getString("provider"),
            rs.getString("model"),
            (Integer) rs.getObject("input_tokens"),
            (Integer) rs.getObject("output_tokens"),
            rs.getTimestamp("created_at").toInstant());

    private final JdbcTemplate jdbcTemplate;

    public MessageRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Message insert(UUID conversationId, MessageRole role, String content, String provider, String model,
                           Integer inputTokens, Integer outputTokens) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbcTemplate.update(
                "INSERT INTO messages (id, conversation_id, role, content, provider, model, input_tokens, "
                        + "output_tokens, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                id, conversationId, role.name(), content, provider, model, inputTokens, outputTokens,
                Timestamp.from(now));
        return new Message(id, conversationId, role, content, provider, model, inputTokens, outputTokens, now);
    }

    /**
     * All messages in a conversation, oldest first.
     */
    public List<Message> findByConversationId(UUID conversationId) {
        return jdbcTemplate.query(
                "SELECT * FROM messages WHERE conversation_id = ? ORDER BY created_at ASC", MAPPER, conversationId);
    }

    public int countByConversationId(UUID conversationId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM messages WHERE conversation_id = ?", Integer.class, conversationId);
        return count == null ? 0 : count;
    }

    /**
     * Number of assistant replies (i.e. completed AI calls) in {@code [start, end)}, across all
     * conversations — used for the global daily AI-call cap.
     */
    public int countAssistantMessagesBetween(Instant start, Instant end) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM messages WHERE role = 'ASSISTANT' AND created_at >= ? AND created_at < ?",
                Integer.class, Timestamp.from(start), Timestamp.from(end));
        return count == null ? 0 : count;
    }
}
