import request from '@/utils/request'

/** 项目与项目成员模块（接口设计文档 3.4 / 3.5） */

/** 项目列表：MEMBER 服务端自动过滤为「我参与的项目」；status 1 进行中 / 0 已归档 */
export function listProjects(orgId, params) {
  return request.get(`/organizations/${orgId}/projects`, { params })
}

/** 创建项目（OWNER/ADMIN）：创建者自动成为 PROJECT_OWNER */
export function createProject(orgId, data) {
  return request.post(`/organizations/${orgId}/projects`, data)
}

/** 项目详情：{ id, name, description, status, myRole, projectRole, memberCount, createdAt } */
export function getProject(orgId, projectId) {
  return request.get(`/organizations/${orgId}/projects/${projectId}`)
}

/** 编辑项目（OWNER/ADMIN）：{ name?, description? } */
export function updateProject(orgId, projectId, data) {
  return request.put(`/organizations/${orgId}/projects/${projectId}`, data)
}

/** 归档项目（只读封存） */
export function archiveProject(orgId, projectId) {
  return request.post(`/organizations/${orgId}/projects/${projectId}/archive`)
}

/** 恢复项目 */
export function restoreProject(orgId, projectId) {
  return request.post(`/organizations/${orgId}/projects/${projectId}/restore`)
}

/** 项目成员列表：[{ userId, username, projectRole, joinedAt }] */
export function listProjectMembers(orgId, projectId) {
  return request.get(`/organizations/${orgId}/projects/${projectId}/members`)
}

/** 添加项目成员（必须是本组织成员），幂等 */
export function addProjectMember(orgId, projectId, userId) {
  return request.post(`/organizations/${orgId}/projects/${projectId}/members`, { userId })
}

/** 移除项目成员（PROJECT_OWNER 不可移除；其任务 assignee 置空） */
export function removeProjectMember(orgId, projectId, userId) {
  return request.delete(`/organizations/${orgId}/projects/${projectId}/members/${userId}`)
}
