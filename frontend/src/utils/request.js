import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'

export const TOKEN_KEY = 'tf_token'

/**
 * M0 前端统一请求层（T0.5）：
 * - 请求拦截器：注入 JWT（Authorization: Bearer <token>）
 * - 响应拦截器：解包 Result（code=0 返回 data）；按错误码统一提示；401 跳登录
 */

function getToken() {
  return localStorage.getItem(TOKEN_KEY)
}

/** 401 统一处理：清除登录态并跳转登录页（携带回跳地址） */
function handleUnauthorized() {
  localStorage.removeItem(TOKEN_KEY)
  const current = router.currentRoute.value
  if (current.path !== '/login') {
    router.push({ path: '/login', query: { redirect: current.fullPath } })
  }
}

// 错误码 → 统一提示文案（PRD 6.4 / 接口设计文档 1.3）
const ERROR_MESSAGES = {
  40301: '无权限执行此操作',
  40302: '无权限执行此操作',
  40401: '资源不存在',
  40901: '任务已被他人更新，请刷新后重试',
  42901: '请求过于频繁，请稍后重试',
  50000: '服务异常，请稍后重试',
}

const request = axios.create({
  baseURL: '/api',
  timeout: 15000,
})

request.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

request.interceptors.response.use(
  (response) => {
    const res = response.data
    // 非标准 Result 结构（如文件流）直接透传
    if (res === null || typeof res !== 'object' || !('code' in res)) {
      return res
    }
    if (res.code === 0) {
      return res.data // 解包：调用方直接拿到 data
    }
    if (res.code === 40101) {
      // 未登录 / Token 失效 → 统一跳登录
      handleUnauthorized()
    } else if (res.code === 40001) {
      // 参数校验失败：逐字段提示（data.errors: [{field, message}]）
      const errors = res.data?.errors || []
      const detail = errors.map((e) => e.message).join('；')
      ElMessage.error(detail || res.message || '参数校验失败')
    } else {
      ElMessage.error(ERROR_MESSAGES[res.code] ?? res.message ?? '请求失败')
    }
    return Promise.reject(res)
  },
  (error) => {
    // HTTP 层错误（M1 起安全过滤链会返回 HTTP 401）
    if (error.response?.status === 401) {
      handleUnauthorized()
    } else if (error.code === 'ECONNABORTED') {
      ElMessage.error('请求超时，请稍后重试')
    } else {
      ElMessage.error('网络异常，请稍后重试')
    }
    return Promise.reject(error)
  }
)

export default request
