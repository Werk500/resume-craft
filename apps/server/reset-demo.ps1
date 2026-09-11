# ============================================================
# 演示数据重置：删除演示账号及其简历/版本/诊断/匹配/投递，并清理缓存
#
# 用法：
#   pwsh ./reset-demo.ps1
#   pwsh ./reset-demo.ps1 -Username demo_user
# ============================================================
param(
    [string]$Username = "demo_user",
    [string]$MysqlUser = "root",
    [string]$MysqlPassword = "123456",
    [string]$RedisPassword = "123456",
    [string]$RedisCli = ""
)

$ErrorActionPreference = "Stop"

function Info($message) { Write-Host "[RESET] $message" -ForegroundColor Cyan }
function Ok($message) { Write-Host "  OK  $message" -ForegroundColor Green }
function Warn($message) { Write-Host "  !!  $message" -ForegroundColor Yellow }
function Fail($message) { Write-Host "[FAIL] $message" -ForegroundColor Red; exit 1 }

if (-not (Get-Command mysql -ErrorAction SilentlyContinue)) {
    Fail "未找到 mysql 客户端，请确认 MySQL 已安装且在 PATH 中"
}

$query = "SELECT id FROM resume_craft.sys_user WHERE username='$Username' LIMIT 1;"
# 注意：原生命令参数必须写成 "-u$MysqlUser"（带引号），否则 PowerShell 不会展开变量
$userId = (mysql "-u$MysqlUser" "-p$MysqlPassword" -N -B -e $query 2>$null)

if (-not $userId) {
    Warn "未找到用户: $Username（跳过数据库清理）"
}
else {
    Info "清理用户 $Username (id=$userId) 的业务数据"
    $sql = "DELETE FROM resume_craft.application_record WHERE user_id = $userId;" +
           "DELETE FROM resume_craft.match_result WHERE user_id = $userId;" +
           "DELETE FROM resume_craft.diagnosis WHERE user_id = $userId;" +
           "DELETE FROM resume_craft.resume_version WHERE user_id = $userId;" +
           "DELETE FROM resume_craft.resume WHERE user_id = $userId;" +
           "DELETE FROM resume_craft.sys_user WHERE id = $userId;"
    mysql "-u$MysqlUser" "-p$MysqlPassword" -e $sql 2>$null | Out-Null
    Ok "已删除用户及其简历/版本/诊断/匹配/投递数据"
}

# ---------- 清理 Redis 缓存（resume-craft:*） ----------
if (-not $RedisCli) {
    $cmd = Get-Command redis-cli -ErrorAction SilentlyContinue
    if ($cmd) {
        $RedisCli = $cmd.Source
    }
    elseif (Test-Path "D:\JAVA001\Redis7\redis-cli.exe") {
        $RedisCli = "D:\JAVA001\Redis7\redis-cli.exe"
    }
}

if ($RedisCli) {
    $keys = & $RedisCli -a $RedisPassword --scan --pattern "resume-craft:*" 2>$null |
        Where-Object { $_ }
    if ($keys) {
        foreach ($key in $keys) {
            & $RedisCli -a $RedisPassword DEL $key 2>$null | Out-Null
        }
        Ok "已清理 Redis 缓存 $($keys.Count) 个 key"
    }
    else {
        Ok "Redis 无 resume-craft:* 缓存"
    }
}
else {
    Warn "未找到 redis-cli，跳过缓存清理（可传入 -RedisCli 指定路径）"
}

Info "重置完成"
