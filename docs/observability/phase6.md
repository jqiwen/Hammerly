# Phase 6 — Prometheus and Grafana observability

## Architecture

```text
hammerly-backend :5000 ─┐
hammerly-ai      :5001 ─┼─ /actuator/prometheus ─> Prometheus :9090 ─> Grafana :3001
hammerly-worker  :5002 ─┘
```

The applications run on the host during this phase. Prometheus and Grafana run in Docker. The
Prometheus targets therefore use `host.docker.internal`; Compose adds the Linux `host-gateway`
mapping as well. The worker has no application HTTP connector. Port 5002 is its separate,
management-only Actuator connector, while its application remains a Kafka consumer.

Prometheus scrapes every five seconds. Grafana automatically provisions the Prometheus datasource
and the **Hammerly — System Overview** dashboard. No dashboard import or datasource setup is needed.

## Endpoints and access

| Component | Local URL |
| --- | --- |
| Prometheus | <http://localhost:9090> |
| Prometheus targets | <http://localhost:9090/targets> |
| Grafana | <http://localhost:3001> |
| Core health / metrics | <http://localhost:5000/actuator/health> / <http://localhost:5000/actuator/prometheus> |
| AI health / metrics | <http://localhost:5001/actuator/health> / <http://localhost:5001/actuator/prometheus> |
| Worker health / metrics | <http://localhost:5002/actuator/health> / <http://localhost:5002/actuator/prometheus> |

The local Grafana credentials default to `admin` / `admin`. Override them before first startup with
`GRAFANA_ADMIN_USER` and `GRAFANA_ADMIN_PASSWORD` when the defaults are not suitable.

## Metric definitions

Micrometer meter names use dots in Java and are normalized by the Prometheus registry. Timers are
exported in seconds. The important actual Prometheus names are:

| Prometheus name | Type and meaning | Labels |
| --- | --- | --- |
| `ai_requests_total` | Counter of accepted AI requests | none |
| `ai_request_duration_seconds_bucket` / `_count` / `_sum` / `_max` | Timer from accepted request through success, error, or SSE cancellation | `outcome=success|error|cancelled` |
| `llm_errors_total` | Provider attempt errors | `operation=chat|stream`, bounded `category` |
| `redis_cache_hits_total` | Real response-cache hits | none |
| `redis_cache_misses_total` | Real response-cache misses, including failed reads treated as misses | none |
| `active_conversations` | In-flight AI requests on this AI instance | none |
| `kafka_processing_duration_seconds_bucket` / `_count` / `_sum` / `_max` | Worker listener receipt through processing completion | bounded `event_type`, `outcome=success|error` |
| `rag_search_duration_seconds_bucket` / `_count` / `_sum` / `_max` | Reserved real RAG search timer | none |

`rag_search_duration_seconds_*` is registered only when future RAG code calls the prepared
instrumentation boundary. Phase 6 makes no such call and creates no fake observations, so the RAG
panel correctly shows **No data**.

Existing Phase 5 and Resilience4j metrics remain available, including:

- `hammerly_ai_provider_first_token_latency_seconds_bucket` / `_count` / `_sum` / `_max`
- `hammerly_ai_provider_active`
- `hammerly_ai_provider_retry_total`
- `hammerly_ai_provider_http_429_total`
- `hammerly_ai_provider_http_5xx_total`
- `hammerly_ai_provider_timeout_total`
- `hammerly_ai_bulkhead_rejected_total`
- `hammerly_ai_circuit_open_rejected_total`
- `hammerly_ai_circuit_transition_total`
- `resilience4j_circuitbreaker_state`
- worker processed, failed, retry, DLT, duplicate, analytics, and summary metrics
- standard `jvm_*`, `process_*`, `system_*`, and Kafka client metrics

No metric label contains a user ID, conversation ID, request ID, prompt, email, exception message,
or event ID. Worker event types are normalized to a fixed allow-list, with everything else reported
as `unknown`.

## Histograms and important PromQL

The AI request, first-token, and Kafka processing timers publish histogram buckets. RAG will do the
same once a real observation exists.

```promql
# AI P95
histogram_quantile(
  0.95,
  sum by (le) (rate(ai_request_duration_seconds_bucket[5m]))
)

# LLM first-token P99
histogram_quantile(
  0.99,
  sum by (le) (rate(hammerly_ai_provider_first_token_latency_seconds_bucket[5m]))
)

# Worker processing P50
histogram_quantile(
  0.50,
  sum by (le) (rate(kafka_processing_duration_seconds_bucket[5m]))
)

# Redis response-cache hit rate
sum(rate(redis_cache_hits_total[5m]))
/
clamp_min(
  sum(rate(redis_cache_hits_total[5m])) + sum(rate(redis_cache_misses_total[5m])),
  0.000000001
)

# AI success percentage
100 * (sum(rate(ai_request_duration_seconds_count{outcome="success"}[5m])) or vector(0))
/
clamp_min((sum(rate(ai_request_duration_seconds_count[5m])) or vector(0)), 0.000000001)
```

## Dashboard

The provisioned dashboard contains these sections:

- Traffic: request, success, and error rates; active conversations; active LLM calls
- Latency: AI and first-token P50/P95/P99
- Resilience: 429, 5xx, timeout, retry, and bulkhead rates; circuit-breaker state
- Redis: calculated cache hit rate, hits/sec, and misses/sec
- Kafka Worker: processing rate and P50/P95/P99, errors, retries, DLT, and available client lag
- JVM: heap, process CPU, live threads, and GC pause
- RAG: future search duration, with No data expected in Phase 6

## Startup and shutdown

Phase 7 includes Prometheus and Grafana in the unified Compose stack. Copy the root environment
template, configure the database and JWT values described in the repository README, then start all
local services. The default AI profile is the deterministic Phase 5 provider and makes no paid call.

```powershell
$env:HAMMERLY_REDIS_ENABLED = "true"
$env:HAMMERLY_KAFKA_ENABLED = "true"
docker compose up -d --build
docker compose ps
```

The root `.env.example` intentionally defaults both flags to `false` for demo-off safety. Phase 6
Redis and worker/Kafka panels require the explicit `true` overrides above; the metric names and full
Redis/Kafka implementations are unchanged.

Stop the stack without deleting retained data:

```powershell
docker compose down
```

Add `-v` only when Redis, Kafka, Prometheus, Grafana, and optional PostgreSQL data should
intentionally be deleted.

## Security behavior

Only `health` and `prometheus` are exposed in the default local profile; the general `metrics`,
`env`, `beans`, `configprops`, heap dump, and other sensitive Actuator endpoints are not exposed.
Health never includes component details.

Every production profile exposes only `health`. Prometheus is therefore not published on the current
public application listeners by default. A production collector must add an explicitly private scrape
path or management network before opting production metrics back in. Grafana's default local password
is development-only and must not be used for an internet-reachable deployment.

## Validation and known limitations

Phase 6 was smoke-validated with all three Prometheus targets UP and the existing deterministic Phase 5
provider. The run exercised real SSE completion, Redis hits/misses, controlled recovered 5xx/retry
attempts, Kafka publication/processing, histogram quantiles, Resilience4j state, JVM metrics, datasource
provisioning, and rendered Grafana panels. It did not rerun or modify the Phase 5 100/500/1,000-VU results.

Known limitations:

- RAG is not implemented, so its panel has no data by design.
- Production Prometheus/Grafana hosting and a private scrape network are intentionally not part of this phase.
- Worker consumer lag uses the Kafka client metric already exported by Micrometer; no separate Kafka exporter was added.
- Kafka retry/DLT and resilience rejection panels remain zero until those real events occur.
- Prometheus keeps seven days of local data in its named Docker volume.
