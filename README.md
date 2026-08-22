<p align="center">
  <img src="docs/image/hammerly-banner.png" alt="Hammerly Banner" width="100%" />
</p>

# Hammerly — Auction Platform with AI Support

Hammerly is a React and Spring Boot auction application with an isolated AI support service and Kafka-backed background worker. The browser communicates only with Hammerly Core; Core verifies JWTs and proxies AI traffic to the internal AI service.

Live frontend: [hammerly.jqiwen.com](https://hammerly.jqiwen.com)

## Implemented architecture

The synchronous user-facing path is:

```text
React 19
  ↓ HTTP + JWT + SSE
Hammerly Core (hammerly-backend, :5000)
  ↓ trusted internal user header + SSE
Hammerly AI (hammerly-ai, :5001)
  ├── Redis (:6379)
  │   ├── recent conversation state
  │   ├── completed-response cache
  │   └── distributed rate limiting
  └── OpenAI through Spring AI
       ↓
      SSE response
```

The asynchronous side-effect path added in Phase 4 is:

```text
successful AI turn committed to Redis
  ↓ non-blocking dispatch on a dedicated executor
Kafka (:9092, KRaft)
  ├── hammerly.ai.events.v1
  └── hammerly.ai.jobs.v1
       ↓ consumer group hammerly-worker-v1
hammerly-worker (:5002)
  ├── AI-turn analytics metrics
  └── extractive conversation summaries → separate Redis key
```

Kafka is not in the real-time AI response path. Hammerly AI does not wait for Kafka metadata, delivery, acknowledgment, or the worker before completing a chat. A stopped worker leaves chat fully operational while Kafka retains its uncommitted backlog. When the same consumer group restarts, it resumes from committed offsets. A broker outage is also isolated from chat, but events attempted while the broker is completely unavailable are best-effort and are not guaranteed to be recovered.

The repository contains:

- `hammerly-ui/`: React 19, TypeScript, Vite, and Tailwind UI.
- `hammerly-backend/`: authentication, users, profiles, auctions, bids, watchlists, payment metadata, PostgreSQL/Flyway persistence, and the public AI proxy.
- `hammerly-ai/`: OpenAI integration, SSE streaming, bounded provider retry, Redis conversation/cache/rate-limit state, and the asynchronous Kafka producer.
- `hammerly-worker/`: independent Java 21/Spring Boot 3.5.16 Kafka consumer for idempotent summaries and lightweight analytics.

## Phase 3 — Redis-backed AI state

Recent conversation messages are stored at:

```text
hammerly:conversation:{trustedUserId}:{conversationId}
```

A Lua operation atomically appends messages, trims to the configured maximum (20 by default), and refreshes the 24-hour default TTL. Browser history is accepted only as a bootstrap when Redis has no stored history.

Completed AI answers are cached for 15 minutes by default at `hammerly:ai:response:v1:{sha256}`. The key includes prompt, user, conversation, system prompt, and ordered context. A response is cached and appended only after a complete model stream; partial or failed streams are not committed.

The distributed rate limiter defaults to 20 requests per trusted user per 60-second fixed window and uses `hammerly:rate-limit:ai:{trustedUserId}:{windowNumber}`. Redis state operations degrade gracefully and rate limiting fails open during an outage so infrastructure state loss does not make AI support unavailable.

## Phase 4 — Kafka and async worker

After a complete answer and successful Redis conversation append, AI schedules two `message.created` facts. When stored history reaches the configurable threshold (10 messages by default), AI also schedules one `conversation.summary.requested` job. A Redis `SET NX` marker prevents duplicate requests for that threshold.

All conversation records use `conversationId` as their Kafka key. The local broker uses three partitions: events for one conversation remain ordered, while different conversations can run in parallel. AI sends on a dedicated bounded executor and uses short producer timeouts; callbacks record success/failure without changing the chat result.

The worker uses group `hammerly-worker-v1`, `auto.offset.reset=earliest`, disabled auto-commit, and `MANUAL_IMMEDIATE` acknowledgment. It acknowledges only after processing returns successfully. Delivery is at least once. A Redis processing lock coordinates concurrent duplicates, and a completed marker at `hammerly:worker:processed:{eventId}` suppresses redelivery for seven days.

Processing gets an initial attempt plus three fixed 500 ms retries. Exhausted records retain their key and partition and go to `<original-topic>.DLT`. Summary records are stored separately for seven days by default:

```text
hammerly:conversation:summary:{trustedUserId}:{conversationId}
```

The Phase 4 summarizer is deterministic and extractive, so background processing makes no paid model call. The versioned job contains the bounded conversation snapshot needed for worker-offline recovery. See [docs/events/README.md](docs/events/README.md) for every topic and wire contract.

## Metrics and health

AI exposes `/actuator/health` and `/actuator/metrics` on port 5001. Existing cache, rate-limit, Redis, request, and provider metrics remain, with:

- `hammerly.kafka.publish.success` tagged by `eventType`
- `hammerly.kafka.publish.failure` tagged by `eventType`

The worker exposes the same Actuator endpoints on port 5002 and records:

- `hammerly.worker.event.processed`
- `hammerly.worker.event.failed`
- `hammerly.worker.event.retry`
- `hammerly.worker.event.dlt`
- `hammerly.worker.event.duplicate`
- `hammerly.worker.analytics.ai_turn.completed`
- `hammerly.worker.summary.success`
- `hammerly.worker.summary.failure`
- `hammerly.worker.summary.latency`

## Local configuration

`hammerly-ai/.env.example`, `hammerly-worker/.env.example`, `hammerly-backend/.env.example`, and `hammerly-ui/.env.example` document service-specific values. Important Phase 4 variables are:

| Variable | Default | Purpose |
| --- | --- | --- |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Broker address used by AI and worker |
| `HAMMERLY_KAFKA_ENABLED` | `true` | Enables best-effort AI event publication |
| `HAMMERLY_WORKER_GROUP_ID` | `hammerly-worker-v1` | Durable worker consumer group |
| `HAMMERLY_WORKER_CONCURRENCY` | `3` | Parallel listener containers |
| `HAMMERLY_AI_SUMMARY_AFTER_MESSAGES` | `10` | Stored-message summary threshold |
| `HAMMERLY_WORKER_PROCESSED_EVENT_TTL` | `7d` | Completed-event marker TTL |
| `HAMMERLY_WORKER_PROCESSING_LOCK_TTL` | `2m` | In-progress event lock TTL |
| `HAMMERLY_WORKER_SUMMARY_TTL` | `7d` | Separate summary TTL |

## Run locally

Requirements: Java 21+, Node/npm, Docker, an OpenAI API key for live AI answers, and a PostgreSQL connection for Core.

Start Redis and the single-node KRaft broker:

```powershell
docker compose up -d redis kafka
docker compose ps
docker compose exec redis redis-cli ping
docker compose exec kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
```

Start each application in a separate PowerShell terminal:

```powershell
cd hammerly-ai
$env:OPENAI_API_KEY = "your-key"
.\mvnw.cmd spring-boot:run
```

```powershell
cd hammerly-backend
$env:SUPABASE_DB_URL = "your-jdbc-url"
$env:JWT_SECRET = "your-development-secret"
.\mvnw.cmd spring-boot:run
```

```powershell
cd hammerly-worker
.\mvnw.cmd spring-boot:run
```

```powershell
cd hammerly-ui
npm install
npm run dev
```

Ports default to UI `3000`, Core `5000`, AI `5001`, worker `5002`, Redis `6379`, and Kafka `9092`.

Inspect worker lag and a generated summary with:

```powershell
docker compose exec kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --group hammerly-worker-v1 --describe
docker compose exec redis redis-cli GET "hammerly:conversation:summary:{userId}:{conversationId}"
```

## Build and test

```powershell
cd hammerly-ai
.\mvnw.cmd test

cd ..\hammerly-worker
.\mvnw.cmd test

cd ..\hammerly-backend
.\mvnw.cmd test

cd ..\hammerly-ui
npm run type-check
npm run lint
npm run build
```

Worker tests use embedded KRaft Kafka and do not require Docker, Redis, OpenAI, or paid calls. Core integration tests use Testcontainers PostgreSQL when Docker is available.

## Future Phase 5

Phase 4 defines `embedding.requested` and an embedding handler boundary only. The following are deliberately not implemented yet:

- knowledge-base/document ingestion and chunking
- embedding model integration and embedding storage
- pgvector schema and vector similarity search
- retrieval, source citation, and prompt grounding
- RAG evaluation and retrieval caching

Kubernetes, GKE, and Prometheus/Grafana deployment dashboards also remain future infrastructure work.
