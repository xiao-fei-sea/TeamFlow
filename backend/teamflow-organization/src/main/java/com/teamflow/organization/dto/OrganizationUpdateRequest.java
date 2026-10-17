package com.teamflow.organization.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 编辑组织请求：仅名称（接口设计文档 3.2） */
public record OrganizationUpdateRequest(

        @NotBlank(message = "请输入组织名称")
        @Size(max = 64, message = "组织名称不能超过 64 个字符")
        String name
) {
}
