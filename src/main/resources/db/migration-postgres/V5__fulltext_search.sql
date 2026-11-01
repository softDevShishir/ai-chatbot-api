-- PostgreSQL-only: full-text search over products and store policies.
-- This location is NOT on the Flyway path during tests (see application-test.properties),
-- since H2 has no tsvector/GIN support.

ALTER TABLE products
    ADD COLUMN search_vector TSVECTOR GENERATED ALWAYS AS (
        to_tsvector('english', name || ' ' || description || ' ' || category)
    ) STORED;

CREATE INDEX idx_products_search_vector ON products USING GIN (search_vector);

ALTER TABLE store_policies
    ADD COLUMN search_vector TSVECTOR GENERATED ALWAYS AS (
        to_tsvector('english', topic || ' ' || content)
    ) STORED;

CREATE INDEX idx_store_policies_search_vector ON store_policies USING GIN (search_vector);
