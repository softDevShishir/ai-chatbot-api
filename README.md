# ai-chatbot-api

A customer-support chatbot API for a fictional online store ("Aronno Mart"). A logged-in API
client sends a customer message in a conversation; the backend saves it, retrieves relevant
store knowledge (products and policies), builds a system prompt, sends the recent conversation
history to the chosen AI provider (Claude, Gemini or OpenAI), saves the reply, and returns it.

All product/policy data is fictional demo data. This is a backend API only — no UI is included.

## Architecture

```
                         ┌─────────────────────┐
  API client  ──HTTP──▶  │   Spring Security    │  (JWT Bearer auth, CORS)
                         └──────────┬───────────┘
                                    │
                         ┌──────────▼───────────┐
                         │   ChatController /    │
                         │   AuthController       │
                         └──────────┬───────────┘
                                    │
                         ┌──────────▼───────────┐
                         │     ChatService       │  guardrails: message length,
                         │                       │  messages/conversation, rate limit,
                         │                       │  daily AI-call cap
                         └──┬────────┬────────┬──┘
                            │        │        │
                 ┌──────────▼─┐ ┌────▼─────┐ ┌─▼──────────────┐
                 │ Knowledge   │ │ System   │ │ ChatHistory     │
                 │ Search      │ │ Prompt   │ │ Builder (N msgs,│
                 │ (Postgres   │ │ Builder  │ │ char budget)    │
                 │ full-text)  │ │          │ │                 │
                 └─────────────┘ └──────────┘ └─────────────────┘
                            │
                 ┌──────────▼───────────┐
                 │   AiCompletionService  │  resolve provider, retry once,
                 │                        │  fall back to AI_FALLBACK_PROVIDER
                 └──┬──────────┬───────┬──┘
                    │          │       │
              ┌─────▼───┐ ┌────▼───┐ ┌─▼──────┐
              │ Claude  │ │ Gemini │ │ OpenAI │   (official SDKs)
              └─────────┘ └────────┘ └────────┘

                 ┌───────────────────────────┐
                 │  PostgreSQL (Flyway)       │
                 │  conversations / messages  │
                 │  products / store_policies │
                 └───────────────────────────┘
```

## Configuration

All configuration is via environment variables (see `.env.example`). Copy it to `.env` and fill
in real values — `.env` is git-ignored and nothing in this repo ever contains a real secret.

| Variable | Default | Notes |
|---|---|---|
| `STORE_NAME` | `Aronno Mart` | Used in the system prompt |
| `APP_TIMEZONE` | `Asia/Dhaka` | Used for the daily AI-call cap's "day" boundary |
| `ADMIN_USERNAME` / `ADMIN_PASSWORD` | _(blank)_ | Required to log in. Blank means login always fails |
| `JWT_SECRET` | _(blank)_ | **Required**, ≥32 bytes. App refuses to start otherwise |
| `JWT_EXPIRATION_MS` | `3600000` | Token lifetime |
| `CORS_ALLOWED_ORIGINS` | _(blank)_ | Comma-separated exact origins; blank = CORS off; wildcards rejected at startup |
| `DEFAULT_PROVIDER` | `claude` | `claude`, `gemini` or `openai` |
| `AI_FALLBACK_PROVIDER` | _(blank)_ | Optional provider to retry with if the chosen one fails |
| `CLAUDE_API_KEY` | _(blank)_ | Required to use provider `claude` |
| `CLAUDE_MODEL` | `claude-haiku-5-5` | Check https://docs.anthropic.com/en/docs/about-claude/models |
| `CLAUDE_MAX_TOKENS` | `2048` | |
| `GEMINI_API_KEY` | _(blank)_ | Required to use provider `gemini` |
| `GEMINI_MODEL` | `gemini-3.5-flash-lite` | Check https://ai.google.dev/gemini-api/docs/models |
| `GEMINI_MAX_TOKENS` | `2048` | |
| `OPENAI_API_KEY` | _(blank)_ | Required to use provider `openai` |
| `OPENAI_MODEL` | `gpt-5.4-mini-2026-03-17` | Check https://platform.openai.com/docs/models. Reasoning models spend part of the budget on hidden reasoning tokens — raise `OPENAI_MAX_TOKENS` if replies come back empty |
| `OPENAI_MAX_TOKENS` | `4096` | |
| `CHAT_HISTORY_MESSAGES` | `10` | Messages sent to the AI per request, oldest first |
| `CHAT_HISTORY_CHAR_BUDGET` | `6000` | History is trimmed (oldest first) to fit this |
| `CHAT_MAX_MESSAGE_CHARS` | `2000` | Longer messages get `400` |
| `CHAT_MAX_MESSAGES_PER_CONVERSATION` | `100` | Beyond this, a conversation gets `409` |
| `CHAT_RATE_LIMIT_PER_MINUTE` | `20` | Per-user, in-memory |
| `CHAT_MAX_AI_CALLS_PER_DAY` | `200` | Global cap, counted from stored assistant messages for "today" in `APP_TIMEZONE` |
| `DB_URL` / `DB_USERNAME` / `DB_PASSWORD` | `jdbc:postgresql://localhost:5433/chatbot` / `postgres` / `secret` | `DB_PASSWORD=secret` is a local demo default — change it for any real deployment |
| `DB_HOST_PORT` | `5433` | Host port docker compose publishes Postgres on |
| `SERVER_PORT` | `8080` | |

Model names live only in configuration, never in Java code.

## AI providers

Three providers are supported, each via its official SDK: `claude`, `gemini`, `openai`.

- **Selecting one.** Each `POST /api/chats/{id}/messages` request may include a `"provider"`
  field. If omitted, `DEFAULT_PROVIDER` is used. An unknown provider id, or a known one with no
  API key configured, returns `400`.
- **Setup.** A provider only needs its `*_API_KEY` set to become usable — the app starts fine
  with none configured; it just means every request has to pick one that *is* configured, or get
  a `400`. Model names and token limits are per-provider (`CLAUDE_MODEL`/`GEMINI_MODEL`/`OPENAI_MODEL`
  and their `*_MAX_TOKENS` counterparts) — check the linked models page before relying on a
  default, since provider lineups change.
- **Retry.** A single retry is attempted automatically if the provider call fails with a
  rate-limit or a temporary/server error. Authentication and bad-request failures are not
  retried — retrying won't fix a bad key or a malformed request.
- **Fallback.** If `AI_FALLBACK_PROVIDER` is set and the primary provider's call still fails
  after its retry, the fallback provider is tried once. The response's `"provider"` field
  reflects whichever provider actually answered, and `"fallbackUsed"` is `true` when that
  happened.
- **OpenAI reasoning models.** Some OpenAI models spend part of the output budget on hidden
  reasoning tokens before producing visible text. If replies come back empty, raise
  `OPENAI_MAX_TOKENS` rather than assuming the call failed.
- **Token usage.** Every reply reports `inputTokens`/`outputTokens` as billed by the provider
  (Gemini counts "thinking" tokens as output; OpenAI counts hidden reasoning tokens as output).

## Running it

### With Docker Compose (recommended)

```bash
cp .env.example .env
# edit .env: set JWT_SECRET, ADMIN_USERNAME/PASSWORD, and at least one AI provider's key
docker compose up --build
```

The API is on `http://localhost:8080`; Postgres is published on `localhost:${DB_HOST_PORT:-5433}`.

### With Maven (for local development)

```bash
cp .env.example .env
# edit .env — point DB_URL at a Postgres instance you run yourself, e.g. via:
docker run -d --name chatbot-postgres -e POSTGRES_DB=chatbot -e POSTGRES_PASSWORD=secret -p 5433:5432 postgres:16

mvn spring-boot:run
```

### Tests

```bash
mvn test
```

Tests never touch Postgres, the network, or any real AI API — they run against H2 with the AI
providers and knowledge search mocked.

## curl walkthrough

```bash
# 1. Log in
TOKEN=$(curl -s -X POST localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"change-me"}' | jq -r .token)

# 2. Create a chat
CHAT_ID=$(curl -s -X POST localhost:8080/api/chats \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"title":"Shoe question"}' | jq -r .id)

# 3. Ask about a product
curl -s -X POST localhost:8080/api/chats/$CHAT_ID/messages \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"message":"Do you have running shoes?"}' | jq

# 4. Ask a follow-up (retrieval uses the last two user messages)
curl -s -X POST localhost:8080/api/chats/$CHAT_ID/messages \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"message":"What about the price?"}' | jq

# 5. Ask something off-topic (should decline, not invent an answer)
curl -s -X POST localhost:8080/api/chats/$CHAT_ID/messages \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"message":"What is the capital of France?"}' | jq

# 6. Trigger a handoff
curl -s -X POST localhost:8080/api/chats/$CHAT_ID/messages \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"message":"I want to speak to a human agent."}' | jq
# -> response "handoff": true, and the [[HANDOFF]] marker is stripped from "reply"

# 7. Pick a specific provider for one message (overrides DEFAULT_PROVIDER)
curl -s -X POST localhost:8080/api/chats/$CHAT_ID/messages \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"message":"Do you have any bags in stock?","provider":"gemini"}' | jq
# -> response "provider" echoes back which provider actually answered ("gemini",
#    or AI_FALLBACK_PROVIDER's id if gemini failed and fallbackUsed is true)

# 8. Unknown or unconfigured provider -> 400
curl -s -X POST localhost:8080/api/chats/$CHAT_ID/messages \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"message":"hi","provider":"mistral"}' | jq
```

## Errors

All errors share one JSON shape:

```json
{ "timestamp": "...", "status": 400, "error": "Bad Request", "message": "...", "path": "/api/..." }
```

| Status | When |
|---|---|
| 400 | Bad input: malformed JSON, failed validation, message too long, unknown/unconfigured provider |
| 401 | Missing/invalid JWT, or wrong login credentials |
| 404 | Conversation not found, or not owned by the caller |
| 409 | Conversation has reached `CHAT_MAX_MESSAGES_PER_CONVERSATION` |
| 429 | Per-user rate limit or the daily AI-call cap exceeded |
| 502 | The AI provider call failed (after any retry/fallback) |
| 503 | The database is unavailable |

## Limitations

- **Demo data only.** The 15 products and 8 policies are fictional, for demonstration.
- **No streaming.** Replies are returned as a single JSON response, not streamed token-by-token.
- **In-memory rate limiting.** Resets on restart; doesn't work across multiple app instances.
- **Single admin account.** There's no per-customer login — this API is meant to sit behind a
  support agent's own tool or a UI that handles end-customer identity separately.
- **Retrieval is keyword search**, not semantic/embedding-based — it can miss paraphrases.
- **The AI can be wrong.** The system prompt restricts it to the retrieved store data, but model
  errors are still possible.

## Roadmap

- Streaming responses
- A React chat UI
- WhatsApp/Telegram integration

## Screenshots

_(placeholder — add screenshots of the API in use, e.g. via curl or a client, here)_
