package com.co.kc.imchat.management.iam.domain.authorization.model;

/** IAM 内部角色类型。 */
public enum IamRoleType {
    /** 拥有 IAM 全部内部管理权限。 */
    SUPER_ADMIN,
    /** 按权限编码配置的 IAM 自定义角色。 */
    CUSTOM
}
