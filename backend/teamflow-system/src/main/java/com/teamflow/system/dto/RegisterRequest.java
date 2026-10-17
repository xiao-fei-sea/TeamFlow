package com.teamflow.system.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * 注册请求（接口设计文档 3.1）。
 *
 * <p>校验口径（PRD 6.3）：用户名 3–32 位字母/数字/下划线；
 * 密码 8–32 位且必须同时包含字母和数字；邮箱格式合法。
 */
public record RegisterRequest(

        @NotBlank(message = "请输入用户名")
        @Pattern(regexp = "^[A-Za-z0-9_]{3,32}$", message = "用户名须为 3-32 位字母、数字或下划线")
        String username,

        @NotBlank(message = "请输入密码")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,32}$", message = "密码须为 8-32 位且同时包含字母和数字")
        String password,

        @NotBlank(message = "请输入邮箱")
        @Email(message = "邮箱格式不正确")
        String email
) {
}
