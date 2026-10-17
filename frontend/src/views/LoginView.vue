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
    <!-- 左侧品牌区：深松墨 + 任务流线（组织 → 项目 → 看板） -->
    <div class="auth-brand">
      <div class="brand-top">
        <div class="mark tf-num">TF</div>
        <span class="wordmark tf-num">TeamFlow</span>
      </div>

      <div class="brand-hero">
        <h2>让任务自然流动。</h2>
        <p>建组织、立项目、拖卡片——五分钟跑通一条协作流。</p>

        <!-- 流线图：产品核心对象的一次流转 -->
        <svg class="flow" viewBox="0 0 360 96" fill="none" aria-hidden="true">
          <path class="flow-line" d="M18 48 C 90 48, 96 16, 180 16 S 270 80, 342 80" />
          <circle class="flow-node" cx="18" cy="48" r="5" />
          <circle class="flow-node" cx="180" cy="16" r="5" />
          <circle class="flow-node end" cx="342" cy="80" r="5" />
          <text x="18" y="72">组织</text>
          <text x="180" y="40">项目</text>
          <text x="322" y="104" transform="translate(0,-4)">看板</text>
        </svg>
      </div>

      <div class="brand-foot">
        <ul>
          <li>多租户隔离，服务端五步校验</li>
          <li>看板乐观锁，并发拖拽不丢更新</li>
          <li>角色驱动权限，组织到项目两级纵深</li>
        </ul>
        <div class="foot">© 2026 TeamFlow</div>
      </div>
    </div>

    <!-- 右侧表单区：雾纸画布 -->
    <div class="auth-form">
      <div class="form-inner">
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
            {{ isLogin ? '登录' : '注册' }}
          </el-button>
        </el-form>

        <div class="auth-switch">
          <template v-if="isLogin">还没有账号？<el-link type="primary" @click="switchMode('register')">立即注册</el-link></template>
          <template v-else>已有账号？<el-link type="primary" @click="switchMode('login')">直接登录</el-link></template>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.auth-wrap {
  display: grid;
  grid-template-columns: minmax(420px, 1.05fr) minmax(360px, 1fr);
  min-height: 100vh;
}

/* ---------- 品牌区 ---------- */
.auth-brand {
  background: var(--tf-pine);
  color: #e9f1ef;
  padding: 44px 52px;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  position: relative;
  overflow: hidden;
}

.brand-top {
  display: flex;
  align-items: center;
  gap: 12px;
}

.mark {
  width: 40px;
  height: 40px;
  border-radius: 11px;
  background: var(--tf-teal);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  font-size: 17px;
}

.wordmark {
  font-weight: 700;
  font-size: 18px;
  letter-spacing: 0.01em;
}

.brand-hero {
  position: relative;
  z-index: 1;
}

.brand-hero h2 {
  font-size: 30px;
  font-weight: 650;
  letter-spacing: 0.01em;
  margin: 0 0 12px;
}

.brand-hero p {
  font-size: 14px;
  color: rgba(233, 241, 239, 0.66);
  margin: 0 0 34px;
}

/* 流线：一条从「组织」到「看板」的水线 */
.flow {
  width: min(360px, 100%);
  overflow: visible;
}

.flow-line {
  stroke: #3fa392;
  stroke-width: 2;
  stroke-linecap: round;
  stroke-dasharray: 480;
  stroke-dashoffset: 480;
  animation: flow-in 1.1s cubic-bezier(0.22, 0.7, 0.3, 1) 0.25s forwards;
}

.flow-node {
  fill: var(--tf-pine);
  stroke: #3fa392;
  stroke-width: 2;
  opacity: 0;
  animation: node-in 0.4s ease-out forwards;
}
.flow-node:nth-of-type(1) { animation-delay: 0.35s; }
.flow-node:nth-of-type(2) { animation-delay: 0.75s; }
.flow-node:nth-of-type(3) { animation-delay: 1.15s; }
.flow-node.end { fill: #3fa392; }

.flow text {
  fill: rgba(233, 241, 239, 0.6);
  font-size: 12px;
  font-family: var(--tf-font);
  opacity: 0;
  animation: node-in 0.4s ease-out forwards;
}
.flow text:nth-of-type(1) { animation-delay: 0.5s; }
.flow text:nth-of-type(2) { animation-delay: 0.9s; }
.flow text:nth-of-type(3) { animation-delay: 1.3s; }

@keyframes flow-in {
  to { stroke-dashoffset: 0; }
}
@keyframes node-in {
  to { opacity: 1; }
}

.brand-foot ul {
  list-style: none;
  margin: 0 0 22px;
  padding: 0;
  font-size: 13px;
  color: rgba(233, 241, 239, 0.72);
}

.brand-foot li {
  margin-top: 9px;
  display: flex;
  align-items: center;
  gap: 9px;
}

.brand-foot li::before {
  content: '';
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: #3fa392;
  flex: none;
}

.foot {
  font-size: 12px;
  color: rgba(233, 241, 239, 0.38);
}

/* ---------- 表单区 ---------- */
.auth-form {
  background: var(--tf-paper);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 48px 40px;
}

.form-inner {
  width: min(380px, 100%);
}

.auth-form h3 {
  font-size: 24px;
  font-weight: 650;
  margin: 0 0 6px;
}

.auth-form .sub {
  color: var(--tf-ink-3);
  font-size: 13px;
  margin-bottom: 26px;
}

.btn-block {
  width: 100%;
  margin-top: 4px;
}

.auth-switch {
  margin-top: 18px;
  font-size: 13px;
  color: var(--tf-ink-2);
  text-align: center;
}

@media (max-width: 900px) {
  .auth-wrap {
    grid-template-columns: 1fr;
  }

  .auth-brand {
    display: none;
  }

  .auth-form {
    min-height: 100vh;
  }
}
</style>
