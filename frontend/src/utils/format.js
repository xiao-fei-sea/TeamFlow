/** 展示工具：枚举文案、时间格式化、头像配色 */

/** 任务优先级（接口设计文档 2：1 低 / 2 中 / 3 高 / 4 紧急） */
export const PRIORITY_META = {
  1: { label: '低', tag: 'info' },
  2: { label: '中', tag: 'primary' },
  3: { label: '高', tag: 'warning' },
  4: { label: '紧急', tag: 'danger' },
}

/** 任务状态（taskStatus 枚举） */
export const TASK_STATUS_META = {
  TODO: { label: '待办' },
  IN_PROGRESS: { label: '进行中' },
  DONE: { label: '已完成' },
}

/** 项目状态：1 ACTIVE / 0 ARCHIVED */
export const PROJECT_STATUS = {
  1: { label: '进行中', type: 'success' },
  0: { label: '已归档', type: 'info' },
}

/** 组织角色展示名 */
export const ORG_ROLE_LABEL = {
  OWNER: 'OWNER',
  ADMIN: 'ADMIN',
  MEMBER: 'MEMBER',
}

/** LocalDateTime 字符串 → Date（东八区，与后端 yyyy-MM-dd HH:mm:ss 对齐） */
export function parseDateTime(str) {
  if (!str) return null
  return new Date(str.replace(/-/g, '/'))
}

/** Date → yyyy-MM-dd HH:mm:ss（提交后端用） */
export function fmtDateTime(date) {
  if (!date) return null
  const d = date instanceof Date ? date : new Date(date)
  const p = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`
}

/** 头像背景色（按用户名稳定取色；松墨体系的低饱和墨青系） */
const AVATAR_COLORS = ['#0f7b6c', '#8a5fbf', '#b98036', '#c2543f', '#3e6b8f', '#5b7553', '#a85b7e']
export function avatarColor(name = '') {
  let hash = 0
  for (let i = 0; i < name.length; i++) hash = (hash * 31 + name.charCodeAt(i)) >>> 0
  return AVATAR_COLORS[hash % AVATAR_COLORS.length]
}
