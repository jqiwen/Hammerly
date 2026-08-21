# Hammerly

> An AI-powered online auction platform built with Spring Boot microservices, RAG, Kafka, Redis, Kubernetes, and cloud-native observability.

**Live Demo:** https://hammerly.jqiwen.com

---

## Overview

Hammerly is a full-stack online auction platform designed around a scalable microservice architecture.

The platform supports core marketplace workflows including user authentication, auction creation, real-time bidding, watchlists, user profiles, and account management.

Hammerly also includes an AI-powered customer support platform built as an independent Spring Boot microservice. The AI service combines large language models with Retrieval-Augmented Generation (RAG) to answer Hammerly-specific questions using platform documentation and knowledge-base content.

The system is designed to explore production-oriented backend engineering concepts including:

- Microservice architecture
- Generative AI integration
- Retrieval-Augmented Generation
- Distributed caching
- Event-driven processing
- High-concurrency request handling
- Rate limiting
- Fault isolation
- Observability
- Load testing
- Container orchestration
- Horizontal autoscaling

---

## Features

### Online Auction Platform

- User registration and JWT-based authentication
- Create and manage auction listings
- Browse active auctions
- Place and track bids
- Watchlist management
- User profile management
- Secure backend authorization
- PostgreSQL persistence
- Database migration management with Flyway

### AI Customer Support

- Floating AI support assistant integrated into the Hammerly UI
- AI-powered FAQ page
- Multi-turn conversational support
- Streaming AI responses
- Hammerly-specific system prompts
- Retrieval-Augmented Generation
- Knowledge-base document retrieval
- Semantic search using vector embeddings
- Source-grounded responses
- Graceful fallback when the AI provider is unavailable

### High-Concurrency Architecture

- Independent Core and AI microservices
- Redis-backed distributed caching
- Distributed rate limiting
- Conversation-state caching
- Kafka-based asynchronous event processing
- Independent Kafka consumers for background workloads
- LLM timeout and failure isolation
- Circuit breaker and bulkhead protection
- Horizontally scalable stateless services

### Observability & Performance

- Spring Boot Actuator health endpoints
- Micrometer application metrics
- Prometheus monitoring
- Grafana dashboards
- JVM, HTTP, AI, cache, and Kafka metrics
- k6 load testing
- P50 / P95 / P99 latency analysis
- Throughput and error-rate monitoring
- Kubernetes horizontal autoscaling

---

## System Architecture

```text
                           ┌──────────────────────────────┐
                           │      Hammerly Frontend       │
                           │   React + TypeScript +       │
                           │          Zustand             │
                           └──────────────┬───────────────┘
                                          │
                                          │ HTTPS / SSE
                                          ▼
                           ┌──────────────────────────────┐
                           │    Hammerly Core Service     │
                           │        Spring Boot           │
                           │                              │
                           │ Auth / Users / Auctions      │
                           │ Bidding / Watchlists         │
                           │ Profiles / API Gateway       │
                           └───────┬─────────┬────────────┘
                                   │         │
                                   │         │ REST
                                   │         ▼
                                   │  ┌──────────────────────────┐
                                   │  │   Hammerly AI Service    │
                                   │  │       Spring Boot        │
                                   │  │       Spring AI          │
                                   │  │                          │
                                   │  │ LLM / RAG / Embeddings   │
                                   │  │ Semantic Search          │
                                   │  └──────┬──────────┬────────┘
                                   │         │          │
                                   ▼         ▼          ▼
                         ┌──────────────┐  Redis      LLM API
                         │ PostgreSQL   │
                         │ + pgvector   │
                         │  Supabase    │
                         └──────────────┘

                                   │
                                   ▼
                             Apache Kafka
                                   │
                                   ▼
                         ┌─────────────────────┐
                         │   Async Workers     │
                         │    Spring Boot      │
                         │                     │
                         │ Analytics           │
                         │ Summarization       │
                         │ Embedding Jobs      │
                         └─────────────────────┘
