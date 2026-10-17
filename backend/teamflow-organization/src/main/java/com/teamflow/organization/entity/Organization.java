package com.teamflow.organization.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.teamflow.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 组织（租户，organization 表）。status：1 正常 / 0 已删除（停用），无逻辑删除字段。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("organization")
public class Organization extends BaseEntity {

    /** 组织名称 */
    private String name;

    /** 所有者用户 ID（创建组织的人） */
    private Long ownerId;

    /** 1 正常 / 0 已停用 */
    private Integer status;
}
