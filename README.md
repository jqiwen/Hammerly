<p align="center">
  <img src="docs/images/hammerly-banner.png" alt="Hammerly Banner" width="100%" />
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

## Tech Stack

**Frontend**  
`React` `TypeScript` `Zustand` `Vite` `React Router` `SSE`

**Core Backend**  
`Java 21` `Spring Boot` `Spring MVC` `Spring Security` `JWT` `JdbcTemplate` `HikariCP` `Flyway`

**AI Platform**  
`Spring AI` `OpenAI` `RAG` `Embeddings` `pgvector` `Semantic Search` `LLM Streaming`

**Distributed Systems**  
`Apache Kafka` `Spring Kafka` `Redis` `Spring Data Redis` `Resilience4j` `Microservices` `Rate Limiting`

**Database**  
`PostgreSQL` `Supabase` `pgvector`

**Observability & Performance**  
`Spring Boot Actuator` `Micrometer` `Prometheus` `Grafana` `k6`

**Testing**  
`JUnit 5` `Mockito` `Spring Boot Test` `Integration Testing` `Load Testing`

**Cloud & DevOps**  
`Docker` `Docker Compose` `Kubernetes` `GKE` `Google Cloud Run` `Artifact Registry` `Secret Manager` `GitHub Pages` `GitHub Actions`

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

Health check:

```text
http://localhost:5001/health
```

Actuator health:

```text
http://localhost:5001/actuator/health
```

The AI service owns:

```text
LLM orchestration
RAG
Embeddings
Semantic search
Prompt construction
Streaming AI responses
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

The worker consumes Kafka events such as:

```text
message.created
conversation.completed
conversation.summary.requested
embedding.requested
ai.response.completed
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

Health check:

```text
http://localhost:5000/health
```

Hammerly Core owns:

```text
Authentication
Users
Profiles
Auctions
Bids
Watchlists
Payment Methods
Transactional Business Logic
Public REST APIs
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

## Local Architecture

With all services running:

```text
                    React
              localhost:3000
                     |
                     v
               Hammerly Core
              localhost:5000
                /          \
               /            \
              v              v
      Supabase PostgreSQL   Hammerly AI
                            localhost:5001
                              /      \
                             /        \
                            v          v
                          Redis      OpenAI
                            |
                            v
                         pgvector


Core / AI
    |
    v
  Kafka
    |
    v
Async Worker
```

The frontend never communicates directly with Hammerly AI or OpenAI.

All browser requests go through Hammerly Core.

---

## 7. Verify the Services

Core:

```bash
curl http://localhost:5000/health
```

AI:

```bash
curl http://localhost:5001/health
```

Example marketplace API:

```bash
curl http://localhost:5000/api/auctions/get-top
```

Open Hammerly in the browser:

```text
http://localhost:3000
```

Then test:

- Register
- Login
- Browse auctions
- Create auction
- Place bid
- Watchlist
- Profile
- FAQ
- AI Support

---

## 8. Stop Local Infrastructure

Stop Kafka, Redis, Prometheus, and Grafana:

```bash
docker compose down
```

---

# System Architecture

Hammerly follows a microservice architecture that separates transactional marketplace workloads from AI workloads.

```text
                               Internet
                                  |
                                  v
                        +-------------------+
                        |   React Frontend  |
                        +---------+---------+
                                  |
                                  | HTTPS / SSE
                                  v
                        +-------------------+
                        |   Hammerly Core   |
                        |    Spring Boot    |
                        +----+---------+----+
                             |         |
                             |         |
                  SQL        |         | REST
                             |         |
                             v         v
                    +-------------+   +------------------+
                    | PostgreSQL  |   |  Hammerly AI     |
                    | + pgvector  |   |  Spring Boot     |
                    +-------------+   |  Spring AI       |
                                      +----+---------+---+
                                           |         |
                                           |         |
                                           v         v
                                        Redis      LLM API
                                           |
                                           |
                     +---------------------+
                     |
                     v
                 Apache Kafka
                     |
                     v
               Async Workers
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

Real-time AI requests use REST + streaming:

```text
React
  |
  v
Core
  |
  v
AI Service
  |
  v
LLM
```

Background workloads use Kafka:

```text
Core / AI
    |
    v
  Kafka
    |
    +------> Analytics Worker
    |
    +------> Summary Worker
    |
    +------> Embedding Worker
```

Kafka is intentionally not placed in the real-time chat response path.

---

# Failure Isolation

AI failures do not prevent Hammerly marketplace operations from running.

If Hammerly AI becomes unavailable:

```text
AI Service       ❌

Authentication   ✅
Auction Listing  ✅
Auction Creation ✅
Bidding          ✅
Watchlists       ✅
Profiles         ✅
```

Core returns a controlled AI-unavailable response instead of failing the entire application.

LLM calls are protected using:

- Timeouts
- Circuit breakers
- Bulkheads
- Controlled retries
- Rate limits
- Graceful fallback responses

---

# Observability

Hammerly uses:

```text
Spring Boot Actuator
        |
        v
Micrometer
        |
        v
Prometheus
        |
        v
Grafana
```

Metrics include:

- Requests per second
- HTTP P50 latency
- HTTP P95 latency
- HTTP P99 latency
- Error rate
- JVM memory
- CPU
- Garbage collection
- Database connection pool usage
- AI request latency
- Time to first token
- LLM provider errors
- RAG retrieval latency
- Redis cache hit rate
- Kafka consumer lag
- Active AI conversations

Custom metrics include:

```text
hammerly_ai_requests_total
hammerly_ai_request_duration_seconds
hammerly_llm_errors_total
hammerly_rag_search_duration_seconds
hammerly_cache_hits_total
hammerly_active_conversations
```

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

## Load Testing

k6 is used to progressively test:

```text
100 concurrent users
        |
        v
500 concurrent users
        |
        v
1,000+ concurrent users
```

Measured metrics include:

- Requests per second
- P50 latency
- P95 latency
- P99 latency
- Error rate
- CPU usage
- Memory usage
- Redis hit rate
- Kafka consumer lag
- Kubernetes replica count

High-volume AI load tests use a mock LLM provider instead of the real OpenAI API to avoid external provider rate limits and unnecessary API costs.

Real OpenAI requests are used only for integration and end-to-end testing.

---

# Docker

Each service is independently containerized.

```bash
docker build -t hammerly-core ./hammerly-backend
docker build -t hammerly-ai ./hammerly-ai
docker build -t hammerly-worker ./hammerly-worker
```

Docker Compose is used for local infrastructure and distributed integration testing.

---

# Kubernetes

Hammerly services can run independently in Kubernetes.

```text
GKE
 |
 +---- hammerly-core
 |      +---- pod
 |      +---- pod
 |      +---- pod
 |
 +---- hammerly-ai
 |      +---- pod
 |      +---- pod
 |
 +---- hammerly-worker
 |      +---- pod
 |      +---- pod
 |
 +---- Prometheus
 |
 +---- Grafana
```

Horizontal Pod Autoscaling allows the AI and Core services to scale independently.

Example:

```text
2 AI pods
    |
traffic increases
    |
    v
5 AI pods
    |
    v
10 AI pods
```

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

## Secrets

Sensitive production configuration is stored using Google Secret Manager.

Examples:

```text
SUPABASE_DB_URL
JWT_SECRET
OPENAI_API_KEY
```

Secrets are never committed to GitHub or exposed to the React frontend.

---

# Production Architecture

```text
https://hammerly.jqiwen.com
            |
            v
       React Frontend
            |
            v
       Hammerly Core
         /       \
        /         \
       v           v
PostgreSQL     Hammerly AI
                  |
           +------+------+
           |             |
           v             v
         Redis        RAG / LLM
           |
           v
         Kafka
           |
           v
       AI Workers
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

The frontend never calls OpenAI directly.

```text
Correct:

Browser
   |
   v
Core
   |
   v
AI
   |
   v
OpenAI
```

---

# Engineering Highlights

Hammerly explores several production-oriented software engineering concepts:

- Full-stack React + Spring Boot application design
- Spring Boot microservice architecture
- Service-to-service REST communication
- Generative AI integration with Spring AI
- Streaming LLM responses
- Retrieval-Augmented Generation
- PostgreSQL vector search with pgvector
- Redis distributed caching
- Distributed rate limiting
- Kafka event-driven processing
- Asynchronous workers
- Fault isolation
- Circuit breakers and bulkheads
- Docker containerization
- Kubernetes orchestration
- Horizontal autoscaling
- Prometheus monitoring
- Grafana dashboards
- k6 high-concurrency load testing
- P50 / P95 / P99 latency analysis
- CI/CD with GitHub Actions
- Cloud deployment with Google Cloud
- Secure secret management

---

# Repository Structure

```text
Hammerly/
|
├── hammerly-ui/
│   └── React + TypeScript frontend
│
├── hammerly-backend/
│   └── Hammerly Core Spring Boot service
│
├── hammerly-ai/
│   └── Hammerly AI Spring Boot service
│
├── hammerly-worker/
│   └── Kafka background worker
│
├── infrastructure/
│   ├── docker/
│   ├── kubernetes/
│   └── monitoring/
│
├── load-tests/
│   └── k6/
│
├── docs/
│   ├── images/
│   ├── architecture.md
│   ├── microservices.md
│   ├── ai-rag.md
│   └── performance.md
│
├── docker-compose.yml
│
└── README.md
```

---

## License

This project was developed for educational, portfolio, and software engineering demonstration purposes.
