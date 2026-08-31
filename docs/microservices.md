# Hammerly service architecture

## Synchronous request path

React calls Hammerly Core only. Core authenticates public requests with JWT, serves the marketplace,
uses PostgreSQL as the source of truth, and applies cache-aside Redis reads to auction detail and top
listings. A Redis read/write/invalidation error records a bounded metric/log and falls back to the
database.

For support chat, Core forwards a trusted user ID and shared internal token to Hammerly AI and
proxies SSE bytes without buffering. AI applies rate limiting, builds bounded context, embeds the
current query, searches READY knowledge chunks with pgvector cosine distance, and grounds the model
prompt. Retrieved text is explicitly untrusted reference data; instructions inside it are ignored.

```text
React → Core/JWT → AI → query embedding → PostgreSQL/pgvector → LLM → SSE → React
          ├── PostgreSQL
          └── Redis marketplace cache       AI └── Redis state/RAG cache
```

SSE preserves existing `chunk`, `done`, and safe `error` events and adds an optional `metadata`
event containing only sources actually included in the prompt. Kafka is not part of this path.

## Durable knowledge ingestion

`POST /internal/knowledge/documents` requires `X-Hammerly-Internal-Token` whenever a token is
configured and always in the production profile. Core writes a `PENDING` document and its
`embedding.requested` outbox row in one PostgreSQL transaction. Duplicate source/content pairs return
the existing document.

The outbox relay locks pending rows with `FOR UPDATE SKIP LOCKED`, publishes them to
`hammerly.ai.jobs.v1`, and marks them published only after Kafka confirms the send. Failure leaves the
row pending for a later attempt, providing at-least-once—not exactly-once—delivery.

Worker flow:

```text
Kafka → existing Redis event claim → PROCESSING → deterministic chunks → embedding provider
      → transaction(delete old chunks, insert vectors, READY, increment knowledge version) → ack
```

The default chunker uses a deterministic whitespace-token approximation (650 units, 100 overlap).
OpenAI `text-embedding-3-small` at 1,536 dimensions is the live provider; a normalized hashed
bag-of-words vector is used for tests and local no-cost runs. Stable chunk indexes and UUIDs plus
atomic replacement make repeated delivery safe. Exhausted records go to the existing `.DLT`; an
identifiable non-READY document is marked `FAILED` with only a sanitized exception class.

## Context and retrieval

AI sends the system prompt, optional Redis conversation summary, at most six recent turns, bounded
retrieved chunks, and the current question. Redis retention is separate from model context. The
default context limit is 16,000 characters.

Retrieval is capped at four chunks and a 0.25 cosine similarity threshold. Its cache key is a SHA-256
of normalized query, knowledge-base version, top-K, threshold, and embedding model under
`hammerly:rag:retrieval:v1:*`. The five-minute TTL is configurable. Redis failure falls through to
embedding/vector search. The entire retrieval is bounded to two seconds by default; database,
embedding, or timeout failure continues ungrounded chat and emits metrics without exposing details.

## Trust and secrets

- Browser JWTs are accepted only by Core; AI trusts only Core's internal headers and token.
- Knowledge endpoints use the same internal-token header and require it in production.
- `OPENAI_API_KEY`, database credentials, Redis passwords, JWT secrets, and internal tokens are never
  Vite variables and must live in environment/secret stores.
- Prometheus labels are bounded enums/operations. User IDs, conversation IDs, document IDs, prompts,
  queries, and emails are not metric labels.

## Failure boundaries

- Redis unavailable: marketplace uses PostgreSQL; AI state/cache/rate-limit operations degrade.
- Kafka unavailable: chat continues; knowledge outbox rows remain pending until recovery.
- Worker unavailable: Kafka retains uncommitted jobs for the durable consumer group.
- RAG database/embedding unavailable: retrieval fails within its bound and chat continues without
  citations.
- Provider 429/5xx/timeout: at most two attempts with capped backoff, only before the first token.
- Provider/circuit/bulkhead failure: a stable safe API/SSE error is returned without secret details.

See the root README for local commands, environment variables, evaluation, dashboards, CI/CD, and
deployment controls.
