# Hammerly

Hammerly is a local auction application with a React/TypeScript frontend, a Spring Boot backend, and SQLite storage.

## Prerequisites

- Java 21 (a newer JDK that can compile for Java 21 also works)
- Node.js 20.19+ and npm

## Start the backend

Windows PowerShell:

```powershell
cd hammerly-backend
.\mvnw.cmd spring-boot:run
```

macOS/Linux:

```bash
cd hammerly-backend
./mvnw spring-boot:run
```

The API runs at `http://localhost:5000`, the health check is `http://localhost:5000/health`, and Swagger UI is `http://localhost:5000/api-docs`.

SQLite data is stored at `hammerly-backend/data/hammerly.db` by default. Schema creation and idempotent sample data initialization happen automatically. Sample account passwords are `password123`.

## Start the frontend

In a second terminal:

```powershell
cd hammerly-ui
npm install
npm run dev
```

The UI runs at `http://localhost:3000` and its existing Vite proxy sends `/api/*` requests to the backend on port 5000.

## Run backend tests

Windows:

```powershell
cd hammerly-backend
.\mvnw.cmd clean test
```

macOS/Linux:

```bash
cd hammerly-backend
./mvnw clean test
```

See [the backend README](hammerly-backend/README.md) for configuration and architecture details.
