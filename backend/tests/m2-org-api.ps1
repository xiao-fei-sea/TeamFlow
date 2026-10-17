# M2 组织与成员接口测试（T2.4 矩阵 / T2.5 级联 / T2.6 搜索 / T2.7 越权 / T2.8 删除）
# 用法: powershell -ExecutionPolicy Bypass -File backend\tests\m2-org-api.ps1 [-BaseUrl http://localhost:8081]
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
    $params = @{ Method = $Method; Uri = ($BaseUrl + $Path); Headers = $headers
                 ContentType = 'application/json'; UseBasicParsing = $true }
    if ($null -ne $Body) { $params['Body'] = ($Body | ConvertTo-Json -Depth 5 -Compress) }
    try {
        $resp = Invoke-WebRequest @params
        return @{ status = [int]$resp.StatusCode; body = ($resp.Content | ConvertFrom-Json) }
    } catch {
        $status = 0; $json = $null
        try { $status = [int]$_.Exception.Response.StatusCode } catch {}
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
    if ($Condition) { $script:pass++; Write-Host ("PASS  " + $Name) }
    else {
        $script:fail++; $script:failures.Add($Name)
        Write-Host ("FAIL  " + $Name + "  " + $Detail)
    }
}

function Exec-Sql {
    param([string]$Sql)
    Push-Location (Join-Path $PSScriptRoot '..\..')
    try { cmd /c "docker compose -f `"docker-compose.dev.yml`" exec -T mysql mysql -uroot -pteamflow123 teamflow -e `"$Sql`" 2>nul"; return ($LASTEXITCODE -eq 0) }
    finally { Pop-Location }
}

Write-Host "=== M2 org api tests => $BaseUrl ==="
$sfx = Get-Date -Format 'HHmmss'

# ---------- 0. 准备用户 ----------
$users = @{}
foreach ($spec in @(@('owner', "m2o$sfx"), @('adm', "m2a$sfx"), @('mem', "m2m$sfx"), @('out', "m2x$sfx"), @('new', "m2n$sfx"), @('cap', "m2c$sfx"))) {
    $key = $spec[0]; $uname = $spec[1]
    $r = Invoke-Api 'POST' '/api/auth/register' @{ username = $uname; password = 'pass1234'; email = "$uname@test.com" }
    if ($r.body.code -ne 0) { Write-Host ("FATAL  register " + $key + " failed: " + $r.body.code); exit 1 }
    $users[$key] = @{ token = $r.body.data.token; id = $r.body.data.user.id; username = $uname }
}
$T = { param($k) $users[$k].token }
$ownerT = $users['owner'].token
$admT = $users['adm'].token
$memT = $users['mem'].token
$outT = $users['out'].token

# ---------- 1. 创建组织 T2.1 ----------
$r = Invoke-Api 'POST' '/api/organizations' @{ name = "Org$sfx" } -Token $ownerT
Assert ($r.body.code -eq 0 -and $r.body.data.myRole -eq 'OWNER') 'create org -> OWNER' ("code=" + $r.body.code)
$orgId = $r.body.data.id
Assert ($orgId -is [string] -and $r.body.data.createdAt) 'create org returns id( string )/createdAt'
$r = Invoke-Api 'POST' '/api/organizations' @{ name = ('x' * 65) } -Token $ownerT
Assert ($r.body.code -eq 40001) 'create org name>64 -> 40001'

# ---------- 2. T2.7 越权（outsider 非成员访问） ----------
$opaths = @(
    @{ m = 'GET'; p = "/api/organizations/$orgId" },
    @{ m = 'GET'; p = "/api/organizations/$orgId/members" },
    @{ m = 'POST'; p = "/api/organizations/$orgId/members"; b = @{ userId = '1'; role = 'MEMBER' } },
    @{ m = 'PUT'; p = "/api/organizations/$orgId/members/1/role"; b = @{ role = 'ADMIN' } },
    @{ m = 'DELETE'; p = "/api/organizations/$orgId/members/1" },
    @{ m = 'PUT'; p = "/api/organizations/$orgId"; b = @{ name = 'hacked' } },
    @{ m = 'DELETE'; p = "/api/organizations/$orgId`?confirmName=x" }
)
$allTenantRejected = $true
foreach ($o in $opaths) {
    $r = Invoke-Api $o.m $o.p $o.b -Token $outT
    if ($r.body.code -ne 40301) { $allTenantRejected = $false; Write-Host ("      leak: " + $o.m + " " + $o.p + " code=" + $r.body.code) }
}
Assert $allTenantRejected 'T2.7 outsider all org APIs -> 40301'
$r = Invoke-Api 'GET' "/api/users/search`?orgId=$orgId&keyword=x" -Token $outT
Assert ($r.body.code -eq 40301) 'T2.7 outsider user-search -> 40301'

# ---------- 3. 列表/详情 T2.2 ----------
$r = Invoke-Api 'GET' '/api/organizations' -Token $ownerT
$mine = @($r.body.data | Where-Object { $_.id -eq $orgId })
Assert ($r.body.code -eq 0 -and $mine.Count -eq 1 -and $mine[0].myRole -eq 'OWNER' -and $mine[0].memberCount -eq 1) 'my org list has memberCount'
$r = Invoke-Api 'GET' "/api/organizations/$orgId" -Token $ownerT
Assert ($r.body.code -eq 0 -and $r.body.data.ownerId -eq $users['owner'].id -and $r.body.data.projectCount -eq 0) 'org detail ownerId/projectCount'

# ---------- 4. 添加成员 T2.4 ----------
$r = Invoke-Api 'POST' "/api/organizations/$orgId/members" @{ userId = $users['adm'].id; role = 'OWNER' } -Token $ownerT
Assert ($r.body.code -eq 40001) 'add with role=OWNER -> 40001'
$r = Invoke-Api 'POST' "/api/organizations/$orgId/members" @{ userId = '999999'; role = 'MEMBER' } -Token $ownerT
Assert ($r.body.code -eq 40001) 'add nonexistent user -> 40001'
$r = Invoke-Api 'POST' "/api/organizations/$orgId/members" @{ userId = $users['adm'].id; role = 'ADMIN' } -Token $ownerT
Assert ($r.body.code -eq 0 -and $r.body.data.role -eq 'ADMIN' -and $r.body.data.username -eq $users['adm'].username) 'OWNER add ADMIN'
$r = Invoke-Api 'POST' "/api/organizations/$orgId/members" @{ userId = $users['adm'].id; role = 'MEMBER' } -Token $ownerT
Assert ($r.body.code -eq 0 -and $r.body.data.role -eq 'ADMIN') 'idempotent re-add keeps current role'
$r = Invoke-Api 'POST' "/api/organizations/$orgId/members" @{ userId = $users['mem'].id; role = 'MEMBER' } -Token $ownerT
Assert ($r.body.code -eq 0) 'OWNER add MEMBER'
$r = Invoke-Api 'POST' "/api/organizations/$orgId/members" @{ userId = $users['adm'].id; role = 'MEMBER' } -Token $memT
Assert ($r.body.code -eq 40302) 'MEMBER add member -> 40302'

# ---------- 5. 成员列表 ----------
$r = Invoke-Api 'GET' "/api/organizations/$orgId/members`?page=1&size=2" -Token $memT
Assert ($r.body.code -eq 0 -and $r.body.data.total -eq 3 -and $r.body.data.pages -eq 2) 'member list page/size/pages'
Assert ($r.body.data.list[0].role -eq 'OWNER') 'member list OWNER first'
$r = Invoke-Api 'GET' "/api/organizations/$orgId/members`?keyword=$($users['mem'].username)" -Token $ownerT
Assert ($r.body.data.total -eq 1 -and $r.body.data.list[0].userId -eq $users['mem'].id) 'member list keyword filter'

# ---------- 6. 编辑组织 T2.2 ----------
$r = Invoke-Api 'PUT' "/api/organizations/$orgId" @{ name = "Org$sfx-v2" } -Token $memT
Assert ($r.body.code -eq 40302) 'MEMBER edit org -> 40302'
$r = Invoke-Api 'PUT' "/api/organizations/$orgId" @{ name = "Org$sfx-v2" } -Token $admT
Assert ($r.body.code -eq 0) 'ADMIN edit org ok'
$r = Invoke-Api 'GET' "/api/organizations/$orgId" -Token $ownerT
Assert ($r.body.data.name -eq "Org$sfx-v2") 'org name updated'

# ---------- 7. 变更角色（仅 OWNER + 缓存即时失效） ----------
$r = Invoke-Api 'PUT' "/api/organizations/$orgId/members/$($users['mem'].id)/role" @{ role = 'ADMIN' } -Token $admT
Assert ($r.body.code -eq 40302) 'ADMIN change role -> 40302 (OWNER only)'
$r = Invoke-Api 'PUT' "/api/organizations/$orgId/members/$($users['owner'].id)/role" @{ role = 'MEMBER' } -Token $ownerT
Assert ($r.body.code -eq 40001) 'OWNER change own role -> 40001'
$r = Invoke-Api 'PUT' "/api/organizations/$orgId/members/$($users['mem'].id)/role" @{ role = 'ADMIN' } -Token $ownerT
Assert ($r.body.code -eq 0) 'OWNER promote MEMBER -> ADMIN'
$r = Invoke-Api 'POST' "/api/organizations/$orgId/members" @{ userId = $users['new'].id; role = 'MEMBER' } -Token $memT
Assert ($r.body.code -eq 0) 'promoted member can add immediately (cache evicted)'
$r = Invoke-Api 'PUT' "/api/organizations/$orgId/members/$($users['mem'].id)/role" @{ role = 'MEMBER' } -Token $ownerT
Assert ($r.body.code -eq 0) 'OWNER demote ADMIN -> MEMBER'
$r = Invoke-Api 'POST' "/api/organizations/$orgId/members" @{ userId = $users['new'].id; role = 'MEMBER' } -Token $memT
Assert ($r.body.code -eq 40302) 'demoted member denied immediately (cache evicted)'

# ---------- 8. 移除成员（矩阵） ----------
$r = Invoke-Api 'DELETE' "/api/organizations/$orgId/members/$($users['owner'].id)" -Token $admT
Assert ($r.body.code -eq 40302) 'ADMIN remove OWNER -> 40302'
$r = Invoke-Api 'PUT' "/api/organizations/$orgId/members/$($users['mem'].id)/role" @{ role = 'ADMIN' } -Token $ownerT
$r = Invoke-Api 'DELETE' "/api/organizations/$orgId/members/$($users['mem'].id)" -Token $admT
Assert ($r.body.code -eq 40302) 'ADMIN remove ADMIN -> 40302'
$r = Invoke-Api 'PUT' "/api/organizations/$orgId/members/$($users['mem'].id)/role" @{ role = 'MEMBER' } -Token $ownerT
$r = Invoke-Api 'DELETE' "/api/organizations/$orgId/members/$($users['mem'].id)" -Token $memT
Assert ($r.body.code -eq 40001) 'remove self -> 40001'
$r = Invoke-Api 'DELETE' "/api/organizations/$orgId/members/$($users['new'].id)" -Token $admT
Assert ($r.body.code -eq 0) 'ADMIN remove MEMBER ok'

# ---------- 9. T2.5 移除级联（SQL 造项目/任务数据） ----------
$r = Invoke-Api 'POST' "/api/organizations/$orgId/members" @{ userId = $users['new'].id; role = 'MEMBER' } -Token $ownerT
Assert ($r.body.code -eq 0) 're-add member for cascade test'
$newId = $users['new'].id
$null = Exec-Sql "INSERT INTO project (id, organization_id, name, description, status, created_by, deleted, created_at, updated_at) VALUES (910001, $orgId, 'cascade-$sfx', NULL, 1, $($users['owner'].id), 0, NOW(), NOW());"
$null = Exec-Sql "INSERT INTO project_member (id, project_id, user_id, project_role, created_at, updated_at) VALUES (920001, 910001, $newId, 'PROJECT_MEMBER', NOW(), NOW());"
$null = Exec-Sql "INSERT INTO task (id, organization_id, project_id, title, priority, status, version, created_by, deleted, created_at, updated_at) VALUES (930001, $orgId, 910001, 'cascade-task', 2, 'TODO', 0, $($users['owner'].id), 0, NOW(), NOW()); UPDATE task SET assignee_id = $newId WHERE id = 930001;"
$r = Invoke-Api 'DELETE' "/api/organizations/$orgId/members/$newId" -Token $ownerT
Assert ($r.body.code -eq 0) 'OWNER remove MEMBER (cascade)'
Start-Sleep -Milliseconds 200
$pmLeft = (Invoke-Api 'GET' '/api/ping' -Token $ownerT).body.code
$check = "SELECT (SELECT COUNT(*) FROM project_member WHERE project_id=910001 AND user_id=$newId) AS pm, (SELECT COUNT(*) FROM task WHERE id=930001 AND assignee_id IS NULL) AS tk;"
Push-Location (Join-Path $PSScriptRoot '..\..')
$raw = cmd /c "docker compose -f `"docker-compose.dev.yml`" exec -T mysql mysql -uroot -pteamflow123 teamflow -N -e `"$check`" 2>nul"
Pop-Location
Assert ("$raw".Trim() -eq "0`t1") ('T2.5 cascade: project_member removed + assignee NULL  [raw=' + "$raw" + ']')
$null = Exec-Sql "DELETE FROM task WHERE id=930001; DELETE FROM project WHERE id=910001;"

# ---------- 10. 用户搜索 T2.6 ----------
$r = Invoke-Api 'GET' "/api/users/search`?orgId=$orgId&keyword=$($users['mem'].username)" -Token $memT
Assert ($r.body.code -eq 40302) 'MEMBER search users -> 40302'
$r = Invoke-Api 'GET' "/api/users/search`?orgId=$orgId&keyword=$($users['mem'].username)" -Token $ownerT
$item = $r.body.data | Select-Object -First 1
Assert ($r.body.data.Count -ge 1 -and $item.joined -eq $true) 'search joined member'
Assert ($item.email -like '****@test.com' -and $item.email -notlike '*m2m*') ('search email masked  [email=' + $item.email + ']')
$r = Invoke-Api 'GET' "/api/users/search`?orgId=$orgId&keyword=$($users['out'].username)" -Token $ownerT
$item = $r.body.data | Select-Object -First 1
Assert ($r.body.data.Count -eq 1 -and $item.joined -eq $false) 'search not-joined user joined=false'
$r = Invoke-Api 'GET' "/api/users/search`?orgId=$orgId&keyword=" -Token $ownerT
Assert ($r.body.code -eq 0 -and $r.body.data.Count -eq 0) 'search empty keyword -> empty list'

# ---------- 11. 创建上限 ----------
for ($i = 1; $i -le 4; $i++) {
    $r = Invoke-Api 'POST' '/api/organizations' @{ name = "Cap$sfx-$i" } -Token $users['cap'].token
}
$r = Invoke-Api 'POST' '/api/organizations' @{ name = "Cap$sfx-5" } -Token $users['cap'].token
Assert ($r.body.code -eq 0) 'cap user 5th org ok'
$r = Invoke-Api 'POST' '/api/organizations' @{ name = "Cap$sfx-6" } -Token $users['cap'].token
Assert ($r.body.code -eq 40001) '6th org -> 40001 limit'

# ---------- 12. 删除组织 T2.8 ----------
$r = Invoke-Api 'DELETE' "/api/organizations/$orgId`?confirmName=whatever" -Token $admT
Assert ($r.body.code -eq 40302) 'ADMIN delete org -> 40302'
$r = Invoke-Api 'DELETE' "/api/organizations/$orgId`?confirmName=wrong-name" -Token $ownerT
Assert ($r.body.code -eq 40001) 'confirmName mismatch -> 40001'
$r = Invoke-Api 'DELETE' "/api/organizations/$orgId`?confirmName=Org$sfx-v2" -Token $ownerT
Assert ($r.body.code -eq 0) 'OWNER delete org (confirmName ok)'
$r = Invoke-Api 'GET' "/api/organizations/$orgId" -Token $ownerT
Assert ($r.body.code -eq 40401) 'deleted org detail -> 40401 (member loses access)'
$r = Invoke-Api 'GET' '/api/organizations' -Token $ownerT
$left = @($r.body.data | Where-Object { $_.id -eq $orgId })
Assert ($left.Count -eq 0) 'deleted org out of my list'

Write-Host ("=== RESULT: PASS=" + $script:pass + " FAIL=" + $script:fail + " ===")
if ($script:fail -gt 0) {
    $script:failures | ForEach-Object { Write-Host ("  FAILED: " + $_) }
    exit 1
}
exit 0
