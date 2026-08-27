[CmdletBinding(SupportsShouldProcess = $true, ConfirmImpact = 'Medium')]
param(
    [string]$ProjectId = 'hammerly-506214',
    [string]$Region = 'us-west1',
    [string]$ClusterName = 'hammerly-gke'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Invoke-Gcloud {
    param([Parameter(Mandatory = $true)][string[]]$Arguments)

    $output = @(& gcloud @Arguments 2>&1)
    if ($LASTEXITCODE -ne 0) {
        throw "gcloud command failed: gcloud $($Arguments -join ' ')`n$($output -join [Environment]::NewLine)"
    }
    return (($output | ForEach-Object { "$_" }) -join [Environment]::NewLine).Trim()
}

function Test-Cluster {
    & gcloud container clusters describe $ClusterName `
        --project=$ProjectId --region=$Region *> $null
    return $LASTEXITCODE -eq 0
}

if (-not (Get-Command gcloud -ErrorAction SilentlyContinue)) {
    throw 'gcloud is required and must be available on PATH.'
}

Invoke-Gcloud -Arguments @('projects', 'describe', $ProjectId, '--format=value(projectId)') | Out-Null
Invoke-Gcloud -Arguments @(
    'services', 'enable', 'container.googleapis.com', 'artifactregistry.googleapis.com',
    'secretmanager.googleapis.com', 'compute.googleapis.com',
    "--project=$ProjectId", '--quiet'
) | Out-Null

if (Test-Cluster) {
    $autopilot = Invoke-Gcloud -Arguments @(
        'container', 'clusters', 'describe', $ClusterName,
        "--project=$ProjectId", "--region=$Region", '--format=value(autopilot.enabled)'
    )
    Write-Host "Cluster '$ClusterName' already exists (Autopilot=$autopilot)."
} elseif ($PSCmdlet.ShouldProcess(
    "GKE Autopilot cluster $ClusterName in $ProjectId/$Region",
    'create cost-controlled Phase 8 demo cluster'
)) {
    Invoke-Gcloud -Arguments @(
        'container', 'clusters', 'create-auto', $ClusterName,
        "--project=$ProjectId", "--region=$Region",
        '--release-channel=regular', '--quiet'
    ) | Write-Host
}

if (-not $WhatIfPreference) {
    Invoke-Gcloud -Arguments @(
        'container', 'clusters', 'get-credentials', $ClusterName,
        "--project=$ProjectId", "--region=$Region"
    ) | Out-Null
    & kubectl cluster-info
    if ($LASTEXITCODE -ne 0) {
        throw "kubectl could not reach cluster '$ClusterName'."
    }
    Write-Host "GKE demo cluster is ready. Deploy with scripts/gcp/deploy-gke-demo.ps1."
}

