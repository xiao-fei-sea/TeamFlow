package com.teamflow.organization.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.teamflow.common.exception.BusinessException;
import com.teamflow.common.result.ResultCode;
import com.teamflow.organization.dto.OrganizationCreateRequest;
import com.teamflow.organization.dto.OrganizationUpdateRequest;
import com.teamflow.organization.dto.OrganizationVO;
import com.teamflow.organization.entity.Organization;
import com.teamflow.organization.entity.OrganizationMember;
import com.teamflow.organization.enums.OrgRole;
import com.teamflow.organization.guard.TenantGuard;
import com.teamflow.organization.mapper.OrganizationMapper;
import com.teamflow.organization.mapper.OrganizationMemberMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 组织服务：创建（T2.1）/ 我的组织列表、详情、编辑（T2.2）/ 删除（T2.8）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrganizationService {

    /** 单用户可创建的组织数量上限（PRD 5.2） */
    private static final long MAX_ORG_PER_USER = 5;

    private final OrganizationMapper organizationMapper;
    private final OrganizationMemberMapper organizationMemberMapper;
    private final TenantGuard tenantGuard;

    /** 创建组织：同一事务写 organization + OWNER 成员关系（PRD FR-O01） */
    @Transactional
    public OrganizationVO create(Long userId, OrganizationCreateRequest request) {
        long owned = organizationMapper.selectCount(new LambdaQueryWrapper<Organization>()
                .eq(Organization::getOwnerId, userId)
                .eq(Organization::getStatus, 1));
        if (owned >= MAX_ORG_PER_USER) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "最多创建 " + MAX_ORG_PER_USER + " 个组织");
        }

        Organization org = new Organization();
        org.setName(request.name());
        org.setOwnerId(userId);
        org.setStatus(1);
        organizationMapper.insert(org);

        OrganizationMember member = new OrganizationMember();
        member.setOrganizationId(org.getId());
        member.setUserId(userId);
        member.setRole(OrgRole.OWNER.name());
        member.setStatus(1);
        organizationMemberMapper.insert(member);

        log.info("组织创建成功: orgId={}, owner={}", org.getId(), userId);
        return new OrganizationVO(org.getId(), org.getName(), OrgRole.OWNER.name(), null, null, null, org.getCreatedAt());
    }

    /** 我所属的组织列表（组织与成员关系均有效） */
    public List<OrganizationVO> listMy(Long userId) {
        return organizationMemberMapper.listMyOrganizations(userId);
    }

    /** 组织详情（权限：组织成员） */
    public OrganizationVO getDetail(Long userId, Long orgId) {
        String myRole = tenantGuard.requireOrgMember(userId, orgId);
        Organization org = organizationMapper.selectById(orgId);
        return new OrganizationVO(org.getId(), org.getName(), myRole,
                organizationMapper.countMembers(orgId), org.getOwnerId(),
                organizationMapper.countProjects(orgId), org.getCreatedAt());
    }

    /** 编辑组织名称（权限：OWNER/ADMIN，PRD 4.3） */
    public void updateName(Long userId, Long orgId, OrganizationUpdateRequest request) {
        tenantGuard.requireRole(orgId, userId, OrgRole.OWNER, OrgRole.ADMIN);
        Organization org = new Organization();
        org.setId(orgId);
        org.setName(request.name());
        organizationMapper.updateById(org);
        log.info("组织名称变更: orgId={}, operator={}", orgId, userId);
    }

    /** 删除组织（OWNER 专属，confirmName 二次确认；停用而非物理删除，T2.8） */
    public void delete(Long userId, Long orgId, String confirmName) {
        tenantGuard.requireRole(orgId, userId, OrgRole.OWNER);
        Organization org = organizationMapper.selectById(orgId);
        if (confirmName == null || !confirmName.equals(org.getName())) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "确认名称与组织名称不一致");
        }
        Organization update = new Organization();
        update.setId(orgId);
        update.setStatus(0);
        organizationMapper.updateById(update);
        log.info("组织已停用: orgId={}, operator={}", orgId, userId);
    }
}
