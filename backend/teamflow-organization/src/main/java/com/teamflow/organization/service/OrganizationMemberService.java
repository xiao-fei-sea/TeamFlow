package com.teamflow.organization.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.teamflow.common.exception.BusinessException;
import com.teamflow.common.result.PageVO;
import com.teamflow.common.result.ResultCode;
import com.teamflow.organization.dto.MemberAddRequest;
import com.teamflow.organization.dto.MemberVO;
import com.teamflow.organization.entity.OrganizationMember;
import com.teamflow.organization.enums.OrgRole;
import com.teamflow.organization.guard.TenantGuard;
import com.teamflow.organization.mapper.OrganizationMemberMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 组织成员服务（T2.4 成员管理 + T2.5 移除级联）。
 *
 * <p>权限矩阵（PRD 4.3 全局唯一权威）：成员列表=组织成员；添加=OWNER/ADMIN；
 * 变更角色=仅 OWNER；移除=OWNER 任意 / ADMIN 仅 MEMBER；任何情形不可移 OWNER、不可移自己。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrganizationMemberService {

    private final OrganizationMemberMapper organizationMemberMapper;
    private final TenantGuard tenantGuard;

    /** 成员列表（分页 + keyword 模糊），权限：组织成员 */
    public PageVO<MemberVO> pageMembers(Long userId, Long orgId, Integer page, Integer size, String keyword) {
        tenantGuard.requireOrgMember(userId, orgId);
        int[] ps = PageVO.sanitize(page, size);
        Page<MemberVO> result = organizationMemberMapper.pageMembers(new Page<>(ps[0], ps[1]), orgId, keyword);
        return PageVO.of(ps[0], ps[1], result.getTotal(), result.getRecords());
    }

    /** 添加成员（OWNER/ADMIN）。幂等：已是有效成员时直接返回当前关系 */
    @Transactional
    public MemberVO addMember(Long userId, Long orgId, MemberAddRequest request) {
        tenantGuard.requireRole(orgId, userId, OrgRole.OWNER, OrgRole.ADMIN);

        Long targetId = request.userId();
        String role = request.role() == null ? OrgRole.MEMBER.name() : request.role();
        if (OrgRole.OWNER.name().equals(role)) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "不能直接添加 OWNER，角色仅允许 ADMIN 或 MEMBER");
        }
        if (organizationMemberMapper.countActiveUser(targetId) == 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "用户不存在或已被禁用");
        }

        MemberVO existing = organizationMemberMapper.findMemberVO(orgId, targetId);
        if (existing != null) {
            // 幂等：返回当前关系，不重复创建
            return existing;
        }

        OrganizationMember record = organizationMemberMapper.selectOne(new LambdaQueryWrapper<OrganizationMember>()
                .eq(OrganizationMember::getOrganizationId, orgId)
                .eq(OrganizationMember::getUserId, targetId));
        if (record != null) {
            // 曾被移除：同一唯一键记录重新激活并覆盖角色
            record.setStatus(1);
            record.setRole(role);
            organizationMemberMapper.updateById(record);
        } else {
            record = new OrganizationMember();
            record.setOrganizationId(orgId);
            record.setUserId(targetId);
            record.setRole(role);
            record.setStatus(1);
            organizationMemberMapper.insert(record);
        }
        tenantGuard.evictOrgMember(orgId, targetId);
        log.info("成员添加: orgId={}, target={}, role={}, operator={}", orgId, targetId, role, userId);
        return organizationMemberMapper.findMemberVO(orgId, targetId);
    }

    /** 变更成员角色（仅 OWNER，不可变更自己，T2.4） */
    public void changeRole(Long userId, Long orgId, Long targetUserId, String role) {
        tenantGuard.requireRole(orgId, userId, OrgRole.OWNER);
        if (userId.equals(targetUserId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "不能变更自己的角色");
        }
        OrganizationMember target = requireActiveMember(orgId, targetUserId);
        target.setRole(role);
        organizationMemberMapper.updateById(target);
        tenantGuard.evictOrgMember(orgId, targetUserId);
        log.info("成员角色变更: orgId={}, target={}, role={}, operator={}", orgId, targetUserId, role, userId);
    }

    /**
     * 移除成员（T2.4 权限矩阵 + T2.5 同事务级联）：
     * organization_member.status=0 → 移出全部 project_member → 被分配任务 assignee_id 置 NULL。
     * （审计记录随 T5.2 @AuditLog 落地）
     */
    @Transactional
    public void removeMember(Long userId, Long orgId, Long targetUserId) {
        String operatorRole = tenantGuard.requireOrgMember(userId, orgId);
        if (userId.equals(targetUserId)) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "不能移除自己");
        }
        OrganizationMember target = requireActiveMember(orgId, targetUserId);

        String targetRole = target.getRole();
        if (OrgRole.OWNER.name().equals(targetRole)) {
            // 到达此处操作者必为 ADMIN（OWNER 移 OWNER 即移自己，已被拦截）→ 权限不足
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
        if (OrgRole.ADMIN.name().equals(operatorRole) && OrgRole.ADMIN.name().equals(targetRole)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }

        target.setStatus(0);
        organizationMemberMapper.updateById(target);
        organizationMemberMapper.removeFromProjects(orgId, targetUserId);
        organizationMemberMapper.clearAssignee(orgId, targetUserId);
        tenantGuard.evictOrgMember(orgId, targetUserId);
        log.info("成员移除（含级联）: orgId={}, target={}, operator={}", orgId, targetUserId, userId);
    }

    /** 批量查询有效成员 ID（T2.6 用户搜索 joined 标记用） */
    public java.util.Set<Long> findActiveMemberUserIds(Long orgId, java.util.List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return java.util.Set.of();
        }
        return new java.util.HashSet<>(organizationMemberMapper.findActiveMemberUserIds(orgId, userIds));
    }

    private OrganizationMember requireActiveMember(Long orgId, Long targetUserId) {
        OrganizationMember member = organizationMemberMapper.selectOne(new LambdaQueryWrapper<OrganizationMember>()
                .eq(OrganizationMember::getOrganizationId, orgId)
                .eq(OrganizationMember::getUserId, targetUserId)
                .eq(OrganizationMember::getStatus, 1));
        if (member == null) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        return member;
    }
}
