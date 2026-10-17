package com.teamflow.system.entity;

import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.teamflow.common.entity.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 用户账号（sys_user，数据库设计文档 3.1）。
 *
 * <p>status：1 正常 / 0 禁用；deleted 逻辑删除（全局配置按字段名生效）。
 * passwordHash 标记 @JsonIgnore，实体在任何场景下都不向外序列化密码字段。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_user")
public class SysUser extends BaseEntity {

    /** 用户名，全局唯一（uk_username） */
    private String username;

    /** BCrypt 密文 */
    @JsonIgnore
    private String passwordHash;

    /** 邮箱，全局唯一（uk_email） */
    private String email;

    /** 1 正常 / 0 禁用 */
    private Integer status;

    /** 0 未删除 / 1 已删除（逻辑删除） */
    @TableLogic
    private Integer deleted;
}
