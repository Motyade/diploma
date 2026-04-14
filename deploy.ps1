param (
    [string]$Username = "root",
    [string]$HostName = "83.147.255.205",
    [string]$Port = "22",
    [string]$TargetDir = "/opt/retailhub"
)

Write-Host "================================================="
Write-Host "Starting deployment to ${Username}@${HostName}:${Port}"
Write-Host "================================================="

Write-Host "1. Cleaning local target directories to speed up transfer..."
$targetRoots = @(
    "shared\event-contracts",
    "api-gateway",
    "auth-service",
    "store-service",
    "user-service",
    "request-service",
    "notification-service",
    "analytics-service",
    "integration-tests"
)
foreach ($root in $targetRoots) {
    $t = Join-Path $root "target"
    if (Test-Path $t) { Remove-Item -Recurse -Force $t -ErrorAction SilentlyContinue }
}

Write-Host "2. Creating directories on server..."
ssh -p $Port ${Username}@${HostName} "mkdir -p $TargetDir"

Write-Host "3. Copying all files..."
scp -P $Port -r `
    docker-compose.yml `
    pom.xml `
    init-databases.sql `
    .dockerignore `
    shared `
    api-gateway `
    auth-service `
    store-service `
    user-service `
    request-service `
    notification-service `
    analytics-service `
    web-app `
    "${Username}@${HostName}:${TargetDir}/"

Write-Host "4. Cleaning up old files and Docker cache on server..."
ssh -p $Port ${Username}@${HostName} "cd $TargetDir && docker builder prune -f"

Write-Host "5. Building project on server (sequential Maven to save resources)..."
ssh -p $Port ${Username}@${HostName} "cd $TargetDir && mvn clean install -Dmaven.test.skip=true -T 1"

Write-Host "6. Running docker compose up --build..."
ssh -p $Port ${Username}@${HostName} "cd $TargetDir && docker compose down && docker compose up --build -d && docker image prune -a -f"

Write-Host "Deployment completed!"
