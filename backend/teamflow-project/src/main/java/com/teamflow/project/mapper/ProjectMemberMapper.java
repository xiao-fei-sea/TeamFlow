package com.teamflow.project.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.teamflow.project.dto.ProjectMemberVO;
import com.teamflow.project.entity.ProjectMember;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 项目成员 Mapper（接口文档 3.5）；跨 organization_member/sys_user/task 的
 * 校验与级联为单库逻辑外键直查（与 M2 organization 模块同模式）。
 */
@Mapper
public interface ProjectMemberMapper extends BaseMapper<ProjectMember> {

    /** 项目成员列表（含用户名，按 PROJECT_OWNER 优先、加入时间正序） */
    @Select("""
            SELECT pm.user_id AS userId, u.username, pm.project_role AS projectRole, pm.created_at AS joinedAt
            FROM project_member pm
            JOIN sys_user u ON u.id = pm.user_id AND u.deleted = 0
            WHERE pm.project_id = #{projectId}
            ORDER BY FIELD(pm.project_role, 'PROJECT_OWNER', 'PROJECT_MEMBER'), pm.created_at
            """)
    List<ProjectMemberVO> listMembers(@Param("projectId") Long projectId);

    /** 单个成员 VO（幂等/重复校验用） */
    @Select("""
            SELECT pm.user_id AS userId, u.username, pm.project_role AS projectRole, pm.created_at AS joinedAt
            FROM project_member pm
            JOIN sys_user u ON u.id = pm.user_id AND u.deleted = 0
            WHERE pm.project_id = #{projectId} AND pm.user_id = #{userId}
            """)
    ProjectMemberVO findMemberVO(@Param("projectId") Long projectId, @Param("userId") Long userId);

    /** 项目成员数（详情接口 memberCount） */
    @Select("SELECT COUNT(*) FROM project_member WHERE project_id = #{projectId}")
    long countMembers(@Param("projectId") Long projectId);

    /** 添加成员前校验：目标用户必须是本组织有效成员且账号有效（FR-PM02 服务端校验） */
    @Select("""
            SELECT COUNT(*) FROM organization_member om
            JOIN sys_user u ON u.id = om.user_id AND u.status = 1 AND u.deleted = 0
            WHERE om.organization_id = #{orgId} AND om.user_id = #{userId} AND om.status = 1
            """)
    int countOrgMemberValid(@Param("orgId") Long orgId, @Param("userId") Long userId);

    /** 移除项目成员副作用：其在该项目内被分配任务的负责人置空（FR-PM03） */
    @Update("""
            UPDATE task SET assignee_id = NULL
            WHERE project_id = #{projectId} AND assignee_id = #{userId} AND deleted = 0
            """)
    int clearAssignee(@Param("projectId") Long projectId, @Param("userId") Long userId);

    /** 移除项目成员（物理删除，与 M2 移除组织成员的级联语义一致） */
    @Delete("DELETE FROM project_member WHERE project_id = #{projectId} AND user_id = #{userId}")
    int removeMember(@Param("projectId") Long projectId, @Param("userId") Long userId);
}
