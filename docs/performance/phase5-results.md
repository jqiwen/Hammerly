# Phase 5 high-concurrency and resilience results

## Outcome

Hammerly's local deterministic-provider benchmark completed the full 100/500/1,000-VU schedule
with 13,547 successful SSE streams, zero failed or interrupted iterations, and no retry, bulkhead,
or circuit event. The highest validated local level is therefore 1,000 concurrent virtual users
for this workload. That is a workstation comparison, not a production OpenAI capacity guarantee.

Implementation work is currently uncommitted on top of source commit
`bd6b45fc7fe437de596312876be91effe9e73da6`; no final commit SHA is claimed.

## Architecture and resilience behavior

The externally visible path remains synchronous:

```text
browser/k6 → Core public chat endpoint → pooled Core HTTP client
  → Hammerly AI chat/state service → Resilience4j → Spring AI/OpenAI SDK
  → chunk/done SSE events back through Core
```

Kafka remains a non-blocking side effect after a successful turn and is not part of stream
completion. Redis conversation/cache/rate-limit logic remains in the real request path and keeps its
existing graceful-degradation behavior.

Production uses the existing Spring AI/OpenAI SDK model client. SDK retries are disabled so one
layer owns retry accounting. Resilience4j registries create a named TimeLimiter, Retry,
CircuitBreaker, and Bulkhead around the actual model subscription/call. Synchronous attempts have a
per-attempt deadline. Streaming has separate first-token and between-token idle deadlines and no
total wall-clock cap on a healthy long stream.

Retry is bounded to three total attempts with exponential backoff and jitter. A valid provider
`Retry-After` controls 429 delay. Only 429, 5xx, connection, and timeout failures are retryable.
Authentication, quota, invalid input, and other permanent 4xx errors fail immediately. A stream may
retry only before its first token; after a token is emitted, the original failure becomes one safe
SSE error and is never replayed. Bulkhead and open-circuit rejection also map to stable, non-provider
messages without leaking URLs, keys, or raw bodies.

See [phase5-configuration.md](phase5-configuration.md) for every default and override.

## Test environment and workload

- Date: 2026-08-23 EDT
- Host: Windows 11 `10.0.26200`, Intel Core i9-13900HX, 32 logical processors,
  16,907,100,160 bytes host-visible RAM
- Java: host `22.0.1`, project release 21
- Docker Desktop: `27.5.1`, 32 CPUs and 8,185,286,656 bytes Docker-visible memory
- k6: pinned `1.2.2` plus `xk6-sse` `0.1.12`
- Redis: `redis:7-alpine`; Kafka disabled for timing; Core used the existing external test database
- Provider simulator: 100 ms first token, eight tokens, 1,000 ms token interval
- Phase 5 benchmark override: provider bulkhead 512; production default remains 32
- Schedule: 30-second warm-up, 15-second ramps, 120-second holds at 100/500/1,000,
  30-second cooldown, 10-second think time with ±25% jitter
- Cache: cold/unique conversation IDs through a unique run shard

The target used fresh local ports after preliminary runs. k6, Redis, Core, and AI shared the same
physical workstation. The provider was deterministic and free; no live OpenAI load test was run.

## Final benchmark

### Throughput and successful stream duration

| Concurrency (VUs) | Successful streams | Total streams | Success RPS | Error rate | Stream p50 (ms) | Stream p95 (ms) | Stream p99 (ms) |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 100 | 697 | 697 | 5.81 | 0% | 7,182 | 7,209 | 7,218 |
| 500 | 3,464 | 3,464 | 28.87 | 0% | 7,174 | 7,195 | 7,204.37 |
| 1,000 | 6,953 | 6,953 | 57.94 | 0% | 7,172 | 7,193 | 7,202 |

### Connection and first-event latency

| Concurrency (VUs) | Connection p50 (ms) | Connection p95 (ms) | Connection p99 (ms) | First event p50 (ms) | First event p95 (ms) | First event p99 (ms) |
| --- | --- | --- | --- | --- | --- | --- |
| 100 | 125 | 140 | 147 | 125 | 140 | 147 |
| 500 | 120 | 130 | 137 | 120 | 130 | 137.37 |
| 1,000 | 118 | 126 | 133 | 118 | 127 | 133 |

All holds passed. The complete scenario had 13,547 successful streams and zero failures or
interruptions. Success RPS is successful hold-stage count divided by 120 seconds; the tagged k6
counter's generated rate spans the whole scenario and is intentionally not reported as hold RPS.

Server-side metrics corroborated the generator:

- 13,547 provider requests and successes; zero provider failures or retries
- zero 429, 5xx, timeout, network, bulkhead rejection, circuit-open rejection, or circuit transition
- maximum active provider calls 474 and maximum active AI requests 474, below the benchmark bound 512
- provider first-token average 107.31 ms and maximum 119.77 ms
- provider duration average 7.159 seconds and maximum 7.2025 seconds
- 13,547 cache misses, zero hits, and zero Redis errors
- Kafka disabled for benchmark isolation

| Process | Peak working set | Final working set | CPU time | Final threads |
| --- | ---: | ---: | ---: | ---: |
| Core | 561,422,336 bytes (535.4 MiB) | 398,024,704 bytes | 131.20 s | 62 |
| AI | 499,888,128 bytes (476.7 MiB) | 383,713,280 bytes | 163.64 s | 94 |

Raw result: [`phase5-final-100-500-1000.json`](../../load-test/phase5/results/phase5-final-100-500-1000.json).
Machine-readable exports: [`phase5-final-summary.csv`](../../load-test/phase5/results/phase5-final-summary.csv)
and [`phase5-before-after.csv`](../../load-test/phase5/results/phase5-before-after.csv).

## Before and after

The primary baseline used the same provider timing, schedule, think time, cache policy, and local
route. Full baseline details are in [phase5-baseline.md](phase5-baseline.md).

| Concurrency (VUs) | Baseline success RPS | Phase 5 success RPS | Baseline error rate | Phase 5 error rate | Baseline first-event p95 (ms) | Phase 5 first-event p95 (ms) |
| --- | --- | --- | --- | --- | --- | --- |
| 100 | 2.75 | 5.81 | 18.32% | 0% | 25,821.75 | 140 |
| 500 | 2.82 | 28.87 | 91.97% | 0% | 26,964.45 | 130 |
| 1,000 | 2.83 | 57.94 | 95.28% | 0% | 27,065.65 | 127 |

The original fixed 20-thread/50-queue Core streaming executor was the dominant baseline bottleneck.
Phase 5 uses bounded virtual-thread streaming executors, raises Tomcat connection/accept bounds, and
adds a bounded 1,100-connection pooled Core-to-AI client. The provider bulkhead remains the explicit
paid-resource protection boundary.

## Failure injection and automated resilience evidence

The deterministic failure smoke used 5% each for 429, provider 5xx, timeout, connection failure,
and failure after the first token. It completed 387 streams: 363 successful and 24 safe failures
(6.2016%), with zero interrupted iterations. All 24 client failures arrived as sanitized SSE error
events rather than leaked provider details or broken HTTP connections.

Hammerly AI recorded 484 provider attempts, 121 failed attempts, and 97 retries: 24 each for 429,
5xx, and timeout plus 25 retryable connection failures. The remaining 24 network-classified failures
occurred after the first token and correctly were not retried. Client first-event p95 was 661.7 ms;
successful/failure-inclusive stream duration p95 was 876.1 ms. Recovery kept the rolling failure
rate below the circuit threshold, so no circuit opened in this mixed smoke.

Automated tests separately prove CLOSED → OPEN → HALF_OPEN → CLOSED, fail-fast bulkhead rejection,
429/503 retry, non-retryable 400/auth/quota behavior, timeout resource release, retry before the
first token, and no retry after the first token. Raw injection result:
[`phase5-failure-injection.json`](../../load-test/phase5/results/phase5-failure-injection.json).

## Tuning history and rejected results

Short early workloads used a 20 ms token interval and little/no pacing. They were useful for finding
limits but are not substituted for the final realistic-stream run.

| Retained result | Key setting/result |
| --- | --- |
| [`phase5-tuning-32.json`](../../load-test/phase5/results/phase5-tuning-32.json) | Production bulkhead 32; 93.51% / 98.99% / 99.76% hold failures |
| [`phase5-tuning-128.json`](../../load-test/phase5/results/phase5-tuning-128.json) | Bound 128; 0% / 5.73% / 99.76%; exposed outbound/generator limits |
| [`phase5-tuning-pool-256.json`](../../load-test/phase5/results/phase5-tuning-pool-256.json) | Pool enabled but an environment error left the live bound at 32; retained, not relabeled |
| [`phase5-tuning-pool-256-correct.json`](../../load-test/phase5/results/phase5-tuning-pool-256-correct.json) | Correct bound 256; 0% / 0% / 91.51%; most 1,000-VU failures never reached Core |
| [`phase5-tuning-vthreads-jitter-256.json`](../../load-test/phase5/results/phase5-tuning-vthreads-jitter-256.json) | Virtual threads/jitter; 0% / 0% / 91.68%; 15,324 Core-port `TIME_WAIT` sockets confirmed local Docker NAT churn |
| [`phase5-tuning-longstream-512.json`](../../load-test/phase5/results/phase5-tuning-longstream-512.json) | Realistic long stream, shortened holds; 0% / 0% / 1.52% controlled overload, no transport/HTTP failure |

The final schedule added realistic think time, retained long streams, used a fresh local port/run ID,
and allowed previous ephemeral ports to drain. This separated application capacity from a local
Docker Desktop generator limit. All raw results are retained under `load-test/phase5/results`.

## Verification and CI

- Hammerly AI: 63 tests, zero failures/errors/skips
- Hammerly Core: 25 tests, zero failures/errors/skips, including Testcontainers integration tests
- PowerShell load-test wrapper: parsed successfully
- Bash wrapper: syntax execution could not be completed on this host because WSL startup returned
  `E_ACCESSDENIED`; the portable script is checked in and manual Linux CI remains the execution path
- `git diff --check`: clean except expected Windows LF→CRLF notices

Normal CI remains mocked/free and does not invoke OpenAI. A separate manual workflow requires a
target URL, explicit load-test profile confirmation, and selected smoke/full mode; it builds the
pinned k6 image and uploads the raw summary. It contains no live-provider secret.

## Known limits and next validation

- The 1,000-VU result is a single-host deterministic-provider result, not an SLA or sizing promise.
- No paid OpenAI run was authorized; quota, model latency, network variability, and provider-side
  concurrency remain unmeasured.
- Production should keep the default provider bound 32 until account quota and cost policy justify a
  measured increase. The benchmark-only 512 bound must not be copied blindly.
- A distributed generator and production-like network/database/Redis environment are needed to
  establish deployable capacity and tail behavior.
- Server Actuator metrics are aggregate across the run; per-hold server percentiles would require an
  external scrape/metrics system, deliberately outside this phase.
- Java 22 ran Java 21-targeted artifacts locally; CI is the Java 21 compatibility authority.
- Authentication was not weakened. The harness uses the existing guest endpoint behavior unless an
  explicit test bearer token is provided.
- Kubernetes, Prometheus, and Grafana remain out of scope.
