package com.teamflow.common.exception;

import com.teamflow.common.result.FieldErrorItem;
import lombok.Getter;

import java.util.List;

/**
 * 字段级业务校验异常：用于服务层校验（如注册时用户名/邮箱唯一性冲突），
 * 由 GlobalExceptionHandler 转换为 40001 + data.errors[{field, message}]，
 * 与 Bean Validation 的 BindException 响应结构完全一致。
 */
@Getter
public class FieldValidationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final transient List<FieldErrorItem> errors;

    public FieldValidationException(List<FieldErrorItem> errors) {
        super("参数校验失败");
        this.errors = errors;
    }
}
