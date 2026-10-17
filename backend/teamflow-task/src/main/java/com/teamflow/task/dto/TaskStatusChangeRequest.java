package com.teamflow.task.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 状态流转请求（接口文档 3.6 / PRD FR-K02）：看板拖拽提交 targetStatus + version。
 */
public record TaskStatusChangeRequest(
        @NotBlank(message = "目标状态不能为空")
        String targetStatus,
        @NotNull(message = "version 不能为空")
        Integer version) {
}
