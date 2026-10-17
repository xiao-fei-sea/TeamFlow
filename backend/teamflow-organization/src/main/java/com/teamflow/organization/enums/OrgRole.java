package com.teamflow.organization.enums;

/**
 * 组织角色（PRD 4.1）：OWNER 有且仅有一个（P0 不可转让）。
 */
public enum OrgRole {

    OWNER, ADMIN, MEMBER;

    /** 是否拥有组织管理权（编辑组织 / 添加成员 / 移除 MEMBER / 全项目访问权） */
    public boolean canManage() {
        return this == OWNER || this == ADMIN;
    }
}
