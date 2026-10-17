package com.teamflow.organization.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

/**
 * 组织视图：列表项（id/name/myRole/memberCount）与详情（补 ownerId/projectCount）共用，
 * null 字段不输出。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OrganizationVO(
        Long id,
        String name,
        String myRole,
        Long memberCount,
        Long ownerId,
        Long projectCount,
        LocalDateTime createdAt
) {
}
