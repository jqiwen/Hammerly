# Hammerly Core Spring Boot Service

The `hammerly-backend` directory remains the deployable Core service directory for compatibility with existing tooling. Its Spring application identity is `hammerly-core`. Controllers preserve the existing HTTP contract, services own business rules and response mapping, repositories isolate Spring JDBC `JdbcTemplate` SQL, and authentication remains stateless Spring Security with BCrypt and signed JWTs. Persistence uses Supabase PostgreSQL through the JDBC driver and HikariCP.

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
| `HAMMERLY_AUTH_JWT_TTL` | `45m` | Signed access-token lifetime as a Spring duration (`45m`, `1h`, and so on). |
| `HAMMERLY_AUTH_RATE_LIMIT_ENABLED` | `true` | Enables login and registration IP throttling. |
| `HAMMERLY_AUTH_RATE_LIMIT_REDIS_ENABLED` | `false` | Uses Redis for distributed auth limits; safely falls back to bounded process-local windows if unavailable. |
| `HAMMERLY_AUTH_TRUST_FORWARDED_FOR` | `false` | Trusts the first `X-Forwarded-For` address. Enable only behind a trusted proxy such as Cloud Run. |
| `HAMMERLY_AUTH_LOGIN_LIMIT` / `HAMMERLY_AUTH_LOGIN_WINDOW` | `10` / `1m` | Login request limit per client IP and fixed window. |
| `HAMMERLY_AUTH_REGISTER_LIMIT` / `HAMMERLY_AUTH_REGISTER_WINDOW` | `5` / `10m` | Registration request limit per client IP and fixed window. |
| `HAMMERLY_AUTH_RATE_LIMIT_LOCAL_MAX_KEYS` | `10000` | Bounds process-local client windows; overflow traffic shares a fallback bucket. |
| `SPRING_PROFILES_ACTIVE` | none | Use `prod` on Cloud Run. |
| `HAMMERLY_SEED_ENABLED` | `true` locally, `false` in `prod` | Enables idempotent demo users, auctions, and bids. |
| `HAMMERLY_DEBUG_ENDPOINT_ENABLED` | `true` locally; disabled by `prod` | Enables the local `GET /api/auth/` database debug route. |
| `HAMMERLY_AI_URL` | `http://localhost:5001` | Base URL of the internal Hammerly AI service. Core does not contact it during startup. |
| `HAMMERLY_AI_DIAGNOSTIC_ENABLED` | `true` locally; disabled by `prod` | Enables local `GET /internal/integration/ai-health` verification. |
| `HAMMERLY_AI_INTERNAL_TOKEN` | empty locally | Shared token Core adds to internal AI requests; required in production. |
| `HAMMERLY_DB_MAX_POOL_SIZE` | `5` | Hikari maximum pool size. |
| `HAMMERLY_DB_MIN_IDLE` | `0` | Hikari minimum idle connections. Use `1` with a warm portfolio/demo Core instance to avoid first-connection latency. |

The sample accounts created when seeding is enabled are `seller1@hammerly.com`, `seller2@hammerly.com`, and `bidder1@hammerly.com`, each with password `password123`. Production disables seeding unless `HAMMERLY_SEED_ENABLED=true` is explicitly supplied.

## Authentication behavior

Registration trims names and phone values and normalizes email identity with `trim().toLowerCase(Locale.ROOT)` before lookup, persistence, and JWT creation. Flyway also normalizes existing rows and installs a unique index on `lower(btrim(email))`, so case variants cannot create separate accounts even if an application check races. Passwords are never normalized and are stored only as BCrypt strength-10 hashes.

Invalid auth DTOs return HTTP 400 without echoing submitted values:

```json
{"success":false,"error":"VALIDATION_ERROR","message":"Invalid request","fields":{"email":"Email must be valid"}}
```

Unknown-email and wrong-password login failures intentionally share `Invalid email or password`. Protected routes require an unexpired token with the expected HMAC signature, issuer, audience, user ID, and email claims. Browser logout clears local state first and sends the bearer token only as a best-effort acknowledgement; there is no server-side token denylist, so an issued token remains cryptographically valid until its configured expiry. Shorter expiry or a future revocation/refresh-token design is required for stronger forced logout semantics.

Auth throttling counts login and registration requests by a SHA-256-derived client-address key and emits only fixed-cardinality metrics (`operation` and `outcome`), never email addresses or raw IPs. Enable Redis in multi-instance deployments for a shared limit. The bounded local fallback preserves protection during Redis outages but is per process.

## Explicit 100-auction demo seed

The production-safe catalog seed is not connected to application startup. It
creates four reserved `@hammerly.example` sellers and exactly 100 deterministic
auctions (70 active, 15 scheduled using the schema's `active` status with a future
`start_time`, and 15 ended). It never deletes rows and refuses to run if a reserved
demo email belongs to an unexpected user. Existing catalog rows are refreshed only
when both their deterministic title and reserved seller match.

With `SUPABASE_DB_URL` configured, inspect the target using aggregate counts only,
then intentionally seed it:

```powershell
..\scripts\seed-demo-auctions.ps1 -CheckOnly
..\scripts\seed-demo-auctions.ps1
```

The script runs `scripts/seed-demo-auctions.sql` through a temporary PostgreSQL 17
Docker client. Run it again at any time to verify idempotency; the result remains
100 demo auctions. Application startup seeding remains independently controlled by
`HAMMERLY_SEED_ENABLED` and remains `false` in production.

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
