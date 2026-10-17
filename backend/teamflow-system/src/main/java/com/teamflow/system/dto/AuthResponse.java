package com.teamflow.system.dto;

/**
 * 注册/登录响应（接口设计文档 3.1）：自动登录，直接返回 Token。
 */
public record AuthResponse(String token, Long expiresIn, UserVO user) {
}
