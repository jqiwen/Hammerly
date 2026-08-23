# Phase 5 resilience and concurrency configuration

Phase 5 keeps the production OpenAI integration in Spring AI and applies Resilience4j around the
actual provider call. The `loadtest` Spring profile replaces only the model transport with a
deterministic simulator; it still crosses Core, Hammerly AI, Redis state, the resilience chain,
and the SSE controllers.

## Provider resilience defaults

| Environment variable | Default | Meaning |
| --- | ---: | --- |
| `HAMMERLY_AI_LLM_CONNECT_TIMEOUT` | `3s` | Provider TCP/TLS connection timeout |
| `HAMMERLY_AI_LLM_REQUEST_TIMEOUT` | `30s` | Synchronous provider-call timeout |
| `HAMMERLY_AI_LLM_FIRST_TOKEN_TIMEOUT` | `12s` | Maximum wait for the first streamed token |
| `HAMMERLY_AI_LLM_IDLE_TIMEOUT` | `15s` | Maximum silence between streamed tokens |
| `HAMMERLY_AI_LLM_MAX_ATTEMPTS` | `3` | Total attempts, including the first call |
| `HAMMERLY_AI_LLM_RETRY_INITIAL_BACKOFF` | `250ms` | First retry delay |
| `HAMMERLY_AI_LLM_RETRY_MULTIPLIER` | `2.0` | Exponential backoff multiplier |
| `HAMMERLY_AI_LLM_RETRY_JITTER` | `0.25` | Random backoff variation |
| `HAMMERLY_AI_LLM_MAX_CONCURRENT_CALLS` | `32` | Production provider bulkhead permits |
| `HAMMERLY_AI_LLM_MAX_WAIT` | `0ms` | Bulkhead wait; zero means fail fast |
| `HAMMERLY_AI_LLM_CB_WINDOW_SIZE` | `20` | Circuit-breaker sliding window |
| `HAMMERLY_AI_LLM_CB_MINIMUM_CALLS` | `10` | Calls required before evaluating failure rate |
| `HAMMERLY_AI_LLM_CB_FAILURE_RATE` | `50` | Failure percentage that opens the circuit |
| `HAMMERLY_AI_LLM_CB_SLOW_CALL_RATE` | `80` | Slow-call percentage that opens the circuit |
| `HAMMERLY_AI_LLM_CB_SLOW_CALL_DURATION` | `10s` | Duration classified as a slow call |
| `HAMMERLY_AI_LLM_CB_OPEN_WAIT` | `20s` | Time OPEN before HALF_OPEN probes |
| `HAMMERLY_AI_LLM_CB_HALF_OPEN_CALLS` | `3` | Permitted HALF_OPEN probes |

The order for synchronous calls is bulkhead → retry → circuit breaker → per-attempt time limiter.
Streaming calls use bulkhead → retry → circuit breaker around subscription, with first-token and
idle timeouts implemented in Reactor. A streaming call may retry only before its first token;
replaying after output has reached a client would duplicate content and is deliberately forbidden.
Provider 429 responses honor a valid `Retry-After` delay. Retryable failures are 429, provider 5xx,
connection failures, and provider timeouts. Authentication, quota, invalid-request, and other
non-retryable 4xx failures fail immediately.

## HTTP and streaming capacity defaults

| Service | Environment variable | Default |
| --- | --- | ---: |
| Core | `HAMMERLY_HTTP_MAX_CONNECTIONS` | `2000` |
| Core | `HAMMERLY_HTTP_ACCEPT_COUNT` | `1000` |
| Core | `HAMMERLY_AI_CONNECTION_POOL_MAX_TOTAL` | `1100` |
| Core | `HAMMERLY_AI_CONNECTION_POOL_MAX_PER_ROUTE` | `1100` |
| Core | `HAMMERLY_AI_STREAM_MAX_CONCURRENT` | `1100` |
| Core | `HAMMERLY_AI_CONNECT_TIMEOUT` | `2s` |
| Core | `HAMMERLY_AI_READ_TIMEOUT` | `45s` |
| Core | `HAMMERLY_AI_STREAM_TIMEOUT` | `50s` |
| AI | `HAMMERLY_AI_HTTP_MAX_CONNECTIONS` | `2000` |
| AI | `HAMMERLY_AI_HTTP_ACCEPT_COUNT` | `1000` |
| AI | `HAMMERLY_AI_STREAM_MAX_CONCURRENT` | `1100` |
| AI | `HAMMERLY_AI_STREAM_TIMEOUT` | `5m` |

Both Java services enable Java 21 virtual threads. Their MVC async executors are explicitly bounded;
the Core-to-AI Apache HTTP client also has bounded total and per-route connection pools. The default
provider bulkhead remains much smaller than the HTTP limits so paid-provider capacity is protected.
The local 1,000-VU benchmark explicitly overrode it to `512`; that is benchmark evidence, not a new
production quota recommendation.

## Deterministic load-test provider

Activate only on an isolated target with `SPRING_PROFILES_ACTIVE=loadtest`. The profile disables
Kafka and live model auto-configuration, raises the per-user test rate limit, and accepts:

| Environment variable | Default | Meaning |
| --- | ---: | --- |
| `MOCK_LLM_FIRST_TOKEN_DELAY_MS` | `100` | First-token delay in milliseconds |
| `MOCK_LLM_TOKEN_INTERVAL_MS` | `20` | Delay between later tokens |
| `MOCK_LLM_TOKEN_COUNT` | `8` | Tokens in a complete stream |
| `MOCK_LLM_TIMEOUT_DELAY_MS` | `30s` | Delay used by injected timeout cases |
| `MOCK_LLM_429_RATE` | `0.0` | Deterministic simulated rate-limit rate |
| `MOCK_LLM_5XX_RATE` | `0.0` | Deterministic simulated provider-5xx rate |
| `MOCK_LLM_TIMEOUT_RATE` | `0.0` | Deterministic simulated timeout rate |
| `MOCK_LLM_CONNECTION_FAILURE_RATE` | `0.0` | Deterministic connection-failure rate |
| `MOCK_LLM_AFTER_TOKEN_FAILURE_RATE` | `0.0` | Failure rate after the first token |

The checked-in full workload defaults to 30 seconds of warm-up, 15-second ramps, two-minute holds at
100, 500, and 1,000 VUs, a 30-second cooldown, and 10 seconds of think time with ±25% jitter. The
runner refuses a full test unless `PROVIDER_MODE=loadtest`; a live-provider run requires the explicit
`ALLOW_LIVE_PROVIDER_LOAD_TEST=true` override because it may consume quota and incur cost.

## Metrics

Hammerly AI exposes Spring Boot Actuator health and metrics only; Phase 5 does not add Prometheus or
Grafana. Relevant names include request/provider counts and latencies, provider first-token latency,
active and maximum active AI/provider calls, retries, 429/5xx/timeout categories, bulkhead rejection,
circuit state transitions, Redis cache/state errors, and Kafka publish success/failure. The exact
available meter can be listed at `/actuator/metrics` and queried at `/actuator/metrics/{name}`.
