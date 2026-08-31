param(
    [switch]$CheckOnly,
    [switch]$ProfileListings
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
$dockerArguments += @('postgres:17-alpine', 'psql', '--no-psqlrc', '--set', 'ON_ERROR_STOP=1')

if ($ProfileListings) {
    $profileSql = @'
SET search_path TO hammerly, public;
EXPLAIN (ANALYZE, BUFFERS)
SELECT a.id, a.title, a.category, a.description, a.start_price, a.current_bid,
       a.image, a.condition, a.seller_id, a.status, a.start_time, a.end_time,
       a.created_at, COALESCE(u.first_name || ' ' || u.last_name, NULL) AS seller,
       COUNT(b.id) AS total_bids
FROM auctions a
LEFT JOIN users u ON u.id = a.seller_id
LEFT JOIN bids b ON b.auction_id = a.id
WHERE a.status = 'active' AND a.end_time > CURRENT_TIMESTAMP
GROUP BY a.id, u.id ORDER BY a.created_at DESC LIMIT 6;

EXPLAIN (ANALYZE, BUFFERS)
SELECT a.id, a.title, a.category, a.current_bid, a.image, a.condition,
       a.status, a.start_time, a.end_time, a.created_at,
       COALESCE(u.first_name || ' ' || u.last_name, NULL) AS seller,
       COALESCE(bid_totals.total_bids, 0) AS total_bids
FROM (
    SELECT id, title, category, current_bid, image, condition, seller_id,
           status, start_time, end_time, created_at
    FROM auctions
    WHERE status = 'active'
      AND start_time <= CURRENT_TIMESTAMP
      AND end_time > CURRENT_TIMESTAMP
    ORDER BY created_at DESC LIMIT 12
) a
LEFT JOIN users u ON u.id = a.seller_id
LEFT JOIN LATERAL (
    SELECT COUNT(*) AS total_bids FROM bids b WHERE b.auction_id = a.id
) bid_totals ON TRUE
ORDER BY a.created_at DESC;
'@
    $profileSql | & docker @dockerArguments
} elseif ($CheckOnly) {
    $checkSql = @'
SET search_path TO hammerly, public;
SELECT
    (SELECT COUNT(*) FROM auctions) AS total_auctions,
    COUNT(*) FILTER (WHERE seller.email LIKE 'demo-seller-%@hammerly.example') AS demo_auctions,
    COUNT(DISTINCT seller.id) FILTER (WHERE seller.email LIKE 'demo-seller-%@hammerly.example') AS demo_sellers,
    COUNT(*) FILTER (
        WHERE seller.email LIKE 'demo-seller-%@hammerly.example'
          AND (seller.first_name <> 'Hammerly Demo' OR seller.last_name NOT LIKE 'Seller %')
    ) AS conflicting_demo_rows
FROM auctions AS auction
JOIN users AS seller ON seller.id = auction.seller_id;
'@
    $checkSql | & docker @dockerArguments
} else {
    $seedFile = Join-Path $PSScriptRoot 'seed-demo-auctions.sql'
    Get-Content -LiteralPath $seedFile -Raw | & docker @dockerArguments
}

if ($LASTEXITCODE -ne 0) {
    throw "Demo auction database command failed with exit code $LASTEXITCODE."
}
