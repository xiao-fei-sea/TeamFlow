package com.teamflow.common.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 实体基类：雪花 ID + 时间自动填充。
 *
 * <p>约定（数据库设计文档 1）：
 * <ul>
 *   <li>主键 BIGINT，应用层雪花 ID（{@link IdType#ASSIGN_ID}），JSON 输出为字符串</li>
 *   <li>created_at / updated_at 由 {@code MybatisMetaObjectHandler} 自动填充</li>
 *   <li>逻辑删除：需要软删的实体（sys_user/organization/project/task）自行增加
 *       {@code @TableLogic private Integer deleted;} 字段（全局配置已按字段名 deleted 生效）</li>
 * </ul>
 */
@Data
public abstract class BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键：雪花 ID，JSON 中以字符串输出（避免 JS 精度丢失） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /** 创建时间（插入时自动填充） */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间（插入/更新时自动填充） */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
