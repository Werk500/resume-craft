# ============================================================
# Microservices startup script (Enhanced)
# 
# Parameters:
#   -SkipBuild   # Skip mvn package
#   -NoGateway   # Start business services only (debug mode)
# ============================================================

param(
    [switch]$SkipBuild,
    [switch]$NoGateway
)

# ---- Helper functions ----
function Write-ErrorExit {
    param([string]$Message)
    Write-Host "[ERROR] $Message" -ForegroundColor Red
    exit 1
}

function Write-Success {
    param([string]$Message)
    Write-Host "[OK] $Message" -ForegroundColor Green
}

function Write-Info {
    param([string]$Message)
    Write-Host "[INFO] $Message" -ForegroundColor Cyan
}

function Test-PortListening {
    param([int]$Port)
    $conn = netstat -ano | Select-String ":$Port.*LISTENING" | Select-Object -First 1
    return ($conn -ne $null)
}

function Get-ProcessCommandLine {
    param([int]$ProcessId)
    $p = Get-CimInstance Win32_Process -Filter "ProcessId = $ProcessId" -ErrorAction SilentlyContinue
    if ($p) { return $p.CommandLine }
    return $null
}

function Get-ProcessByPortAndJar {
    param([int]$Port, [string]$JarName)
    $conn = netstat -ano | Select-String ":$Port.*LISTENING" | Select-Object -First 1
    if ($conn) {
        $procId = ($conn -split '\s+')[-1]
        try {
            $proc = Get-Process -Id $procId -ErrorAction SilentlyContinue
            $cmdLine = Get-ProcessCommandLine -ProcessId $procId
            # CIM 可能因权限被拒返回 null；这些端口是本项目专用，端口上是 java 即视为本服务旧进程
            $isOurs = $cmdLine -like "*$JarName*" -or ($null -eq $cmdLine -and $proc.Name -eq "java")
            if ($proc -and $isOurs) {
                return $proc
            }
        } catch {
            # Process might have died
        }
    }
    return $null
}

function Wait-ForHealth {
    param([string]$Name, [int]$Port, [int]$TimeoutSeconds = 90)
    
    $url = "http://localhost:$Port/actuator/health"
    $startTime = Get-Date
    $elapsed = 0
    
    Write-Info "Waiting for $Name (port $Port) health check..."
    
    while ($elapsed -lt $TimeoutSeconds) {
        try {
            $response = Invoke-RestMethod -Uri $url -Method Get -TimeoutSec 2 -ErrorAction Stop
            if ($response.status -eq "UP") {
                Write-Success "$Name is UP (took ${elapsed}s)"
                return $true
            }
        } catch {
            # Still starting up
        }
        
        Start-Sleep -Seconds 2
        $elapsed = [int]((Get-Date) - $startTime).TotalSeconds
    }
    
    Write-Host "[ERROR] $Name failed to start within ${TimeoutSeconds}s" -ForegroundColor Red
    
    # Print last 20 lines of error log
    $errLog = Join-Path $PSScriptRoot "logs\$Name.err.log"
    if (Test-Path $errLog) {
        Write-Host "Last 20 lines of error log:" -ForegroundColor Yellow
        Get-Content $errLog -Tail 20 | ForEach-Object { Write-Host $_ -ForegroundColor Red }
    }
    
    return $false
}

# ---- Main script ----
$root = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$envFile = Join-Path $root '.env'

# 1. Load .env
if (Test-Path $envFile) {
    Get-Content $envFile | ForEach-Object {
        if ($_ -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*?)\s*$') {
            [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2], 'Process')
        }
    }
    Write-Success ".env loaded"
} else {
    Write-Warning ".env not found (AI features will use placeholders)"
}

Set-Location $PSScriptRoot

# 2. Dependency checks
Write-Info "Checking dependencies..."

# Check ports: MySQL (3306), Redis (6379), Nacos (8848)
$requiredPorts = @(
    @{Port = 3306; Name = "MySQL"},
    @{Port = 6379; Name = "Redis"},
    @{Port = 8848; Name = "Nacos"}
)

$allPortsOk = $true
foreach ($req in $requiredPorts) {
    if (Test-PortListening $req.Port) {
        Write-Success "$($req.Name) (port $($req.Port)) is listening"
    } else {
        Write-Host "[ERROR] $($req.Name) (port $($req.Port)) is NOT listening" -ForegroundColor Red
        $allPortsOk = $false
    }
}

if (-not $allPortsOk) {
    Write-Host @"

Please start required services first:
  - MySQL:  docker run -d --name mysql -p 3306:3306 -e MYSQL_ROOT_PASSWORD=root mysql:8
  - Redis:  docker run -d --name redis -p 6379:6379 redis:alpine
  - Nacos:  docker run -d --name nacos -p 8848:8848 nacos/nacos-server:v2.3.0

Or use docker-compose up -d mysql redis nacos
"@ -ForegroundColor Yellow
    exit 1
}

# Check Java
$javaExe = $null
$javaHome = [Environment]::GetEnvironmentVariable('JAVA_HOME', 'Machine')
if ($javaHome) {
    $javaCandidate = Join-Path $javaHome "bin\java.exe"
    if (Test-Path $javaCandidate) {
        $javaExe = $javaCandidate
    }
}

if (-not $javaExe) {
    # Try PATH
    $javaCandidate = (Get-Command java -ErrorAction SilentlyContinue).Source
    if ($javaCandidate) {
        $javaExe = $javaCandidate
    }
}

if (-not $javaExe) {
    Write-ErrorExit "Java not found. Please install JDK 17 and set JAVA_HOME"
}

# Verify Java version
try {
    $javaVersion = & $javaExe -version 2>&1 | Select-String "version" | Select-Object -First 1
    if ($javaVersion -notmatch "17\.") {
        Write-ErrorExit "Java 17 required, but found: $javaVersion"
    }
    Write-Success "Java 17 found: $javaExe"
} catch {
    Write-ErrorExit "Failed to execute Java: $_"
}

# 3. Build (unless -SkipBuild)
if (-not $SkipBuild) {
    Write-Info "Building microservices with Maven..."
    mvn -B -s settings-dev.xml -DskipTests package
    if ($LASTEXITCODE -ne 0) {
        Write-ErrorExit "Maven build failed (exit code: $LASTEXITCODE)"
    }
    Write-Success "Build completed"
} else {
    Write-Info "Skipping build (-SkipBuild specified)"
}

# 4. Create logs directory
New-Item -ItemType Directory -Force -Path logs | Out-Null

# 5. Define services (business services first, gateway last)
$services = @(
    @{ name = 'auth-service';        port = 8081; jar = 'auth\target\resumecraft-auth-0.1.0-SNAPSHOT.jar' },
    @{ name = 'resume-service';      port = 8082; jar = 'resume\target\resumecraft-resume-0.1.0-SNAPSHOT.jar' },
    @{ name = 'job-match-service';   port = 8083; jar = 'job-match\target\resumecraft-job-match-0.1.0-SNAPSHOT.jar' },
    @{ name = 'application-service'; port = 8084; jar = 'application\target\resumecraft-application-0.1.0-SNAPSHOT.jar' }
)

if (-not $NoGateway) {
    $services += @{ name = 'gateway'; port = 8080; jar = 'gateway\target\resume-craft-gateway-0.1.0-SNAPSHOT.jar' }
}

$processes = @()
$startTimes = @{}

# 6. Start services one by one
foreach ($s in $services) {
    $jarPath = Join-Path $PSScriptRoot $s.jar
    
    # Check if JAR exists
    if (-not (Test-Path $jarPath)) {
        Write-ErrorExit "JAR not found: $jarPath"
    }
    
    # Kill existing process for this service only (by port + jar name)
    $existingProc = Get-ProcessByPortAndJar -Port $s.port -JarName $s.jar
    if ($existingProc) {
        Write-Info "Stopping existing $($s.name) (PID: $($existingProc.Id))"
        Stop-Process -Id $existingProc.Id -Force -ErrorAction SilentlyContinue
        Start-Sleep -Milliseconds 500
    }
    
    # Start service
    $outLog = Join-Path $PSScriptRoot "logs\$($s.name).out.log"
    $errLog = Join-Path $PSScriptRoot "logs\$($s.name).err.log"
    
    Write-Info "Starting $($s.name) on port $($s.port)..."
    
    $process = Start-Process -FilePath $javaExe -ArgumentList "-jar", $jarPath -WindowStyle Hidden -PassThru -RedirectStandardOutput $outLog -RedirectStandardError $errLog
    
    $processes += @{
        Name = $s.name
        Port = $s.port
        Process = $process
        Jar = $s.jar
    }
    $startTimes[$s.name] = Get-Date
    
    Start-Sleep -Milliseconds 500
}

Write-Info "All services started. Waiting for health checks..."

# 7. Health checks (wait for each service)
$healthResults = @{}
$allHealthy = $true

foreach ($s in $services) {
    $result = Wait-ForHealth -Name $s.name -Port $s.port -TimeoutSeconds 90
    $healthResults[$s.name] = $result
    if (-not $result) {
        $allHealthy = $false
    }
}

if (-not $allHealthy) {
    Write-ErrorExit "One or more services failed health check"
}

# 8. Wait for Nacos registration
Write-Info "Waiting 8 seconds for services to register in Nacos..."
Start-Sleep -Seconds 8

# 9. Summary
Write-Host ""
Write-Host "================================" -ForegroundColor Cyan
Write-Host "  SERVICES STATUS" -ForegroundColor Cyan
Write-Host "================================" -ForegroundColor Cyan
Write-Host ""

$serviceList = $services | ForEach-Object {
    $name = $_.name
    $port = $_.port
    $proc = ($processes | Where-Object { $_.Name -eq $name }).Process
    $procId = if ($proc) { $proc.Id } else { "N/A" }
    $health = if ($healthResults[$name]) { "UP" } else { "DOWN" }
    
    Write-Host ("{0,-20} :{1,-6} PID:{2,-8} Health: {3}" -f $name, $port, $procId, $health) -ForegroundColor $(if ($health -eq "UP") { "Green" } else { "Red" })
}

Write-Host ""
Write-Host "Total services: $($services.Count)" -ForegroundColor Yellow
if (-not $NoGateway) {
    Write-Host "Gateway URL: http://localhost:8080" -ForegroundColor Yellow
}
Write-Host "Nacos Console: http://localhost:8848/nacos" -ForegroundColor Yellow
Write-Host "Logs directory: $PSScriptRoot\logs" -ForegroundColor Yellow
Write-Host ""

if ($NoGateway) {
    Write-Host "Note: Gateway is not started (-NoGateway)" -ForegroundColor Yellow
}
