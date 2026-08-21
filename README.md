# Hammerly

Hammerly is an auction application with a React/TypeScript frontend, a Spring Boot Core microservice, an internal Spring Boot AI microservice foundation, and Supabase PostgreSQL persistence. The existing Core API remains the public backend and the source of truth for transactional business data.

## Architecture

```text
React
  |
  v
Hammerly Core
  |
  +---- PostgreSQL
  |
  v
Hammerly AI
```

- `hammerly-ui`: React/TypeScript frontend deployed to GitHub Pages. It communicates only with Hammerly Core.
- `hammerly-backend`: Hammerly Core microservice. It owns authentication, users, profiles, auctions, bids, watchlists, payment methods, and PostgreSQL transactions.
- `hammerly-ai`: independent Hammerly AI microservice foundation. LLM orchestration, RAG, semantic search, and other AI capabilities will be added incrementally; none are implemented yet.

| Component | Local port |
| --- | ---: |
| Frontend | `3000` |
| Hammerly Core | `5000` |
| Hammerly AI | `5001` |

The browser never calls Hammerly AI directly. Core will supply authentication, authorization, validation, user identity, rate limits, and business context before future AI requests. The AI service does not connect to the Core PostgreSQL database. See [Microservice architecture](docs/microservices.md) for service ownership and the planned evolution.

## Prerequisites

- Java 21
- Node.js 20.19+ and npm
- A Supabase project and its Session Pooler JDBC URL
- Docker Desktop when running backend PostgreSQL integration tests

## Start Hammerly Core

Copy the JDBC connection from the Supabase **Database > Connect** panel. Select the Session Pooler on port `5432`, use the JDBC form, and ensure `sslmode=require` is present. Set it only in the backend environment:

```powershell
cd hammerly-backend
$env:SUPABASE_DB_URL='jdbc:postgresql://<session-pooler-host>:5432/postgres?user=<user>&password=<url-encoded-password>&sslmode=require'
$env:JWT_SECRET='replace-with-a-long-random-secret'
$env:HAMMERLY_AI_URL='http://localhost:5001'
.\mvnw.cmd spring-boot:run
```

Flyway automatically creates and migrates the private `hammerly` PostgreSQL schema. With `HAMMERLY_SEED_ENABLED=true` (the local default), it also initializes the idempotent demo users and auctions; the sample password is `password123`.

Core runs at `http://localhost:5000`, health is `http://localhost:5000/health`, and Swagger UI is `http://localhost:5000/api-docs`. `HAMMERLY_AI_URL` defaults to `http://localhost:5001`; Core does not contact AI at startup and continues serving existing features when AI is unavailable.

## Start Hammerly AI

No database or AI provider credentials are required:

```powershell
cd hammerly-ai
.\mvnw.cmd spring-boot:run
```

AI runs at `http://localhost:5001`. Its foundation endpoints are `GET /health`, `GET /actuator/health`, and `GET /internal/ai/status`. With both services running locally, `GET http://localhost:5000/internal/integration/ai-health` verifies Core-to-AI communication. The Core diagnostic route is disabled by the `prod` profile.

## Start the frontend

In a second terminal:

```powershell
cd hammerly-ui
npm install
npm run dev
```

The UI runs at `http://localhost:3000`; its existing Vite proxy continues to send `/api/*` requests to Spring Boot on port 5000.

Copy `hammerly-ui/.env.example` to `hammerly-ui/.env.local` when a local override is needed. The default development value is `/api`, which uses the Vite proxy rather than hard-coding a production host.

## Production frontend deployment

The frontend deploys from `main` with `.github/workflows/deploy-frontend.yml`. GitHub Actions installs the locked npm dependencies, type-checks the project, builds `hammerly-ui/out`, uploads that directory as the Pages artifact, and deploys it to GitHub Pages. Generated `out` files are not committed.

The repository Actions variable `HAMMERLY_API_URL` supplies `VITE_API_URL` at build time. Its value must be the Cloud Run origin followed by `/api`; frontend API modules append endpoint paths such as `/auth/login` and `/auctions/get-top`.

The production custom domain is `hammerly.jqiwen.com`. `hammerly-ui/public/CNAME` declares it in the artifact, and the `404.html` fallback restores deep BrowserRouter routes after GitHub Pages handles a direct request.

## Run service tests and builds

The tests start an isolated PostgreSQL 16 container and never connect to Supabase:

```powershell
cd hammerly-backend
.\mvnw.cmd clean test
.\mvnw.cmd clean package

cd ..\hammerly-ai
.\mvnw.cmd clean test
.\mvnw.cmd clean package
```

For Core production, set `SPRING_PROFILES_ACTIVE=prod`, store `SUPABASE_DB_URL` and `JWT_SECRET` as secrets, and leave demo seeding and the integration diagnostic disabled. A future deployment can set `HAMMERLY_AI_URL` to the private AI service URL. The AI service is not deployed in this foundation phase. See [the backend README](hammerly-backend/README.md) for all Core environment variables and the existing Cloud Run Secret Manager setup.
