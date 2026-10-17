package com.teamflow.task.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * 编辑任务请求（接口文档 3.6）：字段可选；状态流转走 PATCH /status 不在本接口；
 * dueAt 仅在请求携带时校验不得早于当前（保留过去时间不改允许，PRD FR-T01）。
 */
public record TaskUpdateRequest(
        @Size(max = 128, message = "任务标题不能超过 128 个字符")
        String title,
        @Size(max = 2000, message = "任务描述不能超过 2000 个字符")
        String description,
        Long assigneeId,
        @Min(value = 1, message = "优先级取值 1-4")
        @Max(value = 4, message = "优先级取值 1-4")
        Integer priority,
        LocalDateTime dueAt) {
}
