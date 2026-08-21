<p align="center">
  <img src="docs/images/hammerly-poster.png" alt="Hammerly Banner" width="100%" />
</p>

# Hammerly — AI-Powered Auction Platform

A scalable online auction platform built with React, Spring Boot microservices, Spring AI, RAG, Kafka, Redis, PostgreSQL, Docker, Kubernetes, and Google Cloud.

Hammerly combines a full-featured bidding marketplace with an AI-powered customer support platform designed around microservice isolation, asynchronous processing, distributed caching, observability, and high-concurrency performance.

🌐 **Live Site:** https://hammerly.jqiwen.com

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

# Running Locally

## Prerequisites

Install:

- Java 21
- Node.js 20+
- npm
- Docker
- Docker Compose

You also need:

- A Supabase PostgreSQL project
- An OpenAI API key

---

## 1. Clone the Repository

```bash
git clone https://github.com/jqiwen/Hammerly.git
cd Hammerly
```

---

## 2. Start Infrastructure Services

Start Redis, Kafka, Prometheus, Grafana, and other local infrastructure:

```bash
docker compose up -d
```

Verify the containers:

```bash
docker compose ps
```

The local infrastructure includes:

```text
Redis
Kafka
Prometheus
Grafana
```

---

## 3. Start the AI Service

Open a terminal:

```bash
cd hammerly-ai
```

### Windows PowerShell

```powershell
$env:OPENAI_API_KEY="<your-openai-api-key>"
.\mvnw.cmd spring-boot:run
```

### macOS / Linux

```bash
export OPENAI_API_KEY="<your-openai-api-key>"
./mvnw spring-boot:run
```

The AI service runs locally on:

```text
http://localhost:5001
```

---

## 4. Start the Async Worker

Open another terminal:

```bash
cd hammerly-worker
```

### Windows

```powershell
.\mvnw.cmd spring-boot:run
```

### macOS / Linux

```bash
./mvnw spring-boot:run
```

The worker is not required for normal marketplace requests or real-time AI responses.

---

## 5. Start Hammerly Core

Open another terminal:

```bash
cd hammerly-backend
```

Set the required environment variables.

### Windows PowerShell

```powershell
$env:SUPABASE_DB_URL="<your-supabase-jdbc-url>"
$env:JWT_SECRET="<your-jwt-secret>"
$env:HAMMERLY_AI_URL="http://localhost:5001"

.\mvnw.cmd spring-boot:run
```

### macOS / Linux

```bash
export SUPABASE_DB_URL="<your-supabase-jdbc-url>"
export JWT_SECRET="<your-jwt-secret>"
export HAMMERLY_AI_URL="http://localhost:5001"

./mvnw spring-boot:run
```

The Core service runs locally on:

```text
http://localhost:5000
```

Flyway automatically applies PostgreSQL schema migrations during startup.

---

## 6. Start the Frontend

Open another terminal:

```bash
cd hammerly-ui
npm install
npm run dev
```

The frontend runs locally on:

```text
http://localhost:3000
```

Open:

```text
http://localhost:3000
```

---


# Microservice Design

## Hammerly Core

Directory:

```text
hammerly-backend/
```

Responsibilities:

- Authentication
- Authorization
- User management
- Auction management
- Bidding
- Watchlists
- Profiles
- Payment methods
- PostgreSQL transactions
- Public API boundary
- AI request routing

Hammerly Core remains the source of truth for marketplace data.

---

## Hammerly AI

Directory:

```text
hammerly-ai/
```

Responsibilities:

- Spring AI integration
- LLM orchestration
- Streaming responses
- RAG
- Embedding generation
- Vector retrieval
- Prompt management
- Semantic search
- AI customer support

The AI service does not own transactional marketplace data.

---

## Hammerly Worker

Directory:

```text
hammerly-worker/
```

Responsibilities:

- Kafka event consumption
- Conversation summarization
- Analytics processing
- Batch embeddings
- Background AI tasks
- Non-blocking asynchronous workflows

---

# Service Communication

Real-time AI requests use REST + streaming


Background workloads use Kafka


Kafka is intentionally not placed in the real-time chat response path.

---

# Failure Isolation

AI failures do not prevent Hammerly marketplace operations from running.

If Hammerly AI becomes unavailable Core returns a controlled AI-unavailable response instead of failing the entire application.

LLM calls are protected using:

- Timeouts
- Circuit breakers
- Bulkheads
- Controlled retries
- Rate limits
- Graceful fallback responses

---

# High-Concurrency & Performance

Hammerly is designed for high-concurrency workloads by separating synchronous, asynchronous, and AI workloads.

Performance techniques include:

- Stateless microservices
- Horizontal scaling
- Redis distributed caching
- Distributed rate limiting
- Kafka background processing
- Connection pooling
- Explicit timeouts
- Circuit breakers
- Bulkheads
- Kubernetes HPA
- Streaming LLM responses

---

# Deployment

## Frontend

The React frontend is deployed through GitHub Pages.

```text
https://hammerly.jqiwen.com
```

GitHub Actions automatically builds and deploys frontend changes.

---

## Core Backend

Hammerly Core is deployed as an independent cloud service.

Production secrets are stored outside source control.

---

## AI Service

Hammerly AI is deployed independently from Core.

This allows AI workloads to scale independently from transactional auction traffic.

---

## Database

Hammerly uses Supabase PostgreSQL with:

```text
PostgreSQL
pgvector
SSL
Flyway
```


---

# Security

Hammerly uses several security boundaries:

- Spring Security
- JWT authentication
- Input validation
- Distributed rate limiting
- Service isolation
- Environment-based secrets
- Google Secret Manager
- SSL PostgreSQL connections
- LLM request limits
- Controlled AI errors
- No API keys in React
- No database credentials in frontend code

---

