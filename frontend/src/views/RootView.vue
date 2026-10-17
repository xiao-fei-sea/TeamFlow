<script setup>
/** 根路径占位页：路由守卫会立即解析组织上下文并跳转（lastOrgId 优先 / 引导创建），正常不会停留 */
</script>

<template>
  <div class="root-loading">
    <div class="logo">TF</div>
    <p>正在进入工作台…</p>
  </div>
</template>

<style scoped>
.root-loading {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  color: #646a73;
  font-size: 13px;
}

.logo {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  background: linear-gradient(135deg, #3370ff, #6b9bff);
  color: #fff;
  font-weight: 800;
  font-size: 20px;
  display: flex;
  align-items: center;
  justify-content: center;
}
</style>
