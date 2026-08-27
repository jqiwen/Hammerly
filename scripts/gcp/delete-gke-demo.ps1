[CmdletBinding(SupportsShouldProcess = $true, ConfirmImpact = 'High')]
param(
    [string]$ProjectId = 'hammerly-506214',
    [string]$Region = 'us-west1',
    [string]$ClusterName = 'hammerly-gke'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Test-Cluster {
    & gcloud container clusters describe $ClusterName `
        --project=$ProjectId --region=$Region *> $null
    return $LASTEXITCODE -eq 0
}

if (-not (Test-Cluster)) {
    Write-Host "Cluster '$ClusterName' is already absent; nothing to delete."
    return
}

$clusterResource = "projects/$ProjectId/locations/$Region/clusters/$ClusterName"
if (-not $PSCmdlet.ShouldProcess(
    $clusterResource,
    'delete the Hammerly namespace, load balancer, persistent disks, and GKE cluster'
)) {
    return
}

& gcloud container clusters get-credentials $ClusterName `
    --project=$ProjectId --region=$Region *> $null
if ($LASTEXITCODE -eq 0) {
    & kubectl delete namespace hammerly --ignore-not-found=true --wait=true --timeout=10m
    if ($LASTEXITCODE -ne 0) {
        throw 'Namespace deletion did not complete; refusing to delete the cluster before load balancer/PVC cleanup.'
    }
}

& gcloud container clusters delete $ClusterName `
    --project=$ProjectId --region=$Region --quiet
if ($LASTEXITCODE -ne 0) {
    throw "Failed to delete cluster '$ClusterName'."
}

Write-Host "Deleted GKE cluster '$ClusterName' and the Hammerly namespace it contained."
Write-Host 'Artifact Registry images, Secret Manager versions, Cloud Run, Supabase, and GitHub Pages were intentionally retained.'

