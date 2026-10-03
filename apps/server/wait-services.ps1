# ============================================================
# Wait until the microservices report UP on /actuator/health.
#
# 用法（两步启动，推荐给脚本/CI 用）：
#   pwsh apps/server/start-services.ps1 -SkipBuild -NoWait   # 起完立刻返回
#   pwsh apps/server/wait-services.ps1                       # 轮询到全部 UP
#
# 为什么拆成两步：start-services.ps1 里如果直接等健康检查，调用它的父脚本
# 会被 java 子进程继承的 stdout 句柄拖住，脚本明明跑完了却不返回。
# 拆开之后 start 秒回、wait 自己轮询，任意一方被调用都不会卡。
#
# Parameters:
#   -TimeoutSeconds  轮询上限（默认 120s）
#   -Ports           要等的端口（默认 5 个服务）
# ============================================================

param(
    [int]$TimeoutSeconds = 120,
    [int[]]$Ports = @(8080, 8081, 8082, 8083, 8084)
)

$names = @{ 8080 = 'gateway'; 8081 = 'auth'; 8082 = 'resume'; 8083 = 'job-match'; 8084 = 'application' }
$deadline = (Get-Date).AddSeconds($TimeoutSeconds)

$pending = New-Object System.Collections.ArrayList
foreach ($p in $Ports) { [void]$pending.Add($p) }

Write-Host ("[wait] polling /actuator/health, timeout = " + $TimeoutSeconds + "s ...") -ForegroundColor Cyan

while ($pending.Count -gt 0 -and (Get-Date) -lt $deadline) {
    foreach ($p in @($pending)) {
        try {
            $h = Invoke-RestMethod -Uri "http://localhost:$p/actuator/health" -TimeoutSec 3
            if ($h.status -eq "UP") {
                $label = if ($names.ContainsKey($p)) { $names[$p] } else { "port $p" }
                Write-Host ("[OK]   {0,-12} :{1} UP" -f $label, $p) -ForegroundColor Green
                $pending.Remove($p)
            }
        } catch {
            # 还没起来，下一轮再试
        }
    }
    if ($pending.Count -gt 0) { Start-Sleep -Seconds 2 }
}

if ($pending.Count -eq 0) {
    Write-Host "[DONE] all services UP" -ForegroundColor Green
    exit 0
}

foreach ($p in $pending) {
    $label = if ($names.ContainsKey($p)) { $names[$p] } else { "port $p" }
    Write-Host ("[FAIL] {0,-12} :{1} still DOWN after {2}s" -f $label, $p, $TimeoutSeconds) -ForegroundColor Red
}
exit 1
