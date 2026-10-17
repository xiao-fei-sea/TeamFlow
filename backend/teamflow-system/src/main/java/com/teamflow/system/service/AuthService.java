package com.teamflow.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.teamflow.common.context.TenantContext;
import com.teamflow.common.exception.BusinessException;
import com.teamflow.common.exception.FieldValidationException;
import com.teamflow.common.result.FieldErrorItem;
import com.teamflow.common.result.ResultCode;
import com.teamflow.system.dto.AuthResponse;
import com.teamflow.system.dto.LoginRequest;
import com.teamflow.system.dto.RegisterRequest;
import com.teamflow.system.dto.UserVO;
import com.teamflow.system.entity.SysUser;
import com.teamflow.system.mapper.SysUserMapper;
import com.teamflow.system.security.JwtAuthenticationFilter;
import com.teamflow.system.security.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 认证服务：注册 / 登录 / 登出 / 当前用户。
 *
 * <p>错误口径（PRD 6.4、接口设计文档 3.1）：
 * 唯一性冲突 → 40001 字段级错误；凭据错误统一「用户名或密码错误」防枚举；
 * 禁用账号「账号已被禁用」（仅在凭据正确时提示，避免账号探测）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final StringRedisTemplate stringRedisTemplate;

    /** 注册成功即自动登录（PRD FR-A01）：返回 Token + 用户信息 */
    public AuthResponse register(RegisterRequest request) {
        // 唯一性检查：用户名/邮箱一次性给出全部冲突字段
        List<FieldErrorItem> errors = new ArrayList<>();
        if (sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, request.username())) > 0) {
            errors.add(new FieldErrorItem("username", "用户名已被占用"));
        }
        if (sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getEmail, request.email())) > 0) {
            errors.add(new FieldErrorItem("email", "邮箱已被注册"));
        }
        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }

        SysUser user = new SysUser();
        user.setUsername(request.username());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setEmail(request.email());
        user.setStatus(1);
        sysUserMapper.insert(user);
        log.info("用户注册成功: userId={}, username={}", user.getId(), user.getUsername());
        return buildAuthResponse(user);
    }

    /** 登录（PRD FR-A02）：account 支持用户名或邮箱 */
    public AuthResponse login(LoginRequest request) {
        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, request.account())
                .or()
                .eq(SysUser::getEmail, request.account()));
        // 防枚举：用户不存在与密码错误统一口径
        if (user == null || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "用户名或密码错误");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "账号已被禁用");
        }
        return buildAuthResponse(user);
    }

    /** 登出（PRD FR-A03）：JWT 写入 Redis 黑名单，TTL = 剩余有效期 */
    public void logout(String authorization) {
        if (authorization == null || !authorization.startsWith(JwtAuthenticationFilter.BEARER_PREFIX)) {
            return; // 过滤器已保证到达此处必带有效 Token，此处为防御
        }
        String token = authorization.substring(JwtAuthenticationFilter.BEARER_PREFIX.length()).trim();
        try {
            Claims claims = jwtUtil.parse(token);
            long ttlMillis = claims.getExpiration().getTime() - System.currentTimeMillis();
            if (ttlMillis > 0) {
                stringRedisTemplate.opsForValue().set(
                        JwtAuthenticationFilter.BLACKLIST_PREFIX + claims.getId(), "1",
                        Duration.ofMillis(ttlMillis));
                log.info("用户登出: userId={}, jti 已加入黑名单", claims.getSubject());
            }
            // 已过期 Token 无需拉黑
        } catch (JwtException e) {
            // 无法解析/已过期的 Token 本就无效，忽略
        }
    }

    /** 当前登录用户信息（GET /api/users/me） */
    public UserVO getCurrentUser() {
        Long userId = TenantContext.getUserId();
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            // 账号已被删除：登录态失效
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return UserVO.from(user);
    }

    private AuthResponse buildAuthResponse(SysUser user) {
        String token = jwtUtil.generate(user.getId(), user.getUsername());
        return new AuthResponse(token, jwtUtil.getExpireSeconds(), UserVO.from(user));
    }
}
