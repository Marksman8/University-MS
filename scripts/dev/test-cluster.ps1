# Throwaway PostgreSQL 18 cluster for integration tests (Windows PowerShell 5.1+).
#
#   powershell -ExecutionPolicy Bypass -File scripts\dev\test-cluster.ps1 init    # create, start, ums_test, roles
#   powershell -ExecutionPolicy Bypass -File scripts\dev\test-cluster.ps1 start
#   powershell -ExecutionPolicy Bypass -File scripts\dev\test-cluster.ps1 stop
#   powershell -ExecutionPolicy Bypass -File scripts\dev\test-cluster.ps1 status
#   powershell -ExecutionPolicy Bypass -File scripts\dev\test-cluster.ps1 roles   # re-run create_roles.sql
#   powershell -ExecutionPolicy Bypass -File scripts\dev\test-cluster.ps1 env     # print the env vars for mvn verify
#
# Data lives in .tools\pg18-it (git-ignored), listens on localhost:55432 only, trust authentication.
# The role passwords below are fixed throwaway values for this local trust-auth cluster only;
# never use them anywhere else. Override the binaries directory with -PgBin or UMS_PG_BIN.
param(
    [Parameter(Position = 0)]
    [ValidateSet('init', 'start', 'stop', 'status', 'roles', 'env')]
    [string]$Command = 'status',
    [string]$PgBin = $env:UMS_PG_BIN,
    [int]$Port = 55432
)
$ErrorActionPreference = 'Stop'

$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$data = Join-Path $root '.tools\pg18-it'
$database = 'ums_test'
$passwords = [ordered]@{
    owner_pw = 'it-owner'; app_pw = 'it-app'; console_pw = 'it-console'
    listener_pw = 'it-listener'; backup_pw = 'it-backup'
}

function Find-PgBin {
    if ($PgBin) { return $PgBin }
    $default = 'C:\Program Files\PostgreSQL\18\bin'
    if (Test-Path (Join-Path $default 'pg_ctl.exe')) { return $default }
    $onPath = Get-Command pg_ctl.exe -ErrorAction SilentlyContinue
    if ($onPath) { return (Split-Path $onPath.Source) }
    throw 'PostgreSQL 18 binaries not found; pass -PgBin or set UMS_PG_BIN'
}

$bin = Find-PgBin
$pgCtl = Join-Path $bin 'pg_ctl.exe'
$initdb = Join-Path $bin 'initdb.exe'
$psql = Join-Path $bin 'psql.exe'
$env:PGOPTIONS = '-c client_min_messages=warning'

function Assert-Version18 {
    $version = & $initdb --version
    if ($version -notmatch ' 18\.') { throw "Expected PostgreSQL 18, found: $version" }
}

# pg_ctl starts the server as a child; run it in its own hidden window and wait only for pg_ctl,
# so the long-running server never holds this shell's output handles.
function Invoke-PgCtl([string[]]$Arguments) {
    $quoted = $Arguments | ForEach-Object { if ($_ -match '\s') { '"' + $_ + '"' } else { $_ } }
    $process = Start-Process -FilePath $pgCtl -ArgumentList $quoted -WindowStyle Hidden -PassThru
    $process.WaitForExit()
    return $process.ExitCode
}

function Test-Running {
    if (-not (Test-Path (Join-Path $data 'PG_VERSION'))) { return $false }
    return (Invoke-PgCtl @('-D', $data, 'status')) -eq 0
}

function Start-Cluster {
    if (Test-Running) { Write-Host "Already running on port $Port"; return }
    $code = Invoke-PgCtl @('-D', $data, '-l', (Join-Path $data 'server.log'), '-w', '-t', '60',
        '-o', "-p $Port -c listen_addresses=localhost", 'start')
    if ($code -ne 0) { throw "pg_ctl start failed (exit $code); see $data\server.log" }
    Write-Host "Started PostgreSQL 18 on localhost:$Port"
}

function Invoke-Roles {
    $vars = @()
    foreach ($entry in $passwords.GetEnumerator()) { $vars += @('-v', "$($entry.Key)=$($entry.Value)") }
    & $psql -h localhost -p $Port -U postgres -d $database -X -q -v ON_ERROR_STOP=1 @vars `
        -f (Join-Path $root 'scripts\db\create_roles.sql')
    if ($LASTEXITCODE -ne 0) { throw "create_roles.sql failed (exit $LASTEXITCODE)" }
    Write-Host "Roles applied to $database"
}

function Show-Env {
    Write-Host "`$env:UMS_TEST_DB_URL = 'jdbc:postgresql://localhost:$Port/$database'"
    Write-Host "`$env:UMS_TEST_DB_USER = 'postgres'"
    Write-Host "`$env:UMS_TEST_DB_PASSWORD = 'postgres'"
    Write-Host "`$env:UMS_DB_PASSWORD = '$($passwords.app_pw)'"
    Write-Host "`$env:UMS_CONSOLE_DB_PASSWORD = '$($passwords.console_pw)'"
    Write-Host "`$env:UMS_LISTENER_DB_PASSWORD = '$($passwords.listener_pw)'"
    Write-Host "`$env:UMS_OWNER_DB_PASSWORD = '$($passwords.owner_pw)'"
    Write-Host "`$env:UMS_BACKUP_DB_PASSWORD = '$($passwords.backup_pw)'"
}

switch ($Command) {
    'init' {
        Assert-Version18
        if (-not (Test-Path (Join-Path $data 'PG_VERSION'))) {
            New-Item -ItemType Directory -Force (Split-Path $data) | Out-Null
            & $initdb -D $data -U postgres --auth=trust --encoding=UTF8 --locale=C | Out-Null
            if ($LASTEXITCODE -ne 0) { throw "initdb failed (exit $LASTEXITCODE)" }
        }
        Start-Cluster
        $exists = & $psql -h localhost -p $Port -U postgres -d postgres -X -Atc `
            "SELECT 1 FROM pg_database WHERE datname = '$database'"
        if ($exists -ne '1') {
            & $psql -h localhost -p $Port -U postgres -d postgres -X -q -c "CREATE DATABASE $database ENCODING 'UTF8'"
        }
        Invoke-Roles
        Show-Env
    }
    'start' { Start-Cluster }
    'stop' {
        if (-not (Test-Running)) { Write-Host 'Not running'; break }
        $code = Invoke-PgCtl @('-D', $data, '-m', 'fast', '-w', 'stop')
        if ($code -ne 0) { throw "pg_ctl stop failed (exit $code)" }
        Write-Host 'Stopped'
    }
    'status' { if (Test-Running) { Write-Host "Running on localhost:$Port ($data)" } else { Write-Host 'Not running' } }
    'roles' { Invoke-Roles }
    'env' { Show-Env }
}
