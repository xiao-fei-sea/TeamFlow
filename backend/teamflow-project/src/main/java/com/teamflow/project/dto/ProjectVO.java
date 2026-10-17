package com.teamflow.project.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

/**
 * 项目 VO（接口文档 3.4）。列表与详情共用：
 * 列表不含 memberCount；projectRole 仅项目成员有值，OWNER/ADMIN 未加入项目时为 null。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProjectVO(
        Long id,
        String name,
        String description,
        Integer status,
        Long taskTotal,
        Long taskDoing,
        String myRole,
        String projectRole,
        Long memberCount,
        LocalDateTime createdAt) {
}
