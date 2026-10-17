package com.teamflow.project.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.teamflow.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 项目成员（project_member 表）。project_role：PROJECT_OWNER / PROJECT_MEMBER；
 * 移除为物理删除（与 M2 移除组织成员级联的 DELETE 语义一致）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("project_member")
public class ProjectMember extends BaseEntity {

    /** 项目 ID */
    private Long projectId;

    /** 用户 ID（必须是组织成员） */
    private Long userId;

    /** PROJECT_OWNER / PROJECT_MEMBER */
    private String projectRole;
}
