package com.teamflow.project.service;

import com.teamflow.common.exception.BusinessException;
import com.teamflow.common.result.ResultCode;
import com.teamflow.organization.enums.OrgRole;
import com.teamflow.organization.guard.TenantGuard;
import com.teamflow.project.dto.ProjectMemberVO;
import com.teamflow.project.entity.ProjectMember;
import com.teamflow.project.enums.ProjectRole;
import com.teamflow.project.mapper.ProjectMemberMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 项目成员服务（T3.3，接口文档 3.5）。
 *
 * <p>权限矩阵（PRD 4.3）：列表 = 项目成员或 OWNER/ADMIN；添加/移除 = OWNER/ADMIN。
 * PROJECT_OWNER 不可移除（FR-PM03，P0 负责人随项目创建固定）；被移除者项目内任务负责人置空。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectMemberService {

    private final TenantGuard tenantGuard;
    private final ProjectMemberMapper projectMemberMapper;

    /** 项目成员列表：项目成员 或 OWNER/ADMIN */
    public List<ProjectMemberVO> listMembers(Long userId, Long orgId, Long projectId) {
        requireProjectReadable(userId, orgId, projectId);
        return projectMemberMapper.listMembers(projectId);
    }

    /** 添加项目成员：限本组织有效成员；重复添加幂等返回当前关系 */
    @Transactional
    public ProjectMemberVO addMember(Long userId, Long orgId, Long projectId, Long targetUserId) {
        tenantGuard.requireRole(orgId, userId, OrgRole.OWNER, OrgRole.ADMIN);
        tenantGuard.requireProjectInOrg(projectId, orgId);
        if (targetUserId == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "userId 不能为空");
        }
        if (projectMemberMapper.countOrgMemberValid(orgId, targetUserId) == 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "目标用户不是本组织有效成员");
        }
        ProjectMemberVO existing = projectMemberMapper.findMemberVO(projectId, targetUserId);
        if (existing != null) {
            // 幂等：已是项目成员时返回当前关系，不重复创建
            return existing;
        }
        ProjectMember record = new ProjectMember();
        record.setProjectId(projectId);
        record.setUserId(targetUserId);
        record.setProjectRole(ProjectRole.PROJECT_MEMBER.name());
        projectMemberMapper.insert(record);
        log.info("项目成员添加: projectId={}, userId={}, actor={}", projectId, targetUserId, userId);
        return projectMemberMapper.findMemberVO(projectId, targetUserId);
    }

    /** 移除项目成员：单事务（删除关系 + 其项目内任务负责人置空）；PROJECT_OWNER 不可移除 */
    @Transactional
    public void removeMember(Long userId, Long orgId, Long projectId, Long targetUserId) {
        tenantGuard.requireRole(orgId, userId, OrgRole.OWNER, OrgRole.ADMIN);
        tenantGuard.requireProjectInOrg(projectId, orgId);
        ProjectMemberVO target = projectMemberMapper.findMemberVO(projectId, targetUserId);
        if (target == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        if (ProjectRole.PROJECT_OWNER.name().equals(target.projectRole())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "项目负责人不可移除");
        }
        projectMemberMapper.removeMember(projectId, targetUserId);
        projectMemberMapper.clearAssignee(projectId, targetUserId);
        log.info("项目成员移除: projectId={}, userId={}, actor={}", projectId, targetUserId, userId);
    }

    /** 详情/成员列表等读操作：OWNER/ADMIN 跳过第 5 步，MEMBER 必须为项目成员 */
    public void requireProjectReadable(Long userId, Long orgId, Long projectId) {
        String myRole = tenantGuard.requireOrgMember(userId, orgId);
        tenantGuard.requireProjectInOrg(projectId, orgId);
        if (OrgRole.MEMBER.name().equals(myRole)) {
            tenantGuard.requireProjectMember(projectId, userId);
        }
    }
}
