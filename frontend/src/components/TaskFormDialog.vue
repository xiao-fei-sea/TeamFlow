<script setup>
import { ref, reactive, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import * as taskApi from '@/api/task'
import { parseDateTime, fmtDateTime } from '@/utils/format'

/** 任务创建/编辑表单弹窗（任务列表页与看板页共用，FR-T01/T02）。
 *  校验口径对照 PRD 6.3：标题 1-128 必填、描述 ≤2000、负责人限项目成员（服务端校验）、
 *  截止日期新建/修改时不得早于当前时间（编辑时保留原值不改则放行）
 */

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  /** 编辑时传入原任务；null 表示新建 */
  task: { type: Object, default: null },
  /** 项目成员列表（负责人下拉选项） */
  members: { type: Array, default: () => [] },
  orgId: { type: String, required: true },
  projectId: { type: String, required: true },
})

const emit = defineEmits(['update:modelValue', 'saved'])

const visible = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v),
})

const formRef = ref()
const saving = ref(false)
const form = reactive({ title: '', description: '', assigneeId: '', priority: 2, dueAt: null })

const rules = {
  title: [
    { required: true, message: '请输入任务标题', trigger: 'blur' },
    { min: 1, max: 128, message: '任务标题须为 1-128 字符', trigger: 'blur' },
  ],
  description: [{ max: 2000, message: '任务描述不能超过 2000 字符', trigger: 'blur' }],
  dueAt: [
    {
      validator: (rule, value, callback) => {
        if (!value) return callback()
        const d = value instanceof Date ? value : new Date(value)
        if (Number.isNaN(d.getTime())) return callback(new Error('时间格式不正确'))
        if (d.getTime() < Date.now() - 60 * 1000) {
          const isChanged = props.task
            ? parseDateTime(props.task.dueAt)?.getTime() !== d.getTime()
            : true
          if (!props.task || isChanged) return callback(new Error('截止日期不得早于当前时间'))
        }
        callback()
      },
      trigger: 'change',
    },
  ],
}

watch(visible, (v) => {
  if (v) {
    if (props.task) {
      form.title = props.task.title
      form.description = props.task.description || ''
      form.assigneeId = props.task.assignee?.id || ''
      form.priority = props.task.priority
      form.dueAt = props.task.dueAt ? parseDateTime(props.task.dueAt) : null
    } else {
      form.title = ''
      form.description = ''
      form.assigneeId = ''
      form.priority = 2
      form.dueAt = null
    }
  }
})

async function submit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    const payload = {
      title: form.title.trim(),
      description: form.description,
      assigneeId: form.assigneeId || null,
      priority: form.priority,
      dueAt: form.dueAt ? fmtDateTime(form.dueAt) : null,
    }
    if (props.task) {
      await taskApi.updateTask(props.orgId, props.projectId, props.task.id, payload)
      ElMessage.success('任务已更新')
    } else {
      await taskApi.createTask(props.orgId, props.projectId, payload)
      ElMessage.success('任务已创建，进入「待办」列')
    }
    visible.value = false
    emit('saved')
  } catch (e) {
    /* 错误提示已由 request 拦截器统一弹出 */
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <el-dialog v-model="visible" :title="task ? '编辑任务' : '新建任务'" width="500px" :close-on-click-modal="false">
    <div class="msub" v-if="!task">新任务默认进入「待办」列；负责人只能选择本项目成员（FR-T01）</div>
    <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="submit">
      <el-form-item label="标题" prop="title">
        <el-input v-model="form.title" maxlength="128" show-word-limit placeholder="1-128 字符" @keyup.enter="submit" />
      </el-form-item>
      <el-form-item label="描述" prop="description">
        <el-input v-model="form.description" type="textarea" :rows="3" maxlength="2000" show-word-limit placeholder="≤ 2000 字符（选填）" />
      </el-form-item>
      <el-row :gutter="12">
        <el-col :span="12">
          <el-form-item label="负责人" prop="assigneeId">
            <el-select v-model="form.assigneeId" placeholder="未分配" clearable style="width: 100%">
              <el-option v-for="m in members" :key="m.userId" :label="m.username" :value="m.userId" />
            </el-select>
          </el-form-item>
        </el-col>
        <el-col :span="12">
          <el-form-item label="优先级" prop="priority">
            <el-select v-model="form.priority" style="width: 100%">
              <el-option label="低" :value="1" />
              <el-option label="中" :value="2" />
              <el-option label="高" :value="3" />
              <el-option label="紧急" :value="4" />
            </el-select>
          </el-form-item>
        </el-col>
      </el-row>
      <el-form-item label="截止日期" prop="dueAt">
        <el-date-picker
          v-model="form.dueAt"
          type="datetime"
          placeholder="选择截止时间（选填）"
          format="YYYY-MM-DD HH:mm:ss"
          style="width: 100%"
        />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="visible = false">取消</el-button>
      <el-button type="primary" :loading="saving" @click="submit">{{ task ? '保存' : '创建' }}</el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.msub {
  font-size: 12px;
  color: #646a73;
  margin: -8px 0 12px;
}
</style>
