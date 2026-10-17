package com.teamflow.system.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 登录请求（接口设计文档 3.1）：account 支持用户名或邮箱。
 */
public record LoginRequest(

        @NotBlank(message = "请输入用户名或邮箱")
        String account,

        @NotBlank(message = "请输入密码")
        String password
) {
}
