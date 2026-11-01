package com.example.aichatbot.retrieval;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * {@link KnowledgeSearch} backed by PostgreSQL full-text search over the
 * {@code products} and {@code store_policies} tables (see the
 * db/migration-postgres Flyway location). Not active in the "test" profile —
 * H2 has no tsvector/GIN support, so tests mock {@link KnowledgeSearch}.
 */
@Component
@Profile("!test")
public class PostgresKnowledgeSearch implements KnowledgeSearch {

    static final int MAX_RESULTS = 5;

    /*
     * plainto_tsquery() turns free text into lexemes joined with AND ('red' & 'shoe'),
     * which would require every word of a natural-language question to match. Swapping
     * '&' for '|' makes any matching word count, and ts_rank puts items matching more
     * (and rarer) words first.
     */
    private static final String PRODUCT_SQL = """
            SELECT id, name, description, category, price, in_stock,
                   ts_rank(search_vector, query) AS rank
            FROM products,
                 to_tsquery('english', replace(plainto_tsquery('english', ?)::text, '&', '|')) AS query
            WHERE search_vector @@ query
            ORDER BY rank DESC
            LIMIT ?
            """;

    private static final String POLICY_SQL = """
            SELECT id, topic, content,
                   ts_rank(search_vector, query) AS rank
            FROM store_policies,
                 to_tsquery('english', replace(plainto_tsquery('english', ?)::text, '&', '|')) AS query
            WHERE search_vector @@ query
            ORDER BY rank DESC
            LIMIT ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public PostgresKnowledgeSearch(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public List<KnowledgeItem> search(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        List<Scored> products = jdbcTemplate.query(PRODUCT_SQL, PostgresKnowledgeSearch::mapProduct, query, MAX_RESULTS);
        List<Scored> policies = jdbcTemplate.query(POLICY_SQL, PostgresKnowledgeSearch::mapPolicy, query, MAX_RESULTS);

        return Stream.concat(products.stream(), policies.stream())
                .sorted(Comparator.comparingDouble(Scored::rank).reversed())
                .limit(MAX_RESULTS)
                .map(Scored::item)
                .toList();
    }

    private static Scored mapProduct(ResultSet rs, int rowNum) throws SQLException {
        BigDecimal price = rs.getBigDecimal("price");
        String content = "Category: " + rs.getString("category") + "\n"
                + "Price: " + price.toPlainString() + " BDT\n"
                + "Availability: " + (rs.getBoolean("in_stock") ? "In stock" : "Out of stock") + "\n"
                + "Description: " + rs.getString("description");
        KnowledgeItem item = new KnowledgeItem(KnowledgeItem.PRODUCT, String.valueOf(rs.getLong("id")),
                rs.getString("name"), content);
        return new Scored(item, rs.getDouble("rank"));
    }

    private static Scored mapPolicy(ResultSet rs, int rowNum) throws SQLException {
        KnowledgeItem item = new KnowledgeItem(KnowledgeItem.POLICY, String.valueOf(rs.getLong("id")),
                rs.getString("topic"), rs.getString("content"));
        return new Scored(item, rs.getDouble("rank"));
    }

    private record Scored(KnowledgeItem item, double rank) {
    }
}
