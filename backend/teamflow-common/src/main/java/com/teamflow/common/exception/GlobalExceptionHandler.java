package com.teamflow.common.exception;

import com.teamflow.common.result.FieldErrorItem;
import com.teamflow.common.result.Result;
import com.teamflow.common.result.ResultCode;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 全局异常处理：业务异常/参数校验异常/未知异常统一转换为 Result 响应。
 *
 * <p>约定（PRD 6.4）：
 * <ul>
 *   <li>参数错误 → 40001 + 字段级错误信息</li>
 *   <li>未知异常 → 50000「服务异常，请稍后重试」，不向客户端泄露堆栈细节</li>
 * </ul>
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 参数校验失败（@RequestBody @Valid → MethodArgumentNotValidException，
     * 表单/Query 绑定 → BindException；Spring 6 中前者继承后者，一个处理器覆盖两者）。
     */
    @ExceptionHandler(BindException.class)
    public Result<Map<String, Object>> handleBindException(BindException e) {
        return Result.fail(ResultCode.PARAM_ERROR, Map.of("errors", toFieldErrors(e.getFieldErrors())));
    }

    /** 服务层/路径参数上的 @Validated 约束校验失败 */
    @ExceptionHandler(ConstraintViolationException.class)
    public Result<Map<String, Object>> handleConstraintViolation(ConstraintViolationException e) {
        List<FieldErrorItem> errors = new ArrayList<>();
        for (ConstraintViolation<?> violation : e.getConstraintViolations()) {
            String field = violation.getPropertyPath().toString();
            int dot = field.lastIndexOf('.');
            if (dot >= 0) {
                field = field.substring(dot + 1);
            }
            errors.add(new FieldErrorItem(field, violation.getMessage()));
        }
        return Result.fail(ResultCode.PARAM_ERROR, Map.of("errors", errors));
    }

    /** 缺少必填参数 → 40001 字段级提示 */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public Result<Map<String, Object>> handleMissingParam(MissingServletRequestParameterException e) {
        FieldErrorItem item = new FieldErrorItem(e.getParameterName(), "缺少必填参数");
        return Result.fail(ResultCode.PARAM_ERROR, Map.of("errors", List.of(item)));
    }

    /** 参数类型不匹配 → 40001 字段级提示 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public Result<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        FieldErrorItem item = new FieldErrorItem(e.getName(), "参数类型不正确");
        return Result.fail(ResultCode.PARAM_ERROR, Map.of("errors", List.of(item)));
    }

    /** 请求体不可读（非法 JSON 等）→ 40001 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Map<String, Object>> handleNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败: {}", e.getMessage());
        return Result.fail(ResultCode.PARAM_ERROR, Map.of("errors",
                List.of(new FieldErrorItem("body", "请求体格式不正确"))));
    }

    /** 静态资源不存在（无对应路由）→ 40401，与资源不存在统一口径 */
    @ExceptionHandler(NoResourceFoundException.class)
    public Result<Void> handleNoResource(NoResourceFoundException e) {
        return Result.fail(ResultCode.NOT_FOUND);
    }

    /** 业务异常：透传错误码与提示 */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusiness(BusinessException e) {
        log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    /** 未知异常兜底 → 50000，服务端记录完整堆栈，客户端不泄露细节 */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleUnknown(Exception e) {
        log.error("未知异常", e);
        return Result.fail(ResultCode.SYSTEM_ERROR);
    }

    private List<FieldErrorItem> toFieldErrors(List<FieldError> fieldErrors) {
        List<FieldErrorItem> errors = new ArrayList<>();
        for (FieldError fe : fieldErrors) {
            errors.add(new FieldErrorItem(fe.getField(), fe.getDefaultMessage()));
        }
        return errors;
    }
}
