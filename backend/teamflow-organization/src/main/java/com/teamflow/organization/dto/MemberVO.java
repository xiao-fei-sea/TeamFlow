package com.teamflow.organization.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

/**
 * 成员视图：成员列表项与添加成员响应共用，null 字段不输出。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MemberVO(
        Long userId,
        String username,
        String email,
        String role,
        Integer status,
        LocalDateTime joinedAt
) {
}
