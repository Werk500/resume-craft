# ============================================================
# Microservices shutdown script
# Stops services by port + JAR name matching (safe termination)
# ============================================================

param(
    [switch]$Force  # Force kill even if JAR name doesn't match
)

function Get-ProcessCommandLine {
    param([int]$ProcessId)
    $p = Get-CimInstance Win32_Process -Filter "ProcessId = $ProcessId" -ErrorAction SilentlyContinue
    if ($p) { return $p.CommandLine }
    return $null
}

function Test-PortListening {
    param([int]$Port)
    $conn = netstat -ano | Select-String ":$Port.*LISTENING" | Select-Object -First 1
    return ($conn -ne $null)
}

$services = @(
    @{ name = 'auth-service';        port = 8081; jar = 'resumecraft-auth' },
    @{ name = 'resume-service';      port = 8082; jar = 'resumecraft-resume' },
    @{ name = 'job-match-service';   port = 8083; jar = 'resumecraft-job-match' },
    @{ name = 'application-service'; port = 8084; jar = 'resumecraft-application' },
    @{ name = 'gateway';             port = 8080; jar = 'resume-craft-gateway' }
)

$stopped = @()
$notRunning = @()
$errors = @()

Write-Host "Stopping ResumeCraft microservices..." -ForegroundColor Cyan

foreach ($s in $services) {
    $conn = netstat -ano | Select-String ":$($s.port).*LISTENING" | Select-Object -First 1
    
    if (-not $conn) {
        Write-Host "[SKIP] $($s.name) (port $($s.port)) is not running" -ForegroundColor Gray
        $notRunning += $s.name
        continue
    }
    
    $procId = ($conn -split '\s+')[-1]
    
    try {
        $proc = Get-Process -Id $procId -ErrorAction Stop
        $cmdLine = Get-ProcessCommandLine -ProcessId $procId
        
        # Check if it's our service (JAR name in command line)
        if ($Force -or $cmdLine -like "*$($s.jar)*") {
            Write-Host "[STOP] $($s.name) (port $($s.port), PID: $procId)" -ForegroundColor Yellow
            Stop-Process -Id $procId -Force
            $stopped += $s.name
        } else {
            Write-Host "[SKIP] Port $($s.port) is used by non-service process (PID: $procId)" -ForegroundColor Gray
            $errors += "$($s.name): port $($s.port) occupied by other process (PID: $procId)"
        }
    } catch {
        Write-Host "[ERROR] Failed to stop $($s.name) (PID: $procId): $_" -ForegroundColor Red
        $errors += "$($s.name): $_"
    }
}

# Double-check all processes are actually gone
Start-Sleep -Milliseconds 500

Write-Host ""
Write-Host "================================" -ForegroundColor Cyan
Write-Host "  SHUTDOWN SUMMARY" -ForegroundColor Cyan
Write-Host "================================" -ForegroundColor Cyan
Write-Host "Stopped: $($stopped -join ', ')" -ForegroundColor Green
if ($notRunning) {
    Write-Host "Not running: $($notRunning -join ', ')" -ForegroundColor Gray
}
if ($errors) {
    Write-Host "Errors:" -ForegroundColor Red
    $errors | ForEach-Object { Write-Host "  $_" -ForegroundColor Red }
}

# Verify no ports are still listening
$stillRunning = @()
foreach ($s in $services) {
    if (Test-PortListening $s.port) {
        $stillRunning += "$($s.name):$($s.port)"
    }
}

if ($stillRunning) {
    Write-Host ""
    Write-Host "[WARNING] These services might still be running:" -ForegroundColor Yellow
    $stillRunning | ForEach-Object { Write-Host "  $_" -ForegroundColor Yellow }
    Write-Host "Use -Force to kill all processes on these ports" -ForegroundColor Yellow
} else {
    Write-Host ""
    Write-Host "All services stopped successfully!" -ForegroundColor Green
}

$exitCode = 0
if ($errors.Count -gt 0) { $exitCode = 1 }
if ($stillRunning) { $exitCode = 1 }
exit $exitCode
