import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as authApi from '@/api/auth'
import { TOKEN_KEY } from '@/utils/request'

/** 登录态（T4.2）：token 持久化于 localStorage，用户信息来自 /users/me */
export const useAuthStore = defineStore('auth', () => {
  const token = ref(localStorage.getItem(TOKEN_KEY) || '')
  const user = ref(null)

  function setAuth(tokenValue, userInfo) {
    token.value = tokenValue
    if (tokenValue) localStorage.setItem(TOKEN_KEY, tokenValue)
    else localStorage.removeItem(TOKEN_KEY)
    user.value = userInfo || null
  }

  /** 登录（用户名/邮箱 + 密码），成功自动写入登录态 */
  async function login(account, password) {
    const data = await authApi.login({ account, password })
    setAuth(data.token, data.user)
    return data
  }

  /** 注册成功自动登录 */
  async function register(payload) {
    const data = await authApi.register(payload)
    setAuth(data.token, data.user)
    return data
  }

  /** 有 token 但用户信息缺失时拉取（刷新恢复） */
  async function fetchMe() {
    if (!token.value) return null
    if (!user.value) {
      user.value = await authApi.getMe()
    }
    return user.value
  }

  /** 退出：服务端黑名单 + 本地清理（接口失败也照常清理跳转） */
  async function logout() {
    try {
      await authApi.logout()
    } catch (e) {
      // 忽略：本地登录态无论如何都要清除
    }
    setAuth('', null)
  }

  return { token, user, login, register, logout, fetchMe, setAuth }
})
