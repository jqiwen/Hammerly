param(
    [Parameter(Mandatory = $true)][string]$SummaryPath,
    [Parameter(Mandatory = $true)][string]$AutoscalingPath,
    [Parameter(Mandatory = $true)][string]$OutputPath,
    [int]$HoldSeconds = 120
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$summary = Get-Content -Raw -LiteralPath $SummaryPath | ConvertFrom-Json
$autoscaling = @(Import-Csv -LiteralPath $AutoscalingPath)

function Get-MetricValues {
    param([Parameter(Mandatory = $true)][string]$Name)
    $property = $summary.metrics.PSObject.Properties[$Name]
    if ($null -eq $property) { return $null }
    return $property.Value.values
}

$rows = foreach ($vus in @(100, 500, 1000)) {
    $success = Get-MetricValues -Name "hammerly_sse_streams_successful{concurrency:$vus}"
    $duration = Get-MetricValues -Name "hammerly_sse_stream_duration{concurrency:$vus,outcome:success}"
    $first = Get-MetricValues -Name "hammerly_sse_first_event_latency{concurrency:$vus}"
    $failure = Get-MetricValues -Name "hammerly_sse_stream_failure_rate{concurrency:$vus}"
    $stageSamples = @($autoscaling | Where-Object { [int]$_.vus -eq $vus })
    $maxPods = if ($stageSamples.Count -eq 0) { '' } else {
        ($stageSamples | Measure-Object -Property current_replicas -Maximum).Maximum
    }
    [pscustomobject]@{
        vus = $vus
        max_pods = $maxPods
        successful_streams = if ($null -eq $success) { 0 } else { $success.count }
        rps = if ($null -eq $success) { 0 } else { [math]::Round($success.count / $HoldSeconds, 3) }
        p50_ms = if ($null -eq $duration) { '' } else { [math]::Round($duration.med, 3) }
        p95_ms = if ($null -eq $duration) { '' } else { [math]::Round($duration.'p(95)', 3) }
        p99_ms = if ($null -eq $duration) { '' } else { [math]::Round($duration.'p(99)', 3) }
        error_rate_percent = if ($null -eq $failure) { '' } else { [math]::Round(100 * $failure.rate, 4) }
        first_token_p95_ms = if ($null -eq $first) { '' } else { [math]::Round($first.'p(95)', 3) }
    }
}

$rows | Export-Csv -LiteralPath $OutputPath -NoTypeInformation -Encoding utf8
$rows | Format-Table -AutoSize

