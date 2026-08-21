# Hammerly

Hammerly is an auction application with a React/TypeScript frontend, a Spring Boot Core microservice, an internal Spring Boot AI microservice, and Supabase PostgreSQL persistence. Core remains the public backend and the source of truth for transactional business data; AI customer-support requests use Spring AI and OpenAI behind that boundary.

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
Hammerly AI ----> Spring AI ----> OpenAI
```

- `hammerly-ui`: React/TypeScript frontend deployed to GitHub Pages. It communicates only with Hammerly Core.
- `hammerly-backend`: Hammerly Core microservice. It owns authentication, users, profiles, auctions, bids, watchlists, payment methods, and PostgreSQL transactions.
- `hammerly-ai`: independent internal AI microservice. It owns the support prompt, OpenAI orchestration, provider streaming, and its internal HTTP contract. It has no Core database access.

| Component | Local port |
| --- | ---: |
| Frontend | `3000` |
| Hammerly Core | `5000` |
| Hammerly AI | `5001` |

The browser never calls Hammerly AI directly. Both the floating AI Support widget and FAQ's free-form Ask AI area call Core at `/api/ai/support/**`; Core validates the request and relays it to AI at `/internal/ai/**`. Static FAQ accordion answers stay local and do not incur an LLM request. See [Microservice architecture](docs/microservices.md) for ownership and streaming details.

## Prerequisites

- Java 21
- Node.js 20.19+ and npm
- A Supabase project and its Session Pooler JDBC URL
- An OpenAI API key for live AI answers (health endpoints and automated tests work without one)
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

Core runs at `http://localhost:5000`, health is `http://localhost:5000/health`, and Swagger UI is `http://localhost:5000/api-docs`. `HAMMERLY_AI_URL` defaults to `http://localhost:5001`; Core does not contact AI at startup and continues serving existing features when AI is unavailable. Optional timeout overrides are `HAMMERLY_AI_CONNECT_TIMEOUT` (default `2s`), `HAMMERLY_AI_READ_TIMEOUT` (default `45s`), and `HAMMERLY_AI_STREAM_TIMEOUT` (default `50s`).

## Start Hammerly AI

Set the provider key only in the AI service environment:

```powershell
cd hammerly-ai
$env:OPENAI_API_KEY='<your-key>'
.\mvnw.cmd spring-boot:run
```

AI runs at `http://localhost:5001`. Spring AI `1.1.7` uses its official OpenAI Java SDK-backed starter and defaults to `gpt-5-mini`. `OPENAI_MODEL`, `OPENAI_TIMEOUT`, `OPENAI_MAX_RETRIES` (default `0`), and `OPENAI_MAX_OUTPUT_TOKENS` are optional overrides. Do not put `OPENAI_API_KEY` in a frontend `.env` or any `VITE_*` variable.

Process health remains available without a provider key at `GET /health` and `GET /actuator/health`; `GET /internal/ai/status` reports `aiConfigured: false`, and chat requests return a controlled unavailable response rather than fake output. Internal chat endpoints are:

- `POST /internal/ai/chat` for a complete JSON answer.
- `POST /internal/ai/chat/stream` for genuine provider-originated `text/event-stream` chunks.

With both services running, `GET http://localhost:5000/internal/integration/ai-health` checks the Core-to-AI connection. The diagnostic route is disabled by the `prod` profile.

## Start the frontend

In a second terminal:

```powershell
cd hammerly-ui
npm install
npm run dev
```

The UI runs at `http://localhost:3000`; its Vite proxy sends `/api/*` requests to Core on port 5000. AI responses are read from the POST response stream and progressively update one assistant message.

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

cd ..\hammerly-ui
npm run type-check
npm run build
```

## Phase 2 boundary

Support answers currently combine the Hammerly system prompt, the model's general knowledge, and at most 20 recent client-provided user/assistant messages. React owns this temporary conversation state; Core and AI do not persist it. The message limit is 2,000 characters, each history item is limited to 4,000 characters, and invalid or unsupported history roles are rejected.

There is no Hammerly RAG knowledge base, Redis conversation state, Kafka processing, or AI conversation database in Phase 2. The prompt therefore requires cautious answers when precise Hammerly policy is unavailable. RAG is planned for Phase 3.

For Core production, set `SPRING_PROFILES_ACTIVE=prod`, store `SUPABASE_DB_URL` and `JWT_SECRET` as secrets, and leave demo seeding and the integration diagnostic disabled. A later deployment must set `HAMMERLY_AI_URL` to the private AI service URL and provide `OPENAI_API_KEY` only to the AI service. This task does not deploy any service. See [the backend README](hammerly-backend/README.md) for the existing Cloud Run Secret Manager setup.
