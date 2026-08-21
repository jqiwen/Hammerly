# Hammerly Microservice Architecture

## Service boundary

`hammerly-backend` is Hammerly Core and remains the only public application backend. It owns users, authentication and JWTs, profiles, auctions, bids, watchlists, payment methods, transactional rules, the Supabase PostgreSQL tables, and Flyway migrations.

`hammerly-ai` is an independent internal service. In Phase 2 it owns the customer-support system prompt, Spring AI orchestration, OpenAI calls, and the internal chat/SSE API. It has no datasource, does not read Core tables, and shares no Java source or build dependency with Core. The services intentionally define matching HTTP DTOs independently.

```text
React
  |
  | POST /api/ai/support/chat/stream
  v
Hammerly Core (Spring MVC)
  |
  | POST /internal/ai/chat/stream
  v
Hammerly AI (Spring Boot + Spring AI 1.1.7)
  |
  v
OpenAI
  |
  | provider token stream
  v
Hammerly AI -> Core -> React
```

The browser never calls the AI service directly and never receives an OpenAI credential. Core forwards only the validated chat message and recent user/assistant text—not User objects, JWTs, passwords, payment-card data, or database credentials.

## Chat contracts

AI's internal API is:

- `POST /internal/ai/chat`: complete JSON response `{ "answer": "..." }`.
- `POST /internal/ai/chat/stream`: `text/event-stream` response with `chunk`, `done`, or safe `error` events.
- `GET /internal/ai/status`: process status plus whether `OPENAI_API_KEY` is configured.
- `GET /health` and `GET /actuator/health`: process-level health independent of transient provider availability.

Core's public API is:

- `POST /api/ai/support/chat`.
- `POST /api/ai/support/chat/stream`.

Both POST APIs accept this shape:

```json
{
  "message": "What happens after I win one?",
  "history": [
    { "role": "user", "content": "How do auctions work?" },
    { "role": "assistant", "content": "..." }
  ]
}
```

`history` is optional. Only `user` and `assistant` roles are accepted; the current message is limited to 2,000 characters, history to 20 messages, and each history item to 4,000 characters. Core validates before calling AI, and AI validates again at its trust boundary.

## Streaming implementation

Spring AI's `ChatClient.prompt().stream().content()` supplies the real provider `Flux<String>`; AI does not split a completed response into fake chunks. AI serializes every chunk as an SSE JSON data object so whitespace and line breaks survive the transport.

Core remains a normal Spring MVC service. Only the AI stream route uses `StreamingResponseBody` on a bounded executor. `AiPlatformClient` reads the downstream response incrementally into a 1 KiB buffer, writes each read to the browser output stream, and flushes immediately. It never accumulates the full response. The React API abstraction uses POST `fetch()` plus `ReadableStream`, parses SSE frames, and appends all chunks to one progressively updated assistant message.

The Core connection, downstream read, and MVC async timeouts are explicit and configurable. No retry/circuit-breaker framework is installed; OpenAI retries default to zero to avoid duplicate cost.

## Conversation state and Phase 2 boundary

React temporarily owns the current conversation and sends at most the most recent 20 non-error user/assistant messages with each new request. The local welcome message is not sent to the model. Neither Core nor AI persists conversation state.

Answers currently use:

- The Hammerly support system prompt.
- The model's general knowledge.
- Temporary client-provided conversation history.

Phase 2 deliberately has no Hammerly RAG knowledge base, pgvector/embeddings, Redis state, Kafka, persistent AI conversation storage, or recommendation system. Because precise Hammerly policy is not grounded yet, the system prompt instructs the model to state uncertainty and refer users to the static FAQ or support instead of inventing policies. RAG is planned for Phase 3.

## Configuration

| Variable | Service | Purpose | Default |
| --- | --- | --- | --- |
| `OPENAI_API_KEY` | AI | Required for live OpenAI answers | none |
| `OPENAI_MODEL` | AI | Provider model | `gpt-5-mini` |
| `OPENAI_TIMEOUT` | AI | Provider request timeout | `30s` |
| `OPENAI_MAX_RETRIES` | AI | Provider SDK retries | `0` |
| `OPENAI_MAX_OUTPUT_TOKENS` | AI | Maximum completion tokens | `600` |
| `HAMMERLY_AI_URL` | Core | AI service base URL | `http://localhost:5001` |
| `HAMMERLY_AI_CONNECT_TIMEOUT` | Core | Downstream connection timeout | `2s` |
| `HAMMERLY_AI_READ_TIMEOUT` | Core | Downstream read timeout | `45s` |
| `HAMMERLY_AI_STREAM_TIMEOUT` | Core | MVC async stream timeout | `50s` |

`OPENAI_API_KEY` belongs only in the AI process environment or its production secret store. It must never be committed, logged, placed in `VITE_*`, or sent to React.

| Component | Local URL |
| --- | --- |
| React frontend | `http://localhost:3000` |
| Hammerly Core | `http://localhost:5000` |
| Hammerly AI | `http://localhost:5001` |

## Failure isolation

Core does not call AI at startup or from existing marketplace flows. If AI is stopped, provider access fails, the request times out, or OpenAI returns a rate-limit/5xx error, AI support receives a sanitized unavailable response or SSE error event. Core stays running, and registration, login, auctions, bids, watchlists, profiles, and payments remain independent.

The local-only `GET /internal/integration/ai-health` diagnostic verifies Core-to-AI status and returns a sanitized `503` when AI is unavailable. The `prod` profile disables it. `/internal/ai/**` is reserved for service-to-service calls; network restriction and service authentication must be added before exposing AI in production.

## Planned evolution

1. Microservice foundation (complete)
2. LLM customer support and end-to-end streaming (complete locally)
3. RAG and pgvector
4. Redis conversation state/caching and rate limiting
5. Kafka and asynchronous workers
6. Resilience and high-concurrency load testing
7. Prometheus and Grafana
8. Docker, Kubernetes/GKE, and autoscaling

Phase 2 performs no production deployment. The AI service, Core service, and frontend all require redeployment when this phase is released.
