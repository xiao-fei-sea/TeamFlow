<script setup>
import { ref, reactive, computed, watch, onMounted, inject } from 'vue'
import { useRoute } from 'vue-router'
import draggable from 'vuedraggable'
import * as taskApi from '@/api/task'
import * as projectApi from '@/api/project'
import TaskFormDialog from '@/components/TaskFormDialog.vue'
import { useOrgStore } from '@/stores/org'
import { PRIORITY_META, avatarColor } from '@/utils/format'

/** T4.7 看板页（FR-K01/K02）：
 *  - 三列（待办/进行中/已完成）vuedraggable 拖拽，列头计数，卡片含标题/负责人/优先级/截止（逾期标红）
 *  - 拖拽乐观更新：本地先移动，携带 version 提交 PATCH status；409 冲突回滚（以服务端数据重新分发）并统一提示
 *  - 归档项目禁用拖拽与新建（PRD 第 7 章）
 */

const route = useRoute()
const orgStore = useOrgStore()
const project = inject('project', ref(null))

const orgId = computed(() => String(route.params.orgId))
const projectId = computed(() => String(route.params.projectId))
const isArchived = computed(() => project.value?.status === 0)

const COLS = [
  { key: 'TODO', label: '待办', dot: '#a8b0bf' },
  { key: 'IN_PROGRESS', label: '进行中', dot: '#3370ff' },
  { key: 'DONE', label: '已完成', dot: '#00b578' },
]

const columns = reactive({ TODO: [], IN_PROGRESS: [], DONE: [] })
const loading = ref(false)
const total = ref(0)

/* ---------- 筛选（负责人 / 优先级，FR-K01） ---------- */
const filters = reactive({ assigneeId: '', priority: '' })
const projectMembers = ref([])

async function fetchProjectMembers() {
  try {
    projectMembers.value = await projectApi.listProjectMembers(orgId.value, projectId.value)
  } catch (e) {
    projectMembers.value = []
  }
}

function distribute(tasks) {
  columns.TODO = tasks.filter((t) => t.status === 'TODO')
  columns.IN_PROGRESS = tasks.filter((t) => t.status === 'IN_PROGRESS')
  columns.DONE = tasks.filter((t) => t.status === 'DONE')
}

async function fetchBoard() {
  loading.value = true
  try {
    // 看板一次取全量（上限 100，演示规模内）；筛选条件由服务端过滤
    const data = await taskApi.listTasks(orgId.value, projectId.value, {
      page: 1,
      size: 100,
      assigneeId: filters.assigneeId || undefined,
      priority: filters.priority || undefined,
    })
    total.value = data.total || 0
    distribute(data.list || [])
  } catch (e) {
    distribute([])
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  fetchProjectMembers()
  fetchBoard()
})
watch([orgId, projectId], () => {
  fetchProjectMembers()
  fetchBoard()
})

/* ---------- 拖拽乐观更新 + 409 回滚 ---------- */
const committing = ref(false)

async function onColumnChange(evt, colKey) {
  // added：卡片从其他列拖入本列
  if (!evt.added) return
  const card = evt.added.element
  if (card.status === colKey) return // 列内排序，无需提交

  const fromStatus = card.status
  card.status = colKey // 乐观更新（DOM 已就位）

  committing.value = true
  try {
    const res = await taskApi.updateTaskStatus(orgId.value, projectId.value, card.id, {
      targetStatus: colKey,
      version: card.version,
    })
    card.version = res.version
    card.status = res.status
  } catch (err) {
    // 失败回滚：统一以服务端数据重新分发（含 409 冲突，提示语由拦截器统一弹出）
    card.status = fromStatus
  } finally {
    committing.value = false
    // 静默对账：恢复排序/计数一致性（筛选生效）
    fetchBoard()
  }
}

/* ---------- 新建任务 ---------- */
const createVisible = ref(false)

function openCreate() {
  createVisible.value = true
}

function overdueOf(t) {
  return t.overdue ?? (t.dueAt && t.status !== 'DONE' && new Date(t.dueAt.replace(/-/g, '/')) < new Date())
}

function dueText(t) {
  if (!t.dueAt) return ''
  return `${t.dueAt.slice(5, 10)} ${overdueOf(t) ? '已逾期' : '截止'}`
}
</script>

<template>
  <div>
    <div class="kanban-head">
      <div class="filters">
        <el-select v-model="filters.assigneeId" placeholder="负责人：全部" clearable class="f-sm" @change="fetchBoard">
          <el-option label="未分配" value="none" />
          <el-option v-for="m in projectMembers" :key="m.userId" :label="m.username" :value="m.userId" />
        </el-select>
        <el-select v-model="filters.priority" placeholder="优先级：全部" clearable class="f-sm" @change="fetchBoard">
          <el-option v-for="(meta, p) in PRIORITY_META" :key="p" :label="meta.label" :value="Number(p)" />
        </el-select>
      </div>
      <div class="sp" />
      <span v-if="total > 100" class="more-tip">任务较多，仅展示前 100 张卡片，可用筛选缩小范围</span>
      <el-button v-if="!isArchived" type="primary" @click="openCreate">＋ 新建任务</el-button>
    </div>

    <div v-loading="loading" class="board">
      <div v-for="col in COLS" :key="col.key" class="col" :data-col="col.key">
        <div class="col-head">
          <span class="cdot2" :style="{ background: col.dot }" />
          {{ col.label }}
          <span class="cnt">{{ columns[col.key].length }}</span>
        </div>

        <draggable
          :list="columns[col.key]"
          group="task"
          item-key="id"
          :disabled="isArchived"
          :animation="160"
          ghost-class="ghost"
          class="col-body"
          @change="(evt) => onColumnChange(evt, col.key)"
        >
          <template #item="{ element }">
            <div class="card">
              <div class="c-title">{{ element.title }}</div>
              <div class="c-meta">
                <span class="pri" :class="`pri-${element.priority}`">{{ PRIORITY_META[element.priority]?.label }}</span>
                <span v-if="element.assignee" class="c-assignee">
                  <span class="c-avatar" :style="{ background: avatarColor(element.assignee.username) }">{{ element.assignee.username.slice(0, 1).toUpperCase() }}</span>
                  {{ element.assignee.username }}
                </span>
                <span v-else class="c-assignee none">未分配</span>
                <span v-if="element.dueAt" class="c-due" :class="{ overdue: overdueOf(element) }">{{ dueText(element) }}</span>
              </div>
            </div>
          </template>
        </draggable>

        <button v-if="!isArchived" class="col-add" @click="openCreate">＋ 添加任务</button>
      </div>
    </div>

    <el-empty v-if="!loading && total === 0" description="暂无任务，拖动即状态流转" :image-size="90" />

    <!-- 新建任务弹窗（与任务列表页共用） -->
    <TaskFormDialog
      v-model="createVisible"
      :members="projectMembers"
      :org-id="orgId"
      :project-id="projectId"
      @saved="fetchBoard"
    />
  </div>
</template>

<style scoped>
.kanban-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 14px;
  flex-wrap: wrap;
}

.filters {
  display: flex;
  gap: 8px;
}

.f-sm {
  width: 150px;
}

.sp {
  flex: 1;
}

.more-tip {
  font-size: 12px;
  color: #ff8800;
}

.board {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 14px;
  align-items: start;
}

.col {
  background: rgba(255, 255, 255, 0.75);
  border: 1px solid rgba(51, 112, 255, 0.14);
  border-radius: 12px;
  padding: 10px;
  min-height: 320px;
}

.col-head {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 4px 6px 10px;
  font-size: 13px;
  font-weight: 700;
}

.cdot2 {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.cnt {
  margin-left: auto;
  font-size: 12px;
  color: #646a73;
  background: rgba(31, 35, 41, 0.06);
  padding: 1px 8px;
  border-radius: 8px;
}

.col-body {
  min-height: 60px;
}

.card {
  background: #fff;
  border: 1px solid rgba(51, 112, 255, 0.14);
  border-radius: 10px;
  padding: 11px 12px;
  margin-bottom: 9px;
  cursor: grab;
  transition: box-shadow 0.15s;
}

.card:hover {
  box-shadow: 0 6px 16px rgba(31, 35, 41, 0.1);
}

.ghost {
  opacity: 0.45;
  transform: rotate(2deg);
  border-style: dashed;
}

.c-title {
  font-size: 13px;
  font-weight: 600;
  line-height: 1.4;
  word-break: break-all;
}

.c-meta {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 9px;
  flex-wrap: wrap;
}

.pri {
  font-size: 10px;
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

.c-assignee {
  display: flex;
  align-items: center;
  gap: 5px;
  font-size: 11px;
  color: #646a73;
}

.c-assignee.none {
  color: #a8b0bf;
}

.c-avatar {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  font-size: 9px;
  font-weight: 700;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
}

.c-due {
  font-size: 11px;
  color: #646a73;
  margin-left: auto;
}

.c-due.overdue {
  color: #f54a45;
  font-weight: 700;
}

.col-add {
  width: 100%;
  border: 1px dashed rgba(51, 112, 255, 0.24);
  background: transparent;
  border-radius: 10px;
  padding: 8px;
  color: #646a73;
  font-size: 12px;
  cursor: pointer;
  font-family: inherit;
}

.col-add:hover {
  color: #3370ff;
  border-color: #3370ff;
}

@media (max-width: 900px) {
  .board {
    grid-template-columns: 1fr;
  }
}
</style>
