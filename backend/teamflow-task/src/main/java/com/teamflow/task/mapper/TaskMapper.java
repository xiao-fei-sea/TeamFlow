package com.teamflow.task.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.teamflow.task.entity.Task;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 任务 Mapper（T3.4/T3.5）。列表筛选/排序走动态 SQL（排序字段白名单，防注入）；
 * project 表为单库逻辑外键只读直查（与 M2/M3 跨表模式一致）。
 */
@Mapper
public interface TaskMapper extends BaseMapper<Task> {

    /** 项目行：归属校验、归档判断、冗余 organization_id 反查（T3.5） */
    @Select("SELECT id, organization_id AS organizationId, status FROM project WHERE id = #{projectId} AND deleted = 0")
    TaskProjectRow findProjectRow(@Param("projectId") Long projectId);

    /**
     * 任务列表（分页）：keyword 标题模糊；assigneeId=null 不过滤 / -1=未分配 / 其他=精确；
     * priority/status 精确；orderBy 白名单 created_at|due_at，orderDir asc|desc。
     */
    @Select("""
            <script>
            SELECT t.id, t.title, t.status, t.priority, t.assignee_id AS assigneeId,
                   au.username AS assigneeName, t.due_at AS dueAt, t.version, t.updated_at AS updatedAt,
                   NULL AS description, cu.id AS creatorId, cu.username AS creatorName, t.created_at AS createdAt
            FROM task t
            LEFT JOIN sys_user au ON au.id = t.assignee_id AND au.deleted = 0
            JOIN sys_user cu ON cu.id = t.created_by AND cu.deleted = 0
            WHERE t.project_id = #{projectId} AND t.deleted = 0
              <if test="keyword != null and keyword != ''">
              AND t.title LIKE CONCAT('%', #{keyword}, '%')
              </if>
              <if test="assigneeId != null and assigneeId == -1">
              AND t.assignee_id IS NULL
              </if>
              <if test="assigneeId != null and assigneeId > 0">
              AND t.assignee_id = #{assigneeId}
              </if>
              <if test="priority != null">AND t.priority = #{priority}</if>
              <if test="status != null and status != ''">AND t.status = #{status}</if>
            ORDER BY
              <choose>
                <when test="orderBy == 'due_at'">t.due_at</when>
                <otherwise>t.created_at</otherwise>
              </choose>
              <choose>
                <when test="orderDir == 'asc'">ASC</when>
                <otherwise>DESC</otherwise>
              </choose>
            </script>
            """)
    IPage<TaskRow> pageTasks(Page<TaskRow> page,
                             @Param("projectId") Long projectId,
                             @Param("keyword") String keyword,
                             @Param("assigneeId") Long assigneeId,
                             @Param("priority") Integer priority,
                             @Param("status") String status,
                             @Param("orderBy") String orderBy,
                             @Param("orderDir") String orderDir);

    /** 任务详情行（含描述/创建者/创建时间） */
    @Select("""
            SELECT t.id, t.title, t.status, t.priority, t.assignee_id AS assigneeId,
                   au.username AS assigneeName, t.due_at AS dueAt, t.version, t.updated_at AS updatedAt,
                   t.description, cu.id AS creatorId, cu.username AS creatorName, t.created_at AS createdAt
            FROM task t
            LEFT JOIN sys_user au ON au.id = t.assignee_id AND au.deleted = 0
            JOIN sys_user cu ON cu.id = t.created_by AND cu.deleted = 0
            WHERE t.id = #{taskId} AND t.deleted = 0
            """)
    TaskRow findTaskRow(@Param("taskId") Long taskId);

    /** 状态流转乐观锁提交：成功返回 1，version 不一致返回 0（T3.5，40901） */
    @Update("""
            UPDATE task SET status = #{targetStatus}, version = version + 1
            WHERE id = #{taskId} AND organization_id = #{orgId} AND deleted = 0 AND version = #{version}
            """)
    int casStatus(@Param("taskId") Long taskId, @Param("orgId") Long orgId,
                  @Param("targetStatus") String targetStatus, @Param("version") int version);
    /** 乐观锁提交后的最新 version */
    @Select("SELECT version FROM task WHERE id = #{taskId}")
    Integer getVersion(@Param("taskId") Long taskId);

    /**
     * 任务行投影：平铺列名与构造器参数一一对应（arg-name 构造映射）。
     */
    record TaskRow(Long id, String title, String status, Integer priority, Long assigneeId,
                   String assigneeName, java.time.LocalDateTime dueAt, Integer version,
                   java.time.LocalDateTime updatedAt, String description, Long creatorId,
                   String creatorName, java.time.LocalDateTime createdAt) {
    }

    /** 项目行投影 */
    record TaskProjectRow(Long id, Long organizationId, Integer status) {
    }
}
