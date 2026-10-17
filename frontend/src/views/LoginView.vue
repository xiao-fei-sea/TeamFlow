<script setup>
import { reactive, ref, computed, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

/** T4.1 登录/注册页：校验口径与后端一致（PRD 6.3 校验总表） */

const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()

// mode: login | register（由路由 query 驱动，URL 可直接分享 /login?mode=register）
const mode = computed({
  get: () => (route.query.mode === 'register' ? 'register' : 'login'),
  set: (m) => {
    router.replace({ path: '/login', query: m === 'register' ? { mode: 'register' } : {} })
    nextTick(() => formRef.value?.clearValidate())
  },
})
const loading = ref(false)
const formRef = ref()

const form = reactive({
  account: '',
  username: '',
  email: '',
  password: '',
})

/** PRD 6.3 校验规则（与后端 RegisterRequest 正则完全一致） */
const rules = {
  account: [{ required: true, message: '请输入用户名或邮箱', trigger: 'blur' }],
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9_]{3,32}$/, message: '用户名须为 3-32 位字母、数字或下划线', trigger: 'blur' },
  ],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不正确', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { pattern: /^(?=.*[A-Za-z])(?=.*\d).{8,32}$/, message: '密码须为 8-32 位且同时包含字母和数字', trigger: 'blur' },
  ],
}

const isLogin = computed(() => mode.value === 'login')

async function submit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  loading.value = true
  try {
    if (isLogin.value) {
      await authStore.login(form.account.trim(), form.password)
      ElMessage.success('登录成功')
    } else {
      await authStore.register({
        username: form.username.trim(),
        email: form.email.trim(),
        password: form.password,
      })
      ElMessage.success('注册成功，已自动登录')
    }
    // 登录后恢复回跳地址；否则进入根路径解析组织上下文（无组织 → 引导创建）
    const redirect = route.query.redirect
    router.replace(redirect ? String(redirect) : '/')
  } catch (e) {
    /* 校验错误行内提示；接口错误已由 request 拦截器统一弹出 */
  } finally {
    loading.value = false
  }
}

function switchMode(m) {
  mode.value = m
}
</script>

<template>
  <div class="auth-wrap">
    <!-- 左侧品牌区（原型：auth-brand） -->
    <div class="auth-brand">
      <div>
        <div class="bf-logo">TF</div>
        <h2>{{ isLogin ? 'TeamFlow' : '创建账号' }}</h2>
        <p>{{ isLogin ? '组织即租户，权限纵深，任务全程可追溯的多团队协作平台。' : '注册后即可创建组织，邀请同事开始协作。' }}</p>
      </div>
      <ul>
        <li>多租户数据隔离，服务端五步校验</li>
        <li>看板拖拽流转，乐观锁防并发覆盖</li>
        <li>操作历史与审计日志（P1）</li>
      </ul>
      <div class="foot">© 2026 TeamFlow</div>
    </div>

    <!-- 右侧表单区 -->
    <div class="auth-form">
      <h3>{{ isLogin ? '欢迎回来' : '注册' }}</h3>
      <div class="sub">{{ isLogin ? '登录你的 TeamFlow 账号' : '所有字段均为必填' }}</div>

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" size="large" @keyup.enter="submit">
        <template v-if="isLogin">
          <el-form-item label="用户名 / 邮箱" prop="account">
            <el-input v-model="form.account" placeholder="用户名或邮箱" clearable />
          </el-form-item>
        </template>
        <template v-else>
          <el-form-item label="用户名" prop="username">
            <el-input v-model="form.username" placeholder="3-32 位字母、数字或下划线" clearable />
          </el-form-item>
          <el-form-item label="邮箱" prop="email">
            <el-input v-model="form.email" placeholder="name@example.com" clearable />
          </el-form-item>
        </template>
        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            :placeholder="isLogin ? '请输入密码' : '8-32 位，须含字母和数字'"
          />
        </el-form-item>
        <el-button type="primary" class="btn-block" size="large" :loading="loading" @click="submit">
          {{ isLogin ? '登 录' : '注 册' }}
        </el-button>
      </el-form>

      <div class="auth-switch">
        <template v-if="isLogin">还没有账号？<el-link type="primary" @click="switchMode('register')">立即注册</el-link></template>
        <template v-else>已有账号？<el-link type="primary" @click="switchMode('login')">直接登录</el-link></template>
      </div>
    </div>
  </div>
</template>

<style scoped>
.auth-wrap {
  display: grid;
  grid-template-columns: minmax(320px, 1fr) minmax(360px, 1fr);
  min-height: 100vh;
}

.auth-brand {
  background: linear-gradient(150deg, #2b5cf0, #3370ff 45%, #6b9bff);
  color: #fff;
  padding: 48px 40px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.bf-logo {
  width: 46px;
  height: 46px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.2);
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 800;
  font-size: 20px;
}

.auth-brand h2 {
  font-size: 26px;
  margin: 18px 0 10px;
}

.auth-brand p {
  opacity: 0.85;
  font-size: 14px;
  max-width: 320px;
}

.auth-brand ul {
  list-style: none;
  font-size: 13px;
  opacity: 0.9;
}

.auth-brand li {
  margin-top: 10px;
  display: flex;
  gap: 8px;
  align-items: center;
}

.auth-brand li::before {
  content: '';
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: #7dffb8;
  flex: none;
}

.foot {
  font-size: 12px;
  opacity: 0.6;
}

.auth-form {
  padding: 52px 46px;
  display: flex;
  flex-direction: column;
  justify-content: center;
  background: #fff;
}

.auth-form h3 {
  font-size: 22px;
  margin-bottom: 6px;
}

.auth-form .sub {
  color: #646a73;
  font-size: 13px;
  margin-bottom: 24px;
}

.btn-block {
  width: 100%;
  margin-top: 4px;
}

.auth-switch {
  margin-top: 18px;
  font-size: 13px;
  color: #646a73;
  text-align: center;
}

@media (max-width: 900px) {
  .auth-wrap {
    grid-template-columns: 1fr;
  }

  .auth-brand {
    display: none;
  }
}
</style>
