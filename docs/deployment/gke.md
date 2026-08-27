# Phase 8 — GKE Autopilot deployment and autoscaling

## Scope and safety boundary

GKE is an additional, short-lived demo environment. The existing Cloud Run
services, GitHub Pages deployment, local Docker Compose stack, Phase 5 evidence,
and Phase 6 dashboard remain intact.

The GKE topology is:

```text
GitHub Pages
  → public Backend LoadBalancer → Backend Deployment (2)
    → internal AI Service → AI Deployment (HPA 2..10)
      ├→ internal Redis StatefulSet + 5 GiB PVC
      └→ internal Kafka 3.9.1 KRaft StatefulSet + 10 GiB PVC
          → Worker Deployment (1, consumer group hammerly-worker-v1)

Prometheus + 10 GiB PVC → app pods, kube-state-metrics, kubelet/cAdvisor
Grafana → Prometheus
```

Redis and the single KRaft broker are intentionally inexpensive demonstration
dependencies. They are persistent across pod restarts, but they are not
multi-zone, highly available, encrypted-in-transit production data services.
Prometheus, Grafana, AI, Worker, Redis, and Kafka are all `ClusterIP`-only.

## Repository layout

```text
k8s/
  base/
    backend/          Deployment + ClusterIP Service
    ai/               Deployment + ClusterIP Service + autoscaling/v2 HPA
    worker/           Deployment + management-only ClusterIP Service
    data/             Redis and Kafka KRaft StatefulSets
    observability/    Prometheus, Grafana, kube-state-metrics, RBAC
  overlays/
    demo/             public Backend; real OpenAI production profile
    loadtest/         public Backend; deterministic AI provider only
```

Both overlays are rendered with Kustomize. `GIT_SHA` and `WORKER_GIT_SHA` are
deliberate render-time placeholders; the deployment script replaces them with
validated full 40-character immutable tags before sending YAML to Kubernetes.
No workload deploys `latest`.

## Resource and HPA choices

Phase 5 measured peak local working sets of about 535 MiB for Core and 477 MiB
for AI. Both therefore request 512 MiB and have 1 GiB limits. The AI CPU request
is 100m so the HPA reacts to the measured CPU of this mostly streaming/I/O-bound
service; it is not a CPU limit. Other requests are conservative starting values
and should be revisited using the Phase 8 CSV plus Grafana:

| Workload | Replicas | CPU request / limit | Memory request / limit |
| --- | ---: | ---: | ---: |
| Backend | 2 | 250m / 1 | 512 MiB / 1 GiB |
| AI | HPA 2–10 | 100m / 1 | 512 MiB / 1 GiB |
| Worker | 1 | 100m / 1 | 384 MiB / 1 GiB |
| Redis | 1 | 100m / 500m | 128 MiB / 512 MiB |
| Kafka | 1 | 500m / 1 | 1 GiB / 2 GiB |
| Prometheus | 1 | 250m / 1 | 512 MiB / 1 GiB |
| Grafana | 1 | 100m / 500m | 256 MiB / 512 MiB |
| kube-state-metrics | 1 | 50m / 200m | 128 MiB / 256 MiB |

The AI HPA uses CPU utilization at 60%, `minReplicas: 2`, and
`maxReplicas: 10`. Scale-up permits either three pods or 100% per 60 seconds,
whichever is larger. Scale-down is limited to 25% per minute with a five-minute
stabilization window. This allows genuine 2 → 5 → 10 behavior when utilization
warrants it without forcing those replica counts. Report the actual timeline.

## Prerequisites

- `gcloud` with `gke-gcloud-auth-plugin`, `kubectl`, PowerShell 7, Docker,
  and an authenticated GCP identity
- project `hammerly-506214`, region `us-west1`
- Artifact Registry repository `hammerly`
- immutable images `hammerly-core:<sha>`, `hammerly-ai:<sha>`, and
  `hammerly-worker:<sha>`
- existing Secret Manager secrets:
  `hammerly-supabase-db-url`, `hammerly-jwt-secret`,
  `hammerly-openai-api-key`, `hammerly-redis-password`, and
  `hammerly-ai-internal-token`

The bootstrap creates `hammerly-grafana-admin-password` once if it is missing.
It reads all values into memory and applies one Kubernetes Secret through stdin;
secret values never enter repository manifests, generated evidence, command
logs, or Git. The one-time Grafana Secret Manager upload uses a validated OS
temporary file that is removed in a `finally` block.
Autopilot supplies Workload Identity Federation for GKE. Application pods do not
need GCP API access because the authenticated deployment identity performs the
allowed Secret Manager-to-Kubernetes bootstrap.

## Create and deploy

Create or reuse the Autopilot cluster:

```powershell
./scripts/gcp/create-gke-demo.ps1
```

Deploy the cost-free deterministic provider for the benchmark:

```powershell
$sha = git rev-parse HEAD
./scripts/gcp/deploy-gke-demo.ps1 -Overlay loadtest -GitSha $sha -WorkerGitSha $sha
```

If a service was not changed by the commit, its workflow might not yet have
published that SHA. Build and push the missing immutable image, or use the last
SHA that genuinely built that service with `-WorkerGitSha`. Never relabel an
unrelated image just to make the tag exist.

For a real OpenAI demo after the load test, deploy the separate overlay:

```powershell
./scripts/gcp/deploy-gke-demo.ps1 -Overlay demo -GitSha $sha -WorkerGitSha $sha
```

Do not run the full k6 schedule against `demo`. The runner also refuses a full
test unless the live Deployment reports `SPRING_PROFILES_ACTIVE=loadtest`.

## Validation

The deployment script waits for Redis, Kafka, AI, Worker, Backend, Prometheus,
kube-state-metrics, and Grafana rollouts. Then verify:

```powershell
kubectl get deployments,statefulsets,pods,services,hpa -n hammerly -o wide
kubectl top pods -n hammerly
kubectl get events -n hammerly --sort-by=.lastTimestamp
```

Obtain the Backend address and exercise the complete Backend → AI path:

```powershell
$address = kubectl get service hammerly-backend -n hammerly -o jsonpath='{.status.loadBalancer.ingress[0].ip}'
Invoke-RestMethod "http://$address/actuator/health"
```

The benchmark itself traverses Backend → AI → Redis and produces Kafka events
that Worker consumes. Confirm the integrations from Prometheus after traffic:

```promql
sum(rate(ai_requests_total[1m]))
sum(rate(redis_cache_misses_total[1m]))
sum(rate(hammerly_kafka_publish_success_total[1m]))
sum(rate(kafka_processing_duration_seconds_count{outcome="success"}[1m]))
```

Prometheus target status is available through a local-only port-forward:

```powershell
kubectl port-forward service/prometheus 9090:9090 -n hammerly
# http://localhost:9090/targets
```

Grafana is also local-only. Its password remains in Secret Manager/Kubernetes:

```powershell
kubectl port-forward service/grafana 3001:3000 -n hammerly
# http://localhost:3001
```

Grafana provisions the Phase 6 **Hammerly — System Overview** dashboard plus
**Hammerly — Kubernetes / Autoscaling**. The Kubernetes section shows current
and desired replicas, HPA CPU target, per-pod CPU/memory, restarts, readiness,
RPS, percentiles, and error rate. Prometheus uses Kubernetes pod discovery and
kube-state-metrics; it scrapes kubelet/cAdvisor without a privileged node
exporter, which is compatible with the Autopilot security model.

## Controlled autoscaling benchmark

With `loadtest` deployed and two AI pods ready:

```powershell
./scripts/load-test/run-phase8.ps1 -Mode full
```

The reused Phase 5 schedule is 30 seconds warmup; 15-second ramps; two-minute
holds at 100, 500, and 1,000 VUs; and 30 seconds cooldown. The Phase 8 provider
uses 100 ms first-token delay, eight tokens, and 250 ms token spacing. Kafka and
Redis remain enabled. No request reaches OpenAI.

After k6 finishes, the runner keeps the evidence monitor active for up to ten
minutes while the HPA returns to its two-pod minimum. Set
`-HpaCooldownTimeoutSeconds 0` only when post-load scale-down evidence is not
needed. Prometheus samples use a local port-forward with an in-cluster query
fallback, then are aligned to the actual k6 start before the stage summary is
written.

The runner writes real evidence under `load-test/phase8/results/`:

- raw k6 summary with tagged stage metrics
- `phase8-autoscaling.csv` sampled every 15 seconds
- timestamped `kubectl get pods`, `kubectl get hpa`, and `kubectl top pods`
- a 100/500/1,000-VU result CSV

Copy the measured values and replica timeline into
`docs/performance/phase8-gke-results.md`. Do not mark the phase complete if the
cluster was not deployed, metrics were unavailable, or HPA behavior was not
observed.

## Manual CI/CD

`.github/workflows/deploy-gke.yml` is `workflow_dispatch` only. It runs all
three Maven test suites, authenticates with GitHub OIDC, builds and pushes all
three SHA-tagged images, deploys the selected overlay, waits for rollouts, and
verifies public Backend health. It never creates a cluster on a source push.

Required GitHub variables are the existing `GCP_PROJECT_ID`, `GCP_REGION`,
`GCP_WORKLOAD_IDENTITY_PROVIDER`, `GCP_DEPLOY_SERVICE_ACCOUNT`, and
`GCP_ARTIFACT_REPOSITORY`; `GCP_GKE_CLUSTER` defaults to `hammerly-gke`.
The federated deployer needs Artifact Registry writer, GKE deployment/get-
credentials access, Secret Manager accessor, and Kubernetes RBAC for the
declared resources. First bootstrap also needs permission to create the Grafana
secret; remove that extra permission after it exists. No service-account JSON
key is used.

## Teardown and cost control

Delete the demo as soon as evidence is captured:

```powershell
./scripts/gcp/delete-gke-demo.ps1
```

The script first deletes namespace `hammerly` and waits for LoadBalancer/PVC
cleanup, then deletes exactly
`projects/hammerly-506214/locations/us-west1/clusters/hammerly-gke`. It does not
touch Cloud Run, GitHub Pages, Artifact Registry, Secret Manager, or Supabase.

At August 2026 public list rates, GKE charges a $0.10/cluster-hour management
fee (the billing-account free-tier credit can cover one Autopilot cluster) and
general-purpose Autopilot charges by requested CPU/memory while pods run. These
manifests request about 1.8 vCPU/4.375 GiB initially and 2.6 vCPU/8.375 GiB at
10 AI pods: roughly $0.10/hour and $0.16/hour respectively for pod CPU/memory at
the published base rates, before discounts and Autopilot adjustments. Add the
cluster fee, three persistent disks (25 GiB total), one external forwarding
rule (published US rate $0.025/hour for the first five), processed/network
traffic, and retained image/secret storage. This is an estimate, not an invoice.

Pricing references:

- <https://cloud.google.com/kubernetes-engine/pricing>
- <https://cloud.google.com/compute/disks-image-pricing>
- <https://cloud.google.com/vpc/network-pricing>
- <https://cloud.google.com/artifact-registry/pricing>
- <https://cloud.google.com/secret-manager/pricing>

After cluster deletion, GKE pod compute, cluster management, the cluster
LoadBalancer, and namespace PVC charges should stop. Resources intentionally
still capable of charging are Artifact Registry images, Secret Manager active
versions/access, the existing Cloud Run/Supabase/GitHub environment, and any
disk, address, or forwarding rule left orphaned by an interrupted teardown.
Audit the project after teardown:

```powershell
gcloud container clusters list --project hammerly-506214
gcloud compute disks list --project hammerly-506214 --filter='name~hammerly'
gcloud compute forwarding-rules list --project hammerly-506214 --filter='name~hammerly'
gcloud compute addresses list --project hammerly-506214 --filter='name~hammerly'
```
