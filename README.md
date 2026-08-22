<p align="center">
  <img src="docs/image/hammerly-banner.png" alt="Hammerly Banner" width="100%" />
</p>

# Hammerly — AI-Powered Auction Platform

A scalable online auction platform built with React, Spring Boot microservices, Spring AI, RAG, Kafka, Redis, PostgreSQL, Docker, Kubernetes, and Google Cloud.

Hammerly combines a full-featured bidding marketplace with an AI-powered customer support platform designed around microservice isolation, asynchronous processing, distributed caching, observability, and high-concurrency performance.


### 🌐 [View Hammerly →]([https://jqiwen.com](https://hammerly.jqiwen.com))

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

