package com.teamflow.task.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import com.teamflow.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 任务（task 表）。status：TODO/IN_PROGRESS/DONE；priority：1 低 2 中 3 高 4 紧急。
 * organization_id 为冗余组织 ID，由服务端从 project 反查写入并校验一致（T3.5）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("task")
public class Task extends BaseEntity {

    /** 冗余组织 ID，须与项目一致，服务端反查写入 */
    private Long organizationId;

    /** 所属项目 ID */
    private Long projectId;

    /** 标题 */
    private String title;

    /** 描述（≤2000 字符，应用层校验） */
    private String description;

    /** 负责人（必须是项目成员），可空；updateById 时 null 也写库（支持置空） */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long assigneeId;

    /** 1 低 2 中 3 高 4 紧急 */
    private Integer priority;

    /** TODO / IN_PROGRESS / DONE */
    private String status;

    /** 截止时间 */
    private LocalDateTime dueAt;

    /** 乐观锁版本号（看板拖拽并发控制，40901） */
    @Version
    private Integer version;

    /** 创建者 */
    private Long createdBy;

    /** 0 未删除 / 1 已删除 */
    @TableLogic
    private Integer deleted;
}
