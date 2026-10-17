package com.teamflow.organization.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** 添加成员请求：role 仅允许 ADMIN / MEMBER（OWNER 只能通过创建组织产生） */
public record MemberAddRequest(

        @NotNull(message = "请指定要添加的用户")
        Long userId,

        @Pattern(regexp = "ADMIN|MEMBER", message = "角色仅允许 ADMIN 或 MEMBER")
        String role
) {
}
