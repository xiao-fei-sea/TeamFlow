package com.teamflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * TeamFlow 启动入口。
 *
 * <p>Mapper 不使用 @MapperScan 全局扫描：后续各模块的 Mapper 接口统一加
 * {@code @Mapper} 注解，由 MyBatis-Plus 自动配置扫描主包 com.teamflow 下的注解接口。
 */
@SpringBootApplication
public class TeamFlowApplication {

    public static void main(String[] args) {
        SpringApplication.run(TeamFlowApplication.class, args);
    }
}
