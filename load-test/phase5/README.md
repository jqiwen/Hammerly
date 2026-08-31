# Phase 5 SSE load test

This suite drives the complete synchronous route:

```text
k6 → hammerly-backend → hammerly-ai → loadtest provider → SSE response
```

Redis conversation/cache logic and the production Resilience4j executor remain in the path. Only
the paid model transport is replaced. When a bearer token is supplied, k6 includes it; otherwise the
request exercises the existing guest chat behavior. Kafka is disabled by the load-test profile
because it is an asynchronous side effect and is not part of streaming latency.

## Safety

The full test refuses to start unless `PROVIDER_MODE=loadtest`. The only bypass is the explicit
`ALLOW_LIVE_PROVIDER_LOAD_TEST=true` environment variable, which can consume provider quota and
incur cost. Do not use that bypass for the 100/500/1,000-VU run.

The deterministic provider is activated only with `SPRING_PROFILES_ACTIVE=loadtest`. Do not deploy
that profile to production.

## Toolchain and metrics

The checked-in Dockerfile pins k6 `1.2.2` and `xk6-sse` `0.1.12`, because k6 core does not expose
the SSE event callbacks needed to distinguish connection, first event, completion, and safe SSE
error events. The scripts use a compatible host `k6` when present and otherwise build/run the pinned
Docker image.

Client metrics include:

- `hammerly_sse_connection_latency`
- `hammerly_sse_first_event_latency`
- `hammerly_sse_metadata_events` when `EXPECT_RAG_SOURCES=true`
- `hammerly_sse_stream_duration`, tagged with `outcome`
- successful/failed/total streams per hold stage
- controlled overload, transport, HTTP-status, missing-first-event, incomplete, and SSE-error counts

The generated JSON includes p50 (`med`), p95, p99, max, counts, and rates. Stage throughput in the
performance report is calculated as successful hold-stage streams divided by the two-minute hold;
k6's tagged counter `rate` divides by the entire scenario duration and is not the hold RPS.

Server-side evidence comes from Hammerly AI `/actuator/metrics`: provider and request count/latency,
first-token latency, active/max-active calls, cache hits/misses, retries and error categories,
bulkhead rejections, circuit transitions, Redis errors, and Kafka publishing metrics.

## Start an isolated target

From the repository root, first start Redis:

```powershell
docker compose up -d redis
```

Start Hammerly AI in one terminal. The example below reproduces the final benchmark's roughly
7.1-second stream and raises only the benchmark provider bulkhead; production defaults to 32.

```powershell
cd hammerly-ai
$env:SPRING_PROFILES_ACTIVE='loadtest'
$env:HAMMERLY_AI_LLM_MAX_CONCURRENT_CALLS='512'
$env:MOCK_LLM_FIRST_TOKEN_DELAY_MS='100'
$env:MOCK_LLM_TOKEN_INTERVAL_MS='1000'
$env:MOCK_LLM_TOKEN_COUNT='8'
$env:HAMMERLY_REDIS_ENABLED='true'
$env:HAMMERLY_KAFKA_ENABLED='false'
.\mvnw.cmd spring-boot:run
```

Start Core in another terminal with a test database connection and its existing development auth
configuration:

```powershell
cd hammerly-backend
$env:SUPABASE_DB_URL='your-test-jdbc-url'
$env:HAMMERLY_AI_URL='http://localhost:5001'
.\mvnw.cmd spring-boot:run
```

## Run

The smoke schedule ramps to 10 VUs. The full default schedule has a 30-second warm-up, 15-second
ramps, two-minute holds at 100, 500, and 1,000 VUs, and a 30-second cooldown. Think time defaults to
10 seconds with ±25% jitter.

```powershell
$env:HAMMERLY_LOAD_TEST_BASE_URL='http://localhost:5000'
$env:PROVIDER_MODE='loadtest'
$env:PHASE5_RUN_ID='a001'
.\scripts\load-test\run-phase5.ps1 -Mode smoke
.\scripts\load-test\run-phase5.ps1 -Mode full
```

On Linux/macOS:

```bash
export HAMMERLY_LOAD_TEST_BASE_URL=http://localhost:5000
export PROVIDER_MODE=loadtest
export PHASE5_RUN_ID=a001
./scripts/load-test/run-phase5.sh smoke
./scripts/load-test/run-phase5.sh full
```

`PHASE5_RUN_ID` must be a distinct four-character hexadecimal shard for consecutive runs; this
prevents a previous response-cache entry from turning a cold-cache benchmark into a cache benchmark.
`HAMMERLY_LOAD_TEST_TOKEN` supplies an optional bearer token.

Durations can be shortened with `SMOKE_*` and `PHASE5_*` variables from `stages.js`.
`PHASE5_THINK_TIME` accepts `ms`, `s`, or `m`. Recorded 100/500/1,000 results must use the default
two-minute holds and disclose every override.

## Local generator caveat

On Docker Desktop for Windows, very short high-rate SSE tests can exhaust host/Docker NAT ephemeral
ports and accumulate `TIME_WAIT` sockets. That appears as status `0` before a request reaches Core;
it is a generator/network-path limit, not an application rejection. Use realistic stream durations
and think time, give ports time to drain between runs, assign a new `PHASE5_RUN_ID`, and confirm the
server request count before attributing such failures to Hammerly. For production-grade capacity
claims, use a distributed generator outside the application host.

Retained raw results and the full methodology are documented in
[`docs/performance/phase5-results.md`](../../docs/performance/phase5-results.md).
