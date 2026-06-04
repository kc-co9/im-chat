package com.co.kc.imchat.infrastructure.support.constant;

/**
 * JetCache 缓存名称前缀。
 * <p>
 * 仓储缓存当前统一使用远程缓存，避免可变领域对象污染本地缓存引用。
 */
public final class CacheNames {
    /** 用户基本信息，按 userId 查询。 */
    public static final String USER_ID = "im:chat:user:id:";
    /** 用户基本信息，按 email 查询。 */
    public static final String USER_EMAIL = "im:chat:user:email:";
    /** 邮箱是否已注册。 */
    public static final String USER_EMAIL_CONTAIN = "im:chat:user:email:contain:";
    /** 群组基本信息，按 groupId 查询。 */
    public static final String GROUP_ID = "im:chat:group:id:";
    /** 群成员列表，按 groupId 查询。 */
    public static final String GROUP_MEMBERS = "im:chat:group:members:";
    /** 单个群成员，按 groupId + userId 查询。 */
    public static final String GROUP_MEMBER = "im:chat:group:member:";
    /** 用户是否属于群组。 */
    public static final String GROUP_MEMBER_CONTAIN = "im:chat:group:member:contain:";
    /** 用户好友列表，按 userId 查询。 */
    public static final String FRIENDS_USER = "im:chat:friends:user:";
    /** 单条好友关系，按 userId + friendUserId 查询。 */
    public static final String FRIEND_EDGE = "im:chat:friend:edge:";
    /** 好友关系是否正常可用。 */
    public static final String FRIEND_ACTIVE = "im:chat:friend:active:";

    private CacheNames() {
    }
}
