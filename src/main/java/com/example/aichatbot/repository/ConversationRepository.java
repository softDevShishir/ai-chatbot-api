package com.example.aichatbot.repository;

import com.example.aichatbot.domain.Conversation;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ConversationRepository {

    private static final RowMapper<Conversation> MAPPER = (rs, rowNum) -> new Conversation(
            UUID.fromString(rs.getString("id")),
            rs.getString("owner"),
            rs.getString("title"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant());

    private final JdbcTemplate jdbcTemplate;

    public ConversationRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Conversation create(String owner, String title) {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        jdbcTemplate.update(
                "INSERT INTO conversations (id, owner, title, created_at, updated_at) VALUES (?, ?, ?, ?, ?)",
                id, owner, title, Timestamp.from(now), Timestamp.from(now));
        return new Conversation(id, owner, title, now, now);
    }

    public List<Conversation> findAllByOwner(String owner) {
        return jdbcTemplate.query(
                "SELECT * FROM conversations WHERE owner = ? ORDER BY updated_at DESC", MAPPER, owner);
    }

    public Optional<Conversation> findByIdAndOwner(UUID id, String owner) {
        return jdbcTemplate.query(
                        "SELECT * FROM conversations WHERE id = ? AND owner = ?", MAPPER, id, owner)
                .stream()
                .findFirst();
    }

    public boolean deleteByIdAndOwner(UUID id, String owner) {
        int rows = jdbcTemplate.update("DELETE FROM conversations WHERE id = ? AND owner = ?", id, owner);
        return rows > 0;
    }

    public void touchUpdatedAt(UUID id) {
        jdbcTemplate.update("UPDATE conversations SET updated_at = ? WHERE id = ?",
                Timestamp.from(Instant.now()), id);
    }
}
