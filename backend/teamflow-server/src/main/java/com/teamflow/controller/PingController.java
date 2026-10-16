package com.teamflow.controller;

import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.teamflow.common.exception.BusinessException;
import com.teamflow.common.result.Result;
import com.teamflow.common.result.ResultCode;
import com.teamflow.dto.EchoRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * M0 健康检查与联调演示接口（M1 起认证链路接入后保留 ping，演示端点视情况移除）。
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PingController {

    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;

    /**
     * GET /api/ping — 健康检查：探测 MySQL / Redis 连通性（不因依赖不可用而失败）。
     */
    @GetMapping("/ping")
    public Result<Map<String, Object>> ping() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("app", "teamflow");
        data.put("status", "UP");
        data.put("timestamp", LocalDateTime.now());
        data.put("db", checkDb());
        data.put("redis", checkRedis());
        return Result.ok(data);
    }

    /**
     * POST /api/ping/echo — 参数校验演示：
     * message 为空 → 40001 + 字段级错误；?boom=true → 抛未知异常 → 50000。
     */
    @PostMapping("/ping/echo")
    public Result<Map<String, Object>> echo(@Valid @RequestBody EchoRequest request,
                                            @RequestParam(value = "boom", required = false) Boolean boom) {
        if (Boolean.TRUE.equals(boom)) {
            throw new IllegalStateException("M0 演示：模拟未知异常");
        }
        return Result.ok(Map.of("message", request.getMessage(), "echo", true));
    }

    /**
     * GET /api/ping/demo/{scene} — 错误码行为演示：unauthorized / forbidden / not-found / server-error。
     */
    @GetMapping("/ping/demo/{scene}")
    public Result<Void> demo(@PathVariable("scene") String scene) {
        switch (scene) {
            case "unauthorized" -> throw new BusinessException(ResultCode.UNAUTHORIZED);
            case "forbidden" -> throw new BusinessException(ResultCode.FORBIDDEN_TENANT);
            case "not-found" -> throw new BusinessException(ResultCode.NOT_FOUND);
            case "server-error" -> throw new IllegalStateException("M0 演示：模拟未知异常");
            default -> throw new BusinessException(ResultCode.NOT_FOUND);
        }
    }

    private String checkDb() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return "UP";
        } catch (Exception e) {
            log.warn("MySQL 健康检查失败: {}", e.getMessage());
            return "DOWN: " + briefReason(e);
        }
    }

    private String checkRedis() {
        try {
            String pong = redisTemplate.getConnectionFactory().getConnection().ping();
            return "PONG".equalsIgnoreCase(pong) ? "UP" : "DOWN: unexpected reply " + pong;
        } catch (Exception e) {
            log.warn("Redis 健康检查失败: {}", e.getMessage());
            return "DOWN: " + briefReason(e);
        }
    }

    private String briefReason(Throwable e) {
        String msg = e.getMessage();
        if (StringUtils.isBlank(msg)) {
            return e.getClass().getSimpleName();
        }
        return msg.length() > 120 ? msg.substring(0, 120) : msg;
    }
}
