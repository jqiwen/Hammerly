# Hammerly Spring Boot Backend

The backend is a Java 21 / Spring Boot 3 application. Controllers preserve the original Express API contract, services own business rules and response mapping, repositories isolate `JdbcTemplate` SQL, and a stateless Spring Security filter validates compatible HMAC JWTs. SQLite remains the database.

## Local commands

```powershell
.\mvnw.cmd clean test
.\mvnw.cmd spring-boot:run
```

On macOS/Linux, use `./mvnw` instead. The Maven Wrapper downloads Maven 3.9.9 on first use.

Local URLs:

- API: `http://localhost:5000`
- Health: `http://localhost:5000/health`
- Swagger UI: `http://localhost:5000/api-docs`
- OpenAPI JSON: `http://localhost:5000/v3/api-docs`
- Expected frontend origin: `http://localhost:3000`

The default database is `data/hammerly.db`, relative to this directory. Existing Node-created databases at that path are opened and migrated in place. Existing bcrypt hashes remain valid.

## Environment variables

| Variable | Default | Purpose |
| --- | --- | --- |
| `FRONTEND_URL` | `http://localhost:3000` | Allowed CORS origin |
| `JWT_SECRET` | `your-secret-key-change-in-production` | HMAC secret compatible with existing tokens |
| `JWT_EXPIRATION_MS` | `604800000` | Token lifetime (7 days) |
| `HAMMERLY_DATABASE_PATH` | `data/hammerly.db` | SQLite file path |
| `HAMMERLY_SEED_ENABLED` | `true` | Initialize idempotent local sample data |
| `HAMMERLY_DEBUG_ENDPOINT_ENABLED` | `true` | Enable `GET /api/auth/` database debug route |

The `prod` Spring profile disables the debug database endpoint. Never use the documented local JWT default in a deployed environment.

## Sample users

- `seller1@hammerly.com` / `password123`
- `seller2@hammerly.com` / `password123`
- `bidder1@hammerly.com` / `password123`

## Package layout

- `config`: SQLite, CORS, security, OpenAPI, schema, and seed initialization
- `controller`: compatible HTTP routes
- `service`: validation, business behavior, and client response mapping
- `repository`: SQLite SQL and row mapping
- `security`: JWT generation/validation and the stateless authentication filter
- `dto` / `model`: request and persistence types
- `exception`: consistent legacy-compatible API errors
