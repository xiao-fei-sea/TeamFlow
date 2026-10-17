package com.teamflow.project.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.teamflow.project.dto.ProjectVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 项目 Mapper：列表可见性 + 任务统计子查询（接口文档 3.4）。
 */
@Mapper
public interface ProjectMapper extends BaseMapper<com.teamflow.project.entity.Project> {

    /**
     * 项目列表（分页）：
     * OWNER/ADMIN 全量可见；MEMBER 仅见参与项目（onlyJoined=true，按 project_member 过滤，T3.2）。
     * keyword 名称模糊；status 1 进行中 / 0 已归档；taskTotal/taskDoing 仅统计未删除任务。
     */
    @Select("""
            <script>
            SELECT p.id, p.name, p.description, p.status, p.created_at AS createdAt,
                   (SELECT COUNT(*) FROM task t WHERE t.project_id = p.id AND t.deleted = 0) AS taskTotal,
                   (SELECT COUNT(*) FROM task t WHERE t.project_id = p.id AND t.deleted = 0 AND t.status = 'IN_PROGRESS') AS taskDoing,
                   NULL AS myRole, NULL AS memberCount,
                   (SELECT pm2.project_role FROM project_member pm2
                     WHERE pm2.project_id = p.id AND pm2.user_id = #{userId}) AS projectRole
            FROM project p
            WHERE p.organization_id = #{orgId} AND p.deleted = 0
              AND p.status = #{status}
              <if test="keyword != null and keyword != ''">
              AND p.name LIKE CONCAT('%', #{keyword}, '%')
              </if>
              <if test="onlyJoined">
              AND EXISTS (SELECT 1 FROM project_member pm
                           WHERE pm.project_id = p.id AND pm.user_id = #{userId})
              </if>
            ORDER BY p.created_at DESC
            </script>
            """)
    IPage<ProjectVO> pageProjects(Page<ProjectVO> page,
                                  @Param("orgId") Long orgId,
                                  @Param("userId") Long userId,
                                  @Param("onlyJoined") boolean onlyJoined,
                                  @Param("keyword") String keyword,
                                  @Param("status") int status);

    /** 名称唯一校验：组织内含已归档/已删除（物理唯一键 uk_org_name 占用），excludeId 非空时排除自身 */
    @Select("""
            <script>
            SELECT COUNT(*) FROM project
            WHERE organization_id = #{orgId} AND name = #{name}
              <if test="excludeId != null">AND id != #{excludeId}</if>
            </script>
            """)
    Long countByName(@Param("orgId") Long orgId, @Param("name") String name, @Param("excludeId") Long excludeId);
}
