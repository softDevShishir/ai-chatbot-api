CREATE TABLE conversations (
    id         UUID         PRIMARY KEY,
    owner      VARCHAR(100) NOT NULL,
    title      VARCHAR(200),
    created_at TIMESTAMP    NOT NULL,
    updated_at TIMESTAMP    NOT NULL
);

CREATE INDEX idx_conversations_owner ON conversations (owner);
