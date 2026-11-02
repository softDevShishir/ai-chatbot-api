-- One row per "customer needs a human" event. Portable SQL (PostgreSQL and H2).
--
-- open_conversation_id enforces "at most one OPEN handoff per conversation" in the database
-- itself: it equals conversation_id while the handoff is OPEN and is NULL once RESOLVED.
-- A plain UNIQUE constraint allows any number of NULLs on both databases, so resolved
-- handoffs never collide, while two concurrent OPEN inserts for the same conversation do.
-- (A partial unique index would be the usual tool, but H2 does not support them.)
CREATE TABLE handoff_requests (
    id                    UUID         PRIMARY KEY,
    conversation_id       UUID         NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    owner                 VARCHAR(100) NOT NULL,
    last_customer_message TEXT         NOT NULL,
    status                VARCHAR(10)  NOT NULL CHECK (status IN ('OPEN', 'RESOLVED')),
    notified              BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at            TIMESTAMP    NOT NULL,
    resolved_at           TIMESTAMP,
    open_conversation_id  UUID         UNIQUE REFERENCES conversations (id) ON DELETE CASCADE
);

CREATE INDEX idx_handoff_requests_status_created ON handoff_requests (owner, status, created_at);
