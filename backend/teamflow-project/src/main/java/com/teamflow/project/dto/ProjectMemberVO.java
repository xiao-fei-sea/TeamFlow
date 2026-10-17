package com.teamflow.project.dto;

import java.time.LocalDateTime;

/**
 * 项目成员 VO（接口文档 3.5）。
 */
public record ProjectMemberVO(
        Long userId,
        String username,
        String projectRole,
        LocalDateTime joinedAt) {
}
