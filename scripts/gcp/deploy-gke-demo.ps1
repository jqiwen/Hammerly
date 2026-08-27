[CmdletBinding()]
param(
    [ValidateSet('demo', 'loadtest')]
    [string]$Overlay = 'loadtest',
    [string]$ProjectId = 'hammerly-506214',
    [string]$Region = 'us-west1',
    [string]$ClusterName = 'hammerly-gke',
    [string]$GitSha = '',
    [string]$WorkerGitSha = '',
    [switch]$SkipSecretSync
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path

function Invoke-Gcloud {
    param([Parameter(Mandatory = $true)][string[]]$Arguments)

    $output = @(& gcloud @Arguments 2>&1)
    if ($LASTEXITCODE -ne 0) {
        throw "gcloud command failed: gcloud $($Arguments -join ' ')`n$($output -join [Environment]::NewLine)"
    }
    return (($output | ForEach-Object { "$_" }) -join [Environment]::NewLine).Trim()
}

function Assert-Image {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string]$Tag
    )

    $image = "$Region-docker.pkg.dev/$ProjectId/hammerly/$Name`:$Tag"
    & gcloud artifacts docker images describe $image --project=$ProjectId *> $null
    if ($LASTEXITCODE -ne 0) {
        throw "Immutable image '$image' does not exist. Build and push it before deployment."
    }
}

if (-not $GitSha) {
    $GitSha = (& git -C $repositoryRoot rev-parse HEAD).Trim()
    if ($LASTEXITCODE -ne 0) {
        throw 'Could not determine the application Git SHA.'
    }
}
if ($GitSha -notmatch '^[0-9a-f]{40}$') {
    throw "GitSha must be a full 40-character commit SHA; got '$GitSha'."
}
if (-not $WorkerGitSha) {
    $WorkerGitSha = $GitSha
}
if ($WorkerGitSha -notmatch '^[0-9a-f]{40}$') {
    throw "WorkerGitSha must be a full 40-character commit SHA; got '$WorkerGitSha'."
}

$overlayPath = Join-Path $repositoryRoot "k8s/overlays/$Overlay"
$namespacePath = Join-Path $repositoryRoot 'k8s/base/namespace.yaml'
$dashboardPath = Join-Path $repositoryRoot 'observability/grafana/dashboards/hammerly-overview.json'

Assert-Image -Name 'hammerly-core' -Tag $GitSha
Assert-Image -Name 'hammerly-ai' -Tag $GitSha
Assert-Image -Name 'hammerly-worker' -Tag $WorkerGitSha

Invoke-Gcloud -Arguments @(
    'container', 'clusters', 'get-credentials', $ClusterName,
    "--project=$ProjectId", "--region=$Region"
) | Out-Null

& kubectl apply -f $namespacePath
if ($LASTEXITCODE -ne 0) { throw 'Could not apply the Hammerly namespace.' }

if (-not $SkipSecretSync) {
    & (Join-Path $PSScriptRoot 'bootstrap-gke-secrets.ps1') -ProjectId $ProjectId
    if ($LASTEXITCODE -ne 0) { throw 'Secret synchronization failed.' }
}

$rendered = (& kubectl kustomize $overlayPath) -join "`n"
if ($LASTEXITCODE -ne 0) { throw "Kustomize could not render overlay '$Overlay'." }
$rendered = $rendered.Replace(':WORKER_GIT_SHA', ":$WorkerGitSha")
$rendered = $rendered.Replace(':GIT_SHA', ":$GitSha")
if ($rendered -match '(?:GIT_SHA|WORKER_GIT_SHA)') {
    throw 'Rendered Kubernetes YAML still contains an unresolved image tag placeholder.'
}
$rendered | & kubectl apply -f -
if ($LASTEXITCODE -ne 0) { throw 'Kubernetes resource application failed.' }

& kubectl create configmap grafana-dashboard-app -n hammerly `
    "--from-file=hammerly-overview.json=$dashboardPath" --dry-run=client -o yaml |
    & kubectl apply -f -
if ($LASTEXITCODE -ne 0) { throw 'Could not synchronize the Phase 6 Grafana dashboard.' }
& kubectl rollout restart deployment/grafana -n hammerly

$workloads = @(
    @('statefulset/redis', '300s'),
    @('statefulset/kafka', '600s'),
    @('deployment/hammerly-ai', '600s'),
    @('deployment/hammerly-worker', '600s'),
    @('deployment/hammerly-backend', '600s'),
    @('deployment/prometheus', '300s'),
    @('deployment/kube-state-metrics', '300s'),
    @('deployment/grafana', '300s')
)
foreach ($workload in $workloads) {
    & kubectl rollout status $workload[0] -n hammerly "--timeout=$($workload[1])"
    if ($LASTEXITCODE -ne 0) { throw "Rollout failed for $($workload[0])." }
}

& kubectl get pods,hpa,services -n hammerly -o wide
Write-Host "Deployed overlay '$Overlay' with app SHA $GitSha and worker SHA $WorkerGitSha."
Write-Host 'Use: kubectl get service hammerly-backend -n hammerly -w'
Write-Host 'Grafana: kubectl port-forward service/grafana 3001:3000 -n hammerly'

