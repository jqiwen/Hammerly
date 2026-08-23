param(
    [ValidateSet('smoke', 'full')]
    [string]$Mode = 'smoke',
    [string]$BaseUrl = $(if ($env:HAMMERLY_LOAD_TEST_BASE_URL) {
        $env:HAMMERLY_LOAD_TEST_BASE_URL
    } else {
        'http://localhost:5000'
    }),
    [string]$SummaryExport = ''
)

$ErrorActionPreference = 'Stop'
$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '../..')).Path
$testScript = Join-Path $repositoryRoot 'load-test/phase5/chat-sse.js'

if ($Mode -eq 'full' -and $env:PROVIDER_MODE -ne 'loadtest' -and
        $env:ALLOW_LIVE_PROVIDER_LOAD_TEST -ne 'true') {
    throw 'Full benchmark refused: set PROVIDER_MODE=loadtest.'
}

$env:HAMMERLY_LOAD_TEST_MODE = $Mode
$env:HAMMERLY_LOAD_TEST_BASE_URL = $BaseUrl
if ($SummaryExport) {
    $env:PHASE5_SUMMARY_EXPORT = $SummaryExport
} elseif (-not $env:PHASE5_SUMMARY_EXPORT) {
    $timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
    $env:PHASE5_SUMMARY_EXPORT = Join-Path $repositoryRoot "load-test/phase5/results/$Mode-$timestamp.json"
}

$resultsDirectory = Split-Path -Parent $env:PHASE5_SUMMARY_EXPORT
New-Item -ItemType Directory -Force -Path $resultsDirectory | Out-Null

Write-Host "Running Phase 5 $Mode SSE test against $BaseUrl"
Write-Host "Summary: $($env:PHASE5_SUMMARY_EXPORT)"

if (Get-Command k6 -ErrorAction SilentlyContinue) {
    & k6 run $testScript
    exit $LASTEXITCODE
}

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    throw 'Neither k6 nor Docker is installed.'
}

$containerBaseUrl = $BaseUrl -replace '^http://localhost', 'http://host.docker.internal'
$summaryFileName = Split-Path -Leaf $env:PHASE5_SUMMARY_EXPORT
$containerSummary = "/results/$summaryFileName"

& docker compose --profile loadtest build k6
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}
& docker compose --profile loadtest run --rm `
    -e "HAMMERLY_LOAD_TEST_BASE_URL=$containerBaseUrl" `
    -e "HAMMERLY_LOAD_TEST_MODE=$Mode" `
    -e "PROVIDER_MODE=$($env:PROVIDER_MODE)" `
    -e "ALLOW_LIVE_PROVIDER_LOAD_TEST=$($env:ALLOW_LIVE_PROVIDER_LOAD_TEST)" `
    -e "HAMMERLY_LOAD_TEST_TOKEN=$($env:HAMMERLY_LOAD_TEST_TOKEN)" `
    -e "PHASE5_SUMMARY_EXPORT=$containerSummary" `
    k6 run /workspace/load-test/phase5/chat-sse.js
$exitCode = $LASTEXITCODE

$mountedSummary = Join-Path $repositoryRoot "load-test/phase5/results/$summaryFileName"
if ((Test-Path -LiteralPath $mountedSummary) -and
        ([IO.Path]::GetFullPath($mountedSummary) -ne
         [IO.Path]::GetFullPath($env:PHASE5_SUMMARY_EXPORT))) {
    Copy-Item -LiteralPath $mountedSummary -Destination $env:PHASE5_SUMMARY_EXPORT -Force
}
exit $exitCode
