param(
    [Parameter(Mandatory = $true)][string]$SummaryPath,
    [Parameter(Mandatory = $true)][string]$AutoscalingPath,
    [ValidateRange(1, 65535)][int]$PrometheusPort = 19090
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Get-StageVus {
    param([double]$ElapsedSeconds)

    if ($ElapsedSeconds -ge 45 -and $ElapsedSeconds -lt 165) { return 100 }
    if ($ElapsedSeconds -ge 180 -and $ElapsedSeconds -lt 300) { return 500 }
    if ($ElapsedSeconds -ge 315 -and $ElapsedSeconds -lt 435) { return 1000 }
    return 0
}

function Get-PrometheusScalar {
    param(
        [Parameter(Mandatory = $true)][string]$Query,
        [Parameter(Mandatory = $true)][long]$UnixTime
    )

    $encoded = [Uri]::EscapeDataString($Query)
    $uri = "http://127.0.0.1:$PrometheusPort/api/v1/query?query=$encoded&time=$UnixTime"
    try {
        $response = Invoke-RestMethod -Method Get -Uri $uri -TimeoutSec 5
        if ($response.status -eq 'success' -and $response.data.result.Count -gt 0) {
            return [double]$response.data.result[0].value[1]
        }
    } catch {}

    try {
        $clusterUri = "http://127.0.0.1:9090/api/v1/query?query=$encoded&time=$UnixTime"
        $json = @(& kubectl exec -n hammerly deployment/prometheus -- `
            wget -qO- $clusterUri 2>$null) -join "`n"
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

$summaryItem = Get-Item -LiteralPath $SummaryPath
$summary = Get-Content -Raw -LiteralPath $SummaryPath | ConvertFrom-Json
$duration = [timespan]::FromMilliseconds([double]$summary.state.testRunDurationMs)
$testStarted = [datetimeoffset]$summaryItem.LastWriteTime - $duration
$rows = @(Import-Csv -LiteralPath $AutoscalingPath)

$enriched = foreach ($row in $rows) {
    $timestamp = [datetimeoffset]::Parse($row.timestamp, [Globalization.CultureInfo]::InvariantCulture)
    $elapsed = ($timestamp - $testStarted).TotalSeconds
    $unixTime = $timestamp.ToUnixTimeSeconds()
    $rps = Get-PrometheusScalar -Query 'sum(rate(ai_requests_total[1m]))' -UnixTime $unixTime
    $p95 = Get-PrometheusScalar `
        -Query '1000 * histogram_quantile(0.95, sum by (le) (rate(ai_request_duration_seconds_bucket[1m])))' `
        -UnixTime $unixTime
    $errorRate = Get-PrometheusScalar `
        -Query '100 * (sum(rate(ai_request_duration_seconds_count{outcome=~"error|cancelled"}[1m])) or vector(0)) / clamp_min(sum(rate(ai_request_duration_seconds_count[1m])), 0.000001)' `
        -UnixTime $unixTime

    [pscustomobject]@{
        timestamp = $row.timestamp
        vus = Get-StageVus -ElapsedSeconds $elapsed
        desired_replicas = $row.desired_replicas
        current_replicas = $row.current_replicas
        cpu_utilization = $row.cpu_utilization
        rps = if ($null -eq $rps) { $row.rps } else { Format-Number -Value $rps }
        p95_ms = if ($null -eq $p95) { $row.p95_ms } else { Format-Number -Value $p95 }
        error_rate = if ($null -eq $errorRate) { $row.error_rate } else { Format-Number -Value $errorRate }
    }
}

$enriched | Export-Csv -LiteralPath $AutoscalingPath -NoTypeInformation -Encoding utf8
Write-Host "Aligned autoscaling stages to k6 start $($testStarted.ToString('o')) and enriched Prometheus samples."
