package com.co.kc.imchat.service.social.infrastructure.support.constant;

/**
 * 社交服务缓存名称前缀。
 */
public final class SocialCacheNames {
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

    private SocialCacheNames() {
    }
}
