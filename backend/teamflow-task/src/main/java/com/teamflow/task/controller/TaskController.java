package com.teamflow.task.controller;

import com.teamflow.common.context.TenantContext;
import com.teamflow.common.result.PageVO;
import com.teamflow.common.result.Result;
import com.teamflow.task.dto.TaskCreateRequest;
import com.teamflow.task.dto.TaskStatusChangeRequest;
import com.teamflow.task.dto.TaskUpdateRequest;
import com.teamflow.task.dto.TaskVO;
import com.teamflow.task.service.TaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 任务接口（接口文档 3.6）。
 */
@RestController
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    /** 创建任务（权限：项目成员，含 OWNER/ADMIN） */
    @PostMapping("/api/organizations/{orgId}/projects/{projectId}/tasks")
    public Result<TaskVO> create(@PathVariable Long orgId, @PathVariable Long projectId,
                                 @Valid @RequestBody TaskCreateRequest request) {
        return Result.ok(taskService.create(TenantContext.getUserId(), orgId, projectId, request));
    }

    /** 任务列表（筛选/排序/分页；assigneeId=none 表示未分配） */
    @GetMapping("/api/organizations/{orgId}/projects/{projectId}/tasks")
    public Result<PageVO<TaskVO>> list(@PathVariable Long orgId, @PathVariable Long projectId,
                                       @RequestParam(required = false) Integer page,
                                       @RequestParam(required = false) Integer size,
                                       @RequestParam(required = false) String keyword,
                                       @RequestParam(required = false) String assigneeId,
                                       @RequestParam(required = false) Integer priority,
                                       @RequestParam(required = false) String status,
                                       @RequestParam(required = false) String orderBy,
                                       @RequestParam(required = false) String orderDir) {
        return Result.ok(taskService.list(TenantContext.getUserId(), orgId, projectId,
                page, size, keyword, assigneeId, priority, status, orderBy, orderDir));
    }

    /** 任务详情（项目成员 或 OWNER/ADMIN） */
    @GetMapping("/api/organizations/{orgId}/projects/{projectId}/tasks/{taskId}")
    public Result<TaskVO> detail(@PathVariable Long orgId, @PathVariable Long projectId, @PathVariable Long taskId) {
        return Result.ok(taskService.detail(TenantContext.getUserId(), orgId, projectId, taskId));
    }

    /** 编辑任务（权限：项目成员；归档项目拒绝） */
    @PutMapping("/api/organizations/{orgId}/projects/{projectId}/tasks/{taskId}")
    public Result<TaskVO> update(@PathVariable Long orgId, @PathVariable Long projectId, @PathVariable Long taskId,
                                 @Valid @RequestBody TaskUpdateRequest request) {
        return Result.ok(taskService.update(TenantContext.getUserId(), orgId, projectId, taskId, request));
    }

    /** 状态流转（看板拖拽：targetStatus + version 乐观锁，冲突 40901） */
    @PatchMapping("/api/organizations/{orgId}/projects/{projectId}/tasks/{taskId}/status")
    public Result<TaskVO> changeStatus(@PathVariable Long orgId, @PathVariable Long projectId,
                                       @PathVariable Long taskId,
                                       @Valid @RequestBody TaskStatusChangeRequest request) {
        return Result.ok(taskService.changeStatus(TenantContext.getUserId(), orgId, projectId, taskId, request));
    }

    /** 删除任务（权限 OWNER/ADMIN；软删除） */
    @DeleteMapping("/api/organizations/{orgId}/projects/{projectId}/tasks/{taskId}")
    public Result<Void> delete(@PathVariable Long orgId, @PathVariable Long projectId, @PathVariable Long taskId) {
        taskService.delete(TenantContext.getUserId(), orgId, projectId, taskId);
        return Result.ok();
    }
}
