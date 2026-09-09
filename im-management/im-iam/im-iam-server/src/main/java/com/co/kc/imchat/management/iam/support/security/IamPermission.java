package com.co.kc.imchat.management.iam.support.security;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** IAM 管理端权限。 */
@Getter
@RequiredArgsConstructor
public enum IamPermission {
    ADMINISTRATOR_READ(Code.ADMINISTRATOR_READ, "查询管理员", "查询 IAM 管理员列表"),
    ADMINISTRATOR_WRITE(Code.ADMINISTRATOR_WRITE, "管理管理员", "启用、禁用、删除或重置管理员"),
    APPLICATION_READ(Code.APPLICATION_READ, "查询应用", "查询 IAM 接入应用列表"),
    APPLICATION_WRITE(Code.APPLICATION_WRITE, "管理应用", "注册 IAM 接入应用"),
    CLIENT_WRITE(Code.CLIENT_WRITE, "管理 OAuth 客户端", "注册、停用或轮换 OAuth 客户端密钥"),
    ROLE_READ(Code.ROLE_READ, "查询角色", "查询 IAM 角色列表"),
    ROLE_WRITE(Code.ROLE_WRITE, "管理角色", "创建角色或调整管理员角色"),
    PERMISSION_READ(Code.PERMISSION_READ, "查询权限", "查询应用权限目录"),
    SESSION_READ(Code.SESSION_READ, "查询会话", "查询管理员授权会话"),
    SESSION_REVOKE(Code.SESSION_REVOKE, "撤销会话", "撤销管理员授权会话");

    private final String code;
    private final String displayName;
    private final String description;

    /** 供安全注解使用的 IAM 权限编译期编码。 */
    public static final class Code {
        public static final String ADMINISTRATOR_READ = "iam:administrator:read";
        public static final String ADMINISTRATOR_WRITE = "iam:administrator:write";
        public static final String APPLICATION_READ = "iam:application:read";
        public static final String APPLICATION_WRITE = "iam:application:write";
        public static final String CLIENT_WRITE = "iam:client:write";
        public static final String ROLE_READ = "iam:role:read";
        public static final String ROLE_WRITE = "iam:role:write";
        public static final String PERMISSION_READ = "iam:permission:read";
        public static final String SESSION_READ = "iam:session:read";
        public static final String SESSION_REVOKE = "iam:session:revoke";

        private Code() {
        }
    }
}
