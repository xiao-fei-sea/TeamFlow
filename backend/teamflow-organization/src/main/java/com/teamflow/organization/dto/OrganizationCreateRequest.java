package com.teamflow.organization.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 创建组织请求（接口设计文档 3.2） */
public record OrganizationCreateRequest(

        @NotBlank(message = "请输入组织名称")
        @Size(max = 64, message = "组织名称不能超过 64 个字符")
        String name
) {
}
