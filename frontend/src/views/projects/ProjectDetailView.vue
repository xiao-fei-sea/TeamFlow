<script setup>
import { ref, reactive, computed, watch, onMounted, provide } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as projectApi from '@/api/project'
import { useOrgStore } from '@/stores/org'
import { PROJECT_STATUS } from '@/utils/format'

/** T4.5 项目详情：项目信息头（编辑/归档/恢复）+ 三个子页签（任务 / 看板 / 项目成员）。
 *  project 响应式对象通过 provide 供子页签（看板归档禁拖、任务归档禁写）读取
 */

const route = useRoute()
const router = useRouter()
const orgStore = useOrgStore()

const orgId = computed(() => String(route.params.orgId))
const projectId = computed(() => String(route.params.projectId))
const myRole = computed(() => orgStore.orgs.find((o) => o.id === orgId.value)?.myRole || 'MEMBER')
const canManage = computed(() => myRole.value === 'OWNER' || myRole.value === 'ADMIN')

const project = ref(null)
provide('project', project)

const loading = ref(false)

async function fetchProject() {
  loading.value = true
  try {
    project.value = await projectApi.getProject(orgId.value, projectId.value)
  } catch (e) {
    /* 错误提示已由 request 拦截器统一弹出（40401 项目不存在等） */
  } finally {
    loading.value = false
  }
}

onMounted(fetchProject)
watch([orgId, projectId], fetchProject)

const isArchived = computed(() => project.value?.status === 0)

/* ---------- 页签路由 ---------- */
const activeTab = computed(() => {
  if (route.name === 'project-kanban') return 'kanban'
  if (route.name === 'project-members') return 'members'
  return 'tasks'
})

function onTabChange(name) {
  router.push(`/org/${orgId.value}/projects/${projectId.value}/${name}`)
}

/* ---------- 编辑 / 归档 / 恢复 ---------- */
const editVisible = ref(false)
const editFormRef = ref()
const editForm = reactive({ name: '', description: '' })
const editRules = {
  name: [
    { required: true, message: '请输入项目名称', trigger: 'blur' },
    { min: 1, max: 64, message: '项目名称须为 1-64 字符', trigger: 'blur' },
  ],
  description: [{ max: 512, message: '项目描述不能超过 512 字符', trigger: 'blur' }],
}
const saving = ref(false)

function openEdit() {
  editForm.name = project.value?.name || ''
  editForm.description = project.value?.description || ''
  editVisible.value = true
}

async function submitEdit() {
  const valid = await editFormRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    await projectApi.updateProject(orgId.value, projectId.value, {
      name: editForm.name.trim(),
      description: editForm.description,
    })
    ElMessage.success('项目已更新')
    editVisible.value = false
    fetchProject()
  } catch (e) {
    /* 错误提示已由 request 拦截器统一弹出 */
  } finally {
    saving.value = false
  }
}

async function onArchive() {
  const ok = await ElMessageBox.confirm(
    `归档「${project.value.name}」后项目将被只读封存：所有成员不可再创建、编辑或拖动任务，仅可查看（可随时恢复）。`,
    '归档项目',
    { type: 'warning', confirmButtonText: '归档', cancelButtonText: '取消' }
  ).catch(() => false)
  if (!ok) return
  try {
    await projectApi.archiveProject(orgId.value, projectId.value)
    ElMessage.success('项目已归档')
  } catch (e) {
    /* 错误提示已由 request 拦截器统一弹出 */
  }
  fetchProject()
}

async function onRestore() {
  try {
    await projectApi.restoreProject(orgId.value, projectId.value)
    ElMessage.success('项目已恢复，恢复读写能力')
  } catch (e) {
    /* 错误提示已由 request 拦截器统一弹出 */
  }
  fetchProject()
}
</script>

<template>
  <div v-loading="loading">
    <!-- 项目信息头 -->
    <div class="proj-head" v-if="project">
      <div class="head-main">
        <div class="title-row">
          <h3>{{ project.name }}</h3>
          <span class="st-tag" :class="isArchived ? 'st-archived' : 'st-active'">{{ PROJECT_STATUS[project.status]?.label }}</span>
        </div>
        <div class="desc">{{ project.description || '暂无描述' }}</div>
        <div class="meta">
          <span>创建于 {{ project.createdAt }}</span>
          <el-divider direction="vertical" />
          <span>成员 {{ project.memberCount }} 人</span>
          <el-divider direction="vertical" />
          <span>组织角色 {{ project.myRole }}</span>
        </div>
      </div>
      <div v-if="canManage" class="head-acts">
        <el-button @click="openEdit">编辑</el-button>
        <el-button v-if="!isArchived" type="warning" plain @click="onArchive">归档</el-button>
        <el-button v-else type="success" plain @click="onRestore">恢复</el-button>
      </div>
    </div>

    <!-- 归档只读提示（PRD FR-P04：归档即封存） -->
    <el-alert
      v-if="isArchived"
      type="warning"
      :closable="false"
      show-icon
      class="archived-tip"
      title="项目已归档 · 只读封存"
      description="归档后不可创建、编辑或拖动任务，恢复后能力不变。"
    />

    <el-tabs :model-value="activeTab" class="proj-tabs" @tab-change="onTabChange">
      <el-tab-pane label="任务" name="tasks" />
      <el-tab-pane label="看板" name="kanban" />
      <el-tab-pane label="项目成员" name="members" />
    </el-tabs>

    <router-view />
  </div>
</template>

<style scoped>
.proj-head {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  background: #fff;
  border: 1px solid rgba(51, 112, 255, 0.14);
  border-radius: 12px;
  padding: 16px 18px;
  margin-bottom: 14px;
}

.head-main {
  flex: 1;
  min-width: 0;
}

.title-row {
  display: flex;
  align-items: center;
  gap: 10px;
}

h3 {
  font-size: 17px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.st-tag {
  font-size: 11px;
  padding: 1px 8px;
  border-radius: 8px;
  font-weight: 600;
  flex: none;
}

.st-active {
  background: rgba(0, 181, 120, 0.12);
  color: #00b578;
}

.st-archived {
  background: rgba(100, 106, 115, 0.12);
  color: #646a73;
}

.desc {
  font-size: 13px;
  color: #646a73;
  margin-top: 6px;
}

.meta {
  display: flex;
  align-items: center;
  margin-top: 8px;
  font-size: 12px;
  color: #646a73;
}

.head-acts {
  flex: none;
}

.archived-tip {
  margin-bottom: 14px;
}

.proj-tabs :deep(.el-tabs__header) {
  margin-bottom: 14px;
}
</style>
