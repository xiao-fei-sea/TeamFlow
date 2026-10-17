package com.teamflow.organization.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.teamflow.organization.entity.Organization;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 组织 Mapper。
 */
@Mapper
public interface OrganizationMapper extends BaseMapper<Organization> {

    /** 组织内有效成员数（status=1） */
    @Select("SELECT COUNT(*) FROM organization_member WHERE organization_id = #{orgId} AND status = 1")
    long countMembers(@Param("orgId") Long orgId);

    /** 组织内未删除项目数（含已归档） */
    @Select("SELECT COUNT(*) FROM project WHERE organization_id = #{orgId} AND deleted = 0")
    long countProjects(@Param("orgId") Long orgId);
}
