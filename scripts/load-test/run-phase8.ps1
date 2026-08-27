param(
    [ValidateSet('smoke', 'full')]
    [string]$Mode = 'full',
    [string]$BaseUrl = '',
    [ValidateRange(5, 60)][int]$SampleSeconds = 15,
    [ValidateRange(1, 65535)][int]$PrometheusPort = 19090,
    [ValidateRange(0, 900)][int]$HpaCooldownTimeoutSeconds = 600
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$resultsDirectory = Join-Path $repositoryRoot 'load-test/phase8/results'
$summaryPath = Join-Path $resultsDirectory 'phase8-k6-summary.json'
$autoscalingPath = Join-Path $resultsDirectory 'phase8-autoscaling.csv'
$snapshotPath = Join-Path $resultsDirectory 'phase8-kubectl-snapshots.log'
$stageResultsPath = Join-Path $resultsDirectory 'phase8-results.csv'
$monitorScript = Join-Path $PSScriptRoot 'capture-phase8-autoscaling.ps1'
$stopFile = Join-Path ([IO.Path]::GetTempPath()) "hammerly-phase8-stop-$([guid]::NewGuid().ToString('N'))"

if (-not (Test-Path -LiteralPath $resultsDirectory)) {
    New-Item -ItemType Directory -Force -Path $resultsDirectory | Out-Null
}

$profile = @(& kubectl get deployment hammerly-ai -n hammerly `
    -o "jsonpath={.spec.template.spec.containers[?(@.name=='ai')].env[?(@.name=='SPRING_PROFILES_ACTIVE')].value}" 2>$null) -join ''
if ($LASTEXITCODE -ne 0) {
    throw 'Could not read the deployed Hammerly AI profile.'
}
if ($Mode -eq 'full' -and $profile -ne 'loadtest') {
    throw "Full benchmark refused: deployed AI profile is '$profile', not 'loadtest'."
}

if (-not $BaseUrl) {
    for ($attempt = 1; $attempt -le 60; $attempt++) {
        $address = @(& kubectl get service hammerly-backend -n hammerly `
            -o 'jsonpath={.status.loadBalancer.ingress[0].ip}{.status.loadBalancer.ingress[0].hostname}' 2>$null) -join ''
        if ($address) { break }
        Start-Sleep -Seconds 5
    }
    if (-not $address) { throw 'Backend LoadBalancer has no external address.' }
    $BaseUrl = "http://$address"
}
$BaseUrl = $BaseUrl.TrimEnd('/')

$health = Invoke-RestMethod -Method Get -Uri "$BaseUrl/actuator/health" -TimeoutSec 15
if ($health.status -ne 'UP') {
    throw "Backend health is '$($health.status)', not UP."
}

$useLocalK6 = $null -ne (Get-Command k6 -ErrorAction SilentlyContinue)
if (-not $useLocalK6) {
    if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
        throw 'Neither k6 nor Docker is installed.'
    }
    & docker build -f (Join-Path $repositoryRoot 'load-test/phase5/Dockerfile.k6') `
        -t hammerly-k6-sse:1.2.2 $repositoryRoot
    if ($LASTEXITCODE -ne 0) { throw 'Could not build the pinned k6 SSE image.' }
}

$shellPath = (Get-Process -Id $PID).Path
$portForwardOut = Join-Path ([IO.Path]::GetTempPath()) "hammerly-phase8-prom-$([guid]::NewGuid().ToString('N')).log"
$portForwardErr = Join-Path ([IO.Path]::GetTempPath()) "hammerly-phase8-prom-$([guid]::NewGuid().ToString('N')).err"
$portForward = Start-Process -FilePath kubectl -WindowStyle Hidden -PassThru `
    -ArgumentList @('port-forward', 'service/prometheus', "$PrometheusPort`:9090", '-n', 'hammerly') `
    -RedirectStandardOutput $portForwardOut -RedirectStandardError $portForwardErr
try {
    for ($attempt = 1; $attempt -le 30; $attempt++) {
        try {
            $ready = Invoke-RestMethod -Method Get -Uri "http://127.0.0.1:$PrometheusPort/-/ready" -TimeoutSec 2
            if ($ready -match 'ready') { break }
        } catch {}
        Start-Sleep -Seconds 1
    }

    $monitorArguments = @(
        '-NoProfile', '-File', $monitorScript,
        '-CsvPath', $autoscalingPath,
        '-SnapshotPath', $snapshotPath,
        '-StopFile', $stopFile,
        '-SampleSeconds', $SampleSeconds,
        '-PrometheusPort', $PrometheusPort
    )
    $monitor = Start-Process -FilePath $shellPath -WindowStyle Hidden -PassThru `
        -ArgumentList $monitorArguments
    try {
        $env:HAMMERLY_LOAD_TEST_MODE = $Mode
        $env:HAMMERLY_LOAD_TEST_BASE_URL = $BaseUrl
        $env:PROVIDER_MODE = 'loadtest'
        $env:PHASE5_SUMMARY_EXPORT = '/workspace/load-test/phase8/results/phase8-k6-summary.json'

        Write-Host "Running Phase 8 $Mode benchmark against $BaseUrl"
        if ($useLocalK6) {
            $env:PHASE5_SUMMARY_EXPORT = $summaryPath
            & k6 run (Join-Path $repositoryRoot 'load-test/phase5/chat-sse.js')
        } else {
            & docker run --rm `
                -e "HAMMERLY_LOAD_TEST_BASE_URL=$BaseUrl" `
                -e "HAMMERLY_LOAD_TEST_MODE=$Mode" `
                -e 'PROVIDER_MODE=loadtest' `
                -e 'PHASE5_SUMMARY_EXPORT=/workspace/load-test/phase8/results/phase8-k6-summary.json' `
                -v "${repositoryRoot}:/workspace" `
                hammerly-k6-sse:1.2.2 run /workspace/load-test/phase5/chat-sse.js
        }
        $k6Exit = $LASTEXITCODE
        if ($Mode -eq 'full' -and $HpaCooldownTimeoutSeconds -gt 0) {
            Write-Host 'Load finished; retaining the monitor until AI returns to two replicas or the cooldown timeout expires.'
            $cooldownDeadline = (Get-Date).AddSeconds($HpaCooldownTimeoutSeconds)
            do {
                $replicas = @(& kubectl get deployment hammerly-ai -n hammerly `
                    -o 'jsonpath={.status.readyReplicas}' 2>$null) -join ''
                if ($LASTEXITCODE -eq 0 -and [int]$replicas -le 2) { break }
                Start-Sleep -Seconds $SampleSeconds
            } while ((Get-Date) -lt $cooldownDeadline)
            if (-not $replicas -or [int]$replicas -gt 2) {
                Write-Warning "AI did not return to two Ready replicas within $HpaCooldownTimeoutSeconds seconds."
            }
        }
    } finally {
        New-Item -ItemType File -Path $stopFile -Force | Out-Null
        if (-not $monitor.WaitForExit(45000)) {
            Stop-Process -Id $monitor.Id -Force
        }
    }
    if (Test-Path -LiteralPath $summaryPath) {
        & (Join-Path $PSScriptRoot 'enrich-phase8-evidence.ps1') `
            -SummaryPath $summaryPath -AutoscalingPath $autoscalingPath `
            -PrometheusPort $PrometheusPort
    }
} finally {
    if ($null -ne $portForward -and -not $portForward.HasExited) {
        Stop-Process -Id $portForward.Id -Force
    }
    foreach ($path in @($stopFile, $portForwardOut, $portForwardErr)) {
        if (Test-Path -LiteralPath $path) { Remove-Item -LiteralPath $path -Force }
    }
}

if (-not (Test-Path -LiteralPath $summaryPath)) {
    throw "k6 did not write '$summaryPath'."
}
& (Join-Path $PSScriptRoot 'summarize-phase8.ps1') `
    -SummaryPath $summaryPath -AutoscalingPath $autoscalingPath -OutputPath $stageResultsPath
if ($k6Exit -ne 0) {
    throw "k6 exited with code $k6Exit. Evidence files were retained."
}

Write-Host "Autoscaling evidence: $autoscalingPath"
Write-Host "Stage results: $stageResultsPath"
Write-Host "kubectl snapshots: $snapshotPath"
