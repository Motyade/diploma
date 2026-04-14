param(
    [string]$HostRoot = "http://83.147.255.205:8180",
    [string]$QrToken = "55555555-5555-5555-5555-555555555555",
    [string]$ConsultantPhone = "+70002222222",
    [string]$ConsultantPassword = "password",
    [switch]$SkipLongSlaWait
)

$ErrorActionPreference = "Stop"
$BASE = "$HostRoot/api/v1"

function Step($msg) {
    Write-Host ""
    Write-Host "=== $msg ===" -ForegroundColor Cyan
}

function Info($msg) {
    Write-Host "[INFO] $msg" -ForegroundColor Yellow
}

function Ok($msg) {
    Write-Host "[OK] $msg" -ForegroundColor Green
}

function Fail($msg) {
    Write-Host "[FAIL] $msg" -ForegroundColor Red
    exit 1
}

function Invoke-JsonPost($url, $bodyObj, $headers = @{}) {
    $json = $bodyObj | ConvertTo-Json -Depth 10
    return Invoke-RestMethod -Method POST -Uri $url -ContentType "application/json" -Headers $headers -Body $json
}

function Sleep-WithHint($seconds, $hint) {
    Info "$hint (wait $seconds sec)"
    Start-Sleep -Seconds $seconds
}

Step "0) Check gateway + SockJS"
try {
    $wsInfo = Invoke-WebRequest -Uri "$HostRoot/ws/info" -Method GET -UseBasicParsing
    if ($wsInfo.StatusCode -eq 200) {
        Ok "GET /ws/info -> 200"
    } else {
        Info "GET /ws/info returned $($wsInfo.StatusCode), continue"
    }
} catch {
    Info "GET /ws/info check failed: $($_.Exception.Message)"
    Info "Continue anyway, main API flow is the source of truth"
}

Step "1) Consultant login"
try {
    $auth = Invoke-JsonPost "$BASE/auth/login" @{
        phone_number = $ConsultantPhone
        password     = $ConsultantPassword
    }
} catch {
    Fail "Login failed: $($_.Exception.Message)"
}

if (-not $auth.access_token) {
    Fail "No access_token in login response"
}

$TOKEN = $auth.access_token
$AUTH_HEADERS = @{ Authorization = "Bearer $TOKEN" }
Ok "Login success, token received"

Step "2) CREATE request #1 (CREATED -> WAITING -> ESCALATED -> ASSIGNED -> COMPLETED)"
$r1 = Invoke-JsonPost "$BASE/requests" @{ qr_token = $QrToken }

if (-not $r1.id -or -not $r1.client_session_token) {
    Fail "Create #1 response has no id/client_session_token"
}

$REQ1 = $r1.id
$SESSION1 = $r1.client_session_token
Ok "REQ1: $REQ1"
Ok "SESSION1: $SESSION1"
Info "Subscribe in DevTools to /queue/request/$SESSION1 and store/department topics"

if (-not $SkipLongSlaWait) {
    Step "3) SLA wait"
    Sleep-WithHint 190 "Expect REQUEST_WAITING"
    Sleep-WithHint 130 "Expect REQUEST_ESCALATED"
} else {
    Step "3) Skip SLA wait"
    Info "WAITING/ESCALATED events may be missing"
}

Step "4) ASSIGN REQ1"
try {
    Invoke-RestMethod -Method POST -Uri "$BASE/requests/$REQ1/assign" -Headers $AUTH_HEADERS | Out-Null
    Ok "ASSIGN done"
} catch {
    Fail "ASSIGN failed: $($_.Exception.Message)"
}

Step "5) COMPLETE REQ1"
try {
    Invoke-RestMethod -Method POST -Uri "$BASE/requests/$REQ1/complete" -Headers $AUTH_HEADERS | Out-Null
    Ok "COMPLETE done"
} catch {
    Fail "COMPLETE failed: $($_.Exception.Message)"
}

Step "6) CREATE request #2 + CANCEL"
$r2 = Invoke-JsonPost "$BASE/requests" @{ qr_token = $QrToken }
$REQ2 = $r2.id
$SESSION2 = $r2.client_session_token

if (-not $REQ2 -or -not $SESSION2) {
    Fail "Create #2 response has no id/client_session_token"
}

Ok "REQ2: $REQ2"
Ok "SESSION2: $SESSION2"

try {
    Invoke-RestMethod -Method POST -Uri "$BASE/requests/$REQ2/cancel?session=$SESSION2" | Out-Null
    Ok "CANCEL done"
} catch {
    Fail "CANCEL failed: $($_.Exception.Message)"
}

Step "7) CREATE request #3 -> ASSIGN -> REMIND -> REASSIGN"
$r3 = Invoke-JsonPost "$BASE/requests" @{ qr_token = $QrToken }
$REQ3 = $r3.id
$SESSION3 = $r3.client_session_token

if (-not $REQ3 -or -not $SESSION3) {
    Fail "Create #3 response has no id/client_session_token"
}

Ok "REQ3: $REQ3"
Ok "SESSION3: $SESSION3"

try {
    Invoke-RestMethod -Method POST -Uri "$BASE/requests/$REQ3/assign" -Headers $AUTH_HEADERS | Out-Null
    Ok "REQ3 ASSIGN done"
} catch {
    Fail "REQ3 ASSIGN failed: $($_.Exception.Message)"
}

Sleep-WithHint 70 "REMIND window (>=1 min)"
try {
    Invoke-RestMethod -Method POST -Uri "$BASE/requests/$REQ3/remind?session=$SESSION3" | Out-Null
    Ok "REMIND done (WS event may be absent)"
} catch {
    Info "REMIND returned error: $($_.Exception.Message)"
}

Sleep-WithHint 130 "REASSIGN window (>=3 min)"
try {
    Invoke-RestMethod -Method POST -Uri "$BASE/requests/$REQ3/reassign?session=$SESSION3&reason=prod_ws_manual_test" | Out-Null
    Ok "REASSIGN done"
} catch {
    Fail "REASSIGN failed: $($_.Exception.Message)"
}

Step "Done"
Write-Host "Summary:" -ForegroundColor Magenta
Write-Host "REQ1=$REQ1 SESSION1=$SESSION1" -ForegroundColor White
Write-Host "REQ2=$REQ2 SESSION2=$SESSION2" -ForegroundColor White
Write-Host "REQ3=$REQ3 SESSION3=$SESSION3" -ForegroundColor White
