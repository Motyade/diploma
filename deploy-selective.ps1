param (
    [string[]]$Services = @("api-gateway", "auth-service", "user-service"),
    [string]$Username = "root",
    [string]$HostName = "83.147.255.205",
    [string]$Port = "22",
    [string]$TargetDir = "/opt/retailhub"
)

Write-Host "================================================="
Write-Host "Targeted deployment of: $($Services -join ', ')"
Write-Host "To: ${Username}@${HostName}:${Port}"
Write-Host "================================================="

# 1. Clean local targets for selected services
Write-Host "1. Cleaning local target directories..."
$targetRoots = @("shared/event-contracts") + $Services
foreach ($root in $targetRoots) {
    $t = Join-Path $root "target"
    if (Test-Path $t) { 
        Write-Host "Removing $t"
        Remove-Item -Recurse -Force $t -ErrorAction SilentlyContinue 
    }
}

# 2. Create directory on server
Write-Host "2. Ensuring target directory exists on server..."
ssh -p $Port ${Username}@${HostName} "mkdir -p $TargetDir"

# 3. Copy global files and selected services
Write-Host "3. Copying essential files..."
$filesToCopy = @(
    "docker-compose.yml",
    "pom.xml",
    "init-databases.sql",
    ".dockerignore",
    "shared"
)
$filesToCopy += $Services

foreach ($file in $filesToCopy) {
    Write-Host "Copying $file..."
    scp -P $Port -r $file "${Username}@${HostName}:${TargetDir}/"
}

# 4. Build only selected services on server
$mavenProjectList = ($Services -join ",")
Write-Host "4. Building selected modules on server: $mavenProjectList"
ssh -p $Port ${Username}@${HostName} "cd $TargetDir && mvn clean install -pl $mavenProjectList -am -Dmaven.test.skip=true -T 1"

# 5. Up selected containers
$dockerServiceList = ($Services -join " ")
Write-Host "5. Running docker compose up for: $dockerServiceList"
ssh -p $Port ${Username}@${HostName} "cd $TargetDir && docker compose up --build -d $dockerServiceList && docker image prune -f"

Write-Host "================================================="
Write-Host "Targeted deployment completed!"
Write-Host "================================================="
