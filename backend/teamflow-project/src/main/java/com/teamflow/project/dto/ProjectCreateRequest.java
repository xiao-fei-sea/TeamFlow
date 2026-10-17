package com.teamflow.project.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建项目请求（接口文档 3.4）：名称必填 ≤64，描述选填 ≤512。
 */
public record ProjectCreateRequest(
        @NotBlank(message = "项目名称不能为空")
        @Size(max = 64, message = "项目名称不能超过 64 个字符")
        String name,
        @Size(max = 512, message = "项目描述不能超过 512 个字符")
        String description) {
}
