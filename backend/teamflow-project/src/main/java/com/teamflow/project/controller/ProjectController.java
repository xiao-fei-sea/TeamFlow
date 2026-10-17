package com.teamflow.project.controller;

import com.teamflow.common.context.TenantContext;
import com.teamflow.common.result.PageVO;
import com.teamflow.common.result.Result;
import com.teamflow.project.dto.ProjectCreateRequest;
import com.teamflow.project.dto.ProjectUpdateRequest;
import com.teamflow.project.dto.ProjectVO;
import com.teamflow.project.service.ProjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 项目接口（接口文档 3.4）。
 */
@RestController
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    /** 创建项目（权限 OWNER/ADMIN） */
    @PostMapping("/api/organizations/{orgId}/projects")
    public Result<ProjectVO> create(@PathVariable Long orgId, @Valid @RequestBody ProjectCreateRequest request) {
        return Result.ok(projectService.create(TenantContext.getUserId(), orgId, request));
    }

    /** 项目列表（OWNER/ADMIN 全量；MEMBER 仅见参与项目） */
    @GetMapping("/api/organizations/{orgId}/projects")
    public Result<PageVO<ProjectVO>> list(@PathVariable Long orgId,
                                          @RequestParam(required = false) Integer page,
                                          @RequestParam(required = false) Integer size,
                                          @RequestParam(required = false) String keyword,
                                          @RequestParam(required = false) Integer status) {
        return Result.ok(projectService.list(TenantContext.getUserId(), orgId, page, size, keyword, status));
    }

    /** 项目详情（项目成员 或 OWNER/ADMIN） */
    @GetMapping("/api/organizations/{orgId}/projects/{projectId}")
    public Result<ProjectVO> detail(@PathVariable Long orgId, @PathVariable Long projectId) {
        return Result.ok(projectService.getDetail(TenantContext.getUserId(), orgId, projectId));
    }

    /** 编辑项目（权限 OWNER/ADMIN） */
    @PutMapping("/api/organizations/{orgId}/projects/{projectId}")
    public Result<ProjectVO> update(@PathVariable Long orgId, @PathVariable Long projectId,
                                    @Valid @RequestBody ProjectUpdateRequest request) {
        return Result.ok(projectService.update(TenantContext.getUserId(), orgId, projectId, request));
    }

    /** 归档项目（权限 OWNER/ADMIN） */
    @PostMapping("/api/organizations/{orgId}/projects/{projectId}/archive")
    public Result<Void> archive(@PathVariable Long orgId, @PathVariable Long projectId) {
        projectService.archive(TenantContext.getUserId(), orgId, projectId);
        return Result.ok();
    }

    /** 恢复项目（权限 OWNER/ADMIN） */
    @PostMapping("/api/organizations/{orgId}/projects/{projectId}/restore")
    public Result<Void> restore(@PathVariable Long orgId, @PathVariable Long projectId) {
        projectService.restore(TenantContext.getUserId(), orgId, projectId);
        return Result.ok();
    }
}
