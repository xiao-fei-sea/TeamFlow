package com.teamflow.organization.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.teamflow.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 组织成员关系（organization_member 表）。
 * uk_org_user(organization_id, user_id) 唯一；status：1 在组织 / 0 已移除（保留记录供审计）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("organization_member")
public class OrganizationMember extends BaseEntity {

    private Long organizationId;

    private Long userId;

    /** OWNER / ADMIN / MEMBER */
    private String role;

    /** 1 在组织 / 0 已移除 */
    private Integer status;
}
