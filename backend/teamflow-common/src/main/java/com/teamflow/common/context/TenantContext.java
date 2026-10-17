package com.teamflow.common.context;

/**
 * 租户上下文：ThreadLocal 保存当前请求的登录用户 ID。
 *
 * <p>M1 由 JwtAuthenticationFilter 在认证成功后写入，请求结束清理；
 * M2 起 TenantGuard 与各业务 Service 通过 {@link #getUserId()} 获取当前用户。
 * 注意：线程池复用场景必须在 finally 中 clear，避免串号。
 */
public final class TenantContext {

    private static final ThreadLocal<Long> USER_ID = new ThreadLocal<>();

    private TenantContext() {
    }

    public static void setUserId(Long userId) {
        USER_ID.set(userId);
    }

    /** 当前登录用户 ID；未认证场景返回 null */
    public static Long getUserId() {
        return USER_ID.get();
    }

    public static void clear() {
        USER_ID.remove();
    }
}
