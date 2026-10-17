import request from '@/utils/request'

/** 认证模块（接口设计文档 3.1） */

/** 注册成功自动登录：data = { token, expiresIn, user } */
export function register(data) {
  return request.post('/auth/register', data)
}

/** account 支持用户名或邮箱：data = { token, expiresIn, user } */
export function login(data) {
  return request.post('/auth/login', data)
}

/** 退出登录：JWT 加入 Redis 黑名单 */
export function logout() {
  return request.post('/auth/logout')
}

/** 当前用户信息：{ id, username, email } */
export function getMe() {
  return request.get('/users/me')
}
