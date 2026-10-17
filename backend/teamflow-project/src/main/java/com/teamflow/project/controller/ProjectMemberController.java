package com.teamflow.project.controller;

import com.teamflow.common.context.TenantContext;
import com.teamflow.common.result.Result;
import com.teamflow.project.dto.ProjectMemberVO;
import com.teamflow.project.service.ProjectMemberService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 项目成员接口（接口文档 3.5）。
 */
@RestController
@RequiredArgsConstructor
public class ProjectMemberController {

    private final ProjectMemberService projectMemberService;

    /** 项目成员列表（项目成员 或 OWNER/ADMIN） */
    @GetMapping("/api/organizations/{orgId}/projects/{projectId}/members")
    public Result<List<ProjectMemberVO>> list(@PathVariable Long orgId, @PathVariable Long projectId) {
        return Result.ok(projectMemberService.listMembers(TenantContext.getUserId(), orgId, projectId));
    }

    /** 添加项目成员（权限 OWNER/ADMIN；限本组织成员，重复添加幂等） */
    @PostMapping("/api/organizations/{orgId}/projects/{projectId}/members")
    public Result<ProjectMemberVO> add(@PathVariable Long orgId, @PathVariable Long projectId,
                                       @Valid @RequestBody AddMemberRequest request) {
        return Result.ok(projectMemberService.addMember(
                TenantContext.getUserId(), orgId, projectId, request.userId()));
    }

    /** 移除项目成员（权限 OWNER/ADMIN；PROJECT_OWNER 不可移除；其任务负责人置空） */
    @DeleteMapping("/api/organizations/{orgId}/projects/{projectId}/members/{userId}")
    public Result<Void> remove(@PathVariable Long orgId, @PathVariable Long projectId, @PathVariable Long userId) {
        projectMemberService.removeMember(TenantContext.getUserId(), orgId, projectId, userId);
        return Result.ok();
    }

    public record AddMemberRequest(Long userId) {
    }
}
