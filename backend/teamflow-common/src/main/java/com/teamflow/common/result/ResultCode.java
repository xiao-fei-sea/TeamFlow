package com.teamflow.common.result;

import lombok.Getter;

/**
 * 全局错误码（与接口设计文档 1.3 一致）。
 */
@Getter
public enum ResultCode {

    SUCCESS(0, "success"),

    /** 参数校验失败，data 携带字段级错误信息 */
    PARAM_ERROR(40001, "参数校验失败"),

    /** 未登录 / Token 失效 / 已登出 */
    UNAUTHORIZED(40101, "未登录或登录已过期"),

    /** 非组织成员（跨租户访问）。对外提示与 40302 一致，避免泄露资源存在性 */
    FORBIDDEN_TENANT(40301, "无权限执行此操作"),

    /** 权限不足（角色不满足权限矩阵） */
    FORBIDDEN(40302, "无权限执行此操作"),

    /** 资源不存在（含归属链不符，防探测统一口径） */
    NOT_FOUND(40401, "资源不存在"),

    /** 并发冲突（任务 version 不一致） */
    CONFLICT(40901, "任务已被他人更新，请刷新后重试"),

    /** 请求过于频繁（限流） */
    TOO_MANY_REQUESTS(42901, "请求过于频繁，请稍后重试"),

    /** 系统内部错误（兜底） */
    SYSTEM_ERROR(50000, "服务异常，请稍后重试");

    private final int code;
    private final String message;

    ResultCode(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
