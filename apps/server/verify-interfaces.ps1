# ============================================================
# 第 4 步接口验收脚本（需先启动全部服务）
# 覆盖：job/search、application/stats、resume delete 级联、
#       version export、/internal token、match/body 参数校验
# ============================================================

$Base = "http://localhost:8080"
$resumeBase = "http://localhost:8082"
$root = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$sampleResume = Join-Path $root "docs\test_resume.docx"

$exitCode = 0

function Assert-True {
    param([string]$Name, [bool]$Condition, [string]$Detail = "")
    if ($Condition) {
        Write-Host "[PASS] $Name $Detail" -ForegroundColor Green
    } else {
        Write-Host "[FAIL] $Name $Detail" -ForegroundColor Red
        $script:exitCode = 1
    }
}

function Invoke-Api {
    param([string]$Method, [string]$Uri, $Headers = @{}, $Body = $null)
    $params = @{
        Method      = $Method
        Uri         = $Uri
        Headers     = $Headers
        ContentType = "application/json"
        TimeoutSec  = 30
    }
    if ($null -ne $Body) { $params["Body"] = ($Body | ConvertTo-Json -Depth 8) }
    Invoke-RestMethod @params
}

# ---- 0. 准备测试用户 ----
$stamp = Get-Date -Format "yyyyMMddHHmmss"
$user = "v$stamp"
$password = "Verify@2026"

Invoke-Api -Method Post -Uri "$Base/api/v1/auth/register" -Body @{ username = $user; password = $password; nickname = "verify" } | Out-Null
$login = Invoke-Api -Method Post -Uri "$Base/api/v1/auth/login" -Body @{ username = $user; password = $password }
$auth = @{ Authorization = "Bearer $($login.data.token)" }
Assert-True "Login" ($null -ne $login.data.token)

# ---- 1. job/search ----
Write-Host ""
Write-Host "== 1. job/search ==" -ForegroundColor Cyan

$page1 = Invoke-Api -Method Get -Uri "$Base/api/v1/job/search?page=1&size=2" -Headers $auth
Assert-True "job/search page=1" ($page1.code -eq 200 -and $page1.data.page -eq 1 -and $page1.data.size -eq 2) "total=$($page1.data.total)"

$kw = Invoke-Api -Method Get -Uri "$Base/api/v1/job/search?keyword=Java&page=1&size=5" -Headers $auth
Assert-True "job/search keyword=Java" ($kw.code -eq 200) "total=$($kw.data.total)"

$company = Invoke-Api -Method Get -Uri "$Base/api/v1/job/search?company=测试&page=1&size=5" -Headers $auth
Assert-True "job/search company 参数可调用" ($company.code -eq 200)

# ---- 2. application/stats ----
Write-Host ""
Write-Host "== 2. application/stats ==" -ForegroundColor Cyan

$jobs = Invoke-Api -Method Get -Uri "$Base/api/v1/job" -Headers $auth
$jobId = $jobs.data[0].id
$today = (Get-Date).ToString("yyyy-MM-dd")
Invoke-Api -Method Post -Uri "$Base/api/v1/application" -Headers $auth -Body @{
    resumeVersionId = 0
    jobId           = $jobId
    appliedAt       = $today
    status          = "pending"
} | Out-Null

$stats = Invoke-Api -Method Get -Uri "$Base/api/v1/application/stats" -Headers $auth
$statKeys = $stats.data.PSObject.Properties.Name
$missingKeys = @("pending","interviewing","rejected","no_response","accepted","total") |
    Where-Object { $_ -notin $statKeys } |
    Measure-Object | Select-Object -ExpandProperty Count
Assert-True "application/stats 字段齐全" ($stats.code -eq 200 -and $missingKeys -eq 0 -and $stats.data.pending -ge 1) "pending=$($stats.data.pending) total=$($stats.data.total)"

# ---- 3. match/body 参数校验（空 body 应 400 而不是 500） ----
Write-Host ""
Write-Host "== 3. match/body 校验 ==" -ForegroundColor Cyan

$status = 0
try {
    Invoke-Api -Method Post -Uri "$Base/api/v1/match/body" -Headers $auth -Body @{} | Out-Null
    $status = 200
} catch {
    $status = [int]$_.Exception.Response.StatusCode
}
Assert-True "match/body 空参数返回 400" ($status -eq 400) "HTTP $status"

# ---- 4. 上传 + 内部接口鉴权 + 保存版本 + 导出 + 删除级联 ----
Write-Host ""
Write-Host "== 4. resume/export/delete ==" -ForegroundColor Cyan

$upload = Invoke-RestMethod -Method Post -Uri "$Base/api/v1/resume/upload" -Headers $auth -Form @{
    file = Get-Item $sampleResume
} -TimeoutSec 60
$resumeId = $upload.data.id
Assert-True "resume/upload" ($upload.code -eq 200 -and $resumeId -gt 0) "resumeId=$resumeId"

# 内部接口：不带 token -> 403，带 token -> 200
$noToken = Invoke-WebRequest -Uri "$resumeBase/internal/resume/$resumeId" -SkipHttpErrorCheck -TimeoutSec 10
Assert-True "internal 无 token 被拒" ($noToken.StatusCode -eq 403) "HTTP $($noToken.StatusCode)"
$withToken = Invoke-WebRequest -Uri "$resumeBase/internal/resume/$resumeId" -Headers @{ "X-Internal-Token" = "dev-internal-token" } -SkipHttpErrorCheck -TimeoutSec 10
Assert-True "internal 带 token 放行" ($withToken.StatusCode -eq 200) "HTTP $($withToken.StatusCode)"

# 保存版本
$saved = Invoke-Api -Method Post -Uri "$Base/api/v1/optimize/$resumeId/save" -Headers $auth -Body @{
    content     = "姓名：张三`n技能：Java、Spring Boot"
    versionName = "verify-version"
}
$versionId = $saved.data.id
Assert-True "optimize/save 生成版本" ($saved.code -eq 200 -and $versionId -gt 0) "versionId=$versionId"

# 导出 PDF / DOCX
$pdf = Invoke-WebRequest -Uri "$Base/api/v1/version/$versionId/export?format=pdf" -Headers $auth -SkipHttpErrorCheck -TimeoutSec 60
Assert-True "export pdf Content-Type" ($pdf.StatusCode -eq 200 -and $pdf.Headers."Content-Type" -like "application/pdf*") "type=$($pdf.Headers.'Content-Type')"
$docx = Invoke-WebRequest -Uri "$Base/api/v1/version/$versionId/export?format=docx" -Headers $auth -SkipHttpErrorCheck -TimeoutSec 60
Assert-True "export docx Content-Type" ($docx.StatusCode -eq 200 -and $docx.Headers."Content-Type" -like "application/vnd.openxmlformats*") "type=$($docx.Headers.'Content-Type')"

# 删除简历 -> 版本应被级联清空
$del = Invoke-Api -Method Delete -Uri "$Base/api/v1/resume/$resumeId" -Headers $auth
Assert-True "resume delete" ($del.code -eq 200)
$versions = Invoke-Api -Method Get -Uri "$Base/api/v1/version?resumeId=$resumeId" -Headers $auth
$remaining = @($versions.data).Count
Assert-True "删除后版本已级联清理" ($remaining -eq 0) "remaining=$remaining"

# ---- 汇总 ----
Write-Host ""
if ($exitCode -eq 0) {
    Write-Host "[SUCCESS] 第 4 步接口验收全部通过" -ForegroundColor Green
} else {
    Write-Host "[FAILURE] 存在失败项，请检查上方 [FAIL]" -ForegroundColor Red
}
Write-Host "测试用户: $user（如需清理请手动删除 sys_user）"
exit $exitCode
