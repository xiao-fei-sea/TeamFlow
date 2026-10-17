package com.teamflow.system.dto;

/**
 * 用户对外视图：id 序列化为字符串（Jackson 全局 Long→String）。
 */
public record UserVO(Long id, String username, String email) {

    public static UserVO from(com.teamflow.system.entity.SysUser user) {
        return new UserVO(user.getId(), user.getUsername(), user.getEmail());
    }
}
