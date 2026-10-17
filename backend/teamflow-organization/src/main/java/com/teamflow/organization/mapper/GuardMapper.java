package com.teamflow.organization.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * TenantGuard 专用查询：跨 project / project_member 的只读归属校验（单库逻辑外键）。
 * M3 的 project 模块业务实现后仍复用本组件，避免 organization 依赖 project 模块。
 */
@Mapper
public interface GuardMapper {

    /** 项目所属组织 ID；项目不存在（含已删除）返回 null */
    @Select("SELECT organization_id FROM project WHERE id = #{projectId} AND deleted = 0")
    Long findProjectOrgId(@Param("projectId") Long projectId);

    /** 用户是否为项目成员 */
    @Select("SELECT COUNT(*) FROM project_member WHERE project_id = #{projectId} AND user_id = #{userId}")
    int countProjectMember(@Param("projectId") Long projectId, @Param("userId") Long userId);
}
