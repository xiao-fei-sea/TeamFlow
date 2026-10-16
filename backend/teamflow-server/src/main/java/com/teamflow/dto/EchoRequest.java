package com.teamflow.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * M0 联调演示用请求体：验证参数校验 → 40001 + 字段级错误信息。
 */
@Data
public class EchoRequest {

    @NotBlank(message = "message 不能为空")
    @Size(max = 64, message = "message 长度不能超过 64 个字符")
    private String message;
}
