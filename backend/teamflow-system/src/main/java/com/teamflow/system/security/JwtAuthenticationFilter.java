package com.teamflow.system.security;

import com.teamflow.common.context.TenantContext;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * JWT 认证过滤器（技术计划设计书 6.1）：
 * 解析 Bearer Token → 校验 Redis 登出黑名单 → 写入 SecurityContext 与 TenantContext。
 *
 * <p>无 Token / 无效 / 已过期 / 已登出：不设置认证，由 RestAuthenticationEntryPoint
 * 统一返回 40101。principal=userId，credentials=原始 Token（登出接口直接取用）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String BEARER_PREFIX = "Bearer ";
    public static final String BLACKLIST_PREFIX = "tf:auth:blacklist:";

    private final JwtUtil jwtUtil;
    private final StringRedisTemplate stringRedisTemplate;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = resolveToken(request);
        if (token != null) {
            try {
                Claims claims = jwtUtil.parse(token);
                boolean blacklisted = Boolean.TRUE.equals(
                        stringRedisTemplate.hasKey(BLACKLIST_PREFIX + claims.getId()));
                if (!blacklisted) {
                    Long userId = Long.valueOf(claims.getSubject());
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(userId, token, List.of());
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    TenantContext.setUserId(userId);
                }
            } catch (JwtException | IllegalArgumentException e) {
                // 无效/过期 Token：视为未认证，交给 EntryPoint 统一返回 40101
                SecurityContextHolder.clearContext();
                log.debug("JWT 认证失败: {}", e.getMessage());
            }
        }
        try {
            filterChain.doFilter(request, response);
        } finally {
            // 线程池复用防串号：无论是否写入过都必须清理
            TenantContext.clear();
        }
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length()).trim();
            return token.isEmpty() ? null : token;
        }
        return null;
    }
}
