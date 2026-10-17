package com.teamflow.system.controller;

import com.teamflow.common.context.TenantContext;
import com.teamflow.common.result.Result;
import com.teamflow.system.dto.UserSearchItem;
import com.teamflow.system.dto.UserVO;
import com.teamflow.system.service.AuthService;
import com.teamflow.system.service.UserSearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 用户接口（接口设计文档 3.1）。
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final AuthService authService;
    private final UserSearchService userSearchService;

    /** 当前登录用户信息 */
    @GetMapping("/me")
    public Result<UserVO> me() {
        return Result.ok(authService.getCurrentUser());
    }

    /** 用户搜索（T2.6，orgId 组织的 OWNER/ADMIN），用于添加成员弹窗 */
    @GetMapping("/search")
    public Result<List<UserSearchItem>> search(@RequestParam Long orgId,
                                               @RequestParam(required = false) String keyword) {
        return Result.ok(userSearchService.search(TenantContext.getUserId(), orgId, keyword));
    }
}
