package com.teamflow.project.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.teamflow.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 项目（project 表）。status：1 进行中 / 0 已归档（只读封存，可恢复）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("project")
public class Project extends BaseEntity {

    /** 所属组织 ID */
    private Long organizationId;

    /** 项目名称，组织内唯一（含已归档，物理唯一键 uk_org_name） */
    private String name;

    /** 描述 */
    private String description;

    /** 1 进行中 / 0 已归档 */
    private Integer status;

    /** 创建者（自动成为 PROJECT_OWNER） */
    private Long createdBy;

    /** 0 未删除 / 1 已删除 */
    @TableLogic
    private Integer deleted;
}
