# Hammerly Google Cloud deployment setup

The repository contains prepared deployment workflows, but it does not contain a Google Cloud project, managed Redis, production Kafka broker, or credentials. Complete this setup once before setting `GCP_DEPLOYMENTS_ENABLED=true`.

Production secret values stay in Google Secret Manager. GitHub stores only non-secret resource identifiers and secret **names**. GitHub authenticates to Google Cloud with short-lived OIDC credentials through Workload Identity Federation; do not create or upload a service-account JSON key.

## 1. Choose the project and region

Run these commands in Google Cloud Shell. Replace the project ID, but keep the other names unless you intentionally configure matching GitHub variables later.

```bash
export HAMMERLY_PROJECT_ID="your-gcp-project-id"
export HAMMERLY_REGION="us-central1"
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
for HAMMERLY_SECRET_NAME in hammerly-supabase-db-url hammerly-jwt-secret hammerly-ai-internal-token; do
  gcloud secrets add-iam-policy-binding "${HAMMERLY_SECRET_NAME}" \
    --member="serviceAccount:${HAMMERLY_CORE_RUNTIME_EMAIL}" \
    --role="roles/secretmanager.secretAccessor"
done

for HAMMERLY_SECRET_NAME in hammerly-openai-api-key hammerly-redis-password hammerly-ai-internal-token; do
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

## 6. Configure production Redis before AI deployment

Provision a production Redis endpoint separately. Do not deploy the development Compose container. Ensure Cloud Run can route to the endpoint; private Memorystore normally requires appropriate VPC egress configuration that is specific to your project and therefore is not created by these workflows.

Record only these non-secret connection settings in GitHub variables:

```text
AI_REDIS_HOST
AI_REDIS_PORT
AI_REDIS_SSL
```

The password remains in `hammerly-redis-password` in Secret Manager.

## 7. Configure GitHub

Create a GitHub environment named `production`. Optionally require a reviewer for deployments. Then add these repository variables under **Settings → Secrets and variables → Actions → Variables**:

```text
GCP_DEPLOYMENTS_ENABLED=false
GCP_PROJECT_ID
GCP_REGION
GCP_WORKLOAD_IDENTITY_PROVIDER
GCP_DEPLOY_SERVICE_ACCOUNT
GCP_ARTIFACT_REPOSITORY=hammerly

CORE_CLOUD_RUN_SERVICE=hammerly-core
CORE_RUNTIME_SERVICE_ACCOUNT
CORE_DB_SECRET=hammerly-supabase-db-url
CORE_JWT_SECRET=hammerly-jwt-secret

AI_CLOUD_RUN_SERVICE=hammerly-ai
AI_RUNTIME_SERVICE_ACCOUNT
AI_OPENAI_SECRET=hammerly-openai-api-key
AI_REDIS_PASSWORD_SECRET=hammerly-redis-password
AI_INTERNAL_TOKEN_SECRET=hammerly-ai-internal-token
AI_REDIS_HOST
AI_REDIS_PORT
AI_REDIS_SSL

HAMMERLY_FRONTEND_URL=https://hammerly.jqiwen.com
HAMMERLY_AI_URL
HAMMERLY_API_URL

HAMMERLY_KAFKA_ENABLED=false
KAFKA_BOOTSTRAP_SERVERS
```

Set the service-account variables to their full email addresses. No GitHub Action secret is required for GCP credentials or application runtime secrets.

## 8. First deployment order

1. Leave `HAMMERLY_KAFKA_ENABLED=false`; there is no production Kafka deployment in this repository.
2. Set `GCP_DEPLOYMENTS_ENABLED=true`.
3. Manually run **Deploy AI** from GitHub Actions.
4. Read the resulting AI Cloud Run URL and set `HAMMERLY_AI_URL` to it.
5. Manually run **Deploy Core**.
6. Read the resulting Core URL and set `HAMMERLY_API_URL` to `CORE_URL/api`.
7. Run **Deploy frontend to GitHub Pages** or push a UI change.
8. Confirm Core `/health`, AI `/actuator/health`, and the browser-to-Core flow.

Subsequent relevant pushes to `main` deploy each service independently. Core and AI use stable configured URLs; no generated URL is written into Java or workflow source.

## 9. Worker production decision

`deploy-worker.yml` tests the worker and publishes `hammerly-worker:<git-sha>` and `latest` to Artifact Registry. It intentionally does not start a runtime.

Before enabling a worker runtime, choose and provision:

- a production Kafka provider and authentication mechanism;
- Redis reachable from the chosen runtime;
- networking and secret bindings;
- a continuous worker target such as a Cloud Run worker pool with instance-based billing, or an existing GKE/VM runtime;
- an explicit minimum capacity and shutdown/rebalance test.

An ordinary request-driven Cloud Run service with scale-to-zero is not an acceptable Kafka-consumer deployment. Do not enable `HAMMERLY_KAFKA_ENABLED` in AI until the broker and consumer runtime are ready.

## 10. Branch protection

After the `CI` workflow has run once, open **GitHub → Settings → Branches → Add branch protection rule**:

1. Branch name pattern: `main`.
2. Enable **Require a pull request before merging**.
3. Enable **Require status checks to pass before merging**.
4. Select `UI CI`, `Core CI`, `AI CI`, and `Worker CI`.
5. Enable **Require branches to be up to date before merging** if that matches the team's merge policy.
6. Save the rule.

Path-unaffected service jobs are reported as skipped; the changed services run their full checks. The repository does not change branch protection automatically.
