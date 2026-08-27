# Phase 8 GKE autoscaling results

## Completion status

Phase 8 was validated on a real GKE Autopilot cluster on August 26, 2026. The
cluster was deleted after the evidence and scale-down observation were captured;
the teardown audit is recorded below.

## Test identity and configuration

| Item | Observed value |
| --- | --- |
| Date / timezone | August 26, 2026 / America/New_York (EDT) |
| Application image Git SHA | `821d9b5359f52c8be12d5a790155e0a5a354e4d5` |
| Kubernetes manifest state | Phase 8 working tree, `loadtest` Kustomize overlay |
| GCP project / region | `hammerly-506214` / `us-west1` |
| Cluster / type | `hammerly-gke` / regional GKE Autopilot |
| Kubernetes version | `v1.35.6-gke.1710000` |
| Backend resources | 250m CPU, 512 MiB request; 1 CPU, 1 GiB limit; 2 replicas |
| AI resources | 100m CPU, 512 MiB request; 1 CPU, 1 GiB limit; HPA 2–10 |
| Worker resources | 100m CPU, 384 MiB request; 1 CPU, 1 GiB limit; 1 replica |
| HPA | CPU 60%; scale up by max of +3 or 100%/60s; 300s scale-down stabilization |
| Provider | deterministic loadtest; 100 ms first token, 8 tokens, 250 ms spacing |
| Schedule | 30s warmup, 15s ramps, 120s each at 100/500/1,000 VUs, 30s cooldown |
| Client run duration | 476.168 seconds including graceful completion |

The cluster started the benchmark with two Ready AI pods. The full run completed
18,162 SSE iterations with no k6-interrupted iterations. All values below came
from the retained k6 summary and timestamped Kubernetes/Prometheus evidence.

## Benchmark

RPS is successful streams divided by the two-minute stage hold. Latency and
error rate are client-observed SSE values; first-token latency is measured from
request start to the first `chunk` event.

| VUs | Max AI pods | Successful streams | RPS | P50 (ms) | P95 (ms) | P99 (ms) | Error rate | First-token P95 (ms) |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 100 | 10 | 964 | 8.033 | 2,213.5 | 2,878.7 | 3,385.87 | 0.5160% | 769.6 |
| 500 | 10 | 4,601 | 38.342 | 2,516.0 | 4,866.0 | 6,798.0 | 0.0217% | 1,752.0 |
| 1,000 | 10 | 9,510 | 79.250 | 2,545.0 | 3,317.1 | 3,659.91 | 0.0105% | 1,015.0 |

## Autoscaling timeline

The HPA scaled from 2 → 5 → 7 → 10 without manual replica changes. It reached
the maximum near the end of the 100-VU hold and stayed there through the higher
stages.

| EDT timestamp | Actual stage | Desired / current AI pods | HPA CPU | Observation |
| --- | --- | ---: | ---: | --- |
| 20:14:10.882 | test start | 2 / 2 | 5–7% pre-load | inferred exactly from summary end time minus k6 duration |
| 20:14:50.851 | ramp to 100 | 5 / 5 | 318% | first scale-up completed |
| 20:15:57.007 | 100 VUs | 7 / 5 | 206% | second scale-up requested |
| 20:16:18.591 | 100 VUs | 7 / 7 | 206% | seven pods Ready |
| 20:16:40.164 | 100 VUs | 10 / 10 | 241% | maximum reached |
| 20:17:24–20:18:53 | 500 VUs | 10 / 10 | 224–335% | maximum sustained |
| 20:19:38–20:21:06 | 1,000 VUs | 10 / 10 | 162–207% | maximum sustained |
| 20:30:52.179 | post-load | 2 / 3 | 5% | scale-down converging after stabilization/rate limits |
| 20:31:09.818 | post-load | 2 / 2 | 5% | minimum restored before teardown |

`phase8-autoscaling.csv` aligns stage labels to the actual k6 start and contains
historical Prometheus instant queries for every sample. The initial local
Prometheus port-forward was lost when Autopilot recreated the Prometheus pod;
the retained TSDB was queried by timestamp afterward. Kubernetes HPA/pod/CPU
samples continued independently throughout the event.

## Kubernetes and observability evidence

- Backend, AI, Worker, Redis, Kafka, Prometheus, kube-state-metrics, and Grafana
  were Ready before load. No application pod restarted during the benchmark.
- The public Backend, internal AI, and internal Worker health endpoints all
  reported `UP`.
- Every Prometheus target was healthy before load: Backend (2), AI (2), Worker
  (1), kube-state-metrics (1), kubelet/cAdvisor (4), and Prometheus (1). AI target
  discovery followed the HPA-created pods.
- Grafana 13.1.3 reported database `ok`; both provisioned Hammerly dashboards
  were available through the local-only service.
- A high-load snapshot showed the ten AI pods using roughly 168–197m CPU and
  295–349 MiB each, Backend at 343–380m CPU and 333–341 MiB, Worker at 208m CPU
  and 263 MiB, and Kafka at 728m CPU and 613 MiB.

Evidence files:

- `load-test/phase8/results/phase8-k6-summary.json`
- `load-test/phase8/results/phase8-autoscaling.csv`
- `load-test/phase8/results/phase8-kubectl-snapshots.log`
- `load-test/phase8/results/phase8-results.csv`

## Teardown audit

The exact `hammerly-gke` cluster and its `hammerly` namespace were deleted after
the run. The post-delete audit found no GKE cluster or Hammerly-named orphaned
disk, forwarding rule, or address. Existing Cloud Run services, Artifact
Registry images, Secret Manager secrets, GitHub Pages, and Supabase were left
untouched.

## Limitations

- In-cluster Redis and single-broker Kafka are demonstration infrastructure,
  not HA production services.
- The loadtest provider proves application/Kubernetes scaling, not OpenAI quota
  or provider capacity.
- The 100-VU client error rate represents five failed streams out of 969 stage
  streams; server-side Prometheus error/cancellation rate samples were 0%, so
  these were client-observed incomplete/transport outcomes rather than recorded
  AI error outcomes.
- Results are not portable to other regions, Kubernetes versions, image SHAs,
  provider timings, or resource requests without a new benchmark.
