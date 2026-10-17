package com.teamflow.task.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

/**
 * 任务 VO（接口文档 3.6）。列表项与详情共用：详情另含 description/creator/createdAt。
 * overdue = 状态非 DONE 且截止时间早于当前（PRD FR-S01 口径）。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TaskVO(
        Long id,
        String title,
        String status,
        Integer priority,
        UserRef assignee,
        LocalDateTime dueAt,
        Boolean overdue,
        Integer version,
        LocalDateTime updatedAt,
        String description,
        UserRef creator,
        LocalDateTime createdAt) {

    /** 用户引用（负责人/创建者） */
    public record UserRef(Long id, String username) {
    }
}
