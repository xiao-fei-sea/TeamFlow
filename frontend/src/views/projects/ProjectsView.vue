<script setup>
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Folder } from '@element-plus/icons-vue'
import * as projectApi from '@/api/project'
import { useOrgStore } from '@/stores/org'
import { PROJECT_STATUS, avatarColor } from '@/utils/format'

/** T4.5 项目列表：卡片式列表、搜索/状态筛选、创建/编辑弹窗、归档/恢复；
 *  可见性差异（OWNER/ADMIN 全量 / MEMBER 仅参与）由服务端过滤，前端按角色显隐管理入口
 */

const route = useRoute()
const router = useRouter()
const orgStore = useOrgStore()

const orgId = computed(() => String(route.params.orgId))
const myRole = computed(() => orgStore.orgs.find((o) => o.id === orgId.value)?.myRole || 'MEMBER')
const canManage = computed(() => myRole.value === 'OWNER' || myRole.value === 'ADMIN')

/* ---------- 项目列表 ---------- */
const loading = ref(false)
const list = ref([])
const total = ref(0)
const query = reactive({ page: 1, size: 12, keyword: '', status: 1 })

async function fetchProjects() {
  loading.value = true
  try {
    const data = await projectApi.listProjects(orgId.value, {
      page: query.page,
      size: query.size,
      keyword: query.keyword || undefined,
      status: query.status,
    })
    list.value = data.list || []
    total.value = data.total || 0
    // 当前页被删空时回退一页
    if (list.value.length === 0 && query.page > 1) {
      query.page -= 1
      fetchProjects()
    }
  } catch (e) {
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function onSearch() {
  query.page = 1
  fetchProjects()
}

function setStatus(s) {
  query.status = s
  onSearch()
}

onMounted(fetchProjects)
watch(orgId, fetchProjects)

function openProject(p) {
  router.push(`/org/${orgId.value}/projects/${p.id}/tasks`)
}

/** 卡片上的「我的角色」：优先项目角色，未加入项目则显示组织角色 */
function roleText(p) {
  if (p.projectRole === 'PROJECT_OWNER') return '负责人'
  if (p.projectRole === 'PROJECT_MEMBER') return '成员'
  return `组织${p.myRole}`
}

/* ---------- 创建 / 编辑项目 ---------- */
const editVisible = ref(false)
const editing = ref(null) // null=新建
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

function openCreate() {
  editing.value = null
  editForm.name = ''
  editForm.description = ''
  editVisible.value = true
}

function openEdit(p) {
  editing.value = p
  editForm.name = p.name
  editForm.description = p.description || ''
  editVisible.value = true
}

async function submitEdit() {
  const valid = await editFormRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    if (editing.value) {
      await projectApi.updateProject(orgId.value, editing.value.id, {
        name: editForm.name.trim(),
        description: editForm.description,
      })
      ElMessage.success('项目已更新')
    } else {
      await projectApi.createProject(orgId.value, {
        name: editForm.name.trim(),
        description: editForm.description,
      })
      ElMessage.success('项目创建成功')
    }
    editVisible.value = false
    fetchProjects()
  } catch (e) {
    /* 错误提示已由 request 拦截器统一弹出 */
  } finally {
    saving.value = false
  }
}

/* ---------- 归档 / 恢复 ---------- */
async function onArchive(p) {
  const ok = await ElMessageBox.confirm(
    `归档「${p.name}」后项目将被只读封存：所有成员不可再创建、编辑或拖动任务，仅可查看（可随时恢复）。`,
    '归档项目',
    { type: 'warning', confirmButtonText: '归档', cancelButtonText: '取消' }
  ).catch(() => false)
  if (!ok) return
  try {
    await projectApi.archiveProject(orgId.value, p.id)
    ElMessage.success('项目已归档')
  } catch (e) {
    /* 错误提示已由 request 拦截器统一弹出 */
  }
  fetchProjects()
}

async function onRestore(p) {
  try {
    await projectApi.restoreProject(orgId.value, p.id)
    ElMessage.success('项目已恢复，恢复读写能力')
  } catch (e) {
    /* 错误提示已由 request 拦截器统一弹出 */
  }
  fetchProjects()
}
</script>

<template>
  <div>
    <div class="toolbar">
      <el-input
        v-model="query.keyword"
        placeholder="搜索项目名称…"
        clearable
        class="search"
        @keyup.enter="onSearch"
        @clear="onSearch"
      >
        <template #append>
          <el-button @click="onSearch">搜索</el-button>
        </template>
      </el-input>
      <div class="seg">
        <button :class="{ active: query.status === 1 }" @click="setStatus(1)">进行中</button>
        <button :class="{ active: query.status === 0 }" @click="setStatus(0)">已归档</button>
      </div>
      <div class="sp" />
      <el-button v-if="canManage" type="primary" @click="openCreate">＋ 新建项目</el-button>
    </div>

    <div v-loading="loading" class="proj-grid">
      <div v-for="p in list" :key="p.id" class="proj-card" @click="openProject(p)">
        <div class="card-head">
          <h4 class="p-name" :title="p.name">{{ p.name }}</h4>
          <span class="st-tag" :class="p.status === 1 ? 'st-active' : 'st-archived'">
            {{ PROJECT_STATUS[p.status]?.label }}
          </span>
        </div>
        <div class="desc">{{ p.description || '暂无描述' }}</div>
        <div class="stats">
          <span>任务 <b>{{ p.taskTotal ?? 0 }}</b></span>
          <span>进行中 <b class="doing">{{ p.taskDoing ?? 0 }}</b></span>
          <span>我的角色 <b>{{ roleText(p) }}</b></span>
        </div>
        <div v-if="canManage" class="card-acts" @click.stop>
          <el-button link type="primary" size="small" @click="openEdit(p)">编辑</el-button>
          <el-button v-if="p.status === 1" link type="warning" size="small" @click="onArchive(p)">归档</el-button>
          <el-button v-else link type="success" size="small" @click="onRestore(p)">恢复</el-button>
        </div>
      </div>

      <div v-if="canManage && list.length > 0" class="proj-card add-card" @click="openCreate">＋ 创建新项目</div>

      <el-empty v-if="!loading && list.length === 0" :description="query.status === 1 ? '暂无进行中的项目' : '暂无已归档项目'" class="empty" />
    </div>

    <el-pagination
      :current-page="query.page"
      :page-size="query.size"
      :total="total"
      :page-sizes="[12, 24, 48]"
      layout="total, sizes, prev, pager, next"
      class="pager"
      @update:current-page="(p) => (query.page = p)"
      @update:page-size="(s) => (query.size = s)"
      @current-change="fetchProjects"
      @size-change="onSearch"
    />

    <!-- 创建 / 编辑项目弹窗 -->
    <el-dialog v-model="editVisible" :title="editing ? '编辑项目' : '新建项目'" width="460px" :close-on-click-modal="false">
      <div class="msub" v-if="!editing">项目将归属当前组织，你将成为项目负责人；初始看板为「待办 / 进行中 / 已完成」三列</div>
      <el-form ref="editFormRef" :model="editForm" :rules="editRules" label-position="top" @submit.prevent="submitEdit">
        <el-form-item label="项目名称" prop="name">
          <el-input v-model="editForm.name" maxlength="64" placeholder="1-64 字符，组织内唯一" @keyup.enter="submitEdit" />
        </el-form-item>
        <el-form-item label="项目描述" prop="description">
          <el-input v-model="editForm.description" type="textarea" :rows="3" maxlength="512" show-word-limit placeholder="≤ 512 字符（选填）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="submitEdit">{{ editing ? '保存' : '创建' }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.search {
  width: 260px;
}

.seg {
  display: flex;
  border: 1px solid rgba(51, 112, 255, 0.14);
  border-radius: 9px;
  overflow: hidden;
}

.seg button {
  border: none;
  background: #fff;
  padding: 8px 14px;
  font-size: 13px;
  color: #646a73;
  cursor: pointer;
  font-family: inherit;
}

.seg button.active {
  background: rgba(51, 112, 255, 0.12);
  color: #3370ff;
  font-weight: 600;
}

.sp {
  flex: 1;
}

.proj-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(250px, 1fr));
  gap: 14px;
  min-height: 120px;
}

.proj-card {
  background: #fff;
  border: 1px solid rgba(51, 112, 255, 0.14);
  border-radius: 12px;
  padding: 16px;
  cursor: pointer;
  transition: all 0.18s;
  position: relative;
}

.proj-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 22px rgba(51, 112, 255, 0.14);
  border-color: rgba(51, 112, 255, 0.35);
}

.card-head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.p-name {
  font-size: 15px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
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
  font-size: 12px;
  color: #646a73;
  margin-top: 6px;
  min-height: 34px;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.stats {
  display: flex;
  gap: 14px;
  margin-top: 10px;
  font-size: 12px;
  color: #646a73;
}

.stats b {
  color: #1f2329;
}

.stats .doing {
  color: #3370ff;
}

.card-acts {
  margin-top: 10px;
  padding-top: 8px;
  border-top: 1px dashed rgba(51, 112, 255, 0.14);
}

.add-card {
  border-style: dashed;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 120px;
  color: #646a73;
  font-size: 13px;
}

.empty {
  grid-column: 1 / -1;
}

.pager {
  margin-top: 16px;
  justify-content: flex-end;
}

.msub {
  font-size: 12px;
  color: #646a73;
  margin: -8px 0 14px;
}
</style>
