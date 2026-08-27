# Phase 8 GKE autoscaling benchmark

This directory reuses the Phase 5 xk6-SSE workload. The `loadtest` Kustomize
overlay is the safety boundary: Hammerly AI runs its deterministic provider and
never calls OpenAI during the 100/500/1,000-VU schedule.

Run from the repository root after deploying `k8s/overlays/loadtest`:

```powershell
./scripts/load-test/run-phase8.ps1 -Mode full
```

The runner refuses a full test unless the live AI Deployment declares
`SPRING_PROFILES_ACTIVE=loadtest`. It writes:

- `results/phase8-k6-summary.json`: raw k6 summary
- `results/phase8-autoscaling.csv`: timestamped HPA/CPU/Prometheus samples
- `results/phase8-kubectl-snapshots.log`: pod, HPA, and `kubectl top` evidence
- `results/phase8-results.csv`: per-stage latency/throughput/error summary

The full runner continues sampling after traffic stops until the HPA returns to
two Ready AI pods or its ten-minute evidence timeout expires. Prometheus samples
are enriched from retained timestamped data, so a transient port-forward loss
does not turn application metrics into invented or missing results.

The CSVs are evidence files, not templates. Do not commit synthetic rows.
