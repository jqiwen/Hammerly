# Hammerly Google Cloud deployment setup

Production deployments target project `hammerly-506214` in `us-west1`. The live services are
`hammerly-backend` and `hammerly-ai` on Cloud Run, `hammerly.jqiwen.com` on GitHub Pages, Upstash
Redis over TCP/TLS, and Supabase PostgreSQL/pgvector. Artifact Registry repository `hammerly` also
stores the manually published `hammerly-worker` image. The optional asynchronous tier is a private
single-node Kafka KRaft VM feeding a Cloud Run Worker Pool; its explicit ON/OFF scripts keep it
separate from the request-driven deployment workflows and from the Upstash lifecycle.

```text
React / GitHub Pages
        |
Cloud Run Core
        |
Cloud Run AI -------- OpenAI
   |            \
Upstash Redis   Supabase / pgvector

Asynchronous path only:
Core / AI -> private Compute Engine Kafka (KRaft) -> Cloud Run Worker Pool
```

Kafka is never inserted into the Core-to-AI SSE response path. AI events are best-effort after a
completed response, while Core knowledge jobs remain durable in the PostgreSQL transactional outbox
until Kafka returns.

Production secret values stay in Google Secret Manager. GitHub stores only non-secret resource identifiers and secret **names**. GitHub authenticates to Google Cloud with short-lived OIDC credentials through Workload Identity Federation; do not create or upload a service-account JSON key.

## 1. Choose the project and region

Run these commands in Google Cloud Shell. Replace the project ID, but keep the other names unless you intentionally configure matching GitHub variables later.

```bash
export HAMMERLY_PROJECT_ID="hammerly-506214"
export HAMMERLY_REGION="us-west1"
export HAMMERLY_GAR_REPOSITORY="hammerly"
export HAMMERLY_GITHUB_REPOSITORY="jqiwen/Hammerly"
export HAMMERLY_WIF_POOL="github"
export HAMMERLY_WIF_PROVIDER="hammerly-main"
export HAMMERLY_DEPLOYER_NAME="hammerly-github-deployer"
export HAMMERLY_CORE_RUNTIME_NAME="hammerly-core-runtime"
export HAMMERLY_AI_RUNTIME_NAME="hammerly-ai-runtime"

gcloud config set project "${HAMMERLY_PROJECT_ID}"
export HAMMERLY_PROJECT_NUMBER="$(gcloud projects describe "${HAMMERLY_PROJECT_ID}" --format='value(projectNumber)')"
export HAMMERLY_DEPLOYER_EMAIL="${HAMMERLY_DEPLOYER_NAME}@${HAMMERLY_PROJECT_ID}.iam.gserviceaccount.com"
export HAMMERLY_CORE_RUNTIME_EMAIL="${HAMMERLY_CORE_RUNTIME_NAME}@${HAMMERLY_PROJECT_ID}.iam.gserviceaccount.com"
export HAMMERLY_AI_RUNTIME_EMAIL="${HAMMERLY_AI_RUNTIME_NAME}@${HAMMERLY_PROJECT_ID}.iam.gserviceaccount.com"
```

## 2. Enable APIs and create Artifact Registry

```bash
gcloud services enable \
  artifactregistry.googleapis.com \
  iamcredentials.googleapis.com \
  run.googleapis.com \
  secretmanager.googleapis.com \
  sts.googleapis.com

gcloud artifacts repositories create "${HAMMERLY_GAR_REPOSITORY}" \
  --repository-format=docker \
  --location="${HAMMERLY_REGION}" \
  --description="Hammerly application images"
```

Images use these names and both an immutable commit tag and a convenience `latest` tag:

```text
REGION-docker.pkg.dev/PROJECT_ID/hammerly/hammerly-core:<git-sha>
REGION-docker.pkg.dev/PROJECT_ID/hammerly/hammerly-ai:<git-sha>
REGION-docker.pkg.dev/PROJECT_ID/hammerly/hammerly-worker:<git-sha>
```

The Cloud Run deployment always uses the immutable Git SHA tag.

## 3. Create deployment and runtime service accounts

```bash
gcloud iam service-accounts create "${HAMMERLY_DEPLOYER_NAME}" \
  --display-name="Hammerly GitHub deployer"
gcloud iam service-accounts create "${HAMMERLY_CORE_RUNTIME_NAME}" \
  --display-name="Hammerly Core runtime"
gcloud iam service-accounts create "${HAMMERLY_AI_RUNTIME_NAME}" \
  --display-name="Hammerly AI runtime"

gcloud projects add-iam-policy-binding "${HAMMERLY_PROJECT_ID}" \
  --member="serviceAccount:${HAMMERLY_DEPLOYER_EMAIL}" \
  --role="roles/artifactregistry.writer"
gcloud projects add-iam-policy-binding "${HAMMERLY_PROJECT_ID}" \
  --member="serviceAccount:${HAMMERLY_DEPLOYER_EMAIL}" \
  --role="roles/run.admin"

gcloud iam service-accounts add-iam-policy-binding "${HAMMERLY_CORE_RUNTIME_EMAIL}" \
  --member="serviceAccount:${HAMMERLY_DEPLOYER_EMAIL}" \
  --role="roles/iam.serviceAccountUser"
gcloud iam service-accounts add-iam-policy-binding "${HAMMERLY_AI_RUNTIME_EMAIL}" \
  --member="serviceAccount:${HAMMERLY_DEPLOYER_EMAIL}" \
  --role="roles/iam.serviceAccountUser"
```

The deployer builds and deploys. Core and AI use separate runtime identities, so each receives access only to its own runtime secrets.

## 4. Configure GitHub OIDC federation

The provider condition below accepts only this repository's `main` branch.

```bash
gcloud iam workload-identity-pools create "${HAMMERLY_WIF_POOL}" \
  --location=global \
  --display-name="GitHub Actions"

gcloud iam workload-identity-pools providers create-oidc "${HAMMERLY_WIF_PROVIDER}" \
  --location=global \
  --workload-identity-pool="${HAMMERLY_WIF_POOL}" \
  --display-name="Hammerly main branch" \
  --issuer-uri="https://token.actions.githubusercontent.com" \
  --attribute-mapping="google.subject=assertion.sub,attribute.repository=assertion.repository,attribute.ref=assertion.ref" \
  --attribute-condition="assertion.repository=='${HAMMERLY_GITHUB_REPOSITORY}' && assertion.ref=='refs/heads/main'"

gcloud iam service-accounts add-iam-policy-binding "${HAMMERLY_DEPLOYER_EMAIL}" \
  --role="roles/iam.workloadIdentityUser" \
  --member="principalSet://iam.googleapis.com/projects/${HAMMERLY_PROJECT_NUMBER}/locations/global/workloadIdentityPools/${HAMMERLY_WIF_POOL}/attribute.repository/${HAMMERLY_GITHUB_REPOSITORY}"

gcloud iam workload-identity-pools providers describe "${HAMMERLY_WIF_PROVIDER}" \
  --location=global \
  --workload-identity-pool="${HAMMERLY_WIF_POOL}" \
  --format='value(name)'
```

Save the final command's output as the GitHub variable `GCP_WORKLOAD_IDENTITY_PROVIDER`. It has the form `projects/PROJECT_NUMBER/locations/global/workloadIdentityPools/github/providers/hammerly-main`.

## 5. Create production secrets

Create the containers first:

```bash
for HAMMERLY_SECRET_NAME in \
  hammerly-supabase-db-url \
  hammerly-jwt-secret \
  hammerly-openai-api-key \
  hammerly-redis-password \
  hammerly-ai-internal-token; do
  gcloud secrets create "${HAMMERLY_SECRET_NAME}" --replication-policy=automatic
done
```

Add each sensitive value interactively; paste the value and finish standard input. Do not put values into shell history or GitHub variables.

```bash
gcloud secrets versions add hammerly-supabase-db-url --data-file=-
gcloud secrets versions add hammerly-jwt-secret --data-file=-
gcloud secrets versions add hammerly-openai-api-key --data-file=-
gcloud secrets versions add hammerly-redis-password --data-file=-
```

Generate the Core-to-AI token without displaying it:

```bash
openssl rand -base64 48 | gcloud secrets versions add hammerly-ai-internal-token --data-file=-
```

Grant runtime access:

```bash
for HAMMERLY_SECRET_NAME in hammerly-supabase-db-url hammerly-jwt-secret hammerly-redis-password hammerly-ai-internal-token; do
  gcloud secrets add-iam-policy-binding "${HAMMERLY_SECRET_NAME}" \
    --member="serviceAccount:${HAMMERLY_CORE_RUNTIME_EMAIL}" \
    --role="roles/secretmanager.secretAccessor"
done

for HAMMERLY_SECRET_NAME in hammerly-supabase-db-url hammerly-openai-api-key hammerly-redis-password hammerly-ai-internal-token; do
  gcloud secrets add-iam-policy-binding "${HAMMERLY_SECRET_NAME}" \
    --member="serviceAccount:${HAMMERLY_AI_RUNTIME_EMAIL}" \
    --role="roles/secretmanager.secretAccessor"
done

for HAMMERLY_SECRET_NAME in \
  hammerly-supabase-db-url hammerly-jwt-secret hammerly-openai-api-key \
  hammerly-redis-password hammerly-ai-internal-token; do
  gcloud secrets add-iam-policy-binding "${HAMMERLY_SECRET_NAME}" \
    --member="serviceAccount:${HAMMERLY_DEPLOYER_EMAIL}" \
    --role="roles/secretmanager.secretAccessor"
done
```

## 6. Configure production Upstash Redis

Production uses the Upstash Redis TCP endpoint with TLS, not Memorystore and not the Upstash REST
API. Record only these non-secret connection settings in GitHub repository variables:

```text
AI_REDIS_HOST=relaxed-leopard-178153.upstash.io
AI_REDIS_PORT=6379
AI_REDIS_SSL=true
HAMMERLY_REDIS_ENABLED=true
```

The password remains only in `hammerly-redis-password` in Google Secret Manager and is injected as
`REDIS_PASSWORD`. Never store the password or an Upstash REST token in workflow YAML, source,
documentation, logs, or a GitHub variable. The existing Cloud Run network/subnet settings are
preserved separately by `AI_VPC_NETWORK` and `AI_VPC_SUBNET`.

## 7. Configure GitHub

Create a GitHub environment named `production`. Optionally require a reviewer for deployments. Then add these repository variables under **Settings → Secrets and variables → Actions → Variables**:

```text
GCP_DEPLOYMENTS_ENABLED=false
GCP_PROJECT_ID=hammerly-506214
GCP_REGION=us-west1
GCP_WORKLOAD_IDENTITY_PROVIDER
GCP_DEPLOY_SERVICE_ACCOUNT
GCP_ARTIFACT_REPOSITORY=hammerly

CORE_CLOUD_RUN_SERVICE=hammerly-backend
CORE_RUNTIME_SERVICE_ACCOUNT
CORE_DB_SECRET=hammerly-supabase-db-url
CORE_JWT_SECRET=hammerly-jwt-secret
CORE_REDIS_PASSWORD_SECRET=hammerly-redis-password
CORE_CLOUD_RUN_MIN_INSTANCES=0
HAMMERLY_DB_MAX_POOL_SIZE=5
HAMMERLY_DB_MIN_IDLE=0
HAMMERLY_MARKETPLACE_CACHE_ENABLED=false
HAMMERLY_AUTH_JWT_TTL=45m
HAMMERLY_AUTH_RATE_LIMIT_REDIS_ENABLED=false
HAMMERLY_AUTH_LOGIN_LIMIT=10
HAMMERLY_AUTH_LOGIN_WINDOW=1m
HAMMERLY_AUTH_REGISTER_LIMIT=5
HAMMERLY_AUTH_REGISTER_WINDOW=10m

AI_CLOUD_RUN_SERVICE=hammerly-ai
AI_RUNTIME_SERVICE_ACCOUNT
AI_DB_SECRET=hammerly-supabase-db-url
AI_OPENAI_SECRET=hammerly-openai-api-key
AI_REDIS_PASSWORD_SECRET=hammerly-redis-password
AI_INTERNAL_TOKEN_SECRET=hammerly-ai-internal-token
AI_REDIS_HOST=relaxed-leopard-178153.upstash.io
AI_REDIS_PORT=6379
AI_REDIS_SSL=true
AI_VPC_NETWORK=default
AI_VPC_SUBNET=default
AI_CLOUD_RUN_MIN_INSTANCES=0

OPENAI_MODEL=gpt-4.1-mini
OPENAI_MAX_OUTPUT_TOKENS=200
HAMMERLY_AI_LLM_MAX_ATTEMPTS=2
HAMMERLY_AI_LLM_FIRST_TOKEN_TIMEOUT=8s
HAMMERLY_AI_LLM_IDLE_TIMEOUT=10s
HAMMERLY_RAG_ENABLED=true
HAMMERLY_RAG_TOP_K=3
HAMMERLY_RAG_SIMILARITY_THRESHOLD=0.25
HAMMERLY_RAG_TIMEOUT=1200ms
HAMMERLY_RAG_KB_VERSION_CACHE_TTL=45s
HAMMERLY_AI_CONTEXT_RECENT_TURNS=3
HAMMERLY_AI_CONTEXT_MAX_CHARS=8000
HAMMERLY_AI_EMBEDDING_PROVIDER=openai
HAMMERLY_AI_EMBEDDING_MODEL=text-embedding-3-small

HAMMERLY_FRONTEND_URL=https://hammerly.jqiwen.com
HAMMERLY_AI_URL
HAMMERLY_API_URL

HAMMERLY_REDIS_ENABLED=true
HAMMERLY_KAFKA_ENABLED=false
KAFKA_BOOTSTRAP_SERVERS=kafka-disabled.invalid:9092
```

`AI_DB_SECRET` may be omitted when `CORE_DB_SECRET` already names the shared
Supabase JDBC secret. Likewise, `CORE_REDIS_PASSWORD_SECRET` may be omitted
when `AI_REDIS_PASSWORD_SECRET` already names the shared Redis password
secret. Service-specific variables take precedence when both are configured.

For the lowest-cost configuration, leave both Cloud Run minimums at `0`. For a
portfolio/demo environment where first-request latency matters, set both
`CORE_CLOUD_RUN_MIN_INSTANCES=1` and `AI_CLOUD_RUN_MIN_INSTANCES=1`. This keeps
one warm instance of each service and incurs the corresponding Cloud Run cost.
For the same low-latency Core profile, set `HAMMERLY_DB_MIN_IDLE=1` so its warm
instance retains one ready database connection. Set `HAMMERLY_MARKETPLACE_CACHE_ENABLED=true`
only if Core should use the configured Upstash endpoint for that cache.

Core always enables auth throttling and trusts Cloud Run's forwarded client address in the deployment workflow. The default process-local limiter applies independently to each Cloud Run instance. Set `HAMMERLY_AUTH_RATE_LIMIT_REDIS_ENABLED=true` only when Core should use Upstash to enforce shared thresholds across instances. Redis failures automatically fall back to bounded local windows. Access tokens expire after `HAMMERLY_AUTH_JWT_TTL` (45 minutes by default); logout is client-side token disposal and does not revoke an already issued token.

Cloud Run startup CPU boost is enabled explicitly by both request-driven deployment workflows.
Keep `HAMMERLY_REDIS_ENABLED=true` for production AI so Upstash provides cross-request
conversation-summary, RAG-retrieval, and grounded-FAQ caches; every Redis operation still fails
open to uncached work.

Set the service-account variables to their full email addresses. No GitHub Action secret is required for GCP credentials or application runtime secrets.

## 8. First deployment order

1. Set the documented Upstash variables, keep `HAMMERLY_REDIS_ENABLED=true`, and keep `HAMMERLY_KAFKA_ENABLED=false`. RAG reads can remain enabled because they query READY pgvector chunks synchronously; Kafka is required only to ingest or refresh documents.
2. Set `GCP_DEPLOYMENTS_ENABLED=true`.
3. Manually run **Deploy AI** from GitHub Actions.
4. Read the resulting AI Cloud Run URL and set `HAMMERLY_AI_URL` to it.
5. Manually run **Deploy Core**.
6. Read the resulting Core URL and set `HAMMERLY_API_URL` to `CORE_URL/api`.
7. Run **Deploy Frontend** or push a UI change through CI.
8. Confirm Core `/health`, AI `/actuator/health`, and the browser-to-Core flow.

Subsequent relevant pushes to `main` run the affected CI job first and then call only that service's
deployment workflow. Core and AI use stable configured URLs; no generated URL is written into Java
or workflow source. A manual CI run validates only and never deploys.

The common `_deploy-cloud-run.yml` workflow authenticates, builds the multi-stage image, pushes it,
deploys the immutable commit tag, and checks health. Automatic deployment does not repeat the full
Maven suite because CI has already passed; the Docker build still performs
`mvn package -DskipTests` as compile/package validation. Direct manual Deploy AI/Core runs use that
same Docker validation.

`Publish Worker Image` is `workflow_dispatch` only. It builds and pushes the Worker image but never
starts a Worker runtime. The obsolete GKE deployment and Phase 5 load-test workflows were removed
from GitHub Actions; their manifests, scripts, results, and documentation remain available locally.

## 9. Seed and verify the RAG knowledge document

The canonical portfolio document is `docs/knowledge-base/hammerly-support.md`. Ingestion sends this
document to the configured embedding provider. Start Kafka and a current Worker runtime before
submitting it; otherwise the durable outbox row correctly remains pending and the document cannot
become `READY`.

With the demo infrastructure enabled and the Worker image deployed, run:

```powershell
.\scripts\knowledge\ingest-support.ps1 `
  -BaseUrl 'https://YOUR-CORE-SERVICE' `
  -InternalToken $env:HAMMERLY_AI_INTERNAL_TOKEN
```

The script waits until the document is `READY`. For a direct read-only production verification,
set `SUPABASE_DB_URL` in the current shell and run:

```powershell
.\scripts\knowledge\verify-rag-status.ps1
```

The status check prints document state, chunk count, citable section labels, knowledge-base version,
and aggregate unpublished outbox status. It never prints document content or database credentials.

## 10. Kafka demo infrastructure lifecycle

The Kafka lifecycle is deliberately manual. It does not create a GitHub Actions workflow, change the
existing CI-to-CD gates, or couple Redis to Kafka. `enable-demo-infra.ps1` reads the deployed AI
configuration only to verify that the existing Upstash host, port `6379`, TLS flag, and
`hammerly-redis-password` secret reference are present. It never reads the Redis password and never
creates, updates, stops, or deletes a Memorystore or Upstash resource.

### Billable resources and preview

The defaults are:

- Compute Engine VM `hammerly-kafka` in `us-west1-b`: `e2-small` (2 shared vCPU, 2 GiB RAM), 20 GB
  `pd-standard` boot disk, Ubuntu 24.04, Apache Kafka 3.9.1, one combined KRaft broker/controller;
- Cloud Run Worker Pool `hammerly-worker`: one continuously active 1 vCPU / 1 GiB instance;
- no ZooKeeper, Cloud NAT, Memorystore, GKE, public Kafka address, or Cloud Run minimum-instance
  change.

The machine type can be overridden with `$env:KAFKA_MACHINE_TYPE` or `-KafkaMachineType`; the disk
size/type and worker count also have explicit parameters. Always run the read-only preview first:

```powershell
.\scripts\gcp\enable-demo-infra.ps1 -WhatIf
```

The script prints the exact VM, disk, and worker-pool size before PowerShell asks for confirmation.
Creating the VM or scaling the worker above zero is billable. At current
[Compute Engine list prices](https://cloud.google.com/products/compute/pricing/general-purpose),
`e2-small` is about `$0.01675/hour` (roughly `$12.23` for a 730-hour month before
sustained-use/free-tier effects), and the
[disk price](https://cloud.google.com/compute/disks-image-pricing) for a 20 GB US standard persistent
disk is up to about `$0.80/month` when the account-level free disk allowance is already consumed.
Google's [Cloud Run pricing example](https://cloud.google.com/run/pricing) currently illustrates one
always-on 1 vCPU / 512 MiB Worker Pool at about `$11.61/month` after its free tier (`$16.83` without
it); Hammerly allocates 1 GiB, so budget somewhat more and use the pricing calculator for the billing
account's exact result. Stopping Kafka ends VM CPU charges but retains disk charges. Scaling the
worker to zero ends worker compute charges.

### Enable Kafka

Publish a current worker image with **Publish Worker Image**, preview, then enable:

```powershell
.\scripts\gcp\enable-demo-infra.ps1 -WhatIf
.\scripts\gcp\enable-demo-infra.ps1
```

The idempotent ON workflow:

1. verifies the existing Upstash/TLS configuration and required Secret Manager containers without
   reading or changing their values;
2. creates three target-tagged firewall rules: allow TCP `9092` only from the regional subnet CIDR,
   deny TCP `9092` from every other source (needed because the default VPC has a broader internal
   allow rule), and allow IAP SSH for private administration;
3. creates `hammerly-kafka` only when absent, otherwise starts it when stopped;
4. installs Kafka once, formats a single-node KRaft log only when unformatted, writes a private-IP
   `advertised.listeners`, and runs Kafka under an auto-restarting `systemd` unit;
5. creates or verifies `hammerly.ai.events.v1`, `hammerly.ai.jobs.v1`, and both `.DLT` topics with
   three partitions and replication factor `1`;
6. removes the VM's temporary first-boot external IPv4 address after broker health succeeds;
7. writes the real private `<ip>:9092` to the `KAFKA_BOOTSTRAP_SERVERS` GitHub variable, sets
   `HAMMERLY_KAFKA_ENABLED=true`, and updates only those Kafka fields plus existing Direct VPC egress
   on Core and AI; it does not alter Cloud Run minimum instances or AI/Redis/RAG behavior;
8. creates or reuses `hammerly-worker-runtime`, grants it accessor rights only to the existing Redis,
   Supabase URL, and OpenAI secrets, and creates/updates the private Worker Pool from
   `hammerly-worker:latest`;
9. verifies broker metadata, the topic list, Worker Pool scaling, active consumer group
   `hammerly-worker-v1`, and consumption of one non-business smoke event; unless
   `-SkipApplicationSmokeTest` is specified, it also verifies a real AI response followed by the two
   asynchronous `message.created` events.

The smoke event contains no customer or auction data and only creates the worker's normal temporary
Redis idempotency marker. The AI smoke uses the existing internal token without printing it. Core's
durable producer is validated by its compiled transactional outbox tests and live configuration;
the lifecycle script intentionally does not insert a fake production knowledge document or outbox
row.

### Disable or delete Kafka

Disable the worker and broker while retaining the VM and disk:

```powershell
.\scripts\gcp\disable-demo-infra.ps1
```

The OFF workflow first scales the Worker Pool to zero, then sets only
`HAMMERLY_KAFKA_ENABLED=false` and `KAFKA_BOOTSTRAP_SERVERS=kafka-disabled.invalid:9092` on Core, AI,
and the GitHub repository variables, and finally stops the Kafka VM. Upstash, Supabase, the worker
image, both Cloud Run services, their min-instance settings, and Kafka disk data are untouched.

Permanent Kafka deletion is separate and explicit:

```powershell
.\scripts\gcp\disable-demo-infra.ps1 -DeleteKafka
```

`-DeleteKafka` deletes the VM and its auto-delete boot disk after the safe shutdown sequence. Kafka
data is not recoverable unless separately backed up. It still does not touch Upstash, Supabase, Core,
AI, or the Worker Pool definition.

### Inspect status

```powershell
gcloud compute instances describe hammerly-kafka `
  --project=hammerly-506214 --zone=us-west1-b

gcloud compute ssh hammerly-kafka `
  --project=hammerly-506214 --zone=us-west1-b --tunnel-through-iap `
  --command="sudo systemctl status kafka --no-pager"

gcloud compute ssh hammerly-kafka `
  --project=hammerly-506214 --zone=us-west1-b --tunnel-through-iap `
  --command="sudo /opt/kafka/bin/kafka-topics.sh --bootstrap-server 127.0.0.1:9092 --list"

gcloud compute ssh hammerly-kafka `
  --project=hammerly-506214 --zone=us-west1-b --tunnel-through-iap `
  --command="sudo /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server 127.0.0.1:9092 --describe --group hammerly-worker-v1"

gcloud run worker-pools describe hammerly-worker `
  --project=hammerly-506214 --region=us-west1
```

## 11. Worker runtime

`deploy-worker.yml` remains manual-only and publishes `hammerly-worker:<git-sha>` and `latest` to
Artifact Registry; it never deploys a runtime. The full embedded-Kafka suite remains in CI. The ON
script deploys the image as a Cloud Run Worker Pool because a Kafka consumer is a continuous
pull-based process, not a public HTTP service. Direct VPC egress attaches the worker to
`default/default`, and instances can be set to zero without deleting the pool.

The Worker Pool receives `KAFKA_BOOTSTRAP_SERVERS`, the existing topic/group variables, Upstash over
TLS, the Supabase JDBC URL secret, and the OpenAI secret for production embeddings. No password or
long-lived service-account key is written to source, YAML, command output, or GitHub variables.

## 12. Branch protection

After the `CI` workflow has run once, open **GitHub → Settings → Branches → Add branch protection rule**:

1. Branch name pattern: `main`.
2. Enable **Require a pull request before merging**.
3. Enable **Require status checks to pass before merging**.
4. Select `UI CI`, `Core CI`, `AI CI`, and `Worker CI`.
5. Enable **Require branches to be up to date before merging** if that matches the team's merge policy.
6. Save the rule.

Path-unaffected service jobs are reported as skipped; the changed services run their full checks. The repository does not change branch protection automatically.
