<script setup>
import { ref, reactive, computed, watch, onMounted, inject } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as taskApi from '@/api/task'
import * as projectApi from '@/api/project'
import TaskFormDialog from '@/components/TaskFormDialog.vue'
import { useOrgStore } from '@/stores/org'
import { PRIORITY_META, TASK_STATUS_META, avatarColor } from '@/utils/format'

/** T4.6 任务列表与表单：筛选/排序/分页、创建/编辑弹窗（负责人下拉=项目成员）、删除确认（OWNER/ADMIN）、逾期红色标识 */

const route = useRoute()
const orgStore = useOrgStore()
const project = inject('project', ref(null))

const orgId = computed(() => String(route.params.orgId))
const projectId = computed(() => String(route.params.projectId))
const isArchived = computed(() => project.value?.status === 0)
const myRole = computed(() => orgStore.orgs.find((o) => o.id === orgId.value)?.myRole || 'MEMBER')
const canDelete = computed(() => myRole.value === 'OWNER' || myRole.value === 'ADMIN')

/* ---------- 项目成员（负责人下拉选项） ---------- */
const projectMembers = ref([])
async function fetchProjectMembers() {
  try {
    projectMembers.value = await projectApi.listProjectMembers(orgId.value, projectId.value)
  } catch (e) {
    projectMembers.value = []
  }
}

/* ---------- 任务列表 ---------- */
const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({
  page: 1,
  size: 10,
  keyword: '',
  assigneeId: '',
  priority: '',
  orderBy: 'created_at',
  orderDir: 'desc',
})

async function fetchTasks() {
  loading.value = true
  try {
    const data = await taskApi.listTasks(orgId.value, projectId.value, {
      page: query.page,
      size: query.size,
      keyword: query.keyword || undefined,
      assigneeId: query.assigneeId || undefined,
      priority: query.priority || undefined,
      orderBy: query.orderBy,
      orderDir: query.orderDir,
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
  fetchTasks()
}

function toggleOrder(field) {
  if (query.orderBy === field) {
    query.orderDir = query.orderDir === 'desc' ? 'asc' : 'desc'
  } else {
    query.orderBy = field
    query.orderDir = 'desc'
  }
  onSearch()
}

onMounted(() => {
  fetchProjectMembers()
  fetchTasks()
})
watch([orgId, projectId], () => {
  fetchProjectMembers()
  onSearch()
})

/* ---------- 创建 / 编辑任务（共用 TaskFormDialog） ---------- */
const editVisible = ref(false)
const editing = ref(null)

function openCreate() {
  editing.value = null
  editVisible.value = true
}

function openEdit(task) {
  editing.value = task
  editVisible.value = true
}

/* ---------- 删除任务（仅 OWNER/ADMIN） ---------- */
async function onDelete(task) {
  const ok = await ElMessageBox.confirm(`确定删除任务「${task.title}」吗？删除后从列表与看板消失。`, '删除任务', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消',
    confirmButtonClass: 'el-button--danger',
  }).catch(() => false)
  if (!ok) return
  try {
    await taskApi.deleteTask(orgId.value, projectId.value, task.id)
    ElMessage.success('任务已删除')
  } catch (e) {
    /* 错误提示已由 request 拦截器统一弹出 */
  }
  fetchTasks()
}

function priorityText(p) {
  return PRIORITY_META[p]?.label || p
}
</script>

<template>
  <div>
    <!-- 归档项目禁写（PRD 第 7 章） -->
    <el-alert
      v-if="isArchived"
      type="warning"
      :closable="false"
      show-icon
      class="arch-tip"
      title="项目已归档，任务创建 / 编辑 / 删除已禁用"
    />

    <div class="toolbar">
      <el-input
        v-model="query.keyword"
        placeholder="搜索任务标题…"
        clearable
        class="w-kw"
        @keyup.enter="onSearch"
        @clear="onSearch"
      />
      <el-select v-model="query.assigneeId" placeholder="负责人" clearable class="w-sm" @change="onSearch">
        <el-option label="未分配" value="none" />
        <el-option v-for="m in projectMembers" :key="m.userId" :label="m.username" :value="m.userId" />
      </el-select>
      <el-select v-model="query.priority" placeholder="优先级" clearable class="w-sm" @change="onSearch">
        <el-option v-for="(meta, p) in PRIORITY_META" :key="p" :label="meta.label" :value="Number(p)" />
      </el-select>
      <el-select v-model="query.orderBy" class="w-sm" @change="onSearch">
        <el-option label="按创建时间" value="created_at" />
        <el-option label="按截止时间" value="due_at" />
      </el-select>
      <el-button @click="toggleOrder(query.orderBy)">
        {{ query.orderDir === 'desc' ? '降序 ↓' : '升序 ↑' }}
      </el-button>
      <el-button @click="onSearch">筛选</el-button>
      <div class="sp" />
      <el-button v-if="!isArchived" type="primary" @click="openCreate">＋ 新建任务</el-button>
    </div>

    <el-table v-loading="loading" :data="list" class="tbl">
      <el-table-column label="标题" min-width="220" show-overflow-tooltip>
        <template #default="{ row }">
          <b>{{ row.title }}</b>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <span class="st" :class="`st-${row.status}`">{{ TASK_STATUS_META[row.status]?.label || row.status }}</span>
        </template>
      </el-table-column>
      <el-table-column label="优先级" width="90">
        <template #default="{ row }">
          <span class="pri" :class="`pri-${row.priority}`">{{ priorityText(row.priority) }}</span>
        </template>
      </el-table-column>
      <el-table-column label="负责人" width="130">
        <template #default="{ row }">
          <span v-if="row.assignee" class="assignee">
            <span class="u-avatar" :style="{ background: avatarColor(row.assignee.username) }">{{ row.assignee.username.slice(0, 1).toUpperCase() }}</span>
            {{ row.assignee.username }}
          </span>
          <span v-else class="unassigned">未分配</span>
        </template>
      </el-table-column>
      <el-table-column label="截止日期" width="170">
        <template #default="{ row }">
          <span v-if="row.dueAt" :class="{ overdue: row.overdue }">
            {{ row.dueAt }}{{ row.overdue ? '（已逾期）' : '' }}
          </span>
          <span v-else class="unassigned">—</span>
        </template>
      </el-table-column>
      <el-table-column prop="updatedAt" label="更新时间" width="170" />
      <el-table-column v-if="!isArchived" label="操作" width="130" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button v-if="canDelete" link type="danger" @click="onDelete(row)">删除</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="暂无任务" :image-size="80" />
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
      @current-change="fetchTasks"
      @size-change="onSearch"
    />

    <!-- 创建 / 编辑任务弹窗（与看板页共用） -->
    <TaskFormDialog
      v-model="editVisible"
      :task="editing"
      :members="projectMembers"
      :org-id="orgId"
      :project-id="projectId"
      @saved="fetchTasks"
    />
  </div>
</template>

<style scoped>
.arch-tip {
  margin-bottom: 12px;
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
  flex-wrap: wrap;
}

.w-kw {
  width: 220px;
}

.w-sm {
  width: 140px;
}

.sp {
  flex: 1;
}

.tbl {
  background: #fff;
  border-radius: 12px;
}

.st {
  font-size: 11px;
  font-weight: 700;
  padding: 2px 9px;
  border-radius: 8px;
}

.st-TODO {
  background: rgba(100, 106, 115, 0.12);
  color: #646a73;
}

.st-IN_PROGRESS {
  background: rgba(51, 112, 255, 0.12);
  color: #3370ff;
}

.st-DONE {
  background: rgba(0, 181, 120, 0.12);
  color: #00b578;
}

.pri {
  font-size: 11px;
  font-weight: 700;
  padding: 1px 7px;
  border-radius: 7px;
}

.pri-1 {
  background: rgba(100, 106, 115, 0.12);
  color: #646a73;
}

.pri-2 {
  background: rgba(51, 112, 255, 0.12);
  color: #3370ff;
}

.pri-3 {
  background: rgba(255, 136, 0, 0.14);
  color: #ff8800;
}

.pri-4 {
  background: rgba(245, 74, 69, 0.13);
  color: #f54a45;
}

.assignee {
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.u-avatar {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  color: #fff;
  font-size: 11px;
  font-weight: 700;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: none;
}

.unassigned {
  color: #a8b0bf;
}

.overdue {
  color: #f54a45;
  font-weight: 700;
}

.pager {
  margin-top: 14px;
  justify-content: flex-end;
}

.msub {
  font-size: 12px;
  color: #646a73;
  margin: -8px 0 12px;
}
</style>
