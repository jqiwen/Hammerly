[CmdletBinding(SupportsShouldProcess = $true, ConfirmImpact = "Medium")]
param(
    [string]$ProjectId = "hammerly-506214",
    [string]$Region = "us-west1",
    [string]$AiService = "hammerly-ai",
    [string]$WorkerPool = "hammerly-worker",
    [string]$KafkaVm = "hammerly-kafka",
    [string]$RedisInstance = "hammerly-redis",
    [string]$InternalTokenSecret = "hammerly-ai-internal-token",
    [switch]$DeleteRedis
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Invoke-Gcloud {
    param([Parameter(Mandatory = $true)][string[]]$Arguments)

    $output = @(& gcloud @Arguments 2>&1)
    if ($LASTEXITCODE -ne 0) {
        $message = ($output | ForEach-Object { "$_" }) -join [Environment]::NewLine
        throw "gcloud command failed: gcloud $($Arguments -join ' ')`n$message"
    }
    return (($output | ForEach-Object { "$_" }) -join [Environment]::NewLine).Trim()
}

function Test-GcloudResource {
    param([Parameter(Mandatory = $true)][string[]]$Arguments)

    & gcloud @Arguments *> $null
    return $LASTEXITCODE -eq 0
}

function Invoke-GcloudSensitive {
    param([Parameter(Mandatory = $true)][string[]]$Arguments)

    $output = @(& gcloud @Arguments 2>$null)
    if ($LASTEXITCODE -ne 0) {
        throw "A sensitive gcloud read failed: gcloud $($Arguments -join ' ')"
    }
    return (($output | ForEach-Object { "$_" }) -join [Environment]::NewLine).Trim()
}

function Get-KafkaVmDetails {
    $json = Invoke-Gcloud -Arguments @(
        "compute", "instances", "list",
        "--project=$ProjectId",
        "--filter=name=$KafkaVm",
        "--format=json(name,zone,status,networkInterfaces)"
    )
    $matches = @($json | ConvertFrom-Json | Where-Object { $_.name -eq $KafkaVm })
    if ($matches.Count -gt 1) {
        throw "More than one Compute Engine VM is named '$KafkaVm'. Specify an unambiguous VM."
    }
    if ($matches.Count -eq 0) {
        return $null
    }
    $item = $matches[0]
    return [pscustomobject]@{
        Name = $item.name
        Zone = ($item.zone -split "/")[-1]
        Status = $item.status
    }
}

function Get-AiUrl {
    $url = Invoke-Gcloud -Arguments @(
        "run", "services", "describe", $AiService,
        "--project=$ProjectId", "--region=$Region",
        "--format=value(status.url)"
    )
    if ([string]::IsNullOrWhiteSpace($url)) {
        throw "Cloud Run service '$AiService' has no URL."
    }
    return $url.TrimEnd("/")
}

function Get-AiHeaders {
    $token = Invoke-GcloudSensitive -Arguments @(
        "secrets", "versions", "access", "latest",
        "--secret=$InternalTokenSecret", "--project=$ProjectId"
    )
    $headers = @{ "X-Hammerly-User-Id" = "demo-infra-verifier" }
    if (-not [string]::IsNullOrWhiteSpace($token)) {
        $headers["X-Hammerly-Internal-Token"] = $token
    }
    return $headers
}

function Wait-AiHealth {
    param([Parameter(Mandatory = $true)][string]$AiUrl)

    $lastError = "no response"
    for ($attempt = 1; $attempt -le 18; $attempt++) {
        try {
            $health = Invoke-RestMethod -Method Get -Uri "$AiUrl/actuator/health" -TimeoutSec 15
            if ($health.status -eq "UP") {
                return
            }
            $lastError = "status was '$($health.status)'"
        } catch {
            $lastError = $_.Exception.Message
        }
        Start-Sleep -Seconds 5
    }
    throw "AI health verification failed: $lastError"
}

function Confirm-AiDemoOff {
    param(
        [Parameter(Mandatory = $true)][string]$AiUrl,
        [Parameter(Mandatory = $true)][hashtable]$Headers
    )

    $status = Invoke-RestMethod -Method Get -Uri "$AiUrl/internal/ai/status" `
        -Headers $Headers -TimeoutSec 15
    if ($null -eq $status.redisEnabled -or $null -eq $status.kafkaEnabled) {
        throw "The deployed AI revision does not report demo infrastructure mode. Deploy this change before deleting Redis."
    }
    if ([bool]$status.redisEnabled -or [bool]$status.kafkaEnabled) {
        throw "AI did not enter demo-off mode (redisEnabled=$($status.redisEnabled), kafkaEnabled=$($status.kafkaEnabled))."
    }

    $body = @{
        message = "Reply briefly to confirm Hammerly AI chat is available."
        history = @()
        conversationId = [guid]::NewGuid().ToString()
    } | ConvertTo-Json -Compress
    $chat = Invoke-RestMethod -Method Post -Uri "$AiUrl/internal/ai/chat" `
        -Headers $Headers -ContentType "application/json" -Body $body -TimeoutSec 90
    if ($null -eq $chat -or [string]::IsNullOrWhiteSpace([string]$chat.answer)) {
        throw "AI chat verification returned no answer. Redis will not be deleted."
    }
}

if (-not (Get-Command gcloud -ErrorAction SilentlyContinue)) {
    throw "gcloud is required and must be available on PATH."
}

Invoke-Gcloud -Arguments @("projects", "describe", $ProjectId, "--format=value(projectId)") | Out-Null
if (-not (Test-GcloudResource -Arguments @(
    "run", "services", "describe", $AiService,
    "--project=$ProjectId", "--region=$Region"
))) {
    throw "Cloud Run AI service '$AiService' was not found in $ProjectId/$Region."
}

if ($PSCmdlet.ShouldProcess(
    "Cloud Run service $AiService",
    "set HAMMERLY_REDIS_ENABLED=false and HAMMERLY_KAFKA_ENABLED=false"
)) {
    Invoke-Gcloud -Arguments @(
        "run", "services", "update", $AiService,
        "--project=$ProjectId", "--region=$Region",
        "--update-env-vars=HAMMERLY_REDIS_ENABLED=false,HAMMERLY_KAFKA_ENABLED=false",
        "--quiet"
    ) | Write-Host
}

if (Test-GcloudResource -Arguments @(
    "run", "worker-pools", "describe", $WorkerPool,
    "--project=$ProjectId", "--region=$Region"
)) {
    if ($PSCmdlet.ShouldProcess("Cloud Run worker pool $WorkerPool", "scale to zero instances")) {
        Invoke-Gcloud -Arguments @(
            "run", "worker-pools", "update", $WorkerPool,
            "--project=$ProjectId", "--region=$Region", "--instances=0", "--quiet"
        ) | Write-Host
    }
} else {
    Write-Host "Worker pool '$WorkerPool' is absent; nothing to disable."
}

$kafka = Get-KafkaVmDetails
if ($null -eq $kafka) {
    Write-Host "Kafka VM '$KafkaVm' is absent; nothing to stop."
} elseif ($kafka.Status -eq "TERMINATED") {
    Write-Host "Kafka VM '$KafkaVm' is already stopped."
} elseif ($PSCmdlet.ShouldProcess("Compute Engine VM $KafkaVm", "stop")) {
    Invoke-Gcloud -Arguments @(
        "compute", "instances", "stop", $KafkaVm,
        "--project=$ProjectId", "--zone=$($kafka.Zone)", "--quiet"
    ) | Write-Host
}

if ($WhatIfPreference) {
    Write-Host "WhatIf complete. Live verification and Redis deletion were intentionally skipped."
    return
}

$aiUrl = Get-AiUrl
$headers = Get-AiHeaders
Wait-AiHealth -AiUrl $aiUrl
Confirm-AiDemoOff -AiUrl $aiUrl -Headers $headers
Write-Host "Verified AI health and chat with Redis/Kafka explicitly disabled."

$redisExists = Test-GcloudResource -Arguments @(
    "redis", "instances", "describe", $RedisInstance,
    "--project=$ProjectId", "--region=$Region"
)
if (-not $DeleteRedis) {
    if ($redisExists) {
        Write-Host "Redis '$RedisInstance' is still present. Re-run with -DeleteRedis to remove it after this safety gate."
    } else {
        Write-Host "Redis '$RedisInstance' is already absent."
    }
    return
}

if (-not $redisExists) {
    Write-Host "Redis '$RedisInstance' is already absent; nothing to delete."
} elseif ($PSCmdlet.ShouldProcess(
    "Memorystore instance $RedisInstance in $ProjectId/$Region",
    "permanently delete after successful Redis-disabled health and chat verification"
)) {
    Invoke-Gcloud -Arguments @(
        "redis", "instances", "delete", $RedisInstance,
        "--project=$ProjectId", "--region=$Region", "--quiet"
    ) | Write-Host
    Write-Host "Deleted Redis '$RedisInstance'. Its data is not recoverable unless separately exported."
}
