CREATE TABLE messages (
    id              UUID         PRIMARY KEY,
    conversation_id UUID         NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    role            VARCHAR(10)  NOT NULL CHECK (role IN ('USER', 'ASSISTANT')),
    content         TEXT         NOT NULL,
    provider        VARCHAR(20),
    model           VARCHAR(100),
    input_tokens    INTEGER,
    output_tokens   INTEGER,
    created_at      TIMESTAMP    NOT NULL
);

CREATE INDEX idx_messages_conversation_id ON messages (conversation_id, created_at);
