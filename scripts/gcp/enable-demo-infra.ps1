[CmdletBinding(SupportsShouldProcess = $true, ConfirmImpact = "Medium")]
param(
    [string]$ProjectId = "hammerly-506214",
    [string]$Region = "us-west1",
    [string]$Network = "default",
    [string]$Subnet = "default",
    [string]$AiService = "hammerly-ai",
    [string]$AiServiceAccount = "",
    [string]$WorkerPool = "hammerly-worker",
    [ValidateRange(1, 20)][int]$WorkerInstances = 1,
    [string]$WorkerServiceAccount = "",
    [string]$WorkerImage = "",
    [string]$KafkaVm = "hammerly-kafka",
    [string]$RedisInstance = "hammerly-redis",
    [string]$RedisPasswordSecret = "hammerly-redis-password",
    [string]$InternalTokenSecret = "hammerly-ai-internal-token"
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
    if ($null -eq $item.networkInterfaces -or $item.networkInterfaces.Count -eq 0) {
        throw "Kafka VM '$KafkaVm' has no network interface."
    }
    return [pscustomobject]@{
        Name = $item.name
        Zone = ($item.zone -split "/")[-1]
        Status = $item.status
        PrivateIp = $item.networkInterfaces[0].networkIP
    }
}

function Wait-RedisReady {
    for ($attempt = 1; $attempt -le 60; $attempt++) {
        $state = Invoke-Gcloud -Arguments @(
            "redis", "instances", "describe", $RedisInstance,
            "--project=$ProjectId", "--region=$Region", "--format=value(state)"
        )
        if ($state -eq "READY") {
            return
        }
        if ($state -eq "FAILED") {
            throw "Redis '$RedisInstance' entered FAILED state."
        }
        Start-Sleep -Seconds 10
    }
    throw "Timed out waiting for Redis '$RedisInstance' to become READY."
}

function Wait-KafkaVmRunning {
    param([Parameter(Mandatory = $true)][string]$Zone)

    for ($attempt = 1; $attempt -le 30; $attempt++) {
        $state = Invoke-Gcloud -Arguments @(
            "compute", "instances", "describe", $KafkaVm,
            "--project=$ProjectId", "--zone=$Zone", "--format=value(status)"
        )
        if ($state -eq "RUNNING") {
            return
        }
        Start-Sleep -Seconds 5
    }
    throw "Timed out waiting for Kafka VM '$KafkaVm' to become RUNNING."
}

function Set-SecretValue {
    param(
        [Parameter(Mandatory = $true)][string]$SecretName,
        [Parameter(Mandatory = $true)][string]$Value
    )

    $secretExists = Test-GcloudResource -Arguments @(
        "secrets", "describe", $SecretName, "--project=$ProjectId"
    )
    if (-not $secretExists) {
        Invoke-Gcloud -Arguments @(
            "secrets", "create", $SecretName,
            "--project=$ProjectId", "--replication-policy=automatic", "--quiet"
        ) | Write-Host
    } elseif (Test-GcloudResource -Arguments @(
        "secrets", "versions", "access", "latest",
        "--secret=$SecretName", "--project=$ProjectId"
    )) {
        $current = Invoke-GcloudSensitive -Arguments @(
            "secrets", "versions", "access", "latest",
            "--secret=$SecretName", "--project=$ProjectId"
        )
        if ($current -ceq $Value) {
            Write-Host "Secret '$SecretName' already contains the current Redis AUTH value."
            return
        }
    }

    $tempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
    $tempFile = [IO.Path]::GetFullPath([IO.Path]::GetTempFileName())
    if (-not $tempFile.StartsWith($tempRoot, [StringComparison]::OrdinalIgnoreCase)) {
        throw "Refusing to write temporary secret material outside the OS temp directory."
    }
    try {
        [IO.File]::WriteAllText($tempFile, $Value, [Text.UTF8Encoding]::new($false))
        Invoke-Gcloud -Arguments @(
            "secrets", "versions", "add", $SecretName,
            "--project=$ProjectId", "--data-file=$tempFile", "--quiet"
        ) | Write-Host
    } finally {
        if (Test-Path -LiteralPath $tempFile) {
            Remove-Item -LiteralPath $tempFile -Force
        }
    }
}

function Get-AiUrl {
    $url = Invoke-Gcloud -Arguments @(
        "run", "services", "describe", $AiService,
        "--project=$ProjectId", "--region=$Region", "--format=value(status.url)"
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

function Confirm-AiDemoOn {
    param(
        [Parameter(Mandatory = $true)][string]$AiUrl,
        [Parameter(Mandatory = $true)][hashtable]$Headers
    )

    $status = Invoke-RestMethod -Method Get -Uri "$AiUrl/internal/ai/status" `
        -Headers $Headers -TimeoutSec 15
    if ($null -eq $status.redisEnabled -or $null -eq $status.kafkaEnabled) {
        throw "The deployed AI revision does not report demo infrastructure mode. Deploy this change first."
    }
    if (-not [bool]$status.redisEnabled -or -not [bool]$status.kafkaEnabled) {
        throw "AI did not enter demo-on mode (redisEnabled=$($status.redisEnabled), kafkaEnabled=$($status.kafkaEnabled))."
    }

    $body = @{
        message = "Reply briefly to confirm Hammerly AI chat is available."
        history = @()
        conversationId = [guid]::NewGuid().ToString()
    } | ConvertTo-Json -Compress
    $chat = Invoke-RestMethod -Method Post -Uri "$AiUrl/internal/ai/chat" `
        -Headers $Headers -ContentType "application/json" -Body $body -TimeoutSec 90
    if ($null -eq $chat -or [string]::IsNullOrWhiteSpace([string]$chat.answer)) {
        throw "AI chat verification returned no answer."
    }
}

if (-not (Get-Command gcloud -ErrorAction SilentlyContinue)) {
    throw "gcloud is required and must be available on PATH."
}
if ([string]::IsNullOrWhiteSpace($WorkerImage)) {
    $WorkerImage = "$Region-docker.pkg.dev/$ProjectId/hammerly/hammerly-worker:latest"
}
if ([string]::IsNullOrWhiteSpace($WorkerServiceAccount)) {
    $WorkerServiceAccount = "hammerly-worker-runtime@$ProjectId.iam.gserviceaccount.com"
}
if ([string]::IsNullOrWhiteSpace($AiServiceAccount)) {
    $AiServiceAccount = "hammerly-ai-runtime@$ProjectId.iam.gserviceaccount.com"
}

Invoke-Gcloud -Arguments @("projects", "describe", $ProjectId, "--format=value(projectId)") | Out-Null
if (-not (Test-GcloudResource -Arguments @(
    "run", "services", "describe", $AiService,
    "--project=$ProjectId", "--region=$Region"
))) {
    throw "Cloud Run AI service '$AiService' was not found in $ProjectId/$Region."
}

$redisExists = Test-GcloudResource -Arguments @(
    "redis", "instances", "describe", $RedisInstance,
    "--project=$ProjectId", "--region=$Region"
)
$workerExists = Test-GcloudResource -Arguments @(
    "run", "worker-pools", "describe", $WorkerPool,
    "--project=$ProjectId", "--region=$Region"
)
$kafka = Get-KafkaVmDetails
if ($null -eq $kafka) {
    throw "Kafka VM '$KafkaVm' does not exist. Create/configure it first or pass -KafkaVm with the existing VM name."
}
if (-not (Test-GcloudResource -Arguments @(
    "artifacts", "docker", "images", "describe", $WorkerImage,
    "--project=$ProjectId"
))) {
    throw "Worker image '$WorkerImage' does not exist or is not readable. Publish it before enabling demo infrastructure."
}

if ($WhatIfPreference) {
    if (-not $redisExists) {
        $PSCmdlet.ShouldProcess("Memorystore instance $RedisInstance", "create Basic 1 GiB Redis with AUTH") | Out-Null
    }
    $PSCmdlet.ShouldProcess("Compute Engine VM $KafkaVm", "start if stopped") | Out-Null
    $PSCmdlet.ShouldProcess("Cloud Run service $AiService", "configure and enable Redis/Kafka") | Out-Null
    $PSCmdlet.ShouldProcess("Cloud Run worker pool $WorkerPool", "deploy or scale to $WorkerInstances") | Out-Null
    Write-Host "WhatIf complete. No GCP resources or secrets were changed."
    return
}

if (-not $redisExists) {
    if ($PSCmdlet.ShouldProcess("Memorystore instance $RedisInstance", "create Basic 1 GiB Redis with AUTH")) {
        Invoke-Gcloud -Arguments @(
            "redis", "instances", "create", $RedisInstance,
            "--project=$ProjectId", "--region=$Region", "--network=$Network",
            "--connect-mode=DIRECT_PEERING", "--tier=BASIC", "--size=1",
            "--redis-version=redis_7_0", "--enable-auth", "--quiet"
        ) | Write-Host
    }
} else {
    Write-Host "Redis '$RedisInstance' already exists."
}
Wait-RedisReady

$redisHost = Invoke-Gcloud -Arguments @(
    "redis", "instances", "describe", $RedisInstance,
    "--project=$ProjectId", "--region=$Region", "--format=value(host)"
)
$redisPort = Invoke-Gcloud -Arguments @(
    "redis", "instances", "describe", $RedisInstance,
    "--project=$ProjectId", "--region=$Region", "--format=value(port)"
)
$redisAuth = Invoke-GcloudSensitive -Arguments @(
    "redis", "instances", "get-auth-string", $RedisInstance,
    "--project=$ProjectId", "--region=$Region", "--format=value(authString)"
)
if ([string]::IsNullOrWhiteSpace($redisHost) -or
    [string]::IsNullOrWhiteSpace($redisPort) -or
    [string]::IsNullOrWhiteSpace($redisAuth)) {
    throw "Redis '$RedisInstance' did not return a host, port, and AUTH value."
}
if ($PSCmdlet.ShouldProcess(
    "Secret Manager secret $RedisPasswordSecret",
    "store the current Redis AUTH value as a new version when needed"
)) {
    Set-SecretValue -SecretName $RedisPasswordSecret -Value $redisAuth
}
if ($PSCmdlet.ShouldProcess(
    "Secret Manager secret $RedisPasswordSecret",
    "grant accessor to $AiServiceAccount"
)) {
    Invoke-Gcloud -Arguments @(
        "secrets", "add-iam-policy-binding", $RedisPasswordSecret,
        "--project=$ProjectId",
        "--member=serviceAccount:$AiServiceAccount",
        "--role=roles/secretmanager.secretAccessor", "--quiet"
    ) | Out-Null
}

if ($kafka.Status -ne "RUNNING") {
    if ($PSCmdlet.ShouldProcess("Compute Engine VM $KafkaVm", "start")) {
        Invoke-Gcloud -Arguments @(
            "compute", "instances", "start", $KafkaVm,
            "--project=$ProjectId", "--zone=$($kafka.Zone)", "--quiet"
        ) | Write-Host
    }
} else {
    Write-Host "Kafka VM '$KafkaVm' is already running."
}
Wait-KafkaVmRunning -Zone $kafka.Zone
$kafka = Get-KafkaVmDetails
if ($null -eq $kafka -or [string]::IsNullOrWhiteSpace($kafka.PrivateIp)) {
    throw "Kafka VM '$KafkaVm' has no private IP address."
}
$kafkaBootstrap = "$($kafka.PrivateIp):9092"

if ($PSCmdlet.ShouldProcess("Cloud Run service $AiService", "configure private addresses and enable Redis/Kafka")) {
    Invoke-Gcloud -Arguments @(
        "run", "services", "update", $AiService,
        "--project=$ProjectId", "--region=$Region",
        "--network=$Network", "--subnet=$Subnet", "--vpc-egress=private-ranges-only",
        "--update-env-vars=HAMMERLY_REDIS_ENABLED=true,HAMMERLY_KAFKA_ENABLED=true,REDIS_HOST=$redisHost,REDIS_PORT=$redisPort,REDIS_SSL=false,KAFKA_BOOTSTRAP_SERVERS=$kafkaBootstrap",
        "--update-secrets=REDIS_PASSWORD=$RedisPasswordSecret`:latest",
        "--quiet"
    ) | Write-Host
}

if (-not (Test-GcloudResource -Arguments @(
    "iam", "service-accounts", "describe", $WorkerServiceAccount, "--project=$ProjectId"
))) {
    $accountId = ($WorkerServiceAccount -split "@")[0]
    if ($PSCmdlet.ShouldProcess("service account $WorkerServiceAccount", "create worker runtime identity")) {
        Invoke-Gcloud -Arguments @(
            "iam", "service-accounts", "create", $accountId,
            "--project=$ProjectId", "--display-name=Hammerly worker runtime", "--quiet"
        ) | Write-Host
    }
}
if ($PSCmdlet.ShouldProcess(
    "Secret Manager secret $RedisPasswordSecret",
    "grant accessor to $WorkerServiceAccount"
)) {
    Invoke-Gcloud -Arguments @(
        "secrets", "add-iam-policy-binding", $RedisPasswordSecret,
        "--project=$ProjectId",
        "--member=serviceAccount:$WorkerServiceAccount",
        "--role=roles/secretmanager.secretAccessor", "--quiet"
    ) | Out-Null
}

$workerArguments = @(
    "--project=$ProjectId", "--region=$Region", "--image=$WorkerImage",
    "--service-account=$WorkerServiceAccount", "--instances=$WorkerInstances",
    "--cpu=1", "--memory=1Gi", "--network=$Network", "--subnet=$Subnet",
    "--vpc-egress=private-ranges-only",
    "--update-env-vars=REDIS_HOST=$redisHost,REDIS_PORT=$redisPort,REDIS_SSL=false,KAFKA_BOOTSTRAP_SERVERS=$kafkaBootstrap,HAMMERLY_KAFKA_AI_EVENTS_TOPIC=hammerly.ai.events.v1,HAMMERLY_KAFKA_AI_JOBS_TOPIC=hammerly.ai.jobs.v1",
    "--update-secrets=REDIS_PASSWORD=$RedisPasswordSecret`:latest", "--quiet"
)
if ($workerExists) {
    if ($PSCmdlet.ShouldProcess("Cloud Run worker pool $WorkerPool", "configure and scale to $WorkerInstances")) {
        Invoke-Gcloud -Arguments (@("run", "worker-pools", "update", $WorkerPool) + $workerArguments) | Write-Host
    }
} elseif ($PSCmdlet.ShouldProcess("Cloud Run worker pool $WorkerPool", "deploy with $WorkerInstances instance(s)")) {
    Invoke-Gcloud -Arguments (@("run", "worker-pools", "deploy", $WorkerPool) + $workerArguments) | Write-Host
}

$aiUrl = Get-AiUrl
$headers = Get-AiHeaders
Wait-AiHealth -AiUrl $aiUrl
Confirm-AiDemoOn -AiUrl $aiUrl -Headers $headers

$workerJson = Invoke-Gcloud -Arguments @(
    "run", "worker-pools", "describe", $WorkerPool,
    "--project=$ProjectId", "--region=$Region", "--format=json"
)
$worker = $workerJson | ConvertFrom-Json
if ($null -eq $worker.scaling -or [int]$worker.scaling.manualInstanceCount -lt 1) {
    throw "Worker pool '$WorkerPool' is not configured with an active manual instance."
}

Write-Host "Demo infrastructure is ON."
Write-Host "Redis: READY at $redisHost`:$redisPort"
Write-Host "Kafka VM: RUNNING at $kafkaBootstrap"
Write-Host "Worker pool: $($worker.scaling.manualInstanceCount) active instance(s)"
Write-Host "AI: health and chat verified with Redis/Kafka explicitly enabled"
