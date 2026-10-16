<script setup>
import { ref } from 'vue'
import { ping, echo, demo } from '@/api/ping'

/** M0 联调演示页（T0.6）：验证 前端 → 代理 → 后端 全链路 + 统一错误行为 */

const loading = ref('')
const output = ref('// 点击左侧按钮发起请求，此处展示响应结果')

function show(label, value) {
  output.value = `// ${label}\n` + JSON.stringify(value, null, 2)
}

async function run(key, label, fn) {
  loading.value = key
  try {
    const data = await fn()
    show(`${label} — 成功（code=0，拦截器已解包 data）`, data)
  } catch (err) {
    // 业务错误：拦截器已弹统一提示，这里展示完整 Result
    show(`${label} — 业务错误（拦截器已按错误码统一提示）`, err)
  } finally {
    loading.value = ''
  }
}

const actions = [
  { key: 'ping', label: '健康检查 /ping', type: 'success', fn: () => ping() },
  {
    key: 'ok-echo',
    label: '参数校验通过 /echo',
    type: '',
    fn: () => echo({ message: 'hello TeamFlow' }),
  },
  {
    key: 'bad-echo',
    label: '模拟 40001（message 为空）',
    type: 'warning',
    fn: () => echo({ message: '' }),
  },
  {
    key: 'boom',
    label: '模拟 50000（服务端未知异常）',
    type: 'danger',
    fn: () => echo({ message: 'boom' }, { boom: true }),
  },
  { key: '401', label: '模拟 40101（跳转登录页）', type: 'danger', fn: () => demo('unauthorized') },
  { key: '403', label: '模拟 40301（无权限提示）', type: 'warning', fn: () => demo('forbidden') },
  { key: '404', label: '模拟 40401（资源不存在）', type: 'warning', fn: () => demo('not-found') },
]
</script>

<template>
  <div class="page">
    <el-card class="card" shadow="never">
      <template #header>
        <div class="header">
          <span class="title">TeamFlow — M0 地基联调验证</span>
          <el-tag type="info" effect="plain">前端 → Vite 代理 → Spring Boot → MySQL / Redis</el-tag>
        </div>
      </template>

      <div class="actions">
        <el-button
          v-for="a in actions"
          :key="a.key"
          :type="a.type"
          :loading="loading === a.key"
          @click="run(a.key, a.label, a.fn)"
        >
          {{ a.label }}
        </el-button>
      </div>

      <el-input
        v-model="output"
        type="textarea"
        :rows="16"
        readonly
        class="output"
        resize="none"
      />
    </el-card>
  </div>
</template>

<style scoped>
.page {
  max-width: 860px;
  margin: 40px auto;
  padding: 0 16px;
}

.header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.title {
  font-weight: 600;
  font-size: 16px;
}

.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 16px;
}

.output :deep(textarea) {
  font-family: Consolas, Monaco, 'Courier New', monospace;
  font-size: 12px;
  line-height: 1.6;
}
</style>
