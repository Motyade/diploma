param (
    [string]$Username = "root",
    [string]$HostName = "185.221.199.141",
    [string]$Port = "22",
    [string]$TargetDir = "/opt/retailhub"
)

Write-Host "================================================="
Write-Host "Starting deployment to ${Username}@${HostName}:${Port}"
Write-Host "================================================="

Write-Host "1. Cleaning local target directories to speed up transfer..."
# Using SilentlyContinue because IDE or Maven might hold locks on these files.
if (Test-Path "app\target") { Remove-Item -Recurse -Force "app\target" -ErrorAction SilentlyContinue }
if (Test-Path "api-spec\target") { Remove-Item -Recurse -Force "api-spec\target" -ErrorAction SilentlyContinue }

Write-Host "2. Creating directories on server..."
ssh -p $Port ${Username}@${HostName} "mkdir -p $TargetDir"

Write-Host "3. Copying all files..."
scp -P $Port -r docker-compose.yml Dockerfile pom.xml api-spec app nginx .dockerignore "${Username}@${HostName}:${TargetDir}/"

Write-Host "4. Cleaning up old files and Docker cache on server to free up space..."
ssh -p $Port ${Username}@${HostName} "cd $TargetDir && rm -rf app/target api-spec/target && docker builder prune -a -f"

Write-Host "5. Running docker compose up --build..."
ssh -p $Port ${Username}@${HostName} "cd $TargetDir && docker compose down && docker compose up --build -d && docker image prune -a -f"

Write-Host "Deployment completed!"