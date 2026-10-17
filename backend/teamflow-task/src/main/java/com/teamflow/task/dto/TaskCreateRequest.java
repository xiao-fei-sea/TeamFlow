package com.teamflow.task.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

/**
 * 创建任务请求（接口文档 3.6 / PRD FR-T01）：
 * title 必填；assigneeId 必须是项目成员（Service 校验）；dueAt 不得早于当前时间；默认 TODO/2。
 */
public record TaskCreateRequest(
        @NotBlank(message = "任务标题不能为空")
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
