package com.teamflow.organization.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.teamflow.organization.entity.OrganizationMember;
import com.teamflow.organization.dto.MemberVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 组织成员 Mapper：复杂查询（join sys_user 展示用户名/邮箱）手写 SQL。
 */
@Mapper
public interface OrganizationMemberMapper extends BaseMapper<OrganizationMember> {

    /**
     * 成员列表（分页 + keyword 用户名/邮箱模糊），OWNER 在前。
     * 分页插件按首参 IPage 自动 count。
     */
    @Select("""
            <script>
            SELECT om.user_id AS userId, u.username, u.email, om.role, om.status,
                   om.created_at AS joined_at
            FROM organization_member om
            JOIN sys_user u ON u.id = om.user_id AND u.deleted = 0
            WHERE om.organization_id = #{orgId} AND om.status = 1
            <if test="keyword != null and keyword != ''">
              AND (u.username LIKE CONCAT('%', #{keyword}, '%') OR u.email LIKE CONCAT('%', #{keyword}, '%'))
            </if>
            ORDER BY FIELD(om.role, 'OWNER', 'ADMIN', 'MEMBER'), om.created_at
            </script>
            """)
    Page<MemberVO> pageMembers(Page<MemberVO> page, @Param("orgId") Long orgId, @Param("keyword") String keyword);

    /** 单条成员视图（join sys_user），用于添加成员响应；无有效成员关系返回 null */
    @Select("""
            SELECT om.user_id AS userId, u.username, u.email, om.role, om.status,
                   om.created_at AS joined_at
            FROM organization_member om
            JOIN sys_user u ON u.id = om.user_id AND u.deleted = 0
            WHERE om.organization_id = #{orgId} AND om.user_id = #{userId} AND om.status = 1
            """)
    MemberVO findMemberVO(@Param("orgId") Long orgId, @Param("userId") Long userId);

    /** 我所属的组织列表（组织 status=1 且成员关系 status=1），含 memberCount；列表不返回 projectCount（接口文档 3.2），NULL 占位满足按名构造映射 */
    @Select("""
            SELECT o.id, o.name, om.role AS myRole, o.owner_id AS ownerId, o.created_at AS createdAt,
                   (SELECT COUNT(*) FROM organization_member m2 WHERE m2.organization_id = o.id AND m2.status = 1) AS memberCount,
                   NULL AS projectCount
            FROM organization o
            JOIN organization_member om ON om.organization_id = o.id
                 AND om.user_id = #{userId} AND om.status = 1
            WHERE o.status = 1
            ORDER BY o.created_at DESC
            """)
    java.util.List<com.teamflow.organization.dto.OrganizationVO> listMyOrganizations(@Param("userId") Long userId);

    // ---------- T2.5 移除级联（与成员移除同一事务调用） ----------

    /** 添加成员前校验目标用户有效（正常 + 未删除）；organization 直查 sys_user（单库逻辑外键，不经 system 模块） */
    @Select("SELECT COUNT(*) FROM sys_user WHERE id = #{userId} AND status = 1 AND deleted = 0")
    int countActiveUser(@Param("userId") Long userId);

    /** 批量查询在指定组织内的有效成员 ID（T2.6 用户搜索 joined 标记） */
    @Select("""
            <script>
            SELECT user_id FROM organization_member
            WHERE organization_id = #{orgId} AND status = 1
              AND user_id IN
              <foreach collection="userIds" item="uid" open="(" separator="," close=")">#{uid}</foreach>
            </script>
            """)
    java.util.List<Long> findActiveMemberUserIds(@Param("orgId") Long orgId, @Param("userIds") java.util.List<Long> userIds);

    /** 移出该用户在指定组织参与的全部项目（project_member 物理删除） */
    @Update("""
            DELETE pm FROM project_member pm
            JOIN project p ON p.id = pm.project_id AND p.organization_id = #{orgId}
            WHERE pm.user_id = #{userId}
            """)
    int removeFromProjects(@Param("orgId") Long orgId, @Param("userId") Long userId);

    /** 该用户在指定组织被分配任务的负责人置空 */
    @Update("""
            UPDATE task SET assignee_id = NULL
            WHERE organization_id = #{orgId} AND assignee_id = #{userId} AND deleted = 0
            """)
    int clearAssignee(@Param("orgId") Long orgId, @Param("userId") Long userId);
}
