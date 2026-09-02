[CmdletBinding(SupportsShouldProcess = $true, ConfirmImpact = "High")]
param(
    [string]$ProjectId = "hammerly-506214",
    [string]$Region = "us-west1",
    [string]$CoreService = "hammerly-backend",
    [string]$AiService = "hammerly-ai",
    [string]$WorkerPool = "hammerly-worker",
    [string]$KafkaVm = "hammerly-kafka",
    [string]$GitHubRepository = "jqiwen/Hammerly",
    [switch]$SkipGitHubVariables,
    [switch]$DeleteKafka
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Invoke-Gcloud {
    param([Parameter(Mandatory = $true)][string[]]$Arguments)

    $previousWhatIf = $WhatIfPreference
    try {
        $WhatIfPreference = $false
        $output = @(& gcloud @Arguments 2>&1)
        if ($LASTEXITCODE -ne 0) {
            $message = ($output | ForEach-Object { "$_" }) -join [Environment]::NewLine
            throw "gcloud command failed: gcloud $($Arguments -join ' ')`n$message"
        }
        return (($output | ForEach-Object { "$_" }) -join [Environment]::NewLine).Trim()
    } finally {
        $WhatIfPreference = $previousWhatIf
    }
}

function Test-GcloudResource {
    param([Parameter(Mandatory = $true)][string[]]$Arguments)

    $previousWhatIf = $WhatIfPreference
    try {
        $WhatIfPreference = $false
        & gcloud @Arguments *> $null
        return $LASTEXITCODE -eq 0
    } finally {
        $WhatIfPreference = $previousWhatIf
    }
}

function Get-KafkaVmDetails {
    $json = Invoke-Gcloud -Arguments @(
        "compute", "instances", "list",
        "--project=$ProjectId",
        "--format=json(name,zone,status,machineType,disks)"
    )
    $matches = @($json | ConvertFrom-Json | Where-Object { $_.name -eq $KafkaVm })
    if ($matches.Count -gt 1) {
        throw "More than one Compute Engine VM is named '$KafkaVm'."
    }
    if ($matches.Count -eq 0) {
        return $null
    }
    $item = $matches[0]
    return [pscustomobject]@{
        Name = $item.name
        Zone = ($item.zone -split "/")[-1]
        Status = $item.status
        MachineType = ($item.machineType -split "/")[-1]
    }
}

function Set-GitHubVariable {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string]$Value
    )

    $output = @(& gh variable set $Name --repo $GitHubRepository --body $Value 2>&1)
    if ($LASTEXITCODE -ne 0) {
        throw "Could not update GitHub repository variable '$Name': $($output -join [Environment]::NewLine)"
    }
}

if (-not (Get-Command gcloud -ErrorAction SilentlyContinue)) {
    throw "gcloud is required and must be available on PATH."
}
if (-not $SkipGitHubVariables -and -not (Get-Command gh -ErrorAction SilentlyContinue)) {
    throw "gh is required to persist the disabled Kafka feature flag; use -SkipGitHubVariables only when intentionally managing it separately."
}

Invoke-Gcloud -Arguments @("projects", "describe", $ProjectId, "--format=value(projectId)") | Out-Null
$workerExists = Test-GcloudResource -Arguments @(
    "run", "worker-pools", "describe", $WorkerPool,
    "--project=$ProjectId", "--region=$Region"
)
$kafka = Get-KafkaVmDetails

Write-Host "Disable plan (Upstash, Supabase, Core, and AI resources are retained):"
Write-Host "  Scale worker pool '$WorkerPool' to 0 if it exists: $workerExists"
Write-Host "  Set HAMMERLY_KAFKA_ENABLED=false and KAFKA_BOOTSTRAP_SERVERS=kafka-disabled.invalid:9092 on '$CoreService', '$AiService', and in GitHub variables."
if ($null -eq $kafka) {
    Write-Host "  Kafka VM '$KafkaVm' does not exist."
} elseif ($DeleteKafka) {
    Write-Warning "  DELETE Kafka VM '$KafkaVm' in $($kafka.Zone), including its auto-delete boot disk and Kafka data."
} else {
    Write-Host "  Stop Kafka VM '$KafkaVm' in $($kafka.Zone); keep the VM and disk."
}

$action = if ($DeleteKafka) {
    "disable Kafka and permanently delete the Kafka VM and its auto-delete boot disk"
} else {
    "disable Kafka, scale the worker to zero, and stop the Kafka VM while retaining its disk"
}
if (-not $PSCmdlet.ShouldProcess("$ProjectId/$Region", $action)) {
    return
}

if ($workerExists) {
    Invoke-Gcloud -Arguments @(
        "run", "worker-pools", "update", $WorkerPool,
        "--project=$ProjectId", "--region=$Region", "--instances=0", "--quiet"
    ) | Write-Host
}

foreach ($service in @($CoreService, $AiService)) {
    if (Test-GcloudResource -Arguments @(
        "run", "services", "describe", $service,
        "--project=$ProjectId", "--region=$Region"
    )) {
        Invoke-Gcloud -Arguments @(
            "run", "services", "update", $service,
            "--project=$ProjectId", "--region=$Region",
            "--update-env-vars=HAMMERLY_KAFKA_ENABLED=false,KAFKA_BOOTSTRAP_SERVERS=kafka-disabled.invalid:9092", "--quiet"
        ) | Write-Host
    }
}

if (-not $SkipGitHubVariables) {
    Set-GitHubVariable -Name "HAMMERLY_KAFKA_ENABLED" -Value "false"
    Set-GitHubVariable -Name "KAFKA_BOOTSTRAP_SERVERS" -Value "kafka-disabled.invalid:9092"
}

if ($null -ne $kafka -and $kafka.Status -ne "TERMINATED") {
    Invoke-Gcloud -Arguments @(
        "compute", "instances", "stop", $KafkaVm,
        "--project=$ProjectId", "--zone=$($kafka.Zone)", "--quiet"
    ) | Write-Host
}

if ($DeleteKafka -and $null -ne $kafka) {
    Invoke-Gcloud -Arguments @(
        "compute", "instances", "delete", $KafkaVm,
        "--project=$ProjectId", "--zone=$($kafka.Zone)", "--delete-disks=all", "--quiet"
    ) | Write-Host
    Write-Warning "Kafka VM '$KafkaVm' and its boot disk were deleted. Kafka data is not recoverable unless separately backed up."
}

Write-Host "Kafka demo infrastructure is OFF."
Write-Host "Worker pool: zero instances (or absent)"
Write-Host $(if ($DeleteKafka) { "Kafka VM: deleted when present" } else { "Kafka VM: stopped when present; disk retained" })
Write-Host "Upstash Redis: untouched"
Write-Host "Supabase and Cloud Run services: retained"
