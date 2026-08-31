param(
    [string]$BaseUrl = "http://localhost:5000",
    [Parameter(Mandatory = $true)]
    [string]$InternalToken,
    [string]$DocumentPath = "$PSScriptRoot\..\..\docs\knowledge-base\hammerly-support.md",
    [int]$PollSeconds = 2,
    [int]$TimeoutSeconds = 120
)

$resolvedPath = (Resolve-Path -LiteralPath $DocumentPath).Path
$content = Get-Content -LiteralPath $resolvedPath -Raw
$headers = @{ "X-Hammerly-Internal-Token" = $InternalToken }
$body = @{
    title = "Hammerly Support Guide"
    source = "docs/knowledge-base/hammerly-support.md"
    content = $content
} | ConvertTo-Json

$document = Invoke-RestMethod -Method Post -Uri "$BaseUrl/internal/knowledge/documents" `
    -Headers $headers -ContentType "application/json" -Body $body
Write-Host "Accepted document $($document.id) with status $($document.status)"

$deadline = (Get-Date).AddSeconds($TimeoutSeconds)
do {
    Start-Sleep -Seconds $PollSeconds
    $document = Invoke-RestMethod -Method Get `
        -Uri "$BaseUrl/internal/knowledge/documents/$($document.id)" -Headers $headers
    Write-Host "Status: $($document.status)"
    if ($document.status -in @("READY", "FAILED")) { break }
} while ((Get-Date) -lt $deadline)

if ($document.status -ne "READY") {
    throw "Document did not become READY (last status: $($document.status))"
}
$document
