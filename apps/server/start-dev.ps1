# ============================================================
# 后端一键启动脚本（开发用）
# 1. 读取项目根目录 .env 注入环境变量（Spring Boot 不读 .env，必须手动注入）
# 2. 重新打包并启动
# 用法：pwsh ./start-dev.ps1   （或直接双击在终端里运行）
# ============================================================

# ---- 定位项目根目录（本脚本位于 apps/server/ 下）----
$root = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$envFile = Join-Path $root '.env'

# ---- 加载 .env（格式 KEY=VALUE，忽略 # 注释）----
if (Test-Path $envFile) {
    Get-Content $envFile | ForEach-Object {
        if ($_ -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=\s*(.*?)\s*$') {
            [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2], 'Process')
        }
    }
    Write-Host "[OK] 已从 .env 加载环境变量:" -ForegroundColor Green
    Get-Content $envFile | ForEach-Object {
        if ($_ -match '^\s*([A-Za-z_][A-Za-z0-9_]*)\s*=') {
            $name = $Matches[1]
            $value = [Environment]::GetEnvironmentVariable($name, 'Process')
            $shown = if ($value) { '***' } else { '(空)' }
            Write-Host "     $name = $shown" -ForegroundColor DarkGray
        }
    }
} else {
    Write-Warning "未找到 .env 文件: $envFile（AI 相关变量将使用 application.yml 默认值）"
}

# ---- 打包（可注释掉以加快启动）----
Write-Host "[1/2] 打包中..." -ForegroundColor Cyan
Set-Location $PSScriptRoot
mvn -B -s settings-dev.xml -DskipTests package | Out-Host

# ---- 启动 ----
Write-Host "[2/2] 启动后端 :8088 ..." -ForegroundColor Cyan
java -jar target/server-0.1.0-SNAPSHOT.jar
