param(
    [string]$HostName = $env:RETAILHUB_SSH_HOST,
    [string]$User = $env:RETAILHUB_SSH_USER,
    [string]$RemoteDir = $env:RETAILHUB_REMOTE_DIR,
    [string]$Password = $env:RETAILHUB_SSH_PASSWORD
)
$ErrorActionPreference = "Stop"
if (-not $HostName) { $HostName = "83.147.255.205" }
if (-not $User) { $User = "root" }
if (-not $RemoteDir) { $RemoteDir = "/opt/retailhub" }
if (-not $Password) {
    Write-Error "Set environment variable RETAILHUB_SSH_PASSWORD"
}

$plink = Get-Command plink.exe -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source
$pscp = Get-Command pscp.exe -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source
if (-not $plink -or -not $pscp) {
    Write-Error "plink.exe and pscp.exe (PuTTY) must be on PATH"
}

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $root
$bundle = Join-Path $env:TEMP "retailhub-bundle.tgz"
if (Test-Path $bundle) { Remove-Item -Force $bundle }

Write-Host "Packing tarball (no target/)..."
& tar.exe -czf $bundle --exclude=target --exclude=.git -C $root .

Write-Host "Uploading to ${User}@${HostName}..."
& $pscp -pw $Password -batch $bundle "${User}@${HostName}:/tmp/retailhub-bundle.tgz"

$remoteLines = @(
    "set -e",
    "mkdir -p $RemoteDir",
    "cd $RemoteDir",
    "tar xzf /tmp/retailhub-bundle.tgz",
    "rm -f /tmp/retailhub-bundle.tgz",
    "if [ ! -f .env ]; then cp .env.example .env 2>/dev/null || true; fi",
    "touch .env",
    "grep -q '^QR_SCAN_BASE_URL=' .env || echo 'QR_SCAN_BASE_URL=http://83.147.255.205' >> .env",
    "grep -q '^KAFKA_PUBLIC_HOST=' .env || echo 'KAFKA_PUBLIC_HOST=83.147.255.205' >> .env",
    "mvn install -Dmaven.test.skip=true -q -T 1",
    "docker compose up -d --build",
    "docker compose ps"
)
$remoteScript = ($remoteLines -join "`n") + "`n"
$runner = Join-Path $env:TEMP "retailhub-remote.sh"
$utf8NoBom = New-Object System.Text.UTF8Encoding $false
[System.IO.File]::WriteAllText($runner, $remoteScript, $utf8NoBom)

Write-Host "Remote: mvn, docker compose build/up..."
& $pscp -pw $Password -batch $runner "${User}@${HostName}:/tmp/retailhub-remote.sh"
& $plink -ssh "${User}@${HostName}" -pw $Password -batch "sed -i 's/\r$//' /tmp/retailhub-remote.sh 2>/dev/null; chmod +x /tmp/retailhub-remote.sh; bash /tmp/retailhub-remote.sh; rm -f /tmp/retailhub-remote.sh"

Write-Host "Done."
