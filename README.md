<p align="center">
  <img src="docs/image/hammerly-banner.png" alt="Hammerly Banner" width="100%" />
</p>

# Hammerly — Auction Platform with AI Support

Hammerly is a full-stack auction platform built with React and Spring Boot, featuring real-time bidding, authentication, AI-powered customer support, distributed background processing, caching, cloud deployment, and production-style observability.

🌐 **Live Demo:** [hammerly.jqiwen.com](https://hammerly.jqiwen.com)

---

## Overview

Hammerly combines a traditional marketplace application with an independent AI support system and distributed backend architecture.

Users can browse auctions, place bids, manage watchlists, and interact with an AI support assistant that uses RAG to retrieve relevant knowledge and streams responses back to the browser through Server-Sent Events (SSE).

The system is designed around scalability, resilience, and observability using Redis, Kafka, Docker, Kubernetes, GCP, Prometheus, Grafana, and k6.

---

## Architecture

![Hammerly Architecture](docs/images/hammerly-architecture.png)

```text
Users
  ↓
React + TypeScript
  ↓
Spring Boot Core / Auth / API
  ├── PostgreSQL
  ├── Redis
  └── Hammerly AI
        ├── Redis AI State & Cache
        ├── RAG + pgvector
        ├── LLM Provider
        └── Kafka
              ↓
          Async Worker
              ↓
       Knowledge Processing
