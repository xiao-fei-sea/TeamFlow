package com.teamflow.organization.controller;

import com.teamflow.common.context.TenantContext;
import com.teamflow.common.result.PageVO;
import com.teamflow.common.result.Result;
import com.teamflow.organization.dto.MemberAddRequest;
import com.teamflow.organization.dto.MemberVO;
import com.teamflow.organization.service.OrganizationMemberService;
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

/**
 * 组织成员接口（接口设计文档 3.3）。
 */
@RestController
@RequestMapping("/api/organizations/{orgId}/members")
@RequiredArgsConstructor
public class OrganizationMemberController {

    private final OrganizationMemberService organizationMemberService;

    /** 成员列表（分页 + keyword 用户名/邮箱模糊），权限：组织成员 */
    @GetMapping
    public Result<PageVO<MemberVO>> list(@PathVariable Long orgId,
                                         @RequestParam(required = false) Integer page,
                                         @RequestParam(required = false) Integer size,
                                         @RequestParam(required = false) String keyword) {
        return Result.ok(organizationMemberService.pageMembers(
                TenantContext.getUserId(), orgId, page, size, keyword));
    }

    /** 添加成员（OWNER/ADMIN；role 仅 ADMIN/MEMBER；幂等） */
    @PostMapping
    public Result<MemberVO> add(@PathVariable Long orgId, @Valid @RequestBody MemberAddRequest request) {
        return Result.ok(organizationMemberService.addMember(TenantContext.getUserId(), orgId, request));
    }

    /** 变更成员角色（仅 OWNER，不可变更自己） */
    @PutMapping("/{userId}/role")
    public Result<Void> changeRole(@PathVariable Long orgId, @PathVariable Long userId,
                                   @Valid @RequestBody com.teamflow.organization.dto.RoleChangeRequest request) {
        organizationMemberService.changeRole(TenantContext.getUserId(), orgId, userId, request.role());
        return Result.ok();
    }

    /** 移除成员（OWNER 任意 / ADMIN 仅 MEMBER；不可移 OWNER、不可移自己；同事务级联） */
    @DeleteMapping("/{userId}")
    public Result<Void> remove(@PathVariable Long orgId, @PathVariable Long userId) {
        organizationMemberService.removeMember(TenantContext.getUserId(), orgId, userId);
        return Result.ok();
    }
}
