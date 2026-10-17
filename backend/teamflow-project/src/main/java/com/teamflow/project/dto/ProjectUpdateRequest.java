package com.teamflow.project.dto;

import jakarta.validation.constraints.Size;

/**
 * 编辑项目请求（接口文档 3.4）：字段可选，至少传一项（name 必填校验在 Service 层按提交值做）。
 */
public record ProjectUpdateRequest(
        @Size(max = 64, message = "项目名称不能超过 64 个字符")
        String name,
        @Size(max = 512, message = "项目描述不能超过 512 个字符")
        String description) {
}
