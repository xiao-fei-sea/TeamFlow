package com.teamflow.system.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 签发与解析（HS256，技术计划设计书 6.1）。
 *
 * <ul>
 *   <li>载荷：jti（黑名单键）、sub=userId、username</li>
 *   <li>有效期默认 24h（expiresIn=86400）</li>
 *   <li>密钥环境变量化：JWT_SECRET，缺省仅用于本地开发</li>
 * </ul>
 */
@Component
public class JwtUtil {

    private final SecretKey key;
    private final long expireMillis;

    public JwtUtil(@Value("${teamflow.jwt.secret}") String secret,
                   @Value("${teamflow.jwt.expire-hours:24}") long expireHours) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireMillis = expireHours * 3600_000;
    }

    /** 有效期（秒），供响应 expiresIn 字段使用 */
    public long getExpireSeconds() {
        return expireMillis / 1000;
    }

    /** 签发 Token：jti 用于登出黑名单，sub 为 userId */
    public String generate(Long userId, String username) {
        Date now = new Date();
        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(userId))
                .claim("username", username)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expireMillis))
                .signWith(key)
                .compact();
    }

    /**
     * 解析并验签。
     *
     * @throws JwtException 签名不合法/格式错误/已过期（ExpiredJwtException）等
     */
    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
