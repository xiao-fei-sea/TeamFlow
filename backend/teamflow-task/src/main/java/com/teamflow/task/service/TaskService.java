package com.teamflow.task.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.teamflow.common.exception.BusinessException;
import com.teamflow.common.result.PageVO;
import com.teamflow.common.result.ResultCode;
import com.teamflow.organization.enums.OrgRole;
import com.teamflow.organization.guard.TenantGuard;
import com.teamflow.task.dto.TaskCreateRequest;
import com.teamflow.task.dto.TaskStatusChangeRequest;
import com.teamflow.task.dto.TaskUpdateRequest;
import com.teamflow.task.dto.TaskVO;
import com.teamflow.task.entity.Task;
import com.teamflow.task.mapper.TaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 任务服务（T3.4/T3.5/T3.6，接口文档 3.6）。
 *
 * <p>权限矩阵（PRD 4.3）：创建/编辑/拖动 = 项目成员（含 OWNER/ADMIN）；
 * 删除 = 仅 OWNER/ADMIN（组织角色，MEMBER 40302）。
 * 五步校验：requireOrgMember → requireProjectInOrg →（MEMBER 须为项目成员）→ 归档封存禁写。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskService {

    /** 任务状态白名单（PRD 3.2 状态机，三状态自由流转） */
    private static final Set<String> TASK_STATUSES = Set.of("TODO", "IN_PROGRESS", "DONE");

    private static final Set<String> ORDER_BY_ALLOWED = Set.of("created_at", "due_at");

    private final TenantGuard tenantGuard;
    private final TaskMapper taskMapper;

    /** 创建任务：负责人限项目成员、dueAt 不得早于当前、organization_id 由 project 反查写入 */
    @Transactional
    public TaskVO create(Long userId, Long orgId, Long projectId, TaskCreateRequest request) {
        String myRole = requireProjectWritable(userId, orgId, projectId);
        TaskMapper.TaskProjectRow project = requireActiveProject(projectId);

        if (request.assigneeId() != null && countProjectMember(projectId, request.assigneeId()) == 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "负责人必须是项目成员");
        }
        if (request.dueAt() != null && request.dueAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "截止时间不得早于当前时间");
        }
        Task task = new Task();
        task.setOrganizationId(project.organizationId());
        task.setProjectId(projectId);
        task.setTitle(request.title());
        task.setDescription(request.description());
        task.setAssigneeId(request.assigneeId());
        task.setPriority(request.priority() == null ? 2 : request.priority());
        task.setStatus("TODO");
        task.setDueAt(request.dueAt());
        task.setVersion(0);
        task.setCreatedBy(userId);
        taskMapper.insert(task);
        log.info("任务创建: taskId={}, projectId={}, creator={}", task.getId(), projectId, userId);
        return toVO(taskMapper.findTaskRow(task.getId()));
    }

    /** 任务列表（筛选/排序/分页） */
    public PageVO<TaskVO> list(Long userId, Long orgId, Long projectId, Integer page, Integer size,
                               String keyword, String assigneeId, Integer priority, String status,
                               String orderBy, String orderDir) {
        requireProjectReadable(userId, orgId, projectId);
        int[] ps = PageVO.sanitize(page, size);
        Long assignee = parseAssignee(assigneeId);
        String ob = (orderBy != null && ORDER_BY_ALLOWED.contains(orderBy)) ? orderBy : "created_at";
        String od = "asc".equalsIgnoreCase(orderDir) ? "asc" : "desc";
        IPage<TaskMapper.TaskRow> result = taskMapper.pageTasks(
                new Page<>(ps[0], ps[1]), projectId, keyword, assignee, priority, status, ob, od);
        List<TaskVO> list = result.getRecords().stream().map(this::toVO).toList();
        return PageVO.of(ps[0], ps[1], result.getTotal(), list);
    }

    /** 任务详情：项目成员 或 OWNER/ADMIN */
    public TaskVO detail(Long userId, Long orgId, Long projectId, Long taskId) {
        requireProjectReadable(userId, orgId, projectId);
        requireTaskInProject(taskId, projectId);
        TaskMapper.TaskRow row = taskMapper.findTaskRow(taskId);
        if (row == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        return toVO(row);
    }

    /** 编辑任务：归档禁写；dueAt 仅在携带时校验（保留过去时间不改允许）；不校验 version（非拖拽路径） */
    @Transactional
    public TaskVO update(Long userId, Long orgId, Long projectId, Long taskId, TaskUpdateRequest request) {
        requireProjectWritable(userId, orgId, projectId);
        requireActiveProject(projectId);
        Task task = requireTaskInProject(taskId, projectId);
        if (request.title() != null) {
            if (request.title().isBlank()) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "任务标题不能为空");
            }
            task.setTitle(request.title());
        }
        if (request.description() != null) {
            task.setDescription(request.description());
        }
        if (request.assigneeId() != null) {
            if (request.assigneeId() > 0 && countProjectMember(projectId, request.assigneeId()) == 0) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "负责人必须是项目成员");
            }
            task.setAssigneeId(request.assigneeId() > 0 ? request.assigneeId() : null);
        }
        if (request.priority() != null) {
            task.setPriority(request.priority());
        }
        if (request.dueAt() != null) {
            if (request.dueAt().isBefore(LocalDateTime.now())) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "截止时间不得早于当前时间");
            }
            task.setDueAt(request.dueAt());
        }
        taskMapper.updateById(task);
        return toVO(taskMapper.findTaskRow(taskId));
    }

    /**
     * 状态流转（看板拖拽）：乐观锁提交，成功 version+1；version 不一致 40901（T3.5）。
     */
    @Transactional
    public TaskVO changeStatus(Long userId, Long orgId, Long projectId, Long taskId, TaskStatusChangeRequest request) {
        requireProjectWritable(userId, orgId, projectId);
        requireTaskInProject(taskId, projectId);
        String target = request.targetStatus();
        if (!TASK_STATUSES.contains(target)) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "目标状态仅允许 TODO/IN_PROGRESS/DONE");
        }
        TaskMapper.TaskProjectRow project = requireActiveProject(projectId);
        int updated = taskMapper.casStatus(taskId, project.organizationId(), target, request.version());
        if (updated == 0) {
            // 任务已被其他请求更新（version 不一致），前端以服务端数据回滚卡片
            throw new BusinessException(ResultCode.CONFLICT);
        }
        return toVO(taskMapper.findTaskRow(taskId));
    }

    /** 删除任务：仅 OWNER/ADMIN（组织角色）；归档封存禁写；软删除 */
    @Transactional
    public void delete(Long userId, Long orgId, Long projectId, Long taskId) {
        tenantGuard.requireRole(orgId, userId, OrgRole.OWNER, OrgRole.ADMIN);
        tenantGuard.requireProjectInOrg(projectId, orgId);
        requireActiveProject(projectId);
        Task task = requireTaskInProject(taskId, projectId);
        taskMapper.deleteById(task.getId());
        log.info("任务删除: taskId={}, actor={}", taskId, userId);
    }

    // ---------- 私有：五步校验与辅助 ----------

    /** 写操作前置：组织成员 → 项目归属 → MEMBER 须为项目成员（T3.4/T3.5 共用） */
    private String requireProjectWritable(Long userId, Long orgId, Long projectId) {
        String myRole = tenantGuard.requireOrgMember(userId, orgId);
        tenantGuard.requireProjectInOrg(projectId, orgId);
        if (OrgRole.MEMBER.name().equals(myRole)) {
            tenantGuard.requireProjectMember(projectId, userId);
        }
        return myRole;
    }

    /** 读操作前置（列表/详情）：项目成员 或 OWNER/ADMIN，与 ProjectMemberService.requireProjectReadable 同口径 */
    private void requireProjectReadable(Long userId, Long orgId, Long projectId) {
        requireProjectWritable(userId, orgId, projectId);
    }

    /** 项目必须存在且未归档（T3.6 归档封存：任务写操作全部 40302） */
    private TaskMapper.TaskProjectRow requireActiveProject(Long projectId) {
        TaskMapper.TaskProjectRow project = taskMapper.findProjectRow(projectId);
        if (project == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        if (project.status() == null || project.status() != 1) {
            throw new BusinessException(ResultCode.FORBIDDEN, "已归档项目禁止该操作");
        }
        return project;
    }

    /** 任务必须存在且归属 projectId（归属链 org → project → task，不符统一 40401） */
    private Task requireTaskInProject(Long taskId, Long projectId) {
        Task task = taskMapper.selectById(taskId);
        if (task == null || !task.getProjectId().equals(projectId)) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        return task;
    }

    /** assigneeId 解析：null 不过滤；"none" → -1（未分配）；非法值 40001 */
    private Long parseAssignee(String assigneeId) {
        if (assigneeId == null || assigneeId.isBlank()) {
            return null;
        }
        if ("none".equalsIgnoreCase(assigneeId)) {
            return -1L;
        }
        try {
            long v = Long.parseLong(assigneeId);
            if (v <= 0) {
                throw new NumberFormatException();
            }
            return v;
        } catch (NumberFormatException e) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "assigneeId 参数无效");
        }
    }

    private int countProjectMember(Long projectId, Long userId) {
        return tenantGuard.countProjectMember(projectId, userId);
    }

    private boolean overdue(String status, LocalDateTime dueAt) {
        return dueAt != null && !"DONE".equals(status) && dueAt.isBefore(LocalDateTime.now());
    }

    private TaskVO toVO(TaskMapper.TaskRow row) {
        if (row == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        TaskVO.UserRef assignee = row.assigneeId() == null ? null
                : new TaskVO.UserRef(row.assigneeId(), row.assigneeName());
        TaskVO.UserRef creator = row.creatorId() == null ? null
                : new TaskVO.UserRef(row.creatorId(), row.creatorName());
        return new TaskVO(row.id(), row.title(), row.status(), row.priority(), assignee,
                row.dueAt(), overdue(row.status(), row.dueAt()), row.version(), row.updatedAt(),
                row.description(), creator, row.createdAt());
    }
}
