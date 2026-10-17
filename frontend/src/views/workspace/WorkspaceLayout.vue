<script setup>
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Bell, ArrowDown, SwitchButton } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'
import { useOrgStore } from '@/stores/org'
import { avatarColor } from '@/utils/format'

/** T4.2 工作台布局：侧边栏 + 顶栏（组织切换器 / 通知占位 / 头像菜单）+ 组织上下文守卫
 *  T4.3 组织切换：切换后路由变化，各页面按 orgId 重新拉取数据
 */

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const orgStore = useOrgStore()

const orgId = computed(() => String(route.params.orgId || ''))
const currentOrg = computed(() => orgStore.orgs.find((o) => o.id === orgId.value) || null)

/** 页面标题（面包屑第二段） */
const pageLabel = computed(() => {
  if (route.name === 'projects') return '项目'
  if (route.name === 'members') return '成员管理'
  if (route.name === 'project-tasks') return '任务'
  if (route.name === 'project-kanban') return '看板'
  if (route.name === 'project-members') return '项目成员'
  return ''
})

/** 组织上下文守卫：访问未加入的组织 → 回退到有效组织（PRD 第 7 章越权场景） */
async function ensureOrg() {
  try {
    const orgs = await orgStore.loadOrgs()
    if (orgs.some((o) => o.id === orgId.value)) return
    const target = orgStore.resolveOrg(orgs)
    if (target && target !== orgId.value) {
      router.replace(`/org/${target}/projects`)
    } else {
      router.replace('/welcome')
    }
  } catch (e) {
    /* 401 由拦截器统一跳登录 */
  }
}

onMounted(async () => {
  await authStore.fetchMe()
  await ensureOrg()
})
watch(orgId, ensureOrg)

/* ---------- 组织切换器 ---------- */
async function onSwitchOrg(command) {
  if (command === '__create') {
    createVisible.value = true
    return
  }
  if (command === orgId.value) return
  await orgStore.switchOrg(command)
}

/* ---------- 创建组织 ---------- */
const createVisible = ref(false)
const createFormRef = ref()
const createForm = reactive({ name: '' })
const createRules = [
  { required: true, message: '请输入组织名称', trigger: 'blur' },
  { min: 1, max: 64, message: '组织名称须为 1-64 字符', trigger: 'blur' },
]
const creating = ref(false)

async function submitCreate() {
  const valid = await createFormRef.value.validate().catch(() => false)
  if (!valid) return
  creating.value = true
  try {
    await orgStore.createOrg(createForm.name.trim())
    ElMessage.success('组织创建成功')
    createVisible.value = false
    createForm.name = ''
  } catch (e) {
    /* 错误提示已由 request 拦截器统一弹出 */
  } finally {
    creating.value = false
  }
}

/* ---------- 退出登录 ---------- */
async function onLogout() {
  const ok = await ElMessageBox.confirm('确定退出登录吗？', '提示', { type: 'warning', confirmButtonText: '退出', cancelButtonText: '取消' }).catch(() => false)
  if (!ok) return
  await authStore.logout()
  orgStore.reset()
  router.replace('/login')
}
</script>

<template>
  <div class="app">
    <!-- 侧边栏：深松墨，纵向流线导航 -->
    <aside class="side">
      <div class="brand">
        <div class="mark tf-num">TF</div>
        <span class="wordmark tf-num">TeamFlow</span>
      </div>

      <!-- 组织切换器（T4.3：仅列出所属组织） -->
      <el-dropdown class="org-switch-dd" trigger="click" @command="onSwitchOrg">
        <div class="org-switch">
          <div class="org-avatar" :style="{ background: avatarColor(currentOrg?.name) }">
            {{ (currentOrg?.name || '?').slice(0, 1) }}
          </div>
          <div class="o-text">
            <div class="o-name">{{ currentOrg?.name || '…' }}</div>
            <div class="o-sub">我的角色：{{ currentOrg?.myRole || '-' }}</div>
          </div>
          <el-icon class="caret"><ArrowDown /></el-icon>
        </div>
        <template #dropdown>
          <el-dropdown-menu>
            <el-dropdown-item
              v-for="o in orgStore.orgs"
              :key="o.id"
              :command="o.id"
              :class="{ current: o.id === orgId }"
            >
              <span class="mi-avatar" :style="{ background: avatarColor(o.name) }">{{ o.name.slice(0, 1) }}</span>
              <span class="mi-name">{{ o.name }}</span>
              <span class="mi-check" v-if="o.id === orgId">✓ 当前</span>
            </el-dropdown-item>
            <el-dropdown-item divided command="__create">＋ 创建组织</el-dropdown-item>
          </el-dropdown-menu>
        </template>
      </el-dropdown>

      <nav class="nav">
        <router-link class="nav-item" :class="{ active: route.name === 'projects' }" :to="`/org/${orgId}/projects`">项目</router-link>
        <router-link class="nav-item" :class="{ active: route.name === 'members' }" :to="`/org/${orgId}/members`">成员管理</router-link>
        <span class="nav-item disabled">数据统计<span class="p1tag">P1</span></span>
        <span class="nav-item disabled">审计日志<span class="p1tag">P1</span></span>
      </nav>

      <div class="quit" @click="onLogout"><el-icon><SwitchButton /></el-icon>退出登录</div>
    </aside>

    <!-- 主区 -->
    <div class="main">
      <div class="topbar">
        <div class="crumb">
          {{ currentOrg?.name || '工作台' }}
          <span class="sep">/</span>
          <span class="cur">{{ pageLabel }}</span>
        </div>
        <div class="sp" />
        <!-- 通知占位（P1：M5 交付 WebSocket 推送） -->
        <el-tooltip content="站内通知将在 P1（M5）交付" placement="bottom">
          <el-badge :value="0" :hidden="true" class="bell">
            <el-icon :size="16"><Bell /></el-icon>
          </el-badge>
        </el-tooltip>
        <el-dropdown trigger="click">
          <div class="avatar" :style="{ background: avatarColor(authStore.user?.username) }" :title="authStore.user?.username">
            {{ (authStore.user?.username || '?').slice(0, 1).toUpperCase() }}
          </div>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item disabled>
                <div class="user-info">
                  <div class="u-name">{{ authStore.user?.username }}</div>
                  <div class="u-mail">{{ authStore.user?.email }}</div>
                </div>
              </el-dropdown-item>
              <el-dropdown-item divided @click="onLogout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>

      <div class="content">
        <router-view />
      </div>
    </div>

    <!-- 创建组织弹窗 -->
    <el-dialog v-model="createVisible" title="创建组织" width="440px" :close-on-click-modal="false">
      <div class="msub">组织是数据隔离的边界，创建者自动成为 OWNER（单用户最多创建 5 个）</div>
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" @submit.prevent="submitCreate">
        <el-form-item label="组织名称" prop="name">
          <el-input v-model="createForm.name" maxlength="64" placeholder="1-64 字符" @keyup.enter="submitCreate" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="submitCreate">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.app {
  display: grid;
  grid-template-columns: 216px 1fr;
  min-height: 100vh;
}

/* ---------- 侧边栏 ---------- */
.side {
  background: linear-gradient(180deg, #fbfcff, #f3f6ff);
  border-right: 1px solid rgba(51, 112, 255, 0.14);
  padding: 16px 12px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.org-switch-dd {
  width: 100%;
}

.org-switch {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px;
  border-radius: 10px;
  cursor: pointer;
  border: 1px solid rgba(51, 112, 255, 0.14);
  background: #fff;
  width: 100%;
  outline: none;
}

.org-switch:hover {
  border-color: #3370ff;
}

.org-avatar {
  width: 30px;
  height: 30px;
  border-radius: 8px;
  color: #fff;
  font-size: 13px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  flex: none;
}

.o-text {
  min-width: 0;
}

.o-name {
  font-size: 14px;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.o-sub {
  font-size: 11px;
  color: #646a73;
}

.caret {
  margin-left: auto;
  color: #646a73;
  font-size: 12px;
}

.nav {
  margin-top: 14px;
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 9px 10px;
  border-radius: 9px;
  font-size: 14px;
  color: #646a73;
  text-decoration: none;
  cursor: pointer;
}

.nav-item:hover {
  background: rgba(51, 112, 255, 0.07);
  color: #1f2329;
}

.nav-item.active {
  background: rgba(51, 112, 255, 0.12);
  color: #3370ff;
  font-weight: 600;
}

.nav-item.disabled {
  cursor: not-allowed;
  opacity: 0.75;
}

.p1tag {
  margin-left: auto;
  font-size: 10px;
  padding: 0 6px;
  border-radius: 8px;
  background: rgba(255, 136, 0, 0.15);
  color: #ff8800;
}

.quit {
  margin-top: auto;
  font-size: 13px;
  color: #646a73;
  padding: 9px 10px;
  cursor: pointer;
  border-radius: 9px;
  display: flex;
  align-items: center;
  gap: 6px;
}

.quit:hover {
  background: rgba(245, 74, 69, 0.08);
  color: #f54a45;
}

/* ---------- 主区 ---------- */
.main {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.topbar {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 20px;
  border-bottom: 1px solid rgba(51, 112, 255, 0.14);
  background: rgba(255, 255, 255, 0.7);
}

.crumb {
  font-size: 14px;
  font-weight: 600;
}

.crumb .sep {
  color: #646a73;
  font-weight: 400;
  margin: 0 6px;
}

.crumb .cur {
  color: #3370ff;
}

.sp {
  flex: 1;
}

.bell {
  width: 34px;
  height: 34px;
  border-radius: 9px;
  border: 1px solid rgba(51, 112, 255, 0.14);
  background: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: #646a73;
}

.bell:hover {
  color: #3370ff;
  border-color: #3370ff;
}

.avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  color: #fff;
  font-size: 13px;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  outline: none;
}

.content {
  padding: 20px;
  overflow: auto;
  flex: 1;
}

/* 下拉项 */
.mi-avatar {
  width: 22px;
  height: 22px;
  border-radius: 6px;
  color: #fff;
  font-size: 11px;
  font-weight: 700;
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.mi-name {
  overflow: hidden;
  text-overflow: ellipsis;
}

.mi-check {
  margin-left: auto;
  color: #00b578;
  font-size: 12px;
}

.user-info .u-name {
  font-weight: 600;
}

.user-info .u-mail {
  font-size: 12px;
  color: #646a73;
}

.msub {
  font-size: 12px;
  color: #646a73;
  margin: -8px 0 14px;
}

@media (max-width: 900px) {
  .app {
    grid-template-columns: 1fr;
  }

  .side {
    flex-direction: row;
    flex-wrap: wrap;
    align-items: center;
  }

  .nav {
    flex-direction: row;
    margin-top: 0;
  }

  .quit {
    margin-top: 0;
    margin-left: auto;
  }
}
</style>
