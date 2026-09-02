[CmdletBinding(SupportsShouldProcess = $true, ConfirmImpact = "High")]
param(
    [string]$ProjectId = "hammerly-506214",
    [string]$Region = "us-west1",
    [string]$Zone = "us-west1-b",
    [string]$Network = "default",
    [string]$Subnet = "default",
    [string]$CoreService = "hammerly-backend",
    [string]$AiService = "hammerly-ai",
    [string]$WorkerPool = "hammerly-worker",
    [ValidateRange(1, 20)][int]$WorkerInstances = 1,
    [string]$WorkerServiceAccount = "",
    [string]$WorkerImage = "",
    [string]$WorkerGroupId = "hammerly-worker-v1",
    [string]$KafkaVm = "hammerly-kafka",
    [string]$KafkaMachineType = "",
    [ValidateRange(10, 100)][int]$KafkaBootDiskSizeGb = 20,
    [ValidateSet("pd-standard", "pd-balanced")][string]$KafkaBootDiskType = "pd-standard",
    [string]$KafkaVersion = "3.9.1",
    [ValidateRange(1, 12)][int]$KafkaPartitions = 3,
    [string]$KafkaNetworkTag = "hammerly-kafka",
    [string]$RedisPasswordSecret = "hammerly-redis-password",
    [string]$DatabaseUrlSecret = "hammerly-supabase-db-url",
    [string]$OpenAiSecret = "hammerly-openai-api-key",
    [string]$InternalTokenSecret = "hammerly-ai-internal-token",
    [string]$GitHubRepository = "jqiwen/Hammerly",
    [switch]$SkipGitHubVariables,
    [switch]$SkipApplicationSmokeTest
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$KafkaEventsTopic = "hammerly.ai.events.v1"
$KafkaJobsTopic = "hammerly.ai.jobs.v1"
$KafkaTopics = @(
    $KafkaEventsTopic,
    $KafkaJobsTopic,
    "$KafkaEventsTopic.DLT",
    "$KafkaJobsTopic.DLT"
)
$KafkaAllowRule = "$KafkaVm-allow-9092"
$KafkaDenyRule = "$KafkaVm-deny-9092"
$KafkaIapSshRule = "$KafkaVm-allow-iap-ssh"

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

function Invoke-GcloudSensitive {
    param([Parameter(Mandatory = $true)][string[]]$Arguments)

    $previousWhatIf = $WhatIfPreference
    try {
        $WhatIfPreference = $false
        $output = @(& gcloud @Arguments 2>$null)
        if ($LASTEXITCODE -ne 0) {
            throw "A sensitive gcloud read failed; command output was suppressed."
        }
        return (($output | ForEach-Object { "$_" }) -join [Environment]::NewLine).Trim()
    } finally {
        $WhatIfPreference = $previousWhatIf
    }
}

function Test-GcloudResource {
    param([Parameter(Mandatory = $true)][string[]]$Arguments)

    $previousWhatIf = $WhatIfPreference
    $previousErrorAction = $ErrorActionPreference
    try {
        $WhatIfPreference = $false
        $ErrorActionPreference = "Continue"
        $output = @(& gcloud @Arguments 2>&1)
        if ($LASTEXITCODE -eq 0) {
            return $true
        }

        $message = ($output | ForEach-Object { "$_" }) -join [Environment]::NewLine
        if ($message -match "(?i)\bNOT_FOUND\b|cannot find|not found|was not found") {
            return $false
        }
        throw "gcloud existence probe failed: gcloud $($Arguments -join ' ')`n$message"
    } finally {
        $ErrorActionPreference = $previousErrorAction
        $WhatIfPreference = $previousWhatIf
    }
}

function Get-KafkaVmDetails {
    $json = Invoke-Gcloud -Arguments @(
        "compute", "instances", "list",
        "--project=$ProjectId",
        "--format=json(name,zone,status,machineType,disks,networkInterfaces)"
    )
    $instances = @($json | ConvertFrom-Json)
    $matches = @($instances | Where-Object {
        $null -ne $_ -and
        $null -ne $_.PSObject.Properties["name"] -and
        [string]$_.PSObject.Properties["name"].Value -eq $KafkaVm
    })
    if ($matches.Count -gt 1) {
        throw "More than one Compute Engine VM is named '$KafkaVm'."
    }
    if ($matches.Count -eq 0) {
        return $null
    }

    $item = $matches[0]
    if ($null -eq $item.networkInterfaces -or $item.networkInterfaces.Count -eq 0) {
        throw "Kafka VM '$KafkaVm' has no network interface."
    }
    $accessConfigs = @()
    $accessConfigProperty = $item.networkInterfaces[0].PSObject.Properties["accessConfigs"]
    if ($null -ne $accessConfigProperty) {
        $accessConfigs = @($accessConfigProperty.Value)
    }
    return [pscustomobject]@{
        Name = $item.name
        Zone = ($item.zone -split "/")[-1]
        Status = $item.status
        MachineType = ($item.machineType -split "/")[-1]
        PrivateIp = $item.networkInterfaces[0].networkIP
        ExternalIp = if ($accessConfigs.Count -gt 0) { $accessConfigs[0].natIP } else { $null }
        AccessConfigName = if ($accessConfigs.Count -gt 0) { $accessConfigs[0].name } else { $null }
    }
}

function Get-CloudRunService {
    param([Parameter(Mandatory = $true)][string]$Service)

    $json = Invoke-Gcloud -Arguments @(
        "run", "services", "describe", $Service,
        "--project=$ProjectId", "--region=$Region", "--format=json"
    )
    return $json | ConvertFrom-Json
}

function Get-CloudRunEnvValue {
    param(
        [Parameter(Mandatory = $true)]$Service,
        [Parameter(Mandatory = $true)][string]$Name
    )

    $entry = @($Service.spec.template.spec.containers[0].env | Where-Object { $_.name -eq $Name })
    if ($entry.Count -eq 0) {
        return $null
    }
    return [string]$entry[0].value
}

function Assert-UpstashConfiguration {
    param([Parameter(Mandatory = $true)]$AiConfiguration)

    $redisEnabled = Get-CloudRunEnvValue -Service $AiConfiguration -Name "HAMMERLY_REDIS_ENABLED"
    $redisSsl = Get-CloudRunEnvValue -Service $AiConfiguration -Name "REDIS_SSL"
    $redisPort = Get-CloudRunEnvValue -Service $AiConfiguration -Name "REDIS_PORT"
    $redisHost = Get-CloudRunEnvValue -Service $AiConfiguration -Name "REDIS_HOST"
    if ($redisEnabled -ne "true" -or $redisSsl -ne "true" -or $redisPort -ne "6379") {
        throw "Production AI must already use Upstash with HAMMERLY_REDIS_ENABLED=true, REDIS_SSL=true, and REDIS_PORT=6379."
    }
    if ([string]::IsNullOrWhiteSpace($redisHost) -or
            $redisHost -match "^(localhost|127\.|redis-not-configured\.|.*\.internal$)") {
        throw "Production AI does not have a valid external Upstash REDIS_HOST."
    }
    if (-not (Test-GcloudResource -Arguments @(
        "secrets", "describe", $RedisPasswordSecret, "--project=$ProjectId"
    ))) {
        throw "Required existing Upstash password secret '$RedisPasswordSecret' was not found."
    }

    Write-Host "Upstash preflight passed (host configured, port 6379, TLS enabled, Secret Manager reference present)."
    return $redisHost
}

function Ensure-FirewallRule {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][ValidateSet("ALLOW", "DENY")][string]$Action,
        [Parameter(Mandatory = $true)][int]$Priority,
        [Parameter(Mandatory = $true)][string]$SourceRange,
        [Parameter(Mandatory = $true)][string]$Rule
    )

    $common = @(
        "--project=$ProjectId", "--network=$Network", "--direction=INGRESS",
        "--priority=$Priority", "--source-ranges=$SourceRange",
        "--target-tags=$KafkaNetworkTag", "--quiet"
    )
    $updateRuleArgument = if ($Action -eq "ALLOW") { "--allow=$Rule" } else { "--rules=$Rule" }
    if (Test-GcloudResource -Arguments @(
        "compute", "firewall-rules", "describe", $Name, "--project=$ProjectId"
    )) {
        $existingJson = Invoke-Gcloud -Arguments @(
            "compute", "firewall-rules", "describe", $Name,
            "--project=$ProjectId", "--format=json(allowed,denied)"
        )
        $existing = $existingJson | ConvertFrom-Json
        $deniedProperty = $existing.PSObject.Properties["denied"]
        $existingAction = if ($null -ne $deniedProperty -and @($deniedProperty.Value).Count -gt 0) {
            "DENY"
        } else {
            "ALLOW"
        }
        if ($existingAction -ne $Action) {
            throw "Firewall rule '$Name' exists with action $existingAction; expected $Action. Refusing to replace it implicitly."
        }
        Invoke-Gcloud -Arguments @(
            "compute", "firewall-rules", "update", $Name,
            "--priority=$Priority", "--source-ranges=$SourceRange",
            "--target-tags=$KafkaNetworkTag", $updateRuleArgument, "--quiet"
        ) | Out-Null
    } else {
        $createActionArguments = if ($Action -eq "ALLOW") {
            @("--allow=$Rule")
        } else {
            @("--action=DENY", "--rules=$Rule")
        }
        Invoke-Gcloud -Arguments (@(
            "compute", "firewall-rules", "create", $Name
        ) + $common + $createActionArguments) | Out-Null
    }
}

function Wait-KafkaVmRunning {
    param([Parameter(Mandatory = $true)][string]$VmZone)

    for ($attempt = 1; $attempt -le 60; $attempt++) {
        $state = Invoke-Gcloud -Arguments @(
            "compute", "instances", "describe", $KafkaVm,
            "--project=$ProjectId", "--zone=$VmZone", "--format=value(status)"
        )
        if ($state -eq "RUNNING") {
            return
        }
        Start-Sleep -Seconds 5
    }
    throw "Timed out waiting for Kafka VM '$KafkaVm' to enter RUNNING state."
}

function Invoke-KafkaRemote {
    param(
        [Parameter(Mandatory = $true)][string]$VmZone,
        [Parameter(Mandatory = $true)][string]$Command
    )

    return Invoke-Gcloud -Arguments @(
        "compute", "ssh", $KafkaVm,
        "--project=$ProjectId", "--zone=$VmZone", "--tunnel-through-iap",
        "--command=$Command", "--quiet"
    )
}

function Wait-KafkaReady {
    param([Parameter(Mandatory = $true)][string]$VmZone)

    $lastError = "startup is still in progress"
    for ($attempt = 1; $attempt -le 60; $attempt++) {
        try {
            Invoke-KafkaRemote -VmZone $VmZone -Command (
                "sudo systemctl is-active --quiet kafka.service && " +
                "sudo /opt/kafka/bin/kafka-broker-api-versions.sh " +
                "--bootstrap-server 127.0.0.1:9092 >/dev/null"
            ) | Out-Null
            return
        } catch {
            $lastError = $_.Exception.Message
        }
        Start-Sleep -Seconds 5
    }

    try {
        Invoke-Gcloud -Arguments @(
            "compute", "instances", "get-serial-port-output", $KafkaVm,
            "--project=$ProjectId", "--zone=$VmZone", "--port=1"
        ) | Write-Warning
    } catch {
        Write-Warning "Could not read serial-port diagnostics."
    }
    throw "Kafka broker health check timed out. Last error: $lastError"
}

function Ensure-KafkaTopics {
    param([Parameter(Mandatory = $true)][string]$VmZone)

    foreach ($topic in $KafkaTopics) {
        Invoke-KafkaRemote -VmZone $VmZone -Command (
            "sudo /opt/kafka/bin/kafka-topics.sh --bootstrap-server 127.0.0.1:9092 " +
            "--create --if-not-exists --topic '$topic' --partitions $KafkaPartitions " +
            "--replication-factor 1"
        ) | Out-Null
    }
    $listed = Invoke-KafkaRemote -VmZone $VmZone -Command (
        "sudo /opt/kafka/bin/kafka-topics.sh --bootstrap-server 127.0.0.1:9092 --list"
    )
    foreach ($topic in $KafkaTopics) {
        if (@($listed -split "`n" | ForEach-Object { $_.Trim() }) -notcontains $topic) {
            throw "Required Kafka topic '$topic' is missing after provisioning."
        }
    }
    Write-Host "Kafka topics verified: $($KafkaTopics -join ', ')"
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

function Grant-SecretAccess {
    param(
        [Parameter(Mandatory = $true)][string]$SecretName,
        [Parameter(Mandatory = $true)][string]$ServiceAccount
    )

    if (-not (Test-GcloudResource -Arguments @(
        "secrets", "describe", $SecretName, "--project=$ProjectId"
    ))) {
        throw "Required existing secret '$SecretName' was not found."
    }
    Invoke-Gcloud -Arguments @(
        "secrets", "add-iam-policy-binding", $SecretName,
        "--project=$ProjectId", "--member=serviceAccount:$ServiceAccount",
        "--role=roles/secretmanager.secretAccessor", "--quiet"
    ) | Out-Null
}

function Wait-WorkerGroup {
    param([Parameter(Mandatory = $true)][string]$VmZone)

    $lastOutput = "consumer group has not registered"
    for ($attempt = 1; $attempt -le 36; $attempt++) {
        try {
            $lastOutput = Invoke-KafkaRemote -VmZone $VmZone -Command (
                "sudo /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server 127.0.0.1:9092 " +
                "--describe --group '$WorkerGroupId' --members 2>&1"
            )
            if ($lastOutput -match [regex]::Escape($WorkerGroupId) -and
                    $lastOutput -notmatch "no active members|does not exist") {
                return
            }
        } catch {
            $lastOutput = $_.Exception.Message
        }
        Start-Sleep -Seconds 5
    }
    throw "Worker pool did not join consumer group '$WorkerGroupId'. Last output: $lastOutput"
}

function Get-ConsumerOffsetTotal {
    param(
        [Parameter(Mandatory = $true)][string]$VmZone,
        [Parameter(Mandatory = $true)][string]$Topic
    )

    $output = Invoke-KafkaRemote -VmZone $VmZone -Command (
        "sudo /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server 127.0.0.1:9092 " +
        "--describe --group '$WorkerGroupId' 2>/dev/null || true"
    )
    [long]$total = 0
    foreach ($line in ($output -split "`r?`n")) {
        $columns = @($line.Trim() -split "\s+")
        if ($columns.Count -ge 5 -and $columns[1] -eq $Topic -and $columns[3] -match "^\d+$") {
            $total += [long]$columns[3]
        }
    }
    return $total
}

function Get-WorkerManualInstanceCount {
    param([Parameter(Mandatory = $true)]$WorkerConfiguration)

    $scalingProperty = $WorkerConfiguration.PSObject.Properties["scaling"]
    if ($null -ne $scalingProperty) {
        $manualCountProperty = $scalingProperty.Value.PSObject.Properties["manualInstanceCount"]
        if ($null -ne $manualCountProperty) {
            return [int]$manualCountProperty.Value
        }
    }
    $metadataProperty = $WorkerConfiguration.PSObject.Properties["metadata"]
    if ($null -ne $metadataProperty) {
        $annotationsProperty = $metadataProperty.Value.PSObject.Properties["annotations"]
        if ($null -ne $annotationsProperty) {
            $manualCountProperty = $annotationsProperty.Value.PSObject.Properties[
                "run.googleapis.com/manualInstanceCount"
            ]
            if ($null -ne $manualCountProperty) {
                return [int]$manualCountProperty.Value
            }
        }
    }
    throw "Worker pool response did not contain a manual instance count."
}

function Publish-WorkerSmokeEvent {
    param([Parameter(Mandatory = $true)][string]$VmZone)

    $before = Get-ConsumerOffsetTotal -VmZone $VmZone -Topic $KafkaEventsTopic
    $eventId = [guid]::NewGuid()
    $conversationId = "infra-smoke-$($eventId.ToString('N'))"
    $occurredAt = [DateTimeOffset]::UtcNow.ToString("o")
    $event = [ordered]@{
        eventId = $eventId
        eventType = "message.created"
        eventVersion = 1
        occurredAt = $occurredAt
        producer = "hammerly-infrastructure-smoke"
        correlationId = [guid]::NewGuid()
        userId = "infrastructure-smoke"
        conversationId = $conversationId
        payload = [ordered]@{
            role = "USER"
            content = "Kafka infrastructure smoke test; contains no customer or business data."
            createdAt = $occurredAt
        }
    }
    $json = $event | ConvertTo-Json -Depth 5 -Compress
    $encoded = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($json))
    $remoteCommand = 'payload=$(printf ''%s'' ''{0}'' | base64 --decode); printf ''%s|%s\n'' ''{1}'' "$payload" | sudo /opt/kafka/bin/kafka-console-producer.sh --bootstrap-server 127.0.0.1:9092 --topic ''{2}'' --property parse.key=true --property ''key.separator=|''' -f $encoded, $conversationId, $KafkaEventsTopic
    Invoke-KafkaRemote -VmZone $VmZone -Command $remoteCommand | Out-Null

    for ($attempt = 1; $attempt -le 30; $attempt++) {
        $after = Get-ConsumerOffsetTotal -VmZone $VmZone -Topic $KafkaEventsTopic
        if ($after -gt $before) {
            Write-Host "Worker smoke event consumed by group '$WorkerGroupId' (eventId=$eventId)."
            return
        }
        Start-Sleep -Seconds 2
    }
    throw "Worker group '$WorkerGroupId' did not consume the non-business smoke event."
}

function Confirm-AiApplicationSmoke {
    param(
        [Parameter(Mandatory = $true)]$AiConfiguration,
        [Parameter(Mandatory = $true)][string]$VmZone
    )

    $aiUrl = [string]$AiConfiguration.status.url
    if ([string]::IsNullOrWhiteSpace($aiUrl)) {
        throw "Cloud Run AI service '$AiService' has no URL."
    }
    $token = Invoke-GcloudSensitive -Arguments @(
        "secrets", "versions", "access", "latest",
        "--secret=$InternalTokenSecret", "--project=$ProjectId"
    )
    $headers = @{
        "X-Hammerly-User-Id" = "demo-infra-verifier"
        "X-Hammerly-Internal-Token" = $token
    }

    $status = Invoke-RestMethod -Method Get -Uri "$($aiUrl.TrimEnd('/'))/internal/ai/status" `
        -Headers $headers -TimeoutSec 20
    if (-not [bool]$status.redisEnabled -or -not [bool]$status.kafkaEnabled) {
        throw "AI status did not report Redis and Kafka enabled."
    }

    $before = Get-ConsumerOffsetTotal -VmZone $VmZone -Topic $KafkaEventsTopic
    $body = @{
        message = "Reply briefly to confirm Hammerly AI chat is available."
        history = @()
        conversationId = [guid]::NewGuid().ToString()
    } | ConvertTo-Json -Compress
    $chat = Invoke-RestMethod -Method Post -Uri "$($aiUrl.TrimEnd('/'))/internal/ai/chat" `
        -Headers $headers -ContentType "application/json" -Body $body -TimeoutSec 90
    if ($null -eq $chat -or [string]::IsNullOrWhiteSpace([string]$chat.answer)) {
        throw "AI chat smoke test returned no answer."
    }

    for ($attempt = 1; $attempt -le 30; $attempt++) {
        $after = Get-ConsumerOffsetTotal -VmZone $VmZone -Topic $KafkaEventsTopic
        if ($after -ge ($before + 2)) {
            Write-Host "AI chat and asynchronous Kafka publication verified; worker consumed both message facts."
            return
        }
        Start-Sleep -Seconds 2
    }
    throw "AI chat succeeded, but its two asynchronous message events were not observed as consumed."
}

if (-not (Get-Command gcloud -ErrorAction SilentlyContinue)) {
    throw "gcloud is required and must be available on PATH."
}
if (-not $SkipGitHubVariables -and -not (Get-Command gh -ErrorAction SilentlyContinue)) {
    throw "gh is required to persist Kafka repository variables; use -SkipGitHubVariables only when intentionally managing them separately."
}
if ([string]::IsNullOrWhiteSpace($KafkaMachineType)) {
    $KafkaMachineType = if ([string]::IsNullOrWhiteSpace($env:KAFKA_MACHINE_TYPE)) {
        "e2-small"
    } else {
        $env:KAFKA_MACHINE_TYPE
    }
}
if ([string]::IsNullOrWhiteSpace($WorkerImage)) {
    $WorkerImage = "$Region-docker.pkg.dev/$ProjectId/hammerly/hammerly-worker:latest"
}
if ([string]::IsNullOrWhiteSpace($WorkerServiceAccount)) {
    $WorkerServiceAccount = "hammerly-worker-runtime@$ProjectId.iam.gserviceaccount.com"
}

Invoke-Gcloud -Arguments @("projects", "describe", $ProjectId, "--format=value(projectId)") | Out-Null
$subnetJson = Invoke-Gcloud -Arguments @(
    "compute", "networks", "subnets", "describe", $Subnet,
    "--project=$ProjectId", "--region=$Region", "--format=json(name,network,ipCidrRange)"
)
$subnetConfiguration = $subnetJson | ConvertFrom-Json
$subnetCidr = [string]$subnetConfiguration.ipCidrRange
if ([string]::IsNullOrWhiteSpace($subnetCidr)) {
    throw "Subnet '$Subnet' did not return an IPv4 CIDR."
}

$aiConfiguration = Get-CloudRunService -Service $AiService
$coreConfiguration = Get-CloudRunService -Service $CoreService
$redisHost = Assert-UpstashConfiguration -AiConfiguration $aiConfiguration
foreach ($secret in @($DatabaseUrlSecret, $OpenAiSecret, $InternalTokenSecret)) {
    if (-not (Test-GcloudResource -Arguments @(
        "secrets", "describe", $secret, "--project=$ProjectId"
    ))) {
        throw "Required existing secret '$secret' was not found."
    }
}
if (-not (Test-GcloudResource -Arguments @(
    "artifacts", "docker", "images", "describe", $WorkerImage, "--project=$ProjectId"
))) {
    throw "Worker image '$WorkerImage' does not exist. Run the 'Publish Worker Image' workflow first."
}

$kafka = Get-KafkaVmDetails
$workerExists = Test-GcloudResource -Arguments @(
    "run", "worker-pools", "describe", $WorkerPool,
    "--project=$ProjectId", "--region=$Region"
)

Write-Host "Billable resource plan (no Memorystore):"
if ($null -eq $kafka) {
    Write-Host "  CREATE VM: $KafkaVm in $Zone, $KafkaMachineType, $KafkaBootDiskSizeGb GB $KafkaBootDiskType boot disk."
    Write-Host "  A temporary ephemeral external IPv4 is used only for first boot, then removed."
} else {
    Write-Host "  REUSE VM: $KafkaVm in $($kafka.Zone), $($kafka.MachineType), private IP retained."
}
if ($workerExists) {
    Write-Host "  UPDATE/SCALE worker pool: $WorkerPool to $WorkerInstances instance(s), each 1 vCPU / 1 GiB."
} else {
    Write-Host "  CREATE worker pool: $WorkerPool with $WorkerInstances continuously billed instance(s), each 1 vCPU / 1 GiB."
}
Write-Host "  Core/AI min-instance settings are not changed. Upstash and Supabase are not modified."

$action = "enable private Kafka, update Core/AI Kafka variables, and run $WorkerInstances continuously billed worker instance(s)"
if (-not $PSCmdlet.ShouldProcess("$ProjectId/$Region", $action)) {
    return
}

foreach ($serviceState in @(
    [pscustomobject]@{ Name = $CoreService; Configuration = $coreConfiguration },
    [pscustomobject]@{ Name = $AiService; Configuration = $aiConfiguration }
)) {
    if ((Get-CloudRunEnvValue -Service $serviceState.Configuration -Name "HAMMERLY_KAFKA_ENABLED") -ne "false") {
        Invoke-Gcloud -Arguments @(
            "run", "services", "update", $serviceState.Name,
            "--project=$ProjectId", "--region=$Region",
            "--update-env-vars=HAMMERLY_KAFKA_ENABLED=false", "--quiet"
        ) | Write-Host
    }
}
if ($workerExists) {
    Invoke-Gcloud -Arguments @(
        "run", "worker-pools", "update", $WorkerPool,
        "--project=$ProjectId", "--region=$Region", "--instances=0", "--quiet"
    ) | Write-Host
}

Ensure-FirewallRule -Name $KafkaAllowRule -Action ALLOW -Priority 900 `
    -SourceRange $subnetCidr -Rule "tcp:9092"
Ensure-FirewallRule -Name $KafkaDenyRule -Action DENY -Priority 1000 `
    -SourceRange "0.0.0.0/0" -Rule "tcp:9092"
Ensure-FirewallRule -Name $KafkaIapSshRule -Action ALLOW -Priority 900 `
    -SourceRange "35.235.240.0/20" -Rule "tcp:22"

if ($null -eq $kafka) {
    $startupScript = Join-Path $PSScriptRoot "kafka-startup.sh"
    if (-not (Test-Path -LiteralPath $startupScript -PathType Leaf)) {
        throw "Kafka startup script was not found at '$startupScript'."
    }
    Invoke-Gcloud -Arguments @(
        "compute", "instances", "create", $KafkaVm,
        "--project=$ProjectId", "--zone=$Zone", "--machine-type=$KafkaMachineType",
        "--network=$Network", "--subnet=$Subnet", "--tags=$KafkaNetworkTag",
        "--boot-disk-size=$($KafkaBootDiskSizeGb)GB", "--boot-disk-type=$KafkaBootDiskType",
        "--image-family=ubuntu-2404-lts-amd64", "--image-project=ubuntu-os-cloud",
        "--metadata=hammerly-kafka-version=$KafkaVersion",
        "--metadata-from-file=startup-script=$startupScript",
        "--no-service-account", "--shielded-secure-boot", "--shielded-vtpm",
        "--shielded-integrity-monitoring", "--quiet"
    ) | Write-Host
    $kafka = Get-KafkaVmDetails
} elseif ($kafka.Status -ne "RUNNING") {
    Invoke-Gcloud -Arguments @(
        "compute", "instances", "start", $KafkaVm,
        "--project=$ProjectId", "--zone=$($kafka.Zone)", "--quiet"
    ) | Write-Host
}

$effectiveZone = if ($null -eq $kafka) { $Zone } else { $kafka.Zone }
Wait-KafkaVmRunning -VmZone $effectiveZone
Wait-KafkaReady -VmZone $effectiveZone
$kafka = Get-KafkaVmDetails
if ($null -eq $kafka -or [string]::IsNullOrWhiteSpace($kafka.PrivateIp)) {
    throw "Kafka VM '$KafkaVm' has no private IP."
}
$kafkaBootstrap = "$($kafka.PrivateIp):9092"
Ensure-KafkaTopics -VmZone $effectiveZone

if (-not [string]::IsNullOrWhiteSpace($kafka.ExternalIp)) {
    Invoke-Gcloud -Arguments @(
        "compute", "instances", "delete-access-config", $KafkaVm,
        "--project=$ProjectId", "--zone=$effectiveZone",
        "--access-config-name=$($kafka.AccessConfigName)", "--quiet"
    ) | Out-Null
    Write-Host "Removed the Kafka VM's temporary external IPv4 address."
}

foreach ($service in @($CoreService, $AiService)) {
    Invoke-Gcloud -Arguments @(
        "run", "services", "update", $service,
        "--project=$ProjectId", "--region=$Region",
        "--network=$Network", "--subnet=$Subnet", "--vpc-egress=private-ranges-only",
        "--update-env-vars=HAMMERLY_KAFKA_ENABLED=false,KAFKA_BOOTSTRAP_SERVERS=$kafkaBootstrap",
        "--quiet"
    ) | Write-Host
}

if (-not (Test-GcloudResource -Arguments @(
    "iam", "service-accounts", "describe", $WorkerServiceAccount, "--project=$ProjectId"
))) {
    $accountId = ($WorkerServiceAccount -split "@")[0]
    Invoke-Gcloud -Arguments @(
        "iam", "service-accounts", "create", $accountId,
        "--project=$ProjectId", "--display-name=Hammerly worker runtime", "--quiet"
    ) | Out-Null
}
foreach ($secret in @($RedisPasswordSecret, $DatabaseUrlSecret, $OpenAiSecret)) {
    Grant-SecretAccess -SecretName $secret -ServiceAccount $WorkerServiceAccount
}

$workerEnvironment = @(
    "SPRING_PROFILES_ACTIVE=prod",
    "MANAGEMENT_PORT=5002",
    "HAMMERLY_KAFKA_ENABLED=true",
    "KAFKA_BOOTSTRAP_SERVERS=$kafkaBootstrap",
    "HAMMERLY_KAFKA_AI_EVENTS_TOPIC=$KafkaEventsTopic",
    "HAMMERLY_KAFKA_AI_JOBS_TOPIC=$KafkaJobsTopic",
    "HAMMERLY_WORKER_GROUP_ID=$WorkerGroupId",
    "HAMMERLY_WORKER_CONCURRENCY=3",
    "REDIS_HOST=$redisHost",
    "REDIS_PORT=6379",
    "REDIS_SSL=true",
    "HAMMERLY_DB_SSL_MODE=require",
    "HAMMERLY_AI_EMBEDDING_PROVIDER=openai"
) -join ","
$workerSecrets = @(
    "REDIS_PASSWORD=$RedisPasswordSecret`:latest",
    "SUPABASE_DB_URL=$DatabaseUrlSecret`:latest",
    "OPENAI_API_KEY=$OpenAiSecret`:latest"
) -join ","
Invoke-Gcloud -Arguments @(
    "run", "worker-pools", "deploy", $WorkerPool,
    "--project=$ProjectId", "--region=$Region", "--image=$WorkerImage",
    "--service-account=$WorkerServiceAccount", "--instances=$WorkerInstances",
    "--cpu=1", "--memory=1Gi", "--network=$Network", "--subnet=$Subnet",
    "--vpc-egress=private-ranges-only", "--update-env-vars=$workerEnvironment",
    "--update-secrets=$workerSecrets", "--quiet"
) | Write-Host

Wait-WorkerGroup -VmZone $effectiveZone
Publish-WorkerSmokeEvent -VmZone $effectiveZone

foreach ($service in @($CoreService, $AiService)) {
    Invoke-Gcloud -Arguments @(
        "run", "services", "update", $service,
        "--project=$ProjectId", "--region=$Region",
        "--network=$Network", "--subnet=$Subnet", "--vpc-egress=private-ranges-only",
        "--update-env-vars=HAMMERLY_KAFKA_ENABLED=true,KAFKA_BOOTSTRAP_SERVERS=$kafkaBootstrap",
        "--quiet"
    ) | Write-Host
}

$aiConfiguration = Get-CloudRunService -Service $AiService
$coreConfiguration = Get-CloudRunService -Service $CoreService
foreach ($serviceState in @(
    [pscustomobject]@{ Name = $AiService; Configuration = $aiConfiguration },
    [pscustomobject]@{ Name = $CoreService; Configuration = $coreConfiguration }
)) {
    if ((Get-CloudRunEnvValue -Service $serviceState.Configuration -Name "HAMMERLY_KAFKA_ENABLED") -ne "true" -or
            (Get-CloudRunEnvValue -Service $serviceState.Configuration -Name "KAFKA_BOOTSTRAP_SERVERS") -ne $kafkaBootstrap) {
        throw "Cloud Run service '$($serviceState.Name)' does not have the expected Kafka configuration."
    }
}

if (-not $SkipApplicationSmokeTest) {
    Confirm-AiApplicationSmoke -AiConfiguration $aiConfiguration -VmZone $effectiveZone
}

$workerJson = Invoke-Gcloud -Arguments @(
    "run", "worker-pools", "describe", $WorkerPool,
    "--project=$ProjectId", "--region=$Region", "--format=json"
)
$worker = $workerJson | ConvertFrom-Json
$workerInstanceCount = Get-WorkerManualInstanceCount -WorkerConfiguration $worker
if ($workerInstanceCount -lt 1) {
    throw "Worker pool '$WorkerPool' is not configured with an active manual instance."
}

if (-not $SkipGitHubVariables) {
    Set-GitHubVariable -Name "KAFKA_BOOTSTRAP_SERVERS" -Value $kafkaBootstrap
    Set-GitHubVariable -Name "HAMMERLY_KAFKA_ENABLED" -Value "true"
}

Write-Host "Kafka demo infrastructure is ON."
Write-Host "Kafka VM: $KafkaVm ($effectiveZone, $($kafka.MachineType), private-only $kafkaBootstrap)"
Write-Host "Kafka topics: $($KafkaTopics -join ', ')"
Write-Host "Worker pool: $WorkerPool ($workerInstanceCount active instance(s), group $WorkerGroupId)"
Write-Host "Upstash Redis: verified and untouched"
