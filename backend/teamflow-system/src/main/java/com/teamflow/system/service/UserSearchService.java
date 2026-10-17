package com.teamflow.system.service;

import com.teamflow.organization.guard.TenantGuard;
import com.teamflow.organization.service.OrganizationMemberService;
import com.teamflow.system.dto.UserSearchItem;
import com.teamflow.system.entity.SysUser;
import com.teamflow.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * 用户搜索（T2.6）：添加组织成员弹窗用。
 * 权限：orgId 组织的 OWNER/ADMIN（复用 TenantGuard）；keyword 按用户名/邮箱精确匹配；
 * 仅返回有效用户；邮箱脱敏（b***@example.com）；标记是否已加入该组织。
 */
@Service
@RequiredArgsConstructor
public class UserSearchService {

    private static final int SEARCH_LIMIT = 20;

    private final SysUserMapper sysUserMapper;
    private final TenantGuard tenantGuard;
    private final OrganizationMemberService organizationMemberService;

    public List<UserSearchItem> search(Long userId, Long orgId, String keyword) {
        tenantGuard.requireRole(orgId, userId,
                com.teamflow.organization.enums.OrgRole.OWNER,
                com.teamflow.organization.enums.OrgRole.ADMIN);
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        List<SysUser> users = sysUserMapper.searchActiveByKeyword(keyword.trim());
        Set<Long> joinedIds = organizationMemberService.findActiveMemberUserIds(
                orgId, users.stream().map(SysUser::getId).toList());
        return users.stream()
                .map(u -> new UserSearchItem(u.getId(), u.getUsername(), maskEmail(u.getEmail()),
                        joinedIds.contains(u.getId())))
                .toList();
    }

    /** 邮箱脱敏：保留首字符 + *** + @域名 */
    static String maskEmail(String email) {
        if (email == null || email.isEmpty()) {
            return "***";
        }
        int at = email.indexOf('@');
        if (at <= 0) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
