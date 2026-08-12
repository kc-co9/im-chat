package com.co.kc.imchat.service.account.infrastructure.support.constant;

/**
 * 账号服务缓存名称前缀。
 */
public final class AccountCacheNames {
    /** 用户基本信息，按 userId 查询。 */
    public static final String USER_ID = "im:chat:user:id:";
    /** 用户基本信息，按 email 查询。 */
    public static final String USER_EMAIL = "im:chat:user:email:";
    /** 邮箱是否已注册。 */
    public static final String USER_EMAIL_CONTAIN = "im:chat:user:email:contain:";
    /** 登录会话缓存。 */
    public static final String USER_SESSION = "im:chat:session:";

    private AccountCacheNames() {
    }
}
