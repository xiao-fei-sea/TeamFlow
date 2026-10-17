<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useOrgStore } from '@/stores/org'

/** 无组织用户引导创建组织（PRD FR-A02：无组织则引导创建） */

const router = useRouter()
const orgStore = useOrgStore()

const loading = ref(false)
const formRef = ref()
const form = reactive({ name: '' })
const rules = [
  { required: true, message: '请输入组织名称', trigger: 'blur' },
  { min: 1, max: 64, message: '组织名称须为 1-64 字符', trigger: 'blur' },
]

onMounted(async () => {
  // 已有组织时不停留在引导页
  try {
    const orgs = await orgStore.loadOrgs()
    if (orgs.length > 0) {
      router.replace(`/org/${orgStore.resolveOrg(orgs)}/projects`)
    }
  } catch (e) {
    /* 401 由拦截器统一跳登录 */
  }
})

async function submit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    await orgStore.createOrg(form.name.trim())
    ElMessage.success('组织创建成功')
  } catch (e) {
    /* 错误提示已由 request 拦截器统一弹出 */
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="welcome">
    <div class="panel">
      <div class="logo">TF</div>
      <h2>创建你的第一个组织</h2>
      <p class="tip">组织是数据隔离的边界，创建后你将自动成为组织 OWNER，可以邀请同事、创建项目并分配任务。</p>

      <el-form ref="formRef" :model="form" :rules="rules" @submit.prevent="submit">
        <el-form-item prop="name">
          <el-input v-model="form.name" placeholder="组织名称（1-64 字符）" maxlength="64" size="large" @keyup.enter="submit" />
        </el-form-item>
        <el-button type="primary" size="large" class="w-full" :loading="loading" @click="submit">创建组织</el-button>
      </el-form>
    </div>
  </div>
</template>

<style scoped>
.welcome {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
}

.panel {
  width: 420px;
  background: #fff;
  border: 1px solid rgba(51, 112, 255, 0.14);
  border-radius: 12px;
  padding: 40px 36px;
  box-shadow: 0 6px 24px rgba(51, 112, 255, 0.08);
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
  margin-bottom: 16px;
}

h2 {
  font-size: 20px;
  margin-bottom: 8px;
}

.tip {
  color: #646a73;
  font-size: 13px;
  margin-bottom: 20px;
}

.w-full {
  width: 100%;
}
</style>
