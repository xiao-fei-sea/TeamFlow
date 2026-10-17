# M1 认证接口测试（T1.5）——注册/登录/登出/me 正反例
# 用法: powershell -ExecutionPolicy Bypass -File backend\tests\m1-auth-api.ps1 [-BaseUrl http://localhost:8081]
# 依赖: 后端已启动；禁用账号用例需要 docker compose（mysql 服务）
param([string]$BaseUrl = "http://localhost:8081")

$ErrorActionPreference = 'Stop'
try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch {}

$script:pass = 0
$script:fail = 0
$script:failures = New-Object System.Collections.Generic.List[string]

function Invoke-Api {
    param([string]$Method, [string]$Path, $Body, [string]$Token)
    $headers = @{}
    if ($Token) { $headers['Authorization'] = "Bearer $Token" }
    $params = @{
        Method      = $Method
        Uri         = ($BaseUrl + $Path)
        Headers     = $headers
        ContentType = 'application/json'
        UseBasicParsing = $true
    }
    if ($null -ne $Body) { $params['Body'] = ($Body | ConvertTo-Json -Depth 5 -Compress) }
    try {
        $resp = Invoke-WebRequest @params
        return @{ status = [int]$resp.StatusCode; body = ($resp.Content | ConvertFrom-Json) }
    } catch {
        $status = 0; $json = $null
        try { $status = [int]$_.Exception.Response.StatusCode } catch {}
        # PS5.1 下 4xx/5xx 响应体优先从 ErrorDetails 取，GetResponseStream 可能已被消费
        $content = $null
        try { $content = $_.ErrorDetails.Message } catch {}
        if (-not $content) {
            try {
                $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
                $content = $reader.ReadToEnd()
            } catch {}
        }
        try { $json = $content | ConvertFrom-Json } catch { $json = $null }
        return @{ status = $status; body = $json }
    }
}

function Assert {
    param([bool]$Condition, [string]$Name, [string]$Detail = '')
    if ($Condition) {
        $script:pass++
        Write-Host ("PASS  " + $Name)
    } else {
        $script:fail++
        $script:failures.Add($Name)
        Write-Host ("FAIL  " + $Name + "  " + $Detail)
    }
}

function HasFieldError {
    param($Data, [string]$Field)
    if ($null -eq $Data -or $null -eq $Data.errors) { return $false }
    return (@($Data.errors | Where-Object { $_.field -eq $Field }).Count -gt 0)
}

Write-Host "=== M1 auth api tests => $BaseUrl ==="
$suffix = (Get-Date -Format 'HHmmss')
$u1 = "m1a$suffix"; $e1 = "$u1@test.com"; $p1 = 'pass1234'
$u2 = "m1b$suffix"; $e2 = "$u2@test.com"

# ---------- 0. 健康检查保持放行 ----------
$r = Invoke-Api 'GET' '/api/ping'
Assert ($r.status -eq 200 -and $r.body.code -eq 0) 'ping permitAll' ("status=" + $r.status)

# ---------- 1. 注册正例 ----------
$r = Invoke-Api 'POST' '/api/auth/register' @{ username = $u1; password = $p1; email = $e1 }
Assert ($r.status -eq 200 -and $r.body.code -eq 0) 'register ok' ("code=" + $r.body.code)
Assert ($r.body.data.token -and $r.body.data.token.Length -gt 20) 'register returns token'
Assert ($r.body.data.expiresIn -eq 86400) 'register expiresIn=86400' ("expiresIn=" + $r.body.data.expiresIn)
Assert ($r.body.data.user.username -eq $u1 -and $r.body.data.user.email -eq $e1) 'register user info'
Assert ($r.body.data.user.id -is [string] -and $r.body.data.user.id.Length -ge 10) 'user.id is snowflake string' ("id=" + $r.body.data.user.id)
$token1 = $r.body.data.token
if ($null -eq $token1) { Write-Host 'FATAL  register failed, abort'; exit 1 }

# ---------- 2. 注册参数校验反例 ----------
$r = Invoke-Api 'POST' '/api/auth/register' @{ username = 'ab'; password = $p1; email = $e2 }
Assert ($r.body.code -eq 40001 -and (HasFieldError $r.body.data 'username')) 'register bad username -> 40001 field=username'
$r = Invoke-Api 'POST' '/api/auth/register' @{ username = $u2; password = 'abcdefgh'; email = $e2 }
Assert ($r.body.code -eq 40001 -and (HasFieldError $r.body.data 'password')) 'register password without digit -> 40001 field=password'
$r = Invoke-Api 'POST' '/api/auth/register' @{ username = $u2; password = $p1; email = 'not-an-email' }
Assert ($r.body.code -eq 40001 -and (HasFieldError $r.body.data 'email')) 'register bad email -> 40001 field=email'
$r = Invoke-Api 'POST' '/api/auth/register' @{ username = $u1; password = $p1; email = $e2 }
Assert ($r.body.code -eq 40001 -and (HasFieldError $r.body.data 'username') -and (HasFieldError $r.body.data 'email') -eq $false) 'duplicate username -> 40001 field=username'
$r = Invoke-Api 'POST' '/api/auth/register' @{ username = $u2; password = $p1; email = $e1 }
Assert ($r.body.code -eq 40001 -and (HasFieldError $r.body.data 'email')) 'duplicate email -> 40001 field=email'

# ---------- 3. 登录 ----------
$r = Invoke-Api 'POST' '/api/auth/login' @{ account = $u1; password = $p1 }
Assert ($r.body.code -eq 0 -and $r.body.data.token) 'login by username'
$r = Invoke-Api 'POST' '/api/auth/login' @{ account = $e1; password = $p1 }
Assert ($r.body.code -eq 0 -and $r.body.data.token) 'login by email'
$errWrongPwd = Invoke-Api 'POST' '/api/auth/login' @{ account = $u1; password = 'wrong1234' }
$errNoUser   = Invoke-Api 'POST' '/api/auth/login' @{ account = 'ghost_user_x'; password = $p1 }
Assert ($errWrongPwd.body.code -eq 40001) 'login wrong password -> 40001'
Assert ($errNoUser.body.code -eq 40001) 'login unknown account -> 40001'
Assert ($errWrongPwd.body.message -eq $errNoUser.body.message) 'anti-enumeration: same message' ("a=[" + $errWrongPwd.body.message + "] b=[" + $errNoUser.body.message + "]")

# ---------- 4. 身份 me ----------
$r = Invoke-Api 'GET' '/api/users/me'
Assert ($r.status -eq 401 -and $r.body.code -eq 40101) 'me without token -> HTTP401/40101' ("status=" + $r.status + " code=" + $r.body.code)
$r = Invoke-Api 'GET' '/api/users/me' -Token ($token1.Substring(0, $token1.Length - 2) + 'xx')
Assert ($r.status -eq 401 -and $r.body.code -eq 40101) 'me with tampered token -> HTTP401/40101'
$r = Invoke-Api 'GET' '/api/users/me' -Token $token1
Assert ($r.body.code -eq 0 -and $r.body.data.username -eq $u1 -and $r.body.data.email -eq $e1) 'me with valid token'

# ---------- 5. 登出 + 黑名单 ----------
$r = Invoke-Api 'POST' '/api/auth/logout'
Assert ($r.status -eq 401 -and $r.body.code -eq 40101) 'logout without token -> HTTP401/40101'
$r = Invoke-Api 'POST' '/api/auth/logout' -Token $token1
Assert ($r.body.code -eq 0) 'logout ok'
$r = Invoke-Api 'GET' '/api/users/me' -Token $token1
Assert ($r.status -eq 401 -and $r.body.code -eq 40101) 'blacklisted token rejected -> HTTP401/40101'

# ---------- 6. 禁用账号（需要 mysql 容器） ----------
$r = Invoke-Api 'POST' '/api/auth/register' @{ username = $u2; password = $p1; email = $e2 }
$token2 = $r.body.data.token
$compose = Join-Path (Join-Path $PSScriptRoot '..\..') 'docker-compose.dev.yml'
$dockerOk = $true
$sql = "UPDATE sys_user SET status=0 WHERE username='$u2';"
Push-Location (Join-Path $PSScriptRoot '..\..')
try {
    # cmd 承接 stderr（mysql 密码告警），避免 PS 将其升级为终止错误
    cmd /c "docker compose -f `"$compose`" exec -T mysql mysql -uroot -pteamflow123 teamflow -e `"$sql`" 2>nul"
    if ($LASTEXITCODE -ne 0) { $dockerOk = $false; Write-Host ("WARN  docker mysql exec failed, exit=" + $LASTEXITCODE) }
} finally { Pop-Location }
if ($dockerOk) {
    $r = Invoke-Api 'POST' '/api/auth/login' @{ account = $u2; password = $p1 }
    Assert ($r.body.code -eq 40001) 'disabled account login -> 40001' ("message=[" + $r.body.message + "]")
    $sql = "UPDATE sys_user SET status=1 WHERE username='$u2';"
    Push-Location (Join-Path $PSScriptRoot '..\..')
    try { cmd /c "docker compose -f `"$compose`" exec -T mysql mysql -uroot -pteamflow123 teamflow -e `"$sql`" 2>nul" } finally { Pop-Location }
    $r = Invoke-Api 'POST' '/api/auth/login' @{ account = $u2; password = $p1 }
    Assert ($r.body.code -eq 0) 'restored account login ok'
} else {
    Write-Host 'SKIP  disabled-account cases (mysql container unavailable)'
}

Write-Host ("=== RESULT: PASS=" + $script:pass + " FAIL=" + $script:fail + " ===")
if ($script:fail -gt 0) {
    $script:failures | ForEach-Object { Write-Host ("  FAILED: " + $_) }
    exit 1
}
exit 0
