param(
    [string]$DocumentSource = "docs/knowledge-base/hammerly-support.md"
)

$ErrorActionPreference = 'Stop'

$databaseUrl = $env:SUPABASE_DB_URL
if ([string]::IsNullOrWhiteSpace($databaseUrl)) {
    throw 'SUPABASE_DB_URL is not configured.'
}

$postgresUri = $databaseUrl -replace '^jdbc:', ''
$connectionUri = [Uri]$postgresUri
$jdbcOptions = [System.Web.HttpUtility]::ParseQueryString($connectionUri.Query)
$env:PGHOST = $connectionUri.Host
$env:PGPORT = if ($connectionUri.IsDefaultPort) { '5432' } else { [string]$connectionUri.Port }
$env:PGDATABASE = $connectionUri.AbsolutePath.TrimStart('/')
$env:PGSSLMODE = if ([string]::IsNullOrWhiteSpace($jdbcOptions['sslmode'])) {
    'require'
} else {
    $jdbcOptions['sslmode']
}

if (-not [string]::IsNullOrWhiteSpace($env:SUPABASE_DB_USERNAME)) {
    $env:PGUSER = $env:SUPABASE_DB_USERNAME
} elseif (-not [string]::IsNullOrWhiteSpace($jdbcOptions['user'])) {
    $env:PGUSER = $jdbcOptions['user']
}
if (-not [string]::IsNullOrWhiteSpace($env:SUPABASE_DB_PASSWORD)) {
    $env:PGPASSWORD = $env:SUPABASE_DB_PASSWORD
} elseif (-not [string]::IsNullOrWhiteSpace($jdbcOptions['password'])) {
    $env:PGPASSWORD = $jdbcOptions['password']
}

$dockerArguments = @(
    'run', '--rm', '-i',
    '-e', 'PGHOST', '-e', 'PGPORT', '-e', 'PGDATABASE', '-e', 'PGSSLMODE'
)
if (-not [string]::IsNullOrWhiteSpace($env:PGUSER)) {
    $dockerArguments += @('-e', 'PGUSER')
}
if (-not [string]::IsNullOrWhiteSpace($env:PGPASSWORD)) {
    $dockerArguments += @('-e', 'PGPASSWORD')
}
$dockerArguments += @(
    'postgres:17-alpine', 'psql', '--no-psqlrc', '--set', 'ON_ERROR_STOP=1',
    '--set', "document_source=$DocumentSource"
)

$query = @'
SET search_path TO hammerly, public;
SELECT d.id,
       d.status,
       d.title,
       d.source,
       left(d.content_hash, 12) AS content_hash_prefix,
       count(c.id) AS chunk_count,
       coalesce(string_agg(DISTINCT c.metadata ->> 'sectionTitle', ', '), '') AS sections,
       d.updated_at
FROM knowledge_documents d
LEFT JOIN knowledge_chunks c ON c.document_id = d.id
WHERE d.source = :'document_source'
GROUP BY d.id, d.status, d.title, d.source, d.content_hash, d.updated_at
ORDER BY d.updated_at DESC;

SELECT version, updated_at FROM knowledge_base_state WHERE id = 1;

SELECT topic, event_type, status, count(*) AS event_count
FROM outbox_events
WHERE published_at IS NULL
GROUP BY topic, event_type, status
ORDER BY topic, event_type, status;
'@

$query | & docker @dockerArguments
if ($LASTEXITCODE -ne 0) {
    throw "RAG status query failed with exit code $LASTEXITCODE."
}
