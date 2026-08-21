# Hammerly Spring Boot Backend

The backend is a Java 21 / Spring Boot 3 application. Controllers preserve the existing HTTP contract, services own business rules and response mapping, repositories isolate Spring JDBC `JdbcTemplate` SQL, and the existing stateless Spring Security/JWT flow remains unchanged. Persistence uses Supabase PostgreSQL through the JDBC driver and HikariCP.

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

Store the complete Session Pooler JDBC URL in Google Secret Manager, grant the Cloud Run service account `roles/secretmanager.secretAccessor`, and expose the secret as `SUPABASE_DB_URL`:

```powershell
gcloud secrets create hammerly-supabase-db-url --replication-policy=automatic
gcloud secrets versions add hammerly-supabase-db-url --data-file=-
gcloud secrets add-iam-policy-binding hammerly-supabase-db-url --member="serviceAccount:<CLOUD_RUN_SERVICE_ACCOUNT>" --role="roles/secretmanager.secretAccessor"
gcloud run services update <CLOUD_RUN_SERVICE> --region=<REGION> --update-secrets="SUPABASE_DB_URL=hammerly-supabase-db-url:latest" --update-env-vars="SPRING_PROFILES_ACTIVE=prod,HAMMERLY_SEED_ENABLED=false"
```

When `gcloud secrets versions add` waits for standard input, paste the JDBC URL and finish input without committing it to a file. Keep the existing `PORT`, `FRONTEND_URL`, and `JWT_SECRET` Cloud Run configuration. The pool defaults to at most five connections, so no SQLite-era single-instance restriction is needed.

## Package layout

- `config`: CORS, security, OpenAPI, and optional demo seed initialization
- `controller`: compatible HTTP routes
- `service`: validation, business behavior, and client response mapping
- `repository`: PostgreSQL `JdbcTemplate` SQL and row mapping
- `security`: JWT generation/validation and the stateless authentication filter
- `db/migration`: Flyway-owned PostgreSQL schema evolution
- `dto` / `model`: request and persistence types
- `exception`: consistent legacy-compatible API errors
