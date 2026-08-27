[CmdletBinding()]
param(
    [string]$ProjectId = 'hammerly-506214',
    [string]$Namespace = 'hammerly',
    [string]$GrafanaSecret = 'hammerly-grafana-admin-password'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Invoke-GcloudSensitive {
    param([Parameter(Mandatory = $true)][string[]]$Arguments)

    $output = @(& gcloud @Arguments 2>$null)
    if ($LASTEXITCODE -ne 0) {
        throw "A required Secret Manager read failed for project '$ProjectId'."
    }
    return (($output | ForEach-Object { "$_" }) -join [Environment]::NewLine).TrimEnd("`r", "`n")
}

function Test-GcpSecret {
    param([Parameter(Mandatory = $true)][string]$Name)

    & gcloud secrets describe $Name --project=$ProjectId *> $null
    return $LASTEXITCODE -eq 0
}

function New-RandomPassword {
    $bytes = [byte[]]::new(36)
    [Security.Cryptography.RandomNumberGenerator]::Fill($bytes)
    return [Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+', 'A').Replace('/', 'B')
}

function Add-GcpSecretVersion {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][string]$Value
    )

    $tempRoot = [IO.Path]::GetFullPath([IO.Path]::GetTempPath())
    $tempFile = [IO.Path]::GetFullPath([IO.Path]::GetTempFileName())
    if (-not $tempFile.StartsWith($tempRoot, [StringComparison]::OrdinalIgnoreCase)) {
        throw 'Refusing to write secret material outside the OS temporary directory.'
    }
    try {
        [IO.File]::WriteAllText($tempFile, $Value, [Text.UTF8Encoding]::new($false))
        & gcloud secrets versions add $Name --project=$ProjectId --data-file=$tempFile --quiet *> $null
        if ($LASTEXITCODE -ne 0) {
            throw "Could not add the initial version of Secret Manager secret '$Name'."
        }
    } finally {
        if (Test-Path -LiteralPath $tempFile) {
            Remove-Item -LiteralPath $tempFile -Force
        }
    }
}

function ConvertTo-Base64 {
    param([AllowEmptyString()][string]$Value)
    return [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes($Value))
}

if (-not (Get-Command gcloud -ErrorAction SilentlyContinue)) {
    throw 'gcloud is required and must be available on PATH.'
}
if (-not (Get-Command kubectl -ErrorAction SilentlyContinue)) {
    throw 'kubectl is required and must be available on PATH.'
}

if (-not (Test-GcpSecret -Name $GrafanaSecret)) {
    & gcloud secrets create $GrafanaSecret --project=$ProjectId `
        --replication-policy=automatic --quiet *> $null
    if ($LASTEXITCODE -ne 0) {
        throw "Could not create Secret Manager secret '$GrafanaSecret'."
    }
    Add-GcpSecretVersion -Name $GrafanaSecret -Value (New-RandomPassword)
    Write-Host "Created Secret Manager secret '$GrafanaSecret' with a generated value."
}

$secretMap = [ordered]@{
    SUPABASE_DB_URL = 'hammerly-supabase-db-url'
    JWT_SECRET = 'hammerly-jwt-secret'
    OPENAI_API_KEY = 'hammerly-openai-api-key'
    REDIS_PASSWORD = 'hammerly-redis-password'
    HAMMERLY_AI_INTERNAL_TOKEN = 'hammerly-ai-internal-token'
    GRAFANA_ADMIN_PASSWORD = $GrafanaSecret
}

$encoded = [ordered]@{}
foreach ($entry in $secretMap.GetEnumerator()) {
    if (-not (Test-GcpSecret -Name $entry.Value)) {
        throw "Required Secret Manager secret '$($entry.Value)' does not exist."
    }
    $value = Invoke-GcloudSensitive -Arguments @(
        'secrets', 'versions', 'access', 'latest',
        "--secret=$($entry.Value)", "--project=$ProjectId"
    )
    if ([string]::IsNullOrWhiteSpace($value)) {
        throw "Secret Manager secret '$($entry.Value)' has an empty latest version."
    }
    $encoded[$entry.Key] = ConvertTo-Base64 -Value $value
}

$manifest = [ordered]@{
    apiVersion = 'v1'
    kind = 'Secret'
    metadata = [ordered]@{ name = 'hammerly-app-secrets'; namespace = $Namespace }
    type = 'Opaque'
    data = $encoded
} | ConvertTo-Json -Depth 6 -Compress

$manifest | & kubectl apply -f -
if ($LASTEXITCODE -ne 0) {
    throw 'kubectl failed to apply the in-memory Kubernetes Secret.'
}
Write-Host 'Synchronized Kubernetes secrets from Secret Manager without writing values to the repository.'

