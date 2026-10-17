package com.teamflow.organization.controller;

import com.teamflow.common.context.TenantContext;
import com.teamflow.common.result.Result;
import com.teamflow.organization.dto.OrganizationCreateRequest;
import com.teamflow.organization.dto.OrganizationUpdateRequest;
import com.teamflow.organization.dto.OrganizationVO;
import com.teamflow.organization.service.OrganizationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 组织接口（接口设计文档 3.2）。
 */
@RestController
@RequestMapping("/api/organizations")
@RequiredArgsConstructor
public class OrganizationController {

    private final OrganizationService organizationService;

    /** 创建组织：创建者自动 OWNER（PRD FR-O01） */
    @PostMapping
    public Result<OrganizationVO> create(@Valid @RequestBody OrganizationCreateRequest request) {
        return Result.ok(organizationService.create(TenantContext.getUserId(), request));
    }

    /** 我所属的组织列表 */
    @GetMapping
    public Result<List<OrganizationVO>> listMine() {
        return Result.ok(organizationService.listMy(TenantContext.getUserId()));
    }

    /** 组织详情（权限：组织成员） */
    @GetMapping("/{orgId}")
    public Result<OrganizationVO> detail(@PathVariable Long orgId) {
        return Result.ok(organizationService.getDetail(TenantContext.getUserId(), orgId));
    }

    /** 编辑组织名称（权限：OWNER/ADMIN） */
    @PutMapping("/{orgId}")
    public Result<Void> update(@PathVariable Long orgId, @Valid @RequestBody OrganizationUpdateRequest request) {
        organizationService.updateName(TenantContext.getUserId(), orgId, request);
        return Result.ok();
    }

    /** 删除组织（OWNER 专属，confirmName 二次确认，停用而非物理删除） */
    @DeleteMapping("/{orgId}")
    public Result<Void> delete(@PathVariable Long orgId, @RequestParam String confirmName) {
        organizationService.delete(TenantContext.getUserId(), orgId, confirmName);
        return Result.ok();
    }
}
