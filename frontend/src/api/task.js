import request from '@/utils/request'

/** 任务模块（接口设计文档 3.6） */

/**
 * 任务列表。
 * params: page/size/keyword/assigneeId(none=未分配)/priority/status/orderBy(created_at|due_at)/orderDir(asc|desc)
 */
export function listTasks(orgId, projectId, params) {
  return request.get(`/organizations/${orgId}/projects/${projectId}/tasks`, { params })
}

/** 创建任务：负责人限项目成员；dueAt 新建时不得早于当前时间；默认 status=TODO、priority=2 */
export function createTask(orgId, projectId, data) {
  return request.post(`/organizations/${orgId}/projects/${projectId}/tasks`, data)
}

/** 任务详情（含 description、creator、createdAt） */
export function getTask(orgId, projectId, taskId) {
  return request.get(`/organizations/${orgId}/projects/${projectId}/tasks/${taskId}`)
}

/** 编辑任务（字段可选）；响应含最新 version */
export function updateTask(orgId, projectId, taskId, data) {
  return request.put(`/organizations/${orgId}/projects/${projectId}/tasks/${taskId}`, data)
}

/** 状态流转（看板拖拽，乐观锁）：{ targetStatus, version }；冲突 40901 返回服务端最新数据 */
export function updateTaskStatus(orgId, projectId, taskId, data) {
  return request.patch(`/organizations/${orgId}/projects/${projectId}/tasks/${taskId}/status`, data)
}

/** 删除任务（OWNER/ADMIN），软删除 */
export function deleteTask(orgId, projectId, taskId) {
  return request.delete(`/organizations/${orgId}/projects/${projectId}/tasks/${taskId}`)
}
