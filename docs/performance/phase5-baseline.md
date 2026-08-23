# Phase 5 pre-change baseline

## Provenance

- Source: unmodified `main` commit `bd6b45fc7fe437de596312876be91effe9e73da6`
- Date: 2026-08-23 EDT
- Host: Windows 11 `10.0.26200`, Intel Core i9-13900HX, 32 logical processors,
  16,907,100,160 bytes host-visible RAM
- Runtime: Java `22.0.1` running Java 21-targeted artifacts; Docker Desktop `27.5.1`
- Generator: pinned k6 `1.2.2` with `xk6-sse` `0.1.12` in Docker Desktop
- Dependencies: `redis:7-alpine`; Kafka disabled; existing external test PostgreSQL connection
- Provider: local OpenAI-compatible deterministic SSE mock, 100 ms first token, eight tokens,
  1,000 ms token interval, immediate completion after the eighth token
- Workload: 30-second warm-up, 15-second ramps, two-minute holds at 100/500/1,000 VUs,
  30-second cooldown, 10-second think time with ±25% jitter, cold/unique conversation IDs

The checked-out baseline applications were built and launched from a separate archived worktree.
The current Phase 5 source did not serve baseline traffic. Two readiness calls ran before k6; they
are present in server totals but not in the k6 hold-stage table.

## Corrected comparable result

| Hold | Total streams | Successful | Success RPS | Failure rate | Successful duration p50 | p95 | p99 | First event p95 |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 100 VUs | 404 | 330 | 2.75 | 18.32% | 23,469 ms | 25,828.85 ms | 26,373.72 ms | 25,821.75 ms |
| 500 VUs | 4,208 | 338 | 2.82 | 91.97% | 26,511 ms | 26,972.60 ms | 27,014.63 ms | 26,964.45 ms |
| 1,000 VUs | 7,201 | 340 | 2.83 | 95.28% | 26,512 ms | 27,070.80 ms | 46,265.19 ms | 27,065.65 ms |

Success RPS is successful streams divided by the 120-second hold. k6 reports a tagged counter rate
over the entire scenario, so that generated value is not used as hold throughput.

Across the complete scenario, k6 observed 14,821 streams: 1,259 succeeded, 13,562 failed
(91.5053%), 14,717 iterations completed, and 105 were interrupted while the final backlog drained.
The expected latency/error thresholds failed, and k6 exited non-zero. The process itself remained
alive and responsive to health/metrics requests.

Hammerly AI recorded 1,261 successful provider streams, including the two readiness calls. Provider
time was 9,037.7226 seconds total, 7.167 seconds average, and 7.2007 seconds maximum. There were
1,261 cache misses and no hits because every conversation/request identity was unique. The baseline
did not have Phase 5 retry, bulkhead, circuit, first-token, or active-provider meters.

| Process | Peak working set | Final working set | CPU time | Final threads |
| --- | ---: | ---: | ---: | ---: |
| Core | 452,612,096 bytes (431.6 MiB) | 435,744,768 bytes | 106.52 s | 85 |
| AI | 443,351,040 bytes (422.8 MiB) | 440,311,808 bytes | 97.59 s | 116 |

## Bottleneck evidence

The original Core MVC streaming executor had `corePoolSize=20`, `maxPoolSize=20`, and queue
capacity `50`. At concurrency it emitted `TaskRejectedException`/`RejectedExecutionException`
stack traces from `StreamingResponseBodyReturnValueHandler`. The successful stream ceiling remained
about 2.8/s while rejected requests returned quickly, causing total attempts and failure percentage
to climb with VUs. Core also used a non-pooled outbound HTTP path. This was an application capacity
limit, not a provider or Redis limit.

## Retained preliminary runs

No failed benchmark was deleted:

- [`baseline-full.json`](../../load-test/phase5/results/baseline-full.json) is the first aggressive,
  no-think-time baseline: 49,721 streams and 71.869% aggregate failure. It established the executor
  rejection signature but is not the before/after comparison.
- [`phase5-baseline-comparable-100-500-1000.json`](../../load-test/phase5/results/phase5-baseline-comparable-100-500-1000.json)
  used the realistic schedule but the mock waited an accidental extra second after its last token.
  That harness discrepancy was found during review; this run is retained but excluded from the main
  comparison.
- [`phase5-baseline-comparable-corrected.json`](../../load-test/phase5/results/phase5-baseline-comparable-corrected.json)
  is the corrected primary baseline reported above.

The baseline establishes local, reproducible comparative evidence. It is not a production capacity
claim: generator, services, Redis, and Docker NAT shared one workstation, and the deterministic
provider does not model OpenAI quota, regional network behavior, or paid-model variance.
