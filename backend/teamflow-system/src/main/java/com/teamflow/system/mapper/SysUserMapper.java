package com.teamflow.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.teamflow.system.entity.SysUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 用户账号 Mapper：单表 CRUD 由 MyBatis-Plus 提供，搜索为手写精确匹配。
 */
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {

    /** T2.6 用户搜索：用户名/邮箱精确匹配，仅有效用户（正常 + 未删除） */
    @Select("""
            SELECT id, username, email FROM sys_user
            WHERE status = 1 AND deleted = 0
              AND (username = #{keyword} OR email = #{keyword})
            LIMIT 20
            """)
    List<SysUser> searchActiveByKeyword(@Param("keyword") String keyword);
}
