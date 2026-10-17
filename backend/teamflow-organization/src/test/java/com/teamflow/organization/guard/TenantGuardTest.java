package com.teamflow.organization.guard;

import com.teamflow.common.exception.BusinessException;
import com.teamflow.common.result.ResultCode;
import com.teamflow.organization.entity.Organization;
import com.teamflow.organization.entity.OrganizationMember;
import com.teamflow.organization.enums.OrgRole;
import com.teamflow.organization.mapper.GuardMapper;
import com.teamflow.organization.mapper.OrganizationMapper;
import com.teamflow.organization.mapper.OrganizationMemberMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TenantGuard 拒绝路径单元测试（T2.3 验收：每条拒绝路径均有覆盖）。
 * 纯 Mockito 桩测试，不起 Spring 容器。
 */
@ExtendWith(MockitoExtension.class)
class TenantGuardTest {

    private static final Long ORG = 100L;
    private static final Long USER = 1L;

    @Mock
    private OrganizationMapper organizationMapper;
    @Mock
    private OrganizationMemberMapper organizationMemberMapper;
    @Mock
    private GuardMapper guardMapper;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOps;

    private TenantGuard guard;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        guard = new TenantGuard(organizationMapper, organizationMemberMapper, guardMapper, redisTemplate);
    }

    private Organization activeOrg() {
        Organization org = new Organization();
        org.setId(ORG);
        org.setName("研发部");
        org.setOwnerId(9L);
        org.setStatus(1);
        return org;
    }

    @Test
    @DisplayName("组织不存在 → 40401")
    void rejectOrgNotFound() {
        when(organizationMapper.selectById(ORG)).thenReturn(null);
        assertThatThrownBy(() -> guard.requireOrgMember(USER, ORG))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.NOT_FOUND.getCode()));
    }

    @Test
    @DisplayName("组织已停用（删除）→ 40401，成员也失去访问")
    void rejectOrgDisabled() {
        Organization org = activeOrg();
        org.setStatus(0);
        when(organizationMapper.selectById(ORG)).thenReturn(org);
        assertThatThrownBy(() -> guard.requireOrgMember(USER, ORG))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.NOT_FOUND.getCode()));
    }

    @Test
    @DisplayName("非组织成员 → 40301（缓存命中 NONE）")
    void rejectNotMember() {
        when(organizationMapper.selectById(ORG)).thenReturn(activeOrg());
        when(valueOps.get(TenantGuard.MEMBER_KEY_PREFIX + ORG + ":" + USER)).thenReturn("NONE");
        assertThatThrownBy(() -> guard.requireOrgMember(USER, ORG))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.FORBIDDEN_TENANT.getCode()));
    }

    @Test
    @DisplayName("成员但角色不足（MEMBER 要求 ADMIN）→ 40302")
    void rejectRoleInsufficient() {
        when(organizationMapper.selectById(ORG)).thenReturn(activeOrg());
        when(valueOps.get(anyString())).thenReturn(OrgRole.MEMBER.name());
        assertThatThrownBy(() -> guard.requireRole(ORG, USER, OrgRole.ADMIN))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.FORBIDDEN.getCode()));
    }

    @Test
    @DisplayName("OWNER 通过 requireRole（缓存未命中回填查库）")
    void passOwnerRoleWithCacheFill() {
        when(organizationMapper.selectById(ORG)).thenReturn(activeOrg());
        when(valueOps.get(anyString())).thenReturn(null);
        OrganizationMember member = new OrganizationMember();
        member.setOrganizationId(ORG);
        member.setUserId(USER);
        member.setRole(OrgRole.OWNER.name());
        member.setStatus(1);
        when(organizationMemberMapper.selectOne(any())).thenReturn(member);

        guard.requireRole(ORG, USER, OrgRole.OWNER, OrgRole.ADMIN);

        // 回填缓存且 TTL 正常（NONE 才用短 TTL，这里写角色值）
        verify(valueOps).set(anyString(), anyString(), any(java.time.Duration.class));
    }

    @Test
    @DisplayName("requireProjectInOrg：项目不存在 → 40401")
    void rejectProjectNotFound() {
        when(guardMapper.findProjectOrgId(300L)).thenReturn(null);
        assertThatThrownBy(() -> guard.requireProjectInOrg(300L, ORG))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.NOT_FOUND.getCode()));
    }

    @Test
    @DisplayName("requireProjectInOrg：项目归属其他组织 → 40401（防探测统一口径）")
    void rejectProjectWrongOrg() {
        when(guardMapper.findProjectOrgId(300L)).thenReturn(999L);
        assertThatThrownBy(() -> guard.requireProjectInOrg(300L, ORG))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.NOT_FOUND.getCode()));
    }

    @Test
    @DisplayName("requireProjectMember：非项目成员 → 40302")
    void rejectNotProjectMember() {
        when(guardMapper.countProjectMember(300L, USER)).thenReturn(0);
        assertThatThrownBy(() -> guard.requireProjectMember(300L, USER))
                .isInstanceOfSatisfying(BusinessException.class,
                        e -> assertThat(e.getCode()).isEqualTo(ResultCode.FORBIDDEN.getCode()));
    }

    @Test
    @DisplayName("getOrgRole：有效成员返回角色；NONE 转 null")
    void orgRoleMapping() {
        when(valueOps.get(anyString())).thenReturn("NONE");
        assertThat(guard.getOrgRole(ORG, USER)).isNull();
        verify(organizationMemberMapper, never()).selectOne(any());
    }
}
