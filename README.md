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
  ├── PostgreSQL + Flyway
  ├── Redis marketplace cache-aside
  └── trusted internal user header + SSE
       ↓
     Hammerly AI (hammerly-ai, :5001)
       ├── Redis conversation, response, rate-limit, and RAG caches
       ├── query embedding
       ├── cosine retrieval → PostgreSQL + pgvector
       ├── bounded summary + recent turns + grounded chunks
       └── OpenAI through Spring AI (or deterministic local provider)
            ↓
          sources/chunk/done SSE events → React response + Sources
```

The durable knowledge-ingestion path is:

```text
protected knowledge POST
  ↓ one PostgreSQL transaction
knowledge_documents(PENDING) + outbox_events
  ↓ at-least-once outbox relay
Kafka hammerly.ai.jobs.v1 (key = document ID)
  ↓ consumer group hammerly-worker-v1
hammerly-worker (:5002)
  ↓ PROCESSING → deterministic chunking → embeddings
atomic chunk/vector replacement in pgvector
  ↓
READY + knowledge-base version increment
```

Kafka is **not in the synchronous AI response path**. A stopped broker or worker does not block chat. Knowledge events are durable in the transactional outbox while Kafka is unavailable; after recovery the relay republishes pending rows. AI conversation analytics events remain best-effort side effects and do not change the chat result.

The repository contains:

- `hammerly-ui/`: React 19, TypeScript, Vite, and Tailwind UI.
- `hammerly-backend/`: authentication, users, profiles, auctions, bids, watchlists, payment metadata, PostgreSQL/Flyway persistence, and the public AI proxy.
- `hammerly-ai/`: OpenAI integration, bounded RAG retrieval, prompt grounding, citations, SSE streaming, bounded provider retry, and Redis state/caches.
- `hammerly-worker/`: Java 21/Spring Boot 3.5.16 at-least-once consumer for idempotent knowledge indexing, summaries, and lightweight analytics.

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

## Phase 5 — high concurrency and provider resilience

The production Spring AI/OpenAI SDK call is now protected by named Resilience4j TimeLimiter, Retry,
CircuitBreaker, and Bulkhead instances. Retry is bounded and status-aware; 429 honors `Retry-After`,
retryable 5xx/connection/timeout failures back off with jitter, and permanent 4xx failures fail
immediately. Streaming uses separate first-token and idle timeouts and can retry only before the
first token, preventing duplicate client output.

Both Java services use bounded virtual-thread MVC streaming executors. Core also uses a bounded,
pooled HTTP client to AI, while the provider bulkhead remains the smaller paid-resource protection
boundary. Saturation and open circuits return stable SSE errors without exposing provider details.
Redis keeps its graceful degradation and Kafka remains outside the synchronous response path.

The reproducible deterministic-provider benchmark passed two-minute holds at 100, 500, and 1,000
VUs with 13,547 successful streams and zero failures. At 1,000 VUs it completed 57.94 successful
streams/s, with 127 ms first-event p95 and 7,193 ms full-stream p95. This is local comparative
evidence, not a production OpenAI capacity guarantee. See
[the Phase 5 results](docs/performance/phase5-results.md),
[baseline](docs/performance/phase5-baseline.md), and
[configuration reference](docs/performance/phase5-configuration.md).

## Phase 6 — Prometheus and Grafana observability

Core, AI, and Worker now expose Prometheus scrape endpoints, with a local Prometheus/Grafana stack
and a provisioned **Hammerly — System Overview** dashboard. The dashboard covers traffic, latency,
provider TTFT/full completion, Redis and marketplace cache behavior, RAG embedding/search/results,
Kafka processing/DLT, and JVM health. Prometheus scrapes every five seconds.

Phase 7 merges this configuration into the main local stack. Start it with:

```bash
docker compose up -d --build
```

Open Prometheus at <http://localhost:9090> and Grafana at <http://localhost:3001> (local development
credentials: `admin` / `admin`). See the [Phase 6 observability guide](docs/observability/phase6.md)
for architecture, PromQL, dashboard sections, security boundaries, validation, and shutdown steps.

## Phase 8 — GKE Autopilot and real autoscaling

Hammerly retains a separate, teardown-friendly GKE demonstration target as historical infrastructure-as-code. Kustomize bases deploy two
Backend replicas, two-to-ten AI replicas behind an `autoscaling/v2` CPU HPA, one Worker, persistent
in-cluster Redis and Kafka KRaft, Prometheus, Grafana, and kube-state-metrics. Only Backend receives
an external LoadBalancer; every other service remains cluster-internal. The obsolete GKE GitHub
Actions deployment has been removed; the manifests and explicit local scripts remain available and
are separate from the Cloud Run production path.

The `demo` overlay retains real OpenAI behavior. The `loadtest` overlay selects the deterministic
Phase 5 provider, keeping the 100/500/1,000-VU autoscaling benchmark free of OpenAI calls. Lifecycle,
secrets, observability, CI/CD, cost controls, exact commands, and production limitations are in the
[GKE deployment guide](docs/deployment/gke.md). Real measured evidence belongs in the
[Phase 8 results](docs/performance/phase8-gke-results.md); the phase is not complete until that file
and `load-test/phase8/results/` contain an actual GKE run.

## Metrics and health

Core, AI, and Worker expose only `/actuator/health` and `/actuator/prometheus` locally on ports 5000,
5001, and 5002 respectively. Worker port 5002 is a management-only listener. The production profile
exposes health only, so metrics are not published on the current public application listeners.

Canonical Prometheus metrics include:

- `ai_requests_total`, `ai_end_to_end_duration_seconds`, and `ai_core_proxy_duration_seconds`
- `llm_errors_total`, provider first-token latency, retries, 429/5xx/timeouts, bulkhead, and circuit state
- `ai_provider_first_token_seconds`, `ai_provider_full_response_seconds`, and `ai_provider_retries_total`
- `redis_cache_hits_total`, `redis_cache_misses_total`, `marketplace_cache_*`, and `active_conversations`
- histogram `kafka_processing_duration_seconds`, plus bounded worker outcome/retry/DLT metrics
- `rag_embedding_duration_seconds`, `rag_search_duration_seconds`, `rag_search_results`, and `rag_cache_*`

## CI/CD pipeline

CI is the primary validation gate. It never reads production secrets or calls paid OpenAI APIs. A
successful affected-service job on a push to `main` calls the corresponding deployment workflow;
pull requests and manual CI runs validate without deploying.

```text
push / pull request
        |
        v
       CI
        |
        +----------------------+----------------------+
        |                      |                      |
   AI changed             Core changed           UI changed
        |                      |                      |
    Deploy AI             Deploy Core         Deploy Frontend
        |                      |                      |
    Cloud Run              Cloud Run            GitHub Pages

Worker change -> Worker CI only
Manual Publish Worker Image -> Artifact Registry (no runtime deployment)
```

[`ci.yml`](.github/workflows/ci.yml) exposes `UI CI`, `Core CI`, `AI CI`, and `Worker CI` checks and
selects only affected services. Java validation uses Java 21, each service's Maven wrapper, and Maven
dependency caching; UI validation uses Node 24, `npm ci`, lint, type-checking, and a production build.
Changes to shared deployment logic can select both Cloud Run services, while validation-only files
do not cause production deployments.

[`deploy-frontend.yml`](.github/workflows/deploy-frontend.yml) builds and publishes the GitHub Pages
artifact after UI CI succeeds. A direct manual run performs lint and type-checking itself. It fails
early when the public repository variable `HAMMERLY_API_URL` is absent. The checked-in
`hammerly-ui/public/CNAME` continues to provide `hammerly.jqiwen.com`.

Core and AI use production multi-stage, non-root images. [`deploy-core.yml`](.github/workflows/deploy-core.yml)
and [`deploy-ai.yml`](.github/workflows/deploy-ai.yml) are manually runnable and are also called by CI
after the relevant tests pass. The common [`_deploy-cloud-run.yml`](.github/workflows/_deploy-cloud-run.yml)
authenticates with GitHub OIDC/Google Workload Identity Federation, builds and pushes both
`${GITHUB_SHA}` and `latest`, deploys the immutable SHA tag to Cloud Run, and verifies the health
endpoint. Its Docker build runs `mvn package -DskipTests`, so CD retains compile/package validation
without repeating CI's complete Maven test suite. Runtime secret values come directly from Google
Secret Manager. `GCP_DEPLOYMENTS_ENABLED` defaults to disabled-by-absence.

AI's `/internal/**` endpoints require `HAMMERLY_AI_INTERNAL_TOKEN` when it is configured. Core attaches the same token to every internal AI request. Production binds the shared value from Secret Manager to both services, while local development may leave it empty. This prevents a public Cloud Run URL from treating a caller-supplied user header as trusted. Health checks remain non-AI, public, and free of provider calls.

The Kafka worker is different from the request-driven services. [`deploy-worker.yml`](.github/workflows/deploy-worker.yml)
is manual-only: its multi-stage Docker build packages the Worker and publishes
`hammerly-worker:${GITHUB_SHA}` plus `latest` to Artifact Registry. Worker tests remain in CI, and the
publication workflow never starts a Worker Pool or other runtime. Kafka stays disabled in production
until its provider, network path, and continuous worker runtime are restored deliberately.

The old GKE deployment and Phase 5 load-test Actions workflows have been removed from the Actions
UI. Kubernetes/Helm examples, k6 scripts, retained results, and performance documentation remain
available for explicit local/manual use.

Complete one-time API, Artifact Registry, service-account, Workload Identity Federation, Secret Manager, GitHub variable, rollout-order, and branch-protection setup in [`docs/deployment/gcp.md`](docs/deployment/gcp.md). No long-lived GCP JSON key is required.

### Production deployment

The live environment uses project `hammerly-506214` in `us-west1`:

```text
https://hammerly.jqiwen.com (GitHub Pages)
  → hammerly-backend (Cloud Run)
    → hammerly-ai (Cloud Run)
      ├── OpenAI (key from Secret Manager)
      ├── Upstash Redis over TCP/TLS (password from Secret Manager)
      └── Supabase PostgreSQL + pgvector (when RAG is enabled)
```

Production AI uses Upstash's normal Redis TCP endpoint with TLS. Host, port, and SSL mode are
non-secret GitHub variables; only the Redis password is injected from the Google Secret Manager
secret `hammerly-redis-password`. The Upstash REST token is not used. Kafka remains disabled and no
production Worker runtime is started by these workflows.

### Local development versus production

| Concern | Local development | Production |
| --- | --- | --- |
| PostgreSQL | Developer Supabase URL or test container | Secret Manager `SUPABASE_DB_URL` |
| Redis | Docker Compose on `localhost:6379`, optional per flag | Upstash Redis over TCP/TLS; password from Secret Manager |
| Kafka | Docker Compose on `localhost:9092`, optional per flag | Disabled for now; Worker image/runtime support retained |
| OpenAI | Ignored local file or environment variable | Secret Manager `OPENAI_API_KEY` |
| Core-to-AI trust | Token optional on localhost | Secret Manager shared internal token |
| GCP authentication | Not required | GitHub OIDC/WIF, no service-account key file |
| Deployment URLs | localhost defaults | GitHub variables, never Java constants |

## Local configuration

The root `.env.example` is the fresh-clone Compose template. The service-level examples remain useful
when running Java processes directly. Important Phase 4/5 variables are:

| Variable | Default | Purpose |
| --- | --- | --- |
| `HAMMERLY_REDIS_ENABLED` | `true` locally and in current production | Selects Redis or bounded process-local AI state |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Broker address used by AI and worker |
| `HAMMERLY_KAFKA_ENABLED` | `true` locally, `false` production default | Enables best-effort AI event publication |
| `HAMMERLY_WORKER_GROUP_ID` | `hammerly-worker-v1` | Durable worker consumer group |
| `HAMMERLY_WORKER_CONCURRENCY` | `3` | Parallel listener containers |
| `HAMMERLY_AI_SUMMARY_AFTER_MESSAGES` | `10` | Stored-message summary threshold |
| `HAMMERLY_WORKER_PROCESSED_EVENT_TTL` | `7d` | Completed-event marker TTL |
| `HAMMERLY_WORKER_PROCESSING_LOCK_TTL` | `2m` | In-progress event lock TTL |
| `HAMMERLY_WORKER_SUMMARY_TTL` | `7d` | Separate summary TTL |
| `HAMMERLY_AI_INTERNAL_TOKEN` | empty locally | Shared Core-to-AI production request token |
| `HAMMERLY_AUTH_JWT_TTL` | `45m` | Core access-token lifetime |
| `HAMMERLY_AUTH_RATE_LIMIT_REDIS_ENABLED` | `true` in Compose, `false` Cloud Run default | Distributed auth throttling with bounded process-local fallback |
| `HAMMERLY_AUTH_LOGIN_LIMIT` / `HAMMERLY_AUTH_LOGIN_WINDOW` | `10` / `1m` | Login requests per client IP and window |
| `HAMMERLY_AUTH_REGISTER_LIMIT` / `HAMMERLY_AUTH_REGISTER_WINDOW` | `5` / `10m` | Registration requests per client IP and window |
| `HAMMERLY_MARKETPLACE_CACHE_ENABLED` | `true` locally, `false` production default | Core Redis cache-aside with PostgreSQL fallback |
| `HAMMERLY_MARKETPLACE_CACHE_OPERATION_TIMEOUT` | `500ms` | Hard deadline before marketplace reads fall through to PostgreSQL |
| `REDIS_CONNECT_TIMEOUT` / `REDIS_COMMAND_TIMEOUT` | `250ms` | Bounds synchronous Redis failure detection in the local services |
| `HAMMERLY_AI_LLM_MAX_ATTEMPTS` | `2` | Total attempts; retry is allowed only before the first token |
| `HAMMERLY_AI_LLM_FIRST_TOKEN_TIMEOUT` | `8s` | Maximum wait for the first streamed token per attempt |
| `HAMMERLY_AI_LLM_IDLE_TIMEOUT` | `10s` | Maximum gap between streamed tokens |
| `OPENAI_MODEL` | `gpt-4.1-mini` | Low-latency non-reasoning support model |
| `OPENAI_MAX_OUTPUT_TOKENS` | `200` | Bounded support-answer output budget |
| `HAMMERLY_RAG_ENABLED` | `false` fail-safe service default, `true` in Compose/Kubernetes/Cloud Run deploy | Query embedding and pgvector retrieval switch |
| `HAMMERLY_RAG_TOP_K` | `3` | Maximum retrieved chunks; Recall@3 remains 1.0 in the repository evaluation |
| `HAMMERLY_RAG_SIMILARITY_THRESHOLD` | `0.25` | Minimum cosine similarity |
| `HAMMERLY_RAG_TIMEOUT` | `1200ms` | Whole retrieval deadline before graceful degradation |
| `HAMMERLY_RAG_CACHE_TTL` | `5m` | Versioned Redis retrieval-cache TTL |
| `HAMMERLY_RAG_KB_VERSION_CACHE_TTL` | `45s` | Process-local knowledge-version TTL before PostgreSQL refresh |
| `HAMMERLY_AI_CONTEXT_RECENT_TURNS` | `3` | Recent user/assistant turns supplied to the model |
| `HAMMERLY_AI_CONTEXT_MAX_CHARS` | `8000` | Combined model-context character bound |
| `HAMMERLY_AI_EMBEDDING_PROVIDER` | `deterministic` locally | Shared ingestion/query provider (`openai` in live environments) |
| `HAMMERLY_RAG_CHUNK_TOKENS` | `650` | Deterministic whitespace-token approximation per chunk |
| `HAMMERLY_RAG_CHUNK_OVERLAP_TOKENS` | `100` | Approximate chunk overlap |
| `HAMMERLY_AI_LLM_MAX_CONCURRENT_CALLS` | `32` | Production provider bulkhead permits |
| `HAMMERLY_AI_STREAM_MAX_CONCURRENT` | `1100` | AI MVC streaming executor bound |
| `HAMMERLY_AI_CONNECTION_POOL_MAX_TOTAL` | `1100` | Core-to-AI outbound pool bound |

## Run the full local stack

The fresh-clone path requires Docker with Compose and a long random local JWT secret. The default
stack includes PostgreSQL with pgvector, Redis, Kafka, Prometheus, and Grafana.
The UI remains a separately started development service on port 3000.

```bash
git clone https://github.com/jqiwen/Hammerly.git
cd Hammerly
cp .env.example .env
# Fill JWT_SECRET in .env. Leave SUPABASE_DB_* blank for local pgvector.
docker compose up -d --build
```

The default stack starts `backend`, `ai`, `worker`, `postgres`/pgvector, `redis`, `kafka`,
`prometheus`, and `grafana`.
AI uses the deterministic, no-cost `loadtest` provider by default. The example environment enables
the local Redis/Kafka integrations so cache, outbox, worker, and observability flows work after one
Compose startup; production profiles still default both optional integrations off.
An existing host `OPENAI_API_KEY` is not used for chat unless `.env` explicitly sets
`HAMMERLY_AI_PROFILE=live` and supplies the key.

Use these lifecycle commands from the repository root:

```bash
docker compose ps
docker compose logs -f
docker compose down
docker compose down -v
```

`docker compose down` preserves named data volumes. The `-v` form intentionally removes local Redis,
Kafka, Prometheus, Grafana, and optional PostgreSQL data.

### PostgreSQL / Supabase

Compose defaults to `pgvector/pgvector:pg17` at `postgres:5432` (host port `5433`). To use Supabase,
set `SUPABASE_DB_URL`, username/password as needed, and `HAMMERLY_DB_SSL_MODE=require`. Supabase must
have the `vector` extension enabled before Flyway V4 runs if the project role cannot create
extensions: `CREATE EXTENSION IF NOT EXISTS vector;`.

### Knowledge ingestion and retrieval evaluation

With the full stack running and a shared internal token configured, ingest and poll the sample:

```powershell
.\scripts\knowledge\ingest-support.ps1 -InternalToken $env:HAMMERLY_AI_INTERNAL_TOKEN
```

The expected lifecycle is `202/PENDING → PROCESSING → READY`. Reposting identical source/content is
deduplicated. Run the no-cost retrieval evaluation with:

```powershell
cd hammerly-worker
.\mvnw.cmd -Dtest=RagRetrievalEvaluationTest test
```

This reports deterministic Recall@4/hit rate against
[`docs/rag/evaluation.json`](docs/rag/evaluation.json); it is not generated-answer accuracy.
The deterministic embedding is a repeatable lexical test double, not a semantic model. When using it
to exercise citation transport against one broad sample chunk, a lower local
`HAMMERLY_RAG_SIMILARITY_THRESHOLD` may be appropriate; keep a validated threshold for OpenAI/live
embeddings.

Ports default to UI `3000`, Core `5000`, AI `5001`, worker management `5002`, Redis `6379`, Kafka
`9092`, Grafana `3001`, and Prometheus `9090`. Containers use `redis:6379`, `kafka:29092`,
`ai:5001`, and the other Docker service names internally; host Kafka clients use `localhost:9092`.

Inspect worker lag and generated summaries with:

```bash
docker compose exec kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server kafka:29092 --group hammerly-worker-v1 --describe
docker compose exec redis redis-cli GET "hammerly:conversation:summary:{userId}:{conversationId}"
```

Prometheus is available at <http://localhost:9090> and Grafana at <http://localhost:3001>. The
Prometheus datasource and **Hammerly — System Overview** dashboard are provisioned automatically.
The default Grafana credentials are `admin` / `admin` and are only for local development.

To run the UI separately:

```bash
cd hammerly-ui
npm install
npm run dev
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
npm ci
npm run type-check
npm run lint
npm run build
```

Worker tests use embedded KRaft Kafka and deterministic embeddings; CI makes no paid API call. Core
integration tests use the pgvector PostgreSQL Testcontainers image when Docker is available.

Run the free deterministic SSE smoke/full suite using the commands and safety guard in
[`load-test/phase5/README.md`](load-test/phase5/README.md). Full mode refuses to run without an
explicit load-test provider selection.

## Failure behavior and latency tuning

Redis failures fall through to PostgreSQL or uncached AI work. The marketplace cache has a hard
operation deadline and AI briefly short-circuits repeated Redis operations after a connection
failure. Query embedding, PostgreSQL, or
vector-search failures are bounded by `HAMMERLY_RAG_TIMEOUT` and chat continues without sources.
Kafka is absent from chat; durable ingestion stays pending in the outbox until Kafka recovers.
Worker delivery is at least once, with stable event IDs, Redis processing claims, deterministic
chunk IDs, atomic replacement, retries, DLT publication, and sanitized `FAILED` state.

The former interactive worst case allowed three provider attempts with a 12-second first-token
timeout plus backoff while Core waited roughly 45–50 seconds. Defaults are now two attempts, an
8-second first-token deadline, 10-second idle deadline, capped two-second `Retry-After`, and concise
200-token `gpt-4.1-mini` support answers without a reasoning-token budget. Provider retry remains impossible after any token has been
emitted. Cloud Run cold-start cost/latency is selected independently with GitHub variables
`CORE_CLOUD_RUN_MIN_INSTANCES` and `AI_CLOUD_RUN_MIN_INSTANCES`: keep both at `0` for cost
saving, or set both to `1` for warm portfolio/demo instances.

Every completed SSE request writes one identifier-free `ai_latency` summary with Core-to-AI
network time, Redis summary, knowledge-version, RAG-cache, embedding, vector-search, total RAG
duration, grounded-FAQ-cache, provider TTFT and attempts, first SSE-token time, and total duration.
Provider completion logs include only finish reason and
token/character counts so token-limit truncation is diagnosable without recording prompts or
retrieved text. Core writes a matching `core_ai_latency` summary with request-to-AI-start, first AI byte,
and total browser-facing stream time. These fields are intended for cold-versus-warm comparisons.

With the production Upstash endpoint reachable, keep `HAMMERLY_REDIS_ENABLED=true` so conversation
summaries, versioned RAG retrievals, and explicitly standalone grounded FAQ answers survive across
Cloud Run requests and instances. Redis failures remain fail-open, and the connection uses normal
Redis TCP/TLS rather than the Upstash REST API.

The GKE demo is intentionally separate from the Cloud Run production path. Prometheus and Grafana
are private ClusterIP services reached through `kubectl port-forward`; in-cluster Redis and Kafka are
portfolio demonstration infrastructure rather than a proposed HA production data tier.
