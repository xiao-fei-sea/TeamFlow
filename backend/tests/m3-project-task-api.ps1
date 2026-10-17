# M3 项目与任务接口测试（T3.1 CRUD / T3.2 可见性 / T3.3 项目成员 / T3.4 任务 / T3.5 乐观锁 / T3.6 归档封存 / 级联 / 双端并发拖拽）
# 用法: powershell -ExecutionPolicy Bypass -File backend\tests\m3-project-task-api.ps1 [-BaseUrl http://localhost:8081]
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

function Sql-Scalar {
    param([string]$Sql)
    Push-Location (Join-Path $PSScriptRoot '..\..')
    $raw = cmd /c "docker compose -f `"docker-compose.dev.yml`" exec -T mysql mysql -uroot -pteamflow123 teamflow -N -e `"$Sql`" 2>nul"
    Pop-Location
    return "$raw".Trim()
}

Write-Host "=== M3 project/task api tests => $BaseUrl ==="
$sfx = Get-Date -Format 'HHmmss'

# ---------- 0. 准备用户与组织 ----------
$users = @{}
foreach ($spec in @(@('owner', "m3o$sfx"), @('adm', "m3a$sfx"), @('mem', "m3m$sfx"), @('mem2', "m3n$sfx"), @('out', "m3x$sfx"))) {
    $key = $spec[0]; $uname = $spec[1]
    $r = Invoke-Api 'POST' '/api/auth/register' @{ username = $uname; password = 'pass1234'; email = "$uname@test.com" }
    if ($r.body.code -ne 0) { Write-Host ("FATAL  register " + $key + " failed: " + $r.body.code); exit 1 }
    $users[$key] = @{ token = $r.body.data.token; id = $r.body.data.user.id; username = $uname }
}
$ownerT = $users['owner'].token
$admT = $users['adm'].token
$memT = $users['mem'].token
$mem2T = $users['mem2'].token
$outT = $users['out'].token

$r = Invoke-Api 'POST' '/api/organizations' @{ name = "MOrg$sfx" } -Token $ownerT
if ($r.body.code -ne 0) { Write-Host ("FATAL  create org failed: " + $r.body.code); exit 1 }
$orgId = $r.body.data.id
$null = Invoke-Api 'POST' "/api/organizations/$orgId/members" @{ userId = $users['adm'].id; role = 'ADMIN' } -Token $ownerT
$null = Invoke-Api 'POST' "/api/organizations/$orgId/members" @{ userId = $users['mem'].id; role = 'MEMBER' } -Token $ownerT
$null = Invoke-Api 'POST' "/api/organizations/$orgId/members" @{ userId = $users['mem2'].id; role = 'MEMBER' } -Token $ownerT

$projA = "PA$sfx"
$projB = "PB$sfx"

# ---------- 1. T3.1 项目 CRUD ----------
$r = Invoke-Api 'POST' "/api/organizations/$orgId/projects" @{ name = $projA } -Token $memT
Assert ($r.body.code -eq 40302) 'T3.1 MEMBER create project -> 40302' ("code=" + $r.body.code)
$r = Invoke-Api 'POST' "/api/organizations/$orgId/projects" @{ name = 'hack' } -Token $outT
Assert ($r.body.code -eq 40301) 'T3.1 outsider create project -> 40301' ("code=" + $r.body.code)

$r = Invoke-Api 'POST' "/api/organizations/$orgId/projects" @{ name = $projA; description = 'proj A' } -Token $ownerT
Assert ($r.body.code -eq 0 -and $r.body.data.projectRole -eq 'PROJECT_OWNER' -and $r.body.data.myRole -eq 'OWNER' -and $r.body.data.status -eq 1) 'T3.1 OWNER create A -> PROJECT_OWNER/status=1' ("code=" + $r.body.code)
$projAId = $r.body.data.id
Assert ($projAId -is [string] -and $r.body.data.createdAt) 'T3.1 create project returns id(string)/createdAt'
Start-Sleep -Seconds 1
$r = Invoke-Api 'POST' "/api/organizations/$orgId/projects" @{ name = $projB } -Token $admT
Assert ($r.body.code -eq 0 -and $r.body.data.myRole -eq 'ADMIN') 'T3.1 ADMIN create B -> myRole=ADMIN' ("code=" + $r.body.code)
$projBId = $r.body.data.id

$r = Invoke-Api 'POST' "/api/organizations/$orgId/projects" @{ name = $projA } -Token $admT
Assert ($r.body.code -eq 40001) 'T3.1 duplicate name in org -> 40001' ("code=" + $r.body.code)
$r = Invoke-Api 'POST' "/api/organizations/$orgId/projects" @{ name = ('x' * 65) } -Token $ownerT
Assert ($r.body.code -eq 40001) 'T3.1 name>64 -> 40001'
$r = Invoke-Api 'POST' "/api/organizations/$orgId/projects" @{ name = '   ' } -Token $ownerT
Assert ($r.body.code -eq 40001) 'T3.1 blank name -> 40001'

$r = Invoke-Api 'PUT' "/api/organizations/$orgId/projects/$projAId" @{ name = 'newname' } -Token $memT
Assert ($r.body.code -eq 40302) 'T3.1 MEMBER edit project -> 40302'
$r = Invoke-Api 'PUT' "/api/organizations/$orgId/projects/$projAId" @{ name = "PA2$sfx" } -Token $admT
Assert ($r.body.code -eq 0 -and $r.body.data.name -eq "PA2$sfx") 'T3.1 ADMIN edit name ok'
$r = Invoke-Api 'PUT' "/api/organizations/$orgId/projects/$projAId" @{ name = $projB } -Token $admT
Assert ($r.body.code -eq 40001) 'T3.1 edit to existing name -> 40001'
$r = Invoke-Api 'PUT' "/api/organizations/$orgId/projects/$projAId" @{ name = "PA2$sfx" } -Token $admT
Assert ($r.body.code -eq 0) 'T3.1 edit keep own name (exclude self) ok'

$r = Invoke-Api 'GET' "/api/organizations/$orgId/projects/$projAId" -Token $outT
Assert ($r.body.code -eq 40301) 'T3.1 outsider project detail -> 40301'
$r = Invoke-Api 'GET' "/api/organizations/$orgId/projects/$projAId" -Token $memT
Assert ($r.body.code -eq 40302) 'T3.1 org-MEMBER(not project member) detail -> 40302'
$r = Invoke-Api 'GET' "/api/organizations/$orgId/projects/$projAId" -Token $ownerT
Assert ($r.body.code -eq 0 -and $r.body.data.memberCount -eq 1) 'T3.1 OWNER detail memberCount=1'
$r = Invoke-Api 'GET' "/api/organizations/$orgId/projects/$projAId" -Token $admT
Assert ($r.body.code -eq 0 -and $null -eq $r.body.data.projectRole) 'T3.1 org-ADMIN(not joined) detail -> projectRole null'

$r = Invoke-Api 'POST' "/api/organizations/$orgId/projects/$projAId/archive" -Token $memT
Assert ($r.body.code -eq 40302) 'T3.1 MEMBER archive -> 40302'
$r = Invoke-Api 'POST' "/api/organizations/$orgId/projects/$projAId/archive" -Token $admT
Assert ($r.body.code -eq 0) 'T3.1 ADMIN archive A ok'
$r = Invoke-Api 'GET' "/api/organizations/$orgId/projects/$projAId" -Token $ownerT
Assert ($r.body.code -eq 0 -and $r.body.data.status -eq 0) 'T3.1 archived detail status=0'
$r = Invoke-Api 'POST' "/api/organizations/$orgId/projects" @{ name = "PA2$sfx" } -Token $ownerT
Assert ($r.body.code -eq 40001) 'T3.1 name unique covers archived project -> 40001'
$r = Invoke-Api 'POST' "/api/organizations/$orgId/projects/$projAId/restore" -Token $admT
Assert ($r.body.code -eq 0) 'T3.1 ADMIN restore A ok'
$r = Invoke-Api 'GET' "/api/organizations/$orgId/projects/$projAId" -Token $ownerT
Assert ($r.body.data.status -eq 1) 'T3.1 restored status=1'

# ---------- 2. T3.2 列表可见性 ----------
$r = Invoke-Api 'GET' "/api/organizations/$orgId/projects" -Token $memT
Assert ($r.body.code -eq 0 -and $r.body.data.total -eq 0) 'T3.2 MEMBER list -> only joined (empty)' ("total=" + $r.body.data.total)
$r = Invoke-Api 'GET' "/api/organizations/$orgId/projects" -Token $ownerT
Assert ($r.body.code -eq 0 -and $r.body.data.total -eq 2) 'T3.2 OWNER list sees all (2)' ("total=" + $r.body.data.total)
Assert ($r.body.data.list[0].id -eq $projBId) 'T3.2 list ordered created_at desc (B first)'
$r = Invoke-Api 'GET' "/api/organizations/$orgId/projects`?page=2&size=1" -Token $ownerT
Assert ($r.body.data.total -eq 2 -and $r.body.data.pages -eq 2 -and $r.body.data.list[0].id -eq $projAId) 'T3.2 pagination page2 size1 -> A'
$pa = $r.body.data.list[0]
Assert ($pa.taskTotal -eq 0 -and $pa.taskDoing -eq 0 -and $pa.myRole -eq 'OWNER' -and $pa.projectRole -eq 'PROJECT_OWNER') 'T3.2 list item counters/roles'
$r = Invoke-Api 'GET' "/api/organizations/$orgId/projects`?keyword=$projB" -Token $ownerT
Assert ($r.body.data.total -eq 1 -and $r.body.data.list[0].id -eq $projBId) 'T3.2 keyword filter'

# ---------- 3. T3.3 项目成员管理 ----------
$r = Invoke-Api 'GET' "/api/organizations/$orgId/projects/$projAId/members" -Token $memT
Assert ($r.body.code -eq 40302) 'T3.3 org-MEMBER(not joined) member list -> 40302'
$r = Invoke-Api 'GET' "/api/organizations/$orgId/projects/$projAId/members" -Token $admT
Assert ($r.body.code -eq 0 -and $r.body.data.Count -eq 1 -and $r.body.data[0].projectRole -eq 'PROJECT_OWNER') 'T3.3 org-ADMIN member list ok (OWNER first)'
$r = Invoke-Api 'GET' "/api/organizations/$orgId/projects/$projAId/members" -Token $outT
Assert ($r.body.code -eq 40301) 'T3.3 outsider member list -> 40301'

$r = Invoke-Api 'POST' "/api/organizations/$orgId/projects/$projAId/members" @{ userId = $users['mem'].id } -Token $memT
Assert ($r.body.code -eq 40302) 'T3.3 MEMBER add project member -> 40302'
$r = Invoke-Api 'POST' "/api/organizations/$orgId/projects/$projAId/members" @{ userId = $users['out'].id } -Token $ownerT
Assert ($r.body.code -eq 40001) 'T3.3 add non-org user -> 40001'
$r = Invoke-Api 'POST' "/api/organizations/$orgId/projects/$projAId/members" @{ userId = '999999' } -Token $ownerT
Assert ($r.body.code -eq 40001) 'T3.3 add nonexistent user -> 40001'
$r = Invoke-Api 'POST' "/api/organizations/$orgId/projects/$projAId/members" @{ userId = $users['mem'].id } -Token $ownerT
Assert ($r.body.code -eq 0 -and $r.body.data.projectRole -eq 'PROJECT_MEMBER') 'T3.3 OWNER add mem -> PROJECT_MEMBER'
$r = Invoke-Api 'POST' "/api/organizations/$orgId/projects/$projAId/members" @{ userId = $users['mem'].id } -Token $ownerT
Assert ($r.body.code -eq 0 -and $r.body.data.projectRole -eq 'PROJECT_MEMBER') 'T3.3 re-add idempotent'
$r = Invoke-Api 'POST' "/api/organizations/$orgId/projects/$projAId/members" @{ userId = $users['mem2'].id } -Token $ownerT
Assert ($r.body.code -eq 0) 'T3.3 OWNER add mem2'
$r = Invoke-Api 'GET' "/api/organizations/$orgId/projects/$projAId/members" -Token $memT
Assert ($r.body.code -eq 0 -and $r.body.data.Count -eq 3 -and $r.body.data[0].userId -eq $users['owner'].id -and $r.body.data[0].joinedAt) 'T3.3 member list 3 rows, OWNER first, joinedAt set'

$r = Invoke-Api 'DELETE' "/api/organizations/$orgId/projects/$projAId/members/$($users['mem2'].id)" -Token $memT
Assert ($r.body.code -eq 40302) 'T3.3 MEMBER remove project member -> 40302'
$r = Invoke-Api 'DELETE' "/api/organizations/$orgId/projects/$projAId/members/$($users['owner'].id)" -Token $ownerT
Assert ($r.body.code -eq 40001) 'T3.3 remove PROJECT_OWNER -> 40001'
$r = Invoke-Api 'DELETE' "/api/organizations/$orgId/projects/$projAId/members/$($users['mem2'].id)" -Token $ownerT
Assert ($r.body.code -eq 0) 'T3.3 OWNER remove mem2 ok'
$r = Invoke-Api 'GET' "/api/organizations/$orgId/projects/$projAId/members" -Token $ownerT
Assert ($r.body.data.Count -eq 2) 'T3.3 member list back to 2'

# ---------- 4. T3.4 任务 CRUD ----------
$r = Invoke-Api 'POST' "/api/organizations/$orgId/projects/$projAId/tasks" @{ title = "t0_$sfx"; assigneeId = $users['mem2'].id } -Token $ownerT
Assert ($r.body.code -eq 40001) 'T3.4 assignee not project member -> 40001' ("code=" + $r.body.code)
$r = Invoke-Api 'POST' "/api/organizations/$orgId/projects/$projAId/tasks" @{ title = "t0_$sfx"; assigneeId = $users['out'].id } -Token $ownerT
Assert ($r.body.code -eq 40001) 'T3.4 assignee non-org user -> 40001'
$r = Invoke-Api 'POST' "/api/organizations/$orgId/projects/$projAId/tasks" @{ title = "t0_$sfx"; dueAt = '2020-01-01 00:00:00' } -Token $ownerT
Assert ($r.body.code -eq 40001) 'T3.4 dueAt in past -> 40001'
$r = Invoke-Api 'POST' "/api/organizations/$orgId/projects/$projAId/tasks" @{ title = "t0_$sfx"; priority = 5 } -Token $ownerT
Assert ($r.body.code -eq 40001) 'T3.4 priority=5 -> 40001'
$r = Invoke-Api 'POST' "/api/organizations/$orgId/projects/$projAId/tasks" @{ title = "t0_$sfx"; priority = 0 } -Token $ownerT
Assert ($r.body.code -eq 40001) 'T3.4 priority=0 -> 40001'
$r = Invoke-Api 'POST' "/api/organizations/$orgId/projects/$projAId/tasks" @{ title = '   ' } -Token $ownerT
Assert ($r.body.code -eq 40001) 'T3.4 blank title -> 40001'
$r = Invoke-Api 'POST' "/api/organizations/$orgId/projects/$projAId/tasks" @{ title = ('x' * 129) } -Token $ownerT
Assert ($r.body.code -eq 40001) 'T3.4 title>128 -> 40001'
$r = Invoke-Api 'POST' "/api/organizations/$orgId/projects/$projAId/tasks" @{ title = "t0_$sfx" } -Token $outT
Assert ($r.body.code -eq 40301) 'T3.4 outsider create task -> 40301'

$dueSoon = (Get-Date).AddSeconds(3).ToString('yyyy-MM-dd HH:mm:ss')
$dueLate = (Get-Date).AddHours(2).ToString('yyyy-MM-dd HH:mm:ss')
$r = Invoke-Api 'POST' "/api/organizations/$orgId/projects/$projAId/tasks" @{ title = "t1_$sfx"; description = 'd1'; assigneeId = $users['mem'].id; priority = 3; dueAt = $dueLate } -Token $ownerT
Assert ($r.body.code -eq 0 -and $r.body.data.status -eq 'TODO' -and $r.body.data.priority -eq 3 -and $r.body.data.version -eq 0) 'T3.4 create t1 (defaults TODO/version=0)' ("code=" + $r.body.code)
$task1 = $r.body.data.id
Assert ($r.body.data.assignee.id -eq $users['mem'].id -and $r.body.data.assignee.username -eq $users['mem'].username) 'T3.4 t1 assignee set'
$r = Invoke-Api 'POST' "/api/organizations/$orgId/projects/$projAId/tasks" @{ title = "t2_$sfx" } -Token $memT
Assert ($r.body.code -eq 0 -and $r.body.data.priority -eq 2 -and $null -eq $r.body.data.assignee) 'T3.4 create t2 by MEMBER, default priority=2, no assignee'
$task2 = $r.body.data.id
$r = Invoke-Api 'POST' "/api/organizations/$orgId/projects/$projAId/tasks" @{ title = "t3_$sfx"; dueAt = $dueSoon } -Token $memT
Assert ($r.body.code -eq 0) 'T3.4 create t3 dueAt=+3s'
$task3 = $r.body.data.id
$r = Invoke-Api 'POST' "/api/organizations/$orgId/projects/$projAId/tasks" @{ title = "t4_$sfx" } -Token $admT
Assert ($r.body.code -eq 0) 'T3.4 create t4 by org-ADMIN (not joined) ok'
$task4 = $r.body.data.id

Start-Sleep -Seconds 4
$r = Invoke-Api 'GET' "/api/organizations/$orgId/projects/$projAId/tasks" -Token $memT
Assert ($r.body.code -eq 0 -and $r.body.data.total -eq 4) 'T3.4 task list total=4' ("total=" + $r.body.data.total)
$t3item = @($r.body.data.list | Where-Object { $_.id -eq $task3 })[0]
Assert ($t3item.overdue -eq $true) 'T3.4/S01 overdue=true when dueAt passed and status TODO'
Assert ($null -eq $r.body.data.list[0].PSObject.Properties['description']) 'T3.4 list item omits description'

$r = Invoke-Api 'GET' "/api/organizations/$orgId/projects/$projAId/tasks`?keyword=t1_$sfx" -Token $ownerT
Assert ($r.body.data.total -eq 1 -and $r.body.data.list[0].id -eq $task1) 'T3.4 keyword filter'
$r = Invoke-Api 'GET' "/api/organizations/$orgId/projects/$projAId/tasks`?assigneeId=$($users['mem'].id)" -Token $ownerT
Assert ($r.body.data.total -eq 1 -and $r.body.data.list[0].id -eq $task1) 'T3.4 assigneeId filter'
$r = Invoke-Api 'GET' "/api/organizations/$orgId/projects/$projAId/tasks`?assigneeId=none" -Token $ownerT
Assert ($r.body.data.total -eq 3) 'T3.4 assigneeId=none -> 3 unassigned' ("total=" + $r.body.data.total)
$r = Invoke-Api 'GET' "/api/organizations/$orgId/projects/$projAId/tasks`?priority=3" -Token $ownerT
Assert ($r.body.data.total -eq 1) 'T3.4 priority filter'
$r = Invoke-Api 'GET' "/api/organizations/$orgId/projects/$projAId/tasks`?status=DONE" -Token $ownerT
Assert ($r.body.data.total -eq 0) 'T3.4 status=DONE filter empty'
$r = Invoke-Api 'GET' "/api/organizations/$orgId/projects/$projAId/tasks`?orderBy=due_at&orderDir=asc" -Token $ownerT
Assert ($r.body.code -eq 0 -and $r.body.data.list[3].id -eq $task1) 'T3.4 orderBy due_at asc -> t1 last (latest due)'
$r = Invoke-Api 'GET' "/api/organizations/$orgId/projects/$projAId/tasks`?orderBy=due_at&orderDir=desc" -Token $ownerT
Assert ($r.body.data.list[0].id -eq $task1) 'T3.4 orderBy due_at desc -> t1 first'
$r = Invoke-Api 'GET' "/api/organizations/$orgId/projects/$projAId/tasks`?orderBy=evil--x&orderDir=zzz" -Token $ownerT
Assert ($r.body.code -eq 0) 'T3.4 illegal orderBy/orderDir fallback ok'
$r = Invoke-Api 'GET' "/api/organizations/$orgId/projects/$projAId/tasks`?page=2&size=2" -Token $ownerT
Assert ($r.body.data.total -eq 4 -and $r.body.data.pages -eq 2 -and $r.body.data.list.Count -eq 2) 'T3.4 pagination page2/size2'

$r = Invoke-Api 'GET' "/api/organizations/$orgId/projects/$projAId/tasks/$task1" -Token $outT
Assert ($r.body.code -eq 40301) 'T3.4 outsider task detail -> 40301'
$r = Invoke-Api 'GET' "/api/organizations/$orgId/projects/$projAId/tasks/$task1" -Token $memT
Assert ($r.body.code -eq 0 -and $r.body.data.description -eq 'd1' -and $r.body.data.creator.id -eq $users['owner'].id -and $r.body.data.createdAt) 'T3.4 detail has description/creator/createdAt'
$r = Invoke-Api 'GET' "/api/organizations/$orgId/projects/$projAId/tasks/$task1" -Token $mem2T
Assert ($r.body.code -eq 40302) 'T3.4 removed project member task detail -> 40302'

$r = Invoke-Api 'PUT' "/api/organizations/$orgId/projects/$projAId/tasks/$task1" @{ title = '   ' } -Token $ownerT
Assert ($r.body.code -eq 40001) 'T3.4 edit blank title -> 40001'
$r = Invoke-Api 'PUT' "/api/organizations/$orgId/projects/$projAId/tasks/$task1" @{ dueAt = '2020-01-01 00:00:00' } -Token $ownerT
Assert ($r.body.code -eq 40001) 'T3.4 edit dueAt to past -> 40001'
$r = Invoke-Api 'PUT' "/api/organizations/$orgId/projects/$projAId/tasks/$task1" @{ description = 'd1-v2' } -Token $memT
Assert ($r.body.code -eq 0 -and $r.body.data.description -eq 'd1-v2' -and $r.body.data.dueAt -eq $dueLate) 'T3.4 MEMBER edit desc, dueAt kept'
$r = Invoke-Api 'PUT' "/api/organizations/$orgId/projects/$projAId/tasks/$task1" @{ assigneeId = 0 } -Token $ownerT
Assert ($r.body.code -eq 0 -and $null -eq $r.body.data.assignee) 'T3.4 edit assigneeId=0 -> unassigned'
$r = Invoke-Api 'PUT' "/api/organizations/$orgId/projects/$projAId/tasks/$task1" @{ assigneeId = $users['mem'].id } -Token $ownerT
Assert ($r.body.code -eq 0 -and $r.body.data.assignee.id -eq $users['mem'].id) 'T3.4 reassign ok'
$r = Invoke-Api 'PUT' "/api/organizations/$orgId/projects/$projAId/tasks/$task1" @{ assigneeId = 999999 } -Token $ownerT
Assert ($r.body.code -eq 40001) 'T3.4 edit assignee nonexistent -> 40001'

$r = Invoke-Api 'DELETE' "/api/organizations/$orgId/projects/$projAId/tasks/$task2" -Token $memT
Assert ($r.body.code -eq 40302) 'T3.4 MEMBER delete task -> 40302'
$r = Invoke-Api 'DELETE' "/api/organizations/$orgId/projects/$projAId/tasks/$task4" -Token $admT
Assert ($r.body.code -eq 0) 'T3.4 ADMIN delete task ok'
$r = Invoke-Api 'GET' "/api/organizations/$orgId/projects/$projAId/tasks/$task4" -Token $ownerT
Assert ($r.body.code -eq 40401) 'T3.4 deleted task detail -> 40401'
$r = Invoke-Api 'DELETE' "/api/organizations/$orgId/projects/$projAId/tasks/999888777" -Token $ownerT
Assert ($r.body.code -eq 40401) 'T3.4 delete nonexistent task -> 40401'

# ---------- 5. T3.5 状态流转 + 乐观锁 ----------
$v1 = (Invoke-Api 'GET' "/api/organizations/$orgId/projects/$projAId/tasks/$task1" -Token $ownerT).body.data.version
$r = Invoke-Api 'PATCH' "/api/organizations/$orgId/projects/$projAId/tasks/$task1/status" @{ targetStatus = 'IN_PROGRESS'; version = $v1 } -Token $memT
Assert ($r.body.code -eq 0 -and $r.body.data.status -eq 'IN_PROGRESS' -and $r.body.data.version -eq ($v1 + 1)) 'T3.5 PATCH status ok -> version+1' ("code=" + $r.body.code + " v=" + $r.body.data.version)
$r = Invoke-Api 'PATCH' "/api/organizations/$orgId/projects/$projAId/tasks/$task1/status" @{ targetStatus = 'DONE'; version = $v1 } -Token $ownerT
Assert ($r.body.code -eq 40901) 'T3.5 stale version -> 40901' ("code=" + $r.body.code)
$r = Invoke-Api 'PATCH' "/api/organizations/$orgId/projects/$projAId/tasks/$task1/status" @{ targetStatus = 'BAD'; version = ($v1 + 1) } -Token $ownerT
Assert ($r.body.code -eq 40001) 'T3.5 illegal targetStatus -> 40001'
$r = Invoke-Api 'PATCH' "/api/organizations/$orgId/projects/$projAId/tasks/$task1/status" @{ targetStatus = 'DONE' } -Token $ownerT
Assert ($r.body.code -eq 40001) 'T3.5 missing version -> 40001'
$r = Invoke-Api 'PATCH' "/api/organizations/999888/projects/$projAId/tasks/$task1/status" @{ targetStatus = 'DONE'; version = ($v1 + 1) } -Token $ownerT
Assert ($r.body.code -eq 40401) 'T3.5 wrong orgId path -> 40401'
$r = Invoke-Api 'PATCH' "/api/organizations/$orgId/projects/$projBId/tasks/$task1/status" @{ targetStatus = 'DONE'; version = ($v1 + 1) } -Token $ownerT
Assert ($r.body.code -eq 40401) 'T3.5 task under wrong project path -> 40401'
$r = Invoke-Api 'PATCH' "/api/organizations/$orgId/projects/$projAId/tasks/$task1/status" @{ targetStatus = 'DONE'; version = ($v1 + 1) } -Token $ownerT
Assert ($r.body.code -eq 0 -and $r.body.data.version -eq ($v1 + 2)) 'T3.5 second transition ok'

# ---------- 6. 级联：移除项目成员 -> assignee 置空 ----------
$r = Invoke-Api 'POST' "/api/organizations/$orgId/projects/$projAId/members" @{ userId = $users['mem2'].id } -Token $ownerT
Assert ($r.body.code -eq 0) 'cascade: re-add mem2'
$r = Invoke-Api 'POST' "/api/organizations/$orgId/projects/$projAId/tasks" @{ title = "tc_$sfx"; assigneeId = $users['mem2'].id } -Token $mem2T
Assert ($r.body.code -eq 0) 'cascade: mem2 create task assigned to self'
$taskC = $r.body.data.id
$before = Sql-Scalar "SELECT IFNULL(assignee_id, -1) FROM task WHERE id=$taskC AND deleted=0;"
Assert ("$before" -eq "$($users['mem2'].id)") 'cascade: SQL assignee set before removal' ("raw=" + $before)
$r = Invoke-Api 'DELETE' "/api/organizations/$orgId/projects/$projAId/members/$($users['mem2'].id)" -Token $ownerT
Assert ($r.body.code -eq 0) 'cascade: OWNER remove mem2 (project member)'
Start-Sleep -Milliseconds 200
$after = Sql-Scalar "SELECT IFNULL(assignee_id, -1) FROM task WHERE id=$taskC AND deleted=0;"
Assert ("$after" -eq '-1') 'cascade: SQL assignee NULL after removal' ("raw=" + $after)

# ---------- 7. T3.6 归档封存 ----------
$r = Invoke-Api 'POST' "/api/organizations/$orgId/projects/$projAId/archive" -Token $ownerT
Assert ($r.body.code -eq 0) 'T3.6 archive A'
$r = Invoke-Api 'POST' "/api/organizations/$orgId/projects/$projAId/tasks" @{ title = "tarc_$sfx" } -Token $ownerT
Assert ($r.body.code -eq 40302) 'T3.6 archived: create task -> 40302'
$r = Invoke-Api 'PUT' "/api/organizations/$orgId/projects/$projAId/tasks/$task1" @{ description = 'no' } -Token $ownerT
Assert ($r.body.code -eq 40302) 'T3.6 archived: edit task -> 40302'
$vNow = (Invoke-Api 'GET' "/api/organizations/$orgId/projects/$projAId/tasks/$task1" -Token $ownerT).body.data.version
$r = Invoke-Api 'PATCH' "/api/organizations/$orgId/projects/$projAId/tasks/$task1/status" @{ targetStatus = 'TODO'; version = $vNow } -Token $ownerT
Assert ($r.body.code -eq 40302) 'T3.6 archived: drag task -> 40302'
$r = Invoke-Api 'DELETE' "/api/organizations/$orgId/projects/$projAId/tasks/$task1" -Token $ownerT
Assert ($r.body.code -eq 40302) 'T3.6 archived: delete task -> 40302'
$r = Invoke-Api 'GET' "/api/organizations/$orgId/projects/$projAId/tasks" -Token $memT
Assert ($r.body.code -eq 0) 'T3.6 archived: read still allowed'
$r = Invoke-Api 'POST' "/api/organizations/$orgId/projects/$projAId/restore" -Token $ownerT
Assert ($r.body.code -eq 0) 'T3.6 restore A'
$r = Invoke-Api 'POST' "/api/organizations/$orgId/projects/$projAId/tasks" @{ title = "tpost_$sfx" } -Token $memT
Assert ($r.body.code -eq 0) 'T3.6 restored: create task ok'
$taskPost = $r.body.data.id

# ---------- 8. 双端并发拖拽模拟（顺序：端1 成功 -> 端2 旧版本 40901） ----------
$vC = (Invoke-Api 'GET' "/api/organizations/$orgId/projects/$projAId/tasks/$taskPost" -Token $ownerT).body.data.version
$r1 = Invoke-Api 'PATCH' "/api/organizations/$orgId/projects/$projAId/tasks/$taskPost/status" @{ targetStatus = 'IN_PROGRESS'; version = $vC } -Token $memT
$r2 = Invoke-Api 'PATCH' "/api/organizations/$orgId/projects/$projAId/tasks/$taskPost/status" @{ targetStatus = 'IN_PROGRESS'; version = $vC } -Token $ownerT
Assert ($r1.body.code -eq 0 -and $r1.body.data.version -eq ($vC + 1)) 'concurrent: client A wins (version+1)'
Assert ($r2.body.code -eq 40901) 'concurrent: client B stale -> 40901' ("code=" + $r2.body.code)

# ---------- 9. 收尾：删除组织 ----------
$r = Invoke-Api 'DELETE' "/api/organizations/$orgId`?confirmName=MOrg$sfx" -Token $ownerT
Assert ($r.body.code -eq 0) 'cleanup: OWNER delete org'
$r = Invoke-Api 'GET' "/api/organizations/$orgId/projects/$projAId" -Token $ownerT
Assert ($r.body.code -eq 40401) 'cleanup: org deleted -> project inaccessible'

Write-Host ("=== RESULT: PASS=" + $script:pass + " FAIL=" + $script:fail + " ===")
if ($script:fail -gt 0) {
    $script:failures | ForEach-Object { Write-Host ("  FAILED: " + $_) }
    exit 1
}
exit 0
