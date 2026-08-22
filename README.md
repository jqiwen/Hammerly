<p align="center">
  <img src="docs/image/hammerly-banner.png" alt="Hammerly Banner" width="100%" />
</p>

# Hammerly — Auction Platform with AI Support

Hammerly is a React and Spring Boot auction application with an isolated Spring AI customer-support service. The browser communicates only with Hammerly Core; Hammerly Core verifies JWTs and proxies AI traffic to the internal Hammerly AI service.

Live frontend: https://hammerly.jqiwen.com

<<<<<<< HEAD
## Implemented architecture
=======

### 🌐 [View Hammerly →](https://hammerly.jqiwen.com)

---

## Overview

Hammerly is a full-stack auction platform that allows users to create listings, place bids, manage watchlists, and interact with an AI-powered customer support assistant.

The system separates transactional marketplace workloads from AI workloads using independent Spring Boot microservices.

- **Frontend:** React + TypeScript application
- **Core Service:** Spring Boot service for users, auctions, bids, authentication, and business logic
- **AI Service:** Spring Boot + Spring AI service for LLM orchestration, RAG, embeddings, and semantic search
- **Async Worker:** Kafka consumer service for background AI and event-processing workloads
- **Database:** Supabase PostgreSQL + pgvector
- **Cache:** Redis
- **Messaging:** Apache Kafka
- **Observability:** Prometheus + Grafana
- **Infrastructure:** Docker + Kubernetes + Google Cloud
- **Performance Testing:** k6

The browser communicates only with the Hammerly Core service. AI services remain behind the Core service boundary.

---

## Features

### Online Auction Platform

- User registration and login
- JWT-based authentication
- Create and manage auction listings
- Browse active auctions
- Place and track bids
- Watchlist management
- User profile management
- Payment method management
- PostgreSQL persistence
- Flyway database migrations
- Role and authorization checks through Spring Security

### AI Customer Support

- Floating AI Support assistant
- AI-powered FAQ interface
- Streaming LLM responses
- Multi-turn conversation context
- Hammerly-specific system prompts
- Retrieval-Augmented Generation
- Semantic search
- Vector embeddings
- Source-grounded responses
- Graceful AI provider failure handling

### RAG Knowledge Base

Hammerly AI retrieves platform-specific information before generating responses.

The knowledge base contains:

- Auction rules
- Bidding instructions
- Seller guides
- Account help
- Watchlist information
- Platform FAQs
- Customer support documentation


### Distributed Processing

- Kafka event producers and consumers
- Asynchronous conversation processing
- AI response completion events
- Background conversation summaries
- Knowledge-base embedding jobs
- Analytics event processing
- Independent worker scaling

### Redis

Redis is used for:

- Distributed caching
- AI response caching
- RAG retrieval caching
- Conversation state
- Distributed rate limiting
- Shared state across multiple service instances

### High-Concurrency Design

- Stateless Core and AI services
- Independent service scaling
- Redis-backed shared state
- Kafka-backed asynchronous workloads
- Bounded LLM concurrency
- Explicit request timeouts
- Circuit breakers
- Bulkhead isolation
- Rate limiting
- Graceful degradation
- Kubernetes horizontal autoscaling

---

# Microservice Design

## Hammerly Core

Directory:
>>>>>>> e713cbf6c393665e383fee92684edb68e212be11

```text
React
  ↓ HTTP + JWT + SSE
Hammerly Core (hammerly-backend)
  ↓ trusted internal user header + SSE
Hammerly AI (hammerly-ai)
  ├── Redis
  │   ├── recent conversation state
  │   ├── completed AI response cache
  │   └── distributed per-user rate limiting
  └── OpenAI through Spring AI
```

The repository currently contains:

- `hammerly-ui/`: React 19, TypeScript, Vite, and Tailwind UI
- `hammerly-backend/`: authentication, users, profiles, auctions, bids, watchlists, payment-method metadata, PostgreSQL/Flyway persistence, and the public AI proxy
- `hammerly-ai/`: Hammerly support prompt, OpenAI integration, streaming chat orchestration, Redis state, rate limiting, and Actuator metrics

The marketplace uses JWT authentication. Core verifies the token and creates an `AuthenticatedUser`; the browser cannot choose the internal user identifier sent to AI.

## Phase 3 — Redis-backed AI State

Redis is used only by `hammerly-ai` and has three responsibilities.

### 1. Recent conversation state

Each UI chat hook creates one UUID `conversationId`. Core combines that ID with its trusted authenticated user ID before forwarding the request. For clients without a JWT, Core creates an isolated guest identity scoped to the unguessable conversation UUID; authenticated users always use the user ID from the verified JWT.

Conversation messages are JSON objects containing `role`, `content`, and `timestamp`. A Lua operation appends messages, trims the Redis list to the configured maximum, and refreshes its TTL atomically. The default is the most recent 20 messages for 24 hours. Browser history is accepted only as a backward-compatible bootstrap when Redis has no stored history; stored Redis context is authoritative afterward.

Key:

```text
hammerly:conversation:{trustedUserId}:{conversationId}
```

### 2. AI response cache

Completed responses are cached for 15 minutes by default. The SHA-256 key includes the cache version, system-prompt hash, trusted user ID, conversation ID, normalized prompt, and ordered conversation roles/content. Context changes therefore produce a different key, and cached content cannot cross users or conversations.

Key:

```text
hammerly:ai:response:v1:{sha256}
```

On a cache hit, AI does not call OpenAI. The cached answer is emitted as a normal SSE `chunk` followed by the existing `done` event. An exact retry of the immediately previous user turn checks the prior-context key, so a completed request can be replayed even though its user/assistant pair has already entered conversation history. On a miss, chunks stream immediately; only a successfully completed answer is cached and appended to conversation history.

### 3. Distributed rate limiting

The default policy is 20 AI requests per trusted user per 60-second fixed window. A Redis Lua script performs `INCR` and initial expiry atomically, so the count is shared by all Hammerly AI instances. Core obtains a permit before committing the public streaming response, allowing request 21 to return HTTP 429 with rate-limit headers and this error code:

```json
{
  "error": "AI_RATE_LIMIT_EXCEEDED",
  "message": "Too many AI requests. Please try again shortly."
}
```

Key:

```text
hammerly:rate-limit:ai:{trustedUserId}:{windowNumber}
```

### Redis failure behavior

Conversation reads/writes and response-cache operations log warnings and degrade gracefully; an AI request can continue without stored state or caching. Rate limiting deliberately fails open during a Redis outage and records a warning and failure metric, so Redis loss does not make AI support unavailable. The Redis health contributor is disabled for overall application health because Redis is a degradable dependency.

### Metrics

Actuator exposes `/actuator/health` and `/actuator/metrics`. Phase 3 records:

- `hammerly.ai.cache.hit`
- `hammerly.ai.cache.miss`
- `hammerly.ai.rate_limit.allowed`
- `hammerly.ai.rate_limit.rejected`
- `hammerly.ai.rate_limit.redis_failure`
- `hammerly.ai.conversation.read` with `outcome=success|failure`
- `hammerly.ai.conversation.write` with `outcome=success|failure`
- `hammerly.ai.redis.error` with a component tag
- `hammerly.ai.request.latency` with `outcome=cache_hit|llm_request|llm_error`

## Configuration

`hammerly-ai/.env.example` documents the complete local AI configuration. Spring Boot reads these values from the process environment (an IDE or shell may load an `.env` file for you).

| Variable | Default | Purpose |
| --- | ---: | --- |
| `REDIS_HOST` | `localhost` | Redis hostname |
| `REDIS_PORT` | `6379` | Redis port |
| `REDIS_PASSWORD` | empty | Optional Redis password |
| `REDIS_SSL` | `false` | Enable Redis TLS |
| `REDIS_CONNECT_TIMEOUT` | `2s` | Lettuce connection timeout |
| `REDIS_COMMAND_TIMEOUT` | `2s` | Redis command timeout |
| `HAMMERLY_AI_CONVERSATION_MAX_MESSAGES` | `20` | Stored messages per conversation |
| `HAMMERLY_AI_CONVERSATION_TTL` | `86400` | Conversation TTL in seconds |
| `HAMMERLY_AI_RESPONSE_CACHE_TTL` | `900` | Response-cache TTL in seconds |
| `HAMMERLY_AI_RATE_LIMIT_REQUESTS` | `20` | Requests per window |
| `HAMMERLY_AI_RATE_LIMIT_WINDOW_SECONDS` | `60` | Fixed-window length |

Core configuration remains in `hammerly-backend/.env.example`; its AI base URL defaults to `http://localhost:5001`.

## Run locally

Requirements: Java 21+, Node/npm, Docker, an OpenAI API key for live AI answers, and a PostgreSQL connection for Core.

From the repository root, start only Redis and verify it:

```powershell
docker compose up -d redis
docker compose exec redis redis-cli ping
```

The expected response is `PONG`.

In separate terminals, provide the environment variables appropriate to your machine and start the services:

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
cd hammerly-ui
npm install
npm run dev
```

Development ports default to UI `3000`, Core `5000`, AI `5001`, and Redis `6379`.

## Build and test

```powershell
cd hammerly-ai
.\mvnw.cmd test
.\mvnw.cmd package -DskipTests

cd ..\hammerly-backend
.\mvnw.cmd test
.\mvnw.cmd package -DskipTests

cd ..\hammerly-ui
npm run type-check
npm run lint
npm run build
```

Core integration tests use Testcontainers PostgreSQL and run when Docker is available.

## Future phases

The following are not implemented by Phase 3 and remain future work:

- retrieval-augmented generation and knowledge-base ingestion
- embeddings, semantic search, and pgvector
- Kafka producers, consumers, and asynchronous AI workers
- Kubernetes deployment and autoscaling
- Prometheus/Grafana deployment dashboards
