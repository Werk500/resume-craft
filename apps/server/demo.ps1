# ============================================================
# 一键演示：注册/登录 → 创建简历 → 诊断 → 匹配 → 定向优化 → 提升对比 → 投递
#
# 用法：
#   pwsh ./demo.ps1                  # 需先启动后端（start-services.ps1）
#   pwsh ./demo.ps1 -StartServices   # 自动启动后端（跳过构建）
#   pwsh ./demo.ps1 -KeepData        # 保留演示数据（默认结束后清理演示简历）
# ============================================================
param(
    [string]$BaseUrl = "http://localhost:8080",
    [string]$Username = "demo_user",
    [string]$Password = "Demo@2026",
    [switch]$StartServices,
    [switch]$KeepData
)

$ErrorActionPreference = "Stop"

function Info($message) { Write-Host "[DEMO] $message" -ForegroundColor Cyan }
function Ok($message) { Write-Host "  OK  $message" -ForegroundColor Green }
function Warn($message) { Write-Host "  !!  $message" -ForegroundColor Yellow }
function Fail($message) { Write-Host "[FAIL] $message" -ForegroundColor Red; exit 1 }

function Invoke-Api {
    param(
        [string]$Method,
        [string]$Path,
        $Body = $null,
        [string]$Token
    )

    $headers = @{}
    if ($Token) { $headers["Authorization"] = "Bearer $Token" }

    $req = @{ Method = $Method; Uri = "$BaseUrl$Path"; TimeoutSec = 180 }
    if ($headers.Count -gt 0) { $req.Headers = $headers }
    if ($null -ne $Body) {
        $req.ContentType = "application/json"
        $req.Body = ($Body | ConvertTo-Json -Depth 8)
    }

    try {
        return Invoke-RestMethod @req
    }
    catch {
        $status = $_.Exception.Response.StatusCode.value__
        $detail = $_.ErrorDetails.Message
        if (-not $detail) { $detail = $_.Exception.Message }
        throw "HTTP $status $detail"
    }
}

# ---------- 0. 服务健康检查 ----------
if ($StartServices) {
    Info "启动后端服务（-SkipBuild）..."
    & (Join-Path $PSScriptRoot "start-services.ps1") -SkipBuild
}

Info "检查网关健康状态: $BaseUrl"
try {
    $health = Invoke-RestMethod -Uri "$BaseUrl/actuator/health" -TimeoutSec 10
    if ($health.status -ne "UP") { Fail "网关健康状态异常: $($health.status)" }
    Ok "gateway UP"
}
catch {
    Fail "无法连接网关，请先运行 pwsh ./start-services.ps1（或使用 -StartServices）"
}

# ---------- 1. 注册 / 登录 ----------
Info "准备演示账号: $Username"
try {
    Invoke-Api -Method Post -Path "/api/v1/auth/register" `
        -Body @{ username = $Username; password = $Password; nickname = "演示用户" } | Out-Null
    Ok "注册成功"
}
catch {
    Warn "注册跳过（账号可能已存在）"
}

$login = Invoke-Api -Method Post -Path "/api/v1/auth/login" `
    -Body @{ username = $Username; password = $Password }
$token = $login.data.token
Ok "登录成功, userId=$($login.data.user.userId)"

# ---------- 2. 创建简历 ----------
Info "创建演示简历（from-text，免文件上传）"
$resumeText = @"
# 张三

电话：13800138000 | 邮箱：zhangsan@example.com | 北京 | 2027届应届生

## 教育背景
北京邮电大学 软件工程 本科 2027届

## 专业技能
Java、Spring Boot、MyBatis、MySQL、Redis、Git

## 项目经历
### 校园二手交易平台（2025.03 - 2025.08）
负责后端开发，使用 Spring Boot + MyBatis 实现用户、商品、订单模块；MySQL 存储数据，Redis 缓存热点商品。

## 自我评价
学习能力强，喜欢研究后端技术。
"@

$resume = Invoke-Api -Method Post -Path "/api/v1/resume/from-text" -Token $token `
    -Body @{ fileName = "demo-resume.md"; rawText = $resumeText }
$resumeId = $resume.data.id
Ok "简历创建成功: resumeId=$resumeId"

# ---------- 3. AI 诊断 ----------
Info "AI 诊断（约 10~30 秒）"
$diagnosis = Invoke-Api -Method Get -Path "/api/v1/diagnose/$resumeId" -Token $token
Ok "诊断完成: 总分=$($diagnosis.data.totalScore) 建议数=$($diagnosis.data.suggestions.Count)"

# ---------- 4. 选择岗位 ----------
Info "从岗位库选择目标 JD"
$jobs = (Invoke-Api -Method Get -Path "/api/v1/job" -Token $token).data
$job = $jobs | Where-Object { $_.title -match "后端" } | Select-Object -First 1
if (-not $job) { $job = $jobs | Where-Object { $_.title -match "Java" } | Select-Object -First 1 }
if (-not $job) { $job = $jobs | Select-Object -First 1 }
if (-not $job) { Fail "岗位库为空，请先导入 JD 种子数据" }

$jobId = $job.id
Ok "目标岗位: #$jobId $($job.company) · $($job.title) · $($job.location)"

# ---------- 5. 优化前匹配 ----------
Info "匹配（优化前）"
$before = (Invoke-Api -Method Post -Path "/api/v1/match/body" -Token $token `
        -Body @{ resumeId = $resumeId; jobId = $jobId; forceRefresh = $true }).data
Ok "综合=$($before.overallScore) 关键词=$($before.keywordCoverage) 语义=$($before.semanticSimilarity) 硬性=$($before.hardRequirementScore)"

# ---------- 6. 定向优化 ----------
Info "定向优化（AI 改写，约 10~60 秒）"
$targeted = (Invoke-Api -Method Post -Path "/api/v1/optimize/$resumeId/targeted?jobId=$jobId" -Token $token).data
Ok "生成版本 #$($targeted.versionId)  改动=$($targeted.changes.Count) 条  缺口=$($targeted.gaps.Count) 条"

# ---------- 7. 优化后匹配 ----------
Info "匹配（优化后版本）"
$after = (Invoke-Api -Method Post -Path "/api/v1/match/body" -Token $token `
        -Body @{ resumeId = $resumeId; jobId = $jobId; versionId = $targeted.versionId; forceRefresh = $true }).data
$delta = [math]::Round($after.overallScore - $before.overallScore)
Ok "综合=$($after.overallScore)（提升 $delta）关键词=$($after.keywordCoverage) 语义=$($after.semanticSimilarity)"

# ---------- 8. 投递记录 ----------
Info "记录投递并统计"
$application = Invoke-Api -Method Post -Path "/api/v1/application" -Token $token -Body @{
    resumeVersionId = $targeted.versionId
    jobId           = $jobId
    appliedAt       = (Get-Date -Format "yyyy-MM-dd")
    channel         = "一键演示"
    status          = "pending"
    notes           = "demo.ps1 自动创建"
}
$stats = (Invoke-Api -Method Get -Path "/api/v1/application/stats" -Token $token).data
Ok "投递记录 #$($application.data.id) 已创建；统计 total=$($stats.total) pending=$($stats.pending)"

# ---------- 9. 汇总 ----------
Write-Host ""
Write-Host "==================== 演示结果 ====================" -ForegroundColor Cyan
Write-Host ("账号     : {0} / {1}" -f $Username, $Password)
Write-Host ("简历     : #{0}  (前端 http://localhost:3001/resume/{0})" -f $resumeId)
Write-Host ("目标岗位 : #{0} {1} · {2}" -f $jobId, $job.company, $job.title)
Write-Host ("诊断总分 : {0}" -f $diagnosis.data.totalScore)
Write-Host ("匹配提升 : {0} -> {1}  (delta {2})" -f $before.overallScore, $after.overallScore, $delta)
Write-Host ("优化版本 : #{0}" -f $targeted.versionId)
Write-Host ("投递统计 : total={0}" -f $stats.total)
Write-Host "=================================================" -ForegroundColor Cyan

# ---------- 10. 清理 ----------
if (-not $KeepData) {
    Info "清理演示简历（级联清理版本/诊断/匹配/投递）"
    Invoke-Api -Method Delete -Path "/api/v1/resume/$resumeId" -Token $token | Out-Null
    Ok "已清理 resumeId=$resumeId（使用 -KeepData 可保留）"
}
else {
    Warn "已保留演示数据（-KeepData）"
}
