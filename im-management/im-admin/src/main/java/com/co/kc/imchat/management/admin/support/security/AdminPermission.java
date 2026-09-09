package com.co.kc.imchat.management.admin.support.security;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Admin 向 IAM 声明的普通用户管理权限。
 */
@Getter
@RequiredArgsConstructor
public enum AdminPermission {
    USER_READ(Code.USER_READ, "查询普通用户", "查询普通用户列表与详情"),
    USER_UPDATE(Code.USER_UPDATE, "修改普通用户", "修改普通用户资料"),
    USER_PASSWORD_RESET(Code.USER_PASSWORD_RESET, "重置普通用户密码", "重置普通用户登录密码"),
    USER_BAN(Code.USER_BAN, "封禁普通用户", "封禁或解除封禁普通用户"),
    USER_DELETE(Code.USER_DELETE, "删除普通用户", "逻辑删除普通用户");

    private final String code;
    private final String displayName;
    private final String description;

    /**
     * Admin 权限编码。
     */
    public static final class Code {
        public static final String USER_READ = "user:read";
        public static final String USER_UPDATE = "user:update";
        public static final String USER_PASSWORD_RESET = "user:password:reset";
        public static final String USER_BAN = "user:ban";
        public static final String USER_DELETE = "user:delete";

        private Code() {
        }
    }

}
