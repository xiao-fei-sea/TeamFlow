<script setup>
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as orgApi from '@/api/org'
import { useAuthStore } from '@/stores/auth'
import { useOrgStore } from '@/stores/org'
import { avatarColor } from '@/utils/format'

/** T4.4 成员管理页：列表/搜索/分页、添加成员（用户搜索）、角色变更（仅 OWNER）、移除确认；
 *  操作按钮按 PRD 4.3 组织级权限矩阵显隐（前端显隐仅为体验，安全由服务端保证）
 */

const route = useRoute()
const authStore = useAuthStore()
const orgStore = useOrgStore()
const orgId = computed(() => String(route.params.orgId))

const myId = computed(() => authStore.user?.id)
const myRole = computed(() => orgStore.orgs.find((o) => o.id === orgId.value)?.myRole || 'MEMBER')
const canManage = computed(() => myRole.value === 'OWNER' || myRole.value === 'ADMIN')

/** 变更角色：仅 OWNER，且不可变更自己 */
function canChangeRole(row) {
  return myRole.value === 'OWNER' && row.userId !== myId.value
}

/** 移除：OWNER 可移除任意非 OWNER 成员；ADMIN 仅可移除 MEMBER；任何人都不可移除 OWNER 和自己 */
function canRemove(row) {
  if (row.role === 'OWNER') return false
  if (row.userId === myId.value) return false
  return myRole.value === 'OWNER' || (myRole.value === 'ADMIN' && row.role === 'MEMBER')
}

/* ---------- 成员列表 ---------- */
const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ page: 1, size: 10, keyword: '' })

async function fetchMembers() {
  loading.value = true
  try {
    const data = await orgApi.listMembers(orgId.value, {
      page: query.page,
      size: query.size,
      keyword: query.keyword || undefined,
    })
    list.value = data.list || []
    total.value = data.total || 0
  } catch (e) {
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function onSearch() {
  query.page = 1
  fetchMembers()
}

onMounted(fetchMembers)
watch(orgId, fetchMembers)

/* ---------- 添加成员 ---------- */
const addVisible = ref(false)
const addRole = ref('MEMBER')
const searchKeyword = ref('')
const searchLoading = ref(false)
const searchResults = ref([])
const searched = ref(false)
const addingIds = ref([])

function openAdd() {
  addVisible.value = true
  searchKeyword.value = ''
  searchResults.value = []
  searched.value = false
  addRole.value = 'MEMBER'
}

async function doSearch() {
  const kw = searchKeyword.value.trim()
  if (!kw) {
    ElMessage.warning('请输入用户名或邮箱搜索')
    return
  }
  searchLoading.value = true
  try {
    const data = await orgApi.searchUsers(orgId.value, kw)
    searchResults.value = data?.list || []
    searched.value = true
  } catch (e) {
    searchResults.value = []
  } finally {
    searchLoading.value = false
  }
}

async function doAdd(user) {
  if (user.joined) return
  addingIds.value.push(user.id)
  try {
    await orgApi.addMember(orgId.value, { userId: user.id, role: addRole.value })
    ElMessage.success(`已添加 ${user.username} 为 ${addRole.value}`)
    user.joined = true
    fetchMembers()
    orgStore.loadOrgs(true).catch(() => {})
  } catch (e) {
    /* 错误提示已由 request 拦截器统一弹出 */
  } finally {
    addingIds.value = addingIds.value.filter((id) => id !== user.id)
  }
}

/* ---------- 变更角色 ---------- */
const roleVisible = ref(false)
const roleTarget = ref(null)
const roleValue = ref('MEMBER')
const roleSaving = ref(false)

function openRoleChange(row) {
  roleTarget.value = row
  roleValue.value = row.role
  roleVisible.value = true
}

async function submitRoleChange() {
  if (!roleTarget.value) return
  roleSaving.value = true
  try {
    await orgApi.changeMemberRole(orgId.value, roleTarget.value.userId, roleValue.value)
    ElMessage.success('角色已变更，权限立即生效')
    roleVisible.value = false
    fetchMembers()
  } catch (e) {
    /* 错误提示已由 request 拦截器统一弹出 */
  } finally {
    roleSaving.value = false
  }
}

/* ---------- 移除成员 ---------- */
async function onRemove(row) {
  const ok = await ElMessageBox.confirm(
    `确定移除成员「${row.username}」吗？移除后其将失去组织内一切访问权：参与的全部项目同步移出，其被分配的任务变为「未分配」（历史记录保留）。`,
    '移除成员',
    { type: 'warning', confirmButtonText: '确定移除', cancelButtonText: '取消', confirmButtonClass: 'el-button--danger' }
  ).catch(() => false)
  if (!ok) return
  try {
    await orgApi.removeMember(orgId.value, row.userId)
    ElMessage.success('成员已移除')
  } catch (e) {
    /* 错误提示已由 request 拦截器统一弹出 */
  }
  fetchMembers()
  orgStore.loadOrgs(true).catch(() => {})
}

const ROLE_CLASS = { OWNER: 'role-OWNER', ADMIN: 'role-ADMIN', MEMBER: 'role-MEMBER' }
</script>

<template>
  <div>
    <div class="toolbar">
      <el-input
        v-model="query.keyword"
        placeholder="搜索用户名 / 邮箱…"
        clearable
        class="search"
        @keyup.enter="onSearch"
        @clear="onSearch"
      >
        <template #append>
          <el-button @click="onSearch">搜索</el-button>
        </template>
      </el-input>
      <div class="sp" />
      <el-button v-if="canManage" type="primary" @click="openAdd">＋ 添加成员</el-button>
    </div>

    <el-table v-loading="loading" :data="list" class="tbl">
      <el-table-column label="用户名" min-width="160">
        <template #default="{ row }">
          <span class="u-cell">
            <span class="u-avatar" :style="{ background: avatarColor(row.username) }">{{ row.username.slice(0, 1).toUpperCase() }}</span>
            <b>{{ row.username }}</b>
            <span v-if="row.userId === myId" class="me">（我）</span>
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="email" label="邮箱" min-width="200" show-overflow-tooltip />
      <el-table-column label="组织角色" width="120">
        <template #default="{ row }">
          <span class="role" :class="ROLE_CLASS[row.role]">{{ row.role }}</span>
        </template>
      </el-table-column>
      <el-table-column prop="joinedAt" label="加入时间" width="180" />
      <el-table-column v-if="canManage" label="操作" width="170">
        <template #default="{ row }">
          <el-button v-if="canChangeRole(row)" link type="primary" @click="openRoleChange(row)">变更角色</el-button>
          <el-button v-if="canRemove(row)" link type="danger" @click="onRemove(row)">移除</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="暂无匹配成员" :image-size="80" />
      </template>
    </el-table>

    <el-pagination
      :current-page="query.page"
      :page-size="query.size"
      :total="total"
      :page-sizes="[10, 20, 50]"
      layout="total, sizes, prev, pager, next"
      class="pager"
      @update:current-page="(p) => (query.page = p)"
      @update:page-size="(s) => (query.size = s)"
      @current-change="fetchMembers"
      @size-change="onSearch"
    />

    <!-- 添加成员弹窗：搜索已注册用户并指定角色（FR-O04，幂等） -->
    <el-dialog v-model="addVisible" title="添加组织成员" width="480px" :close-on-click-modal="false">
      <div class="msub">搜索已注册用户并指定角色；已在组织内的用户标记「已加入」，不可重复添加</div>
      <div class="add-role">
        <span class="lbl">指定角色：</span>
        <el-radio-group v-model="addRole">
          <el-radio value="MEMBER">MEMBER</el-radio>
          <el-radio value="ADMIN">ADMIN</el-radio>
        </el-radio-group>
      </div>
      <div class="add-search">
        <el-input
          v-model="searchKeyword"
          placeholder="输入完整用户名或邮箱搜索…"
          clearable
          @keyup.enter="doSearch"
        />
        <el-button type="primary" :loading="searchLoading" @click="doSearch">搜索</el-button>
      </div>

      <div v-if="searched" class="user-pick">
        <div v-for="u in searchResults" :key="u.id" class="up" :class="{ disabled: u.joined }">
          <span class="u-avatar" :style="{ background: avatarColor(u.username) }">{{ u.username.slice(0, 1).toUpperCase() }}</span>
          <span class="up-text">
            <span class="up-name">{{ u.username }}</span>
            <span class="up-mail">{{ u.email }}</span>
          </span>
          <el-tag v-if="u.joined" type="info" size="small">已加入</el-tag>
          <el-button v-else type="primary" size="small" :loading="addingIds.includes(u.id)" @click="doAdd(u)">
            添加
          </el-button>
        </div>
        <el-empty v-if="searchResults.length === 0" description="未找到该用户，请确认其已注册" :image-size="60" />
      </div>

      <template #footer>
        <el-button @click="addVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 变更角色弹窗（仅 OWNER） -->
    <el-dialog v-model="roleVisible" title="变更成员角色" width="420px" :close-on-click-modal="false">
      <div class="msub" v-if="roleTarget">为「{{ roleTarget.username }}」选择新的组织角色，变更后权限立即生效</div>
      <el-radio-group v-model="roleValue" class="role-group">
        <el-radio value="ADMIN">ADMIN — 可管理成员与项目</el-radio>
        <el-radio value="MEMBER">MEMBER — 仅参与项目</el-radio>
      </el-radio-group>
      <template #footer>
        <el-button @click="roleVisible = false">取消</el-button>
        <el-button type="primary" :loading="roleSaving" @click="submitRoleChange">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
}

.search {
  width: 280px;
}

.sp {
  flex: 1;
}

.tbl {
  background: #fff;
  border-radius: 12px;
}

.u-cell {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.u-avatar {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  color: #fff;
  font-size: 12px;
  font-weight: 700;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: none;
}

.me {
  color: #646a73;
  font-size: 12px;
}

.role {
  font-size: 11px;
  font-weight: 700;
  padding: 2px 9px;
  border-radius: 8px;
}

.role-OWNER {
  background: rgba(255, 136, 0, 0.14);
  color: #ff8800;
}

.role-ADMIN {
  background: rgba(51, 112, 255, 0.12);
  color: #3370ff;
}

.role-MEMBER {
  background: rgba(0, 181, 120, 0.12);
  color: #00b578;
}

.pager {
  margin-top: 14px;
  justify-content: flex-end;
}

.msub {
  font-size: 12px;
  color: #646a73;
  margin: -8px 0 14px;
}

.add-role {
  display: flex;
  align-items: center;
  margin-bottom: 12px;
}

.add-role .lbl {
  font-size: 13px;
  color: #1f2329;
}

.add-search {
  display: flex;
  gap: 8px;
  margin-bottom: 12px;
}

.user-pick {
  border: 1px solid rgba(51, 112, 255, 0.14);
  border-radius: 10px;
  max-height: 220px;
  overflow: auto;
}

.up {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 9px 11px;
  font-size: 13px;
  border-top: 1px solid rgba(31, 35, 41, 0.05);
}

.up:first-child {
  border-top: none;
}

.up.disabled {
  color: #c0c6d1;
}

.up-text {
  display: flex;
  flex-direction: column;
  min-width: 0;
  flex: 1;
}

.up-mail {
  font-size: 11px;
  color: #646a73;
}

.role-group {
  display: flex;
  flex-direction: column;
  gap: 10px;
  align-items: flex-start;
}
</style>
