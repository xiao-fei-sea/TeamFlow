package com.teamflow.organization.guard;

import com.teamflow.common.exception.BusinessException;
import com.teamflow.common.result.ResultCode;
import com.teamflow.organization.entity.Organization;
import com.teamflow.organization.entity.OrganizationMember;
import com.teamflow.organization.enums.OrgRole;
import com.teamflow.organization.mapper.GuardMapper;
import com.teamflow.organization.mapper.OrganizationMapper;
import com.teamflow.organization.mapper.OrganizationMemberMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 租户校验组件（技术计划设计书 6.2，全项目最高风险项）。
 *
 * <p>五步校验中的第 2–5 步；第 1 步（JWT 识别用户）由 JwtAuthenticationFilter 完成。
 * 所有组织/项目/任务相关 Service 方法入口必须显式调用。
 *
 * <ul>
 *   <li>组织不存在/已停用 → 40401（统一「资源不存在」口径，防探测）</li>
 *   <li>非组织成员 → 40301（跨租户访问）</li>
 *   <li>角色不足 → 40302</li>
 *   <li>项目归属链不符 → 40401；MEMBER 非项目成员 → 40302</li>
 * </ul>
 *
 * <p>成员角色走 Redis 缓存 {@code tf:org:member:{orgId}:{userId}}（值=角色/NONE，
 * 正常 30min、NONE 5min 防穿透），角色变更/成员移除/重新加入时必须调用 {@link #evictOrgMember}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantGuard {

    public static final String MEMBER_KEY_PREFIX = "tf:org:member:";
    private static final String NONE = "NONE";
    private static final Duration ROLE_TTL = Duration.ofMinutes(30);
    private static final Duration NONE_TTL = Duration.ofMinutes(5);

    private final OrganizationMapper organizationMapper;
    private final OrganizationMemberMapper organizationMemberMapper;
    private final GuardMapper guardMapper;
    private final StringRedisTemplate stringRedisTemplate;

    /** 步骤 2：组织存在且启用，且 userId 是其有效成员；通过后返回角色 */
    public String requireOrgMember(Long userId, Long orgId) {
        Organization org = organizationMapper.selectById(orgId);
        if (org == null || org.getStatus() == null || org.getStatus() != 1) {
            // 组织不存在或已停用：统一资源不存在口径（含已删除组织，成员失去访问）
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
        String role = loadRole(orgId, userId);
        if (NONE.equals(role)) {
            throw new BusinessException(ResultCode.FORBIDDEN_TENANT);
        }
        return role;
    }

    /** 步骤 3：RBAC 角色判断（先过成员校验） */
    public void requireRole(Long orgId, Long userId, OrgRole... allowed) {
        String role = requireOrgMember(userId, orgId);
        for (OrgRole r : allowed) {
            if (r.name().equals(role)) {
                return;
            }
        }
        throw new BusinessException(ResultCode.FORBIDDEN);
    }

    /** 步骤 4：资源归属链——项目必须存在且归属 orgId，不符按「资源不存在」处理（防探测） */
    public void requireProjectInOrg(Long projectId, Long orgId) {
        Long projectOrgId = guardMapper.findProjectOrgId(projectId);
        if (projectOrgId == null || !projectOrgId.equals(orgId)) {
            throw new BusinessException(ResultCode.NOT_FOUND);
        }
    }

    /** 步骤 5：MEMBER 必须是项目成员（OWNER/ADMIN 由调用方先行跳过） */
    public void requireProjectMember(Long projectId, Long userId) {
        if (guardMapper.countProjectMember(projectId, userId) == 0) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
    }

    /** 项目成员计数（任务 assignee 校验等场景复用，不抛异常） */
    public int countProjectMember(Long projectId, Long userId) {
        return guardMapper.countProjectMember(projectId, userId);
    }

    /** 取角色（带缓存，不校验组织状态）；非有效成员返回 null */
    public String getOrgRole(Long orgId, Long userId) {
        String role = loadRole(orgId, userId);
        return NONE.equals(role) ? null : role;
    }

    /** 角色缓存失效：角色变更/移除成员/重新加入时必须调用 */
    public void evictOrgMember(Long orgId, Long userId) {
        stringRedisTemplate.delete(MEMBER_KEY_PREFIX + orgId + ":" + userId);
    }

    /** 读缓存未命中则查库回填：有效成员存角色，否则存 NONE */
    private String loadRole(Long orgId, Long userId) {
        String key = MEMBER_KEY_PREFIX + orgId + ":" + userId;
        try {
            String cached = stringRedisTemplate.opsForValue().get(key);
            if (cached != null) {
                return cached;
            }
        } catch (Exception e) {
            log.warn("角色缓存读取失败，降级查库: {}", e.getMessage());
        }
        OrganizationMember member = organizationMemberMapper.selectOne(new LambdaQueryWrapper<OrganizationMember>()
                .eq(OrganizationMember::getOrganizationId, orgId)
                .eq(OrganizationMember::getUserId, userId)
                .eq(OrganizationMember::getStatus, 1));
        String role = member == null ? NONE : member.getRole();
        try {
            stringRedisTemplate.opsForValue().set(key, role, NONE.equals(role) ? NONE_TTL : ROLE_TTL);
        } catch (Exception e) {
            log.warn("角色缓存写入失败: {}", e.getMessage());
        }
        return role;
    }
}
