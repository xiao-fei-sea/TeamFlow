package com.teamflow.project.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.teamflow.common.exception.BusinessException;
import com.teamflow.common.result.PageVO;
import com.teamflow.common.result.ResultCode;
import com.teamflow.organization.enums.OrgRole;
import com.teamflow.organization.guard.TenantGuard;
import com.teamflow.project.dto.ProjectCreateRequest;
import com.teamflow.project.dto.ProjectMemberVO;
import com.teamflow.project.dto.ProjectUpdateRequest;
import com.teamflow.project.dto.ProjectVO;
import com.teamflow.project.entity.Project;
import com.teamflow.project.entity.ProjectMember;
import com.teamflow.project.enums.ProjectRole;
import com.teamflow.project.mapper.ProjectMapper;
import com.teamflow.project.mapper.ProjectMemberMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 项目服务（T3.1/T3.2）。
 *
 * <p>权限矩阵（PRD 4.3）：创建/编辑/归档/恢复 = OWNER/ADMIN；
 * 列表与详情 = 组织成员（MEMBER 列表仅见参与项目、详情须为项目成员）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProjectService {

    private final TenantGuard tenantGuard;
    private final ProjectMapper projectMapper;
    private final ProjectMemberMapper projectMemberMapper;

    /** 创建项目：创建者自动 PROJECT_OWNER（PRD FR-P01）；名称组织内唯一（含已归档，物理键占用） */
    @Transactional
    public ProjectVO create(Long userId, Long orgId, ProjectCreateRequest request) {
        String myRole = tenantGuard.requireOrgMember(userId, orgId);
        tenantGuard.requireRole(orgId, userId, OrgRole.OWNER, OrgRole.ADMIN);
        checkNameUnique(orgId, request.name(), null);

        Project project = new Project();
        project.setOrganizationId(orgId);
        project.setName(request.name());
        project.setDescription(request.description());
        project.setStatus(1);
        project.setCreatedBy(userId);
        projectMapper.insert(project);

        ProjectMember member = new ProjectMember();
        member.setProjectId(project.getId());
        member.setUserId(userId);
        member.setProjectRole(ProjectRole.PROJECT_OWNER.name());
        projectMemberMapper.insert(member);

        log.info("项目创建成功: projectId={}, orgId={}, owner={}", project.getId(), orgId, userId);
        return toVO(project, myRole, ProjectRole.PROJECT_OWNER.name(), 1L, null, null);
    }

    /** 项目列表（T3.2）：OWNER/ADMIN 全量；MEMBER 仅见参与项目 */
    public PageVO<ProjectVO> list(Long userId, Long orgId, Integer page, Integer size, String keyword, Integer status) {
        String myRole = tenantGuard.requireOrgMember(userId, orgId);
        int[] ps = PageVO.sanitize(page, size);
        int st = (status != null && status == 0) ? 0 : 1;
        boolean onlyJoined = OrgRole.MEMBER.name().equals(myRole);
        IPage<ProjectVO> result = projectMapper.pageProjects(
                new Page<>(ps[0], ps[1]), orgId, userId, onlyJoined, keyword, st);
        List<ProjectVO> list = result.getRecords().stream()
                .map(vo -> new ProjectVO(vo.id(), vo.name(), vo.description(), vo.status(),
                        vo.taskTotal(), vo.taskDoing(), myRole, vo.projectRole(), null, vo.createdAt()))
                .toList();
        return PageVO.of(ps[0], ps[1], result.getTotal(), list);
    }

    /** 项目详情：项目成员 或 OWNER/ADMIN（PRD 4.3） */
    public ProjectVO getDetail(Long userId, Long orgId, Long projectId) {
        String myRole = tenantGuard.requireOrgMember(userId, orgId);
        tenantGuard.requireProjectInOrg(projectId, orgId);
        if (OrgRole.MEMBER.name().equals(myRole)) {
            tenantGuard.requireProjectMember(projectId, userId);
        }
        Project project = getById(projectId);
        String projectRole = memberRole(projectId, userId);
        return toVO(project, myRole, projectRole, projectMemberMapper.countMembers(projectId), null, null);
    }

    /** 编辑项目（名称/描述，字段可选） */
    @Transactional
    public ProjectVO update(Long userId, Long orgId, Long projectId, ProjectUpdateRequest request) {
        String myRole = tenantGuard.requireOrgMember(userId, orgId);
        tenantGuard.requireRole(orgId, userId, OrgRole.OWNER, OrgRole.ADMIN);
        tenantGuard.requireProjectInOrg(projectId, orgId);
        Project project = getById(projectId);
        if (request.name() != null) {
            if (request.name().isBlank()) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "项目名称不能为空");
            }
            checkNameUnique(orgId, request.name(), projectId);
            project.setName(request.name());
        }
        if (request.description() != null) {
            project.setDescription(request.description());
        }
        projectMapper.updateById(project);
        String projectRole = memberRole(projectId, userId);
        return toVO(project, myRole, projectRole, projectMemberMapper.countMembers(projectId), null, null);
    }

    /** 归档：只读封存（PRD FR-P04）；幂等，重复归档直接成功 */
    @Transactional
    public void archive(Long userId, Long orgId, Long projectId) {
        changeStatus(userId, orgId, projectId, 0);
    }

    /** 恢复：回到进行中；幂等 */
    @Transactional
    public void restore(Long userId, Long orgId, Long projectId) {
        changeStatus(userId, orgId, projectId, 1);
    }

    private void changeStatus(Long userId, Long orgId, Long projectId, int status) {
        tenantGuard.requireRole(orgId, userId, OrgRole.OWNER, OrgRole.ADMIN);
        tenantGuard.requireProjectInOrg(projectId, orgId);
        Project project = getById(projectId);
        project.setStatus(status);
        projectMapper.updateById(project);
    }

    /** 名称唯一（组织内含已归档；物理唯一键 uk_org_name 连已删除项目一并占用，故不加 deleted 条件） */
    private void checkNameUnique(Long orgId, String name, Long excludeProjectId) {
        Long count = projectMapper.countByName(orgId, name, excludeProjectId);
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "项目名称已存在");
        }
    }

    private Project getById(Long projectId) {
        Project project = projectMapper.selectById(projectId);
        if (project == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        return project;
    }

    private String memberRole(Long projectId, Long userId) {
        ProjectMemberVO mv = projectMemberMapper.findMemberVO(projectId, userId);
        return mv == null ? null : mv.projectRole();
    }

    private ProjectVO toVO(Project p, String myRole, String projectRole, Long memberCount, Long taskTotal, Long taskDoing) {
        return new ProjectVO(p.getId(), p.getName(), p.getDescription(), p.getStatus(),
                taskTotal, taskDoing, myRole, projectRole, memberCount, p.getCreatedAt());
    }
}
