package com.teamflow.organization.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** 变更成员角色请求：仅 OWNER 可操作，ADMIN / MEMBER 之间变更 */
public record RoleChangeRequest(

        @NotBlank(message = "请指定目标角色")
        @Pattern(regexp = "ADMIN|MEMBER", message = "角色仅允许 ADMIN 或 MEMBER")
        String role
) {
}
