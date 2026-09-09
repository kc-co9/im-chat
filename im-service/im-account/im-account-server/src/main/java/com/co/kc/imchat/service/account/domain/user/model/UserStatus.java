package com.co.kc.imchat.service.account.domain.user.model;

/**
 * 普通用户业务状态。
 */
public enum UserStatus {
    /** 正常状态，允许用户登录并使用账号能力。 */
    NORMAL,

    /** 封禁状态，禁止用户登录和继续认证。 */
    BANNED
}
