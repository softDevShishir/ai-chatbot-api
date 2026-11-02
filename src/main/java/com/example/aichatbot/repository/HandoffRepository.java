package com.example.aichatbot.repository;

import com.example.aichatbot.domain.HandoffRequest;
import com.example.aichatbot.domain.HandoffStatus;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class HandoffRepository {

    private static final RowMapper<HandoffRequest> MAPPER = (rs, rowNum) -> {
        Timestamp resolvedAt = rs.getTimestamp("resolved_at");
        return new HandoffRequest(
                UUID.fromString(rs.getString("id")),
                UUID.fromString(rs.getString("conversation_id")),
                rs.getString("owner"),
                rs.getString("last_customer_message"),
                HandoffStatus.valueOf(rs.getString("status")),
                rs.getBoolean("notified"),
                rs.getTimestamp("created_at").toInstant(),
                resolvedAt == null ? null : resolvedAt.toInstant());
    };

    private final JdbcTemplate jdbcTemplate;

    public HandoffRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Inserts a new OPEN handoff unless the conversation already has one. The "already has one"
     * decision is made by the database's unique constraint on {@code open_conversation_id}, so it
     * holds even when two replies race.
     *
     * @return the new row, or empty if the conversation already has an OPEN handoff
     */
    public Optional<HandoffRequest> insertIfNoneOpen(UUID conversationId, String owner, String lastCustomerMessage) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        try {
            jdbcTemplate.update(
                    "INSERT INTO handoff_requests (id, conversation_id, owner, last_customer_message, status, "
                            + "notified, created_at, resolved_at, open_conversation_id) "
                            + "VALUES (?, ?, ?, ?, 'OPEN', FALSE, ?, NULL, ?)",
                    id, conversationId, owner, lastCustomerMessage, Timestamp.from(now), conversationId);
        } catch (DuplicateKeyException e) {
            return Optional.empty();
        }
        return Optional.of(new HandoffRequest(id, conversationId, owner, lastCustomerMessage,
                HandoffStatus.OPEN, false, now, null));
    }

    public void markNotified(UUID id) {
        jdbcTemplate.update("UPDATE handoff_requests SET notified = TRUE WHERE id = ?", id);
    }

    public List<HandoffRequest> findByOwnerAndStatus(String owner, HandoffStatus status) {
        return jdbcTemplate.query(
                "SELECT * FROM handoff_requests WHERE owner = ? AND status = ? ORDER BY created_at DESC",
                MAPPER, owner, status.name());
    }

    public Optional<HandoffRequest> findByIdAndOwner(UUID id, String owner) {
        return jdbcTemplate.query("SELECT * FROM handoff_requests WHERE id = ? AND owner = ?", MAPPER, id, owner)
                .stream().findFirst();
    }

    /** Marks an OPEN handoff RESOLVED (and frees the conversation for a future handoff). */
    public boolean resolve(UUID id, String owner) {
        int rows = jdbcTemplate.update(
                "UPDATE handoff_requests SET status = 'RESOLVED', resolved_at = ?, open_conversation_id = NULL "
                        + "WHERE id = ? AND owner = ? AND status = 'OPEN'",
                Timestamp.from(Instant.now()), id, owner);
        return rows > 0;
    }
}
