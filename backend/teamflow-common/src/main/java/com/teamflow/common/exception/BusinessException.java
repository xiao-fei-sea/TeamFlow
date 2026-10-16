package com.teamflow.common.exception;

import com.teamflow.common.result.ResultCode;
import lombok.Getter;

/**
 * 业务异常：携带错误码，由 GlobalExceptionHandler 统一转换为 Result 响应。
 */
@Getter
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int code;

    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    public BusinessException(ResultCode resultCode, String message) {
        super(message);
        this.code = resultCode.getCode();
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }
}
