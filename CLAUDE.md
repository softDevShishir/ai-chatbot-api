# CLAUDE.md

Guidance for Claude Code (or any agent) working in this repository. Keep this file current when
the architecture or conventions change — it's the fastest way for a future session to get
oriented.

## What this is

A Spring Boot customer-support chatbot API for a fictional online store, with conversation
memory (Postgres-backed) and answers grounded in the store's own product/policy data via
keyword-based retrieval. See `README.md` for the full picture (architecture diagram, config
table, curl walkthrough, limitations).

## Architecture

- `config/` — Spring config: JWT/CORS/security, and the Claude/Gemini/OpenAI SDK client beans
  (always built, even without an API key — see `AiProvider.isAvailable()`).
- `security/` — JWT issuance/validation (`JwtService`), the auth filter, the admin credential
  check (`AdminCredentialsService`).
- `provider/` — `AiProvider` abstraction (`ClaudeProvider`/`GeminiProvider`/`OpenAiProvider`),
  `AiProviderException` with a `Kind` that drives retry/fallback, and `AiCompletionService`
  which resolves the provider for a request, retries once on `RATE_LIMITED`/`TEMPORARY`, and
  falls back to `AI_FALLBACK_PROVIDER` if configured.
- `retrieval/` — `KnowledgeSearch` abstraction; `PostgresKnowledgeSearch` (full-text, `@Profile("!test")`)
  is the real implementation, `NoOpKnowledgeSearch` (`@Profile("test")`) backs tests that don't
  care about retrieval (most tests `@MockBean` it directly instead).
- `service/` — `ChatService` (the core flow: save message → retrieve knowledge → build prompt →
  call AI → strip handoff marker → save reply), plus its focused collaborators:
  `ChatHistoryBuilder` (windowing + char-budget trimming), `SystemPromptBuilder` (the one place
  the system prompt text lives), `RateLimiter` (in-memory, per-user), and `HandoffService`: when a reply has `handoff=true`,
  `ChatService` calls `HandoffService.onHandoff`, which saves a `handoff_requests` row (at most one
  OPEN per conversation — enforced by the unique `open_conversation_id` column) and sends the
  Telegram alert on the background executor. It must never throw or block: the customer's reply
  never depends on Telegram. `HandoffController` serves `GET /api/handoffs` and
  `POST /api/handoffs/{id}/resolve`, owner-scoped like conversations.
- `notification/` — `TelegramConfig` (env-only settings, masked `toString`) and `TelegramNotifier`
  (plain-text `sendMessage`, 5s timeouts, never throws, redacts the bot token from anything it
  logs; the token is part of the request URL). The notifier bean and the bounded
  `handoffAlertExecutor` are built in `config/HandoffConfig`.
- `repository/` — plain `JdbcTemplate` repositories (no JPA). `conversations`/`messages` use
  app-generated UUIDs; `products`/`store_policies` use identity columns.
- `domain/` — `Conversation`, `Message`, `MessageRole`, `HandoffRequest`, `HandoffStatus`.
- `dto/` / `controller/` — HTTP layer. `GlobalExceptionHandler` (in `exception/`) maps every
  exception to the one `ErrorResponse` shape.

## Database / migrations

Flyway, two locations:
- `db/migration` — portable SQL, runs on both Postgres and H2 (tests). Includes
  `handoff_requests` (V6); H2 has no partial unique indexes, so "one OPEN handoff per conversation"
  uses a nullable `UNIQUE open_conversation_id` that is cleared on resolve.
- `db/migration-postgres` — Postgres-only (tsvector/GIN full-text columns on `products` and
  `store_policies`). Excluded from the `test` Spring profile — see `application-test.properties`.

The `test` profile also supplies `jwt.secret`/`admin.username`/`admin.password` directly (not via
env var placeholders), since `src/test/resources/application.properties` would otherwise shadow
`src/main/resources/application.properties` wholesale rather than merge with it.

## Conventions

- Model names (`CLAUDE_MODEL`/`GEMINI_MODEL`/`OPENAI_MODEL`) live only in `application.properties`
  / `.env`, never in Java code.
- Every `AiProvider` wraps SDK exceptions into `AiProviderException` with a `Kind`
  (`RATE_LIMITED`/`TEMPORARY`/`AUTHENTICATION`/`BAD_REQUEST`/`UNKNOWN`); only the first two are
  retried.
- Never log or return API keys, the JWT secret, or the admin password. `AdminCredentialsService`
  masks its fields in `toString()`; see `SecretLeakTest` for the enforcement test.
- Tests never hit Postgres, the network, or a real AI API — `KnowledgeSearch` and
  `AiCompletionService`/`AiProvider` are mocked wherever a test exercises the chat flow.
- Telegram (`TELEGRAM_BOT_TOKEN`/`TELEGRAM_CHAT_ID`/`TELEGRAM_API_BASE_URL`) is optional and
  env-only. The bot token is a secret like the API keys: never log or return it, and never log a
  Telegram exception without passing its message through `TelegramNotifier.redact` (the token is
  in the URL). `application-test.properties` blanks the Telegram settings so a developer's real
  `.env` can't trigger a real alert; tests mock `TelegramNotifier` or bind its `RestClient` to
  `MockRestServiceServer`.
- New guardrails/limits should throw one of the `exception/Api*Exception` types (or
  `TooManyRequestsException`) rather than returning ad-hoc error bodies, so they stay covered by
  `GlobalExceptionHandler`'s single error shape.
