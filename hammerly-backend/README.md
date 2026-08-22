# Hammerly Core Spring Boot Service

The `hammerly-backend` directory remains the deployable Core service directory for compatibility with existing tooling. Its Spring application identity is `hammerly-core`. Controllers preserve the existing HTTP contract, services own business rules and response mapping, repositories isolate Spring JDBC `JdbcTemplate` SQL, and the existing stateless Spring Security/JWT flow remains unchanged. Persistence uses Supabase PostgreSQL through the JDBC driver and HikariCP.

Hammerly tables live in the private PostgreSQL schema `hammerly`; the React application never connects to Supabase directly. Flyway applies the migrations in `src/main/resources/db/migration` automatically before the application starts.

## Supabase setup and local startup

1. Create or select a Supabase project.
2. Open **Database > Connect** and select the **Session Pooler** JDBC connection on port `5432`.
3. Ensure the URL begins with `jdbc:postgresql://` and contains `sslmode=require`.
4. Paste the complete JDBC URL into the `SUPABASE_DB_URL` environment variable. Do not commit it to `.env.example` or any source file.
5. Start the backend:

```powershell
cd hammerly-backend
$env:SUPABASE_DB_URL='jdbc:postgresql://<session-pooler-host>:5432/postgres?user=<user>&password=<url-encoded-password>&sslmode=require'
$env:JWT_SECRET='replace-with-a-long-random-secret'
.\mvnw.cmd spring-boot:run
```

On macOS/Linux, export the same variables and run `./mvnw spring-boot:run`. The API runs at `http://localhost:5000`, health is `http://localhost:5000/health`, Swagger UI is `http://localhost:5000/api-docs`, and OpenAPI JSON is `http://localhost:5000/v3/api-docs`.

No tables need to be created in the Supabase dashboard. Flyway creates the `hammerly` schema, all five application tables, constraints, and query indexes when the database is empty.

## Environment variables

| Variable | Default | Purpose |
| --- | --- | --- |
| `SUPABASE_DB_URL` | none; required | Supabase Session Pooler JDBC URL. SSL is also enforced by the datasource configuration. |
| `FRONTEND_URL` | `http://localhost:3000` | Allowed CORS origin. |
| `JWT_SECRET` | local-only placeholder | Existing HMAC JWT signing secret; always override in production. |
| `JWT_EXPIRATION_MS` | `604800000` | Token lifetime in milliseconds. |
| `SPRING_PROFILES_ACTIVE` | none | Use `prod` on Cloud Run. |
| `HAMMERLY_SEED_ENABLED` | `true` locally, `false` in `prod` | Enables idempotent demo users, auctions, and bids. |
| `HAMMERLY_DEBUG_ENDPOINT_ENABLED` | `true` locally; disabled by `prod` | Enables the local `GET /api/auth/` database debug route. |
| `HAMMERLY_AI_URL` | `http://localhost:5001` | Base URL of the internal Hammerly AI service. Core does not contact it during startup. |
| `HAMMERLY_AI_DIAGNOSTIC_ENABLED` | `true` locally; disabled by `prod` | Enables local `GET /internal/integration/ai-health` verification. |
| `HAMMERLY_AI_INTERNAL_TOKEN` | empty locally | Shared token Core adds to internal AI requests; required in production. |
| `HAMMERLY_DB_MAX_POOL_SIZE` | `5` | Hikari maximum pool size. |
| `HAMMERLY_DB_MIN_IDLE` | `0` | Hikari minimum idle connections. |

The sample accounts created when seeding is enabled are `seller1@hammerly.com`, `seller2@hammerly.com`, and `bidder1@hammerly.com`, each with password `password123`. Production disables seeding unless `HAMMERLY_SEED_ENABLED=true` is explicitly supplied.

The Java/API monetary representation remains `double` for compatibility, while JDBC writes are converted to `BigDecimal` and PostgreSQL stores auction and bid amounts as `NUMERIC(12,2)`.

## Tests and packaging

Integration tests use an isolated PostgreSQL 16 Testcontainer and never use Supabase credentials. Docker must be running for the database integration suite.

```powershell
.\mvnw.cmd clean test
.\mvnw.cmd package
```

## Cloud Run and Secret Manager

The root `deploy-core.yml` workflow builds the production container, publishes the immutable commit-tagged image to Artifact Registry, deploys Cloud Run, binds `SUPABASE_DB_URL`, `JWT_SECRET`, and `HAMMERLY_AI_INTERNAL_TOKEN` from Secret Manager, and checks `/health`. Follow the repository-wide one-time setup in [`../docs/deployment/gcp.md`](../docs/deployment/gcp.md). Do not put database or JWT values in GitHub variables or service YAML.

## Package layout

- `config`: CORS, security, OpenAPI, and optional demo seed initialization
- `client`: isolated HTTP clients for downstream internal services
- `controller`: compatible HTTP routes
- `service`: validation, business behavior, and client response mapping
- `repository`: PostgreSQL `JdbcTemplate` SQL and row mapping
- `security`: JWT generation/validation and the stateless authentication filter
- `db/migration`: Flyway-owned PostgreSQL schema evolution
- `dto` / `model`: request and persistence types
- `exception`: consistent legacy-compatible API errors
