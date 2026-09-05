# ============================================================
# Microservices startup script (Step 3)
# 1. Load .env into environment
# 2. Start 4 business services + gateway as detached processes
# ============================================================

# ---- Load .env ----
$root = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$envFile = Join-Path $root '.env'
if (Test-Path $envFile) {
    Get-Content $envFile | ForEach-Object {
        if ($_ -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*?)\s*$') {
            [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2], 'Process')
        }
    }
    Write-Host "[OK] .env loaded" -ForegroundColor Green
} else {
    Write-Warning ".env not found (AI features will use placeholders)"
}

# ---- Start services (services first, gateway last) ----
Set-Location $PSScriptRoot
$javaHome = 'C:\Program Files\Java\jdk-17.0.19\bin\java.exe'

$services = @(
    @{ name = 'auth-service';        port = 8081; jar = 'auth\target\resumecraft-auth-0.1.0-SNAPSHOT.jar' },
    @{ name = 'resume-service';      port = 8082; jar = 'resume\target\resumecraft-resume-0.1.0-SNAPSHOT.jar' },
    @{ name = 'job-match-service';   port = 8083; jar = 'job-match\target\resumecraft-job-match-0.1.0-SNAPSHOT.jar' },
    @{ name = 'application-service'; port = 8084; jar = 'application\target\resumecraft-application-0.1.0-SNAPSHOT.jar' }
)

foreach ($s in $services) {
    $conn = netstat -ano | Select-String ":$($s.port).*LISTENING" | Select-Object -First 1
    if ($conn) { $pidOld = ($conn -split '\s+')[-1]; Stop-Process -Id $pidOld -Force -ErrorAction SilentlyContinue }
    Start-Sleep -Milliseconds 500
    Start-Process -FilePath $javaHome -ArgumentList "-jar", (Join-Path $PSScriptRoot $s.jar) -WindowStyle Hidden
    Write-Output "[start] $($s.name) :$($s.port)"
}

Start-Sleep -Seconds 5
$conn = netstat -ano | Select-String ':8080.*LISTENING' | Select-Object -First 1
if ($conn) { $pidOld = ($conn -split '\s+')[-1]; Stop-Process -Id $pidOld -Force -ErrorAction SilentlyContinue }
Start-Process -FilePath $javaHome -ArgumentList "-jar", (Join-Path $PSScriptRoot 'gateway\target\resume-craft-gateway-0.1.0-SNAPSHOT.jar') -WindowStyle Hidden
Write-Output "[start] resume-craft-gateway :8080"

Write-Output ''
Write-Output '5 processes started. Wait ~50s, then check Nacos console (http://localhost:8848/nacos) for 5 services.'
