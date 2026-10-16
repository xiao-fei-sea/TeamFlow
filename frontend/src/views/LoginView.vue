<script setup>
import { useRouter } from 'vue-router'

/** M1 交付真正的登录/注册页；当前为 401 跳转演示占位页 */
const router = useRouter()
</script>

<template>
  <div class="page">
    <el-card class="card" shadow="never">
      <h2>登录</h2>
      <p class="tip">登录 / 注册功能将在 M1（认证里程碑）交付。</p>
      <p class="tip">当前页面用于验证：未登录（40101）时全局统一跳转到登录页。</p>
      <el-button type="primary" @click="router.push('/')">返回首页</el-button>
    </el-card>
  </div>
</template>

<style scoped>
.page {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 100vh;
}

.card {
  width: 420px;
  text-align: center;
}

.tip {
  color: #909399;
  font-size: 13px;
  margin: 8px 0 16px;
}
</style>
