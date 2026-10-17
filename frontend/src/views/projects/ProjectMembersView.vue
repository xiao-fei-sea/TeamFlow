<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import * as projectApi from '@/api/project'
import * as orgApi from '@/api/org'
import { useOrgStore } from '@/stores/org'
import { avatarColor } from '@/utils/format'

/** 项目成员页签（FR-PM01~03）：仅组织 OWNER/ADMIN 可管理；
 *  添加成员只能从本组织成员中选择（服务端校验），PROJECT_OWNER 不可移除
 */

const route = useRoute()
const orgStore = useOrgStore()

const orgId = computed(() => String(route.params.orgId))
const projectId = computed(() => String(route.params.projectId))
const canManage = computed(() => {
  const role = orgStore.orgs.find((o) => o.id === orgId.value)?.myRole
  return role === 'OWNER' || role === 'ADMIN'
})

const loading = ref(false)
const members = ref([])

async function fetchMembers() {
  loading.value = true
  try {
    members.value = await projectApi.listProjectMembers(orgId.value, projectId.value)
  } catch (e) {
    members.value = []
  } finally {
    loading.value = false
  }
}

onMounted(fetchMembers)
watch([orgId, projectId], fetchMembers)

/* ---------- 添加项目成员（从本组织成员中选择） ---------- */
const addVisible = ref(false)
const addLoading = ref(false)
const candidates = ref([])
const addingIds = ref([])

async function openAdd() {
  addVisible.value = true
  addLoading.value = true
  try {
    // 拉取本组织成员（上限 100），排除已在项目内的
    const page = await orgApi.listMembers(orgId.value, { page: 1, size: 100 })
    const inProject = new Set(members.value.map((m) => m.userId))
    candidates.value = (page.list || []).filter((m) => !inProject.has(m.userId))
  } catch (e) {
    candidates.value = []
  } finally {
    addLoading.value = false
  }
}

async function doAdd(member) {
  addingIds.value.push(member.userId)
  try {
    await projectApi.addProjectMember(orgId.value, projectId.value, member.userId)
    ElMessage.success(`已将 ${member.username} 加入项目`)
    member._added = true
    fetchMembers()
    openAdd() // 刷新候选列表
  } catch (e) {
    /* 错误提示已由 request 拦截器统一弹出 */
  } finally {
    addingIds.value = addingIds.value.filter((id) => id !== member.userId)
  }
}

/* ---------- 移除项目成员 ---------- */
async function onRemove(member) {
  const ok = await ElMessageBox.confirm(
    `确定将「${member.username}」移出项目吗？其被分配的任务将变为「未分配」（历史记录保留）。`,
    '移除项目成员',
    { type: 'warning', confirmButtonText: '移除', cancelButtonText: '取消' }
  ).catch(() => false)
  if (!ok) return
  try {
    await projectApi.removeProjectMember(orgId.value, projectId.value, member.userId)
    ElMessage.success('已移出项目')
  } catch (e) {
    /* 错误提示已由 request 拦截器统一弹出 */
  }
  fetchMembers()
}

const ROLE_LABEL = { PROJECT_OWNER: '负责人', PROJECT_MEMBER: '成员' }
</script>

<template>
  <div>
    <div class="toolbar">
      <span class="hint">负责人默认为项目创建者，不可移除；MEMBER 被移出组织时会自动移出其参与的全部项目</span>
      <div class="sp" />
      <el-button v-if="canManage" type="primary" @click="openAdd">＋ 添加项目成员</el-button>
    </div>

    <el-table v-loading="loading" :data="members" class="tbl">
      <el-table-column label="用户名" min-width="160">
        <template #default="{ row }">
          <span class="u-cell">
            <span class="u-avatar" :style="{ background: avatarColor(row.username) }">{{ row.username.slice(0, 1).toUpperCase() }}</span>
            <b>{{ row.username }}</b>
          </span>
        </template>
      </el-table-column>
      <el-table-column label="项目角色" width="140">
        <template #default="{ row }">
          <span class="role" :class="row.projectRole === 'PROJECT_OWNER' ? 'role-owner' : 'role-member'">
            {{ ROLE_LABEL[row.projectRole] || row.projectRole }}
          </span>
        </template>
      </el-table-column>
      <el-table-column prop="joinedAt" label="加入时间" width="200" />
      <el-table-column v-if="canManage" label="操作" width="120">
        <template #default="{ row }">
          <el-button v-if="row.projectRole !== 'PROJECT_OWNER'" link type="danger" @click="onRemove(row)">移除</el-button>
        </template>
      </el-table-column>
      <template #empty>
        <el-empty description="暂无项目成员" :image-size="80" />
      </template>
    </el-table>

    <!-- 从本组织成员中选择添加（FR-PM02） -->
    <el-dialog v-model="addVisible" title="添加项目成员" width="460px" :close-on-click-modal="false">
      <div class="msub">只能添加本组织成员；已在项目内的成员不再列出（重复添加幂等）</div>
      <div v-loading="addLoading" class="cand-list">
        <div v-for="m in candidates" :key="m.userId" class="cand">
          <span class="u-avatar" :style="{ background: avatarColor(m.username) }">{{ m.username.slice(0, 1).toUpperCase() }}</span>
          <span class="cand-name">{{ m.username }}</span>
          <span class="cand-role">{{ m.role }}</span>
          <el-button type="primary" size="small" :loading="addingIds.includes(m.userId)" @click="doAdd(m)">加入项目</el-button>
        </div>
        <el-empty v-if="!addLoading && candidates.length === 0" description="组织内成员均已加入该项目" :image-size="60" />
      </div>
      <template #footer>
        <el-button @click="addVisible = false">关闭</el-button>
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

.hint {
  font-size: 12px;
  color: #646a73;
}

.sp {
  flex: 1;
}

.tbl {
  background: #fff;
  border-radius: 12px;
}

.u-cell,
.cand {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.cand {
  display: flex;
  width: 100%;
  padding: 9px 6px;
  border-bottom: 1px solid rgba(31, 35, 41, 0.05);
}

.cand-name {
  flex: 1;
  font-size: 13px;
}

.cand-role {
  font-size: 11px;
  color: #646a73;
  margin-right: 12px;
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

.role {
  font-size: 11px;
  font-weight: 700;
  padding: 2px 9px;
  border-radius: 8px;
}

.role-owner {
  background: rgba(255, 136, 0, 0.14);
  color: #ff8800;
}

.role-member {
  background: rgba(0, 181, 120, 0.12);
  color: #00b578;
}

.msub {
  font-size: 12px;
  color: #646a73;
  margin: -8px 0 12px;
}

.cand-list {
  max-height: 320px;
  overflow: auto;
  min-height: 80px;
}
</style>
