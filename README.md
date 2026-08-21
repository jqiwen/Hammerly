# Hammerly

Hammerly is an auction application with a React/TypeScript frontend, a Spring Boot backend, and Supabase PostgreSQL persistence.

```text
Browser -> React / Vite -> Spring Boot REST API -> JdbcTemplate -> Supabase PostgreSQL
```

The frontend continues to use the existing Spring Boot API and JWT authentication. It does not receive database credentials and does not connect to Supabase directly.

## Prerequisites

- Java 21
- Node.js 20.19+ and npm
- A Supabase project and its Session Pooler JDBC URL
- Docker Desktop when running backend PostgreSQL integration tests

## Start the backend

Copy the JDBC connection from the Supabase **Database > Connect** panel. Select the Session Pooler on port `5432`, use the JDBC form, and ensure `sslmode=require` is present. Set it only in the backend environment:

```powershell
cd hammerly-backend
$env:SUPABASE_DB_URL='jdbc:postgresql://<session-pooler-host>:5432/postgres?user=<user>&password=<url-encoded-password>&sslmode=require'
$env:JWT_SECRET='replace-with-a-long-random-secret'
.\mvnw.cmd spring-boot:run
```

Flyway automatically creates and migrates the private `hammerly` PostgreSQL schema. With `HAMMERLY_SEED_ENABLED=true` (the local default), it also initializes the idempotent demo users and auctions; the sample password is `password123`.

The API runs at `http://localhost:5000`, health is `http://localhost:5000/health`, and Swagger UI is `http://localhost:5000/api-docs`.

## Start the frontend

In a second terminal:

```powershell
cd hammerly-ui
npm install
npm run dev
```

The UI runs at `http://localhost:3000`; its existing Vite proxy continues to send `/api/*` requests to Spring Boot on port 5000.

## Run backend tests and build

The tests start an isolated PostgreSQL 16 container and never connect to Supabase:

```powershell
cd hammerly-backend
.\mvnw.cmd clean test
.\mvnw.cmd package
```

For production, set `SPRING_PROFILES_ACTIVE=prod`, store `SUPABASE_DB_URL` and `JWT_SECRET` as secrets, and leave demo seeding disabled. See [the backend README](hammerly-backend/README.md) for all environment variables and the exact Cloud Run Secret Manager change.
