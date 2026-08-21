# Hammerly Microservice Architecture

## Service boundary

`hammerly-backend` is the Hammerly Core service and remains the only public application backend. It owns users, authentication and JWTs, profiles, auctions, bids, watchlists, payment methods, and all transactional business rules. It is the sole owner of the current Supabase PostgreSQL tables and Flyway migrations.

`hammerly-ai` is an independent internal service. It currently owns only its runtime and health/status contract. Future responsibilities may include LLM orchestration, RAG, embeddings, semantic search, recommendation, generated content, customer-support intelligence, and conversation intelligence.

The AI service has no datasource dependency, does not read Core tables, and shares no Java source or build dependency with Core. Each service defines its own HTTP DTOs and builds independently.

## Communication and failure isolation

```text
Browser / React
       |
       v
Hammerly Core -----> Hammerly AI
       |
       v
Supabase PostgreSQL
```

Initial service communication uses REST/HTTP. Core's `AiPlatformClient` calls `GET {HAMMERLY_AI_URL}/internal/ai/status` with explicit connection and read timeouts. The local-only `GET /internal/integration/ai-health` diagnostic returns a sanitized response and reports HTTP `503` when AI is unavailable.

Core never calls AI during startup or from an existing business flow, so AI downtime cannot block registration, login, auction browsing, auction creation, bidding, watchlists, profiles, or payments. The `prod` profile disables the Core diagnostic endpoint.

The `/internal/ai/**` namespace is reserved for service-to-service APIs. Service authentication is intentionally deferred, so Hammerly AI must not be publicly exposed in production until authentication and network access controls are added. The frontend must continue calling Core only.

Future asynchronous workloads may use Kafka, but no broker, producer, consumer, or event contract is part of this phase.

## Local runtime

| Component | URL |
| --- | --- |
| React frontend | `http://localhost:3000` |
| Hammerly Core | `http://localhost:5000` |
| Hammerly AI | `http://localhost:5001` |

`HAMMERLY_AI_URL` configures the downstream base URL in Core and defaults to `http://localhost:5001`. Cloud Run can later set it to the deployed `hammerly-ai` service URL without a code change.

## Planned evolution

1. Microservice foundation
2. LLM integration
3. RAG and pgvector
4. Redis caching and rate limiting
5. Kafka and asynchronous workers
6. Resilience and high-concurrency load testing
7. Prometheus and Grafana
8. Docker, Kubernetes/GKE, and autoscaling

These phases describe direction only. This foundation phase adds no LLM, RAG, vector storage, Redis, Kafka, or Kubernetes implementation and performs no production deployment.
