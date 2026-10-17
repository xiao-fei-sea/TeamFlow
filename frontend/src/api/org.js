import request from '@/utils/request'

/** 组织与组织成员模块（接口设计文档 3.2 / 3.3） */

/** 创建组织：创建者自动成为 OWNER，单用户上限 5 个 */
export function createOrg(data) {
  return request.post('/organizations', data)
}

/** 我所属的组织列表：[{ id, name, myRole, memberCount }] */
export function listMyOrgs() {
  return request.get('/organizations')
}

/** 组织详情：{ id, name, ownerId, myRole, memberCount, projectCount, createdAt } */
export function getOrg(orgId) {
  return request.get(`/organizations/${orgId}`)
}

/** 编辑组织名称（OWNER/ADMIN） */
export function updateOrg(orgId, data) {
  return request.put(`/organizations/${orgId}`, data)
}

/** 删除（停用）组织（OWNER 专属，confirmName 二次确认） */
export function deleteOrg(orgId, confirmName) {
  return request.delete(`/organizations/${orgId}`, { params: { confirmName } })
}

/** 成员列表（分页 + keyword 用户名/邮箱模糊） */
export function listMembers(orgId, params) {
  return request.get(`/organizations/${orgId}/members`, { params })
}

/** 添加成员：{ userId, role: ADMIN|MEMBER }，幂等 */
export function addMember(orgId, data) {
  return request.post(`/organizations/${orgId}/members`, data)
}

/** 变更成员角色（仅 OWNER）：{ role } */
export function changeMemberRole(orgId, userId, role) {
  return request.put(`/organizations/${orgId}/members/${userId}/role`, { role })
}

/** 移除成员（OWNER 任意 / ADMIN 仅 MEMBER；级联移出项目、任务负责人置空） */
export function removeMember(orgId, userId) {
  return request.delete(`/organizations/${orgId}/members/${userId}`)
}

/** 搜索用户（添加成员用，OWNER/ADMIN）：{ list: [{ id, username, email(脱敏), joined }] } */
export function searchUsers(orgId, keyword) {
  return request.get('/users/search', { params: { orgId, keyword } })
}
