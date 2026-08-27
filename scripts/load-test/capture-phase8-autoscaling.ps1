param(
    [string]$CsvPath,
    [string]$SnapshotPath,
    [string]$StopFile,
    [ValidateRange(5, 60)][int]$SampleSeconds = 15,
    [ValidateRange(1, 65535)][int]$PrometheusPort = 19090,
    [int]$WarmupSeconds = 30,
    [int]$RampSeconds = 15,
    [int]$Hold100Seconds = 120,
    [int]$Hold500Seconds = 120,
    [int]$Hold1000Seconds = 120,
    [int]$CooldownSeconds = 30,
    [switch]$Append,
    [ValidateRange(0, 900)][int]$MaxSeconds = 0,
    [ValidateRange(0, 100)][int]$StopAtReplicas = 0
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Get-StageVus {
    param([double]$Elapsed)

    if ($Elapsed -lt $WarmupSeconds) { return 25 }
    $cursor = $WarmupSeconds
    if ($Elapsed -lt $cursor + $RampSeconds) {
        return [math]::Round(25 + (75 * (($Elapsed - $cursor) / $RampSeconds)))
    }
    $cursor += $RampSeconds
    if ($Elapsed -lt $cursor + $Hold100Seconds) { return 100 }
    $cursor += $Hold100Seconds
    if ($Elapsed -lt $cursor + $RampSeconds) {
        return [math]::Round(100 + (400 * (($Elapsed - $cursor) / $RampSeconds)))
    }
    $cursor += $RampSeconds
    if ($Elapsed -lt $cursor + $Hold500Seconds) { return 500 }
    $cursor += $Hold500Seconds
    if ($Elapsed -lt $cursor + $RampSeconds) {
        return [math]::Round(500 + (500 * (($Elapsed - $cursor) / $RampSeconds)))
    }
    $cursor += $RampSeconds
    if ($Elapsed -lt $cursor + $Hold1000Seconds) { return 1000 }
    return 0
}

function Get-PrometheusScalar {
    param([Parameter(Mandatory = $true)][string]$Query)

    try {
        $encoded = [Uri]::EscapeDataString($Query)
        $response = Invoke-RestMethod -Method Get `
            -Uri "http://127.0.0.1:$PrometheusPort/api/v1/query?query=$encoded" `
            -TimeoutSec 5
        if ($response.status -eq 'success' -and $response.data.result.Count -gt 0) {
            return [double]$response.data.result[0].value[1]
        }
    } catch {}

    try {
        $json = @(& kubectl exec -n hammerly deployment/prometheus -- `
            wget -qO- "http://127.0.0.1:9090/api/v1/query?query=$encoded" 2>$null) -join "`n"
        if ($LASTEXITCODE -eq 0 -and $json) {
            $response = $json | ConvertFrom-Json
            if ($response.status -eq 'success' -and $response.data.result.Count -gt 0) {
                return [double]$response.data.result[0].value[1]
            }
        }
    } catch {}
    return $null
}

function Format-Number {
    param($Value, [int]$Decimals = 3)
    if ($null -eq $Value) { return '' }
    $number = [double]$Value
    if ([double]::IsNaN($number) -or [double]::IsInfinity($number)) { return '' }
    return $number.ToString("F$Decimals", [Globalization.CultureInfo]::InvariantCulture)
}

$csvDirectory = Split-Path -Parent $CsvPath
if (-not (Test-Path -LiteralPath $csvDirectory)) {
    New-Item -ItemType Directory -Force -Path $csvDirectory | Out-Null
}
if ($Append) {
    if (-not (Test-Path -LiteralPath $CsvPath)) {
        throw "Cannot append because '$CsvPath' does not exist."
    }
    Add-Content -LiteralPath $SnapshotPath `
        -Value "`nPhase 8 cooldown capture resumed $(Get-Date -Format o)" -Encoding utf8
} else {
    'timestamp,vus,desired_replicas,current_replicas,cpu_utilization,rps,p95_ms,error_rate' |
        Set-Content -LiteralPath $CsvPath -Encoding utf8
    "Phase 8 kubectl evidence started $(Get-Date -Format o)" |
        Set-Content -LiteralPath $SnapshotPath -Encoding utf8
}

$started = Get-Date
$nextSnapshot = [datetime]::MinValue
while (-not (Test-Path -LiteralPath $StopFile)) {
    $elapsed = ((Get-Date) - $started).TotalSeconds
    $hpaJson = @(& kubectl get hpa hammerly-ai -n hammerly -o json 2>$null) -join "`n"
    if ($LASTEXITCODE -eq 0 -and $hpaJson) {
        $hpa = $hpaJson | ConvertFrom-Json
        $desired = if ($null -eq $hpa.status.desiredReplicas) { '' } else { $hpa.status.desiredReplicas }
        $current = if ($null -eq $hpa.status.currentReplicas) { '' } else { $hpa.status.currentReplicas }
        $cpu = ''
        foreach ($metric in @($hpa.status.currentMetrics)) {
            if ($metric.type -eq 'Resource' -and $metric.resource.name -eq 'cpu') {
                $cpu = $metric.resource.current.averageUtilization
            }
        }

        $rps = Get-PrometheusScalar -Query 'sum(rate(ai_requests_total[1m]))'
        $p95 = Get-PrometheusScalar -Query '1000 * histogram_quantile(0.95, sum by (le) (rate(ai_request_duration_seconds_bucket[1m])))'
        $errorRate = Get-PrometheusScalar -Query '100 * (sum(rate(ai_request_duration_seconds_count{outcome=~"error|cancelled"}[1m])) or vector(0)) / clamp_min(sum(rate(ai_request_duration_seconds_count[1m])), 0.000001)'
        $row = @(
            (Get-Date -Format o),
            $(if ($Append) { 0 } else { Get-StageVus -Elapsed $elapsed }),
            $desired,
            $current,
            $cpu,
            (Format-Number -Value $rps),
            (Format-Number -Value $p95),
            (Format-Number -Value $errorRate)
        ) -join ','
        Add-Content -LiteralPath $CsvPath -Value $row -Encoding utf8
        if ($StopAtReplicas -gt 0 -and "$current" -and [int]$current -le $StopAtReplicas) { break }
    }

    if ((Get-Date) -ge $nextSnapshot) {
        Add-Content -LiteralPath $SnapshotPath `
            -Value "`n=== $(Get-Date -Format o) ===" -Encoding utf8
        @(& kubectl get pods -n hammerly -o wide 2>&1) |
            Add-Content -LiteralPath $SnapshotPath -Encoding utf8
        @(& kubectl get hpa -n hammerly 2>&1) |
            Add-Content -LiteralPath $SnapshotPath -Encoding utf8
        @(& kubectl top pods -n hammerly 2>&1) |
            Add-Content -LiteralPath $SnapshotPath -Encoding utf8
        $nextSnapshot = (Get-Date).AddSeconds(60)
    }

    if ($MaxSeconds -gt 0 -and $elapsed -ge $MaxSeconds) { break }
    Start-Sleep -Seconds $SampleSeconds
}

Add-Content -LiteralPath $SnapshotPath `
    -Value "`n=== final $(Get-Date -Format o) ===" -Encoding utf8
@(& kubectl get pods -n hammerly -o wide 2>&1) |
    Add-Content -LiteralPath $SnapshotPath -Encoding utf8
@(& kubectl get hpa -n hammerly 2>&1) |
    Add-Content -LiteralPath $SnapshotPath -Encoding utf8
@(& kubectl top pods -n hammerly 2>&1) |
    Add-Content -LiteralPath $SnapshotPath -Encoding utf8
