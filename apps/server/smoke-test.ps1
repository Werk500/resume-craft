# ============================================================
# Smoke Test Script - ResumeCraft Microservices
# Validates gateway routing to all 4 business services
# ============================================================

param(
    [string]$BaseUrl = "http://localhost:8080",
    [switch]$Verbose
)

$exitCode = 0
$testResults = @()
$passedCount = 0
$failedCount = 0
$timestamp = Get-Date -Format "yyyyMMddHHmmss"
$testUser = "smoke_$timestamp"
$testPassword = "Smoke@2026"

# ---- Helper functions ----
function Write-TestResult {
    param(
        [string]$TestName,
        [bool]$Passed,
        [string]$Message = ""
    )
    
    $status = if ($Passed) { "[PASS]" } else { "[FAIL]" }
    $color = if ($Passed) { "Green" } else { "Red" }
    
    if ($Message) {
        Write-Host "$status $TestName - $Message" -ForegroundColor $color
    } else {
        Write-Host "$status $TestName" -ForegroundColor $color
    }
    
    $script:testResults += @{
        Name = $TestName
        Passed = $Passed
        Message = $Message
    }
    if ($Passed) {
        $script:passedCount++
    } else {
        $script:failedCount++
    }
    
    if (-not $Passed) {
        $script:exitCode = 1
    }
}

function Invoke-ApiRequest {
    param(
        [string]$Method = "GET",
        [string]$Endpoint,
        [string]$Body = "",
        [string]$Token = ""
    )
    
    $url = "$BaseUrl$Endpoint"
    $headers = @{
        "Content-Type" = "application/json"
        "Accept" = "application/json"
    }
    
    if ($Token) {
        $headers["Authorization"] = "Bearer $Token"
    }
    
    $params = @{
        Method = $Method
        Uri = $url
        Headers = $headers
        ContentType = "application/json"
        ErrorAction = "Stop"
        TimeoutSec = 10
    }
    
    if ($Body) {
        $params["Body"] = $Body
    }
    
    if ($Verbose) {
        Write-Host "[DEBUG] $Method $url" -ForegroundColor Gray
        if ($Body) { Write-Host "[DEBUG] Body: $Body" -ForegroundColor Gray }
    }
    
    try {
        $response = Invoke-RestMethod @params
        $statusCode = 200  # Invoke-RestMethod throws on 4xx/5xx
        return @{
            Success = $true
            StatusCode = $statusCode
            Body = $response
            RawResponse = $response
        }
    } catch {
        $statusCode = $null
        try { $statusCode = [int]$_.Exception.Response.StatusCode } catch { $statusCode = 0 }
        # PowerShell 7: 错误响应体在 ErrorDetails.Message；PS 5.1 也可能没有
        $responseBody = $_.ErrorDetails.Message
        if ($responseBody) {
            try { $responseBody = $responseBody | ConvertFrom-Json } catch { }
        }
        return @{
            Success = $false
            StatusCode = $statusCode
            Body = $responseBody
            RawResponse = $_.Exception.Message
        }
    }
}

# ---- Main test execution ----
Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  RESUMECRAFT SMOKE TEST" -ForegroundColor Cyan
Write-Host "  Base URL: $BaseUrl" -ForegroundColor Cyan
Write-Host "  Test User: $testUser" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# ---- Step 1: Check Gateway Health ----
Write-Host "[1] Gateway Health Check" -ForegroundColor Yellow

$response = Invoke-ApiRequest -Endpoint "/actuator/health"

if ($response.Success -and $response.Body.status -eq "UP") {
    Write-TestResult -TestName "Gateway Health" -Passed $true -Message "status=UP"
} else {
    Write-TestResult -TestName "Gateway Health" -Passed $false -Message "Failed or not UP"
}

# ---- Step 2: Register Test User ----
Write-Host ""
Write-Host "[2] User Registration" -ForegroundColor Yellow

$registerBody = @{
    username = $testUser
    password = $testPassword
    nickname = "Smoke User"
} | ConvertTo-Json

$response = Invoke-ApiRequest -Method "POST" -Endpoint "/api/v1/auth/register" -Body $registerBody

if ($response.Success -and $response.Body.code -eq 200) {
    Write-TestResult -TestName "Register User" -Passed $true -Message "User created: $testUser"
} elseif ($response.Success -and $response.Body.message -like "*already exists*") {
    Write-TestResult -TestName "Register User" -Passed $true -Message "User already exists (continuing)"
} else {
    Write-TestResult -TestName "Register User" -Passed $false -Message "Registration failed (HTTP $($response.StatusCode))"
}

# ---- Step 3: Login and Get Token ----
Write-Host ""
Write-Host "[3] User Login" -ForegroundColor Yellow

$loginBody = @{
    username = $testUser
    password = $testPassword
} | ConvertTo-Json

$response = Invoke-ApiRequest -Method "POST" -Endpoint "/api/v1/auth/login" -Body $loginBody

if ($response.Success -and $response.Body.code -eq 200 -and $response.Body.data.token) {
    $token = $response.Body.data.token
    Write-TestResult -TestName "User Login" -Passed $true -Message "Token obtained"
} else {
    Write-TestResult -TestName "User Login" -Passed $false -Message "Login failed (HTTP $($response.StatusCode))"
    $token = $null
}

# ---- Step 4: Get Current User Info (Auth Service) ----
Write-Host ""
Write-Host "[4] Get Current User (Auth Service)" -ForegroundColor Yellow

if ($token) {
    $response = Invoke-ApiRequest -Endpoint "/api/v1/auth/me" -Token $token
    
    if ($response.Success -and $response.Body.code -eq 200 -and $response.Body.data.userId) {
        Write-TestResult -TestName "Auth Service (GET /me)" -Passed $true -Message "userId: $($response.Body.data.userId)"
    } else {
        Write-TestResult -TestName "Auth Service (GET /me)" -Passed $false -Message "Failed (HTTP $($response.StatusCode))"
    }
} else {
    Write-TestResult -TestName "Auth Service (GET /me)" -Passed $false -Message "Skipped (no token)"
}

# ---- Step 5: Get Resume (Resume Service) ----
Write-Host ""
Write-Host "[5] Resume Service" -ForegroundColor Yellow

if ($token) {
    $response = Invoke-ApiRequest -Endpoint "/api/v1/resume" -Token $token
    
    if ($response.Success -and $response.Body.code -eq 200) {
        $count = if ($response.Body.data) { $response.Body.data.Count } else { 0 }
        Write-TestResult -TestName "Resume Service (GET /resume)" -Passed $true -Message "Returned $count items"
    } else {
        Write-TestResult -TestName "Resume Service (GET /resume)" -Passed $false -Message "Failed (HTTP $($response.StatusCode))"
    }
} else {
    Write-TestResult -TestName "Resume Service (GET /resume)" -Passed $false -Message "Skipped (no token)"
}

# ---- Step 6: Get Job Matches (Job Match Service) ----
Write-Host ""
Write-Host "[6] Job Match Service" -ForegroundColor Yellow

if ($token) {
    $response = Invoke-ApiRequest -Endpoint "/api/v1/job" -Token $token
    
    if ($response.Success -and $response.Body.code -eq 200) {
        $count = if ($response.Body.data) { $response.Body.data.Count } else { 0 }
        Write-TestResult -TestName "Job Match Service (GET /job)" -Passed $true -Message "Returned $count items"
    } else {
        Write-TestResult -TestName "Job Match Service (GET /job)" -Passed $false -Message "Failed (HTTP $($response.StatusCode))"
    }
} else {
    Write-TestResult -TestName "Job Match Service (GET /job)" -Passed $false -Message "Skipped (no token)"
}

# ---- Step 7: Get Applications (Application Service) ----
Write-Host ""
Write-Host "[7] Application Service" -ForegroundColor Yellow

if ($token) {
    $response = Invoke-ApiRequest -Endpoint "/api/v1/application" -Token $token
    
    if ($response.Success -and $response.Body.code -eq 200) {
        $count = if ($response.Body.data) { $response.Body.data.Count } else { 0 }
        Write-TestResult -TestName "Application Service (GET /application)" -Passed $true -Message "Returned $count items"
    } else {
        Write-TestResult -TestName "Application Service (GET /application)" -Passed $false -Message "Failed (HTTP $($response.StatusCode))"
    }
} else {
    Write-TestResult -TestName "Application Service (GET /application)" -Passed $false -Message "Skipped (no token)"
}

# ---- Summary ----
Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  TEST SUMMARY" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

$total = $passedCount + $failedCount

Write-Host "Total Tests : $total" -ForegroundColor White
Write-Host "Passed      : $passedCount" -ForegroundColor Green
Write-Host "Failed      : $failedCount" -ForegroundColor Red

if ($failedCount -eq 0) {
    Write-Host ""
    Write-Host "[SUCCESS] All smoke tests passed!" -ForegroundColor Green
} else {
    Write-Host ""
    Write-Host "[FAILURE] $failedCount test(s) failed!" -ForegroundColor Red
    Write-Host ""
    Write-Host "Failed tests:" -ForegroundColor Yellow
    $testResults | Where-Object { -not $_.Passed } | ForEach-Object {
        Write-Host "  - $($_.Name): $($_.Message)" -ForegroundColor Red
    }
}

# ---- Cleanup note ----
Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  CLEANUP" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "Test user '$testUser' created in database." -ForegroundColor Yellow
Write-Host "To clean up: manually delete username like 'smoke_%' from sys_user table." -ForegroundColor Yellow
Write-Host ""

# ---- Exit with appropriate code ----
exit $exitCode
