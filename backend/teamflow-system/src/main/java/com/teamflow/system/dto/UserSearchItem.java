package com.teamflow.system.dto;

/**
 * 用户搜索项（T2.6，接口设计文档 3.1）：邮箱脱敏输出；joined=true 已是该组织成员，前端置灰不可选。
 */
public record UserSearchItem(Long id, String username, String email, Boolean joined) {
}
